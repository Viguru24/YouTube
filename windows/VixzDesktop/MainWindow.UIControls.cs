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
        #region Sleep Timer

        private void SleepTimerBtn_Click(object sender, RoutedEventArgs e)
        {
            SleepTimerPopup.IsOpen = true;
        }

        private void CloseSleepModal_Click(object sender, RoutedEventArgs e)
        {
            SleepTimerPopup.IsOpen = false;
        }

        private void SleepSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (SleepSliderValueText != null)
            {
                SleepSliderValueText.Text = $"{(int)e.NewValue} minutes";
            }
        }

        private void Preset15_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 15; }
        private void Preset30_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 30; }
        private void Preset45_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 45; }
        private void Preset60_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 60; }

        private void StartSleepTimer_Click(object sender, RoutedEventArgs e)
        {
            int minutes = (int)SleepSlider.Value;
            ArmSleepTimer(minutes);
            SleepTimerPopup.IsOpen = false;
        }

        /// <summary>Shared helper used by both the manual popup and the AI voice command.</summary>
        private void ArmSleepTimer(int minutes)
        {
            _lastSleepDurationMinutes = minutes;
            _sleepRemainingSeconds = minutes * 60;

            _sleepTimer?.Stop();
            _sleepTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
            _sleepTimer.Tick += SleepTimer_Tick;
            _sleepTimer.Start();

            CancelSleepBtn.Visibility = Visibility.Visible;

            // Inject badge inside WebView2 — bypasses WebView2 airspace (WPF can't overlay WebView2 HWND)
            InjectSleepBadgeJs(minutes);
            ShowToast($"🌙 Sleep Timer set for {minutes}m");
        }

        private void InjectSleepBadgeJs(int minutes)
        {
            var label = Newtonsoft.Json.JsonConvert.SerializeObject("🌙 " + $"{minutes:D2}:00");
            var js = $@"(function(){{
    var e=document.getElementById('vixz-sleep-badge'); if(e) e.remove();
    var b=document.createElement('div');
    b.id='vixz-sleep-badge';
    b.style.cssText='position:fixed;top:14px;right:14px;z-index:2147483647;background:rgba(0,0,0,0.75);' +
        'border:1.5px solid #FFD700;border-radius:20px;padding:5px 14px;pointer-events:none;' +
        'font-family:system-ui,sans-serif;font-size:13px;font-weight:700;color:#FFD700;' +
        'letter-spacing:0.5px;box-shadow:0 2px 14px rgba(255,215,0,0.3);';
    b.textContent={label};
    document.body.appendChild(b);
}})();";
            _ = VideoWebView.ExecuteScriptAsync(js);
        }

        private void UpdateSleepBadgeJs(int mins, int secs)
        {
            var label = Newtonsoft.Json.JsonConvert.SerializeObject("🌙 " + $"{mins:D2}:{secs:D2}");
            _ = VideoWebView.ExecuteScriptAsync(
                $"(function(){{var b=document.getElementById('vixz-sleep-badge');if(b)b.textContent={label};}})();");
        }

        private void RemoveSleepBadgeJs()
        {
            _ = VideoWebView.ExecuteScriptAsync(
                "(function(){var b=document.getElementById('vixz-sleep-badge');if(b)b.remove();})();");
        }

        private void CancelSleepTimer_Click(object sender, RoutedEventArgs e)
        {
            _sleepTimer?.Stop();
            CancelSleepBtn.Visibility = Visibility.Collapsed;
            RemoveSleepBadgeJs();
            SleepTimerPopup.IsOpen = false;
            ShowToast("🌙 Sleep Timer Cancelled");
        }

        private void SleepTimer_Tick(object? sender, EventArgs e)
        {
            _sleepRemainingSeconds--;
            if (_sleepRemainingSeconds > 0)
            {
                var ts = TimeSpan.FromSeconds(_sleepRemainingSeconds);
                // Update badge inside WebView2
                UpdateSleepBadgeJs(ts.Minutes, ts.Seconds);
                // Keep WPF text in sync as fallback
                SleepCountdownText.Text = $"{ts.Minutes:D2}:{ts.Seconds:D2}";
            }
            else
            {
                _sleepTimer?.Stop();
                RemoveSleepBadgeJs();
                CancelSleepBtn.Visibility = Visibility.Collapsed;

                // Auto-pause video
                _ = VideoWebView.ExecuteScriptAsync("pauseVideo()");

                // Show 1-Click Resume Pill
                ResumeSleepText.Text = $"Resume for {_lastSleepDurationMinutes}m 🌙";
                ResumeSleepPill.Visibility = Visibility.Visible;
            }
        }

        private void ResumeSleepPill_MouseDown(object sender, MouseButtonEventArgs e)
        {
            ResumeSleepPill.Visibility = Visibility.Collapsed;
            _ = VideoWebView.ExecuteScriptAsync("playVideo()");
            ArmSleepTimer(_lastSleepDurationMinutes);
        }

        #endregion

        #region Window Controls & Keyboard Shortcuts

        private void TitleBar_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                if (e.ClickCount == 2)
                {
                    MaximizeToggle();
                }
                else
                {
                    DragMove();
                }
            }
        }

        private void PinButton_Click(object sender, RoutedEventArgs e)
        {
            _isAlwaysOnTop = !_isAlwaysOnTop;
            Topmost = _isAlwaysOnTop;
            PinButton.Foreground = _isAlwaysOnTop ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            ShowToast(_isAlwaysOnTop ? "📌 Always-on-Top Pinned" : "Unpinned");
        }

        protected override void OnStateChanged(EventArgs e)
        {
            base.OnStateChanged(e);
            if (MaximizeBtn != null)
            {
                MaximizeBtn.Content = WindowState == WindowState.Maximized ? "❐" : "▢";
            }
            if (WindowState == WindowState.Maximized)
            {
                if (MainBorder != null)
                {
                    MainBorder.CornerRadius = new CornerRadius(0);
                    MainBorder.BorderThickness = new Thickness(0);
                }
                if (TitleBarBorder != null)
                {
                    TitleBarBorder.CornerRadius = new CornerRadius(0);
                }
            }
            else
            {
                if (MainBorder != null)
                {
                    MainBorder.CornerRadius = new CornerRadius(14);
                    MainBorder.BorderThickness = new Thickness(1);
                }
                if (TitleBarBorder != null)
                {
                    TitleBarBorder.CornerRadius = new CornerRadius(14, 14, 0, 0);
                }
            }
        }

        private void MinimizeButton_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void MaximizeButton_Click(object sender, RoutedEventArgs e)
        {
            MaximizeToggle();
        }

        private void MaximizeToggle()
        {
            WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        }

        private void PlayerContainer_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ClickCount == 2)
            {
                MaximizeToggle();
            }
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void DevToolsBtn_Click(object sender, RoutedEventArgs e)
        {
            VideoWebView.CoreWebView2?.OpenDevToolsWindow();
        }

        private void FullscreenBtn_Click(object sender, RoutedEventArgs e)
        {
            ToggleFullscreen();
        }

        private void ToggleFullscreen()
        {
            _isCustomFullscreen = !_isCustomFullscreen;
            if (_isCustomFullscreen)
            {
                WindowState = WindowState.Maximized;
                SidebarCol.Width = new GridLength(0);
                TitleBarRow.Height = new GridLength(0);
            }
            else
            {
                WindowState = WindowState.Normal;
                SidebarCol.Width = new GridLength(210);
                TitleBarRow.Height = new GridLength(46);
            }
        }

        private async void Window_PreviewKeyDown(object sender, KeyEventArgs e)
        {
            var actualKey = e.Key == Key.System ? e.SystemKey : e.Key;
            if (actualKey == Key.F12)
            {
                e.Handled = true;
                VideoWebView.CoreWebView2?.OpenDevToolsWindow();
                return;
            }

            // Do not intercept hotkeys if typing inside any input text box
            if (Keyboard.FocusedElement is TextBox || 
                Keyboard.FocusedElement is PasswordBox || 
                SearchBox.IsFocused || 
                (AiPromptBox != null && AiPromptBox.IsFocused) || 
                (NewFolderInput != null && NewFolderInput.IsFocused))
            {
                return;
            }

            switch (e.Key)
            {
                case Key.Space:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("togglePlay();");
                    break;
                case Key.K:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("togglePlay();");
                    break;
                case Key.Left:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(-5);");
                    ShowToast("⏪ -5s");
                    break;
                case Key.Right:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(5);");
                    ShowToast("⏩ +5s");
                    break;
                case Key.J:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(-10);");
                    ShowToast("⏪ -10s");
                    break;
                case Key.L:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(10);");
                    ShowToast("⏩ +10s");
                    break;
                case Key.M:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("toggleMute();");
                    break;
                case Key.S:
                    e.Handled = true;
                    await CaptureScreenshotAsync();
                    break;
                case Key.D:
                    e.Handled = true;
                    await DownloadCurrentVideoAsync();
                    break;
                case Key.F:
                    e.Handled = true;
                    ToggleFullscreen();
                    break;
                case Key.T:
                    e.Handled = true;
                    PinButton_Click(this, new RoutedEventArgs());
                    break;
                case Key.N:
                    e.Handled = true;
                    PlayNextVideo();
                    break;
                case Key.P:
                    e.Handled = true;
                    PlayPreviousVideo();
                    break;
                case Key.F12:
                    e.Handled = true;
                    VideoWebView.CoreWebView2?.OpenDevToolsWindow();
                    break;
                case Key.Escape:
                    e.Handled = true;
                    if (SeriesHubPopup != null && SeriesHubPopup.IsOpen) SeriesHubPopup.IsOpen = false;
                    else if (SleepTimerPopup.IsOpen) SleepTimerPopup.IsOpen = false;
                    else if (FolderPopup.IsOpen) FolderPopup.IsOpen = false;
                    else if (_isCustomFullscreen) ToggleFullscreen();
                    else if (PlayerView.Visibility == Visibility.Visible) SwitchToFeedView();
                    break;
            }
        }

        private DispatcherTimer? _toastTimer;

        public void ShowToast(string message)
        {
            if (BottomToastText == null || BottomToastPill == null) return;

            BottomToastText.Text = message;
            BottomToastPill.Visibility = Visibility.Visible;

            _toastTimer?.Stop();
            _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(2000) };
            _toastTimer.Tick += (s, e) =>
            {
                BottomToastPill.Visibility = Visibility.Collapsed;
                _toastTimer.Stop();
            };
            _toastTimer.Start();
        }

        #endregion

        #region Auto-Fading Bottom Player Bar

        private DispatcherTimer? _bottomBarHideTimer;
        private bool _isMouseOverBottomBar = false;

        private void SetupBottomBarAutoFade()
        {
            _bottomBarHideTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1.5) };
            _bottomBarHideTimer.Tick += (s, e) =>
            {
                if (PlayerView.Visibility == Visibility.Visible && !_isMouseOverBottomBar)
                {
                    // If any popup is open, don't hide the bar
                    if ((SharePopup != null && SharePopup.IsOpen) ||
                        (DownloadChoicePopup != null && DownloadChoicePopup.IsOpen) ||
                        (QualityPopup != null && QualityPopup.IsOpen) ||
                        (SleepTimerPopup != null && SleepTimerPopup.IsOpen) ||
                        (FolderPopup != null && FolderPopup.IsOpen) ||
                        (AccountPopup != null && AccountPopup.IsOpen))
                    {
                        return;
                    }

                    AnimateBottomBar(0.0);
                }
            };
        }

        private void PlayerView_MouseMove(object sender, MouseEventArgs e)
        {
            if (PlayerView.Visibility == Visibility.Visible)
            {
                AnimateBottomBar(1.0);
                _bottomBarHideTimer?.Stop();
                if (!_isMouseOverBottomBar)
                {
                    _bottomBarHideTimer?.Start();
                }
            }
        }

        private void PlayerBottomBar_MouseEnter(object sender, MouseEventArgs e)
        {
            _isMouseOverBottomBar = true;
            _bottomBarHideTimer?.Stop();
            AnimateBottomBar(1.0);
        }

        private void PlayerBottomBar_MouseLeave(object sender, MouseEventArgs e)
        {
            _isMouseOverBottomBar = false;
            _bottomBarHideTimer?.Stop();
            _bottomBarHideTimer?.Start();
        }

        private void AnimateBottomBar(double targetOpacity)
        {
            if (PlayerBottomBar == null) return;
            if (Math.Abs(PlayerBottomBar.Opacity - targetOpacity) < 0.01) return;

            var anim = new DoubleAnimation(targetOpacity, TimeSpan.FromMilliseconds(220));
            PlayerBottomBar.BeginAnimation(OpacityProperty, anim);
        }

        #endregion

        #region Collapsible Sidebar

        private void SidebarToggle_Click(object sender, RoutedEventArgs e)
        {
            StorageService.Settings.IsSidebarCollapsed = !StorageService.Settings.IsSidebarCollapsed;
            StorageService.Save();
            ApplySidebarState();
            ShowToast(StorageService.Settings.IsSidebarCollapsed ? "◀ Sidebar Folded" : "☰ Sidebar Expanded");
        }

        private void ApplySidebarState()
        {
            if (SidebarCol == null) return;
            double targetWidth = StorageService.Settings.IsSidebarCollapsed ? 0 : 210;

            if (SidebarToggleBtn != null)
            {
                SidebarToggleBtn.Content = StorageService.Settings.IsSidebarCollapsed ? "▶" : "☰";
                SidebarToggleBtn.ToolTip = StorageService.Settings.IsSidebarCollapsed ? "Expand Sidebar Menu" : "Fold Sidebar Menu";
                SidebarToggleBtn.Foreground = StorageService.Settings.IsSidebarCollapsed ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            }

            SidebarCol.Width = new GridLength(targetWidth);
        }

        #endregion

        #region Dynamic Ambient Lighting Glow

        private void AmbientGlowBtn_Click(object sender, RoutedEventArgs e)
        {
            StorageService.Settings.IsAmbientGlowEnabled = !StorageService.Settings.IsAmbientGlowEnabled;
            StorageService.Save();
            ApplyAmbientGlowState();
            ShowToast(StorageService.Settings.IsAmbientGlowEnabled ? "💡 Ambient Glow: ON" : "💡 Ambient Glow: OFF");
        }

        private void ApplyAmbientGlowState()
        {
            if (AmbientGlowBorder != null)
            {
                AmbientGlowBorder.Visibility = StorageService.Settings.IsAmbientGlowEnabled ? Visibility.Visible : Visibility.Collapsed;
            }
            if (AmbientGlowBtn != null)
            {
                AmbientGlowBtn.Foreground = StorageService.Settings.IsAmbientGlowEnabled ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.Gray;
            }
            UpdateAmbientGlowFromVideo(_currentVideo);
        }

        private void UpdateAmbientGlowFromVideo(VideoItem? video)
        {
            if (video == null) return;

            // Generate an adaptive cinematic color palette for the ambient glow
            var hash = Math.Abs((video.Title + video.ChannelTitle).GetHashCode());
            byte r = (byte)(80 + (hash % 160));
            byte g = (byte)(35 + ((hash / 13) % 130));
            byte b = (byte)(90 + ((hash / 17) % 150));
            var colorHex = $"#{r:X2}{g:X2}{b:X2}";

            if (AmbientGlowBorder != null)
            {
                AmbientGlowBorder.Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromArgb(95, r, g, b));
            }

            if (VideoWebView?.CoreWebView2 != null)
            {
                _ = VideoWebView.ExecuteScriptAsync($"setAmbientGlow({StorageService.Settings.IsAmbientGlowEnabled.ToString().ToLower()}, '{colorHex}');");
            }

            if (PlayerView != null)
            {
                // Always keep PlayerView 100% opaque (Alpha = 255) so background feed never bleeds through
                byte bgR = (byte)Math.Clamp(9 + (r * 0.12), 0, 255);
                byte bgG = (byte)Math.Clamp(9 + (g * 0.12), 0, 255);
                byte bgB = (byte)Math.Clamp(12 + (b * 0.12), 0, 255);
                PlayerView.Background = StorageService.Settings.IsAmbientGlowEnabled 
                    ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(bgR, bgG, bgB))
                    : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(9, 9, 12));
            }
        }

        #endregion

        #region Subscription Folder Groupings

        private string _activeSubscriptionFolder = "All";

        private async void SubscriptionFolderChip_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string folderName)
            {
                _activeSubscriptionFolder = folderName;
                UpdateFolderChipHighlights();

                if (folderName == "All")
                {
                    SubscribedChannelsList.ItemsSource = UserProfileData.SubscribedChannels;
                    _isDiscoveryFeed = true;
                    SwitchToFeedView();
                    await LoadFeedAsync("🔔 Subscriptions Feed", () => YouTubeService.GetSubscribedFeedAsync(forceRefresh: true));
                }
                else if (StorageService.Settings.SubscriptionFolders.TryGetValue(folderName, out var channels))
                {
                    var matched = UserProfileData.SubscribedChannels
                        .Where(c => channels.Any(ch => c.IndexOf(ch, StringComparison.OrdinalIgnoreCase) >= 0 || ch.IndexOf(c, StringComparison.OrdinalIgnoreCase) >= 0))
                        .ToList();
                    
                    if (matched.Count == 0)
                    {
                        matched = channels;
                    }
                    SubscribedChannelsList.ItemsSource = matched;
                    _isDiscoveryFeed = true;
                    SwitchToFeedView();
                    await LoadFeedAsync($"📁 {folderName} Feed", () => YouTubeService.FetchSubscribedProfileFeedAsync(matched, 0, 150, forceRefresh: true));
                }
                ShowToast($"📁 Folder: {folderName}");
            }
        }

        private void UpdateFolderChipHighlights()
        {
            var gold = (System.Windows.Media.Brush)FindResource("AccentGold");
            var normal = (System.Windows.Media.Brush)FindResource("TextSecondary");

            if (FolderChipAll != null) FolderChipAll.Foreground = _activeSubscriptionFolder == "All" ? gold : normal;
            if (FolderChipAi != null) FolderChipAi.Foreground = _activeSubscriptionFolder == "AI & Tech" ? gold : normal;
            if (FolderChipPodcasts != null) FolderChipPodcasts.Foreground = _activeSubscriptionFolder == "Podcasts" ? gold : normal;
            if (FolderChipGaming != null) FolderChipGaming.Foreground = _activeSubscriptionFolder == "Gaming" ? gold : normal;
            if (FolderChipMusic != null) FolderChipMusic.Foreground = _activeSubscriptionFolder == "Music" ? gold : normal;
        }

        #endregion

        #region Video Quality

        private void QualityBtn_Click(object sender, RoutedEventArgs e)
        {
            QualityPopup.IsOpen = !QualityPopup.IsOpen;
        }

        private async void QualityOption_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string q)
            {
                QualityPopup.IsOpen = false;
                StorageService.SetPreferredQuality(q);
                UpdateQualityButtonText(q);
                await VideoWebView.ExecuteScriptAsync($"setQuality('{q}')");
                ShowToast($"📺 Quality set to {QualityBtn.Content}");
            }
        }

        #endregion
    }
}
