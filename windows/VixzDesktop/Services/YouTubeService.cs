using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using YoutubeExplode;
using YoutubeExplode.Common;
using YoutubeExplode.Search;
using YoutubeExplode.Videos.Streams;
using VixzDesktop.Models;

using System.Net.Http;
using System.IO;
using System.Text.RegularExpressions;
using Newtonsoft.Json.Linq;

namespace VixzDesktop.Services
{
    public class YouTubeService
    {
        private static readonly YoutubeClient _client = new YoutubeClient();
        private static readonly HttpClient _httpClient = new HttpClient();

        static YouTubeService()
        {
            try
            {
                _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
                _httpClient.DefaultRequestHeaders.Add("Accept-Language", "en-US,en;q=0.9");
            }
            catch { }
        }

        public static string? BuildSearchFilterSp(string? dateFilter, string? durationFilter, string? sortBy)
        {
            if (dateFilter == "today" && sortBy == "views") return "CAMSBAgCEAE%3D";
            if (dateFilter == "today" && (sortBy == "latest" || sortBy == null)) return "CAISAggC";
            if (dateFilter == "today") return "EgIIAg%3D%3D";

            if (dateFilter == "week" && sortBy == "views") return "CAMSBAgDEAE%3D";
            if (dateFilter == "week" && (sortBy == "latest" || sortBy == null)) return "CAISAggD";
            if (dateFilter == "week") return "EgIIAw%3D%3D";

            if (dateFilter == "month" && sortBy == "views") return "CAMSBAgEEAE%3D";
            if (dateFilter == "month" && (sortBy == "latest" || sortBy == null)) return "CAISAggE";
            if (dateFilter == "month") return "EgIIBA%3D%3D";

            if (dateFilter == "hour" && sortBy == "views") return "CAMSBAgBEAE%3D";
            if (dateFilter == "hour") return "EgIIAQ%3D%3D";

            if (durationFilter == "short" && sortBy == "views") return "CAMSBAgBEAE%3D";
            if (durationFilter == "short" && sortBy == "latest") return "CAISBAgBEAE%3D";
            if (durationFilter == "short") return "EgQQARgB";

            if (durationFilter == "medium" && sortBy == "views") return "CAMSBAgDEAE%3D";
            if (durationFilter == "medium" && sortBy == "latest") return "CAISBAgDEAE%3D";
            if (durationFilter == "medium") return "EgQQARgD";

            if (durationFilter == "long" && sortBy == "views") return "CAMSBAgCEAE%3D";
            if (durationFilter == "long" && sortBy == "latest") return "CAISBAgCEAE%3D";
            if (durationFilter == "long") return "EgQQARgC";

            if (sortBy == "latest") return "CAI%3D";
            if (sortBy == "views") return "CAM%3D";
            if (sortBy == "rating") return "CAE%3D";

            return null;
        }

