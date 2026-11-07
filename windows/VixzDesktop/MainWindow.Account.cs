using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;
using Newtonsoft.Json.Linq;
using VixzDesktop.Models;
using VixzDesktop.Services;

namespace VixzDesktop
{
    public partial class MainWindow : Window
    {
        #region User Account & Google Profile

        private bool _isSyncingAccount = false;

        public async Task<UserAccount?> SyncAccountProfileAsync(bool silent = true)
        {
            if (_isSyncingAccount) return StorageService.Settings.UserAccount;
            _isSyncingAccount = true;

            var logFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop", "account_sync.log");

            try
            {
                // ── Step 1: Try VideoWebView first ──────────────────────────────────────
                // It's already warm, authenticated, and on a live YouTube page — no extra
                // navigation needed. This avoids triggering YouTube's bot-detection throttle
                // that was causing the BackgroundSyncWebView to get a signed-out 154KB page.
                if (VideoWebView?.CoreWebView2 != null)
                {
                    var src = VideoWebView.Source?.ToString() ?? "";
                    // Only run on actual YouTube pages — not vixz.app/player.html
                    if (src.Contains("youtube.com") || src.Contains("google.com"))
                    {
                        File.AppendAllText(logFile, $"[{DateTime.Now}] Trying VideoWebView extraction on: {src}\n");
                        var quick = await AccountSyncService.ExtractAndUpdateAccountAsync(VideoWebView.CoreWebView2);
                        File.AppendAllText(logFile, $"[{DateTime.Now}] VideoWebView result: Name='{quick?.DisplayName}', Email='{quick?.Email}'\n");
                        if (quick != null && !string.IsNullOrWhiteSpace(quick.Email))
                        {
                            UpdateAccountUi();
                            if (!silent) ShowToast($"✅ Connected as {quick.Email}");
                            return quick;
                        }
                    }
                }

                // ── Step 2: BackgroundSyncWebView with consent cookies pre-injected ────
                BackgroundSyncWebView.Visibility = Visibility.Visible;
                var env = await WebViewManager.GetEnvironmentAsync();
                await BackgroundSyncWebView.EnsureCoreWebView2Async(env);
                await WebViewManager.MaskWebViewIndicatorsAsync(BackgroundSyncWebView.CoreWebView2);
                BackgroundSyncWebView.CoreWebView2.Settings.UserAgent = WebViewManager.CommonUserAgent;
                WebViewManager.EnsureConsentCookiesAsync(BackgroundSyncWebView.CoreWebView2);

                bool hasAuth = await WebViewManager.HasYouTubeAuthCookiesAsync(BackgroundSyncWebView.CoreWebView2);
                if (!hasAuth)
                {
                    if (!silent) ShowToast("⚠️ No Google account session found");
                    return null;
                }

                File.AppendAllText(logFile, $"[{DateTime.Now}] Starting BackgroundSyncWebView sync... HasAuth={hasAuth}\n");

                var tcs = new TaskCompletionSource<bool>();
                void OnNavCompleted(object? s, CoreWebView2NavigationCompletedEventArgs e)
                {
                    BackgroundSyncWebView.CoreWebView2.NavigationCompleted -= OnNavCompleted;
                    tcs.TrySetResult(e.IsSuccess);
                }

                BackgroundSyncWebView.CoreWebView2.NavigationCompleted += OnNavCompleted;
                BackgroundSyncWebView.CoreWebView2.Navigate("https://www.youtube.com");

                var completed = await Task.WhenAny(tcs.Task, Task.Delay(10000));
                bool navOk = completed == tcs.Task && await tcs.Task;
                File.AppendAllText(logFile, $"[{DateTime.Now}] Navigation to youtube.com finished: {navOk}\n");

                if (navOk)
                {
                    UserAccount? updated = null;

                    for (int attempt = 1; attempt <= 4; attempt++)
                    {
                        await Task.Delay(attempt == 1 ? 1200 : 1500);
                        updated = await AccountSyncService.ExtractAndUpdateAccountAsync(BackgroundSyncWebView.CoreWebView2);
                        File.AppendAllText(logFile, $"[{DateTime.Now}] Attempt {attempt}: Name='{updated?.DisplayName}', Email='{updated?.Email}', Avatar='{updated?.AvatarUrl}'\n");
                        if (updated != null && !string.IsNullOrWhiteSpace(updated.Email))
                            break;
                    }

                    // Fallback: youtube.com/account if main page gave signed-out response
                    // Fallback: youtube.com/account if main page gave signed-out response or missing channel name
                    if (updated == null || string.IsNullOrWhiteSpace(updated.Email) || string.IsNullOrWhiteSpace(updated.DisplayName) || updated.DisplayName == "Google User" || updated.DisplayName == "Google Account")
                    {
                        var tcsAccount = new TaskCompletionSource<bool>();
                        void OnAccountNav(object? s, CoreWebView2NavigationCompletedEventArgs e)
                        {
                            BackgroundSyncWebView.CoreWebView2.NavigationCompleted -= OnAccountNav;
                            tcsAccount.TrySetResult(e.IsSuccess);
                        }
                        BackgroundSyncWebView.CoreWebView2.NavigationCompleted += OnAccountNav;
                        BackgroundSyncWebView.CoreWebView2.Navigate("https://www.youtube.com/account");
                        var compAcc = await Task.WhenAny(tcsAccount.Task, Task.Delay(8000));
                        bool accNavOk = compAcc == tcsAccount.Task && await tcsAccount.Task;
                        File.AppendAllText(logFile, $"[{DateTime.Now}] Navigation to youtube.com/account: {accNavOk}\n");
                        if (accNavOk)
                        {
                            await Task.Delay(1500);
                            var accUpdated = await AccountSyncService.ExtractAndUpdateAccountAsync(BackgroundSyncWebView.CoreWebView2);
                            File.AppendAllText(logFile, $"[{DateTime.Now}] /account result: Name='{accUpdated?.DisplayName}', Email='{accUpdated?.Email}'\n");
                            if (accUpdated != null) updated = accUpdated;
                        }
                    }

                    if (updated != null && !string.IsNullOrWhiteSpace(updated.Email))
                    {
                        UpdateAccountUi();
                        if (!silent)
                        {
                            var who = !string.IsNullOrWhiteSpace(updated.Email) ? updated.Email : updated.DisplayName;
                            ShowToast($"✅ Connected as {who}");
                        }
                        return updated;
                    }

                    // Sync failed to get account from YouTube main page.
                    // The BackgroundSyncWebView may be getting a bot-throttle page (111KB).
                    // This is transient — don't prompt sign-in here, as it causes a loop.
                    // The user can manually sign in via the account button.
                    File.AppendAllText(logFile, $"[{DateTime.Now}] Sync: could not extract account info — possibly throttled, will retry next cycle\n");
                }
            }
            catch (Exception ex)
            {
                File.AppendAllText(logFile, $"[{DateTime.Now}] Sync error: {ex}\n");
                System.Diagnostics.Debug.WriteLine($"Account sync error: {ex.Message}");
            }
            finally
            {
                _isSyncingAccount = false;
                try
                {
                    BackgroundSyncWebView.Visibility = Visibility.Collapsed;
                    BackgroundSyncWebView.CoreWebView2?.Navigate("about:blank");
                }
                catch { }
            }

            return StorageService.Settings.UserAccount;
        }

