using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public static class VpsSyncService
    {
        private static readonly HttpClient _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(12)
        };

        private static Timer? _syncTimer;
        private static bool _isSyncing = false;

        public static event Action? OnSyncCompleted;

        public static void InitializeAutoSync()
        {
            _syncTimer?.Dispose();
            // Run sync every 5 minutes (initial run after 10 seconds)
            _syncTimer = new Timer(async _ =>
            {
                if (StorageService.Settings.IsVpsSyncEnabled && !string.IsNullOrWhiteSpace(StorageService.Settings.VpsServerUrl))
                {
                    await SyncWithServerAsync();
                }
            }, null, TimeSpan.FromSeconds(10), TimeSpan.FromMinutes(5));
        }

        private static string CleanUrl(string? url)
        {
            return (url ?? "").Trim().TrimEnd('/');
        }

        private static void ApplyAuthHeaders(HttpRequestMessage request, string? apiKey)
        {
            var key = !string.IsNullOrWhiteSpace(apiKey) ? apiKey.Trim() : StorageService.Settings.VpsApiKey?.Trim();
            if (!string.IsNullOrWhiteSpace(key))
            {
                request.Headers.TryAddWithoutValidation("X-API-Key", key);
                request.Headers.TryAddWithoutValidation("Authorization", $"Bearer {key}");
            }
        }

        public static async Task<(bool success, string message)> TestConnectionAsync(string serverUrl, string? apiKey)
        {
            var baseUrl = CleanUrl(serverUrl);
            if (string.IsNullOrWhiteSpace(baseUrl))
            {
                return (false, "Please enter a valid server URL.");
            }

            try
            {
                var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/health");
                ApplyAuthHeaders(request, apiKey);

                var response = await _httpClient.SendAsync(request);
                if (response.IsSuccessStatusCode)
                {
                    return (true, "Connected successfully to VPS Sync Server! (Health: OK)");
                }
                else
                {
                    return (false, $"Server returned HTTP {(int)response.StatusCode} {response.ReasonPhrase}");
                }
            }
            catch (Exception ex)
            {
                return (false, $"Connection failed: {ex.Message}");
            }
        }

        public static async Task NotifyWatchedAsync(VideoItem video, double position = 0)
        {
            if (!StorageService.Settings.IsVpsSyncEnabled) return;
            var baseUrl = CleanUrl(StorageService.Settings.VpsServerUrl);
            if (string.IsNullOrWhiteSpace(baseUrl) || string.IsNullOrWhiteSpace(video.Id)) return;

            try
            {
                var payload = new
                {
                    video_id = video.Id,
                    title = video.Title ?? "",
                    channel = video.ChannelTitle ?? "",
                    duration = video.DurationText ?? "",
                    thumbnail = video.ThumbnailUrl ?? "",
                    position = position > 0 ? position : StorageService.GetPlaybackPosition(video.Id),
                    watched_at = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                };

                var json = JsonConvert.SerializeObject(payload);
                var request = new HttpRequestMessage(HttpMethod.Post, $"{baseUrl}/api/v1/sync/watched")
                {
                    Content = new StringContent(json, Encoding.UTF8, "application/json")
                };
                ApplyAuthHeaders(request, null);

                await _httpClient.SendAsync(request);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[VpsSync] Failed to notify watched video: {ex.Message}");
            }
        }

        public static async Task<(bool success, string message)> SyncWithServerAsync()
        {
            if (_isSyncing) return (false, "Sync already in progress");

            var baseUrl = CleanUrl(StorageService.Settings.VpsServerUrl);
            if (string.IsNullOrWhiteSpace(baseUrl))
            {
                return (false, "VPS Server URL is not configured.");
            }

            _isSyncing = true;
            try
            {
                // 1. Prepare local state
                var localWatched = StorageService.Settings.WatchHistory.Select(v => new
                {
                    video_id = v.Id,
                    title = v.Title ?? "",
                    channel = v.ChannelTitle ?? "",
                    duration = v.DurationText ?? "",
                    thumbnail = v.ThumbnailUrl ?? "",
                    position = StorageService.GetPlaybackPosition(v.Id),
                    watched_at = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                }).ToList();

                var payload = new
                {
                    client_id = "vixz-windows",
                    watched = localWatched,
                    favorites = StorageService.Settings.Favorites.Select(f => f.Id).ToList(),
                    disliked_videos = StorageService.Settings.DislikedVideoIds,
                    disliked_channels = StorageService.Settings.DislikedChannels,
                    subscribed_channels = StorageService.Settings.SubscribedChannels
                };

                var json = JsonConvert.SerializeObject(payload);
                var request = new HttpRequestMessage(HttpMethod.Post, $"{baseUrl}/api/v1/sync/full")
                {
                    Content = new StringContent(json, Encoding.UTF8, "application/json")
                };
                ApplyAuthHeaders(request, null);

                var response = await _httpClient.SendAsync(request);
                if (!response.IsSuccessStatusCode)
                {
                    return (false, $"Server returned HTTP {(int)response.StatusCode} {response.ReasonPhrase}");
                }

                var respContent = await response.Content.ReadAsStringAsync();
                var data = JObject.Parse(respContent);

                // 2. Merge Watched Videos
                var serverWatched = data["watched"] as JArray;
                if (serverWatched != null)
                {
                    var existingHistoryIds = new HashSet<string>(
                        StorageService.Settings.WatchHistory.Select(v => v.Id),
                        StringComparer.OrdinalIgnoreCase
                    );

                    foreach (var item in serverWatched)
                    {
                        var vid = item["video_id"]?.ToString();
                        if (string.IsNullOrWhiteSpace(vid)) continue;

                        var pos = item["position"]?.Value<double>() ?? 0;
                        if (pos > 0)
                        {
                            StorageService.Settings.WatchPositions[vid] = pos;
                        }

                        if (!existingHistoryIds.Contains(vid))
                        {
                            var title = item["title"]?.ToString() ?? $"Video {vid}";
                            var channel = item["channel"]?.ToString() ?? "YouTube";
                            var thumb = item["thumbnail"]?.ToString() ?? $"https://img.youtube.com/vi/{vid}/hqdefault.jpg";
                            var dur = item["duration"]?.ToString() ?? "10:00";

                            var newVid = new VideoItem
                            {
                                Id = vid,
                                Title = title,
                                ChannelTitle = channel,
                                ThumbnailUrl = thumb,
                                DurationText = dur
                            };

                            StorageService.Settings.WatchHistory.Add(newVid);
                            existingHistoryIds.Add(vid);
                        }
                    }

                    if (StorageService.Settings.WatchHistory.Count > 250)
                    {
                        StorageService.Settings.WatchHistory = StorageService.Settings.WatchHistory.Take(250).ToList();
                    }
                }

                // 3. Merge Disliked Videos
                var serverDislikes = data["disliked_videos"] as JArray;
                if (serverDislikes != null)
                {
                    foreach (var d in serverDislikes)
                    {
                        var id = d?.ToString();
                        if (!string.IsNullOrWhiteSpace(id) && !StorageService.Settings.DislikedVideoIds.Contains(id, StringComparer.OrdinalIgnoreCase))
                        {
                            StorageService.Settings.DislikedVideoIds.Add(id);
                        }
                    }
                }

                // 4. Merge Disliked Channels
                var serverDislikedChannels = data["disliked_channels"] as JArray;
                if (serverDislikedChannels != null)
                {
                    foreach (var ch in serverDislikedChannels)
                    {
                        var name = ch?.ToString();
                        if (!string.IsNullOrWhiteSpace(name) && !StorageService.Settings.DislikedChannels.Contains(name, StringComparer.OrdinalIgnoreCase))
                        {
                            StorageService.Settings.DislikedChannels.Add(name);
                        }
                    }
                }

                // 5. Merge Subscribed Channels
                var serverSubs = data["subscribed_channels"] as JArray ?? data["subscribedChannels"] as JArray;
                if (serverSubs != null)
                {
                    foreach (var s in serverSubs)
                    {
                        var ch = s?.ToString()?.Trim();
                        if (!string.IsNullOrWhiteSpace(ch) && !WillRyanProfileData.IsSubscribed(ch))
                        {
                            WillRyanProfileData.AddSubscribedChannel(ch);
                        }
                    }
                }

                // Save locally
                StorageService.Settings.LastVpsSyncTime = DateTime.UtcNow;
                StorageService.Save();

                // Notify UI
                System.Windows.Application.Current?.Dispatcher.Invoke(() =>
                {
                    OnSyncCompleted?.Invoke();
                });

                var totalWatched = StorageService.Settings.WatchHistory.Count;
                return (true, $"Synced successfully! ({totalWatched} watched videos synchronized with VPS)");
            }
            catch (Exception ex)
            {
                return (false, $"Sync error: {ex.Message}");
            }
            finally
            {
                _isSyncing = false;
            }
        }
    }
}