        public static async Task<List<VideoItem>> SearchVideosAsync(
            string query, 
            int maxResults = 50, 
            string? spFilter = null, 
            string? dateFilter = null, 
            string? durationFilter = null, 
            string? sortBy = null, 
            bool sortByUploadDate = false)
        {
            var results = new List<VideoItem>();
            var seenIds = new HashSet<string>();

            // Auto-compute spFilter if not explicitly provided but filter options are present
            if (string.IsNullOrWhiteSpace(spFilter) && (!string.IsNullOrWhiteSpace(dateFilter) || !string.IsNullOrWhiteSpace(durationFilter) || !string.IsNullOrWhiteSpace(sortBy)))
            {
                spFilter = BuildSearchFilterSp(dateFilter, durationFilter, sortBy);
            }

            // 1. High-fidelity extraction via ytInitialData JSON
            try
            {
                var encoded = Uri.EscapeDataString(query);
                string sortParam = "";
                if (!string.IsNullOrWhiteSpace(spFilter))
                {
                    sortParam = $"&sp={spFilter}";
                }
                else if (sortByUploadDate)
                {
                    sortParam = "&sp=CAI%3D";
                }

                var url = $"https://www.youtube.com/results?search_query={encoded}{sortParam}&hl=en&gl=US";

                var request = new HttpRequestMessage(HttpMethod.Get, url);
                request.Headers.Add("Cookie", "PREF=hl=en&gl=US; SOCS=CAI");

                var response = await _httpClient.SendAsync(request);
                var html = await response.Content.ReadAsStringAsync();

                var match = Regex.Match(html, @"(?:var\s+ytInitialData\s*=\s*|ytInitialData\s*=\s*)(\{.+?\});(?:</script>|\n)", RegexOptions.Singleline);
                if (match.Success)
                {
                    var jsonStr = match.Groups[1].Value;
                    var jObj = JObject.Parse(jsonStr);
                    WalkJsonTree(jObj, results, seenIds, 150);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"ytInitialData parse error: {ex.Message}");
            }

            // Apply filter to the first batch
            var filteredResults = ApplyLocalFilters(results, dateFilter, durationFilter, sortBy);

            // 2. YoutubeExplode stream pagination to ensure we reach the requested maxResults of MATCHING items
            if (filteredResults.Count < maxResults)
            {
                try
                {
                    var searchResults = _client.Search.GetVideosAsync(query);
                    await foreach (var video in searchResults)
                    {
                        if (!seenIds.Contains(video.Id.Value))
                        {
                            seenIds.Add(video.Id.Value);
                            var item = new VideoItem
                            {
                                Id = video.Id.Value,
                                Title = video.Title,
                                ChannelTitle = video.Author.ChannelTitle,
                                ChannelId = video.Author.ChannelId.Value,
                                ThumbnailUrl = video.Thumbnails.OrderByDescending(t => t.Resolution.Area).FirstOrDefault()?.Url ?? $"https://i.ytimg.com/vi/{video.Id.Value}/hqdefault.jpg",
                                Duration = video.Duration,
                                DurationText = video.Duration.HasValue ? FormatDuration(video.Duration.Value) : "Live",
                                UploadDateText = ""
                            };

                            // Quick duration filter check
                            if (durationFilter == "short" && item.Duration.HasValue && item.Duration.Value.TotalMinutes >= 4) continue;
                            if (durationFilter == "medium" && item.Duration.HasValue && (item.Duration.Value.TotalMinutes < 4 || item.Duration.Value.TotalMinutes > 20)) continue;
                            if (durationFilter == "long" && item.Duration.HasValue && item.Duration.Value.TotalMinutes <= 20) continue;

                            results.Add(item);
                            filteredResults = ApplyLocalFilters(results, dateFilter, durationFilter, sortBy);

                            if (filteredResults.Count >= maxResults) break;
                        }
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"YoutubeExplode search fallback error: {ex.Message}");
                }
            }

            return filteredResults.Where(v => !StorageService.IsDisliked(v.Id)).ToList();
        }

