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

        public static async Task<List<VideoItem>> SearchVideosAsync(string query, int maxResults = 50, string? spFilter = null, bool sortByUploadDate = false)
        {
            var results = new List<VideoItem>();
            var seenIds = new HashSet<string>();

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
                    WalkJsonTree(jObj, results, seenIds, maxResults);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"ytInitialData parse error: {ex.Message}");
            }

            // 2. YoutubeExplode stream pagination to fill up to maxResults
            if (results.Count < maxResults)
            {
                try
                {
                    var searchResults = _client.Search.GetVideosAsync(query);
                    await foreach (var video in searchResults)
                    {
                        if (!seenIds.Contains(video.Id.Value))
                        {
                            seenIds.Add(video.Id.Value);
                            results.Add(new VideoItem
                            {
                                Id = video.Id.Value,
                                Title = video.Title,
                                ChannelTitle = video.Author.ChannelTitle,
                                ChannelId = video.Author.ChannelId.Value,
                                ThumbnailUrl = video.Thumbnails.OrderByDescending(t => t.Resolution.Area).FirstOrDefault()?.Url ?? $"https://i.ytimg.com/vi/{video.Id.Value}/hqdefault.jpg",
                                Duration = video.Duration,
                                DurationText = video.Duration.HasValue ? FormatDuration(video.Duration.Value) : "Live",
                                UploadDateText = ""
                            });
                        }

                        if (results.Count >= maxResults) break;
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"YoutubeExplode search fallback error: {ex.Message}");
                }
            }

            return results.Where(v => !StorageService.IsDisliked(v.Id)).ToList();
        }

        public static async Task<List<VideoItem>> FetchNextSearchBatchAsync(string query, HashSet<string> existingIds, int takeCount = 35)
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
                        results.Add(new VideoItem
                        {
                            Id = video.Id.Value,
                            Title = video.Title,
                            ChannelTitle = video.Author.ChannelTitle,
                            ChannelId = video.Author.ChannelId.Value,
                            ThumbnailUrl = video.Thumbnails.OrderByDescending(t => t.Resolution.Area).FirstOrDefault()?.Url ?? $"https://i.ytimg.com/vi/{video.Id.Value}/hqdefault.jpg",
                            Duration = video.Duration,
                            DurationText = video.Duration.HasValue ? FormatDuration(video.Duration.Value) : "Live",
                            UploadDateText = ""
                        });

                        if (results.Count >= takeCount) break;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error fetching next search batch: {ex.Message}");
            }
            return results.Where(v => !StorageService.IsDisliked(v.Id)).ToList();
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

        public static async Task<List<VideoItem>> GetChannelVideosFeedAsync(string channelNameOrId)
        {
            if (string.IsNullOrWhiteSpace(channelNameOrId)) return new List<VideoItem>();

            var channelQuery = channelNameOrId.Trim();
            var results = new List<VideoItem>();
            var seenIds = new HashSet<string>();

            try
            {
                string? targetUrl = null;
                if (channelQuery.StartsWith("UC") && channelQuery.Length == 24)
                {
                    targetUrl = $"https://www.youtube.com/channel/{channelQuery}/videos";
                }
                else if (channelQuery.StartsWith("@"))
                {
                    targetUrl = $"https://www.youtube.com/{channelQuery}/videos";
                }
                else
                {
                    // Search for the creator channel using YouTube channel filter
                    var searchUrl = $"https://www.youtube.com/results?search_query={Uri.EscapeDataString(channelQuery)}&sp=EgIQAg%3D%3D&hl=en&gl=US";
                    var searchReq = new HttpRequestMessage(HttpMethod.Get, searchUrl);
                    searchReq.Headers.Add("Cookie", "PREF=hl=en&gl=US; SOCS=CAI");
                    var searchResp = await _httpClient.SendAsync(searchReq);
                    var searchHtml = await searchResp.Content.ReadAsStringAsync();

                    var sm = Regex.Match(searchHtml, @"(?:var\s+ytInitialData\s*=\s*|ytInitialData\s*=\s*)(\{.+?\});(?:</script>|\n)", RegexOptions.Singleline);
                    if (sm.Success)
                    {
                        var sObj = JObject.Parse(sm.Groups[1].Value);
                        JToken? cr = null;
                        void FindChannelRenderer(JToken token)
                        {
                            if (cr != null || token == null) return;
                            if (token is JObject jo)
                            {
                                if (jo["channelRenderer"] != null)
                                {
                                    cr = jo["channelRenderer"];
                                    return;
                                }
                                foreach (var prop in jo.Properties()) FindChannelRenderer(prop.Value);
                            }
                            else if (token is JArray ja)
                            {
                                foreach (var item in ja) FindChannelRenderer(item);
                            }
                        }
                        FindChannelRenderer(sObj);

                        if (cr != null)
                        {
                            var canonicalUrl = cr?["navigationEndpoint"]?["browseEndpoint"]?["canonicalBaseUrl"]?.ToString();
                            var cid = cr?["channelId"]?.ToString();
                            if (!string.IsNullOrWhiteSpace(canonicalUrl))
                            {
                                targetUrl = $"https://www.youtube.com{canonicalUrl}/videos";
                            }
                            else if (!string.IsNullOrWhiteSpace(cid))
                            {
                                targetUrl = $"https://www.youtube.com/channel/{cid}/videos";
                            }
                        }
                    }
                }

                if (!string.IsNullOrWhiteSpace(targetUrl))
                {
                    var req = new HttpRequestMessage(HttpMethod.Get, targetUrl);
                    req.Headers.Add("Cookie", "PREF=hl=en&gl=US; SOCS=CAI");
                    var resp = await _httpClient.SendAsync(req);
                    var html = await resp.Content.ReadAsStringAsync();

                    var vm = Regex.Match(html, @"(?:var\s+ytInitialData\s*=\s*|ytInitialData\s*=\s*)(\{.+?\});(?:</script>|\n)", RegexOptions.Singleline);
                    if (vm.Success)
                    {
                        var vObj = JObject.Parse(vm.Groups[1].Value);
                        string channelTitle = vObj?["metadata"]?["channelMetadataRenderer"]?["title"]?.ToString() ?? channelQuery;

                        void ExtractChannelVideos(JToken token)
                        {
                            if (token == null || results.Count >= 50) return;

                            if (token is JObject jo)
                            {
                                // Modern lockupViewModel
                                if (jo["lockupViewModel"] is JObject lum)
                                {
                                    var vid = lum["contentId"]?.ToString();
                                    if (!string.IsNullOrWhiteSpace(vid) && vid.Length == 11 && seenIds.Add(vid))
                                    {
                                        var meta = lum["metadata"]?["lockupMetadataViewModel"];
                                        var title = meta?["title"]?["content"]?.ToString() ?? "";
                                        var rows = meta?["metadata"]?["contentMetadataViewModel"]?["metadataRows"] as JArray;
                                        string views = "";
                                        string pub = "";
                                        if (rows != null && rows.Count > 0)
                                        {
                                            var parts = rows[0]?["metadataParts"] as JArray;
                                            if (parts != null && parts.Count > 0) views = parts[0]?["text"]?["content"]?.ToString() ?? "";
                                            if (parts != null && parts.Count > 1) pub = parts[1]?["text"]?["content"]?.ToString() ?? "";
                                        }

                                        string dur = "Video";
                                        var tov = lum["contentImage"]?["thumbnailViewModel"]?["overlays"] as JArray;
                                        if (tov != null)
                                        {
                                            foreach (var ov in tov)
                                            {
                                                var badges = ov?["thumbnailBottomOverlayViewModel"]?["badges"] as JArray;
                                                if (badges != null)
                                                {
                                                    foreach (var badge in badges)
                                                    {
                                                        var badgeText = badge?["thumbnailBadgeViewModel"]?["text"]?.ToString();
                                                        if (!string.IsNullOrWhiteSpace(badgeText))
                                                        {
                                                            dur = badgeText;
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (dur != "Video") break;
                                            }
                                        }

                                        if (!string.IsNullOrWhiteSpace(title))
                                        {
                                            results.Add(new VideoItem
                                            {
                                                Id = vid,
                                                Title = System.Net.WebUtility.HtmlDecode(title),
                                                ChannelTitle = System.Net.WebUtility.HtmlDecode(channelTitle),
                                                ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
                                                DurationText = dur,
                                                UploadDateText = System.Net.WebUtility.HtmlDecode(pub),
                                                ViewCountText = System.Net.WebUtility.HtmlDecode(views)
                                            });
                                        }
                                    }
                                }
                                // Traditional videoRenderer
                                else if (jo["videoRenderer"] is JObject vr)
                                {
                                    var vid = vr["videoId"]?.ToString();
                                    if (!string.IsNullOrWhiteSpace(vid) && vid.Length == 11 && seenIds.Add(vid))
                                    {
                                        var title = "";
                                        var titleRuns = vr["title"]?["runs"] as JArray;
                                        if (titleRuns != null && titleRuns.Count > 0)
                                            title = string.Join("", titleRuns.Select(r => r["text"]?.ToString() ?? ""));
                                        else
                                            title = vr["title"]?["simpleText"]?.ToString() ?? "";

                                        var pub = vr["publishedTimeText"]?["simpleText"]?.ToString() ?? "";
                                        var views = vr["shortViewCountText"]?["simpleText"]?.ToString() ?? "";
                                        var dur = vr["lengthText"]?["simpleText"]?.ToString() ?? "Video";

                                        if (!string.IsNullOrWhiteSpace(title))
                                        {
                                            results.Add(new VideoItem
                                            {
                                                Id = vid,
                                                Title = System.Net.WebUtility.HtmlDecode(title),
                                                ChannelTitle = System.Net.WebUtility.HtmlDecode(channelTitle),
                                                ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
                                                DurationText = dur,
                                                UploadDateText = System.Net.WebUtility.HtmlDecode(pub),
                                                ViewCountText = System.Net.WebUtility.HtmlDecode(views)
                                            });
                                        }
                                    }
                                }

                                foreach (var prop in jo.Properties()) ExtractChannelVideos(prop.Value);
                            }
                            else if (token is JArray ja)
                            {
                                foreach (var it in ja) ExtractChannelVideos(it);
                            }
                        }

                        if (vObj != null) ExtractChannelVideos(vObj);
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Channel feed extraction error: {ex.Message}");
            }

            // Fallback: If channel direct scraping returned nothing, use search sorted by date
            if (results.Count == 0)
            {
                results = await SearchVideosAsync(channelQuery, 35, sortByUploadDate: true);
            }

            return results.Where(v => !StorageService.IsDisliked(v.Id)).ToList();
        }

        public static async Task<List<VideoItem>> GetSubscribedFeedAsync(string? channelName = null)
        {
            var channels = WillRyanProfileData.SubscribedChannels;
            if (!string.IsNullOrWhiteSpace(channelName))
            {
                return await GetChannelVideosFeedAsync(channelName);
            }

            var list = new List<VideoItem>();
            var rand = new Random();
            var sampled = channels.OrderBy(_ => rand.Next()).Take(8).ToList();
            var tasks = sampled.Select(c => SearchVideosAsync(c, 5, sortByUploadDate: true)).ToList();
            var batchResults = await Task.WhenAll(tasks);

            foreach (var b in batchResults)
            {
                list.AddRange(b);
            }

            var uniqueVideos = list.GroupBy(v => v.Id).Select(g => g.First()).ToList();
            return RecommendationEngine.ScoreAndRankVideos(
                uniqueVideos,
                StorageService.Settings.Favorites,
                StorageService.Settings.WatchHistory,
                channels
            );
        }

        public static async Task<List<VideoItem>> GetHomeFeedAsync()
        {
            var rawList = new List<VideoItem>();
            var channels = WillRyanProfileData.SubscribedChannels;

            var rand = new Random();
            var sampledChannels = channels.OrderBy(_ => rand.Next()).Take(4).ToList();
            var channelTasks = sampledChannels.Select(c => SearchVideosAsync(c, 5)).ToList();

            var topics = new[] { "Tech News", "AI Breakthroughs", "World News Today", "Trending Music" };
            var topicTask = SearchVideosAsync(topics[rand.Next(topics.Length)], 12);

            var allTasks = new List<Task<List<VideoItem>>>(channelTasks) { topicTask };
            var allResults = await Task.WhenAll(allTasks);

            foreach (var res in allResults)
            {
                rawList.AddRange(res);
            }

            var uniqueVideos = rawList.GroupBy(v => v.Id).Select(g => g.First()).ToList();

            // Run exact recommendation engine scoring & ranking
            return RecommendationEngine.ScoreAndRankVideos(
                uniqueVideos,
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
                        var dt = (v.UploadDateText ?? "").ToLowerInvariant();
                        return dt.Contains("minute") || dt.Contains("second") || dt.Contains("moment") || dt.Contains("1 hour");
                    }).ToList();
                }
                else if (dateFilter == "today")
                {
                    // Strict Today: less than 24h (seconds, minutes, hours, moments, today). Excludes "1 day ago", "2 days ago", etc.
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").ToLowerInvariant();
                        if (dt.Contains("minute") || dt.Contains("second") || dt.Contains("hour") || dt.Contains("moment") || dt.Contains("today"))
                        {
                            return true;
                        }
                        return false;
                    }).ToList();
                }
                else if (dateFilter == "week")
                {
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").ToLowerInvariant();
                        return !dt.Contains("month") && !dt.Contains("year");
                    }).ToList();
                }
                else if (dateFilter == "month")
                {
                    list = list.Where(v => {
                        var dt = (v.UploadDateText ?? "").ToLowerInvariant();
                        return !dt.Contains("year");
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

            string? spParam = null;
            if (sortBy == "latest") spParam = "CAI%3D";
            else if (sortBy == "views") spParam = "CAM%3D";
            else if (dateFilter == "today") spParam = "EgIIAg%3D%3D";
            else if (dateFilter == "week") spParam = "EgIIAw%3D%3D";
            else if (dateFilter == "month") spParam = "EgIIBA%3D%3D";

            var searchQueries = new[] { "breaking news", "trending today", "latest podcast", "technology news", "viral" };
            var tasks = searchQueries.Select(q => SearchVideosAsync(q, 25, spFilter: spParam)).ToList();

            // Also query top subscribed channels with sort by upload date
            foreach (var ch in WillRyanProfileData.SubscribedChannels.Take(5))
            {
                tasks.Add(GetSubscribedFeedAsync(ch));
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
