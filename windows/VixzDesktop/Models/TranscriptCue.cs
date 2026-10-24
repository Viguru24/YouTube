using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace VixzDesktop.Models
{
    public class TranscriptCue : INotifyPropertyChanged
    {
        public double StartSeconds { get; set; }
        public double DurationSeconds { get; set; }
        public string Text { get; set; } = "";

        private bool _isActive = false;
        public bool IsActive
        {
            get => _isActive;
            set { if (_isActive != value) { _isActive = value; OnPropertyChanged(); } }
        }

        public string FormattedTime
        {
            get
            {
                var ts = TimeSpan.FromSeconds(Math.Max(0, StartSeconds));
                return ts.TotalHours >= 1
                    ? ts.ToString(@"h\:mm\:ss")
                    : ts.ToString(@"m\:ss");
            }
        }

        public event PropertyChangedEventHandler? PropertyChanged;
        protected void OnPropertyChanged([CallerMemberName] string? name = null)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
        }
    }
}
