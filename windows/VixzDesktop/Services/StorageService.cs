using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public class AppSettings
    {
        public bool IsAutoplayEnabled { get; set; } = true;
        public string ActiveScreenshotFolder { get; set; } = "Default";
        public string? CustomScreenshotPath { get; set; } = null;
        public Dictionary<string, string> CustomFolderPaths { get; set; } = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        public List<string> ScreenshotFolders { get; set; } = new List<string>
        {
            "Default",
            "Screenshots",
            "Favorites",
            "Recipes",
            "Notes",
            "Tutorials"
        };
        public List<VideoItem> Favorites { get; set; } = new List<VideoItem>();
        public List<VideoItem> WatchLater { get; set; } = new List<VideoItem>();
        public List<VideoItem> WatchHistory { get; set; } = new List<VideoItem>();
        public List<VideoItem> Downloads { get; set; } = new List<VideoItem>();
        public List<string> DislikedVideoIds { get; set; } = new List<string>();
        public List<string> DeletedVideoIds { get; set; } = new List<string>();
        public List<string> DislikedChannels { get; set; } = new List<string>();
        public List<string> SubscribedChannels { get; set; } = new List<string>();
        public bool HasInitializedSubscriptions { get; set; } = false;
        public Dictionary<string, double> WatchPositions { get; set; } = new Dictionary<string, double>();
        public double Volume { get; set; } = 1.0;
        public string PreferredQuality { get; set; } = "hd1080";
        public UserAccount UserAccount { get; set; } = new UserAccount();
        public string? GeminiApiKey { get; set; } = null;
        public bool IsAmbientGlowEnabled { get; set; } = true;
        public bool IsSidebarCollapsed { get; set; } = false;
        public List<string> SearchHistory { get; set; } = new List<string>
        {
            "AI Documentary",
            "Lofi Beats Live",
            "NVIDIA RTX 5080",
            "Space Exploration"
        };
        public Dictionary<string, List<string>> SubscriptionFolders { get; set; } = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase)
        {
            { "All", new List<string>() },
            { "AI & Tech", new List<string> { "Two Minute Papers", "Fireship", "Matt Wolfe", "ThePrimeTime", "Yannic Kilcher" } },
            { "Podcasts", new List<string> { "Lex Fridman", "The Joe Rogan Experience", "Huberman Lab", "Dwarkesh Patel" } },
            { "Gaming", new List<string> { "IGN", "Gameranx", "Digital Foundry" } },
            { "Music", new List<string> { "Lofi Girl", "NCS", "ChilledCow" } }
        };
    }

    public static class StorageService
    {
        private static readonly string AppDataFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "VixzDesktop"
        );
        private static readonly string SettingsFile = Path.Combine(AppDataFolder, "settings.json");

        public static AppSettings Settings { get; private set; } = new AppSettings();

        static StorageService()
        {
            Load();
        }

        public static void Load()
        {
            try
            {
                if (File.Exists(SettingsFile))
                {
                    var json = File.ReadAllText(SettingsFile);
                    var settings = JsonConvert.DeserializeObject<AppSettings>(json);
                    if (settings != null)
                    {
                        Settings = settings;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error loading settings: {ex.Message}");
            }
        }

        public static void Save()
        {
            try
            {
                if (!Directory.Exists(AppDataFolder))
                {
                    Directory.CreateDirectory(AppDataFolder);
                }

                var json = JsonConvert.SerializeObject(Settings, Formatting.Indented);
                File.WriteAllText(SettingsFile, json);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error saving settings: {ex.Message}");
            }
        }

        public static void AddHistory(VideoItem video)
        {
            Settings.WatchHistory.RemoveAll(v => v.Id == video.Id);
            Settings.WatchHistory.Insert(0, video);
            if (Settings.WatchHistory.Count > 100)
            {
                Settings.WatchHistory.RemoveAt(Settings.WatchHistory.Count - 1);
            }
            Save();
        }

        public static void ToggleFavorite(VideoItem video)
        {
            var existing = Settings.Favorites.Find(v => v.Id == video.Id);
            if (existing != null)
            {
                Settings.Favorites.Remove(existing);
                video.IsFavorite = false;
            }
            else
            {
                // If previously disliked, remove dislike
                RemoveDislike(video.Id);
                video.IsDisliked = false;
                video.IsFavorite = true;
                Settings.Favorites.Insert(0, video);
            }
            Save();
        }

        public static void AddDislike(VideoItem video)
        {
            if (string.IsNullOrWhiteSpace(video.Id)) return;

            if (!Settings.DislikedVideoIds.Contains(video.Id))
            {
                Settings.DislikedVideoIds.Add(video.Id);
            }

            // NOTE: Disliking a video ONLY dislikes that specific video, NEVER the whole channel.

            video.IsDisliked = true;
            video.IsFavorite = false;
            video.IsWatchLater = false;

            // Remove from favorites, watch later, and watch history
            Settings.Favorites.RemoveAll(v => v.Id == video.Id);
            Settings.WatchLater.RemoveAll(v => v.Id == video.Id);
            Settings.WatchHistory.RemoveAll(v => v.Id == video.Id);

            Save();
        }

        public static void RemoveDislike(string videoId)
        {
            if (string.IsNullOrWhiteSpace(videoId)) return;
            Settings.DislikedVideoIds.RemoveAll(id => id.Equals(videoId, StringComparison.OrdinalIgnoreCase));
            Save();
        }

        public static void AddDislikedChannel(string channelName)
        {
            if (string.IsNullOrWhiteSpace(channelName)) return;
            if (!Settings.DislikedChannels.Contains(channelName, StringComparer.OrdinalIgnoreCase))
            {
                Settings.DislikedChannels.Add(channelName);
                Save();
            }
        }

        public static void RemoveDislikedChannel(string channelName)
        {
            if (string.IsNullOrWhiteSpace(channelName)) return;
            Settings.DislikedChannels.RemoveAll(c => c.Equals(channelName, StringComparison.OrdinalIgnoreCase));
            Save();
        }

        public static bool IsDisliked(string videoId)
        {
            if (string.IsNullOrWhiteSpace(videoId)) return false;
            return Settings.DislikedVideoIds.Any(id => id.Equals(videoId, StringComparison.OrdinalIgnoreCase));
        }

        public static void DeleteVideo(VideoItem video)
        {
            if (video == null || string.IsNullOrWhiteSpace(video.Id)) return;

            if (!Settings.DeletedVideoIds.Contains(video.Id, StringComparer.OrdinalIgnoreCase))
            {
                Settings.DeletedVideoIds.Add(video.Id);
            }

            // Remove from saved collections
            Settings.Favorites.RemoveAll(v => v.Id.Equals(video.Id, StringComparison.OrdinalIgnoreCase));
            Settings.WatchLater.RemoveAll(v => v.Id.Equals(video.Id, StringComparison.OrdinalIgnoreCase));
            Settings.WatchHistory.RemoveAll(v => v.Id.Equals(video.Id, StringComparison.OrdinalIgnoreCase));
            Settings.Downloads.RemoveAll(v => v.Id.Equals(video.Id, StringComparison.OrdinalIgnoreCase));

            // If downloaded file exists, delete it if possible
            if (video.IsDownloaded && !string.IsNullOrWhiteSpace(video.LocalFilePath))
            {
                try
                {
                    if (File.Exists(video.LocalFilePath))
                    {
                        File.Delete(video.LocalFilePath);
                    }
                }
                catch { }
            }

            Save();
        }

        public static bool IsDeleted(string videoId)
        {
            if (string.IsNullOrWhiteSpace(videoId)) return false;
            return Settings.DeletedVideoIds.Any(id => id.Equals(videoId, StringComparison.OrdinalIgnoreCase));
        }

        public static void RestoreDeletedVideo(string videoId)
        {
            if (string.IsNullOrWhiteSpace(videoId)) return;
            Settings.DeletedVideoIds.RemoveAll(id => id.Equals(videoId, StringComparison.OrdinalIgnoreCase));
            Save();
        }

        public static void ToggleWatchLater(VideoItem video)
        {
            var existing = Settings.WatchLater.Find(v => v.Id == video.Id);
            if (existing != null)
            {
                Settings.WatchLater.Remove(existing);
                video.IsWatchLater = false;
            }
            else
            {
                video.IsWatchLater = true;
                Settings.WatchLater.Insert(0, video);
            }
            Save();
        }

        public static void AddDownload(VideoItem video)
        {
            if (video == null || string.IsNullOrWhiteSpace(video.Id)) return;
            Settings.Downloads.RemoveAll(v => v.Id.Equals(video.Id, StringComparison.OrdinalIgnoreCase));
            Settings.Downloads.Insert(0, video);
            Save();
        }

        public static void SavePlaybackPosition(string videoId, double positionSeconds)
        {
            if (string.IsNullOrWhiteSpace(videoId)) return;
            if (positionSeconds > 3)
            {
                Settings.WatchPositions[videoId] = positionSeconds;
                Save();
            }
        }

        public static double GetPlaybackPosition(string videoId)
        {
            if (string.IsNullOrWhiteSpace(videoId)) return 0;
            if (Settings.WatchPositions.TryGetValue(videoId, out double pos))
            {
                return pos;
            }
            return 0;
        }

        public static void SetUserAccount(UserAccount account)
        {
            Settings.UserAccount = account ?? new UserAccount();
            Save();
        }

        public static void SignOutUser()
        {
            Settings.UserAccount = new UserAccount();
            Save();
        }

        public static void SetPreferredQuality(string quality)
        {
            Settings.PreferredQuality = quality ?? "hd1080";
            Save();
        }

        public static void AddSearchHistory(string query)
        {
            if (string.IsNullOrWhiteSpace(query)) return;
            query = query.Trim();
            Settings.SearchHistory.RemoveAll(q => string.Equals(q, query, StringComparison.OrdinalIgnoreCase));
            Settings.SearchHistory.Insert(0, query);
            if (Settings.SearchHistory.Count > 15)
            {
                Settings.SearchHistory = Settings.SearchHistory.Take(15).ToList();
            }
            Save();
        }

        public static void RemoveSearchHistoryItem(string query)
        {
            Settings.SearchHistory.RemoveAll(q => string.Equals(q, query, StringComparison.OrdinalIgnoreCase));
            Save();
        }

        public static void ClearSearchHistory()
        {
            Settings.SearchHistory.Clear();
            Save();
        }

        public static void ResetAlgorithm()
        {
            Settings.WatchHistory.Clear();
            Settings.DislikedVideoIds.Clear();
            Settings.DislikedChannels.Clear();
            Settings.WatchPositions.Clear();
            Save();
        }
    }
}
