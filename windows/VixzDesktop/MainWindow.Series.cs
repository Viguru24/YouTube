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
        #region Channel TV Series & Shows Explorer

        private string? _currentSeriesTitle = null;
        private string? _currentSeriesChannel = null;
        private VideoItem? _seriesReturnVideo = null;
        private string? _activeSeriesSeasonFilter = null;
        private SeriesInfo? _lastDetectedSeriesInfo = null;

        private async void SeriesHubBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null && !string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle))
            {
                _seriesReturnVideo = _currentVideo;
                await OpenSeriesForVideoAsync(_currentVideo);
            }
            else
            {
                ShowToast("Play or select a video to find series episodes");
            }
        }

        private async void CardSeries_Click(object sender, RoutedEventArgs e)
        {
            e.Handled = true; // Prevent video card from playing
            if (sender is FrameworkElement elem && elem.Tag is VideoItem video && !string.IsNullOrWhiteSpace(video.ChannelTitle))
            {
                await OpenSeriesForVideoAsync(video);
            }
        }

        private void NavSeries_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null && !string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle))
            {
                var seriesInfo = SeriesDetectionService.DetectSeries(_currentVideo);
                OpenSeriesHubPopup(seriesInfo.SeriesTitle, _currentVideo.ChannelTitle);
            }
            else
            {
                OpenSeriesHubPopup();
            }
        }

        private async void ContextMenuSeries_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video && !string.IsNullOrWhiteSpace(video.ChannelTitle))
            {
                await OpenSeriesForVideoAsync(video);
            }
        }

        public async Task OpenSeriesForVideoAsync(VideoItem video)
        {
            if (video == null) return;

            var seriesInfo = SeriesDetectionService.DetectSeries(video);
            _lastDetectedSeriesInfo = seriesInfo;
            _currentSeriesTitle = seriesInfo.SeriesTitle;
            _currentSeriesChannel = video.ChannelTitle;
            _activeSeriesSeasonFilter = !string.IsNullOrWhiteSpace(seriesInfo.Season) ? seriesInfo.Season : null;

            var chanDesc = !string.IsNullOrWhiteSpace(video.ChannelTitle) ? $" on {video.ChannelTitle}" : "";
            ShowToast($"📺 Finding '{seriesInfo.SeriesTitle}'{chanDesc}...");
            await LoadSeriesFeedAsync(_currentSeriesChannel, _currentSeriesTitle, _activeSeriesSeasonFilter, seriesInfo);
        }

        public async Task LoadSeriesFeedAsync(string? channel, string seriesTitle, string? seasonFilter = null, SeriesInfo? seriesInfo = null)
        {
            if (string.IsNullOrWhiteSpace(seriesTitle)) return;

            _isDiscoveryFeed = false;
            _currentSeriesChannel = !string.IsNullOrWhiteSpace(channel) ? channel.Trim() : null;
            _currentSeriesTitle = seriesTitle.Trim();
            _activeSeriesSeasonFilter = seasonFilter;

            if (seriesInfo == null)
            {
                seriesInfo = SeriesDetectionService.DetectSeries(_currentSeriesTitle, _currentSeriesChannel ?? "");
                _lastDetectedSeriesInfo = seriesInfo;
            }

            UpdateSeriesBannerUi(_currentSeriesChannel, _currentSeriesTitle, seasonFilter, seriesInfo);

            SwitchToFeedView();

            var chanDisplay = !string.IsNullOrWhiteSpace(_currentSeriesChannel) ? _currentSeriesChannel : "All YouTube";
            var feedLabel = !string.IsNullOrWhiteSpace(seasonFilter)
                ? $"📺 {chanDisplay} • {_currentSeriesTitle} ({seasonFilter})"
                : $"📺 {chanDisplay} • {_currentSeriesTitle}";

            await LoadFeedAsync(feedLabel, async () =>
            {
                var vids = await YouTubeService.GetChannelSeriesVideosAsync(_currentSeriesChannel, _currentSeriesTitle, seasonFilter);
                Dispatcher.Invoke(() =>
                {
                    if (SeriesBannerCount != null)
                    {
                        SeriesBannerCount.Text = $"({vids.Count} videos found)";
                    }
                });
                return vids;
            });
        }

        private void UpdateSeriesBannerUi(string? channel, string seriesTitle, string? activeSeason, SeriesInfo seriesInfo)
        {
            if (SeriesBannerBar == null || SeriesBannerTitle == null || SeriesBannerChannel == null || SeriesBannerPillsPanel == null) return;

            SeriesBannerTitle.Text = seriesTitle;
            if (!string.IsNullOrWhiteSpace(channel))
            {
                SeriesBannerChannel.Text = channel.StartsWith("@") ? channel : $"@{channel}";
                if (SeriesBannerAllChannelBtn != null) SeriesBannerAllChannelBtn.Visibility = Visibility.Visible;
            }
            else
            {
                SeriesBannerChannel.Text = "🌐 All YouTube";
                if (SeriesBannerAllChannelBtn != null) SeriesBannerAllChannelBtn.Visibility = Visibility.Collapsed;
            }

            SeriesBannerCount.Text = "(Searching...)";
            SeriesBannerBar.Visibility = Visibility.Visible;

            if (_seriesReturnVideo != null)
            {
                SeriesBannerBackToVideoBtn.Visibility = Visibility.Visible;
                SeriesBannerBackToVideoBtn.ToolTip = $"Resume watching: {_seriesReturnVideo.Title}";
            }
            else
            {
                SeriesBannerBackToVideoBtn.Visibility = Visibility.Collapsed;
            }

            SeriesBannerPillsPanel.Children.Clear();

            var goldBrush = (System.Windows.Media.Brush)FindResource("AccentGold");

            // 1. All Series Videos Pill
            var allBtn = new Button
            {
                Content = $"⭐ All {seriesTitle}",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 10.5,
                Padding = new Thickness(9, 3, 9, 3),
                Margin = new Thickness(0, 0, 6, 4),
                Foreground = string.IsNullOrWhiteSpace(activeSeason) ? goldBrush : System.Windows.Media.Brushes.White,
                Tag = ""
            };
            allBtn.Click += async (s, e) =>
            {
                await LoadSeriesFeedAsync(channel, seriesTitle, null, seriesInfo);
            };
            SeriesBannerPillsPanel.Children.Add(allBtn);

            // 2. Season Pills
            var seasonKeywords = seriesInfo.SuggestedKeywords
                .Where(k => Regex.IsMatch(k, @"(?i)\b(?:Season|Series|Book|Campaign)\s*\d+\b"))
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .ToList();

            if (seasonKeywords.Count == 0)
            {
                seasonKeywords = new List<string> { "Season 1", "Season 2", "Season 3", "Season 4", "Season 5" };
            }

            foreach (var season in seasonKeywords)
            {
                var isSelected = !string.IsNullOrWhiteSpace(activeSeason) &&
                                 string.Equals(activeSeason, season, StringComparison.OrdinalIgnoreCase);

                var sBtn = new Button
                {
                    Content = $"🎬 {season}",
                    Style = (Style)FindResource("GlassButton"),
                    FontSize = 10.5,
                    Padding = new Thickness(9, 3, 9, 3),
                    Margin = new Thickness(0, 0, 6, 4),
                    Foreground = isSelected ? goldBrush : System.Windows.Media.Brushes.White,
                    Tag = season
                };
                sBtn.Click += async (s, e) =>
                {
                    await LoadSeriesFeedAsync(channel, seriesTitle, season, seriesInfo);
                };
                SeriesBannerPillsPanel.Children.Add(sBtn);
            }

            // 3. Full Episodes Pill
            var fullBtn = new Button
            {
                Content = "🍿 Full Episodes",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 10.5,
                Padding = new Thickness(9, 3, 9, 3),
                Margin = new Thickness(0, 0, 6, 4),
                Foreground = activeSeason == "Full Episode" ? goldBrush : System.Windows.Media.Brushes.White,
                Tag = "Full Episode"
            };
            fullBtn.Click += async (s, e) =>
            {
                await LoadSeriesFeedAsync(channel, seriesTitle, "Full Episode", seriesInfo);
            };
            SeriesBannerPillsPanel.Children.Add(fullBtn);

            // 4. Playlists Pill
            var plBtn = new Button
            {
                Content = "📋 Playlists",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 10.5,
                Padding = new Thickness(9, 3, 9, 3),
                Margin = new Thickness(0, 0, 6, 4),
                Tag = "Playlists"
            };
            plBtn.Click += async (s, e) =>
            {
                await LoadSeriesPlaylistsAsync(channel, seriesTitle);
            };
            SeriesBannerPillsPanel.Children.Add(plBtn);
        }

        public async Task LoadSeriesPlaylistsAsync(string? channel, string seriesQuery)
        {
            if (string.IsNullOrWhiteSpace(seriesQuery)) return;

            SwitchToFeedView();
            var chanDisplay = !string.IsNullOrWhiteSpace(channel) ? $"{channel} • " : "";
            var label = $"📋 {chanDisplay}Playlists for '{seriesQuery}'";
            await LoadFeedAsync(label, async () =>
            {
                var pls = await YouTubeService.GetChannelPlaylistsAsync(channel, seriesQuery);
                Dispatcher.Invoke(() =>
                {
                    if (SeriesBannerCount != null)
                    {
                        SeriesBannerCount.Text = $"({pls.Count} playlists found)";
                    }
                });
                return pls;
            });
        }

        public async Task LoadPlaylistFeedAsync(string playlistId, string playlistTitle)
        {
            if (string.IsNullOrWhiteSpace(playlistId)) return;

            ShowToast($"📋 Loading playlist: {playlistTitle}...");
            SwitchToFeedView();
            await LoadFeedAsync($"📋 Playlist: {playlistTitle}", () => YouTubeService.GetPlaylistVideosFeedAsync(playlistId));
        }

        private void SeriesBannerBackToVideo_Click(object sender, RoutedEventArgs e)
        {
            if (_seriesReturnVideo != null)
            {
                FeedView.Visibility = Visibility.Collapsed;
                PlayerView.Visibility = Visibility.Visible;
                AnimateBottomBar(1.0);
                ShowToast($"▶ Resumed: {_seriesReturnVideo.Title}");
            }
        }

        private async void SeriesBannerAllChannel_Click(object sender, RoutedEventArgs e)
        {
            if (!string.IsNullOrWhiteSpace(_currentSeriesChannel))
            {
                SeriesBannerBar.Visibility = Visibility.Collapsed;
                await NavigateToChannelFeedAsync(_currentSeriesChannel);
            }
        }

        private void SeriesBannerClose_Click(object sender, RoutedEventArgs e)
        {
            SeriesBannerBar.Visibility = Visibility.Collapsed;
            _seriesReturnVideo = null;
        }

        private void SeriesBannerRefine_Click(object sender, RoutedEventArgs e)
        {
            OpenSeriesHubPopup();
        }

        private void OpenSeriesHubPopup(string? prefillQuery = null, string? prefillChannel = null)
        {
            if (!string.IsNullOrWhiteSpace(prefillChannel))
            {
                _currentSeriesChannel = prefillChannel;
            }
            else if (string.IsNullOrWhiteSpace(_currentSeriesChannel) && _currentVideo != null && !string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle))
            {
                _currentSeriesChannel = _currentVideo.ChannelTitle;
            }

            if (!string.IsNullOrWhiteSpace(prefillQuery))
            {
                _currentSeriesTitle = prefillQuery;
            }

            var activeChan = _currentSeriesChannel?.Trim();
            SeriesPopupChannelSubtext.Text = !string.IsNullOrWhiteSpace(activeChan) ? $"Target: @{activeChan.TrimStart('@')}" : "Scope: All YouTube";
            SeriesPopupChannelInput.Text = activeChan ?? "";
            SeriesPopupInput.Text = _currentSeriesTitle ?? "";

            SeriesPillsPanel.Children.Clear();
            var pills = _lastDetectedSeriesInfo?.SuggestedKeywords ?? new List<string> { "Season 1", "Season 2", "Season 3", "Full Episodes", "Playlists" };
            foreach (var pill in pills)
            {
                var btn = new Button
                {
                    Content = pill,
                    Style = (Style)FindResource("GlassButton"),
                    FontSize = 10.5,
                    Padding = new Thickness(8, 3, 8, 3),
                    Margin = new Thickness(0, 0, 5, 5),
                    Tag = pill
                };
                btn.Click += async (s, e) =>
                {
                    var showTitle = !string.IsNullOrWhiteSpace(SeriesPopupInput.Text) ? SeriesPopupInput.Text.Trim() : (_currentSeriesTitle ?? pill);
                    var chan = !string.IsNullOrWhiteSpace(SeriesPopupChannelInput.Text) ? SeriesPopupChannelInput.Text.Trim() : _currentSeriesChannel;
                    SeriesHubPopup.IsOpen = false;
                    if (pill == "Playlists")
                    {
                        await LoadSeriesPlaylistsAsync(chan, showTitle);
                    }
                    else
                    {
                        var season = Regex.IsMatch(pill, @"(?i)\b(?:Season|Series|Book|Campaign)\s*\d+\b") ? pill : (pill == "Full Episodes" ? "Full Episode" : null);
                        await LoadSeriesFeedAsync(chan, showTitle, season);
                    }
                };
                SeriesPillsPanel.Children.Add(btn);
            }

            SeriesHubPopup.IsOpen = true;
            SeriesPopupInput.Focus();
            if (!string.IsNullOrEmpty(SeriesPopupInput.Text))
            {
                SeriesPopupInput.SelectAll();
            }
        }

        private void CloseSeriesPopup_Click(object sender, RoutedEventArgs e)
        {
            SeriesHubPopup.IsOpen = false;
        }

        private void SeriesPopupClearChannelBtn_Click(object sender, RoutedEventArgs e)
        {
            _currentSeriesChannel = null;
            SeriesPopupChannelInput.Text = "";
            SeriesPopupChannelSubtext.Text = "Scope: All YouTube";
        }

        private void SeriesPopupChannelInput_TextChanged(object sender, TextChangedEventArgs e)
        {
            var chan = SeriesPopupChannelInput.Text.Trim();
            SeriesPopupChannelSubtext.Text = !string.IsNullOrWhiteSpace(chan) ? $"Target: @{chan.TrimStart('@')}" : "Scope: All YouTube";
        }

        private async void SeriesPopupChannelInput_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await ExecuteSeriesPopupSearchAsync();
            }
        }

        private async Task ExecuteSeriesPopupSearchAsync()
        {
            var q = SeriesPopupInput.Text.Trim();
            if (string.IsNullOrWhiteSpace(q))
            {
                q = _currentSeriesTitle ?? "";
            }

            if (string.IsNullOrWhiteSpace(q))
            {
                ShowToast("⚠️ Please enter a TV series or show title to search");
                return;
            }

            var chan = SeriesPopupChannelInput.Text.Trim();
            _currentSeriesChannel = !string.IsNullOrWhiteSpace(chan) ? chan : null;
            _currentSeriesTitle = q;

            SeriesHubPopup.IsOpen = false;
            await LoadSeriesFeedAsync(_currentSeriesChannel, q);
        }

        private async void SeriesPopupSearchBtn_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteSeriesPopupSearchAsync();
        }

        private async void SeriesPopupInput_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await ExecuteSeriesPopupSearchAsync();
            }
        }

        private async void SeriesPopupFindAllBtn_Click(object sender, RoutedEventArgs e)
        {
            await ExecuteSeriesPopupSearchAsync();
        }

        private async void SeriesPopupPlaylistsBtn_Click(object sender, RoutedEventArgs e)
        {
            var q = SeriesPopupInput.Text.Trim();
            if (string.IsNullOrWhiteSpace(q)) q = _currentSeriesTitle ?? "";
            if (string.IsNullOrWhiteSpace(q))
            {
                ShowToast("⚠️ Please enter a TV series title to find playlists");
                return;
            }

            var chan = SeriesPopupChannelInput.Text.Trim();
            _currentSeriesChannel = !string.IsNullOrWhiteSpace(chan) ? chan : null;

            SeriesHubPopup.IsOpen = false;
            await LoadSeriesPlaylistsAsync(_currentSeriesChannel, q);
        }

        #endregion
    }
}
