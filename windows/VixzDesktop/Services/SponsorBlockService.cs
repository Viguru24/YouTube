using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public class SponsorBlockService
    {
        private static readonly HttpClient _httpClient = new HttpClient();

        public static async Task<List<SponsorSegment>> GetSegmentsAsync(string videoId)
        {
            var segments = new List<SponsorSegment>();
            if (string.IsNullOrWhiteSpace(videoId)) return segments;

            try
            {
                var url = $"https://sponsor.ajay.app/api/skipSegments?videoID={videoId}&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\"]";
                using var request = new HttpRequestMessage(HttpMethod.Get, url);
                request.Headers.UserAgent.ParseAdd("VixzDesktop/1.0 (Windows NT 10.0; Win64; x64)");

                var response = await _httpClient.SendAsync(request);
                if (!response.IsSuccessStatusCode) return segments;

                var json = await response.Content.ReadAsStringAsync();
                var array = JArray.Parse(json);

                foreach (var item in array)
                {
                    var segmentArray = item["segment"] as JArray;
                    if (segmentArray != null && segmentArray.Count >= 2)
                    {
                        var start = segmentArray[0].Value<double>();
                        var end = segmentArray[1].Value<double>();
                        var category = item["category"]?.ToString() ?? "sponsor";
                        var actionType = item["actionType"]?.ToString() ?? "skip";

                        segments.Add(new SponsorSegment
                        {
                            UUID = item["UUID"]?.ToString() ?? Guid.NewGuid().ToString(),
                            StartTime = start,
                            EndTime = end,
                            Category = category,
                            ActionType = actionType
                        });
                    }
                }
            }
            catch
            {
                // Silently handle offline / no sponsor segments
            }

            return segments;
        }
    }
}
