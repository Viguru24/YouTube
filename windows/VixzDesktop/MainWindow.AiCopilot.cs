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
        #region AI Copilot & Assistant

        private void AiCopilotBtn_Click(object sender, RoutedEventArgs e)
        {
            ToggleAiCopilotDrawer();
        }

        private double _savedAiDrawerWidth = 390;

        private void CloseAiCopilot_Click(object sender, RoutedEventArgs e)
        {
            if (AiDrawerCol.ActualWidth > 200)
            {
                _savedAiDrawerWidth = AiDrawerCol.ActualWidth;
            }
            AiCopilotPanel.Visibility = Visibility.Collapsed;
            AiGridSplitter.Visibility = Visibility.Collapsed;
            AiDrawerCol.Width = new GridLength(0);
            SpeechService.Instance.StopSpeaking();
            SpeechService.Instance.StopListening();
            ResetAiMicBtnUi();
        }

        // ---- AI Font Size Controls ----
        private const double AiFontSizeDefault = 14.0;

        private void AiFontSizeSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (AiMessageStack == null) return;
            ApplyAiFontSize(e.NewValue);

            // Persist setting permanently
            if (Math.Abs(StorageService.Settings.AiFontSize - e.NewValue) > 0.05)
            {
                StorageService.Settings.AiFontSize = e.NewValue;
                StorageService.Save();
            }
        }

        private void AiFontResetBtn_Click(object sender, RoutedEventArgs e)
        {
            if (AiFontSizeSlider != null)
            {
                AiFontSizeSlider.Value = AiFontSizeDefault;
                StorageService.Settings.AiFontSize = AiFontSizeDefault;
                StorageService.Save();
            }
        }

        private void AiChatScrollViewer_PreviewMouseWheel(object sender, System.Windows.Input.MouseWheelEventArgs e)
        {
            // Ctrl+Scroll changes font size; plain scroll handled normally
            if (System.Windows.Input.Keyboard.IsKeyDown(System.Windows.Input.Key.LeftCtrl) ||
                System.Windows.Input.Keyboard.IsKeyDown(System.Windows.Input.Key.RightCtrl))
            {
                e.Handled = true;
                double delta = e.Delta > 0 ? 0.5 : -0.5;
                double newVal = Math.Clamp((AiFontSizeSlider?.Value ?? AiFontSizeDefault) + delta,
                                           AiFontSizeSlider?.Minimum ?? 11,
                                           AiFontSizeSlider?.Maximum ?? 20);
                if (AiFontSizeSlider != null) AiFontSizeSlider.Value = newVal;
            }
        }

        private void ApplyAiFontSize(double size)
        {
            // Walk all TextBlocks inside AiMessageStack and scale them
            foreach (var tb in FindVisualChildren<TextBlock>(AiMessageStack))
            {
                // Scale relative to the element's original role:
                // Headers (bold) stay slightly smaller; body text gets the full size
                if (tb.FontWeight == FontWeights.Bold)
                    tb.FontSize = Math.Max(size - 1.5, 9);
                else
                    tb.FontSize = size;
            }
        }

        private static IEnumerable<T> FindVisualChildren<T>(System.Windows.DependencyObject parent) where T : System.Windows.DependencyObject
        {
            if (parent == null) yield break;
            int count = System.Windows.Media.VisualTreeHelper.GetChildrenCount(parent);
            for (int i = 0; i < count; i++)
            {
                var child = System.Windows.Media.VisualTreeHelper.GetChild(parent, i);
                if (child is T t) yield return t;
                foreach (var desc in FindVisualChildren<T>(child))
                    yield return desc;
            }
        }

        private void ToggleAiCopilotDrawer()
        {
            if (AiCopilotPanel.Visibility == Visibility.Visible)
            {
                CloseAiCopilot_Click(null!, null!);
            }
            else
            {
                AiCopilotPanel.Visibility = Visibility.Visible;
                AiGridSplitter.Visibility = Visibility.Visible;
                AiDrawerCol.Width = new GridLength(Math.Max(320, _savedAiDrawerWidth));
                if (AiFontSizeSlider != null && StorageService.Settings.AiFontSize >= 11 && StorageService.Settings.AiFontSize <= 20)
                {
                    AiFontSizeSlider.Value = StorageService.Settings.AiFontSize;
                    ApplyAiFontSize(StorageService.Settings.AiFontSize);
                }
                AiPromptBox.Focus();
            }
        }

        private void AiSettingsBtn_Click(object sender, RoutedEventArgs e)
        {
            var currentKey = StorageService.Settings.GeminiApiKey ?? "";
            if (string.IsNullOrWhiteSpace(currentKey))
            {
                var autoKey = AiCopilotService.TryDetectLocalFreeLlmApiKey();
                if (!string.IsNullOrWhiteSpace(autoKey)) currentKey = autoKey;
            }

            var prompt = new Window
            {
                Title = "⚙️ Vixz AI Brain Settings",
                Width = 540,
                Height = 390,
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
                Text = "⚡ Connect Real AI (Gemini / Groq / OpenAI / FreeLLMAPI)",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 8)
            };
            var desc = new TextBlock
            {
                Text = "Paste your API key below. Supported providers:\n• Google Gemini (AIzaSy...) — free, recommended, no VPN issues\n• Groq (gsk_...) — fast, free tier\n• OpenAI (sk-...) — paid\n• FreeLLMAPI (freellma...) — local proxy, works behind any VPN",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 10)
            };

            var linkRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) };

            var autoDetectBtn = new Button
            {
                Content = "⚡ Auto-Detect FreeLLMAPI",
                Style = (Style)FindResource("GlassButton"),
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                FontSize = 11,
                Padding = new Thickness(10, 5, 10, 5),
                Margin = new Thickness(0, 0, 8, 0),
                HorizontalAlignment = HorizontalAlignment.Left
            };

            var linkBtn = new Button
            {
                Content = "🌐 Get Gemini Key",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 11,
                Padding = new Thickness(10, 5, 10, 5),
                Margin = new Thickness(0, 0, 8, 0),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            linkBtn.Click += (s, ev) =>
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "https://aistudio.google.com/apikey",
                        UseShellExecute = true
                    });
                }
                catch { }
            };

            var freeLlmBtn = new Button
            {
                Content = "🔌 FreeLLMAPI Site",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 11,
                Padding = new Thickness(10, 5, 10, 5),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            freeLlmBtn.Click += (s, ev) =>
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "https://freellmapi.com",
                        UseShellExecute = true
                    });
                }
                catch { }
            };
            linkRow.Children.Add(autoDetectBtn);
            linkRow.Children.Add(linkBtn);
            linkRow.Children.Add(freeLlmBtn);

            var txtBox = new TextBox
            {
                Text = currentKey,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 10)
            };

            var statusBlock = new TextBlock
            {
                Text = "",
                FontSize = 11,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 12)
            };

            autoDetectBtn.Click += async (s, ev) =>
            {
                var detected = AiCopilotService.TryDetectLocalFreeLlmApiKey();
                if (!string.IsNullOrWhiteSpace(detected))
                {
                    txtBox.Text = detected;
                    statusBlock.Text = "⏳ Testing detected FreeLLMAPI key...";
                    statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary");
                    var (ok, msg, resKey) = await AiCopilotService.ValidateApiKeyAsync(detected);
                    statusBlock.Text = msg;
                    statusBlock.Foreground = ok
                        ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.LightGreen)
                        : new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FF7043"));
                }
                else
                {
                    statusBlock.Text = "⚠️ FreeLLMAPI desktop app not found or not initialized.";
                    statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                }
            };

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var testBtn = new Button
            {
                Content = "Test Key",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            testBtn.Click += async (s, ev) =>
            {
                var val = txtBox.Text.Trim();
                if (string.IsNullOrWhiteSpace(val))
                {
                    statusBlock.Text = "⚠️ Please enter an API key to test.";
                    statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                    return;
                }
                statusBlock.Text = "⏳ Testing API connection...";
                statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary");
                testBtn.IsEnabled = false;

                var (ok, msg, resKey) = await AiCopilotService.ValidateApiKeyAsync(val);
                if (!string.IsNullOrWhiteSpace(resKey) && resKey != val)
                {
                    txtBox.Text = resKey;
                }
                statusBlock.Text = msg;
                statusBlock.Foreground = ok
                    ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.LightGreen)
                    : new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FF7043"));
                testBtn.IsEnabled = true;
            };

            var clearBtn = new Button
            {
                Content = "Clear Key",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            clearBtn.Click += (s, ev) =>
            {
                StorageService.Settings.GeminiApiKey = null;
                StorageService.Save();
                ShowToast("Cleared AI API Key");
                prompt.Close();
            };

            var saveBtn = new Button
            {
                Content = "Save & Activate",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                Padding = new Thickness(14, 6, 14, 6)
            };
            saveBtn.Click += (s, ev) =>
            {
                var val = txtBox.Text.Trim();
                StorageService.Settings.GeminiApiKey = string.IsNullOrWhiteSpace(val) ? null : val;
                StorageService.Save();
                ShowToast("✨ AI Brain Settings Saved!");
                prompt.Close();
            };

            btnRow.Children.Add(testBtn);
            btnRow.Children.Add(clearBtn);
            btnRow.Children.Add(saveBtn);

            sp.Children.Add(heading);
            sp.Children.Add(desc);
            sp.Children.Add(linkRow);
            sp.Children.Add(txtBox);
            sp.Children.Add(statusBlock);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void UpdateJevButtonUi()
        {
            if (JevAgentBtn == null) return;
            if (StorageService.Settings.IsJevEnabled && !string.IsNullOrWhiteSpace(StorageService.Settings.JevApiKey))
            {
                JevAgentBtn.Content = "⚡ Jev ON";
                JevAgentBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
            }
            else
            {
                JevAgentBtn.Content = "⚡ Jev OFF";
                JevAgentBtn.Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary");
            }
        }

        private void JevSettings_Click(object sender, RoutedEventArgs e)
        {
            var prompt = new Window
            {
                Title = "⚡ TypeSafe AI Jev System-One Settings",
                Width = 540,
                Height = 440,
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
                Text = "⚡ TypeSafe AI Jev System-One Decision Engine",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var desc = new TextBlock
            {
                Text = "Jev is an ultra-fast (70-300ms) non-generative decision model designed for browser automation. In Vixz Desktop, it powers:\n" +
                       "• 🔐 Autonomous Google Account Chooser & Cookie Consent Bypassing\n" +
                       "• 📺 Smart Viewport / Playback Quality Arbitration\n" +
                       "• 🎵 Autonomous Content Curator & Next-Up DJ",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 10)
            };

            // Toggle switch / checkbox
            var toggleRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) };
            var toggleCheck = new CheckBox
            {
                Content = "Enable Jev System-One Autonomous Engine",
                IsChecked = StorageService.Settings.IsJevEnabled,
                Foreground = System.Windows.Media.Brushes.White,
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                VerticalAlignment = VerticalAlignment.Center
            };
            toggleRow.Children.Add(toggleCheck);

            // Direct Link Button to Console
            var linkRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 10) };
            var linkBtn = new Button
            {
                Content = "🌐 Get Jev API Key ($5 Free Credit at console.typesafe.ai)",
                Style = (Style)FindResource("GlassButton"),
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                FontSize = 11,
                Padding = new Thickness(12, 6, 12, 6),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            linkBtn.Click += (s, ev) =>
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "https://console.typesafe.ai",
                        UseShellExecute = true
                    });
                }
                catch { }
            };
            linkRow.Children.Add(linkBtn);

            var keyLabel = new TextBlock
            {
                Text = "TypeSafe AI API Key (apikey_...):",
                FontSize = 11,
                Foreground = (System.Windows.Media.Brush)FindResource("TextPrimary"),
                Margin = new Thickness(0, 0, 0, 4)
            };

            var keyBox = new TextBox
            {
                Text = StorageService.Settings.JevApiKey ?? "",
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 10)
            };

            var statusBlock = new TextBlock
            {
                Text = StorageService.Settings.IsJevEnabled ? "🟢 Jev is currently ACTIVE" : "⚪ Jev is currently DISABLED",
                FontSize = 11,
                Foreground = StorageService.Settings.IsJevEnabled
                    ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.LightGreen)
                    : (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 12)
            };

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var testBtn = new Button
            {
                Content = "Test Jev Key",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            testBtn.Click += async (s, ev) =>
            {
                var val = keyBox.Text.Trim();
                if (string.IsNullOrWhiteSpace(val))
                {
                    statusBlock.Text = "⚠️ Please enter an API key to test.";
                    statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                    return;
                }
                statusBlock.Text = "⏳ Testing TypeSafe Jev System-One connection...";
                statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary");
                testBtn.IsEnabled = false;

                var (ok, msg) = await JevService.ValidateApiKeyAsync(val);
                statusBlock.Text = msg;
                statusBlock.Foreground = ok
                    ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Colors.LightGreen)
                    : new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FF7043"));
                testBtn.IsEnabled = true;
            };

            var clearBtn = new Button
            {
                Content = "Clear Key",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            clearBtn.Click += (s, ev) =>
            {
                keyBox.Text = "";
                StorageService.Settings.JevApiKey = null;
                StorageService.Save();
                UpdateJevButtonUi();
                ShowToast("Cleared Jev API Key");
            };

            var saveBtn = new Button
            {
                Content = "Save & Apply",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                Padding = new Thickness(14, 6, 14, 6)
            };
            saveBtn.Click += (s, ev) =>
            {
                var val = keyBox.Text.Trim();
                StorageService.Settings.JevApiKey = string.IsNullOrWhiteSpace(val) ? null : val;
                StorageService.Settings.IsJevEnabled = toggleCheck.IsChecked ?? true;
                StorageService.Save();
                UpdateJevButtonUi();
                ShowToast($"⚡ Jev System-One Settings Saved! ({(StorageService.Settings.IsJevEnabled ? "Enabled" : "Disabled")})");
                prompt.Close();
            };

            btnRow.Children.Add(testBtn);
            btnRow.Children.Add(clearBtn);
            btnRow.Children.Add(saveBtn);

            sp.Children.Add(heading);
            sp.Children.Add(desc);
            sp.Children.Add(toggleRow);
            sp.Children.Add(linkRow);
            sp.Children.Add(keyLabel);
            sp.Children.Add(keyBox);
            sp.Children.Add(statusBlock);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void VpsSyncSettings_Click(object sender, RoutedEventArgs e)
        {
            var prompt = new Window
            {
                Title = "☁️ VPS Cross-Device Cloud Sync",
                Width = 500,
                Height = 440,
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
                Text = "☁️ VPS Cross-Device Synchronization",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var desc = new TextBlock
            {
                Text = "Synchronize watched videos, playback positions, and algorithm preferences directly with your private VPS sync server so you never see or re-watch the same video twice across PC and Mobile.",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 14)
            };

            var urlLabel = new TextBlock
            {
                Text = "VPS Server URL (e.g. http://192.168.1.100:8089 or https://sync.mydomain.com):",
                FontSize = 11,
                Foreground = (System.Windows.Media.Brush)FindResource("TextPrimary"),
                Margin = new Thickness(0, 0, 0, 4)
            };

            var urlBox = new TextBox
            {
                Text = StorageService.Settings.VpsServerUrl ?? "",
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 10)
            };

            var keyLabel = new TextBlock
            {
                Text = "Optional API Key / Access Token:",
                FontSize = 11,
                Foreground = (System.Windows.Media.Brush)FindResource("TextPrimary"),
                Margin = new Thickness(0, 0, 0, 4)
            };

            var keyBox = new TextBox
            {
                Text = StorageService.Settings.VpsApiKey ?? "",
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 10)
            };

            var autoSyncCb = new CheckBox
            {
                Content = "Enable automatic background sync (every 5 minutes)",
                IsChecked = StorageService.Settings.IsVpsSyncEnabled,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                FontSize = 11.5,
                Margin = new Thickness(0, 0, 0, 12)
            };

            var lastSyncText = StorageService.Settings.LastVpsSyncTime.HasValue 
                ? $"Last Synced: {StorageService.Settings.LastVpsSyncTime.Value.ToLocalTime():g}" 
                : "Not synced yet";

            var statusBlock = new TextBlock
            {
                Text = $"Status: {lastSyncText} • Watched videos recorded: {StorageService.Settings.WatchHistory.Count}",
                FontSize = 11,
                Foreground = (System.Windows.Media.Brush)FindResource("TextMuted"),
                Margin = new Thickness(0, 0, 0, 14),
                TextWrapping = TextWrapping.Wrap
            };

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };

            var testBtn = new Button
            {
                Content = "⚡ Test & Sync Now",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };

            var saveBtn = new Button
            {
                Content = "Save Settings",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                Padding = new Thickness(14, 6, 14, 6)
            };

            testBtn.Click += async (s, ev) =>
            {
                var url = urlBox.Text.Trim();
                var key = keyBox.Text.Trim();
                if (string.IsNullOrWhiteSpace(url))
                {
                    statusBlock.Foreground = System.Windows.Media.Brushes.OrangeRed;
                    statusBlock.Text = "Please enter a VPS server URL first.";
                    return;
                }

                statusBlock.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                statusBlock.Text = "Connecting & synchronizing with VPS...";
                testBtn.IsEnabled = false;

                StorageService.Settings.VpsServerUrl = url;
                StorageService.Settings.VpsApiKey = string.IsNullOrWhiteSpace(key) ? null : key;
                StorageService.Settings.IsVpsSyncEnabled = autoSyncCb.IsChecked == true;

                var (success, message) = await VpsSyncService.SyncWithServerAsync();
                testBtn.IsEnabled = true;

                if (success)
                {
                    statusBlock.Foreground = System.Windows.Media.Brushes.LightGreen;
                    statusBlock.Text = $"✅ {message}";
                    ShowToast("☁️ Synced with VPS successfully!");
                }
                else
                {
                    statusBlock.Foreground = System.Windows.Media.Brushes.OrangeRed;
                    statusBlock.Text = $"❌ {message}";
                }
            };

            saveBtn.Click += (s, ev) =>
            {
                var url = urlBox.Text.Trim();
                var key = keyBox.Text.Trim();
                StorageService.Settings.VpsServerUrl = string.IsNullOrWhiteSpace(url) ? null : url;
                StorageService.Settings.VpsApiKey = string.IsNullOrWhiteSpace(key) ? null : key;
                StorageService.Settings.IsVpsSyncEnabled = autoSyncCb.IsChecked == true;
                StorageService.Save();
                ShowToast("☁️ VPS Sync Settings Saved!");
                prompt.Close();
            };

            btnRow.Children.Add(testBtn);
            btnRow.Children.Add(saveBtn);

            sp.Children.Add(heading);
            sp.Children.Add(desc);
            sp.Children.Add(urlLabel);
            sp.Children.Add(urlBox);
            sp.Children.Add(keyLabel);
            sp.Children.Add(keyBox);
            sp.Children.Add(autoSyncCb);
            sp.Children.Add(statusBlock);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void AiChipSummarize_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Summarise this video");
        }

        private void AiChipLofi_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Play Lofi Beats live");
        }

        private void AiChipTimer_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Set sleep timer for 30 minutes");
        }

        private void AiChipNews_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Find breaking news from today");
        }

        private async void AiPromptBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await SubmitAiCommandAsync();
            }
        }

        private async void AiSendBtn_Click(object sender, RoutedEventArgs e)
        {
            await SubmitAiCommandAsync();
        }

        private async Task SubmitAiCommandAsync(string? explicitPrompt = null)
        {
            var prompt = explicitPrompt ?? AiPromptBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(prompt)) return;

            if (AiCopilotPanel.Visibility != Visibility.Visible)
            {
                AiCopilotPanel.Visibility = Visibility.Visible;
                AiGridSplitter.Visibility = Visibility.Visible;
                AiDrawerCol.Width = new GridLength(Math.Max(320, _savedAiDrawerWidth));
            }

            AiPromptBox.Text = "";

            // 1. Add User Message Card
            AddUserMessageBubble(prompt);

            // 2. Add Thinking Indicator
            var thinkingCard = AddThinkingBubble();

            try
            {
                // 3. Process via AiCopilotService with WebView2 transcript fetcher delegate
                var result = await AiCopilotService.ProcessCommandAsync(prompt, _currentVideo, async (vid) =>
                {
                    try
                    {
                        if (VideoWebView.CoreWebView2 != null)
                        {
                            var rawJson = await VideoWebView.ExecuteScriptAsync($"fetchTranscript('{vid}')");
                            if (!string.IsNullOrWhiteSpace(rawJson) && rawJson != "null" && rawJson != "\"\"")
                            {
                                return Newtonsoft.Json.JsonConvert.DeserializeObject<string>(rawJson) ?? "";
                            }
                        }
                    }
                    catch { }
                    return "";
                });

                // Remove thinking indicator
                AiMessageStack.Children.Remove(thinkingCard);

                // 4. Render AI Response
                AddAiResponseBubble(result);

                // 5. Execute side effects
                if (result.Type == AiCommandType.PlayVideo && result.TargetVideo != null)
                {
                    await PlayVideoAsync(result.TargetVideo);
                    ShowToast($"▶ Playing {result.TargetVideo.Title}");
                }
                else if (result.Type == AiCommandType.ControlSeek && result.SeekSeconds.HasValue)
                {
                    await VideoWebView.ExecuteScriptAsync($"seek({result.SeekSeconds.Value.ToString(System.Globalization.CultureInfo.InvariantCulture)})");
                    ShowToast(result.SeekSeconds.Value > 0 ? $"⏩ +{result.SeekSeconds.Value}s" : $"⏪ {result.SeekSeconds.Value}s");
                }
                else if (result.Type == AiCommandType.ControlPause)
                {
                    await VideoWebView.ExecuteScriptAsync("pauseVideo()");
                    ShowToast("⏸️ Paused");
                }
                else if (result.Type == AiCommandType.ControlPlay)
                {
                    await VideoWebView.ExecuteScriptAsync("playVideo()");
                    ShowToast("▶ Resumed");
                }
                else if (result.Type == AiCommandType.SetSleepTimer && result.TimerMinutes.HasValue)
                {
                    ArmSleepTimer(result.TimerMinutes.Value);
                }
                else if (result.Type == AiCommandType.SearchFeed && !string.IsNullOrWhiteSpace(result.SearchQuery))
                {
                    SearchBox.Text = result.SearchQuery;
                    await PerformSearchWithFiltersAsync();
                }
            }
            catch (Exception ex)
            {
                AiMessageStack.Children.Remove(thinkingCard);
                AddSimpleAiText($"⚠️ Error: {ex.Message}");
            }

            AiChatScrollViewer.ScrollToEnd();
        }

        private void AddUserMessageBubble(string text)
        {
            var border = new Border
            {
                Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#3E2866")),
                BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#809355FF")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12, 12, 2, 12),
                Padding = new Thickness(12, 8, 12, 8),
                Margin = new Thickness(30, 4, 0, 6),
                HorizontalAlignment = HorizontalAlignment.Right
            };

            var tb = new TextBlock
            {
                Text = text,
                Foreground = System.Windows.Media.Brushes.White,
                FontSize = 12.5,
                TextWrapping = TextWrapping.Wrap
            };

            border.Child = tb;
            AiMessageStack.Children.Add(border);
        }

        private Border AddThinkingBubble()
        {
            var border = new Border
            {
                Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#1C1C28")),
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(0, 4, 30, 6),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            var tb = new TextBlock
            {
                Text = "✨ AI is analyzing...",
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                FontSize = 12,
                FontWeight = FontWeights.SemiBold
            };
            border.Child = tb;
            AiMessageStack.Children.Add(border);
            AiChatScrollViewer.ScrollToEnd();
            return border;
        }

        private void AddSimpleAiText(string text)
        {
            var border = new Border
            {
                Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#181824")),
                BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#33FFFFFF")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12, 12, 12, 2),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 4, 20, 6),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            var tb = new TextBlock
            {
                Text = text,
                Foreground = System.Windows.Media.Brushes.White,
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap
            };
            border.Child = tb;
            AiMessageStack.Children.Add(border);
            if (AiFontSizeSlider != null)
            {
                ApplyAiFontSize(AiFontSizeSlider.Value);
            }
        }

        private void AddAiResponseBubble(AiCommandResult result)
        {
            var mainContainer = new StackPanel
            {
                Margin = new Thickness(0, 4, 10, 8),
                HorizontalAlignment = HorizontalAlignment.Stretch
            };

            // 1. Text Message
            if (!string.IsNullOrWhiteSpace(result.ResponseMessage))
            {
                var textBorder = new Border
                {
                    Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#1C1C28")),
                    BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#33FFD700")),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(12, 12, 12, 2),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 6)
                };
                var tb = new TextBlock
                {
                    Text = result.ResponseMessage.Replace("**", "").Replace("*", ""),
                    Foreground = System.Windows.Media.Brushes.White,
                    FontSize = 12,
                    TextWrapping = TextWrapping.Wrap
                };
                textBorder.Child = tb;
                mainContainer.Children.Add(textBorder);
            }

            // 2. Rich Summary Card (if Summarize command)
            if (result.Summary != null)
            {
                var sum = result.Summary;
                var card = new Border
                {
                    Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#14141E")),
                    BorderBrush = (System.Windows.Media.Brush)FindResource("AccentGold"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(12),
                    Padding = new Thickness(14, 14, 14, 14),
                    Margin = new Thickness(0, 4, 0, 6),
                    HorizontalAlignment = HorizontalAlignment.Stretch
                };

                var cardStack = new StackPanel();

                // Warning / Auth notification if user's API key had issues
                if (!string.IsNullOrWhiteSpace(sum.AuthWarning))
                {
                    var warnBorder = new Border
                    {
                        Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#2E1A1A")),
                        BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FF7043")),
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(8),
                        Padding = new Thickness(10, 8, 10, 8),
                        Margin = new Thickness(0, 0, 0, 10)
                    };

                    var warnStack = new StackPanel();
                    var warnText = new TextBlock
                    {
                        Text = sum.AuthWarning,
                        Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FFAA88")),
                        FontSize = 11,
                        LineHeight = 16,
                        TextWrapping = TextWrapping.Wrap
                    };
                    warnStack.Children.Add(warnText);

                    var openSettingsBtn = new Button
                    {
                        Content = "⚙️ Configure AI Brain",
                        Style = (Style)FindResource("GlassButton"),
                        FontSize = 10.5,
                        Padding = new Thickness(8, 3, 8, 3),
                        Margin = new Thickness(0, 6, 0, 0),
                        HorizontalAlignment = HorizontalAlignment.Left
                    };
                    openSettingsBtn.Click += (s, ev) => AiSettingsBtn_Click(s, ev);
                    warnStack.Children.Add(openSettingsBtn);

                    warnBorder.Child = warnStack;
                    cardStack.Children.Add(warnBorder);
                }

                // TL;DR Header & Source Citation
                var headerPanel = new WrapPanel
                {
                    Orientation = Orientation.Horizontal,
                    Margin = new Thickness(0, 0, 0, 8),
                    VerticalAlignment = VerticalAlignment.Center
                };

                var tldrHeader = new TextBlock
                {
                    Text = "📌 EXECUTIVE SUMMARY",
                    Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                    FontSize = 12,
                    FontWeight = FontWeights.Bold,
                    VerticalAlignment = VerticalAlignment.Center,
                    Margin = new Thickness(0, 0, 10, 4)
                };
                headerPanel.Children.Add(tldrHeader);

                if (!string.IsNullOrWhiteSpace(sum.SourceCitation))
                {
                    var citeBadge = new Border
                    {
                        Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#1F2937")),
                        BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#374151")),
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 2, 6, 2),
                        VerticalAlignment = VerticalAlignment.Center,
                        Margin = new Thickness(0, 0, 0, 4)
                    };
                    var citeText = new TextBlock
                    {
                        Text = sum.SourceCitation,
                        Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#9CA3AF")),
                        FontSize = 9.5,
                        FontWeight = FontWeights.SemiBold
                    };
                    citeBadge.Child = citeText;
                    headerPanel.Children.Add(citeBadge);
                }

                cardStack.Children.Add(headerPanel);

                var tldrBody = new TextBlock
                {
                    Text = sum.Tldr,
                    Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#EEEEEE")),
                    FontSize = 13.5,
                    LineHeight = 21,
                    TextWrapping = TextWrapping.Wrap,
                    Margin = new Thickness(0, 0, 0, 12)
                };
                cardStack.Children.Add(tldrBody);

                // Key Takeaways Header
                if (sum.KeyTakeaways.Count > 0)
                {
                    var takeHeader = new TextBlock
                    {
                        Text = "🔑 KEY TAKEAWAYS",
                        Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                        FontSize = 12,
                        FontWeight = FontWeights.Bold,
                        Margin = new Thickness(0, 4, 0, 8)
                    };
                    cardStack.Children.Add(takeHeader);

                    foreach (var point in sum.KeyTakeaways)
                    {
                        var pointGrid = new Grid { Margin = new Thickness(0, 3, 0, 5) };
                        pointGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                        pointGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

                        var dot = new TextBlock 
                        { 
                            Text = "• ", 
                            Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"), 
                            FontSize = 14,
                            Margin = new Thickness(0, 0, 4, 0)
                        };
                        Grid.SetColumn(dot, 0);

                        var pointText = new TextBlock 
                        { 
                            Text = point, 
                            Foreground = System.Windows.Media.Brushes.White, 
                            FontSize = 13, 
                            LineHeight = 19,
                            TextWrapping = TextWrapping.Wrap 
                        };
                        Grid.SetColumn(pointText, 1);

                        pointGrid.Children.Add(dot);
                        pointGrid.Children.Add(pointText);
                        cardStack.Children.Add(pointGrid);
                    }
                }

                // Interactive Timestamp Chapters
                if (sum.Chapters.Count > 0)
                {
                    var chapHeader = new TextBlock
                    {
                        Text = "⏱️ TIMELINE CHAPTERS (CLICK TO JUMP)",
                        Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                        FontSize = 11.5,
                        FontWeight = FontWeights.Bold,
                        Margin = new Thickness(0, 12, 0, 8)
                    };
                    cardStack.Children.Add(chapHeader);

                    var chapWrap = new WrapPanel();
                    foreach (var chap in sum.Chapters)
                    {
                        var btn = new Button
                        {
                            Content = $"▶ {chap.TimeFormatted} {chap.Title}",
                            Style = (Style)FindResource("GlassButton"),
                            FontSize = 12,
                            Padding = new Thickness(8, 4, 8, 4),
                            Margin = new Thickness(0, 0, 4, 4),
                            Tag = chap.Seconds
                        };
                        btn.Click += async (s, e) =>
                        {
                            if (s is Button b && b.Tag is double sec)
                            {
                                await VideoWebView.ExecuteScriptAsync($"seek({sec.ToString(System.Globalization.CultureInfo.InvariantCulture)})");
                                ShowToast($"▶ Jumped to {chap.TimeFormatted}");
                            }
                        };
                        chapWrap.Children.Add(btn);
                    }
                    cardStack.Children.Add(chapWrap);
                }

                card.Child = cardStack;
                mainContainer.Children.Add(card);
            }


            // 3. Web Facts / Live Knowledge Card
            if (result.WebFacts.Count > 0)
            {
                var factsCard = new Border
                {
                    Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#14141E")),
                    BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#3300E5FF")),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(12),
                    Padding = new Thickness(12),
                    Margin = new Thickness(0, 4, 0, 6)
                };

                var factStack = new StackPanel();
                var factHeader = new TextBlock
                {
                    Text = "🌐 LIVE WEB INTELLIGENCE & KEY FACTS",
                    Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#00E5FF")),
                    FontSize = 10,
                    FontWeight = FontWeights.Bold,
                    Margin = new Thickness(0, 0, 0, 6)
                };
                factStack.Children.Add(factHeader);

                foreach (var fact in result.WebFacts)
                {
                    var factGrid = new Grid { Margin = new Thickness(0, 2, 0, 2) };
                    factGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                    factGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

                    var bullet = new TextBlock
                    {
                        Text = "• ",
                        Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#00E5FF")),
                        FontSize = 11,
                        Margin = new Thickness(0, 0, 4, 0)
                    };
                    Grid.SetColumn(bullet, 0);

                    var factText = new TextBlock
                    {
                        Text = fact,
                        Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#CCCCCC")),
                        FontSize = 11,
                        LineHeight = 15.5,
                        TextWrapping = TextWrapping.Wrap
                    };
                    Grid.SetColumn(factText, 1);

                    factGrid.Children.Add(bullet);
                    factGrid.Children.Add(factText);
                    factStack.Children.Add(factGrid);
                }

                factsCard.Child = factStack;
                mainContainer.Children.Add(factsCard);
            }

            // 4. Copy & Read Aloud Action Row
            var copyRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Left,
                Margin = new Thickness(0, 2, 0, 0)
            };

            var copyBtn = new Button
            {
                Content = "📋 Copy",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 10.5,
                Padding = new Thickness(8, 3, 8, 3),
                Cursor = System.Windows.Input.Cursors.Hand,
                ToolTip = "Copy this response to clipboard"
            };

            var speechBtn = new Button
            {
                Content = "🔊 Read",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 10.5,
                Padding = new Thickness(8, 3, 8, 3),
                Margin = new Thickness(6, 0, 0, 0),
                Cursor = System.Windows.Input.Cursors.Hand,
                ToolTip = "Read this text out loud (Text-to-Speech)"
            };

            var copiedLabel = new System.Windows.Controls.TextBlock
            {
                Text = "✔ Copied!",
                Foreground = new System.Windows.Media.SolidColorBrush(
                    (System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#4CAF50")),
                FontSize = 10.5,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(8, 0, 0, 0),
                Visibility = Visibility.Collapsed
            };

            copyBtn.Click += (s, e) =>
            {
                // Build full plain-text copy string
                var sb = new System.Text.StringBuilder();
                if (!string.IsNullOrWhiteSpace(result.ResponseMessage))
                    sb.AppendLine(result.ResponseMessage.Replace("**", "").Replace("*", ""));

                if (result.Summary != null)
                {
                    var sum = result.Summary;
                    if (!string.IsNullOrWhiteSpace(sum.Tldr))
                    {
                        sb.AppendLine();
                        sb.AppendLine("EXECUTIVE SUMMARY:");
                        sb.AppendLine(sum.Tldr);
                    }
                    if (sum.KeyTakeaways.Count > 0)
                    {
                        sb.AppendLine();
                        sb.AppendLine("KEY TAKEAWAYS:");
                        foreach (var pt in sum.KeyTakeaways)
                            sb.AppendLine($"• {pt}");
                    }
                    if (sum.Chapters.Count > 0)
                    {
                        sb.AppendLine();
                        sb.AppendLine("TIMELINE CHAPTERS:");
                        foreach (var ch in sum.Chapters)
                            sb.AppendLine($"[{ch.TimeFormatted}] {ch.Title}");
                    }
                }

                if (result.WebFacts.Count > 0)
                {
                    sb.AppendLine();
                    sb.AppendLine("LIVE WEB INTELLIGENCE & KEY FACTS:");
                    foreach (var fact in result.WebFacts)
                        sb.AppendLine($"• {fact}");
                }

                System.Windows.Clipboard.SetText(sb.ToString().Trim());

                // Flash "Copied!" label then hide after 2 seconds
                copiedLabel.Visibility = Visibility.Visible;
                var fadeTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2) };
                fadeTimer.Tick += (_, __) => { copiedLabel.Visibility = Visibility.Collapsed; fadeTimer.Stop(); };
                fadeTimer.Start();
            };

            speechBtn.Click += (s, e) =>
            {
                if (SpeechService.Instance.IsSpeaking && speechBtn.Content.ToString() == "⏹ Stop")
                {
                    SpeechService.Instance.StopSpeaking();
                    speechBtn.Content = "🔊 Read";
                    speechBtn.Foreground = System.Windows.Media.Brushes.White;
                    return;
                }

                // Construct text to read aloud
                var sbRead = new System.Text.StringBuilder();
                if (result.Summary != null)
                {
                    var sum = result.Summary;
                    if (!string.IsNullOrWhiteSpace(sum.Tldr))
                    {
                        sbRead.AppendLine("Executive Summary. " + sum.Tldr);
                    }
                    if (sum.KeyTakeaways.Count > 0)
                    {
                        sbRead.AppendLine("Key takeaways. " + string.Join(". ", sum.KeyTakeaways));
                    }
                }
                else if (!string.IsNullOrWhiteSpace(result.ResponseMessage))
                {
                    sbRead.AppendLine(result.ResponseMessage);
                }

                if (result.WebFacts.Count > 0)
                {
                    sbRead.AppendLine("Key facts. " + string.Join(". ", result.WebFacts));
                }

                var textToSpeak = sbRead.ToString().Trim();
                if (string.IsNullOrWhiteSpace(textToSpeak)) return;

                // Stop any previous speech first
                SpeechService.Instance.StopSpeaking();

                speechBtn.Content = "⏹ Stop";
                speechBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");

                SpeechService.Instance.SpeakAsync(textToSpeak,
                    onStart: () =>
                    {
                        speechBtn.Content = "⏹ Stop";
                        speechBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                    },
                    onComplete: () =>
                    {
                        speechBtn.Content = "🔊 Read";
                        speechBtn.Foreground = System.Windows.Media.Brushes.White;
                    });
            };

            copyRow.Children.Add(copyBtn);
            copyRow.Children.Add(speechBtn);
            copyRow.Children.Add(copiedLabel);
            mainContainer.Children.Add(copyRow);

            AiMessageStack.Children.Add(mainContainer);
            if (AiFontSizeSlider != null)
            {
                ApplyAiFontSize(AiFontSizeSlider.Value);
            }
            AiChatScrollViewer.ScrollToEnd();
        }

        private void SearchMicBtn_Click(object sender, RoutedEventArgs e)
        {
            if (SpeechService.Instance.IsListening)
            {
                SpeechService.Instance.StopListening();
                ResetSearchMicBtnUi();
                return;
            }

            SearchBox.Focus();
            var started = SpeechService.Instance.StartListening(
                onHypothesis: (hypothesis) =>
                {
                    SearchBox.Text = hypothesis;
                    SearchBox.CaretIndex = hypothesis.Length;
                },
                onFinalResult: async (finalText) =>
                {
                    SearchBox.Text = finalText;
                    SearchBox.CaretIndex = finalText.Length;
                    ResetSearchMicBtnUi();
                    await PerformSearchWithFiltersAsync();
                },
                onError: (err) =>
                {
                    ResetSearchMicBtnUi();
                    ShowToast($"⚠️ {err}");
                },
                onStopped: () =>
                {
                    ResetSearchMicBtnUi();
                });

            if (started)
            {
                SearchMicBtn.Content = "🔴";
                SearchMicBtn.Background = new System.Windows.Media.SolidColorBrush(
                    (System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#E53935"));
                SearchMicBtn.ToolTip = "Listening... Speak now (Click to stop)";
                ShowToast("🎙️ Listening for search...");
            }
        }

        private void ResetSearchMicBtnUi()
        {
            SearchMicBtn.Content = "🎙️";
            SearchMicBtn.ClearValue(Button.BackgroundProperty);
            SearchMicBtn.ToolTip = "Voice Search (Click to speak)";
        }

        private void AiMicBtn_Click(object sender, RoutedEventArgs e)
        {
            if (SpeechService.Instance.IsListening)
            {
                SpeechService.Instance.StopListening();
                ResetAiMicBtnUi();
                return;
            }

            AiPromptBox.Focus();
            var started = SpeechService.Instance.StartListening(
                onHypothesis: (hypothesis) =>
                {
                    AiPromptBox.Text = hypothesis;
                    AiPromptBox.CaretIndex = hypothesis.Length;
                },
                onFinalResult: (finalText) =>
                {
                    AiPromptBox.Text = finalText;
                    AiPromptBox.CaretIndex = finalText.Length;
                    ResetAiMicBtnUi();
                    _ = SubmitAiCommandAsync(finalText);
                },
                onError: (err) =>
                {
                    ResetAiMicBtnUi();
                    ShowToast($"⚠️ {err}");
                },
                onStopped: () =>
                {
                    ResetAiMicBtnUi();
                });

            if (started)
            {
                AiMicBtn.Content = "🔴";
                AiMicBtn.Background = new System.Windows.Media.SolidColorBrush(
                    (System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#E53935"));
                AiMicBtn.ToolTip = "Listening... Speak now (Click to stop)";
                ShowToast("🎙️ Listening... Speak your command");
            }
        }

        private void ResetAiMicBtnUi()
        {
            AiMicBtn.Content = "🎙️";
            AiMicBtn.ClearValue(Button.BackgroundProperty);
            AiMicBtn.ToolTip = "Voice Command (Click to speak)";
        }

        #endregion
    }
}
