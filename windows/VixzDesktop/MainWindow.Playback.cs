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
        #region Video Player

        private async void VideoCard_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton != MouseButton.Left) return;

            // Prevent video playback if click originated from interactive child buttons (Like, Dislike, Delete, etc.)
            if (e.OriginalSource is DependencyObject dep)
            {
                DependencyObject? current = dep;
                while (current != null && current != sender)
                {
                    if (current is Button)
                    {
                        return;
                    }
                    current = VisualTreeHelper.GetParent(current);
                }
            }

            if (sender is FrameworkElement elem && elem.DataContext is VideoItem video)
            {
                if (video.DurationText.Contains("Playlist") || video.Id.StartsWith("PL") || video.Id.StartsWith("OLAK5uy_"))
                {
                    await LoadPlaylistFeedAsync(video.Id, video.Title);
                    return;
                }
                _currentVideoIndex = _currentFeed.IndexOf(video);
                await PlayVideoAsync(video);
            }
        }

        private async Task PlayLocalVideoInWebViewAsync(string filePath, string videoId, double startSec = 0)
        {
            if (VideoWebView.CoreWebView2 == null)
            {
                await InitializeWebViewAsync();
            }

            var dir = System.IO.Path.GetDirectoryName(filePath);
            var fileName = System.IO.Path.GetFileName(filePath);
            if (!string.IsNullOrEmpty(dir) && System.IO.Directory.Exists(dir))
            {
                try
                {
                    VideoWebView.CoreWebView2?.SetVirtualHostNameToFolderMapping(
                        "downloads.vixz",
                        dir,
                        Microsoft.Web.WebView2.Core.CoreWebView2HostResourceAccessKind.Allow
                    );
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[WebView2] Host mapping error: {ex.Message}");
                }
            }

            var currentSrc = VideoWebView.Source?.ToString() ?? "";
            if (!currentSrc.Contains("vixz.app/player.html"))
            {
                VideoWebView.CoreWebView2?.Navigate("https://vixz.app/player.html");
                for (int i = 0; i < 20; i++)
                {
                    await Task.Delay(100);
                    var ready = await VideoWebView.ExecuteScriptAsync("document.readyState");
                    if (ready != null && ready.Contains("complete")) break;
                }
            }

            var mediaUrl = $"https://downloads.vixz/{Uri.EscapeDataString(fileName)}";
            await VideoWebView.ExecuteScriptAsync($"loadLocalVideo('{mediaUrl}', {startSec.ToString(System.Globalization.CultureInfo.InvariantCulture)}, '{videoId}')");
            UpdateQualityButtonText("Offline MP4");
        }

        public async Task PlayDirectWatchPageAsync(string videoId, double startSec)
        {
            if (VideoWebView.CoreWebView2 == null)
            {
                await InitializeWebViewAsync();
            }

            _isDirectWatchMode = true;
            _knownDirectWatchVideos.Add(videoId);

            FeedView.Visibility = Visibility.Collapsed;
            PlayerView.Visibility = Visibility.Visible;
            AnimateBottomBar(1.0);
            _bottomBarHideTimer?.Stop();
            _bottomBarHideTimer?.Start();

            ShowToast("🛡️ Restriction bypassed: Playing stream");

            var secInt = Math.Max(0, (int)startSec);
            var targetUrl = $"https://www.youtube.com/watch?v={videoId}" + (secInt > 3 ? $"&t={secInt}" : "");
            VideoWebView.CoreWebView2?.Navigate(targetUrl);
        }

        private async Task PlayVideoAsync(VideoItem video, double? resumePos = null)
        {
            SpeechService.Instance.StopSpeaking();
            // Check if this is a local downloaded file
            string? localFilePath = null;
            try
            {
                if (!string.IsNullOrWhiteSpace(video.LocalFilePath) && System.IO.File.Exists(video.LocalFilePath))
                {
                    localFilePath = video.LocalFilePath;
                }
                else
                {
                    var savedDl = StorageService.Settings.Downloads.FirstOrDefault(d => d.Id.Equals(video.Id, StringComparison.OrdinalIgnoreCase));
                    if (savedDl != null && !string.IsNullOrWhiteSpace(savedDl.LocalFilePath) && System.IO.File.Exists(savedDl.LocalFilePath))
                    {
                        localFilePath = savedDl.LocalFilePath;
                    }
                }

                if (string.IsNullOrWhiteSpace(localFilePath))
                {
                    var folder = DownloadService.GetDownloadsFolder();
                    if (System.IO.Directory.Exists(folder))
                    {
                        var matchingFiles = System.IO.Directory.GetFiles(folder, $"*{video.Id}*.*")
                            .Where(f => f.EndsWith(".mp4", StringComparison.OrdinalIgnoreCase) ||
                                        f.EndsWith(".m4a", StringComparison.OrdinalIgnoreCase) ||
                                        f.EndsWith(".mp3", StringComparison.OrdinalIgnoreCase) ||
                                        f.EndsWith(".webm", StringComparison.OrdinalIgnoreCase) ||
                                        f.EndsWith(".mkv", StringComparison.OrdinalIgnoreCase))
                            .ToArray();
                        if (matchingFiles.Length > 0 && System.IO.File.Exists(matchingFiles[0]))
                        {
                            localFilePath = matchingFiles[0];
                        }
                    }
                }
            }
            catch { }

            _currentVideo = video;
            StorageService.AddHistory(video);

            // Update feed items
            if (FeedTitleText?.Text == "📜 Watch History")
            {
                // Move video to top of current Watch History feed
                _rawUnfilteredFeed?.RemoveAll(v => v.Id == video.Id);
                _rawUnfilteredFeed?.Insert(0, video);
                _currentFeed?.RemoveAll(v => v.Id == video.Id);
                _currentFeed?.Insert(0, video);
                if (VideoItemsControl != null)
                {
                    VideoItemsControl.ItemsSource = null;
                    VideoItemsControl.ItemsSource = _currentFeed;
                }
            }
            else if (FeedTitleText?.Text != "💾 Downloaded Videos & Audio" && (FeedTitleText == null || !FeedTitleText.Text.StartsWith("📺")) && (FeedTitleText == null || !FeedTitleText.Text.StartsWith("📋")))
            {
                // Drop the video from the discovery/recommendation feed immediately
                _rawUnfilteredFeed?.RemoveAll(v => v.Id == video.Id);
                _currentFeed?.RemoveAll(v => v.Id == video.Id);
                if (VideoItemsControl != null)
                {
                    VideoItemsControl.ItemsSource = null;
                    VideoItemsControl.ItemsSource = _currentFeed;
                }
            }

            CurrentVideoTitle.Text = video.Title;
            CurrentVideoChannel.Text = video.ChannelTitle;
            CurrentVideoDate.Text = !string.IsNullOrWhiteSpace(video.DisplayTimestamp) ? $" • {video.DisplayTimestamp}" : (!string.IsNullOrWhiteSpace(video.UploadDateText) ? $" • {video.UploadDateText}" : "");
            CurrentVideoViews.Text = !string.IsNullOrWhiteSpace(video.DisplayViews) ? $" • {video.DisplayViews}" : "";
            UpdateSubscribeToggleBtn();

            // If date is missing, fetch full details asynchronously
            if (string.IsNullOrWhiteSpace(video.UploadDateText))
            {
                _ = Task.Run(async () =>
                {
                    var details = await YouTubeService.GetVideoDetailsAsync(video.Id);
                    if (details != null && !string.IsNullOrWhiteSpace(details.UploadDateText))
                    {
                        Dispatcher.Invoke(() =>
                        {
                            if (_currentVideo?.Id == video.Id)
                            {
                                video.UploadDateText = details.UploadDateText;
                                CurrentVideoDate.Text = $" • {video.DisplayTimestamp}";
                                if (!string.IsNullOrWhiteSpace(details.ViewCountText))
                                {
                                    video.ViewCountText = details.ViewCountText;
                                    CurrentVideoViews.Text = $" • {video.DisplayViews}";
                                }
                            }
                        });
                    }
                });
            }

            var isDisliked = StorageService.IsDisliked(video.Id);
            ThumbsUpBtn.Foreground = video.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            ThumbsDownBtn.Foreground = isDisliked ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;
            FavBtn.Foreground = video.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            WatchLaterBtn.Foreground = video.IsWatchLater ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;

            FeedView.Visibility = Visibility.Collapsed;
            PlayerView.Visibility = Visibility.Visible;
            UpdateAmbientGlowFromVideo(video);
            AnimateBottomBar(1.0);
            _bottomBarHideTimer?.Stop();
            _bottomBarHideTimer?.Start();

            if (VideoWebView.CoreWebView2 == null)
            {
                await InitializeWebViewAsync();
            }

            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = false;
            }

            var savedPos = resumePos ?? StorageService.GetPlaybackPosition(video.Id);

            // Reset player timeline & scrubber
            _currentVideoDuration = video.Duration?.TotalSeconds ?? 0;
            _currentVideoPosition = savedPos;
            if (PlayerDurationText != null) PlayerDurationText.Text = _currentVideoDuration > 0 ? FormatSeconds(_currentVideoDuration) : "0:00";
            if (PlayerCurrentTimeText != null) PlayerCurrentTimeText.Text = FormatSeconds(savedPos);
            if (PlayerProgressBar != null) PlayerProgressBar.Value = _currentVideoDuration > 0 ? (savedPos / _currentVideoDuration) * 100 : 0;
            if (PlayerScrubberSlider != null) PlayerScrubberSlider.Value = PlayerProgressBar?.Value ?? 0;

            // Reset Binge mode overlay
            _isBingeCardDismissedForCurrentVideo = false;
            _bingeTimer?.Stop();
            if (NextEpisodeCard != null) NextEpisodeCard.Visibility = Visibility.Collapsed;

            // Load SponsorBlock segments in background
            _activeSponsorSegments = await SponsorBlockService.GetSegmentsAsync(video.Id);
            RenderSponsorBlockSegments(SponsorBlockCanvas?.ActualWidth ?? 0);

            // Reset transcript state
            _allTranscriptCues.Clear();
            _filteredTranscriptCues.Clear();
            _currentActiveCue = null;
            if (TranscriptDrawer != null && TranscriptDrawer.Visibility == Visibility.Visible)
            {
                _ = LoadTranscriptForCurrentVideoAsync();
            }

            if (savedPos > 3)
            {
                var ts = TimeSpan.FromSeconds(savedPos);
                var timeFormatted = ts.Hours > 0 ? $"{ts.Hours}:{ts.Minutes:D2}:{ts.Seconds:D2}" : $"{ts.Minutes:D2}:{ts.Seconds:D2}";
                ShowToast($"▶ Resuming from {timeFormatted}");
            }

            if (!string.IsNullOrWhiteSpace(localFilePath))
            {
                video.LocalFilePath = localFilePath;
                video.IsDownloaded = true;
                _isDirectWatchMode = false;
                await PlayLocalVideoInWebViewAsync(localFilePath, video.Id, savedPos);
                ShowToast($"⚡ Playing offline download: {video.Title}");
                return;
            }

            if (_knownDirectWatchVideos.Contains(video.Id))
            {
                await PlayDirectWatchPageAsync(video.Id, savedPos);
                return;
            }

            _isDirectWatchMode = false;

            var prefQuality = StorageService.Settings.PreferredQuality ?? "hd1080";

            // If Jev is enabled, dynamically arbitrate optimal quality based on player viewport size
            if (JevService.IsConfiguredAndEnabled)
            {
                try
                {
                    var w = (int)Math.Max(ActualWidth, 640);
                    var h = (int)Math.Max(ActualHeight, 360);
                    var available = new List<string> { "hd1080", "hd720", "large", "medium" };
                    var jevQuality = await JevService.DecideOptimalQualityAsync(w, h, prefQuality, available);
                    if (!string.IsNullOrEmpty(jevQuality))
                    {
                        prefQuality = jevQuality;
                    }
                }
                catch { }
            }

            UpdateQualityButtonText(prefQuality);

            var currentSrc = VideoWebView.Source?.ToString() ?? "";
            var targetUrl = $"https://vixz.app/player.html?v={video.Id}&t={savedPos.ToString(System.Globalization.CultureInfo.InvariantCulture)}&vq={prefQuality}";
            if (currentSrc.Contains("vixz.app/player.html"))
            {
                var canLoad = await VideoWebView.ExecuteScriptAsync("typeof window.loadVideo === 'function'");
                if (canLoad == "true")
                {
                    await VideoWebView.ExecuteScriptAsync($"loadVideo('{video.Id}', {savedPos.ToString(System.Globalization.CultureInfo.InvariantCulture)}, '{prefQuality}')");
                }
                else
                {
                    VideoWebView.CoreWebView2?.Navigate(targetUrl);
                }
            }
            else
            {
                VideoWebView.CoreWebView2?.Navigate(targetUrl);
            }

            // Inject SponsorBlock segments directly into player engine
            if (_activeSponsorSegments != null && _activeSponsorSegments.Count > 0)
            {
                var segsList = _activeSponsorSegments.Select(s => new { start = s.StartTime, end = s.EndTime, category = s.Category }).ToList();
                var json = Newtonsoft.Json.JsonConvert.SerializeObject(segsList);
                await VideoWebView.ExecuteScriptAsync($"setSponsorSegments({json})");
            }
            else
            {
                await VideoWebView.ExecuteScriptAsync("setSponsorSegments([])");
            }

            // Start SponsorBlock monitor
            StartSponsorBlockMonitor();
        }

        private async void SeekMinus10_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(-10);");
            ShowToast("⏪ -10s");
        }

        private async void SeekMinus5_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(-5);");
            ShowToast("⏪ -5s");
        }

        private async void SeekPlus5_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(5);");
            ShowToast("⏩ +5s");
        }

        private async void SeekPlus10_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(10);");
            ShowToast("⏩ +10s");
        }

        private async void PopOutPlayer_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null)
            {
                ShowToast("No video currently loaded");
                return;
            }

            // 1. Synchronously mute main window WebView2 audio stream on line 1 before any async calls
            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = true;
            }

            double curSec = 0;
            try
            {
                var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out curSec);
                await VideoWebView.ExecuteScriptAsync("document.querySelectorAll('video, audio').forEach(m => { m.pause(); m.muted = true; }); if (window.player && typeof window.player.pauseVideo === 'function') window.player.pauseVideo();");
            }
            catch { }

            // Mute Main WebView2 natively at Chromium host level to guarantee 0 duplicate sound
            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = true;
            }

            _popOutWindow?.Close();
            _popOutWindow = new PopOutPlayerWindow(this, _currentVideo, curSec);

            var workArea = SystemParameters.WorkArea;
            _popOutWindow.Left = workArea.Right - _popOutWindow.Width - 30;
            _popOutWindow.Top = workArea.Bottom - _popOutWindow.Height - 30;

            _popOutWindow.Show();
            SwitchToFeedView();
            ShowToast("⧉ Floating Pop-Out Player Launched (Always on Top)");
        }

        public void ReturnFromPopOut(VideoItem video, double positionSeconds)
        {
            _popOutWindow = null;
            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = false;
            }
            VideoWebView.Visibility = Visibility.Visible;
            SwitchToPlayerView();
            _ = PlayVideoAsync(video, positionSeconds);
            ShowToast($"⧉ Restored to main player at {TimeSpan.FromSeconds(positionSeconds):mm\\:ss}");
        }

        private void StartSponsorBlockMonitor()
        {
            _sponsorBlockTimer?.Stop();
            if (_activeSponsorSegments == null || _activeSponsorSegments.Count == 0) return;

            _sponsorBlockTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
            _sponsorBlockTimer.Tick += async (s, e) =>
            {
                try
                {
                    if (VideoWebView.CoreWebView2 != null && _activeSponsorSegments.Count > 0)
                    {
                        var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                        if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double currentSec))
                        {
                            var segment = _activeSponsorSegments.FirstOrDefault(seg => currentSec >= seg.StartTime && currentSec < (seg.EndTime - 0.5));
                            if (segment != null)
                            {
                                await VideoWebView.ExecuteScriptAsync($"seek({segment.EndTime - currentSec + 0.1})");
                                ShowToast($"⏭️ Skipped {segment.Category}");
                            }
                        }
                    }
                }
                catch { }
            };
            _sponsorBlockTimer.Start();
        }

        public async void PlayNextVideo()
        {
            if (_currentFeed.Count == 0) return;

            VideoItem? next = null;

            // If Jev System-One is enabled, autonomously curate the best next video based on current context
            if (JevService.IsConfiguredAndEnabled && _currentVideo != null && _currentFeed.Count > 1)
            {
                try
                {
                    var candidates = _currentFeed.Where(v => v.Id != _currentVideo.Id).ToList();
                    var jevChoice = await JevService.CurateNextVideoAsync(_currentVideo.Title, _currentVideo.ChannelTitle, candidates);
                    if (jevChoice != null)
                    {
                        next = jevChoice;
                        _currentVideoIndex = _currentFeed.IndexOf(jevChoice);
                        ShowToast($"⚡ Jev AI DJ: Playing '{jevChoice.Title}'");
                    }
                }
                catch { }
            }

            if (next == null)
            {
                _currentVideoIndex++;
                if (_currentVideoIndex >= _currentFeed.Count)
                {
                    _currentVideoIndex = 0;
                }
                next = _currentFeed[_currentVideoIndex];
                ShowToast("Autoplay: Playing Next Video ⏭️");
            }

            _ = PlayVideoAsync(next);
        }

        public void PlayPreviousVideo()
        {
            if (_currentFeed.Count == 0) return;

            _currentVideoIndex--;
            if (_currentVideoIndex < 0)
            {
                _currentVideoIndex = _currentFeed.Count - 1;
            }

            var prev = _currentFeed[_currentVideoIndex];
            _ = PlayVideoAsync(prev);
            ShowToast("Playing Previous Video ⏮️");
        }

        private void BackToFeed_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
        }

        private void NextVideoBtn_Click(object sender, RoutedEventArgs e)
        {
            PlayNextVideo();
        }

        private void ThumbsUpBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                if (StorageService.IsDisliked(_currentVideo.Id))
                {
                    StorageService.RemoveDislike(_currentVideo.Id);
                    ThumbsDownBtn.Foreground = System.Windows.Media.Brushes.White;
                }

                StorageService.ToggleFavorite(_currentVideo);
                ThumbsUpBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                FavBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                ShowToast(_currentVideo.IsFavorite ? "👍 Liked! Algorithm boosted for this creator." : "Removed Like");
            }
        }

        private void ThumbsDownBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                var targetVideo = _currentVideo;
                StorageService.AddDislike(targetVideo);

                ThumbsUpBtn.Foreground = System.Windows.Media.Brushes.White;
                FavBtn.Foreground = System.Windows.Media.Brushes.White;
                ThumbsDownBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");

                // Completely remove from active feeds
                _currentFeed.RemoveAll(v => v.Id == targetVideo.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == targetVideo.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;

                ShowToast("👎 Disliked! Video removed & assigned negative score.");

                if (_currentFeed.Count > 0)
                {
                    PlayNextVideo();
                }
                else
                {
                    SwitchToFeedView();
                }
            }
        }

        private void FavBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                if (StorageService.IsDisliked(_currentVideo.Id))
                {
                    StorageService.RemoveDislike(_currentVideo.Id);
                    if (ThumbsDownBtn != null) ThumbsDownBtn.Foreground = System.Windows.Media.Brushes.White;
                }
                StorageService.ToggleFavorite(_currentVideo);
                FavBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                if (ThumbsUpBtn != null) ThumbsUpBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                ShowToast(_currentVideo.IsFavorite ? "Added to Favorites ⭐" : "Removed from Favorites");
            }
        }

        #endregion

        #region Video Card Context Menu Handlers

        private void ContextMenuPlay_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                _ = PlayVideoAsync(video);
            }
        }

        private void ContextMenuLike_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                if (StorageService.IsDisliked(video.Id))
                {
                    StorageService.RemoveDislike(video.Id);
                }
                StorageService.ToggleFavorite(video);
                ShowToast(video.IsFavorite ? "👍 Liked! Boosted in algorithm." : "Removed Like");
            }
        }

        private void ContextMenuDislike_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                StorageService.AddDislike(video);
                _currentFeed.RemoveAll(v => v.Id == video.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;
                ShowToast("👎 Disliked & removed from feed! Algorithm updated.");
            }
        }

        private void ContextMenuDelete_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                if (FeedTitleText?.Text == "📜 Watch History")
                {
                    StorageService.RemoveFromWatchHistory(video.Id);
                    _currentFeed.RemoveAll(v => v.Id == video.Id);
                    _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                    VideoItemsControl.ItemsSource = null;
                    VideoItemsControl.ItemsSource = _currentFeed;
                    ShowToast("🗑️ Removed from Watch History");
                    return;
                }

                StorageService.DeleteVideo(video);
                _currentFeed.RemoveAll(v => v.Id == video.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;
                ShowToast("🗑️ Video removed from feed");
            }
        }

        private void ContextMenuDeleteChannel_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video && !string.IsNullOrWhiteSpace(video.ChannelTitle))
            {
                var ch = video.ChannelTitle.Trim();
                var result = MessageBox.Show(
                    $"Are you sure you want to permanently delete and block the channel '{ch}'?\n\nAll videos from this channel will be removed and you will never see recommendations or videos from this channel again.",
                    "Permanently Delete Channel",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Warning);

                if (result == MessageBoxResult.Yes)
                {
                    StorageService.PermanentlyDeleteChannel(ch);
                    _currentFeed.RemoveAll(v => v.ChannelTitle != null && (v.ChannelTitle.Equals(ch, StringComparison.OrdinalIgnoreCase) || v.ChannelTitle.Contains(ch, StringComparison.OrdinalIgnoreCase)));
                    _rawUnfilteredFeed.RemoveAll(v => v.ChannelTitle != null && (v.ChannelTitle.Equals(ch, StringComparison.OrdinalIgnoreCase) || v.ChannelTitle.Contains(ch, StringComparison.OrdinalIgnoreCase)));
                    VideoItemsControl.ItemsSource = null;
                    VideoItemsControl.ItemsSource = _currentFeed;
                    ShowToast($"🚫 Channel '{ch}' permanently deleted & blocked!");
                }
            }
        }

        #region Video Tile Quick Action Buttons (Thumbs Up, Thumbs Down, Delete)

        private void CardThumbsUp_Click(object sender, RoutedEventArgs e)
        {
            e.Handled = true;
            if (sender is FrameworkElement elem && elem.DataContext is VideoItem video)
            {
                if (StorageService.IsDisliked(video.Id))
                {
                    StorageService.RemoveDislike(video.Id);
                    video.IsDisliked = false;
                }
                StorageService.ToggleFavorite(video);
                ShowToast(video.IsFavorite ? "👍 Liked! Boosted in algorithm." : "Removed Like");
            }
        }

        private void CardThumbsDown_Click(object sender, RoutedEventArgs e)
        {
            e.Handled = true;
            if (sender is FrameworkElement elem && elem.DataContext is VideoItem video)
            {
                StorageService.AddDislike(video);
                _currentFeed.RemoveAll(v => v.Id == video.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;
                ShowToast("👎 Disliked & removed from feed! Algorithm updated.");
            }
        }

        private void CardDelete_Click(object sender, RoutedEventArgs e)
        {
            e.Handled = true;
            if (sender is FrameworkElement elem && elem.DataContext is VideoItem video)
            {
                if (FeedTitleText?.Text == "📜 Watch History")
                {
                    StorageService.RemoveFromWatchHistory(video.Id);
                    _currentFeed.RemoveAll(v => v.Id == video.Id);
                    _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                    VideoItemsControl.ItemsSource = null;
                    VideoItemsControl.ItemsSource = _currentFeed;
                    ShowToast("🗑️ Removed from Watch History");
                    return;
                }

                StorageService.DeleteVideo(video);
                _currentFeed.RemoveAll(v => v.Id == video.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;
                ShowToast("🗑️ Video removed from feed");
            }
        }

        #endregion

        private void ContextMenuFavorite_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                StorageService.ToggleFavorite(video);
                ShowToast(video.IsFavorite ? "⭐ Added to Favorites" : "Removed from Favorites");
            }
        }

        private void ContextMenuWatchLater_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                StorageService.ToggleWatchLater(video);
                ShowToast(video.IsWatchLater ? "🕒 Added to Watch Later" : "Removed from Watch Later");
            }
        }

        private void ContextMenuShare_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                OpenShareModal(video, 0);
            }
        }

        private void ContextMenuCopyLink_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                var url = $"https://youtu.be/{video.Id}";
                Clipboard.SetText(url);
                ShowToast("Video link copied to clipboard! 📋✨");
            }
        }

        private async void ContextMenuChannel_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video && !string.IsNullOrWhiteSpace(video.ChannelTitle))
            {
                await NavigateToChannelFeedAsync(video.ChannelTitle);
            }
        }

        private async void ChannelName_MouseDown(object sender, MouseButtonEventArgs e)
        {
            e.Handled = true; // Prevent triggering the card's PlayVideoAsync
            if (sender is FrameworkElement elem && elem.DataContext is VideoItem video && !string.IsNullOrWhiteSpace(video.ChannelTitle))
            {
                await NavigateToChannelFeedAsync(video.ChannelTitle);
            }
        }

        private async void CurrentVideoChannel_MouseDown(object sender, MouseButtonEventArgs e)
        {
            e.Handled = true;
            if (_currentVideo != null && !string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle))
            {
                await NavigateToChannelFeedAsync(_currentVideo.ChannelTitle);
            }
        }

        #endregion



        private void WatchLaterBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                StorageService.ToggleWatchLater(_currentVideo);
                WatchLaterBtn.Foreground = _currentVideo.IsWatchLater ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;
                ShowToast(_currentVideo.IsWatchLater ? "Added to Watch Later 🕒" : "Removed from Watch Later");
            }
        }

        private void AutoplayBtn_Click(object sender, RoutedEventArgs e)
        {
            StorageService.Settings.IsAutoplayEnabled = !StorageService.Settings.IsAutoplayEnabled;
            StorageService.Save();
            UpdateAutoplayUi();
            ShowToast(StorageService.Settings.IsAutoplayEnabled ? "▶️ Autoplay is ON" : "⏸️ Autoplay is OFF");
        }

        private void UpdateAutoplayUi()
        {
            if (AutoplayBtn != null)
            {
                AutoplayBtn.Content = StorageService.Settings.IsAutoplayEnabled ? "▶️" : "⏸️";
                AutoplayBtn.Foreground = StorageService.Settings.IsAutoplayEnabled ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.Gray;
            }
        }

        #region Visual Scrubber & Timeline Progress

        private double _currentVideoDuration = 0;
        private double _currentVideoPosition = 0;
        private bool _isUserScrubbing = false;

        public void UpdateVideoDuration(double duration)
        {
            if (duration <= 0) return;
            _currentVideoDuration = duration;
            if (PlayerDurationText != null) PlayerDurationText.Text = FormatSeconds(duration);
            RenderSponsorBlockSegments(SponsorBlockCanvas?.ActualWidth ?? 0);
            RenderChapterTicks(ChapterTicksCanvas?.ActualWidth ?? 0);
        }

        public void UpdatePlaybackTimeline(double position)
        {
            _currentVideoPosition = position;
            if (!_isUserScrubbing)
            {
                if (PlayerCurrentTimeText != null) PlayerCurrentTimeText.Text = FormatSeconds(position);
                if (_currentVideoDuration > 0)
                {
                    var pct = Math.Clamp((position / _currentVideoDuration) * 100, 0, 100);
                    if (PlayerProgressBar != null) PlayerProgressBar.Value = pct;
                    if (PlayerScrubberSlider != null) PlayerScrubberSlider.Value = pct;
                }
            }
            SyncActiveTranscriptCue(position);
            CheckBingeModeTrigger(position);
        }

        private string FormatSeconds(double seconds)
        {
            var ts = TimeSpan.FromSeconds(Math.Max(0, seconds));
            return ts.Hours > 0 ? $"{ts.Hours}:{ts.Minutes:D2}:{ts.Seconds:D2}" : $"{ts.Minutes}:{ts.Seconds:D2}";
        }

        private void ScrubberGrid_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            RenderSponsorBlockSegments(e.NewSize.Width);
            RenderChapterTicks(e.NewSize.Width);
        }

        private void RenderSponsorBlockSegments(double totalWidth)
        {
            if (SponsorBlockCanvas == null) return;
            SponsorBlockCanvas.Children.Clear();
            if (_currentVideoDuration <= 0 || _activeSponsorSegments == null || _activeSponsorSegments.Count == 0 || totalWidth <= 0)
                return;

            foreach (var seg in _activeSponsorSegments)
            {
                var startX = Math.Clamp((seg.StartTime / _currentVideoDuration) * totalWidth, 0, totalWidth);
                var endX = Math.Clamp((seg.EndTime / _currentVideoDuration) * totalWidth, 0, totalWidth);
                var w = Math.Max(2, endX - startX);

                var colorHex = seg.Category switch
                {
                    "sponsor" => "#00D084",
                    "intro" or "outro" => "#00A8FF",
                    "interaction" => "#F39C12",
                    "selfpromo" => "#9B59B6",
                    _ => "#00D084"
                };

                var rect = new System.Windows.Shapes.Rectangle
                {
                    Width = w,
                    Height = 4,
                    Fill = new SolidColorBrush((Color)ColorConverter.ConvertFromString(colorHex)),
                    Opacity = 0.85,
                    RadiusX = 1,
                    RadiusY = 1,
                    ToolTip = $"{seg.Category} ({FormatSeconds(seg.StartTime)} - {FormatSeconds(seg.EndTime)})"
                };

                Canvas.SetLeft(rect, startX);
                SponsorBlockCanvas.Children.Add(rect);
            }
        }

        private void RenderChapterTicks(double totalWidth)
        {
            if (ChapterTicksCanvas == null) return;
            ChapterTicksCanvas.Children.Clear();
        }

        private void PlayerScrubber_PreviewMouseDown(object sender, MouseButtonEventArgs e)
        {
            _isUserScrubbing = true;
        }

        private void PlayerScrubber_PreviewMouseUp(object sender, MouseButtonEventArgs e)
        {
            _isUserScrubbing = false;
            if (_currentVideoDuration > 0 && PlayerScrubberSlider != null)
            {
                var targetSec = (PlayerScrubberSlider.Value / 100.0) * _currentVideoDuration;
                _ = SeekToSecondsAsync(targetSec);
            }
        }

        private void PlayerScrubber_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_isUserScrubbing && _currentVideoDuration > 0)
            {
                var targetSec = (e.NewValue / 100.0) * _currentVideoDuration;
                if (PlayerCurrentTimeText != null) PlayerCurrentTimeText.Text = FormatSeconds(targetSec);
                if (PlayerProgressBar != null) PlayerProgressBar.Value = e.NewValue;
            }
        }

        private async Task SeekToSecondsAsync(double seconds)
        {
            try
            {
                var secStr = seconds.ToString(System.Globalization.CultureInfo.InvariantCulture);
                if (_isDirectWatchMode)
                {
                    await VideoWebView.ExecuteScriptAsync($"document.querySelector('video')?.currentTime = {secStr};");
                }
                else
                {
                    await VideoWebView.ExecuteScriptAsync($"seekTo({secStr});");
                }
            }
            catch { }
        }

        #endregion

        #region Episodic Binge Mode & Next Episode Advancer

        private bool _isBingeCardDismissedForCurrentVideo = false;
        private VideoItem? _bingeNextVideo = null;
        private DispatcherTimer? _bingeTimer = null;
        private int _bingeCountdown = 5;

        private void CheckBingeModeTrigger(double position)
        {
            if (_currentVideoDuration < 20 || position < (_currentVideoDuration - 12))
                return;

            if (_isBingeCardDismissedForCurrentVideo || (NextEpisodeCard != null && NextEpisodeCard.Visibility == Visibility.Visible))
                return;

            VideoItem? nextVideo = null;
            var series = SeriesDetectionService.DetectSeries(_currentVideo);
            if (series != null && series.EpisodeNumber.HasValue)
            {
                var nextEpNum = series.EpisodeNumber.Value + 1;
                nextVideo = _currentFeed.FirstOrDefault(v =>
                {
                    var s = SeriesDetectionService.DetectSeries(v);
                    return s != null && s.SeriesTitle.Equals(series.SeriesTitle, StringComparison.OrdinalIgnoreCase) && s.EpisodeNumber == nextEpNum;
                });
            }

            if (nextVideo == null && _currentFeed.Count > 0 && _currentVideo != null)
            {
                var idx = _currentFeed.FindIndex(v => v.Id == _currentVideo.Id);
                if (idx >= 0 && idx + 1 < _currentFeed.Count)
                {
                    nextVideo = _currentFeed[idx + 1];
                }
            }

            if (nextVideo != null && NextEpisodeCard != null)
            {
                _bingeNextVideo = nextVideo;
                NextEpisodeTitle.Text = nextVideo.Title;
                NextEpisodeChannel.Text = nextVideo.ChannelTitle;
                NextEpisodeSeriesTag.Text = series?.EpisodeNumber.HasValue == true
                    ? $"S{series.SeasonNumber ?? 1:D2}E{(series.EpisodeNumber.Value + 1):D2} • Next Episode"
                    : "⚡ Up Next in Feed";

                if (!string.IsNullOrEmpty(nextVideo.ThumbnailUrl))
                {
                    try
                    {
                        var bi = new BitmapImage();
                        bi.BeginInit();
                        bi.UriSource = new Uri(nextVideo.ThumbnailUrl, UriKind.Absolute);
                        bi.CacheOption = BitmapCacheOption.OnLoad;
                        bi.EndInit();
                        NextEpisodeThumb.Source = bi;
                    }
                    catch { }
                }

                NextEpisodeCard.Visibility = Visibility.Visible;
                _bingeCountdown = 5;
                NextEpisodeCountdownText.Text = $"{_bingeCountdown}s";

                _bingeTimer?.Stop();
                _bingeTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
                _bingeTimer.Tick += async (s, e) =>
                {
                    _bingeCountdown--;
                    if (_bingeCountdown <= 0)
                    {
                        _bingeTimer?.Stop();
                        NextEpisodeCard.Visibility = Visibility.Collapsed;
                        if (_bingeNextVideo != null)
                        {
                            await PlayVideoAsync(_bingeNextVideo);
                        }
                    }
                    else
                    {
                        NextEpisodeCountdownText.Text = $"{_bingeCountdown}s";
                    }
                };
                _bingeTimer.Start();
            }
        }

        private void PlayNextEpisodeNow_Click(object sender, RoutedEventArgs e)
        {
            _bingeTimer?.Stop();
            if (NextEpisodeCard != null) NextEpisodeCard.Visibility = Visibility.Collapsed;
            if (_bingeNextVideo != null)
            {
                _ = PlayVideoAsync(_bingeNextVideo);
            }
        }

        private void DismissNextEpisode_Click(object sender, RoutedEventArgs e)
        {
            _bingeTimer?.Stop();
            if (NextEpisodeCard != null) NextEpisodeCard.Visibility = Visibility.Collapsed;
            _isBingeCardDismissedForCurrentVideo = true;
        }

        #endregion

        #region Interactive Live Transcript & In-Video Search

        private List<TranscriptCue> _allTranscriptCues = new();
        private List<TranscriptCue> _filteredTranscriptCues = new();
        private TranscriptCue? _currentActiveCue = null;

        private void TranscriptBtn_Click(object sender, RoutedEventArgs e)
        {
            if (TranscriptDrawer == null) return;
            TranscriptDrawer.Visibility = (TranscriptDrawer.Visibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible);
            if (TranscriptDrawer.Visibility == Visibility.Visible)
            {
                _ = LoadTranscriptForCurrentVideoAsync();
            }
        }

        private void CloseTranscriptDrawer_Click(object sender, RoutedEventArgs e)
        {
            if (TranscriptDrawer != null) TranscriptDrawer.Visibility = Visibility.Collapsed;
        }

        private async Task LoadTranscriptForCurrentVideoAsync()
        {
            if (_currentVideo == null) return;
            if (TranscriptStatusText != null) TranscriptStatusText.Text = "⏳ Loading live transcript...";
            if (TranscriptItemsControl != null) TranscriptItemsControl.ItemsSource = null;

            var cues = await TranscriptService.GetTranscriptAsync(_currentVideo.Id, FetchTranscriptFromWebViewAsync);
            _allTranscriptCues = cues ?? new List<TranscriptCue>();
            _filteredTranscriptCues = new List<TranscriptCue>(_allTranscriptCues);

            if (TranscriptItemsControl != null) TranscriptItemsControl.ItemsSource = _filteredTranscriptCues;
            if (TranscriptCountText != null) TranscriptCountText.Text = $"({_allTranscriptCues.Count} lines)";
            if (TranscriptStatusText != null)
            {
                TranscriptStatusText.Text = _allTranscriptCues.Count > 0 ? "" : "No transcript available for this video.";
            }

            SyncActiveTranscriptCue(_currentVideoPosition);
        }

        private void TranscriptSearchInput_TextChanged(object sender, TextChangedEventArgs e)
        {
            var query = TranscriptSearchInput?.Text?.Trim() ?? "";
            if (string.IsNullOrWhiteSpace(query))
            {
                _filteredTranscriptCues = new List<TranscriptCue>(_allTranscriptCues);
            }
            else
            {
                _filteredTranscriptCues = _allTranscriptCues.Where(c => c.Text.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
            }
            if (TranscriptItemsControl != null)
            {
                TranscriptItemsControl.ItemsSource = null;
                TranscriptItemsControl.ItemsSource = _filteredTranscriptCues;
            }
            if (TranscriptCountText != null) TranscriptCountText.Text = $"({_filteredTranscriptCues.Count} matches)";
        }

        private void ClearTranscriptSearch_Click(object sender, RoutedEventArgs e)
        {
            if (TranscriptSearchInput != null) TranscriptSearchInput.Text = string.Empty;
        }

        private void TranscriptCue_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.DataContext is TranscriptCue cue)
            {
                _ = SeekToSecondsAsync(cue.StartSeconds);
                ShowToast($"▶ Jumped to {cue.FormattedTime}");
            }
        }

        private void CopyTranscriptCue_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.DataContext is TranscriptCue cue && _currentVideo != null)
            {
                var quote = $"\"{cue.Text}\"\nhttps://youtu.be/{_currentVideo.Id}?t={(int)cue.StartSeconds}s";
                Clipboard.SetText(quote);
                ShowToast($"📋 Copied quote & timestamp at {cue.FormattedTime}");
            }
        }

        private void SyncActiveTranscriptCue(double position)
        {
            if (TranscriptDrawer == null || TranscriptDrawer.Visibility != Visibility.Visible || _allTranscriptCues.Count == 0)
                return;

            TranscriptCue? matchingCue = null;
            for (int i = 0; i < _allTranscriptCues.Count; i++)
            {
                var cue = _allTranscriptCues[i];
                var nextStart = (i + 1 < _allTranscriptCues.Count) ? _allTranscriptCues[i + 1].StartSeconds : double.MaxValue;
                if (position >= cue.StartSeconds && position < nextStart)
                {
                    matchingCue = cue;
                    break;
                }
            }

            if (matchingCue != null && matchingCue != _currentActiveCue)
            {
                if (_currentActiveCue != null) _currentActiveCue.IsActive = false;
                matchingCue.IsActive = true;
                _currentActiveCue = matchingCue;
            }
        }

        private async Task<string> FetchTranscriptFromWebViewAsync(string videoId)
        {
            try
            {
                if (VideoWebView?.CoreWebView2 != null)
                {
                    var js = @"(function() {
                        try {
                            var items = document.querySelectorAll('ytd-transcript-segment-renderer');
                            if (!items || items.length === 0) return '';
                            var res = [];
                            items.forEach(function(it) {
                                var time = it.querySelector('.segment-timestamp')?.textContent?.trim() || '';
                                var text = it.querySelector('.segment-text')?.textContent?.trim() || '';
                                if (time && text) res.push('[' + time + '] ' + text);
                            });
                            return res.join('\n');
                        } catch(e) { return ''; }
                    })()";
                    var result = await VideoWebView.ExecuteScriptAsync(js);
                    if (!string.IsNullOrWhiteSpace(result) && result != "\"\"" && result != "null")
                    {
                        return Newtonsoft.Json.JsonConvert.DeserializeObject<string>(result) ?? "";
                    }
                }
            }
            catch { }
            return string.Empty;
        }

        #endregion

    }
}
