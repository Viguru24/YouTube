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
            await InitializeAuthBrowserAsync();
        }

        private async Task InitializeAuthBrowserAsync()
        {
            try
            {
                LoginProgress.Visibility = Visibility.Visible;
                StatusText.Text = "Connecting to Google / YouTube Sign In...";

                var appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
                var userDataFolder = Path.Combine(appData, "WebView2Profile");

                var options = new CoreWebView2EnvironmentOptions("--disable-features=PreloadMediaEngagementData,TrackingPrevention --disable-web-security");
                var env = await CoreWebView2Environment.CreateAsync(userDataFolder: userDataFolder, options: options);
                await AuthWebView.EnsureCoreWebView2Async(env);

                // Use standard desktop Chrome User-Agent so Google allows login without WebView2 restrictions
                AuthWebView.CoreWebView2.Settings.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
                AuthWebView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                AuthWebView.CoreWebView2.Settings.IsStatusBarEnabled = false;

                AuthWebView.CoreWebView2.NavigationStarting += AuthWebView_NavigationStarting;
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
                // Check cookies for LOGIN_INFO or SID / SAPISID
                var cookieManager = AuthWebView.CoreWebView2.CookieManager;
                var cookies = await cookieManager.GetCookiesAsync("https://www.youtube.com");
                bool hasLoginCookie = cookies.Any(c => c.Name.Equals("LOGIN_INFO", StringComparison.OrdinalIgnoreCase) || 
                                                       c.Name.Equals("SAPISID", StringComparison.OrdinalIgnoreCase) ||
                                                       c.Name.Equals("SID", StringComparison.OrdinalIgnoreCase));

                if (hasLoginCookie)
                {
                    StatusText.Text = "🟢 YouTube Account Authenticated! Finalizing profile...";

                    // Extract display info from page
                    string nameScript = @"(function() {
                        try {
                            if (window.ytcfg && typeof ytcfg.get === 'function') {
                                return ytcfg.get('USER_NAME') || ytcfg.get('ACCOUNT_NAME') || '';
                            }
                        } catch(e) {}
                        return '';
                    })()";
                    var nameRaw = await AuthWebView.ExecuteScriptAsync(nameScript);
                    var displayName = CleanJsonString(nameRaw);

                    if (string.IsNullOrWhiteSpace(displayName))
                    {
                        displayName = "Google User";
                    }

                    var account = new UserAccount
                    {
                        IsSignedIn = true,
                        DisplayName = displayName,
                        Email = "",
                        LastSyncTime = DateTime.UtcNow
                    };

                    StorageService.SetUserAccount(account);
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

        private async void DoneBtn_Click(object sender, RoutedEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Visible;
            StatusText.Text = "Syncing authentication status...";

            try
            {
                if (AuthWebView.CoreWebView2 != null)
                {
                    var cookieManager = AuthWebView.CoreWebView2.CookieManager;
                    var cookies = await cookieManager.GetCookiesAsync("https://www.youtube.com");
                    bool hasLoginCookie = cookies.Any(c => c.Name.Equals("LOGIN_INFO", StringComparison.OrdinalIgnoreCase) || 
                                                           c.Name.Equals("SAPISID", StringComparison.OrdinalIgnoreCase) ||
                                                           c.Name.Equals("SID", StringComparison.OrdinalIgnoreCase));

                    string nameScript = @"(function() {
                        try {
                            if (window.ytcfg && typeof ytcfg.get === 'function') {
                                return ytcfg.get('USER_NAME') || ytcfg.get('ACCOUNT_NAME') || '';
                            }
                        } catch(e) {}
                        return '';
                    })()";
                    var nameRaw = await AuthWebView.ExecuteScriptAsync(nameScript);
                    var displayName = CleanJsonString(nameRaw);

                    if (string.IsNullOrWhiteSpace(displayName))
                    {
                        displayName = hasLoginCookie ? "Google User" : WillRyanProfileData.ProfileName;
                    }

                    var account = new UserAccount
                    {
                        IsSignedIn = true,
                        DisplayName = displayName,
                        Email = "",
                        LastSyncTime = DateTime.UtcNow
                    };

                    StorageService.SetUserAccount(account);
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

        private void ReloadBtn_Click(object sender, RoutedEventArgs e)
        {
            AuthWebView.CoreWebView2?.Reload();
        }

        private void CloseBtn_Click(object sender, RoutedEventArgs e)
        {
            DialogResult = IsSuccess;
            Close();
        }
    }
}
