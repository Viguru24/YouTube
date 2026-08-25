using System;

namespace VixzDesktop.Models
{
    public class UserAccount
    {
        public bool IsSignedIn { get; set; } = false;
        public string DisplayName { get; set; } = "";
        public string Email { get; set; } = "";
        public string AvatarUrl { get; set; } = "";
        public DateTime? LastSyncTime { get; set; }
    }
}