        public static async Task<List<VideoItem>> FetchNextSearchBatchAsync(
            string query, 
            HashSet<string> existingIds, 
            int takeCount = 35,
            string? dateFilter = null,
            string? durationFilter = null,
            string? sortBy = null)
        {
            var results = new List<VideoItem>();
            try
            {
                var searchResults = _client.Search.GetVideosAsync(query);
                await foreach (var video in searchResults)
                {
                    if (!existingIds.Contains(video.Id.Value))
                    {
                        existingIds.Add(video.Id.Value);
                        var item = new VideoItem
                        {
                            Id = video.Id.Value,
                            Title = video.Title,
                            ChannelTitle = video.Author.ChannelTitle,
                            ChannelId = video.Author.ChannelId.Value,
                            ThumbnailUrl = video.Thumbnails.OrderByDescending(t => t.Resolution.Area).FirstOrDefault()?.Url ?? $"https://i.ytimg.com/vi/{video.Id.Value}/hqdefault.jpg",
                            Duration = video.Duration,
                            DurationText = video.Duration.HasValue ? FormatDuration(video.Duration.Value) : "Live",
                            UploadDateText = ""
                        };

                        if (durationFilter == "short" && item.Duration.HasValue && item.Duration.Value.TotalMinutes >= 4) continue;
                        if (durationFilter == "medium" && item.Duration.HasValue && (item.Duration.Value.TotalMinutes < 4 || item.Duration.Value.TotalMinutes > 20)) continue;
                        if (durationFilter == "long" && item.Duration.HasValue && item.Duration.Value.TotalMinutes <= 20) continue;

                        results.Add(item);

                        if (results.Count >= takeCount) break;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error fetching next search batch: {ex.Message}");
            }
            return ApplyLocalFilters(results, dateFilter, durationFilter, sortBy).Where(v => !StorageService.IsDisliked(v.Id)).ToList();
        }

        private static void WalkJsonTree(JToken token, List<VideoItem> results, HashSet<string> seenIds, int maxResults)
        {
            if (token == null || results.Count >= maxResults) return;

            if (token is JObject obj)
            {
                var videoId = obj["videoId"]?.ToString();
                var titleToken = obj["title"];

                if (!string.IsNullOrEmpty(videoId) && videoId.Length == 11 && titleToken != null)
                {
                    if (!seenIds.Contains(videoId))
                    {
                        seenIds.Add(videoId);

                        // 1. Title
                        string title = "";
                        var runs = titleToken["runs"] as JArray;
                        if (runs != null && runs.Count > 0)
                        {
                            title = string.Join("", runs.Select(r => r["text"]?.ToString() ?? ""));
                        }
                        else
                        {
                            title = titleToken["simpleText"]?.ToString() ?? "";
                        }

                        // 2. Channel Title
                        string channel = "";
                        var ownerToken = obj["ownerText"] ?? obj["shortBylineText"] ?? obj["longBylineText"];
                        var ownerRuns = ownerToken?["runs"] as JArray;
                        if (ownerRuns != null && ownerRuns.Count > 0)
                        {
                            channel = string.Join("", ownerRuns.Select(r => r["text"]?.ToString() ?? ""));
                        }
                        else
                        {
                            channel = ownerToken?["simpleText"]?.ToString() ?? "";
                        }

                        // 3. Published Time
                        string pubTime = "";
                        var pubToken = obj["publishedTimeText"];
                        if (pubToken != null)
                        {
                            pubTime = pubToken["simpleText"]?.ToString() ?? "";
                            if (string.IsNullOrEmpty(pubTime))
                            {
                                var pRuns = pubToken["runs"] as JArray;
                                if (pRuns != null && pRuns.Count > 0)
                                {
                                    pubTime = string.Join("", pRuns.Select(r => r["text"]?.ToString() ?? ""));
                                }
                            }
                        }

                        // 4. View Count
                        string viewCount = "";
                        var viewToken = obj["shortViewCountText"] ?? obj["viewCountText"];
                        if (viewToken != null)
                        {
                            viewCount = viewToken["simpleText"]?.ToString() ?? "";
                            if (string.IsNullOrEmpty(viewCount))
                            {
                                var vRuns = viewToken["runs"] as JArray;
                                if (vRuns != null && vRuns.Count > 0)
                                {
                                    viewCount = string.Join("", vRuns.Select(r => r["text"]?.ToString() ?? ""));
                                }
                            }
                        }

                        // Accessibility Fallback for Published Time & View Count
                        var accessLabel = obj["title"]?["accessibility"]?["accessibilityData"]?["label"]?.ToString()
                                       ?? obj["accessibility"]?["accessibilityData"]?["label"]?.ToString()
                                       ?? "";

                        if (string.IsNullOrEmpty(pubTime) && !string.IsNullOrEmpty(accessLabel))
                        {
                            var matchDate = Regex.Match(accessLabel, @"(\d+\s+(?:second|minute|hour|day|week|month|year)s?\s+ago)", RegexOptions.IgnoreCase);
                            if (matchDate.Success)
                            {
                                pubTime = matchDate.Groups[1].Value;
                            }
                            else if (accessLabel.IndexOf("yesterday", StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                pubTime = "Yesterday";
                            }
                            else if (accessLabel.IndexOf("today", StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                pubTime = "Today";
                            }
                        }

                        if (string.IsNullOrEmpty(viewCount) && !string.IsNullOrEmpty(accessLabel))
                        {
                            var matchViews = Regex.Match(accessLabel, @"([\d,]+)\s+views", RegexOptions.IgnoreCase);
                            if (matchViews.Success)
                            {
                                viewCount = $"{matchViews.Groups[1].Value} views";
                            }
                        }

                        // 5. Duration
                        string duration = obj["lengthText"]?["simpleText"]?.ToString() ?? "";
                        if (string.IsNullOrEmpty(duration))
                        {
                            var overlays = obj["thumbnailOverlays"] as JArray;
                            if (overlays != null)
                            {
                                foreach (var ov in overlays)
                                {
                                    var timeText = ov?["thumbnailOverlayTimeStatusRenderer"]?["text"]?["simpleText"]?.ToString();
                                    if (!string.IsNullOrEmpty(timeText))
                                    {
                                        duration = timeText;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!string.IsNullOrWhiteSpace(title) && title != "YouTube Video")
                        {
                            results.Add(new VideoItem
                            {
                                Id = videoId,
                                Title = System.Net.WebUtility.HtmlDecode(title),
                                ChannelTitle = System.Net.WebUtility.HtmlDecode(channel),
                                ThumbnailUrl = $"https://i.ytimg.com/vi/{videoId}/hqdefault.jpg",
                                DurationText = !string.IsNullOrWhiteSpace(duration) ? duration : "Video",
                                UploadDateText = System.Net.WebUtility.HtmlDecode(pubTime),
                                ViewCountText = System.Net.WebUtility.HtmlDecode(viewCount)
                            });
                        }
                    }
                }

                foreach (var prop in obj.Properties())
                {
                    WalkJsonTree(prop.Value, results, seenIds, maxResults);
                }
            }
            else if (token is JArray arr)
            {
                foreach (var child in arr)
                {
                    WalkJsonTree(child, results, seenIds, maxResults);
                }
            }
        }

        public static async Task<List<VideoItem>> GetSubscribedFeedAsync(string? channelName = null)
        {
            var channels = WillRyanProfileData.SubscribedChannels;
            if (!string.IsNullOrWhiteSpace(channelName))
            {
                return await SearchVideosAsync(channelName, 50, spFilter: "CAI%3D", sortByUploadDate: true);
            }

            var list = new List<VideoItem>();
            var seenIds = new HashSet<string>();
            var rand = new Random();
            var sampled = channels.OrderBy(_ => rand.Next()).Take(16).ToList();
            var tasks = sampled.Select(c => SearchVideosAsync(c, 10, spFilter: "CAI%3D", sortByUploadDate: true)).ToList();
            var batchResults = await Task.WhenAll(tasks);

            foreach (var b in batchResults)
            {
                foreach (var v in b)
                {
                    if (seenIds.Add(v.Id))
                    {
                        list.Add(v);
                    }
                }
            }

            return RecommendationEngine.ScoreAndRankVideos(
                list,
                StorageService.Settings.Favorites,
                StorageService.Settings.WatchHistory,
                channels
            );
        }

        public static async Task<List<VideoItem>> GetHomeFeedAsync()
        {
            var rawList = new List<VideoItem>();
            var seenIds = new HashSet<string>();
            var channels = WillRyanProfileData.SubscribedChannels;

            var rand = new Random();
            var sampledChannels = channels.OrderBy(_ => rand.Next()).Take(14).ToList();
            var channelTasks = sampledChannels.Select(c => SearchVideosAsync(c, 10, sortByUploadDate: true)).ToList();

            var topics = new[] { "Trending Worldwide", "Tech & Science News", "World News Today", "Podcasts & Interviews", "Viral Highlights" };
            var topicTasks = topics.Select(t => SearchVideosAsync(t, 15)).ToList();

            var allTasks = new List<Task<List<VideoItem>>>(channelTasks);
            allTasks.AddRange(topicTasks);

            var allResults = await Task.WhenAll(allTasks);

            foreach (var res in allResults)
            {
                foreach (var v in res)
                {
                    if (seenIds.Add(v.Id))
                    {
                        rawList.Add(v);
                    }
                }
            }

            // Run exact recommendation engine scoring & ranking
            return RecommendationEngine.ScoreAndRankVideos(
                rawList,
                StorageService.Settings.Favorites,
                StorageService.Settings.WatchHistory,
                channels
            );
        }

        public static async Task<string?> GetStreamUrlAsync(string videoId)
        {
            try
            {
                var streamManifest = await _client.Videos.Streams.GetManifestAsync(videoId);
                
                // 1. Try muxed streams (combined video + audio)
                var muxedStreamInfo = streamManifest.GetMuxedStreams().GetWithHighestVideoQuality();
                if (muxedStreamInfo != null)
                {
                    return muxedStreamInfo.Url;
                }

                // 2. Fallback to highest quality video-only stream
                var videoOnly = streamManifest.GetVideoOnlyStreams().GetWithHighestVideoQuality();
                return videoOnly?.Url;
            }
            catch
            {
                return null;
            }
        }

        public static async Task<VideoItem?> GetVideoDetailsAsync(string videoId)
        {
            try
            {
                var video = await _client.Videos.GetAsync(videoId);
                return new VideoItem
                {
                    Id = video.Id.Value,
                    Title = video.Title,
                    ChannelTitle = video.Author.ChannelTitle,
                    ChannelId = video.Author.ChannelId.Value,
                    ThumbnailUrl = video.Thumbnails.OrderByDescending(t => t.Resolution.Area).FirstOrDefault()?.Url ?? "",
                    Duration = video.Duration,
                    DurationText = video.Duration.HasValue ? FormatDuration(video.Duration.Value) : "Live",
                    Description = video.Description,
                    UploadDateText = video.UploadDate.ToString("MMM dd, yyyy")
                };
            }
            catch
            {
                return null;
            }
        }

        private static string FormatDuration(TimeSpan duration)
        {
            return duration.Hours > 0
                ? $"{duration.Hours}:{duration.Minutes:D2}:{duration.Seconds:D2}"
                : $"{duration.Minutes}:{duration.Seconds:D2}";
        }

        public static List<VideoItem> ApplyLocalFilters(
            IEnumerable<VideoItem> videos,
            string? dateFilter,
            string? durationFilter,
            string? sortBy)
        {
            var list = videos.Where(v => !StorageService.IsDisliked(v.Id)).ToList();

            // 1. Duration Filter
            if (!string.IsNullOrWhiteSpace(durationFilter))
            {
                if (durationFilter == "short")
                {
                    list = list.Where(v => ParseDurationMinutes(v.DurationText) < 4).ToList();
                }
                else if (durationFilter == "medium")
                {
                    list = list.Where(v => {
                        var m = ParseDurationMinutes(v.DurationText);
                        return m >= 4 && m <= 20;
                    }).ToList();
                }
                else if (durationFilter == "long")
                {
                    list = list.Where(v => ParseDurationMinutes(v.DurationText) > 20).ToList();
                }
            }

            // 2. Date Filter
            if (!string.IsNullOrWhiteSpace(dateFilter))
            {
                if (dateFilter == "hour")
                {
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").Trim();
                        if (string.IsNullOrWhiteSpace(dt)) return false;
                        var sec = ParsePublishedTimeToSeconds(dt);
                        return sec <= 3600; // Within 1 hour
                    }).ToList();
                }
                else if (dateFilter == "today")
                {
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").Trim();
                        if (string.IsNullOrWhiteSpace(dt)) return false;
                        var sec = ParsePublishedTimeToSeconds(dt);
                        return sec <= 86400 * 1.5; // Within 36 hours (e.g. seconds, minutes, hours, 1 day, yesterday)
                    }).ToList();
                }
                else if (dateFilter == "week")
                {
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").Trim();
                        if (string.IsNullOrWhiteSpace(dt)) return false;
                        var sec = ParsePublishedTimeToSeconds(dt);
                        return sec <= 86400 * 7.5; // Within 7 days
                    }).ToList();
                }
                else if (dateFilter == "month")
                {
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").Trim();
                        if (string.IsNullOrWhiteSpace(dt)) return false;
                        var sec = ParsePublishedTimeToSeconds(dt);
                        return sec <= 86400 * 31; // Within 31 days
                    }).ToList();
                }
            }

