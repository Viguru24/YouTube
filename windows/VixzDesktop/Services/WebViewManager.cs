using System;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Web.WebView2.Core;

namespace VixzDesktop.Services
{
    /// <summary>
    /// Centralized factory and lifecycle manager for WebView2 environments across all application windows.
    /// Guarantees a single CoreWebView2Environment is used for the user-data folder to prevent COM "Class not registered"
    /// conflicts, provides automatic profile fallback on lock/corruption, and masks embedded webview signals for Google authentication.
    /// </summary>
    public static class WebViewManager
    {
        private static readonly SemaphoreSlim _initLock = new(1, 1);
        private static CoreWebView2Environment? _sharedEnvironment;

        // Fallback version used before the environment is initialised or if version parsing fails.
        private const string FallbackChromeVersion = "131.0.0.0";

        /// <summary>
        /// Returns a Chrome-compatible User-Agent string whose major version is automatically
        /// derived from the installed WebView2 runtime.  This keeps the UA in sync with the
        /// runtime and prevents YouTube's stale-version bot-detection from triggering.
        /// </summary>
        public static string CommonUserAgent
        {
            get
            {
                var version = FallbackChromeVersion;
                if (_sharedEnvironment != null)
                {
                    try
                    {
                        // BrowserVersionString is e.g. "131.0.6778.205"
                        // We keep the full string so it matches the real runtime exactly.
                        var raw = _sharedEnvironment.BrowserVersionString;
                        if (!string.IsNullOrWhiteSpace(raw))
                            version = raw.Split(' ')[0]; // strip any trailing channel suffix
                    }
                    catch { /* ignore — fall through to hardcoded version */ }
                }
                return $"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{version} Safari/537.36";
            }
        }

        public static readonly string ChromiumFlags =
            "--autoplay-policy=no-user-gesture-required " +
            "--force_high_performance_gpu " +
            "--gpu-preference=2 " +
            "--enable-gpu-rasterization " +
            "--force-gpu-rasterization " +
            "--enable-zero-copy " +
            "--use-angle=d3d11 " +
            "--enable-accelerated-video-decode " +
            "--enable-accelerated-mjpeg-decode " +
            "--enable-accelerated-2d-canvas " +
            "--enable-features=VaapiVideoDecoder,D3D11VideoDecoder,PlatformHEVCDecoderSupport,DirectCompositionVideoOverlays,HardwareMediaKeyHandling " +
            "--disable-features=PreloadMediaEngagementData,TrackingPrevention";

        /// <summary>
        /// Gets or creates the shared CoreWebView2Environment singleton with resilient profile fallback.
        /// </summary>
        public static async Task<CoreWebView2Environment> GetEnvironmentAsync()
        {
            if (_sharedEnvironment != null)
            {
                return _sharedEnvironment;
            }

            await _initLock.WaitAsync();
            try
            {
                if (_sharedEnvironment != null)
                {
                    return _sharedEnvironment;
                }

                var baseDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
                Directory.CreateDirectory(baseDir);

                var primaryProfile = Path.Combine(baseDir, "WebView2Profile");
                var options = new CoreWebView2EnvironmentOptions(ChromiumFlags);

                try
                {
                    _sharedEnvironment = await CoreWebView2Environment.CreateAsync(
                        browserExecutableFolder: null,
                        userDataFolder: primaryProfile,
                        options: options
                    );
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Primary WebView2 profile initialization failed: {ex.Message}. Attempting resilient fallback profile...");

                    // If primary profile is locked or corrupted by another process, fallback to secondary resilient profile
                    var fallbackProfile = Path.Combine(baseDir, "WebView2Profile_Resilient");
                    Directory.CreateDirectory(fallbackProfile);

                    _sharedEnvironment = await CoreWebView2Environment.CreateAsync(
                        browserExecutableFolder: null,
                        userDataFolder: fallbackProfile,
                        options: options
                    );
                }

                return _sharedEnvironment;
            }
            finally
            {
                _initLock.Release();
            }
        }

        /// <summary>
        /// Injects GDPR consent cookies into the WebView2 cookie store so YouTube never
        /// redirects to consent.youtube.com. The sync log shows this gate blocking every
        /// navigation for GB-region sessions. SOCS=CAI = consent accepted, no personalisation.
        /// </summary>
        public static void EnsureConsentCookiesAsync(CoreWebView2 core)
        {
            if (core == null) return;
            try
            {
                var cookieManager = core.CookieManager;

                // Domains that need consent cookies
                var domains = new[] { ".youtube.com", ".google.com" };

                foreach (var domain in domains)
                {
                    // SOCS: primary GDPR consent signal. CAI = accepted.
                    var socs = cookieManager.CreateCookie("SOCS", "CAI", domain, "/");
                    socs.IsSecure = true;
                    socs.Expires = DateTime.UtcNow.AddYears(2);
                    cookieManager.AddOrUpdateCookie(socs);

                    // CONSENT: legacy consent cookie, belt-and-braces
                    var consent = cookieManager.CreateCookie("CONSENT", "YES+cb", domain, "/");
                    consent.IsSecure = true;
                    consent.Expires = DateTime.UtcNow.AddYears(2);
                    cookieManager.AddOrUpdateCookie(consent);
                }

                // PREF: sets language to en-US so YouTube doesn't redirect based on locale
                var pref = cookieManager.CreateCookie("PREF", "hl=en&gl=US", ".youtube.com", "/");
                pref.IsSecure = true;
                pref.Expires = DateTime.UtcNow.AddYears(2);
                cookieManager.AddOrUpdateCookie(pref);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Vixz] EnsureConsentCookiesAsync failed: {ex.Message}");
            }
        }

        /// <summary>
        /// Masks embedded WebView2 indicators on every document created, preventing Google's "browser or app may not be secure" block.
        /// </summary>
        public static async Task MaskWebViewIndicatorsAsync(CoreWebView2 core)
        {
            if (core == null) return;

            // Script executed before any page scripts run
            var stealthScript = @"
                (function() {
                    try {
                        // Only mask window.chrome.webview on Google / YouTube auth pages, NEVER on vixz.app
                        var host = window.location.hostname || '';
                        if (host.includes('google.') || host.includes('youtube.') || host.includes('accounts.')) {
                            if (window.chrome && window.chrome.webview) {
                                try {
                                    Object.defineProperty(window.chrome, 'webview', {
                                        value: undefined,
                                        configurable: false,
                                        writable: false
                                    });
                                } catch(e) {}
                            }
                        }

                        // Ensure navigator.webdriver is false
                        Object.defineProperty(navigator, 'webdriver', {
                            get: () => false,
                            configurable: true
                        });
                    } catch(e) {}
                })();
            ";

            await core.AddScriptToExecuteOnDocumentCreatedAsync(stealthScript);
        }

        /// <summary>
        /// Multi-cookie inspection to robustly verify active YouTube/Google login session across known auth tokens.
        /// </summary>
        public static async Task<bool> HasYouTubeAuthCookiesAsync(CoreWebView2 core)
        {
            if (core == null) return false;

            try
            {
                var cookieManager = core.CookieManager;
                var cookies = await cookieManager.GetCookiesAsync("https://www.youtube.com");

                var authCookieNames = new[]
                {
                    "LOGIN_INFO",
                    "SAPISID",
                    "APISID",
                    "SID",
                    "HSID",
                    "SSID",
                    "__Secure-1PSID",
                    "__Secure-3PSID"
                };

                return cookies.Any(c => authCookieNames.Contains(c.Name, StringComparer.OrdinalIgnoreCase));
            }
            catch
            {
                return false;
            }
        }
    }
}
