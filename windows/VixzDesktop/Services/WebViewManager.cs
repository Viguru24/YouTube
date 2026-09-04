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

        public static string CommonUserAgent =>
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

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
            "--disable-features=PreloadMediaEngagementData,TrackingPrevention " +
            "--disable-web-security " +
            "--allow-running-insecure-content";

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
        /// Masks embedded WebView2 indicators on every document created, preventing Google's "browser or app may not be secure" block.
        /// </summary>
        public static async Task MaskWebViewIndicatorsAsync(CoreWebView2 core)
        {
            if (core == null) return;

            // Script executed before any page scripts run
            var stealthScript = @"
                (function() {
                    try {
                        // Mask window.chrome.webview which Google/YouTube scripts check
                        if (window.chrome && window.chrome.webview) {
                            try {
                                Object.defineProperty(window.chrome, 'webview', {
                                    value: undefined,
                                    configurable: false,
                                    writable: false
                                });
                            } catch(e) {}
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
