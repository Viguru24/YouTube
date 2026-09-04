using System;
using System.IO;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;
using VixzDesktop.Models;
using VixzDesktop.Services;

namespace VixzDesktop
{
    public partial class PopOutPlayerWindow : Window
    {
        private readonly MainWindow _mainWindow;
        private VideoItem _video;
        private double _currentPosition = 0;
        private DispatcherTimer? _sleepTimer;
        private int _sleepRemainingSeconds = 0;

        public PopOutPlayerWindow(MainWindow mainWindow, VideoItem video, double startPositionSeconds = 0)
        {
            InitializeComponent();
            _mainWindow = mainWindow;
            _video = video;
            _currentPosition = startPositionSeconds;

            MiniVideoTitle.Text = video.Title;
            UpdateFavoriteUi();

            Loaded += PopOutPlayerWindow_Loaded;
            Closing += PopOutPlayerWindow_Closing;
        }

        private DispatcherTimer? _footerHideTimer;

        private void FooterOverlay_MouseEnter(object sender, MouseEventArgs e)
        {
            var anim = new System.Windows.Media.Animation.DoubleAnimation(0.05, 1.0, TimeSpan.FromMilliseconds(150));
            FooterOverlay?.BeginAnimation(OpacityProperty, anim);

            _footerHideTimer?.Stop();
            _footerHideTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2) };
            _footerHideTimer.Tick += (s, ev) =>
            {
                _footerHideTimer.Stop();
                if (FooterOverlay != null && !FooterOverlay.IsMouseOver)
                {
                    var fadeOut = new System.Windows.Media.Animation.DoubleAnimation(1.0, 0.05, TimeSpan.FromMilliseconds(300));
                    FooterOverlay.BeginAnimation(OpacityProperty, fadeOut);
                }
            };
            _footerHideTimer.Start();
        }

        private void FooterOverlay_MouseLeave(object sender, MouseEventArgs e)
        {
            var anim = new System.Windows.Media.Animation.DoubleAnimation(1.0, 0.05, TimeSpan.FromMilliseconds(250));
            FooterOverlay?.BeginAnimation(OpacityProperty, anim);
        }

        private async void PopOutPlayerWindow_Loaded(object sender, RoutedEventArgs e)
        {
            await InitializeMiniWebViewAsync();
        }

        private async Task InitializeMiniWebViewAsync()
        {
            try
            {
                var env = await WebViewManager.GetEnvironmentAsync();
                await MiniWebView.EnsureCoreWebView2Async(env);
                await WebViewManager.MaskWebViewIndicatorsAsync(MiniWebView.CoreWebView2);

                MiniWebView.CoreWebView2.Settings.IsWebMessageEnabled = true;
                MiniWebView.CoreWebView2.Settings.AreDefaultScriptDialogsEnabled = true;
                MiniWebView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                MiniWebView.CoreWebView2.Settings.UserAgent = WebViewManager.CommonUserAgent;

                MiniWebView.CoreWebView2.WebMessageReceived += MiniWebView_WebMessageReceived;

                var webAssets = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    "VixzDesktop", "WebAssets"
                );
                if (!Directory.Exists(webAssets)) Directory.CreateDirectory(webAssets);

                MiniWebView.CoreWebView2.SetVirtualHostNameToFolderMapping(
                    "vixz.app",
                    webAssets,
                    CoreWebView2HostResourceAccessKind.Allow
                );

                // Intercept and mock ad requests with 200 OK to prevent ERR_CONNECTION_REFUSED
                MiniWebView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);


                MiniWebView.CoreWebView2.WebResourceRequested += (s, args) =>
                {
                    try
                    {
                        var uri = args.Request.Uri.ToLowerInvariant();
                        // Only intercept external tracking ad domains, never tamper with youtube.com internal integrity/pagead scripts
                        if (!uri.Contains("youtube.com") && (uri.Contains("doubleclick") || uri.Contains("googleads") || uri.Contains("viewthroughconversion") || uri.Contains("ad_status") || uri.Contains("favicon.ico")))
                        {
                            string origin = "*";
                            try
                            {
                                if (args.Request.Headers.Contains("Origin"))
                                {
                                    origin = args.Request.Headers.GetHeader("Origin");
                                    if (string.IsNullOrWhiteSpace(origin)) origin = "*";
                                }
                            }
                            catch { }

                            string contentType = "text/plain";
                            byte[] bodyBytes = Array.Empty<byte>();

                            if (uri.Contains(".js") || uri.Contains("ad_status"))
                            {
                                contentType = "application/javascript";
                                bodyBytes = System.Text.Encoding.UTF8.GetBytes("/* mock ad script */\nwindow.google_ad_status = 1;\n");
                            }
                            else if (uri.Contains("favicon.ico"))
                            {
                                contentType = "image/x-icon";
                                bodyBytes = new byte[] { 0, 0, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 32, 0, 68, 0, 0, 0, 22, 0, 0, 0, 40, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 1, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
                            }
                            else
                            {
                                contentType = "application/json";
                                bodyBytes = System.Text.Encoding.UTF8.GetBytes("{\"status\":\"ok\",\"id\":\"0\"}");
                            }

                            var headers = $"Content-Type: {contentType}\r\nAccess-Control-Allow-Origin: {origin}\r\nAccess-Control-Allow-Credentials: true\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\nAccess-Control-Allow-Headers: *";
                            var emptyStream = new MemoryStream(bodyBytes);
                            args.Response = MiniWebView.CoreWebView2.Environment.CreateWebResourceResponse(
                                emptyStream,
                                200,
                                "OK",
                                headers
                            );
                        }
                    }
                    catch { }
                };

                // Intercept 'More videos' and external links so they play inside Vixz instead of opening external browsers
                MiniWebView.CoreWebView2.NewWindowRequested += async (s, args) =>
                {
                    args.Handled = true;
                    if (args.Uri.Contains("accounts.google.com") || args.Uri.Contains("/signin") || args.Uri.Contains("ServiceLogin"))
                    {
                        Dispatcher.Invoke(() =>
                        {
                            var win = new SignInWindow { Owner = this };
                            if (win.ShowDialog() == true || win.IsSuccess)
                            {
                                _mainWindow.UpdateAccountUi();
                                MiniWebView.CoreWebView2.Reload();
                            }
                        });
                        return;
                    }

                    var vid = MainWindow.ExtractYouTubeVideoId(args.Uri);
                    if (!string.IsNullOrEmpty(vid))
                    {
                        _currentPosition = 0;
                        _video = await YouTubeService.GetVideoDetailsAsync(vid) ?? new VideoItem
                        {
                            Id = vid,
                            Title = "YouTube Video",
                            ChannelTitle = "YouTube",
                            ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                        };
                        MiniVideoTitle.Text = _video.Title;
                        UpdateFavoriteUi();
                        MiniWebView.CoreWebView2.Navigate($"https://vixz.app/player.html?v={vid}&t=0");
                    }
                };

                MiniWebView.CoreWebView2.NavigationStarting += async (s, args) =>
                {
                    if (args.Uri.StartsWith("https://vixz.app", StringComparison.OrdinalIgnoreCase))
                    {
                        return;
                    }

                    args.Cancel = true;
                    if (args.Uri.Contains("accounts.google.com") || args.Uri.Contains("/signin") || args.Uri.Contains("ServiceLogin"))
                    {
                        Dispatcher.Invoke(() =>
                        {
                            var win = new SignInWindow { Owner = this };
                            if (win.ShowDialog() == true || win.IsSuccess)
                            {
                                _mainWindow.UpdateAccountUi();
                                MiniWebView.CoreWebView2.Reload();
                            }
                        });
                        return;
                    }

                    var vid = MainWindow.ExtractYouTubeVideoId(args.Uri);
                    if (!string.IsNullOrEmpty(vid))
                    {
                        _currentPosition = 0;
                        _video = await YouTubeService.GetVideoDetailsAsync(vid) ?? new VideoItem
                        {
                            Id = vid,
                            Title = "YouTube Video",
                            ChannelTitle = "YouTube",
                            ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                        };
                        MiniVideoTitle.Text = _video.Title;
                        UpdateFavoriteUi();
                        MiniWebView.CoreWebView2.Navigate($"https://vixz.app/player.html?v={vid}&t=0");
                    }
                };

                var startSec = Math.Max(0, (int)_currentPosition);
                var prefQuality = StorageService.Settings.PreferredQuality ?? "hd1080";
                QualityBtn.Content = prefQuality == "hd1080" ? "HD" : (prefQuality == "hd720" ? "720" : "Auto");
                MiniWebView.CoreWebView2.Navigate($"https://vixz.app/player.html?v={_video.Id}&t={startSec.ToString(System.Globalization.CultureInfo.InvariantCulture)}&vq={prefQuality}");

                _ = Task.Run(async () =>
                {
                    var segs = await SponsorBlockService.GetSegmentsAsync(_video.Id);
                    if (segs.Count > 0)
                    {
                        var segsList = segs.Select(s => new { start = s.StartTime, end = s.EndTime, category = s.Category }).ToList();
                        var json = Newtonsoft.Json.JsonConvert.SerializeObject(segsList);
                        await Dispatcher.InvokeAsync(async () => await MiniWebView.ExecuteScriptAsync($"setSponsorSegments({json})"));
                    }
                });
            }
            catch (Exception ex)
            {
                var msg = ex.Message.Contains("Class not registered", StringComparison.OrdinalIgnoreCase)
                    ? "The pop-out player requires the Microsoft Edge WebView2 Runtime, which doesn't appear to be installed on this machine.\n\nPlease download it from:\nhttps://developer.microsoft.com/microsoft-edge/webview2/\n\nAfter installing, restart Vixz and try again."
                    : $"Pop-out player could not start: {ex.Message}";

                MessageBox.Show(msg, "Pop-Out Player Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                Close();
            }
        }

        private void MiniWebView_WebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
        {
            var msg = e.TryGetWebMessageAsString();
            if (string.IsNullOrEmpty(msg)) return;

            if (msg.StartsWith("POS:"))
            {
                var parts = msg.Split(':');
                if (parts.Length == 3 && double.TryParse(parts[2], System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out var sec))
                {
                    _currentPosition = sec;
                    StorageService.SavePlaybackPosition(parts[1], sec);
                }
            }
            else if (msg.StartsWith("SPONSOR_SKIPPED:"))
            {
                var cat = msg.Substring("SPONSOR_SKIPPED:".Length);
                _mainWindow.ShowToast($"⏭️ Skipped {cat} (in-video ad)");
            }
            else if (msg == "VIDEO_ENDED")
            {
                _mainWindow.PlayNextVideo();
            }
        }

        private void TitleBar_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                DragMove();
            }
        }

        private void PinBtn_Click(object sender, RoutedEventArgs e)
        {
            Topmost = !Topmost;
            PinBtn.Foreground = Topmost ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.Gray;
        }

        private async void DockBackBtn_Click(object sender, RoutedEventArgs e)
        {
            await DockBackToMainWindowAsync();
        }

        private async void CloseBtn_Click(object sender, RoutedEventArgs e)
        {
            await DockBackToMainWindowAsync();
        }

        public async Task DockBackToMainWindowAsync()
        {
            try
            {
                if (MiniWebView.CoreWebView2 != null)
                {
                    var timeStr = await MiniWebView.ExecuteScriptAsync("getCurrentTime()");
                    if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out var sec))
                    {
                        _currentPosition = sec;
                    }
                }
            }
            catch { }

            _mainWindow.ReturnFromPopOut(_video, _currentPosition);
            Close();
        }

        private void PopOutPlayerWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            _sleepTimer?.Stop();
        }

        #region Player Controls

        private async void SeekMinus10_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteScriptSafeAsync("seek(-10)");
        }

        private async void SeekMinus5_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteScriptSafeAsync("seek(-5)");
        }

        private async void PlayPause_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteScriptSafeAsync("togglePlay()");
        }

        private async void SeekPlus5_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteScriptSafeAsync("seek(5)");
        }

        private async void SeekPlus10_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteScriptSafeAsync("seek(10)");
        }

        private async void Screenshot_Click(object sender, RoutedEventArgs e)
        {
            double curSec = _currentPosition;
            try
            {
                var timeStr = await MiniWebView.ExecuteScriptAsync("getCurrentTime()");
                double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out curSec);
            }
            catch { }

            var path = await ScreenshotService.CaptureAndSaveAsync(
                MiniWebView,
                this,
                _video.Title,
                curSec,
                StorageService.Settings.ActiveScreenshotFolder
            );

            if (path != null)
            {
                _mainWindow.ShowToast("📸 Screenshot saved from Pop-out!");
            }
        }

        private void Sleep_Click(object sender, RoutedEventArgs e)
        {
            if (_sleepTimer != null && _sleepTimer.IsEnabled)
            {
                _sleepTimer.Stop();
                _sleepTimer = null;
                _mainWindow.ShowToast("🌙 Sleep timer cancelled");
                return;
            }

            _sleepRemainingSeconds = 30 * 60;
            _sleepTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
            _sleepTimer.Tick += async (s, ev) =>
            {
                _sleepRemainingSeconds--;
                if (_sleepRemainingSeconds <= 0)
                {
                    _sleepTimer.Stop();
                    await ExecuteScriptSafeAsync("if (player && typeof player.pauseVideo === 'function') player.pauseVideo();");
                    _mainWindow.ShowToast("🌙 Sleep timer ended - playback paused");
                }
            };
            _sleepTimer.Start();
            _mainWindow.ShowToast("🌙 Sleep timer set for 30 minutes");
        }

        private async void Quality_Click(object sender, RoutedEventArgs e)
        {
            var current = StorageService.Settings.PreferredQuality ?? "hd1080";
            var next = current == "hd1080" ? "hd720" : (current == "hd720" ? "default" : "hd1080");
            StorageService.SetPreferredQuality(next);
            QualityBtn.Content = next == "hd1080" ? "HD" : (next == "hd720" ? "720" : "Auto");
            await ExecuteScriptSafeAsync($"setQuality('{next}')");
            _mainWindow.ShowToast($"📺 Quality set to {QualityBtn.Content}");
        }

        private void ThumbsUp_Click(object sender, RoutedEventArgs e)
        {
            if (StorageService.IsDisliked(_video.Id))
            {
                StorageService.RemoveDislike(_video.Id);
            }
            StorageService.ToggleFavorite(_video);
            UpdateFavoriteUi();
            _mainWindow.ShowToast(_video.IsFavorite ? "👍 Liked! Creator boosted in algorithm." : "Removed Like");
        }

        private void ThumbsDown_Click(object sender, RoutedEventArgs e)
        {
            StorageService.AddDislike(_video);
            UpdateFavoriteUi();
            _mainWindow.ShowToast("👎 Disliked! Video removed & deprioritized.");
            _mainWindow.PlayNextVideo();
        }

        private void Favorite_Click(object sender, RoutedEventArgs e)
        {
            StorageService.ToggleFavorite(_video);
            UpdateFavoriteUi();
            _mainWindow.ShowToast(_video.IsFavorite ? "⭐ Added to Favorites" : "Removed from Favorites");
        }

        private void UpdateFavoriteUi()
        {
            var isDisliked = StorageService.IsDisliked(_video.Id);
            if (FavBtn != null)
            {
                FavBtn.Foreground = _video.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            }
            if (ThumbsUpBtn != null)
            {
                ThumbsUpBtn.Foreground = _video.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            }
            if (ThumbsDownBtn != null)
            {
                ThumbsDownBtn.Foreground = isDisliked ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;
            }
        }

        private void NextVideo_Click(object sender, RoutedEventArgs e)
        {
            _mainWindow.PlayNextVideo();
        }

        private async Task ExecuteScriptSafeAsync(string script)
        {
            try
            {
                if (MiniWebView.CoreWebView2 != null)
                {
                    await MiniWebView.ExecuteScriptAsync(script);
                }
            }
            catch { }
        }

        #endregion
    }
}
