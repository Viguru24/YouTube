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
        #region Video & Audio Download & Screenshot

        private void DownloadVideoBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null)
            {
                ShowToast("⚠️ No video is currently playing");
                return;
            }
            if (DownloadChoicePopup != null)
            {
                DownloadChoicePopup.IsOpen = !DownloadChoicePopup.IsOpen;
            }
        }

        private void CloseDownloadPopup_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
        }

        private void OpenDownloadsFolder_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
            try
            {
                var downloadsFolder = DownloadService.GetDownloadsFolder();
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"\"{downloadsFolder}\"",
                    UseShellExecute = true
                });
                ShowToast($"📂 Opening {Path.GetFileName(downloadsFolder)}...");
            }
            catch (Exception ex)
            {
                ShowToast($"⚠️ Could not open folder: {ex.Message}");
            }
        }

        private async Task<string?> ExportWebViewCookiesAsync()
        {
            try
            {
                if (VideoWebView?.CoreWebView2 != null)
                {
                    var wvCookies = await VideoWebView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.youtube.com");
                    if (wvCookies != null && wvCookies.Count > 0)
                    {
                        return DownloadService.WriteNetscapeCookieFile(wvCookies);
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Downloads] Cookie export failed: {ex.Message}");
            }
            return null;
        }

        private async void DownloadMp4Choice_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
            if (_currentVideo == null)
            {
                ShowToast("⚠️ No video is currently playing");
                return;
            }
            var cookiePath = await ExportWebViewCookiesAsync();
            DownloadQueueService.Instance.Enqueue(_currentVideo, isVideo: true, cookiePath);
            UpdateDownloadQueueBadge();
            ShowToast($"📥 Enqueued MP4 download: {_currentVideo.Title}");
        }

        private async void DownloadMp3Choice_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
            if (_currentVideo == null)
            {
                ShowToast("⚠️ No video is currently playing");
                return;
            }
            var cookiePath = await ExportWebViewCookiesAsync();
            DownloadQueueService.Instance.Enqueue(_currentVideo, isVideo: false, cookiePath);
            UpdateDownloadQueueBadge();
            ShowToast($"🎵 Enqueued MP3 audio extraction: {_currentVideo.Title}");
        }

        public void UpdateDownloadQueueBadge()
        {
            var activeCount = DownloadQueueService.Instance.Tasks.Count(t => t.Status == DownloadStatus.Downloading || t.Status == DownloadStatus.Queued);
            if (DownloadQueueBtn != null)
            {
                DownloadQueueBtn.Content = activeCount > 0 ? $"📥 Queue ({activeCount})" : "📥 Queue";
                DownloadQueueBtn.Foreground = activeCount > 0 ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            }
        }

        private void DownloadQueueBtn_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadQueueItemsControl != null)
            {
                DownloadQueueItemsControl.ItemsSource = DownloadQueueService.Instance.Tasks;
            }
            if (DownloadQueuePopup != null)
            {
                DownloadQueuePopup.IsOpen = !DownloadQueuePopup.IsOpen;
            }
        }

        private void CloseDownloadQueuePopup_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadQueuePopup != null)
            {
                DownloadQueuePopup.IsOpen = false;
            }
        }

        private void ClearCompletedDownloads_Click(object sender, RoutedEventArgs e)
        {
            DownloadQueueService.Instance.ClearCompleted();
            UpdateDownloadQueueBadge();
            ShowToast("🧹 Completed downloads cleared");
        }

        private void PauseDownloadTask_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button b && b.DataContext is DownloadTask task)
            {
                DownloadQueueService.Instance.Pause(task.Id);
                UpdateDownloadQueueBadge();
                ShowToast($"⏸️ Paused: {task.Title}");
            }
        }

        private async void ResumeDownloadTask_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button b && b.DataContext is DownloadTask task)
            {
                var cookiePath = await ExportWebViewCookiesAsync();
                DownloadQueueService.Instance.Resume(task.Id, cookiePath);
                UpdateDownloadQueueBadge();
                ShowToast($"▶ Resumed: {task.Title}");
            }
        }

        private async void RetryDownloadTask_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button b && b.DataContext is DownloadTask task)
            {
                var cookiePath = await ExportWebViewCookiesAsync();
                DownloadQueueService.Instance.Retry(task.Id, cookiePath);
                UpdateDownloadQueueBadge();
                ShowToast($"🔄 Retrying: {task.Title}");
            }
        }

        private void CancelDownloadTask_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button b && b.DataContext is DownloadTask task)
            {
                DownloadQueueService.Instance.Cancel(task.Id);
                UpdateDownloadQueueBadge();
                ShowToast($"✕ Removed: {task.Title}");
            }
        }

        private bool _isDownloading = false;

        private async Task DownloadCurrentVideoAsync(bool isVideo = true)
        {
            if (_currentVideo == null)
            {
                ShowToast("⚠️ No video is currently playing");
                return;
            }

            if (_isDownloading)
            {
                ShowToast("⏳ Download already in progress...");
                return;
            }

            _isDownloading = true;
            DownloadVideoBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
            ShowToast(isVideo ? "📥 Fetching video streams..." : "🎵 Extracting HQ audio...");

            string? tempCookiePath = null;
            try
            {
                var progressHandler = new Progress<double>(p =>
                {
                    var percent = (int)(p * 100);
                    if (percent % 10 == 0 || percent == 100)
                    {
                        Dispatcher.Invoke(() => ShowToast(isVideo ? $"📥 Downloading MP4: {percent}%" : $"🎵 Downloading Audio: {percent}%"));
                    }
                });

                // Export current WebView2 session cookies so age-restricted videos can be authenticated by yt-dlp
                try
                {
                    if (VideoWebView?.CoreWebView2 != null)
                    {
                        var wvCookies = await VideoWebView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.youtube.com");
                        if (wvCookies != null && wvCookies.Count > 0)
                        {
                            tempCookiePath = DownloadService.WriteNetscapeCookieFile(wvCookies);
                        }
                    }
                }
                catch (Exception exCookie)
                {
                    System.Diagnostics.Debug.WriteLine($"[Vixz] Cookie export for download failed: {exCookie.Message}");
                }

                var filePath = isVideo 
                    ? await DownloadService.DownloadVideoAsync(_currentVideo.Id, _currentVideo.Title, progressHandler, tempCookiePath)
                    : await DownloadService.DownloadAudioAsync(_currentVideo.Id, _currentVideo.Title, progressHandler, tempCookiePath);

                _currentVideo.LocalFilePath = filePath;
                _currentVideo.IsDownloaded = true;
                StorageService.AddDownload(_currentVideo);

                ShowToast(isVideo ? "✅ Downloaded! ⚡ Switched to local offline playback" : "✅ Audio Download Complete!");

                // Seamlessly switch active player to local downloaded file
                if (isVideo && System.IO.File.Exists(filePath) && _currentVideo != null)
                {
                    double currentSec = 0;
                    try
                    {
                        if (VideoWebView != null)
                        {
                            var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                            double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out currentSec);
                        }
                    }
                    catch { }
                    await PlayLocalVideoInWebViewAsync(filePath, _currentVideo.Id, currentSec);
                }

                var typeName = isVideo ? "Video" : "Audio";
                var result = MessageBox.Show($"{typeName} downloaded successfully!\n\nSaved to: {filePath}\n\nWould you like to open the folder?", "Download Complete", MessageBoxButton.YesNo, MessageBoxImage.Information);
                if (result == MessageBoxResult.Yes)
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "explorer.exe",
                        Arguments = $"/select,\"{filePath}\"",
                        UseShellExecute = true
                    });
                }
            }
            catch (Exception ex)
            {
                if (ex.Message.Contains("DRM_PROTECTED_CONTENT") || ex.Message.Contains("DRM protected", StringComparison.OrdinalIgnoreCase) || ex.Message.Contains("Widevine", StringComparison.OrdinalIgnoreCase))
                {
                    MessageBox.Show("🎬 YouTube Movies ('Free with Ads' & rentals) are protected by YouTube's Widevine DRM copyright encryption.\n\nOffline file downloading is restricted by YouTube policy for copyrighted movies, but you can enjoy streaming it directly in Vixz completely ad-free!", "DRM Protected Content", MessageBoxButton.OK, MessageBoxImage.Information);
                    ShowToast("ℹ️ DRM-protected movie (streaming only)");
                }
                else
                {
                    ShowToast($"⚠️ Download failed: {ex.Message}");
                }
            }
            finally
            {
                if (!string.IsNullOrWhiteSpace(tempCookiePath) && System.IO.File.Exists(tempCookiePath))
                {
                    try { System.IO.File.Delete(tempCookiePath); } catch { }
                }
                _isDownloading = false;
                DownloadVideoBtn.Foreground = System.Windows.Media.Brushes.White;
            }
        }

        private async void ScreenshotBtn_Click(object sender, RoutedEventArgs e)
        {
            await CaptureScreenshotAsync();
        }

        private async Task CaptureScreenshotAsync()
        {
            // Shutter Flash Animation
            TriggerShutterFlash();

            double currentSec = 0;
            try
            {
                var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out currentSec);
            }
            catch { }

            var path = await ScreenshotService.CaptureAndSaveAsync(
                VideoWebView,
                _frameTracker.Frames,
                PlayerContainer,
                _currentVideo?.Title ?? "Video",
                currentSec,
                StorageService.Settings.ActiveScreenshotFolder
            );

            if (path != null)
            {
                var folderDisplay = System.IO.Path.GetDirectoryName(path) ?? ScreenshotService.GetTargetDirectory(StorageService.Settings.ActiveScreenshotFolder);
                ShowToast($"📸 Saved to {folderDisplay}");
            }
            else
            {
                ShowToast("⚠️ Failed to capture screenshot");
            }
        }

        private void TriggerShutterFlash()
        {
            var anim = new DoubleAnimation(0.85, 0.0, TimeSpan.FromMilliseconds(200));
            ShutterFlash.BeginAnimation(OpacityProperty, anim);
        }

        private void ChangeFolder_Click(object sender, RoutedEventArgs e)
        {
            UpdateFolderUi();
            FolderListBox.ItemsSource = StorageService.Settings.ScreenshotFolders.ToList();
            FolderListBox.SelectedItem = StorageService.Settings.ActiveScreenshotFolder;
            FolderPopup.IsOpen = true;
        }

        private void CloseFolderModal_Click(object sender, RoutedEventArgs e)
        {
            FolderPopup.IsOpen = false;
        }

        private void BrowseAndSelectFolder_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                var dialog = new Microsoft.Win32.OpenFolderDialog
                {
                    Title = "Select Screenshot Destination Folder",
                    InitialDirectory = ScreenshotService.GetTargetDirectory(),
                    Multiselect = false
                };

                if (dialog.ShowDialog() == true)
                {
                    var selectedPath = dialog.FolderName;
                    if (!string.IsNullOrWhiteSpace(selectedPath))
                    {
                        var folderName = System.IO.Path.GetFileName(selectedPath);
                        if (string.IsNullOrWhiteSpace(folderName)) folderName = selectedPath;

                        StorageService.Settings.CustomScreenshotPath = selectedPath;
                        StorageService.Settings.CustomFolderPaths[folderName] = selectedPath;

                        if (!StorageService.Settings.ScreenshotFolders.Contains(folderName))
                        {
                            StorageService.Settings.ScreenshotFolders.Insert(0, folderName);
                        }

                        StorageService.Settings.ActiveScreenshotFolder = folderName;
                        StorageService.Save();
                        UpdateFolderUi();
                        FolderListBox.ItemsSource = StorageService.Settings.ScreenshotFolders.ToList();
                        FolderListBox.SelectedItem = folderName;
                        FolderPopup.IsOpen = false;
                        ShowToast($"📸 Screenshot folder set to {folderName}");
                    }
                }
            }
            catch (Exception ex)
            {
                ShowToast($"Error opening folder picker: {ex.Message}");
            }
        }

        private void FolderListBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (FolderListBox.SelectedItem is string selected)
            {
                StorageService.Settings.ActiveScreenshotFolder = selected;
                if (StorageService.Settings.CustomFolderPaths.TryGetValue(selected, out var customPath) && !string.IsNullOrWhiteSpace(customPath))
                {
                    StorageService.Settings.CustomScreenshotPath = customPath;
                }
                else if (selected == "Default" || selected == "Screenshots" || selected == "Favorites" || selected == "Recipes" || selected == "Notes" || selected == "Tutorials")
                {
                    StorageService.Settings.CustomScreenshotPath = null;
                }
                StorageService.Save();
                UpdateFolderUi();
                ShowToast($"Active folder: {selected}");
            }
        }

        private void AddNewFolder_Click(object sender, RoutedEventArgs e)
        {
            var newName = NewFolderInput.Text.Trim();
            if (!string.IsNullOrWhiteSpace(newName))
            {
                if (!StorageService.Settings.ScreenshotFolders.Contains(newName))
                {
                    StorageService.Settings.ScreenshotFolders.Add(newName);
                }
                StorageService.Settings.ActiveScreenshotFolder = newName;
                StorageService.Settings.CustomScreenshotPath = null;
                StorageService.Save();
                UpdateFolderUi();
                FolderListBox.ItemsSource = StorageService.Settings.ScreenshotFolders.ToList();
                FolderListBox.SelectedItem = newName;
                NewFolderInput.Text = "";
                ShowToast($"Created & Selected: {newName}");
            }
        }

        private void OpenFolder_Click(object sender, RoutedEventArgs e)
        {
            ScreenshotService.OpenFolderInExplorer();
        }

        private void UpdateFolderUi()
        {
            var targetDir = ScreenshotService.GetTargetDirectory();
            if (CurrentFolderText != null) CurrentFolderText.Text = targetDir;
            if (PopupCurrentFolderText != null) PopupCurrentFolderText.Text = targetDir;
            UpdateRetentionUi();
        }

        private void RetentionOption_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string tag)
            {
                StorageService.Settings.DownloadRetention = tag;
                StorageService.Save();
                UpdateRetentionUi();
                CleanExpiredDownloads();
                var label = tag == "Never" ? "Permanently" : tag;
                ShowToast($"⏱️ Download retention set to {label}");
            }
        }

        private void UpdateRetentionUi()
        {
            var retention = StorageService.Settings.DownloadRetention ?? "Never";
            var label = retention switch
            {
                "24h" => "24 Hours",
                "48h" => "48 Hours",
                "7d" => "7 Days",
                "30d" => "30 Days",
                "Watched" => "Watched",
                _ => "Permanently"
            };

            if (PopupRetentionBadge != null)
            {
                PopupRetentionBadge.Text = $"⏱️ {label}";
                PopupRetentionBadge.Foreground = label == "Permanently"
                    ? new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#81C784"))
                    : new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FFB74D"));
            }

            var activeBg = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#3E2D54"));
            var normalBg = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#222032"));

            if (RetentionNeverBtn != null) RetentionNeverBtn.Background = retention.Equals("Never", StringComparison.OrdinalIgnoreCase) ? activeBg : normalBg;
            if (Retention24hBtn != null) Retention24hBtn.Background = retention.Equals("24h", StringComparison.OrdinalIgnoreCase) ? activeBg : normalBg;
            if (Retention48hBtn != null) Retention48hBtn.Background = retention.Equals("48h", StringComparison.OrdinalIgnoreCase) ? activeBg : normalBg;
            if (Retention7dBtn != null) Retention7dBtn.Background = retention.Equals("7d", StringComparison.OrdinalIgnoreCase) ? activeBg : normalBg;
            if (Retention30dBtn != null) Retention30dBtn.Background = retention.Equals("30d", StringComparison.OrdinalIgnoreCase) ? activeBg : normalBg;
            if (RetentionWatchedBtn != null) RetentionWatchedBtn.Background = retention.Equals("Watched", StringComparison.OrdinalIgnoreCase) ? activeBg : normalBg;
        }

        private void CleanExpiredDownloads()
        {
            var retention = StorageService.Settings.DownloadRetention ?? "Never";
            if (retention.Equals("Never", StringComparison.OrdinalIgnoreCase) || retention.Equals("Permanently", StringComparison.OrdinalIgnoreCase))
            {
                return; // Downloads permanently remain in download folder
            }

            try
            {
                var folder = DownloadService.GetDownloadsFolder();
                if (!System.IO.Directory.Exists(folder)) return;

                var threshold = retention switch
                {
                    "24h" => TimeSpan.FromHours(24),
                    "48h" => TimeSpan.FromHours(48),
                    "7d"  => TimeSpan.FromDays(7),
                    "30d" => TimeSpan.FromDays(30),
                    _     => TimeSpan.MaxValue
                };

                var files = System.IO.Directory.GetFiles(folder, "*.*", System.IO.SearchOption.TopDirectoryOnly)
                    .Where(f => f.EndsWith(".mp4", StringComparison.OrdinalIgnoreCase) ||
                                f.EndsWith(".m4a", StringComparison.OrdinalIgnoreCase) ||
                                f.EndsWith(".mp3", StringComparison.OrdinalIgnoreCase) ||
                                f.EndsWith(".mkv", StringComparison.OrdinalIgnoreCase) ||
                                f.EndsWith(".webm", StringComparison.OrdinalIgnoreCase))
                    .ToList();

                var now = DateTime.Now;
                var watchedIds = StorageService.Settings.WatchHistory.Select(h => h.Id).ToHashSet(StringComparer.OrdinalIgnoreCase);

                foreach (var file in files)
                {
                    bool shouldDelete = false;
                    var fileTime = System.IO.File.GetLastWriteTime(file);

                    if (retention.Equals("Watched", StringComparison.OrdinalIgnoreCase))
                    {
                        var fileNameNoExt = System.IO.Path.GetFileNameWithoutExtension(file);
                        var match = System.Text.RegularExpressions.Regex.Match(fileNameNoExt, @"([a-zA-Z0-9_-]{11})$");
                        var videoId = match.Success ? match.Groups[1].Value : fileNameNoExt;
                        if (watchedIds.Contains(videoId))
                        {
                            shouldDelete = true;
                        }
                    }
                    else if (threshold < TimeSpan.MaxValue && (now - fileTime) >= threshold)
                    {
                        shouldDelete = true;
                    }

                    if (shouldDelete)
                    {
                        try { System.IO.File.Delete(file); } catch { }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[CleanExpiredDownloads] Error: {ex.Message}");
            }
        }

        #endregion
    }
}
