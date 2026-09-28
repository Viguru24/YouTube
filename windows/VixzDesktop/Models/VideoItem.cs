using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Text.RegularExpressions;
using System.Windows;

namespace VixzDesktop.Models
{
    public class VideoItem : INotifyPropertyChanged
    {
        public event PropertyChangedEventHandler? PropertyChanged;

        protected void OnPropertyChanged([CallerMemberName] string? propertyName = null)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }

        private bool _isFavorite = false;
        private bool _isDisliked = false;
        private bool _isWatchLater = false;

        public string Id { get; set; } = string.Empty;
        public string Title { get; set; } = string.Empty;
        public string ChannelTitle { get; set; } = string.Empty;
        public string ChannelId { get; set; } = string.Empty;
        public string ThumbnailUrl { get; set; } = string.Empty;
        public string DurationText { get; set; } = "0:00";
        public TimeSpan? Duration { get; set; }
        private string _viewCountText = string.Empty;
        private string _uploadDateText = string.Empty;

        public string ViewCountText
        {
            get => _viewCountText;
            set
            {
                var formatted = FormatViewsCount(value);
                if (_viewCountText != formatted)
                {
                    _viewCountText = formatted;
                    OnPropertyChanged();
                    OnPropertyChanged(nameof(SubtitleText));
                    OnPropertyChanged(nameof(MetaSubtitleText));
                    OnPropertyChanged(nameof(FormattedDateAndViews));
                    OnPropertyChanged(nameof(DisplayViews));
                }
            }
        }

        public string UploadDateText
        {
            get => _uploadDateText;
            set
            {
                if (_uploadDateText != value)
                {
                    _uploadDateText = value;
                    OnPropertyChanged();
                    OnPropertyChanged(nameof(SubtitleText));
                    OnPropertyChanged(nameof(MetaSubtitleText));
                    OnPropertyChanged(nameof(DisplayTimestamp));
                    OnPropertyChanged(nameof(HasTimestamp));
                    OnPropertyChanged(nameof(TimestampVisibility));
                    OnPropertyChanged(nameof(FormattedDateAndViews));
                }
            }
        }
        public string Description { get; set; } = string.Empty;
        public bool IsShort { get; set; } = false;

        public bool IsFavorite
        {
            get => _isFavorite;
            set
            {
                if (_isFavorite != value)
                {
                    _isFavorite = value;
                    OnPropertyChanged();
                }
            }
        }

        public bool IsWatchLater
        {
            get => _isWatchLater;
            set
            {
                if (_isWatchLater != value)
                {
                    _isWatchLater = value;
                    OnPropertyChanged();
                }
            }
        }

        public bool IsDisliked
        {
            get => _isDisliked;
            set
            {
                if (_isDisliked != value)
                {
                    _isDisliked = value;
                    OnPropertyChanged();
                }
            }
        }

        public bool IsDownloaded { get; set; } = false;
        public string LocalFilePath { get; set; } = string.Empty;
        public long LastPositionSeconds { get; set; } = 0;
        public string RecommendationReason { get; set; } = string.Empty;
        public float AlgorithmScore { get; set; } = 0;

        public string SubtitleText
        {
            get
            {
                var parts = new System.Collections.Generic.List<string>();
                if (!string.IsNullOrWhiteSpace(ChannelTitle)) parts.Add(ChannelTitle);
                if (!string.IsNullOrWhiteSpace(UploadDateText) && UploadDateText != "YouTube") parts.Add(UploadDateText);
                if (!string.IsNullOrWhiteSpace(DisplayViews)) parts.Add(DisplayViews);
                return string.Join(" • ", parts);
            }
        }

        public string MetaSubtitleText
        {
            get
            {
                var parts = new System.Collections.Generic.List<string>();
                if (!string.IsNullOrWhiteSpace(UploadDateText) && UploadDateText != "YouTube") parts.Add(UploadDateText.Replace('\u00A0', ' ').Trim());
                if (!string.IsNullOrWhiteSpace(DisplayViews)) parts.Add(DisplayViews);
                return parts.Count > 0 ? string.Join(" • ", parts) : "";
            }
        }

        public bool HasTimestamp => !string.IsNullOrWhiteSpace(DisplayTimestamp);

        public Visibility TimestampVisibility => HasTimestamp ? Visibility.Visible : Visibility.Collapsed;