            // 3. Sort By
            if (!string.IsNullOrWhiteSpace(sortBy))
            {
                if (sortBy == "latest")
                {
                    list = list.OrderBy(v => ParsePublishedTimeToSeconds(v.UploadDateText)).ToList();
                }
                else if (sortBy == "views")
                {
                    list = list.OrderByDescending(v => ParseViewsToNumber(v.ViewCountText)).ToList();
                }
            }

            return list;
        }

        public static async Task<List<VideoItem>> GetDeepFilteredFeedAsync(string? dateFilter, string? durationFilter, string? sortBy)
        {
            var aggregated = new List<VideoItem>();
            var seenIds = new HashSet<string>();

            string? spParam = BuildSearchFilterSp(dateFilter, durationFilter, sortBy);

            var searchQueries = new[] { "trending worldwide", "breaking news", "tech news", "popular podcast", "viral videos", "music hits" };
            var tasks = searchQueries.Select(q => SearchVideosAsync(q, 30, spFilter: spParam, dateFilter: dateFilter, durationFilter: durationFilter, sortBy: sortBy)).ToList();

            // Also query top 10 subscribed channels
            foreach (var ch in WillRyanProfileData.SubscribedChannels.Take(10))
            {
                tasks.Add(SearchVideosAsync(ch, 15, spFilter: spParam, dateFilter: dateFilter, durationFilter: durationFilter, sortBy: sortBy, sortByUploadDate: (sortBy != "views")));
            }

            try
            {
                var batches = await Task.WhenAll(tasks);
                foreach (var batch in batches)
                {
                    foreach (var video in batch)
                    {
                        if (seenIds.Add(video.Id))
                        {
                            aggregated.Add(video);
                        }
                    }
                }
            }
            catch { }

            return ApplyLocalFilters(aggregated, dateFilter, durationFilter, sortBy);
        }

