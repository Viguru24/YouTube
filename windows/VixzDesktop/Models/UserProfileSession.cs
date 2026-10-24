using System;
using System.Collections.Generic;
using VixzDesktop.Models;

namespace VixzDesktop.Models
{
    public class UserProfileSession
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N");
        public string Name { get; set; } = "Personal";
        public string Email { get; set; } = "";
        public string AvatarUrl { get; set; } = "";
        public string EncryptedCookies { get; set; } = "";
        public List<string> SubscribedChannels { get; set; } = new();
        public List<VideoItem> Favorites { get; set; } = new();
        public List<VideoItem> WatchLater { get; set; } = new();
        public List<VideoItem> WatchHistory { get; set; } = new();
        public DateTime LastUsed { get; set; } = DateTime.UtcNow;
        public bool IsActive { get; set; } = false;

        public string DisplayLabel => string.IsNullOrWhiteSpace(Email)
            ? $"👤 {Name}"
            : $"👤 {Name} ({Email})";
    }
}