        private void AccountBtn_Click(object sender, RoutedEventArgs e)
        {
            var account = StorageService.Settings.UserAccount;
            if (account != null && account.IsSignedIn)
            {
                AccountPopup.IsOpen = !AccountPopup.IsOpen;
                if (AccountPopup.IsOpen && (string.IsNullOrWhiteSpace(account.Email) || account.DisplayName == "Google User"))
                {
                    _ = SyncAccountProfileAsync(silent: true);
                }
            }
            else
            {
                OpenSignInWindow_Click(sender, e);
            }
        }

        private void CloseAccountModal_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
        }

        private void OpenSignInWindow_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
            var win = new SignInWindow { Owner = this };
            if (win.ShowDialog() == true || win.IsSuccess)
            {
                UpdateAccountUi();
                var who = !string.IsNullOrWhiteSpace(StorageService.Settings.UserAccount.Email)
                    ? StorageService.Settings.UserAccount.Email
                    : StorageService.Settings.UserAccount.DisplayName;
                ShowToast($"👤 Signed in as {who}!");
                if (_currentVideo != null && VideoWebView.CoreWebView2 != null)
                {
                    _ = PlayVideoAsync(_currentVideo);
                }
                // Ensure bottom controls are visible after returning from sign-in dialog
                AnimateBottomBar(1.0);
                _bottomBarHideTimer?.Stop();
                _bottomBarHideTimer?.Start();
            }
        }

        private async void SyncAccountSubscriptions_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
            ShowToast("🔄 Syncing Google account & subscriptions...");
            await SyncAccountProfileAsync(silent: false);
            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
            ShowToast("✅ Subscriptions & feed synced!");
        }

        private async void SignOutAccount_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;

            // Clear the stored account first
            StorageService.SignOutUser();
            UpdateAccountUi();

            // Clear YouTube + Google auth cookies from the shared WebView2 profile
            // so HasYouTubeAuthCookiesAsync returns false on next sync
            try
            {
                var coreWv2 = VideoWebView.CoreWebView2;
                if (coreWv2 != null)
                {
                    // Wipe all cookies in the profile — this is a full sign-out
                    coreWv2.CookieManager.DeleteAllCookies();

                    // Also hit Google's sign-out endpoint to invalidate the server-side session
                    var env = await WebViewManager.GetEnvironmentAsync();
                    await BackgroundSyncWebView.EnsureCoreWebView2Async(env);
                    BackgroundSyncWebView.CoreWebView2?.Navigate("https://accounts.google.com/Logout");
                    await Task.Delay(1500);
                    BackgroundSyncWebView.CoreWebView2?.Navigate("about:blank");
                    BackgroundSyncWebView.Visibility = Visibility.Collapsed;
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Vixz] Sign-out cookie clear error: {ex.Message}");
            }

            ShowToast("🚪 Signed out — cookies cleared");
        }

        private async void CreateNewProfile_Click(object sender, RoutedEventArgs e)
        {
            var count = ProfileManagerService.Instance.Profiles.Count + 1;
            var newProfile = ProfileManagerService.Instance.CreateNewProfile($"Profile {count}");
            ShowToast($"✨ Created profile '{newProfile.Name}'. Switching...");
            await ProfileManagerService.Instance.SwitchToProfileAsync(newProfile.Id, VideoWebView?.CoreWebView2);
            UpdateAccountUi();
            SubscribedChannelsList.ItemsSource = null;
            SubscribedChannelsList.ItemsSource = StorageService.Settings.SubscribedChannels;
            SubscribersHeader.Text = $"👤 Subscriptions ({StorageService.Settings.SubscribedChannels.Count})";
            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
        }

        private async void ProfileItem_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.DataContext is UserProfileSession profile)
            {
                if (profile.Id == ProfileManagerService.Instance.ActiveProfileId) return;

                AccountPopup.IsOpen = false;
                ShowToast($"👥 Switching to profile '{profile.Name}'...");

                var ok = await ProfileManagerService.Instance.SwitchToProfileAsync(profile.Id, VideoWebView?.CoreWebView2);
                if (ok)
                {
                    UpdateAccountUi();
                    SubscribedChannelsList.ItemsSource = null;
                    SubscribedChannelsList.ItemsSource = StorageService.Settings.SubscribedChannels;
                    SubscribersHeader.Text = $"👤 Subscriptions ({StorageService.Settings.SubscribedChannels.Count})";
                    ShowToast($"✅ Switched to '{profile.Name}'");
                    await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
                }
            }
        }

        #endregion
    }
}
