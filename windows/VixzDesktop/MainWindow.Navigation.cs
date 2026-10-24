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
        #region Feed & Navigation

        private async Task LoadFeedAsync(string title, Func<Task<List<VideoItem>>> fetcher)
        {
            FeedTitleText.Text = title;
            if (ClearHistoryBtn != null) ClearHistoryBtn.Visibility = Visibility.Collapsed;
            if (ResetAlgorithmBtn != null) ResetAlgorithmBtn.Visibility = Visibility.Visible;
            LoadingSpinner.Visibility = Visibility.Visible;
            VideoItemsControl.ItemsSource = null;

            try
            {
                _rawUnfilteredFeed = await fetcher();
                ApplyCurrentFilters();
            }
            catch (Exception ex)
            {
                ShowToast($"Error loading feed: {ex.Message}");
            }
            finally
            {
                LoadingSpinner.Visibility = Visibility.Collapsed;
            }
        }

        private void ApplyCurrentFilters()
        {
            if (DateFilterCombo == null || DurationFilterCombo == null || SortByFilterCombo == null || VideoItemsControl == null)
            {
                return;
            }

            if (_rawUnfilteredFeed == null || _rawUnfilteredFeed.Count == 0)
            {
                _currentFeed = new List<VideoItem>();
                VideoItemsControl.ItemsSource = _currentFeed;
                return;
            }

            var isWatchHistory = FeedTitleText?.Text == "📜 Watch History";
            if (isWatchHistory)
            {
                // In Watch History, preserve chronological watch order (most recent first = index 0)
                // Filter by date or duration if explicitly selected by the user, but do NOT sort by YouTube upload date
                var dateTag = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
                var durationTag = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

                // Passing null for sortBy maintains the exact watch-history insertion order
                var list = YouTubeService.ApplyLocalFilters(_rawUnfilteredFeed, dateTag, durationTag, sortBy: null);

                _currentFeed = list;
                VideoItemsControl.ItemsSource = _currentFeed;
                return;
            }

            var dateTagFilter = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var durationTagFilter = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var sortByTag = (SortByFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

            var filtered = YouTubeService.ApplyLocalFilters(_rawUnfilteredFeed, dateTagFilter, durationTagFilter, sortByTag);

            // On discovery feeds (Home / Subscriptions), hide already-watched videos
            if (_isDiscoveryFeed)
            {
                var watchedIds = new HashSet<string>(
                    StorageService.Settings.WatchHistory.Select(v => v.Id),
                    StringComparer.OrdinalIgnoreCase);
                filtered = filtered.Where(v => !watchedIds.Contains(v.Id)).ToList();
            }

            _currentFeed = filtered;
            VideoItemsControl.ItemsSource = _currentFeed;
        }

        private void FilterOrSort_Changed(object sender, SelectionChangedEventArgs e)
        {
            ApplyCurrentFilters();
        }

        private async void ApplyFiltersBtn_Click(object sender, RoutedEventArgs e)
        {
            var query = SearchBox.Text.Trim();
            if (!string.IsNullOrWhiteSpace(query))
            {
                await PerformSearchWithFiltersAsync();
                return;
            }

            // If we already have a loaded feed (Subscriptions, Home, Channel, etc.), apply filters directly to it!
            if (_rawUnfilteredFeed != null && _rawUnfilteredFeed.Count > 0)
            {
                ApplyCurrentFilters();
                ShowToast($"⚡ Filtered {_currentFeed.Count} videos");
                return;
            }

            var dateTag = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var durationTag = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var sortByTag = (SortByFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

            if (!string.IsNullOrWhiteSpace(dateTag) || !string.IsNullOrWhiteSpace(durationTag) || sortByTag == "latest" || sortByTag == "views")
            {
                LoadingSpinner.Visibility = Visibility.Visible;
                SwitchToFeedView();
                var label = dateTag == "today" ? "Today's Newest Videos" : "Filtered Feed";
                FeedTitleText.Text = $"⚡ {label}";

                var results = await YouTubeService.GetDeepFilteredFeedAsync(dateTag, durationTag, sortByTag);
                _rawUnfilteredFeed = results;
                _currentFeed = results;
                VideoItemsControl.ItemsSource = _currentFeed;
                LoadingSpinner.Visibility = Visibility.Collapsed;
                ShowToast($"⚡ Found {_currentFeed.Count} videos matching filters");
            }
            else
            {
                ApplyCurrentFilters();
            }
        }

        private void ResetFiltersBtn_Click(object sender, RoutedEventArgs e)
        {
            DateFilterCombo.SelectedIndex = 0;
            DurationFilterCombo.SelectedIndex = 0;
            SortByFilterCombo.SelectedIndex = 1;
            _currentFeed = _rawUnfilteredFeed.ToList();
            VideoItemsControl.ItemsSource = _currentFeed;
            ShowToast("Filters reset");
        }

        private int _feedBatchIndex = 0;

        private async void NavHome_Click(object sender, RoutedEventArgs e)
        {
            _feedBatchIndex = 0;
            _currentSearchQuery = null;
            _isDiscoveryFeed = true;
            SwitchToFeedView();
            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
        }

        private async void NavSubscriptions_Click(object sender, RoutedEventArgs e)
        {
            _feedBatchIndex = 0;
            _currentSearchQuery = null;
            _isDiscoveryFeed = true;
            SwitchToFeedView();
            await LoadFeedAsync("🔔 Subscriptions Feed", () => YouTubeService.GetSubscribedFeedAsync());
        }

        private async void ChannelFilter_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Content is string channelName)
            {
                await NavigateToChannelFeedAsync(channelName);
            }
        }

        public async Task NavigateToChannelFeedAsync(string channelName)
        {
            if (string.IsNullOrWhiteSpace(channelName)) return;

            var cleanName = channelName.Trim();
            _isDiscoveryFeed = false;
            _currentSearchQuery = cleanName;

            SwitchToFeedView();
            await LoadFeedAsync($"👤 {cleanName} (Videos)", () => YouTubeService.GetChannelVideosFeedAsync(cleanName));
        }

        private void RefreshSubscribedChannelsUi()
        {
            SubscribedChannelsList.ItemsSource = null;
            SubscribedChannelsList.ItemsSource = UserProfileData.SubscribedChannels;
            SubscribersHeader.Text = $"👤 Subscriptions ({UserProfileData.SubscribedChannels.Count})";
            UpdateSubscribeToggleBtn();
        }

        private void DeleteChannel_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.Tag is string channelName)
            {
                UserProfileData.RemoveSubscribedChannel(channelName);
                RefreshSubscribedChannelsUi();
                ShowToast($"Unsubscribed from {channelName}");
            }
        }

        private void AddChannelBtn_Click(object sender, RoutedEventArgs e)
        {
            var prompt = new Window
            {
                Title = "➕ Add Subscribed Channel",
                Width = 420,
                Height = 220,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var sp = new StackPanel { Margin = new Thickness(18) };
            var heading = new TextBlock
            {
                Text = "➕ Add Channel / Creator",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 8)
            };
            var desc = new TextBlock
            {
                Text = "Enter any YouTube channel name or handle to add to your custom subscriptions feed:",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 10)
            };

            var txtBox = new TextBox
            {
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 14)
            };

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            var cancelBtn = new Button
            {
                Content = "Cancel",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            cancelBtn.Click += (s, ev) => prompt.Close();

            var addBtn = new Button
            {
                Content = "Add Channel",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                Padding = new Thickness(14, 6, 14, 6)
            };

            Action doAdd = () =>
            {
                var val = txtBox.Text.Trim();
                if (!string.IsNullOrWhiteSpace(val))
                {
                    UserProfileData.AddSubscribedChannel(val);
                    RefreshSubscribedChannelsUi();
                    ShowToast($"Subscribed to {val}");
                    prompt.Close();
                }
            };

            addBtn.Click += (s, ev) => doAdd();
            txtBox.KeyDown += (s, ev) =>
            {
                if (ev.Key == System.Windows.Input.Key.Enter) doAdd();
            };

            btnRow.Children.Add(cancelBtn);
            btnRow.Children.Add(addBtn);

            sp.Children.Add(heading);
            sp.Children.Add(desc);
            sp.Children.Add(txtBox);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            txtBox.Focus();
            prompt.ShowDialog();
        }

        private void ManageChannelsBtn_Click(object sender, RoutedEventArgs e)
        {
            var prompt = new Window
            {
                Title = "⚙️ Manage Subscriptions",
                Width = 480,
                Height = 440,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var grid = new Grid { Margin = new Thickness(18) };
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            var heading = new TextBlock
            {
                Text = "⚙️ Manage Your Subscribed Channels",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 8)
            };
            Grid.SetRow(heading, 0);

            var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Margin = new Thickness(0, 0, 0, 12) };
            var listStack = new StackPanel();

            void PopulateList()
            {
                listStack.Children.Clear();
                if (UserProfileData.SubscribedChannels.Count == 0)
                {
                    listStack.Children.Add(new TextBlock
                    {
                        Text = "No subscribed channels yet. Click '➕' in the sidebar to add your favorites!",
                        Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                        FontSize = 12,
                        Margin = new Thickness(0, 30, 0, 0),
                        HorizontalAlignment = HorizontalAlignment.Center
                    });
                    return;
                }

                foreach (var ch in UserProfileData.SubscribedChannels.ToList())
                {
                    var row = new Grid { Margin = new Thickness(0, 2, 0, 2) };
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                    var chText = new TextBlock
                    {
                        Text = "▶ " + ch,
                        Foreground = System.Windows.Media.Brushes.White,
                        FontSize = 12,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    Grid.SetColumn(chText, 0);

                    var del = new Button
                    {
                        Content = "Remove ✕",
                        Style = (Style)FindResource("GlassButton"),
                        FontSize = 10,
                        Padding = new Thickness(8, 3, 8, 3),
                        Tag = ch
                    };
                    Grid.SetColumn(del, 1);
                    del.Click += (s, ev) =>
                    {
                        if (s is FrameworkElement fe && fe.Tag is string c)
                        {
                            UserProfileData.RemoveSubscribedChannel(c);
                            RefreshSubscribedChannelsUi();
                            PopulateList();
                        }
                    };

                    row.Children.Add(chText);
                    row.Children.Add(del);
                    listStack.Children.Add(row);
                }
            }

            PopulateList();
            scroll.Content = listStack;
            Grid.SetRow(scroll, 1);

            var bottomBar = new Grid();
            bottomBar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            bottomBar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var leftBtns = new StackPanel { Orientation = Orientation.Horizontal };
            var clearAllBtn = new Button
            {
                Content = "Clear All Channels",
                Style = (Style)FindResource("GlassButton"),
                Foreground = (System.Windows.Media.Brush)FindResource("AccentRed"),
                FontSize = 11,
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            clearAllBtn.Click += (s, ev) =>
            {
                UserProfileData.ClearAllSubscribedChannels();
                RefreshSubscribedChannelsUi();
                PopulateList();
                ShowToast("Cleared all subscriptions");
            };

            var restoreBtn = new Button
            {
                Content = "Reset Defaults",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 11,
                Padding = new Thickness(10, 6, 10, 6)
            };
            restoreBtn.Click += (s, ev) =>
            {
                UserProfileData.RestoreDefaultChannels();
                RefreshSubscribedChannelsUi();
                PopulateList();
                ShowToast("Restored default channels");
            };

            var resetAlgoBtn = new Button
            {
                Content = "🤖 Reset Algorithm",
                Style = (Style)FindResource("GlassButton"),
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                FontSize = 11,
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(8, 0, 0, 0),
                ToolTip = "Reset learned recommendations, watch history affinity, and dislikes back to factory state"
            };
            resetAlgoBtn.Click += (s, ev) =>
            {
                prompt.Close();
                PromptResetAlgorithm();
            };

            leftBtns.Children.Add(clearAllBtn);
            leftBtns.Children.Add(restoreBtn);
            leftBtns.Children.Add(resetAlgoBtn);
            Grid.SetColumn(leftBtns, 0);

            var closeBtn = new Button
            {
                Content = "Done",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                FontSize = 11,
                Padding = new Thickness(16, 6, 16, 6)
            };
            closeBtn.Click += (s, ev) => prompt.Close();
            Grid.SetColumn(closeBtn, 1);

            bottomBar.Children.Add(leftBtns);
            bottomBar.Children.Add(closeBtn);
            Grid.SetRow(bottomBar, 2);

            grid.Children.Add(heading);
            grid.Children.Add(scroll);
            grid.Children.Add(bottomBar);

            prompt.Content = grid;
            prompt.ShowDialog();
        }

        private void ResetAlgorithmBtn_Click(object sender, RoutedEventArgs e)
        {
            PromptResetAlgorithm();
        }

        private void PromptResetAlgorithm()
        {
            var prompt = new Window
            {
                Title = "🤖 Reset Recommendation Algorithm",
                Width = 470,
                Height = 310,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var sp = new StackPanel { Margin = new Thickness(20) };

            var heading = new TextBlock
            {
                Text = "🤖 Reset Recommendation Algorithm?",
                FontSize = 15,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 10)
            };

            var body = new TextBlock
            {
                Text = "This action will reset your learned algorithm database back to factory state:\n\n" +
                       "• Clears watch history & learned keyword affinities\n" +
                       "• Clears disliked videos & disliked channel penalties\n" +
                       "• Clears saved playback resume positions\n\n" +
                       "🛡️ Your Subscriptions, Favorites, and Downloads are safe and will NOT be touched.",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                LineHeight = 17,
                Margin = new Thickness(0, 0, 0, 18)
            };

            var btnRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right
            };

            var cancelBtn = new Button
            {
                Content = "Cancel",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 11,
                Padding = new Thickness(16, 6, 16, 6),
                Margin = new Thickness(0, 0, 10, 0)
            };
            cancelBtn.Click += (s, ev) => prompt.Close();

            var confirmBtn = new Button
            {
                Content = "Yes, Reset Algorithm",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentRed"),
                Foreground = System.Windows.Media.Brushes.White,
                FontWeight = FontWeights.Bold,
                FontSize = 11,
                Padding = new Thickness(16, 6, 16, 6)
            };
            confirmBtn.Click += (s, ev) =>
            {
                prompt.Close();
                StorageService.ResetAlgorithm();
                ShowToast("✨ Algorithm reset to factory defaults! Refreshing feed...");
                NavHome_Click(this, new RoutedEventArgs());
            };

            btnRow.Children.Add(cancelBtn);
            btnRow.Children.Add(confirmBtn);

            sp.Children.Add(heading);
            sp.Children.Add(body);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void ClearHistoryBtn_Click(object sender, RoutedEventArgs e)
        {
            PromptClearWatchHistory();
        }

        private void PromptClearWatchHistory()
        {
            var prompt = new Window
            {
                Title = "🗑️ Clear Watch History",
                Width = 440,
                Height = 240,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var sp = new StackPanel { Margin = new Thickness(20) };

            var heading = new TextBlock
            {
                Text = "🗑️ Clear Entire Watch History?",
                FontSize = 15,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentRed"),
                Margin = new Thickness(0, 0, 0, 10)
            };

            var body = new TextBlock
            {
                Text = "Are you sure you want to clear your entire watch history?\n\n" +
                       "• All watched video records will be deleted.\n" +
                       "• Your Favorites, Subscriptions, and Downloads will NOT be touched.",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                LineHeight = 17,
                Margin = new Thickness(0, 0, 0, 18)
            };

            var btnRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Right
            };

            var cancelBtn = new Button
            {
                Content = "Cancel",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 11,
                Padding = new Thickness(16, 6, 16, 6),
                Margin = new Thickness(0, 0, 10, 0)
            };
            cancelBtn.Click += (s, ev) => prompt.Close();

            var confirmBtn = new Button
            {
                Content = "Yes, Clear History",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentRed"),
                Foreground = System.Windows.Media.Brushes.White,
                FontWeight = FontWeights.Bold,
                FontSize = 11,
                Padding = new Thickness(16, 6, 16, 6)
            };
            confirmBtn.Click += (s, ev) =>
            {
                prompt.Close();
                StorageService.ClearWatchHistory();
                _rawUnfilteredFeed = new List<VideoItem>();
                _currentFeed = new List<VideoItem>();
                if (VideoItemsControl != null)
                {
                    VideoItemsControl.ItemsSource = null;
                    VideoItemsControl.ItemsSource = _currentFeed;
                }
                ShowToast("🗑️ Watch history cleared");
            };

            btnRow.Children.Add(cancelBtn);
            btnRow.Children.Add(confirmBtn);

            sp.Children.Add(heading);
            sp.Children.Add(body);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void SubscribeToggleBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null || string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle)) return;
            var channel = _currentVideo.ChannelTitle.Trim();

            if (UserProfileData.IsSubscribed(channel))
            {
                UserProfileData.RemoveSubscribedChannel(channel);
                ShowToast($"Unsubscribed from {channel}");
            }
            else
            {
                UserProfileData.AddSubscribedChannel(channel);
                ShowToast($"Subscribed to {channel}!");
            }
            RefreshSubscribedChannelsUi();
        }

        private void UpdateSubscribeToggleBtn()
        {
            if (_currentVideo == null || string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle))
            {
                SubscribeToggleBtn.Visibility = Visibility.Collapsed;
                return;
            }

            SubscribeToggleBtn.Visibility = Visibility.Visible;
            bool isSubbed = UserProfileData.IsSubscribed(_currentVideo.ChannelTitle);
            if (isSubbed)
            {
                SubscribeToggleBtn.Content = "✓ Subscribed";
                SubscribeToggleBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
            }
            else
            {
                SubscribeToggleBtn.Content = "+ Subscribe";
                SubscribeToggleBtn.Foreground = System.Windows.Media.Brushes.White;
            }
        }

        private async void NavTrending_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            await LoadFeedAsync("🔥 Trending Videos", () => YouTubeService.SearchVideosAsync("Trending Worldwide", 30));
        }

        private void NavFavorites_Click(object sender, RoutedEventArgs e)
        {
            _isDiscoveryFeed = false;
            SwitchToFeedView();
            FeedTitleText.Text = "⭐ Favorite Videos";
            if (ClearHistoryBtn != null) ClearHistoryBtn.Visibility = Visibility.Collapsed;
            if (ResetAlgorithmBtn != null) ResetAlgorithmBtn.Visibility = Visibility.Visible;
            _rawUnfilteredFeed = StorageService.Settings.Favorites.ToList();
            ApplyCurrentFilters();
        }

        private void NavWatchLater_Click(object sender, RoutedEventArgs e)
        {
            _isDiscoveryFeed = false;
            SwitchToFeedView();
            FeedTitleText.Text = "🕒 Watch Later Queue";
            if (ClearHistoryBtn != null) ClearHistoryBtn.Visibility = Visibility.Collapsed;
            if (ResetAlgorithmBtn != null) ResetAlgorithmBtn.Visibility = Visibility.Visible;
            _rawUnfilteredFeed = StorageService.Settings.WatchLater.ToList();
            ApplyCurrentFilters();
        }

        private void NavHistory_Click(object sender, RoutedEventArgs e)
        {
            _isDiscoveryFeed = false;
            SwitchToFeedView();
            FeedTitleText.Text = "📜 Watch History";
            if (ClearHistoryBtn != null) ClearHistoryBtn.Visibility = Visibility.Visible;
            if (ResetAlgorithmBtn != null) ResetAlgorithmBtn.Visibility = Visibility.Collapsed;

            // Reset filter combos to Any so watch history is displayed fully in watch order
            if (DateFilterCombo != null) DateFilterCombo.SelectedIndex = 0;
            if (DurationFilterCombo != null) DurationFilterCombo.SelectedIndex = 0;

            _rawUnfilteredFeed = StorageService.Settings.WatchHistory.ToList();
            ApplyCurrentFilters();
        }

        private void NavDownloads_Click(object sender, RoutedEventArgs e)
        {
            _isDiscoveryFeed = false;
            SwitchToFeedView();
            FeedTitleText.Text = "💾 Downloaded Videos & Audio";
            if (ClearHistoryBtn != null) ClearHistoryBtn.Visibility = Visibility.Collapsed;
            if (ResetAlgorithmBtn != null) ResetAlgorithmBtn.Visibility = Visibility.Visible;
            _rawUnfilteredFeed = SyncLocalDownloadsFeed();
            ApplyCurrentFilters();
        }

        private List<VideoItem> SyncLocalDownloadsFeed()
        {
            var result = new List<VideoItem>();
            try
            {
                CleanExpiredDownloads();
                var folder = DownloadService.GetDownloadsFolder();
                if (System.IO.Directory.Exists(folder))
                {
                    var files = System.IO.Directory.GetFiles(folder, "*.*", System.IO.SearchOption.TopDirectoryOnly)
                        .Where(f => f.EndsWith(".mp4", StringComparison.OrdinalIgnoreCase) ||
                                    f.EndsWith(".m4a", StringComparison.OrdinalIgnoreCase) ||
                                    f.EndsWith(".mp3", StringComparison.OrdinalIgnoreCase) ||
                                    f.EndsWith(".mkv", StringComparison.OrdinalIgnoreCase) ||
                                    f.EndsWith(".webm", StringComparison.OrdinalIgnoreCase))
                        .OrderByDescending(System.IO.File.GetLastWriteTime)
                        .ToList();

                    var savedDownloads = StorageService.Settings.Downloads.ToList();

                    foreach (var file in files)
                    {
                        var fileNameNoExt = System.IO.Path.GetFileNameWithoutExtension(file);
                        var ext = System.IO.Path.GetExtension(file).ToUpperInvariant().TrimStart('.');
                        var match = System.Text.RegularExpressions.Regex.Match(fileNameNoExt, @"([a-zA-Z0-9_-]{11})$");
                        var videoId = match.Success ? match.Groups[1].Value : fileNameNoExt;

                        // Check if we have rich metadata stored
                        var existing = savedDownloads.FirstOrDefault(d =>
                            d.Id.Equals(videoId, StringComparison.OrdinalIgnoreCase) ||
                            fileNameNoExt.Contains(d.Id, StringComparison.OrdinalIgnoreCase));

                        if (existing != null)
                        {
                            existing.LocalFilePath = file;
                            existing.IsDownloaded = true;
                            result.Add(existing);
                        }
                        else
                        {
                            var cleanTitle = match.Success
                                ? fileNameNoExt.Substring(0, match.Index).Trim('_', ' ', '-')
                                : fileNameNoExt;
                            if (string.IsNullOrWhiteSpace(cleanTitle)) cleanTitle = fileNameNoExt;

                            var fi = new System.IO.FileInfo(file);
                            var sizeMb = (fi.Length / (1024.0 * 1024.0)).ToString("0.1") + " MB";

                            result.Add(new VideoItem
                            {
                                Id = videoId,
                                Title = cleanTitle,
                                ChannelTitle = $"💾 Local {ext}",
                                UploadDateText = fi.LastWriteTime.ToString("yyyy-MM-dd"),
                                ViewCountText = sizeMb,
                                DurationText = ext,
                                ThumbnailUrl = match.Success ? $"https://i.ytimg.com/vi/{videoId}/hqdefault.jpg" : "",
                                LocalFilePath = file,
                                IsDownloaded = true
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Downloads] Error loading local files: {ex.Message}");
            }

            // Also include any other stored downloads not matched above
            foreach (var d in StorageService.Settings.Downloads)
            {
                if (!result.Any(r => r.Id.Equals(d.Id, StringComparison.OrdinalIgnoreCase)))
                {
                    result.Add(d);
                }
            }

            return result;
        }

        private async void SwitchToFeedView()
        {
            PlayerView.Visibility = Visibility.Collapsed;
            FeedView.Visibility = Visibility.Visible;
            _sponsorBlockTimer?.Stop();

            // When returning to the feed, ensure the watched video is removed from the visible list (unless in History, Downloads, or Series/Playlist feeds)
            if (_currentVideo != null && FeedTitleText?.Text != "📜 Watch History" && FeedTitleText?.Text != "💾 Downloaded Videos & Audio" && (FeedTitleText == null || !FeedTitleText.Text.StartsWith("📺")) && (FeedTitleText == null || !FeedTitleText.Text.StartsWith("📋")))
            {
                var targetId = _currentVideo.Id;
                if (!string.IsNullOrEmpty(targetId))
                {
                    _rawUnfilteredFeed?.RemoveAll(v => v.Id == targetId);
                    if (_currentFeed != null)
                    {
                        var removed = _currentFeed.RemoveAll(v => v.Id == targetId);
                        if (removed > 0 && VideoItemsControl != null)
                        {
                            VideoItemsControl.ItemsSource = null;
                            VideoItemsControl.ItemsSource = _currentFeed;
                        }
                    }
                }
            }

            try
            {
                if (VideoWebView?.CoreWebView2 != null)
                {
                    // Save playback position
                    if (_currentVideo != null)
                    {
                        var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                        if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double sec) && sec > 1)
                        {
                            StorageService.SavePlaybackPosition(_currentVideo.Id, sec);
                        }
                    }

                    // Immediately pause video and all media elements so it stops playing in the background
                    await VideoWebView.ExecuteScriptAsync("document.querySelectorAll('video, audio').forEach(m => { m.pause(); }); if (window.player && typeof window.player.pauseVideo === 'function') window.player.pauseVideo();");
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error pausing video on SwitchToFeedView: {ex.Message}");
            }
        }

        private void SwitchToPlayerView()
        {
            FeedView.Visibility = Visibility.Collapsed;
            PlayerView.Visibility = Visibility.Visible;
        }

        private async void SearchButton_Click(object sender, RoutedEventArgs e)
        {
            await PerformSearchAsync();
        }

        private void ClearSearchBtn_Click(object sender, RoutedEventArgs e)
        {
            SearchBox.Text = "";
            SearchBox.Focus();
        }

        private void SearchHistoryToggle_Click(object sender, RoutedEventArgs e)
        {
            ToggleSearchHistoryPopup();
        }

        private void ToggleSearchHistoryPopup()
        {
            if (SearchHistoryPopup == null) return;
            if (SearchHistoryPopup.IsOpen)
            {
                SearchHistoryPopup.IsOpen = false;
            }
            else
            {
                UpdateSearchHistoryList();
                SearchHistoryPopup.IsOpen = true;
            }
        }

        private void UpdateSearchHistoryList()
        {
            if (SearchHistoryItemsControl == null) return;
            var history = StorageService.Settings.SearchHistory;
            SearchHistoryItemsControl.ItemsSource = null;
            SearchHistoryItemsControl.ItemsSource = history.ToList();

            if (SearchHistoryEmptyText != null)
            {
                SearchHistoryEmptyText.Visibility = history.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
            }
        }

        private async void SearchHistoryItem_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.DataContext is string query)
            {
                if (SearchHistoryPopup != null) SearchHistoryPopup.IsOpen = false;
                SearchBox.Text = query;
                await PerformSearchAsync();
            }
        }

        private void RemoveSearchHistoryItem_Click(object sender, RoutedEventArgs e)
        {
            e.Handled = true;
            if (sender is Button btn && btn.Tag is string query)
            {
                StorageService.RemoveSearchHistoryItem(query);
                UpdateSearchHistoryList();
            }
        }

        private void ClearAllSearchHistory_Click(object sender, RoutedEventArgs e)
        {
            StorageService.ClearSearchHistory();
            UpdateSearchHistoryList();
            ShowToast("🗑️ Search history cleared");
        }

        private async void SearchBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await PerformSearchAsync();
            }
        }

        private async Task PerformSearchAsync()
        {
            var query = SearchBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(query)) return;

            if (SearchHistoryPopup != null) SearchHistoryPopup.IsOpen = false;
            StorageService.AddSearchHistory(query);

            // Direct YouTube URL or Video ID detection -> play instantly!
            var extractedId = ExtractYouTubeVideoId(query);
            if (!string.IsNullOrEmpty(extractedId))
            {
                var video = await YouTubeService.GetVideoDetailsAsync(extractedId) ?? new VideoItem
                {
                    Id = extractedId,
                    Title = "YouTube Video (" + extractedId + ")",
                    ChannelTitle = "YouTube",
                    ThumbnailUrl = $"https://i.ytimg.com/vi/{extractedId}/hqdefault.jpg"
                };
                await PlayVideoAsync(video);
                return;
            }

            await PerformSearchWithFiltersAsync();
        }

        private string? _currentSearchQuery = null;
        private bool _isLoadingMore = false;

        private async Task PerformSearchWithFiltersAsync()
        {
            var query = SearchBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(query)) return;

            _currentSearchQuery = query;

            var dateTag = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var durationTag = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var sortByTag = (SortByFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

            string? spParam = null;
            if (sortByTag == "latest") spParam = "CAISAhAB";
            else if (sortByTag == "views") spParam = "CAM%3D";
            else if (dateTag == "today") spParam = "EgIIAg%3D%3D";
            else if (dateTag == "week") spParam = "EgIIAw%3D%3D";
            else if (dateTag == "month") spParam = "EgIIBA%3D%3D";
            else if (durationTag == "short") spParam = "EgQQARgB";
            else if (durationTag == "medium") spParam = "EgQQARgD";
            else if (durationTag == "long") spParam = "EgQQARgC";

            LoadingSpinner.Visibility = Visibility.Visible;
            _isDiscoveryFeed = false;
            SwitchToFeedView();
            FeedTitleText.Text = $"🔍 Search: \"{query}\"";

            // If query matches a creator or subscribed channel (or is 1-3 words), also query channel directly in parallel
            var isCreator = UserProfileData.IsSubscribed(query) ||
                            YouTubeService.VerifiedHandles.ContainsKey(query) ||
                            query.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries).Length <= 3;

            Task<List<VideoItem>>? channelTask = null;
            if (isCreator)
            {
                channelTask = YouTubeService.GetChannelVideosFeedAsync(query);
            }

            var searchTask = YouTubeService.SearchVideosAsync(query, 50, spFilter: spParam, sortByUploadDate: sortByTag == "latest");

            var tasks = channelTask != null ? new[] { searchTask, channelTask } : new[] { searchTask };
            await Task.WhenAll(tasks);

            var combined = new List<VideoItem>();
            if (channelTask != null && channelTask.Result != null)
            {
                combined.AddRange(channelTask.Result);
            }
            if (searchTask.Result != null)
            {
                combined.AddRange(searchTask.Result);
            }

            var uniqueVideos = combined.GroupBy(v => v.Id).Select(g => g.First()).ToList();

            // Rank with dominant recency scoring so fresh uploads from minutes/hours ago appear at top!
            var ranked = RecommendationEngine.ScoreAndRankVideos(
                uniqueVideos,
                StorageService.Settings.Favorites,
                StorageService.Settings.WatchHistory,
                UserProfileData.SubscribedChannels
            );

            _rawUnfilteredFeed = ranked;
            _currentFeed = YouTubeService.ApplyLocalFilters(ranked, dateTag, durationTag, sortByTag);
            VideoItemsControl.ItemsSource = _currentFeed;
            LoadingSpinner.Visibility = Visibility.Collapsed;
        }

        private async void FeedScrollViewer_ScrollChanged(object sender, ScrollChangedEventArgs e)
        {
            if (FeedView.Visibility != Visibility.Visible || _isLoadingMore) return;

            // Trigger when scrolled to bottom 85%
            if (e.VerticalChange > 0 && e.VerticalOffset >= e.ExtentHeight - e.ViewportHeight - 350)
            {
                await LoadNextPageOfVideosAsync();
            }
        }

        private async void LoadMoreVideosBtn_Click(object sender, RoutedEventArgs e)
        {
            await LoadNextPageOfVideosAsync();
        }

        private async Task LoadNextPageOfVideosAsync()
        {
            if (_isLoadingMore || _rawUnfilteredFeed == null || _rawUnfilteredFeed.Count == 0) return;
            _isLoadingMore = true;

            if (InfiniteScrollSpinner != null) InfiniteScrollSpinner.Visibility = Visibility.Visible;
            if (LoadMoreVideosBtn != null) LoadMoreVideosBtn.IsEnabled = false;

            try
            {
                var existingIds = new HashSet<string>(_rawUnfilteredFeed.Select(v => v.Id));
                List<VideoItem> moreVideos = new List<VideoItem>();

                if (!string.IsNullOrWhiteSpace(_currentSearchQuery))
                {
                    moreVideos = await YouTubeService.FetchNextSearchBatchAsync(_currentSearchQuery, existingIds, 35);
                }
                else if (_isDiscoveryFeed)
                {
                    _feedBatchIndex++;
                    var nextBatch = await YouTubeService.FetchSubscribedProfileFeedAsync(batchIndex: _feedBatchIndex, batchSize: 25);
                    var ranked = RecommendationEngine.ScoreAndRankVideos(
                        nextBatch,
                        StorageService.Settings.Favorites,
                        StorageService.Settings.WatchHistory,
                        UserProfileData.SubscribedChannels
                    );
                    moreVideos = ranked.Where(v => !existingIds.Contains(v.Id)).ToList();
                }
                else
                {
                    moreVideos = await YouTubeService.FetchNextSearchBatchAsync("Trending Worldwide", existingIds, 35);
                }

                if (moreVideos.Count > 0)
                {
                    _rawUnfilteredFeed.AddRange(moreVideos);
                    ApplyCurrentFilters();
                    ShowToast($"✨ Loaded {moreVideos.Count} more videos (Total: {_currentFeed.Count})");
                }
                else
                {
                    ShowToast("ℹ️ All available videos loaded");
                }
            }
            catch (Exception ex)
            {
                ShowToast($"⚠️ Could not load more: {ex.Message}");
            }
            finally
            {
                _isLoadingMore = false;
                if (InfiniteScrollSpinner != null) InfiniteScrollSpinner.Visibility = Visibility.Collapsed;
                if (LoadMoreVideosBtn != null) LoadMoreVideosBtn.IsEnabled = true;
            }
        }

        public static string? ExtractYouTubeVideoId(string input)
        {
            if (string.IsNullOrWhiteSpace(input)) return null;

            input = input.Trim();

            // 1. Check if it's already an 11-character video ID
            if (System.Text.RegularExpressions.Regex.IsMatch(input, @"^[a-zA-Z0-9_-]{11}$"))
            {
                return input;
            }

            // 2. youtu.be/ID (e.g. https://youtu.be/u0JVCVOIePo?si=...)
            var matchShort = System.Text.RegularExpressions.Regex.Match(input, @"youtu\.be\/([a-zA-Z0-9_-]{11})");
            if (matchShort.Success)
            {
                return matchShort.Groups[1].Value;
            }

            // 3. youtube.com/watch?v=ID or /embed/ID or /shorts/ID or /live/ID
            var matchStandard = System.Text.RegularExpressions.Regex.Match(input, @"(?:v=|embed\/|shorts\/|live\/)([a-zA-Z0-9_-]{11})");
            if (matchStandard.Success)
            {
                return matchStandard.Groups[1].Value;
            }

            return null;
        }

        /// <summary>
        /// Extracts a YouTube channel handle or ID from a URL.
        /// Returns the handle (e.g. "@MrBeast"), channel ID ("UCxxxxxx"), or name for /c/ and /user/ paths.
        /// Returns null if the URL is not a channel URL.
        /// </summary>
        public static string? ExtractYouTubeChannelHandle(string input)
        {
            if (string.IsNullOrWhiteSpace(input)) return null;

            // /@handle  e.g. https://www.youtube.com/@MrBeast
            var matchHandle = System.Text.RegularExpressions.Regex.Match(
                input, @"youtube\.com\/@([a-zA-Z0-9_\-\.]+)");
            if (matchHandle.Success)
                return "@" + matchHandle.Groups[1].Value;

            // /channel/UCxxxxxx
            var matchChannel = System.Text.RegularExpressions.Regex.Match(
                input, @"youtube\.com\/channel\/(UC[a-zA-Z0-9_\-]{22})");
            if (matchChannel.Success)
                return matchChannel.Groups[1].Value;

            // /c/ChannelName  or  /user/Username
            var matchCOrUser = System.Text.RegularExpressions.Regex.Match(
                input, @"youtube\.com\/(?:c|user)\/([a-zA-Z0-9_\-\.]+)");
            if (matchCOrUser.Success)
                return matchCOrUser.Groups[1].Value;

            return null;
        }

        #endregion
    }
}