        public string DisplayTimestamp
        {
            get
            {
                if (string.IsNullOrWhiteSpace(UploadDateText) || UploadDateText == "YouTube")
                {
                    return "";
                }
                var clean = UploadDateText.Replace('\u00A0', ' ').Trim();
                var lower = clean.ToLowerInvariant();

                if (lower.Contains("live") || lower.Contains("watching")) return "LIVE";
                if (lower.Contains("moment") || lower.Contains("just now")) return "NOW";
                if (lower.Contains("today")) return "Today";
                if (lower.Contains("yesterday")) return "1d ago";

                // Formats compact: e.g. "2 hours ago" -> "2h ago", "15 minutes ago" -> "15m ago", "1 day ago" -> "1d ago"
                var match = Regex.Match(lower, @"(\d+)\s*(s|sec|seconds?|m|min|minutes?|h|hr|hours?|d|days?|w|wk|weeks?|mo|mth|months?|y|yr|years?)\b");
                if (match.Success)
                {
                    var num = match.Groups[1].Value;
                    var unit = match.Groups[2].Value;
                    string suffix = "d";
                    if (unit.StartsWith("s")) suffix = "s";
                    else if ((unit.StartsWith("m") && !unit.StartsWith("mo") && !unit.StartsWith("mth")) || unit == "min") suffix = "m";
                    else if (unit.StartsWith("h")) suffix = "h";
                    else if (unit.StartsWith("d")) suffix = "d";
                    else if (unit.StartsWith("w")) suffix = "w";
                    else if (unit.StartsWith("mo") || unit.StartsWith("mth")) suffix = "mo";
                    else if (unit.StartsWith("y")) suffix = "y";
                    return $"{num}{suffix} ago";
                }

                return clean;
            }
        }

        public static string FormatViewsCount(string? raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return "";
            var clean = raw.Replace('\u00A0', ' ').Trim();

            // Already in compact format: e.g. "1.2M views", "450K views", "12B views", "LIVE"
            if (Regex.IsMatch(clean, @"\b\d+(?:\.\d+)?\s*[KMB]\b", RegexOptions.IgnoreCase))
            {
                return clean.EndsWith("views", StringComparison.OrdinalIgnoreCase) || clean.EndsWith("watching", StringComparison.OrdinalIgnoreCase)
                    ? clean
                    : $"{clean} views";
            }

            // Extract numeric part (e.g. "2,177,000", "2.177.000", "21769")
            var match = Regex.Match(clean, @"([\d,\.]{3,})");
            if (match.Success)
            {
                var numStr = match.Groups[1].Value.Replace(",", "").Replace(".", "");
                if (long.TryParse(numStr, out var count))
                {
                    string suffix = clean.Contains("watch", StringComparison.OrdinalIgnoreCase) ? "watching" : "views";
                    if (count >= 1_000_000_000) return $"{count / 1_000_000_000.0:0.#}B {suffix}";
                    if (count >= 1_000_000) return $"{count / 1_000_000.0:0.#}M {suffix}";
                    if (count >= 1_000) return $"{count / 1_000.0:0.#}K {suffix}";
                    return $"{count} {suffix}";
                }
            }

            var smallMatch = Regex.Match(clean, @"^(\d+)\s*(views?|watching)?$", RegexOptions.IgnoreCase);
            if (smallMatch.Success && long.TryParse(smallMatch.Groups[1].Value, out var smallCount))
            {
                string suffix = clean.Contains("watch", StringComparison.OrdinalIgnoreCase) ? "watching" : "views";
                return $"{smallCount} {suffix}";
            }

            return clean.EndsWith("views", StringComparison.OrdinalIgnoreCase) || clean.EndsWith("watching", StringComparison.OrdinalIgnoreCase)
                ? clean
                : $"{clean} views";
        }

        public string DisplayViews => FormatViewsCount(ViewCountText);

        public string FormattedDateAndViews
        {
            get
            {
                var parts = new System.Collections.Generic.List<string>();
                if (!string.IsNullOrWhiteSpace(UploadDateText) && UploadDateText != "YouTube")
                {
                    parts.Add($"🕒 {UploadDateText.Replace('\u00A0', ' ').Trim()}");
                }
                if (!string.IsNullOrWhiteSpace(ViewCountText))
                {
                    var views = DisplayViews;
                    parts.Add($"👁️ {views}");
                }
                return string.Join("   •   ", parts);
            }
        }
    }
}
