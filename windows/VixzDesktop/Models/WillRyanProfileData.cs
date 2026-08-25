using System;
using System.Collections.Generic;
using System.Linq;
using VixzDesktop.Services;

namespace VixzDesktop.Models
{
    public static class WillRyanProfileData
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

        public static bool IsSubscribed(string channelName)
        {
            if (string.IsNullOrWhiteSpace(channelName)) return false;
            var trimmed = channelName.Trim();
            return SubscribedChannels.Any(c => c.Equals(trimmed, StringComparison.OrdinalIgnoreCase));
        }

        public static void AddSubscribedChannel(string name)
        {
            var trimmed = name.Trim();
            if (!string.IsNullOrWhiteSpace(trimmed) && !IsSubscribed(trimmed))
            {
                SubscribedChannels.Insert(0, trimmed);
                StorageService.Save();
            }
        }

        public static void RemoveSubscribedChannel(string name)
        {
            var trimmed = name.Trim();
            SubscribedChannels.RemoveAll(c => c.Equals(trimmed, StringComparison.OrdinalIgnoreCase));
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
