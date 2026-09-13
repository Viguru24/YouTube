using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;
using VixzDesktop.Models;
using VixzDesktop.Services;

namespace VixzDesktop
{
    public partial class SignInWindow : Window
    {
        public bool IsSuccess { get; private set; } = false;
        private bool _isDetecting = false;

        public SignInWindow()
        {
            InitializeComponent();
            Loaded += SignInWindow_Loaded;
        }

        private void TitleBar_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                DragMove();
            }
        }

        private async void SignInWindow_Loaded(object sender, RoutedEventArgs e)
        {
            HideUnavailableBrowserButtons();
            await InitializeAuthBrowserAsync();
        }

        private async Task InitializeAuthBrowserAsync()
        {
            try
            {
                LoginProgress.Visibility = Visibility.Visible;
                StatusText.Text = "Connecting to Google / YouTube Sign In...";

                var env = await WebViewManager.GetEnvironmentAsync();
                await AuthWebView.EnsureCoreWebView2Async(env);
                await WebViewManager.MaskWebViewIndicatorsAsync(AuthWebView.CoreWebView2);

                // Use standard desktop Chrome User-Agent so Google allows login without WebView2 restrictions
                AuthWebView.CoreWebView2.Settings.UserAgent = WebViewManager.CommonUserAgent;
                AuthWebView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                AuthWebView.CoreWebView2.Settings.IsStatusBarEnabled = false;

                AuthWebView.CoreWebView2.NavigationStarting -= AuthWebView_NavigationStarting;
                AuthWebView.CoreWebView2.NavigationStarting += AuthWebView_NavigationStarting;
                AuthWebView.CoreWebView2.NavigationCompleted -= AuthWebView_NavigationCompleted;
                AuthWebView.CoreWebView2.NavigationCompleted += AuthWebView_NavigationCompleted;

                // Navigate directly to YouTube sign-in endpoint
                var targetUrl = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue";
                AuthWebView.CoreWebView2.Navigate(targetUrl);
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Error initializing sign-in: {ex.Message}";
                LoginProgress.Visibility = Visibility.Collapsed;
            }
        }

        private void AuthWebView_NavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Visible;
            if (e.Uri.Contains("youtube.com"))
            {
                StatusText.Text = "Redirecting to YouTube...";
            }
            else if (e.Uri.Contains("accounts.google.com"))
            {
                StatusText.Text = "🔐 Sign in with your Google account";
            }
        }

        private async void AuthWebView_NavigationCompleted(object? sender, CoreWebView2NavigationCompletedEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Collapsed;
            if (AuthWebView.CoreWebView2 == null) return;

            var uri = AuthWebView.CoreWebView2.Source ?? "";

            // Check if user reached YouTube home or signed in state
            if (uri.Contains("youtube.com") && !_isDetecting)
            {
                await DetectYouTubeAuthenticationAsync();
            }
        }

        private async Task DetectYouTubeAuthenticationAsync()
        {
            if (_isDetecting || AuthWebView.CoreWebView2 == null) return;
            _isDetecting = true;

            try
            {
                // Robust multi-cookie check for active login session
                bool hasLoginCookie = await WebViewManager.HasYouTubeAuthCookiesAsync(AuthWebView.CoreWebView2);

                if (hasLoginCookie)
                {
                    StatusText.Text = "🟢 YouTube Account Authenticated! Finalizing profile...";

                    var account = await AccountSyncService.ExtractAndUpdateAccountAsync(AuthWebView.CoreWebView2);
                    if (account == null)
                    {
                        account = new UserAccount
                        {
                            IsSignedIn = true,
                            DisplayName = "Google Account",
                            Email = "",
                            LastSyncTime = DateTime.UtcNow
                        };
                        StorageService.SetUserAccount(account);
                    }

                    var who = !string.IsNullOrWhiteSpace(account.Email) ? account.Email : account.DisplayName;
                    StatusText.Text = $"🟢 Connected as {who}!";
                    IsSuccess = true;

                    await Task.Delay(800);
                    DialogResult = true;
                    Close();
                    return;
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Detect auth error: {ex.Message}");
            }
            finally
            {
                _isDetecting = false;
            }
        }

        private void OpenInBrowserBtn_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                var targetUrl = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue";
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = targetUrl,
                    UseShellExecute = true
                });
                StatusText.Text = "🌐 Sign in via browser, then click 'Done / Sync Account' below.";
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Could not launch browser: {ex.Message}";
            }
        }

        private void HideUnavailableBrowserButtons()
        {
            if (!BrowserCookieImporter.IsBrowserInstalled(BrowserChoice.Chrome))
                ImportChromeBtn.Visibility = Visibility.Collapsed;
            if (!BrowserCookieImporter.IsBrowserInstalled(BrowserChoice.Edge))
                ImportEdgeBtn.Visibility = Visibility.Collapsed;
        }

        private async void ImportChrome_Click(object sender, RoutedEventArgs e)
            => await ImportFromBrowserAsync(BrowserChoice.Chrome);

        private async void ImportEdge_Click(object sender, RoutedEventArgs e)
            => await ImportFromBrowserAsync(BrowserChoice.Edge);

        private async Task ImportFromBrowserAsync(BrowserChoice browser)
        {
            LoginProgress.Visibility = Visibility.Visible;
            StatusText.Text = $"🔍 Reading {browser} cookies...";

            try
            {
                var cookies = await BrowserCookieImporter.ImportYouTubeCookiesAsync(browser);

                if (cookies.Count == 0)
                {
                    StatusText.Text = $"⚠️ No YouTube/Google cookies found in {browser}. Sign in to YouTube in {browser} first.";
                    return;
                }

                if (AuthWebView.CoreWebView2 == null)
                {
                    StatusText.Text = "⚠️ Browser not ready. Click Reload first.";
                    return;
                }

                var cookieManager = AuthWebView.CoreWebView2.CookieManager;

                foreach (var c in cookies)
                {
                    // Normalise domain: WebView2 requires leading dot for domain cookies
                    var domain = c.Domain.StartsWith('.') ? c.Domain : $".{c.Domain}";
                    var wv2Cookie = cookieManager.CreateCookie(c.Name, c.Value, domain, c.Path);
                    wv2Cookie.IsSecure = true;
                    wv2Cookie.SameSite = Microsoft.Web.WebView2.Core.CoreWebView2CookieSameSiteKind.None;
                    cookieManager.AddOrUpdateCookie(wv2Cookie);
                }

                StatusText.Text = $"🟢 {cookies.Count} cookies imported from {browser}! Checking auth...";

                // Navigate to YouTube — NavigationCompleted will run DetectYouTubeAuthenticationAsync
                await Task.Delay(300);
                AuthWebView.CoreWebView2.Navigate("https://www.youtube.com/");
            }
            catch (Exception ex)
            {
                var hint = ex.Message.Contains("profile not found")
                    ? $" Make sure {browser} is installed and you have signed into YouTube in it at least once."
                    : ex is System.IO.IOException
                        ? $" Try closing {browser} and importing again."
                        : "";
                StatusText.Text = $"⚠️ Import failed: {ex.Message}{hint}";
            }
            finally
            {
                LoginProgress.Visibility = Visibility.Collapsed;
            }
        }

        private void OpenPasteCookies_Click(object sender, RoutedEventArgs e)
        {
            PasteCookiesOverlay.Visibility = Visibility.Visible;
            CookiesInputBox.Focus();
        }

        private void CancelPasteCookies_Click(object sender, RoutedEventArgs e)
        {
            PasteCookiesOverlay.Visibility = Visibility.Collapsed;
            CookiesInputBox.Clear();
        }

        private async void ApplyCookies_Click(object sender, RoutedEventArgs e)
        {
            var raw = CookiesInputBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(raw))
            {
                StatusText.Text = "⚠️ Please paste valid cookie data.";
                return;
            }

            if (AuthWebView.CoreWebView2 == null)
            {
                StatusText.Text = "⚠️ Browser not ready. Click Reload first.";
                return;
            }

            try
            {
                var cookieManager = AuthWebView.CoreWebView2.CookieManager;
                int imported = 0;

                foreach (var part in raw.Split(';'))
                {
                    var cookie = part.Trim();
                    if (string.IsNullOrEmpty(cookie)) continue;
                    var eq = cookie.IndexOf('=');
                    if (eq < 0) continue;
                    var name = cookie.Substring(0, eq).Trim();
                    var value = cookie.Substring(eq + 1).Trim();
                    if (string.IsNullOrEmpty(name)) continue;

                    // Inject into both YouTube and Google domains
                    var ytCookie = cookieManager.CreateCookie(name, value, ".youtube.com", "/");
                    ytCookie.IsSecure = true;
                    ytCookie.SameSite = CoreWebView2CookieSameSiteKind.None;
                    cookieManager.AddOrUpdateCookie(ytCookie);

                    var gCookie = cookieManager.CreateCookie(name, value, ".google.com", "/");
                    gCookie.IsSecure = true;
                    gCookie.SameSite = CoreWebView2CookieSameSiteKind.None;
                    cookieManager.AddOrUpdateCookie(gCookie);

                    imported++;
                }

                PasteCookiesOverlay.Visibility = Visibility.Collapsed;
                CookiesInputBox.Clear();
                StatusText.Text = $"🟢 {imported} cookie(s) imported! Reloading...";

                // Navigate to YouTube to trigger auth detection
                await Task.Delay(400);
                AuthWebView.CoreWebView2.Navigate("https://www.youtube.com/");
            }
            catch (Exception ex)
            {
                StatusText.Text = $"⚠️ Cookie import failed: {ex.Message}";
            }
        }

        private async void DoneBtn_Click(object sender, RoutedEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Visible;
            StatusText.Text = "Syncing authentication status...";

            try
            {
                if (AuthWebView.CoreWebView2 != null)
                {
                    bool hasLoginCookie = await WebViewManager.HasYouTubeAuthCookiesAsync(AuthWebView.CoreWebView2);

                    var account = await AccountSyncService.ExtractAndUpdateAccountAsync(AuthWebView.CoreWebView2);
                    if (account == null)
                    {
                        account = new UserAccount
                        {
                            IsSignedIn = hasLoginCookie,
                            DisplayName = hasLoginCookie ? "Google Account" : WillRyanProfileData.ProfileName,
                            Email = "",
                            LastSyncTime = DateTime.UtcNow
                        };
                        StorageService.SetUserAccount(account);
                    }

                    IsSuccess = true;
                    DialogResult = true;
                    Close();
                    return;
                }
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Error: {ex.Message}";
            }
            finally
            {
                LoginProgress.Visibility = Visibility.Collapsed;
            }
        }

        private static string CleanJsonString(string? json)
        {
            if (string.IsNullOrWhiteSpace(json) || json == "null" || json == "\"\"") return "";
            var trimmed = json.Trim();
            if (trimmed.StartsWith("\"") && trimmed.EndsWith("\"") && trimmed.Length >= 2)
            {
                return trimmed.Substring(1, trimmed.Length - 2);
            }
            return trimmed;
        }

        private async void ReloadBtn_Click(object sender, RoutedEventArgs e)
        {
            if (AuthWebView.CoreWebView2 != null)
            {
                AuthWebView.CoreWebView2.Reload();
            }
            else
            {
                await InitializeAuthBrowserAsync();
            }
        }

        private void CloseBtn_Click(object sender, RoutedEventArgs e)
        {
            DialogResult = IsSuccess;
            Close();
        }

        protected override void OnClosed(EventArgs e)
        {
            try
            {
                AuthWebView.Dispose();
            }
            catch { }
            base.OnClosed(e);
        }
    }
}
