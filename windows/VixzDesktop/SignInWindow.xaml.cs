using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;
using Newtonsoft.Json;
using VixzDesktop.Models;
using VixzDesktop.Services;

namespace VixzDesktop
{
    public partial class SignInWindow : Window
    {
        public bool IsSuccess { get; private set; } = false;

        // Prevent re-entrant auth detection if multiple NavigationCompleted events fire at once
        private bool _authDetectionInProgress = false;
        // Track if we've already successfully closed to avoid double-close
        private bool _closed = false;

        public SignInWindow()
        {
            InitializeComponent();
            Loaded += SignInWindow_Loaded;
        }

        private void TitleBar_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left) DragMove();
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
                StatusText.Text = "Connecting to Google sign-in...";

                // Share the same WebView2 profile so cookies are shared with the main window
                var env = await WebViewManager.GetEnvironmentAsync();
                await AuthWebView.EnsureCoreWebView2Async(env);
                await WebViewManager.MaskWebViewIndicatorsAsync(AuthWebView.CoreWebView2);

                AuthWebView.CoreWebView2.Settings.UserAgent = WebViewManager.CommonUserAgent;
                AuthWebView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                AuthWebView.CoreWebView2.Settings.IsStatusBarEnabled = false;

                // Register handlers (guard against double-registration on reload)
                AuthWebView.CoreWebView2.NavigationStarting -= OnNavigationStarting;
                AuthWebView.CoreWebView2.NavigationStarting += OnNavigationStarting;
                AuthWebView.CoreWebView2.NavigationCompleted -= OnNavigationCompleted;
                AuthWebView.CoreWebView2.NavigationCompleted += OnNavigationCompleted;

