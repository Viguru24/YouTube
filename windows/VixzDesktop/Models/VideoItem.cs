using System;

namespace VixzDesktop.Models
{
    public class VideoItem
    {
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
        public bool IsFavorite { get; set; } = false;
        public bool IsWatchLater { get; set; } = false;
        public bool IsDisliked { get; set; } = false;
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
    }
}
