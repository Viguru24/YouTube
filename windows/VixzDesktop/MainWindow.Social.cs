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
        #region Share Modal & Social Handlers

        private int _shareCurrentSeconds = 0;
        private VideoItem? _shareTargetVideo = null;

        private async void ShareBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null)
            {
                ShowToast("No video currently playing ⚠️");
                return;
            }

            int currentSec = 0;
            try
            {
                if (VideoWebView?.CoreWebView2 != null)
                {
                    var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                    if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double parsedSec))
                    {
                        currentSec = (int)parsedSec;
                    }
                }
            }
            catch {}

            OpenShareModal(_currentVideo, currentSec);
        }

        private void OpenShareModal(VideoItem video, int startSeconds)
        {
            // Close other popups
            if (SleepTimerPopup != null) SleepTimerPopup.IsOpen = false;
            if (FolderPopup != null) FolderPopup.IsOpen = false;
            if (QualityPopup != null) QualityPopup.IsOpen = false;
            if (AccountPopup != null) AccountPopup.IsOpen = false;

            _shareTargetVideo = video;
            _shareCurrentSeconds = Math.Max(0, startSeconds);

            if (SharePopupVideoTitle != null) SharePopupVideoTitle.Text = video.Title;
            if (SharePopupVideoChannel != null) SharePopupVideoChannel.Text = video.ChannelTitle;

            if (ShareAtCurrentTimeCheckBox != null)
            {
                ShareAtCurrentTimeCheckBox.IsChecked = false;
                ShareAtCurrentTimeCheckBox.Content = $"Start at current time ({FormatPlaybackTime(_shareCurrentSeconds)})";
                ShareAtCurrentTimeCheckBox.Visibility = _shareCurrentSeconds > 0 ? Visibility.Visible : Visibility.Collapsed;
            }

            UpdateShareUrl();

            if (SharePopup != null)
            {
                SharePopup.IsOpen = true;
            }
        }

        private void UpdateShareUrl()
        {
            if (_shareTargetVideo == null) return;

            string baseUrl = $"https://youtu.be/{_shareTargetVideo.Id}";
            if (ShareAtCurrentTimeCheckBox?.IsChecked == true && _shareCurrentSeconds > 0)
            {
                baseUrl += $"?t={_shareCurrentSeconds}";
            }

            if (ShareUrlTextBox != null)
            {
                ShareUrlTextBox.Text = baseUrl;
            }
        }

        private string FormatPlaybackTime(int seconds)
        {
            var ts = TimeSpan.FromSeconds(Math.Max(0, seconds));
            return ts.Hours > 0 ? ts.ToString(@"h\:mm\:ss") : ts.ToString(@"m\:ss");
        }

        private void CloseShareModal_Click(object sender, RoutedEventArgs e)
        {
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareTimestamp_Changed(object sender, RoutedEventArgs e)
        {
            UpdateShareUrl();
        }

        private async void CopyShareLink_Click(object sender, RoutedEventArgs e)
        {
            var url = ShareUrlTextBox?.Text;
            if (!string.IsNullOrWhiteSpace(url))
            {
                Clipboard.SetText(url);
                ShowToast("Link copied to clipboard! 📋✨");

                if (CopyShareLinkBtn != null)
                {
                    var orig = CopyShareLinkBtn.Content;
                    CopyShareLinkBtn.Content = "✓ Copied!";
                    await System.Threading.Tasks.Task.Delay(1500);
                    CopyShareLinkBtn.Content = orig;
                }
            }
        }

        private void ShareWhatsApp_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var text = $"Check out \"{_shareTargetVideo.Title}\" by {_shareTargetVideo.ChannelTitle}: {url}";
            var waUrl = $"https://api.whatsapp.com/send?text={Uri.EscapeDataString(text)}";
            LaunchBrowserUrl(waUrl);
            ShowToast("Opening WhatsApp... 💬");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareTelegram_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var title = _shareTargetVideo.Title;
            var tgUrl = $"https://t.me/share/url?url={Uri.EscapeDataString(url)}&text={Uri.EscapeDataString(title)}";
            LaunchBrowserUrl(tgUrl);
            ShowToast("Opening Telegram... ✈️");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareTwitter_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var text = $"Watching \"{_shareTargetVideo.Title}\" on Vixz:";
            var twUrl = $"https://twitter.com/intent/tweet?text={Uri.EscapeDataString(text)}&url={Uri.EscapeDataString(url)}";
            LaunchBrowserUrl(twUrl);
            ShowToast("Opening X (Twitter)... 🐦");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareFacebook_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var fbUrl = $"https://www.facebook.com/sharer/sharer.php?u={Uri.EscapeDataString(url)}";
            LaunchBrowserUrl(fbUrl);
            ShowToast("Opening Facebook... 📘");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareEmail_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var subject = $"Video: {_shareTargetVideo.Title}";
            var body = $"Hey, check out this video:\n\n{_shareTargetVideo.Title}\nBy: {_shareTargetVideo.ChannelTitle}\n\n{url}";
            var mailUrl = $"mailto:?subject={Uri.EscapeDataString(subject)}&body={Uri.EscapeDataString(body)}";
            LaunchBrowserUrl(mailUrl);
            ShowToast("Opening Email Client... ✉️");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareMore_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            Clipboard.SetText(url);
            LaunchBrowserUrl(url);
            ShowToast("Opened in browser & copied link! 🌐📋");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void CopyEmbedCode_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var embed = $"<iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/{_shareTargetVideo.Id}\" title=\"YouTube video player\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\" allowfullscreen></iframe>";
            Clipboard.SetText(embed);
            ShowToast("HTML Embed code copied to clipboard! ‹/›✨");
        }

        private void LaunchBrowserUrl(string url)
        {
            try
            {
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = url,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to open URL: {ex.Message}");
            }
        }

        #endregion
    }
}
