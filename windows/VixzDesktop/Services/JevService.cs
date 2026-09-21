using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    /// <summary>
    /// TypeSafe AI "Jev" System-One decision engine client.
    /// Provides ultra-fast (70-300ms) non-generative decisions for:
    /// 1. Autonomous authentication & account chooser resolution
    /// 2. Optimal video stream / quality selection based on device state
    /// 3. Autonomous video recommendation ranking & Next-Up curation
    /// </summary>
    public static class JevService
    {
        private static readonly HttpClient _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(5)
        };

        private const string JevEndpoint = "https://api.typesafe.ai/v1/systemone";

        public static bool IsConfiguredAndEnabled =>
            StorageService.Settings.IsJevEnabled &&
            !string.IsNullOrWhiteSpace(StorageService.Settings.JevApiKey);

        public static string? CurrentApiKey => StorageService.Settings.JevApiKey;

        /// <summary>
        /// Validates a TypeSafe Jev API key by sending a fast test decision question.
        /// </summary>
        public static async Task<(bool Success, string Message)> ValidateApiKeyAsync(string apiKey)
        {
            if (string.IsNullOrWhiteSpace(apiKey))
                return (false, "⚠️ Please provide an API key.");

            try
            {
                var payload = new
                {
                    model = "jev-latest",
                    state = "System health check for Vixz Desktop companion.",
                    questions = new Dictionary<string, object>
                    {
                        ["ping"] = new
                        {
                            type = "choice",
                            instructions = "Confirm the status of this system check.",
                            criteria = new Dictionary<string, string>
                            {
                                ["online"] = "System is operational and ready to process decisions",
                                ["offline"] = "System is unavailable"
                            }
                        }
                    }
                };

                using var request = new HttpRequestMessage(HttpMethod.Post, JevEndpoint);
                request.Headers.Add("Authorization", $"Bearer {apiKey.Trim()}");
                request.Content = new StringContent(JsonConvert.SerializeObject(payload), Encoding.UTF8, "application/json");

                var response = await _httpClient.SendAsync(request);
                var content = await response.Content.ReadAsStringAsync();

                if (response.IsSuccessStatusCode)
                {
                    return (true, "⚡ Connected to TypeSafe Jev System-One! Decisions are active.");
                }

                return (false, $"❌ TypeSafe AI error ({response.StatusCode}): {content}");
            }
            catch (Exception ex)
            {
                return (false, $"❌ Connection error: {ex.Message}");
            }
        }

        /// <summary>
        /// Task 1: Autonomous Auth & Account Chooser.
        /// Evaluates visible interactive choices on Google/YouTube authentication pages
        /// and returns the key of the best action to take.
        /// </summary>
        public static async Task<string?> DecideAuthActionAsync(string pageUrl, string userEmail, Dictionary<string, string> visibleElements)
        {
            if (!IsConfiguredAndEnabled || visibleElements == null || visibleElements.Count == 0)
                return null;

            try
            {
                var criteria = new Dictionary<string, string>();
                foreach (var kvp in visibleElements)
                {
                    criteria[kvp.Key] = kvp.Value;
                }

                var payload = new
                {
                    model = "jev-latest",
                    state = $"The user is attempting to sign in to YouTube as '{userEmail}'. Current page URL: '{pageUrl}'.",
                    questions = new Dictionary<string, object>
                    {
                        ["auth_decision"] = new
                        {
                            type = "choice",
                            instructions = $"Select the element that proceeds with signing in as {userEmail}, accepts necessary terms, or dismisses cookie consent walls.",
                            criteria = criteria
                        }
                    }
                };

                var answer = await ExecuteJevChoiceAsync(payload, "auth_decision");
                Debug.WriteLine($"[Jev] Auth decision for {pageUrl}: '{answer}'");
                return answer;
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[Jev] DecideAuthAction error: {ex.Message}");
                return null;
            }
        }

        /// <summary>
        /// Task 2: Smart Stream / Quality Arbitrator.
        /// Evaluates current window dimensions, preferred quality, and available playback qualities.
        /// </summary>
        public static async Task<string?> DecideOptimalQualityAsync(int windowWidth, int windowHeight, string preferredQuality, List<string> availableQualities)
        {
            if (!IsConfiguredAndEnabled || availableQualities == null || availableQualities.Count == 0)
                return null;

            try
            {
                var criteria = new Dictionary<string, string>();
                foreach (var q in availableQualities)
                {
                    criteria[q] = $"Video resolution option: {q}";
                }

                var payload = new
                {
                    model = "jev-latest",
                    state = $"Player viewport is {windowWidth}x{windowHeight}. User preference is '{preferredQuality}'.",
                    questions = new Dictionary<string, object>
                    {
                        ["quality_choice"] = new
                        {
                            type = "choice",
                            instructions = "Pick the optimal video playback quality that balances fidelity with the current viewport without wasteful bandwidth.",
                            criteria = criteria
                        }
                    }
                };

                var answer = await ExecuteJevChoiceAsync(payload, "quality_choice");
                Debug.WriteLine($"[Jev] Quality decision: '{answer}'");
                return answer;
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[Jev] DecideOptimalQuality error: {ex.Message}");
                return null;
            }
        }

        /// <summary>
        /// Task 3: Autonomous Content Curator & Recommendation DJ.
        /// Evaluates candidates and chooses the best next video based on user context.
        /// </summary>
        public static async Task<VideoItem?> CurateNextVideoAsync(string currentTitle, string currentChannel, List<VideoItem> candidates)
        {
            if (!IsConfiguredAndEnabled || candidates == null || candidates.Count == 0)
                return null;

            try
            {
                var criteria = new Dictionary<string, string>();
                var candidateMap = new Dictionary<string, VideoItem>();

                for (int i = 0; i < Math.Min(candidates.Count, 12); i++)
                {
                    var v = candidates[i];
                    var key = $"video_{i}";
                    criteria[key] = $"Title: '{v.Title}', Channel: '{v.ChannelTitle}'";
                    candidateMap[key] = v;
                }

                var payload = new
                {
                    model = "jev-latest",
                    state = $"The user is currently enjoying the video '{currentTitle}' by creator '{currentChannel}'.",
                    questions = new Dictionary<string, object>
                    {
                        ["next_video"] = new
                        {
                            type = "choice",
                            instructions = "Select the single best candidate video to play next that matches the theme, creator vibe, or audience interest without repeating content.",
                            criteria = criteria
                        }
                    }
                };

                var chosenKey = await ExecuteJevChoiceAsync(payload, "next_video");
                if (!string.IsNullOrEmpty(chosenKey) && candidateMap.TryGetValue(chosenKey, out var chosenVideo))
                {
                    Debug.WriteLine($"[Jev] CurateNextVideo picked: '{chosenVideo.Title}' ({chosenVideo.Id})");
                    return chosenVideo;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[Jev] CurateNextVideo error: {ex.Message}");
            }

            return null;
        }

        private static async Task<string?> ExecuteJevChoiceAsync(object payload, string questionKey)
        {
            var apiKey = CurrentApiKey;
            if (string.IsNullOrWhiteSpace(apiKey)) return null;

            using var request = new HttpRequestMessage(HttpMethod.Post, JevEndpoint);
            request.Headers.Add("Authorization", $"Bearer {apiKey.Trim()}");
            request.Content = new StringContent(JsonConvert.SerializeObject(payload), Encoding.UTF8, "application/json");

            var response = await _httpClient.SendAsync(request);
            if (!response.IsSuccessStatusCode) return null;

            var jsonStr = await response.Content.ReadAsStringAsync();
            var jobj = JObject.Parse(jsonStr);

            var answerToken = jobj["answers"]?[questionKey];
            if (answerToken != null)
            {
                return answerToken["choice"]?.ToString();
            }

            return null;
        }
    }
}
