using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public class AlgorithmSettings
    {
        public float CreatorWeight { get; set; } = 0.7f;
        public float DiscoveryRatio { get; set; } = 0.2f;
        public List<string> BlockedKeywords { get; set; } = new List<string>();
        public List<string> BoostedTopics { get; set; } = new List<string>();
    }

    public static class RecommendationEngine
    {
        private static readonly AlgorithmSettings DefaultSettings = new AlgorithmSettings();

        public static string GetRecommendationReason(
            VideoItem video,
            List<VideoItem> favorites,
            List<VideoItem> watchHistory,
            List<string> subscribedChannels)
        {
            if (StorageService.IsDisliked(video.Id) || StorageService.IsDeleted(video.Id)) return "⛔ Deprioritized / Removed";
            var savedPos = StorageService.GetPlaybackPosition(video.Id);
            if (savedPos > 3) return "🕒 Continue Watching";
            if (video.IsFavorite) return "👍 Liked Favorite";
            if (video.IsWatchLater) return "🔖 Saved Watch Later";

            var isSubscribed = subscribedChannels.Any(c =>
                c.Contains(video.ChannelTitle, StringComparison.OrdinalIgnoreCase) ||
                video.ChannelTitle.Contains(c, StringComparison.OrdinalIgnoreCase));
            if (isSubscribed) return "💡 Subscribed Channel";

            var hasWatched = watchHistory.Any(v => v.ChannelTitle.Equals(video.ChannelTitle, StringComparison.OrdinalIgnoreCase));
            if (hasWatched) return "🔥 Channel You Enjoy";

            return "📈 Popular Recommendation";
        }

        public static List<VideoItem> ScoreAndRankVideos(
            List<VideoItem> videos,
            List<VideoItem> favorites,
            List<VideoItem> watchHistory,
            List<string>? subscribedChannels = null,
            AlgorithmSettings? settings = null)
        {
            if (videos == null || videos.Count == 0) return new List<VideoItem>();

            var currentSettings = settings ?? DefaultSettings;
            var subChannels = subscribedChannels ?? WillRyanProfileData.SubscribedChannels;
            var dislikedIds = new HashSet<string>(StorageService.Settings.DislikedVideoIds ?? new List<string>(), StringComparer.OrdinalIgnoreCase);
            var deletedIds = new HashSet<string>(StorageService.Settings.DeletedVideoIds ?? new List<string>(), StringComparer.OrdinalIgnoreCase);
            var dislikedChannels = new HashSet<string>(StorageService.Settings.DislikedChannels ?? new List<string>(), StringComparer.OrdinalIgnoreCase);

            var blockedLower = currentSettings.BlockedKeywords
                .Select(k => k.Trim().ToLowerInvariant())
                .Where(k => !string.IsNullOrEmpty(k))
                .ToList();

            var boostedLower = currentSettings.BoostedTopics
                .Select(b => b.Trim().ToLowerInvariant())
                .Where(b => !string.IsNullOrEmpty(b))
                .ToList();

            // 0. Filter out permanently disliked, deleted, and blocked videos
            var filteredVideos = videos.Where(v =>
            {
                if (dislikedIds.Contains(v.Id) || deletedIds.Contains(v.Id)) return false;
                var titleLower = v.Title.ToLowerInvariant();
                var chanLower = v.ChannelTitle.ToLowerInvariant();
                return !blockedLower.Any(blk => titleLower.Contains(blk) || chanLower.Contains(blk));
            }).ToList();

            // 1. Identify top favorite channels
            var topChannels = favorites.Select(f => f.ChannelTitle)
                .Concat(watchHistory.Select(w => w.ChannelTitle))
                .GroupBy(c => c, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(g => g.Key, g => g.Count(), StringComparer.OrdinalIgnoreCase);

            // 2. Identify top watched keywords in titles
            var stopWords = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
            {
                "video", "with", "this", "that", "from", "2026", "youtube", "official"
            };

            var topKeywords = watchHistory
                .SelectMany(w => Regex.Split(w.Title.ToLowerInvariant(), @"\s+"))
                .Where(w => w.Length > 3 && !stopWords.Contains(w))
                .GroupBy(w => w, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(g => g.Key, g => g.Count(), StringComparer.OrdinalIgnoreCase);

            var scoredList = new List<(VideoItem Video, float Score)>();

            foreach (var video in filteredVideos)
            {
                float score = 50.0f;

                // A. Favorite & Watch Later Boost
                if (video.IsFavorite) score += 50.0f;
                if (video.IsWatchLater) score += 25.0f;

                // B. User Boosted Topics & Creators (+90 pts)
                var titleLower = video.Title.ToLowerInvariant();
                var chanLower = video.ChannelTitle.ToLowerInvariant();
                if (boostedLower.Any(bst => titleLower.Contains(bst) || chanLower.Contains(bst)))
                {
                    score += 90.0f;
                }

                // C. Disliked Creator / Negative Score Penalty (-80 pts)
                if (dislikedChannels.Contains(video.ChannelTitle) || dislikedChannels.Any(dc => dc.Contains(video.ChannelTitle, StringComparison.OrdinalIgnoreCase)))
                {
                    score -= 80.0f; // Heavy penalty for creators with disliked videos
                }

                // D. Subscribed Profile Channel Boost (+80 * CreatorWeight)
                var isSubscribedProfileChannel = subChannels.Any(c =>
                    c.Contains(video.ChannelTitle, StringComparison.OrdinalIgnoreCase) ||
                    video.ChannelTitle.Contains(c, StringComparison.OrdinalIgnoreCase));
                if (isSubscribedProfileChannel)
                {
                    score += 80.0f * currentSettings.CreatorWeight;
                }

                if (topChannels.TryGetValue(video.ChannelTitle, out int channelHits) && channelHits > 0)
                {
                    score += Math.Min(channelHits * 15.0f * currentSettings.CreatorWeight, 50.0f);
                }

                // E. Keyword & Subject Affinity
                var titleWords = Regex.Split(titleLower, @"\s+");
                int keywordHits = 0;
                foreach (var word in titleWords)
                {
                    if (topKeywords.TryGetValue(word, out int hits))
                    {
                        keywordHits += hits;
                    }
                }
                if (keywordHits > 0)
                {
                    score += Math.Min(keywordHits * 4.0f, 30.0f);
                }

                // F. Discovery Boost
                if (channelHits == 0 && !isSubscribedProfileChannel)
                {
                    score += currentSettings.DiscoveryRatio * 75.0f;
                }

                // G. Watched Progress / Deprioritize Completed Videos on Discovery Feed
                var savedPos = StorageService.GetPlaybackPosition(video.Id);
                if (savedPos > 3)
                {
                    score -= 60.0f;
                }

                video.AlgorithmScore = score;
                video.RecommendationReason = GetRecommendationReason(video, favorites, watchHistory, subChannels);

                scoredList.Add((video, score));
            }

            return scoredList
                .Where(s => s.Score > 0) // Exclude items with negative net score from top recommendations
                .OrderByDescending(s => s.Score)
                .Select(s => s.Video)
                .ToList();
        }
    }
}
