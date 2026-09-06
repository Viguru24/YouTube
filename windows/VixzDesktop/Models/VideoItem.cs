using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;

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
        public string ViewCountText { get; set; } = string.Empty;
        public string UploadDateText { get; set; } = string.Empty;
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
                if (!string.IsNullOrWhiteSpace(ViewCountText)) parts.Add(ViewCountText);
                return string.Join(" • ", parts);
            }
        }

        public string MetaSubtitleText
        {
            get
            {
                var parts = new System.Collections.Generic.List<string>();
                if (!string.IsNullOrWhiteSpace(UploadDateText) && UploadDateText != "YouTube") parts.Add(UploadDateText);
                if (!string.IsNullOrWhiteSpace(ViewCountText)) parts.Add(ViewCountText);
                return parts.Count > 0 ? string.Join(" • ", parts) : "";
            }
        }
    }
}