        public static double ParseDurationMinutes(string dur)
        {
            if (string.IsNullOrWhiteSpace(dur)) return 10;
            var parts = dur.Trim().Split(':');
            if (parts.Length == 2 && double.TryParse(parts[0], out var m) && double.TryParse(parts[1], out var s))
            {
                return m + (s / 60.0);
            }
            if (parts.Length == 3 && double.TryParse(parts[0], out var h) && double.TryParse(parts[1], out var m2) && double.TryParse(parts[2], out var s2))
            {
                return (h * 60.0) + m2 + (s2 / 60.0);
            }
            return 10;
        }

        public static long ParsePublishedTimeToSeconds(string text)
        {
            var lower = (text ?? "").ToLowerInvariant();
            if (lower.Contains("sec")) return 30;
            if (lower.Contains("min")) return 600;
            if (lower.Contains("hour"))
            {
                var match = Regex.Match(lower, @"(\d+)");
                var h = match.Success && int.TryParse(match.Groups[1].Value, out var val) ? val : 1;
                return h * 3600;
            }
            if (lower.Contains("day"))
            {
                var match = Regex.Match(lower, @"(\d+)");
                var d = match.Success && int.TryParse(match.Groups[1].Value, out var val) ? val : 1;
                return d * 86400;
            }
            if (lower.Contains("week"))
            {
                var match = Regex.Match(lower, @"(\d+)");
                var w = match.Success && int.TryParse(match.Groups[1].Value, out var val) ? val : 1;
                return w * 604800;
            }
            if (lower.Contains("month"))
            {
                var match = Regex.Match(lower, @"(\d+)");
                var mo = match.Success && int.TryParse(match.Groups[1].Value, out var val) ? val : 1;
                return mo * 2592000;
            }
            if (lower.Contains("year"))
            {
                var match = Regex.Match(lower, @"(\d+)");
                var y = match.Success && int.TryParse(match.Groups[1].Value, out var val) ? val : 1;
                return y * 31536000;
            }
            return 100000000;
        }

        public static long ParseViewsToNumber(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return 0;
            var match = Regex.Match(text, @"([\d\.]+)\s*([KkMmBb]?)");
            if (!match.Success) return 0;
            if (!double.TryParse(match.Groups[1].Value, out var num)) return 0;
            var mult = match.Groups[2].Value.ToUpperInvariant();
            if (mult == "K") return (long)(num * 1000);
            if (mult == "M") return (long)(num * 1000000);
            if (mult == "B") return (long)(num * 1000000000);
            return (long)num;
        }
    }
}
