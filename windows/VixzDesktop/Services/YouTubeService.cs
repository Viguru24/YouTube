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
        private static string? _lastContinuationToken;
        private static string _innerTubeApiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

        private static string? FindContinuationToken(JToken? token)
        {
            if (token == null) return null;
            if (token is JObject obj)
            {
                if (obj["continuationCommand"]?["token"] != null)
                {
                    return obj["continuationCommand"]?["token"]?.ToString();
                }
                foreach (var prop in obj.Properties())
                {
                    var found = FindContinuationToken(prop.Value);
                    if (!string.IsNullOrEmpty(found)) return found;
                }
            }
            else if (token is JArray arr)
            {
                foreach (var item in arr)
                {
                    var found = FindContinuationToken(item);
                    if (!string.IsNullOrEmpty(found)) return found;
                }
            }
            return null;
        }

        private static string FormatViews(long views)
        {
            if (views < 0) return "";
            if (views >= 1_000_000_000) return $"{views / 1_000_000_000.0:0.#}B views";
            if (views >= 1_000_000) return $"{views / 1_000_000.0:0.#}M views";
            if (views >= 1_000) return $"{views / 1_000.0:0.#}K views";
            return $"{views} views";
        }

        static YouTubeService()
        {
            try
            {
                _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd(WebViewManager.CommonUserAgent);
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
                    sortParam = "&sp=CAISAhAB";
                }

                var url = $"https://www.youtube.com/results?search_query={encoded}{sortParam}&hl=en&gl=US";

                var request = new HttpRequestMessage(HttpMethod.Get, url);
                request.Headers.Add("Cookie", "PREF=hl=en&gl=US; SOCS=CAI");

                var response = await _httpClient.SendAsync(request);
                var html = await response.Content.ReadAsStringAsync();

                var match = Regex.Match(html, @"(?:var\s+ytInitialData\s*=\s*|ytInitialData\s*=\s*)(\{.+?\});(?:</script>|\n)", RegexOptions.Singleline);
                var keyMatch = Regex.Match(html, @"""INNERTUBE_API_KEY"":\s*""([^""]+)""");
                if (keyMatch.Success)
                {
                    _innerTubeApiKey = keyMatch.Groups[1].Value;
                }

                if (match.Success)
                {
                    var jsonStr = match.Groups[1].Value;
                    var jObj = JObject.Parse(jsonStr);
                    _lastContinuationToken = FindContinuationToken(jObj);
                    WalkJsonTree(jObj, results, seenIds, maxResults);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"ytInitialData parse error: {ex.Message}");
            }

            // 2. High-fidelity continuation to fill up to maxResults if needed
            if (results.Count < maxResults && !string.IsNullOrEmpty(_lastContinuationToken))
            {
                try
                {
                    var continuationResults = await FetchContinuationBatchAsync(_lastContinuationToken, seenIds, maxResults - results.Count);
                    results.AddRange(continuationResults);
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Continuation search error: {ex.Message}");
                }
            }

            // 3. YoutubeExplode stream pagination fallback to fill up to maxResults
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
                                UploadDateText = "",
                                ViewCountText = ""
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

            // 4. Background enrichment for any videos lacking upload date or views
            var missingMeta = results.Where(v => string.IsNullOrEmpty(v.UploadDateText)).Take(15).ToList();
            if (missingMeta.Count > 0)
            {
                _ = Task.Run(async () =>
                {
                    foreach (var v in missingMeta)
                    {
                        try
                        {
                            var details = await GetVideoDetailsAsync(v.Id);
                            if (details != null)
                            {
                                if (!string.IsNullOrWhiteSpace(details.UploadDateText)) v.UploadDateText = details.UploadDateText;
                                if (!string.IsNullOrWhiteSpace(details.ViewCountText)) v.ViewCountText = details.ViewCountText;
                            }
                        }
                        catch { }
                    }
                });
            }

            return results.Where(v => !StorageService.IsDisliked(v.Id) && !StorageService.IsDeleted(v.Id)).ToList();
        }

        public static async Task<List<VideoItem>> FetchNextSearchBatchAsync(string query, HashSet<string> existingIds, int takeCount = 35)
        {
            var results = new List<VideoItem>();

            // 1. Try high-fidelity InnerTube continuation (contains publishedTimeText and shortViewCountText)
            if (!string.IsNullOrEmpty(_lastContinuationToken))
            {
                var contResults = await FetchContinuationBatchAsync(_lastContinuationToken, existingIds, takeCount);
                if (contResults.Count > 0)
                {
                    results.AddRange(contResults);
                }
            }

            // 2. Fallback to YoutubeExplode if continuation was not available or didn't return enough
            if (results.Count < takeCount)
            {
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
                                UploadDateText = "",
                                ViewCountText = ""
                            });

                            if (results.Count >= takeCount) break;
                        }
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Error fetching next search batch: {ex.Message}");
                }
            }

            // 3. Background enrichment for any videos lacking upload date or views
            var missing = results.Where(v => string.IsNullOrEmpty(v.UploadDateText)).Take(15).ToList();
            if (missing.Count > 0)
            {
                _ = Task.Run(async () =>
                {
                    foreach (var v in missing)
                    {
                        try
                        {
                            var details = await GetVideoDetailsAsync(v.Id);
                            if (details != null)
                            {
                                if (!string.IsNullOrWhiteSpace(details.UploadDateText)) v.UploadDateText = details.UploadDateText;
                                if (!string.IsNullOrWhiteSpace(details.ViewCountText)) v.ViewCountText = details.ViewCountText;
                            }
                        }
                        catch { }
                    }
                });
            }

            return results.Where(v => !StorageService.IsDisliked(v.Id) && !StorageService.IsDeleted(v.Id)).ToList();
        }

        private static async Task<List<VideoItem>> FetchContinuationBatchAsync(string continuationToken, HashSet<string> existingIds, int takeCount = 35)
        {
            var results = new List<VideoItem>();
            try
            {
                var url = $"https://www.youtube.com/youtubei/v1/search?key={_innerTubeApiKey}&prettyPrint=false";
                var bodyObj = new
                {
                    context = new
                    {
                        client = new
                        {
                            clientName = "WEB",
                            clientVersion = "2.20240901.01.00",
                            hl = "en",
                            gl = "US"
                        }
                    },
                    continuation = continuationToken
                };
                var jsonBody = Newtonsoft.Json.JsonConvert.SerializeObject(bodyObj);
                var content = new StringContent(jsonBody, System.Text.Encoding.UTF8, "application/json");
                var request = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
                request.Headers.Add("X-YouTube-Client-Name", "1");
                request.Headers.Add("X-YouTube-Client-Version", "2.20240901.01.00");

                var response = await _httpClient.SendAsync(request);
                if (response.IsSuccessStatusCode)
                {
                    var responseString = await response.Content.ReadAsStringAsync();
                    var cObj = JObject.Parse(responseString);
                    _lastContinuationToken = FindContinuationToken(cObj);
                    WalkJsonTree(cObj, results, existingIds, takeCount);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error fetching continuation batch: {ex.Message}");
            }
            return results;
        }

        private static void WalkJsonTree(JToken token, List<VideoItem> results, HashSet<string> seenIds, int maxResults)
        {
            if (token == null || results.Count >= maxResults) return;

            if (token is JObject obj)
            {
                // Reject secondary shelves / recommendations that inject unrelated older videos
                var shelfTitle = obj["shelfRenderer"]?["title"]?["simpleText"]?.ToString()
                    ?? obj["shelfRenderer"]?["title"]?["runs"]?[0]?["text"]?.ToString() ?? "";
                if (!string.IsNullOrEmpty(shelfTitle))
                {
                    var stLower = shelfTitle.ToLowerInvariant();
                    if (stLower.Contains("people also watched") || 
                        stLower.Contains("previously watched") || 
                        stLower.Contains("for you") || 
                        stLower.Contains("related to"))
                    {
                        return;
                    }
                }

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

        public static readonly Dictionary<string, string> VerifiedHandles = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "veritasium", "veritasium" },
            { "cleo abram", "cleoabram" },
            { "fireship", "Fireship" },
            { "two minute papers", "TwoMinutePapers" },
            { "two bit da vinci", "twobitdavinci" },
            { "ai revolution", "theairevolution" },
            { "julian goldie seo", "JulianGoldieSEO" },
            { "sabine hossenfelder", "SabineHossenfelder" },
            { "theprimetime", "ThePrimeTimeagen" },
            { "yannic kilcher", "YannicKilcher" },
            { "digital foundry", "digitalfoundry" },
            { "gameranx", "gameranx" },
            { "ign", "IGN" },
            { "lofi girl", "LofiGirl" },
            { "ncs", "NoCopyrightSounds" },
            { "chilledcow", "LofiGirl" },
            { "xiaomanyc", "xiaomanyc" },
            { "redacted", "RedactedNews" },
            { "firstpost", "Firstpost" },
            { "valuetainment", "VALUETAINMENT" },
            { "amala ekpunobi", "AmalaEkpunobi" },
            { "turning point usa", "turningpointusa" },
            { "gbnews", "GBNewsOnline" },
            { "bestintesla", "BestInTesla" },
            { "dr. steve turley", "DrSteveTurley" },
            { "dr steve turley", "DrSteveTurley" },
            { "stephen gardner", "StephenGardner" },
            { "peter h. diamandis", "peterdiamandis" },
            { "peter diamandis", "peterdiamandis" },
            { "tina huang", "TinaHuang1" },
            { "zubair trabzada", "zubairtrabzada" },
            { "worldofai", "WorldofAI" },
            { "world of ai", "WorldofAI" },
            { "nerdy rodent", "NerdyRodent" },
            { "the robotics state", "TheRoboticsState" },
            { "warren smith - secret scholar", "SecretScholarSociety" },
            { "tousi tv", "TousiTV" },
            { "mark rober", "MarkRober" },
            { "mkbhd", "MKBHD" },
            { "marques brownlee", "MKBHD" },
            { "daily dose of internet", "DailyDoseOfInternet" }
        };

        private static readonly System.Collections.Concurrent.ConcurrentDictionary<string, (List<VideoItem> Videos, DateTime Timestamp)> _channelCache = new();

        public static string ResolveChannelHandle(string channelName)
        {
            if (string.IsNullOrWhiteSpace(channelName)) return "";
            var trimmed = channelName.Trim();
            if (trimmed.StartsWith("@")) return trimmed.Substring(1);
            if (VerifiedHandles.TryGetValue(trimmed, out var exactHandle))
            {
                return exactHandle;
            }
            // Strip common noise words like (Show, Podcast, Official, etc.)
            var cleaned = Regex.Replace(trimmed, @"(?i)\b(show|tv|channel|podcast|official|media|news|network)\b", "").Trim();
            if (VerifiedHandles.TryGetValue(cleaned, out var cleanedHandle))
            {
                return cleanedHandle;
            }
            // Fallback: strip punctuation and spaces to form @handle
            var alphanumeric = Regex.Replace(trimmed, @"[^\w]", "");
            return alphanumeric;
        }

        public static async Task<List<VideoItem>> GetChannelVideosFeedAsync(string channelNameOrId, bool forceRefresh = false)
        {
            if (string.IsNullOrWhiteSpace(channelNameOrId)) return new List<VideoItem>();

            var channelQuery = channelNameOrId.Trim();
            var cacheKey = $"channel:{channelQuery.ToLowerInvariant()}";

            if (!forceRefresh && _channelCache.TryGetValue(cacheKey, out var cached) && (DateTime.UtcNow - cached.Timestamp).TotalMinutes < 10)
            {
                return cached.Videos;
            }

            var results = new List<VideoItem>();
            var seenIds = new HashSet<string>();

            try
            {
                var targetUrls = new List<string>();
                if (channelQuery.StartsWith("UC") && channelQuery.Length == 24)
                {
                    targetUrls.Add($"https://www.youtube.com/channel/{channelQuery}/videos?hl=en&gl=US");
                    targetUrls.Add($"https://www.youtube.com/channel/{channelQuery}/streams?hl=en&gl=US");
                }
                else if (channelQuery.StartsWith("@"))
                {
                    targetUrls.Add($"https://www.youtube.com/{channelQuery}/videos?hl=en&gl=US");
                    targetUrls.Add($"https://www.youtube.com/{channelQuery}/streams?hl=en&gl=US");
                }
                else
                {
                    var handle = ResolveChannelHandle(channelQuery);
                    if (!string.IsNullOrWhiteSpace(handle))
                    {
                        targetUrls.Add($"https://www.youtube.com/@{handle}/videos?hl=en&gl=US");
                        targetUrls.Add($"https://www.youtube.com/@{handle}/streams?hl=en&gl=US");
                    }
                }

                if (targetUrls.Count > 0)
                {
                    var fetchTasks = targetUrls.Select(async url =>
                    {
                        try
                        {
                            using var cts = new System.Threading.CancellationTokenSource(TimeSpan.FromSeconds(5));
                            var req = new HttpRequestMessage(HttpMethod.Get, url);
                            req.Headers.Add("Cookie", "PREF=hl=en&gl=US; SOCS=CAI");
                            var resp = await _httpClient.SendAsync(req, cts.Token);
                            if (resp.IsSuccessStatusCode)
                            {
                                var html = await resp.Content.ReadAsStringAsync(cts.Token);
                                var vm = Regex.Match(html, @"(?:var\s+ytInitialData\s*=\s*|ytInitialData\s*=\s*)(\{.+?\});(?:</script>|\n)", RegexOptions.Singleline);
                                if (vm.Success)
                                {
                                    var vObj = JObject.Parse(vm.Groups[1].Value);
                                    string channelTitle = vObj?["metadata"]?["channelMetadataRenderer"]?["title"]?.ToString() ?? channelQuery;
                                    lock (results)
                                    {
                                        ExtractChannelVideosFromToken(vObj, results, seenIds, channelTitle);
                                    }
                                }
                            }
                        }
                        catch { }
                    });

                    await Task.WhenAll(fetchTasks);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Channel feed extraction error for '{channelNameOrId}': {ex.Message}");
            }

            // Fallback: If direct handle returned nothing, try date-sorted search
            if (results.Count == 0)
            {
                try
                {
                    results = await SearchVideosAsync(channelQuery, 30, sortByUploadDate: true);
                }
                catch { }
            }

            var filtered = results
                .Where(v => !StorageService.IsDisliked(v.Id) && !StorageService.IsDeleted(v.Id))
                .OrderBy(v => ParsePublishedTimeToSeconds(v.UploadDateText))
                .ToList();

            if (filtered.Count > 0)
            {
                _channelCache[cacheKey] = (filtered, DateTime.UtcNow);
            }
            return filtered;
        }

        public static void ExtractChannelVideosFromToken(JToken? token, List<VideoItem> results, HashSet<string> seenIds, string fallbackChannelTitle)
        {
            if (token == null || results.Count >= 75) return;

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
                        if (rows != null)
                        {
                            foreach (var row in rows)
                            {
                                var parts = row?["metadataParts"] as JArray;
                                if (parts == null) continue;
                                foreach (var part in parts)
                                {
                                    var txt = part?["text"]?["content"]?.ToString() ?? "";
                                    if (txt.Contains("view", StringComparison.OrdinalIgnoreCase))
                                    {
                                        views = txt;
                                    }
                                    else if (txt.Contains("ago", StringComparison.OrdinalIgnoreCase) || 
                                             txt.Contains("stream", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("Premier", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("today", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("yesterday", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("hour", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("minute", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("second", StringComparison.OrdinalIgnoreCase) ||
                                             txt.Contains("day", StringComparison.OrdinalIgnoreCase))
                                    {
                                        pub = txt;
                                    }
                                }
                            }
                        }

                        string dur = "Video";
                        var tov = lum["contentImage"]?["thumbnailViewModel"]?["overlays"] as JArray;
                        if (tov != null)
                        {
                            foreach (var ov in tov)
                            {
                                var tText = ov?["thumbnailOverlayTimeStatusViewModel"]?["text"]?["content"]?.ToString()
                                    ?? ov?["thumbnailOverlayTimeStatusViewModel"]?["text"]?["simpleText"]?.ToString();
                                if (!string.IsNullOrWhiteSpace(tText)) { dur = tText; break; }

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
                                ChannelTitle = System.Net.WebUtility.HtmlDecode(fallbackChannelTitle),
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
                                ChannelTitle = System.Net.WebUtility.HtmlDecode(fallbackChannelTitle),
                                ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
                                DurationText = dur,
                                UploadDateText = System.Net.WebUtility.HtmlDecode(pub),
                                ViewCountText = System.Net.WebUtility.HtmlDecode(views)
                            });
                        }
                    }
                }

                foreach (var prop in jo.Properties()) ExtractChannelVideosFromToken(prop.Value, results, seenIds, fallbackChannelTitle);
            }
            else if (token is JArray ja)
            {
                foreach (var it in ja) ExtractChannelVideosFromToken(it, results, seenIds, fallbackChannelTitle);
            }
        }

        public static async Task<List<VideoItem>> FetchSubscribedProfileFeedAsync(
            List<string>? subscribedChannels = null,
            int batchIndex = 0,
            int batchSize = 25,
            bool forceRefresh = false)
        {
            var channels = subscribedChannels ?? UserProfileData.SubscribedChannels;
            if (channels == null || channels.Count == 0)
            {
                return new List<VideoItem>();
            }

            var targetChannels = channels;
            if (channels.Count > batchSize)
            {
                var startIndex = (batchIndex * batchSize) % channels.Count;
                var count = Math.Min(batchSize, channels.Count);
                targetChannels = Enumerable.Range(0, count)
                    .Select(i => channels[(startIndex + i) % channels.Count])
                    .ToList();
            }

            var results = new System.Collections.Concurrent.ConcurrentBag<VideoItem>();
            using var semaphore = new System.Threading.SemaphoreSlim(10);

            var tasks = targetChannels.Select(async ch =>
            {
                await semaphore.WaitAsync();
                try
                {
                    var videos = await GetChannelVideosFeedAsync(ch, forceRefresh: forceRefresh);
                    foreach (var v in videos.Take(25))
                    {
                        results.Add(v);
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Error fetching subscribed channel '{ch}': {ex.Message}");
                }
                finally
                {
                    semaphore.Release();
                }
            }).ToList();

            await Task.WhenAll(tasks);

            return results
                .GroupBy(v => v.Id)
                .Select(g => g.First())
                .ToList();
        }

        public static async Task<List<VideoItem>> GetSubscribedFeedAsync(string? channelName = null, int batchIndex = 0)
        {
            var channels = UserProfileData.SubscribedChannels;
            if (!string.IsNullOrWhiteSpace(channelName))
            {
                return await GetChannelVideosFeedAsync(channelName);
            }

            var uniqueVideos = await FetchSubscribedProfileFeedAsync(channels, batchIndex: batchIndex, batchSize: 25);

            return RecommendationEngine.ScoreAndRankVideos(
                uniqueVideos,
                StorageService.Settings.Favorites,
                StorageService.Settings.WatchHistory,
                channels
            );
        }

        public static async Task<List<VideoItem>> GetHomeFeedAsync(int batchIndex = 0)
        {
            var channels = UserProfileData.SubscribedChannels;
            var subVideosTask = FetchSubscribedProfileFeedAsync(channels, batchIndex: batchIndex, batchSize: 25);

            // Also query discovery topic in parallel
            var topics = new[] { "Tech AI News 2026", "Breakthrough Technology", "World News Today" };
            var rand = new Random();
            var topicTask = SearchVideosAsync(topics[rand.Next(topics.Length)], 15, sortByUploadDate: true);

            await Task.WhenAll(subVideosTask, topicTask);

            var combined = new List<VideoItem>();
            combined.AddRange(await subVideosTask);
            combined.AddRange(await topicTask);

            var uniqueVideos = combined.GroupBy(v => v.Id).Select(g => g.First()).ToList();

            // Run exact recommendation engine scoring & ranking with dominant recency bonus
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
                    UploadDateText = video.UploadDate.ToString("MMM dd, yyyy"),
                    ViewCountText = FormatViews(video.Engagement.ViewCount)
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
            var list = videos.Where(v => !StorageService.IsDisliked(v.Id) && !StorageService.IsDeleted(v.Id)).ToList();

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
                    list = list.Where(v => ParsePublishedTimeToSeconds(v.UploadDateText) <= 3600).ToList();
                }
                else if (dateFilter == "today")
                {
                    // Strict Today: within last 24 hours
                    list = list.Where(v => ParsePublishedTimeToSeconds(v.UploadDateText) <= 86400).ToList();
                }
                else if (dateFilter == "week")
                {
                    list = list.Where(v => ParsePublishedTimeToSeconds(v.UploadDateText) <= 604800).ToList();
                }
                else if (dateFilter == "month")
                {
                    list = list.Where(v => ParsePublishedTimeToSeconds(v.UploadDateText) <= 2592000).ToList();
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
            var seenIds = new HashSet<string>();

            string? spParam = null;
            if (sortBy == "latest") spParam = "CAISAhAB";
            else if (sortBy == "views") spParam = "CAM%3D";
            else if (dateFilter == "today") spParam = "EgIIAg%3D%3D";
            else if (dateFilter == "week") spParam = "EgIIAw%3D%3D";
            else if (dateFilter == "month") spParam = "EgIIBA%3D%3D";

            var subVideosTask = FetchSubscribedProfileFeedAsync(UserProfileData.SubscribedChannels, batchIndex: 0, batchSize: 30);

            var searchQueries = new[] { "breaking news", "trending today", "latest podcast", "technology news" };
            var searchTasks = searchQueries.Select(q => SearchVideosAsync(q, 20, spFilter: spParam)).ToList();

            var allTasks = new List<Task<List<VideoItem>>>(searchTasks) { subVideosTask };

            var aggregated = new List<VideoItem>();
            try
            {
                var batches = await Task.WhenAll(allTasks);
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
            if (string.IsNullOrWhiteSpace(text)) return 100_000_000;
            var lower = text.ToLowerInvariant().Trim();

            if (lower.Contains("moment") || lower.Contains("just now")) return 30;
            if (lower.Contains("today")) return 1800;
            if (lower.Contains("yesterday")) return 86400;

            // Matches: "5h ago", "12d ago", "2mo ago", "22m ago", "35s ago", "2 hours ago", "1 day ago", "3 weeks ago", "1 year ago", etc.
            var match = Regex.Match(lower, @"(\d+)\s*(s|sec|seconds?|m|min|minutes?|h|hr|hours?|d|days?|w|weeks?|mo|months?|y|years?)\b");
            if (match.Success && int.TryParse(match.Groups[1].Value, out var val))
            {
                var unit = match.Groups[2].Value;
                if (unit.StartsWith("s")) return Math.Max(val, 15);
                if (unit.StartsWith("m") && !unit.StartsWith("mo")) return val * 60;
                if (unit.StartsWith("h")) return val * 3600;
                if (unit.StartsWith("d")) return val * 86400;
                if (unit.StartsWith("w")) return val * 604800;
                if (unit.StartsWith("mo")) return val * 2592000;
                if (unit.StartsWith("y")) return val * 31536000;
            }

            if (DateTime.TryParse(text, out var dt))
            {
                var diff = (DateTime.UtcNow - dt.ToUniversalTime()).TotalSeconds;
                return diff > 0 ? (long)diff : 86400;
            }

            return 100_000_000;
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