                // Go straight to Google's YouTube sign-in flow
                AuthWebView.CoreWebView2.Navigate(
                    "https://accounts.google.com/ServiceLogin?service=youtube" +
                    "&passive=true" +
                    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
                    "&hl=en");
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Error: {ex.Message}";
                LoginProgress.Visibility = Visibility.Collapsed;
            }
        }

        private void OnNavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Visible;
            var uri = e.Uri ?? "";

            if (uri.Contains("youtube.com"))
                StatusText.Text = "Signing in to YouTube...";
            else if (uri.Contains("accounts.google.com"))
                StatusText.Text = "🔐 Sign in with your Google account";
            else if (uri.Contains("myaccount.google.com"))
                StatusText.Text = "Loading account details...";
        }

        private async void OnNavigationCompleted(object? sender, CoreWebView2NavigationCompletedEventArgs e)
        {
            LoginProgress.Visibility = Visibility.Collapsed;
            if (_closed || _authDetectionInProgress || AuthWebView.CoreWebView2 == null) return;

            var uri = AuthWebView.CoreWebView2.Source ?? "";

            // Only attempt auth detection when we've actually landed on YouTube
            // (not on the account chooser or any intermediate Google page)
            if (uri.StartsWith("https://www.youtube.com") || uri.StartsWith("https://m.youtube.com"))
            {
                await TryCompleteSignInAsync();
            }
            else if (JevService.IsConfiguredAndEnabled &&
                     (uri.Contains("accounts.google.com") || uri.Contains("consent.youtube.com") || uri.Contains("accountchooser")))
            {
                // Autonomous Jev System-One resolution for account chooser & consent
                _ = Task.Run(async () =>
                {
                    await Task.Delay(1000);
                    await Dispatcher.InvokeAsync(async () =>
                    {
                        await TryJevAutoResolveAuthAsync(uri);
                    });
                });
            }
        }

        private async Task TryJevAutoResolveAuthAsync(string uri)
        {
            if (_closed || _authDetectionInProgress || AuthWebView.CoreWebView2 == null) return;
            try
            {
                var extractJs = @"(function() {
                    var items = [];
                    var btns = document.querySelectorAll('button, [role=""button""], a, div[data-identifier], div[data-email], li');
                    for (var i = 0; i < btns.length && items.length < 10; i++) {
                        var b = btns[i];
                        var txt = (b.innerText || b.getAttribute('aria-label') || b.textContent || '').trim();
                        if (txt && txt.length > 2 && txt.length < 80 && b.offsetParent !== null) {
                            items.push({ id: 'btn_' + i, text: txt.replace(/\n+/g, ' ') });
                            b.setAttribute('data-jev-id', 'btn_' + i);
                        }
                    }
                    return JSON.stringify(items);
                })()";

                var jsonStr = await AuthWebView.CoreWebView2.ExecuteScriptAsync(extractJs);
                if (string.IsNullOrWhiteSpace(jsonStr) || jsonStr == "null" || jsonStr == "\"[]\"") return;

                var rawJson = JsonConvert.DeserializeObject<string>(jsonStr);
                if (string.IsNullOrWhiteSpace(rawJson)) return;

                var elementsList = JsonConvert.DeserializeObject<List<Dictionary<string, string>>>(rawJson);
                if (elementsList == null || elementsList.Count == 0) return;

                var visibleMap = new Dictionary<string, string>();
                foreach (var el in elementsList)
                {
                    if (el.TryGetValue("id", out var id) && el.TryGetValue("text", out var text))
                    {
                        visibleMap[id] = text;
                    }
                }

                var userEmail = StorageService.Settings.UserAccount?.Email ?? "joeblack10810@gmail.com";
                var decisionKey = await JevService.DecideAuthActionAsync(uri, userEmail, visibleMap);

                if (!string.IsNullOrEmpty(decisionKey))
                {
                    StatusText.Text = $"⚡ Jev Auto-Action: Selecting '{visibleMap.GetValueOrDefault(decisionKey, decisionKey)}'...";
                    var clickJs = $@"(function() {{
                        var el = document.querySelector('[data-jev-id=""{decisionKey}""]');
                        if (el) {{ el.click(); return true; }}
                        return false;
                    }})()";
                    await AuthWebView.CoreWebView2.ExecuteScriptAsync(clickJs);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[SignInWindow] Jev AutoResolve error: {ex.Message}");
            }
        }

        private async Task TryCompleteSignInAsync()
        {
            if (_authDetectionInProgress || _closed) return;
            _authDetectionInProgress = true;

            try
            {
                StatusText.Text = "Checking authentication...";
                LoginProgress.Visibility = Visibility.Visible;

                // Give the page a moment to set cookies
                await Task.Delay(600);

                bool hasAuth = await WebViewManager.HasYouTubeAuthCookiesAsync(AuthWebView.CoreWebView2);
                if (hasAuth)
                {
                    StatusText.Text = "✅ Signed in! Loading your profile...";

                    // Extract account info from the live YouTube page
                    var account = await AccountSyncService.ExtractAndUpdateAccountAsync(AuthWebView.CoreWebView2);
                    if (account == null || !account.IsSignedIn)
                    {
                        account = new UserAccount
                        {
                            IsSignedIn = true,
                            DisplayName = "Google User",
                            Email = "",
                            LastSyncTime = DateTime.UtcNow
                        };
                        StorageService.SetUserAccount(account);
                    }

                    var who = !string.IsNullOrWhiteSpace(account.Email) ? account.Email : account.DisplayName;
                    StatusText.Text = $"✅ Connected as {who}!";
                    IsSuccess = true;

                    await Task.Delay(700);
                    CloseSuccess();
                }
                else
                {
                    // Landed on YouTube but no auth cookies — signed-out YouTube page
                    StatusText.Text = "⚠️ Sign-in not detected yet. Complete the sign-in above, then click Done.";
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[SignIn] Auth detection error: {ex.Message}");
                StatusText.Text = "Error checking sign-in. Try clicking Done when ready.";
            }
            finally
            {
                _authDetectionInProgress = false;
                LoginProgress.Visibility = Visibility.Collapsed;
            }
        }

        private void CloseSuccess()
        {
            if (_closed) return;
            _closed = true;
            try
            {
                DialogResult = true;
                Close();
            }
            catch { }
        }

        // "Done / Sync Account" button — manual trigger for when auto-detect didn't fire
        private async void DoneBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_authDetectionInProgress) return;
            LoginProgress.Visibility = Visibility.Visible;
            StatusText.Text = "Checking sign-in status...";

            try
            {
                if (AuthWebView.CoreWebView2 == null)
                {
                    StatusText.Text = "Browser not ready yet.";
                    return;
                }

                bool hasAuth = await WebViewManager.HasYouTubeAuthCookiesAsync(AuthWebView.CoreWebView2);
                var account = await AccountSyncService.ExtractAndUpdateAccountAsync(AuthWebView.CoreWebView2);

                if (account == null)
                {
                    account = new UserAccount
                    {
                        IsSignedIn = hasAuth,
                        DisplayName = hasAuth ? "Google User" : "",
                        Email = "",
                        LastSyncTime = DateTime.UtcNow
                    };
                    StorageService.SetUserAccount(account);
                }

                if (hasAuth || account.IsSignedIn)
                {
                    var who = !string.IsNullOrWhiteSpace(account.Email) ? account.Email : account.DisplayName;
                    StatusText.Text = $"✅ Connected as {who}!";
                    IsSuccess = true;
                    await Task.Delay(500);
                    CloseSuccess();
                }
                else
                {
                    StatusText.Text = "❌ Not signed in yet — please complete Google sign-in above.";
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

        // Reload button
        private async void ReloadBtn_Click(object sender, RoutedEventArgs e)
        {
            if (AuthWebView.CoreWebView2 != null)
                AuthWebView.CoreWebView2.Reload();
            else
                await InitializeAuthBrowserAsync();
        }

        // Open in system browser fallback
        private void OpenInBrowserBtn_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "https://www.youtube.com/signin?action_handle_signin=true",
                    UseShellExecute = true
                });
                StatusText.Text = "🌐 Sign in via your browser, then click '✅ Done / Sync Account'.";
            }
            catch (Exception ex)
            {
                StatusText.Text = $"Could not launch browser: {ex.Message}";
            }
        }

        private void CloseBtn_Click(object sender, RoutedEventArgs e)
        {
            DialogResult = IsSuccess;
            _closed = true;
            Close();
        }

        protected override void OnClosed(EventArgs e)
        {
            try { AuthWebView.Dispose(); } catch { }
            base.OnClosed(e);
        }
    }
}
