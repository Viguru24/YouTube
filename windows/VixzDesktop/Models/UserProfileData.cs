using System;
using System.Collections.Generic;
using System.Linq;
using VixzDesktop.Services;

namespace VixzDesktop.Models
{
    public static class UserProfileData
    {
        public static string ProfileName => "Local Profile";

        public static readonly List<string> DefaultSubscribedChannels = new List<string>();

        public static List<string> SubscribedChannels
        {
            get
            {
                if (StorageService.Settings.SubscribedChannels == null)
                {
                    StorageService.Settings.SubscribedChannels = new List<string>();
                }
                return StorageService.Settings.SubscribedChannels;
            }
        }

        public static string SanitizeChannelName(string? raw)
        {
            if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
            var name = raw.Trim();

            // Normalize known mojibake, encoding artifacts, and duplicate variants to canonical clean names
            if (name.Contains("Jane Zhang", StringComparison.OrdinalIgnoreCase)) return "Jane Zhang";
            if (name.StartsWith("Xiaomanyc", StringComparison.OrdinalIgnoreCase)) return "Xiaomanyc";
            if (name.StartsWith("Magnus Midtb", StringComparison.OrdinalIgnoreCase)) return "Magnus Midtbø";
            if (name.StartsWith("Andr", StringComparison.OrdinalIgnoreCase) && name.EndsWith("Duqum", StringComparison.OrdinalIgnoreCase)) return "André Duqum";
            if (name.StartsWith("THEE Sama", StringComparison.OrdinalIgnoreCase)) return "THEE Sama Sambit";
            if (name.StartsWith("AI News & Strategy", StringComparison.OrdinalIgnoreCase)) return "AI News & Strategy Daily | Nate B Jones";
            if (name.StartsWith("Zubair Trabzada", StringComparison.OrdinalIgnoreCase)) return "Zubair Trabzada | AI Workshop";
            if (name.Contains("iQIYI", StringComparison.OrdinalIgnoreCase)) return "iQIYI 潮综艺 - Get the iQIYI APP";

            return name;
        }

        public static bool IsSubscribed(string channelName)
        {
            if (string.IsNullOrWhiteSpace(channelName)) return false;
            var clean = SanitizeChannelName(channelName);
            var trimmed = channelName.Trim();
            return SubscribedChannels.Any(c => 
                c.Equals(clean, StringComparison.OrdinalIgnoreCase) || 
                c.Equals(trimmed, StringComparison.OrdinalIgnoreCase));
        }

        public static void AddSubscribedChannel(string name)
        {
            var clean = SanitizeChannelName(name);
            if (!string.IsNullOrWhiteSpace(clean) && !IsSubscribed(clean))
            {
                SubscribedChannels.Insert(0, clean);
                StorageService.Save();
            }
        }

        public static void RemoveSubscribedChannel(string name)
        {
            var clean = SanitizeChannelName(name);
            var trimmed = name.Trim();
            SubscribedChannels.RemoveAll(c => 
                c.Equals(clean, StringComparison.OrdinalIgnoreCase) || 
                c.Equals(trimmed, StringComparison.OrdinalIgnoreCase));
            StorageService.Save();
        }

        public static void ClearAllSubscribedChannels()
        {
            SubscribedChannels.Clear();
            StorageService.Save();
        }

        public static void RestoreDefaultChannels()
        {
            SubscribedChannels.Clear();
            StorageService.Save();
        }
    }
}
