using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace VixzDesktop.Models
{
    public enum DownloadStatus
    {
        Queued,
        Downloading,
        Paused,
        Completed,
        Failed
    }

    public class DownloadTask : INotifyPropertyChanged
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N");
        public string VideoId { get; set; } = "";
        public string Title { get; set; } = "";
        public string ChannelTitle { get; set; } = "";
        public string ThumbnailUrl { get; set; } = "";
        public bool IsVideo { get; set; } = true;

        private DownloadStatus _status = DownloadStatus.Queued;
        public DownloadStatus Status
        {
            get => _status;
            set { if (_status != value) { _status = value; OnPropertyChanged(); OnPropertyChanged(nameof(StatusText)); OnPropertyChanged(nameof(CanPause)); OnPropertyChanged(nameof(CanResume)); OnPropertyChanged(nameof(CanRetry)); } }
        }

        private double _progress = 0.0;
        public double Progress
        {
            get => _progress;
            set { if (Math.Abs(_progress - value) > 0.001) { _progress = value; OnPropertyChanged(); OnPropertyChanged(nameof(ProgressPercentText)); } }
        }

        private string _speedText = "";
        public string SpeedText
        {
            get => _speedText;
            set { if (_speedText != value) { _speedText = value; OnPropertyChanged(); } }
        }

        public string? FilePath { get; set; }
        public string? ErrorMessage { get; set; }
        public DateTime EnqueuedAt { get; set; } = DateTime.UtcNow;
        public DateTime? CompletedAt { get; set; }

        public string StatusText => Status switch
        {
            DownloadStatus.Queued => "⏳ In Queue",
            DownloadStatus.Downloading => $"⚡ Downloading {ProgressPercentText}",
            DownloadStatus.Paused => "⏸️ Paused",
            DownloadStatus.Completed => "✅ Complete",
            DownloadStatus.Failed => $"⚠️ Failed: {ErrorMessage ?? "Error"}",
            _ => "Unknown"
        };

        public string ProgressPercentText => $"{(int)(Progress * 100)}%";
        public string MediaTypeText => IsVideo ? "🎬 Video (MP4)" : "🎵 Audio (MP3)";

        public bool CanPause => Status == DownloadStatus.Downloading;
        public bool CanResume => Status == DownloadStatus.Paused;
        public bool CanRetry => Status == DownloadStatus.Failed;

        public event PropertyChangedEventHandler? PropertyChanged;
        protected void OnPropertyChanged([CallerMemberName] string? name = null)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
        }
    }
}
