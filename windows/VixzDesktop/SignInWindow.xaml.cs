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

        private async void DoneBtn_Click(object sender, RoutedEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Visible;
            StatusText.Text = "Syncing authentication status...";

            try
            {
                if (AuthWebView.CoreWebView2 != null)
                {
                    bool hasLoginCookie = await WebViewManager.HasYouTubeAuthCookiesAsync(AuthWebView.CoreWebView2);

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
