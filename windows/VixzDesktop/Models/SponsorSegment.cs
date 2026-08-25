namespace VixzDesktop.Models
{
    public class SponsorSegment
    {
        public string UUID { get; set; } = string.Empty;
        public double StartTime { get; set; }
        public double EndTime { get; set; }
        public string Category { get; set; } = "sponsor";
        public string ActionType { get; set; } = "skip";
    }
}
