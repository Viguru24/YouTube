using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using YoutubeExplode;
using YoutubeExplode.Videos.ClosedCaptions;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public enum AiCommandType
    {
        PlayVideo,
        Summarize,
        ControlSeek,
        ControlPause,
        ControlPlay,
        SetSleepTimer,
        SearchFeed,
        ChatAnswer,
        VideoQna,
        GeneralAnswer
    }

    public class AiCommandResult
    {
        public AiCommandType Type { get; set; }
        public string ResponseMessage { get; set; } = "";
        public VideoItem? TargetVideo { get; set; }
        public double? SeekSeconds { get; set; }
        public int? TimerMinutes { get; set; }
        public string SearchQuery { get; set; } = "";
        public string? SpFilter { get; set; }
        public VideoSummaryResult? Summary { get; set; }
        public List<string> WebFacts { get; set; } = new List<string>();
        public List<TimestampChapter> TimestampJumps { get; set; } = new List<TimestampChapter>();
        public string? SourceCitation { get; set; }
    }

    public class VideoSummaryResult
    {
        public string VideoId { get; set; } = "";
        public string VideoTitle { get; set; } = "";
        public string ChannelTitle { get; set; } = "";
        public string Tldr { get; set; } = "";
        public List<string> KeyTakeaways { get; set; } = new List<string>();
        public List<TimestampChapter> Chapters { get; set; } = new List<TimestampChapter>();
        public bool HasTranscript { get; set; }
    }

    public class TimestampChapter
    {
        public double Seconds { get; set; }
        public string TimeFormatted { get; set; } = "";
        public string Title { get; set; } = "";
    }

    public class AiCopilotService
    {
        private static readonly YoutubeClient _client = new YoutubeClient();
        private static readonly HttpClient _httpClient = CreateHttpClient();

        private static HttpClient CreateHttpClient()
        {
            var handler = new HttpClientHandler
            {
                AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate | DecompressionMethods.Brotli
            };
            var client = new HttpClient(handler);
            client.DefaultRequestHeaders.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
            client.DefaultRequestHeaders.Add("Accept-Language", "en-US,en;q=0.9");
            client.Timeout = TimeSpan.FromSeconds(8);
            return client;
        }

        public static async Task<AiCommandResult> ProcessCommandAsync(
            string prompt, 
            VideoItem? currentPlayingVideo, 
            Func<string, Task<string>>? webViewTranscriptFetcher = null)
        {
            if (string.IsNullOrWhiteSpace(prompt))
            {
                return new AiCommandResult
                {
                    Type = AiCommandType.GeneralAnswer,
                    ResponseMessage = "Please type or speak anything! Ask me about the current video, search the web, ask general knowledge, or control playback."
                };
            }

            var cleanPrompt = prompt.Trim();
            var lower = cleanPrompt.ToLowerInvariant();

            // 1. Playback Controls (Pause / Resume / Seek / Sleep Timer)
            if (lower == "pause" || lower == "stop" || lower == "stop video" || lower == "pause video" || lower == "freeze")
            {
                return new AiCommandResult { Type = AiCommandType.ControlPause, ResponseMessage = "⏸️ Playback paused." };
            }

            if (lower == "play" || lower == "resume" || lower == "unpause" || lower == "continue")
            {
                return new AiCommandResult { Type = AiCommandType.ControlPlay, ResponseMessage = "▶️ Playback resumed." };
            }

            var timerMatch = Regex.Match(lower, @"(?:sleep\s*timer|stop|turn\s*off|sleep)\s*(?:in|for)?\s*(\d+)\s*(?:min|minutes|m)?", RegexOptions.IgnoreCase);
            if (timerMatch.Success && int.TryParse(timerMatch.Groups[1].Value, out int minutes))
            {
                return new AiCommandResult
                {
                    Type = AiCommandType.SetSleepTimer,
                    TimerMinutes = minutes,
                    ResponseMessage = $"🌙 Sleep timer armed for **{minutes} minutes**."
                };
            }

            var seekForwardMatch = Regex.Match(lower, @"(?:skip|forward|jump)\s*(?:ahead)?\s*(\d+)\s*(?:s|sec|seconds)?", RegexOptions.IgnoreCase);
            if (seekForwardMatch.Success && double.TryParse(seekForwardMatch.Groups[1].Value, out double fwdSec))
            {
                return new AiCommandResult
                {
                    Type = AiCommandType.ControlSeek,
                    SeekSeconds = fwdSec,
                    ResponseMessage = $"⏩ Skipped forward **+{fwdSec}s**."
                };
            }

            var seekBackMatch = Regex.Match(lower, @"(?:rewind|back|go back)\s*(\d+)\s*(?:s|sec|seconds)?", RegexOptions.IgnoreCase);
            if (seekBackMatch.Success && double.TryParse(seekBackMatch.Groups[1].Value, out double backSec))
            {
                return new AiCommandResult
                {
                    Type = AiCommandType.ControlSeek,
                    SeekSeconds = -backSec,
                    ResponseMessage = $"⏪ Jumped back **-{backSec}s**."
                };
            }

            // 2. Video Summarization Command
            if (lower.Contains("summar") || lower.Contains("tl;dr") || lower.Contains("explain this video") || lower.Contains("tldr"))
            {
                if (currentPlayingVideo == null)
                {
                    return new AiCommandResult
                    {
                        Type = AiCommandType.GeneralAnswer,
                        ResponseMessage = "⚠️ No video is currently playing to summarize. Play a video first, then ask me to summarize it!"
                    };
                }

                var sum = await GenerateSummaryAsync(currentPlayingVideo, webViewTranscriptFetcher);
                return new AiCommandResult
                {
                    Type = AiCommandType.Summarize,
                    TargetVideo = currentPlayingVideo,
                    Summary = sum,
                    ResponseMessage = $"✨ Summarizing **{currentPlayingVideo.Title}**..."
                };
            }

            // 3. Video Q&A ("Chat with Video")
            bool isVideoQuestion = currentPlayingVideo != null && (
                lower.Contains("in this video") || lower.Contains("this video") || 
                lower.Contains("he say") || lower.Contains("she say") || lower.Contains("they say") || 
                lower.Contains("did he") || lower.Contains("did she") || lower.Contains("speaker") ||
                lower.Contains("mention") || lower.Contains("timestamp") || lower.Contains("where does") ||
                lower.StartsWith("what does") || lower.StartsWith("why does") || lower.StartsWith("how does")
            );

            if (isVideoQuestion && currentPlayingVideo != null)
            {
                var videoQna = await AnswerVideoQuestionAsync(cleanPrompt, currentPlayingVideo, webViewTranscriptFetcher);
                if (videoQna != null)
                {
                    return videoQna;
                }
            }

            // 4. Play Specific Video Query (e.g. "play latest Benny Johnson", "watch Tucker Carlson")
            var playMatch = Regex.Match(lower, @"^(?:play|watch|open|start)\s*(?:the)?\s*(?:latest|newest|today's)?\s*(.+)", RegexOptions.IgnoreCase);
            if (playMatch.Success && !lower.Contains("?"))
            {
                var target = playMatch.Groups[1].Value.Trim();
                if (target.Length >= 2 && target != "this" && target != "video")
                {
                    var results = await YouTubeService.SearchVideosAsync(target, 15, sortByUploadDate: true);
                    if (results.Count > 0)
                    {
                        var sortedResults = YouTubeService.ApplyLocalFilters(results, null, null, "latest");
                        var topVideo = sortedResults.FirstOrDefault() ?? results[0];

                        return new AiCommandResult
                        {
                            Type = AiCommandType.PlayVideo,
                            TargetVideo = topVideo,
                            ResponseMessage = $"▶️ Playing the latest video from **{topVideo.ChannelTitle}**:\n*{topVideo.Title}* ({topVideo.UploadDateText})"
                        };
                    }
                }
            }

            // 5. Search Feed / Video Lookup Command
            var explicitSearchMatch = Regex.Match(lower, @"^(?:search|find|show me|look for)\s+(.+)", RegexOptions.IgnoreCase);
            if (explicitSearchMatch.Success && !lower.Contains("who") && !lower.Contains("what") && !lower.Contains("why") && !lower.Contains("is there"))
            {
                var q = explicitSearchMatch.Groups[1].Value.Trim();
                string? sp = null;
                if (lower.Contains("today")) sp = "EgIIAg%3D%3D";
                else if (lower.Contains("this week")) sp = "EgIIAw%3D%3D";
                else if (lower.Contains("short")) sp = "EgQQARgB";
                else if (lower.Contains("long")) sp = "EgQQARgC";

                return new AiCommandResult
                {
                    Type = AiCommandType.SearchFeed,
                    SearchQuery = q,
                    SpFilter = sp,
                    ResponseMessage = $"🔍 Searching for **\"{q}\"**..."
                };
            }

            // 6. Real Conversational LLM Brain (Groq / Gemini / OpenAI)
            var apiKey = StorageService.Settings.GeminiApiKey;
            if (!string.IsNullOrWhiteSpace(apiKey))
            {
                var llmResult = await QueryLlmBrainAsync(cleanPrompt, currentPlayingVideo, apiKey);
                if (llmResult != null)
                {
                    return llmResult;
                }
            }

            // 7. Live Web Knowledge Engine (Zero-Config Built-In Chatbot)
            var webResult = await QueryLiveWebKnowledgeAsync(cleanPrompt);
            if (webResult != null)
            {
                return webResult;
            }

            // 8. Conversational Fallback
            return new AiCommandResult
            {
                Type = AiCommandType.ChatAnswer,
                ResponseMessage = $"🤖 I'm your Vixz AI Assistant! You can ask me questions about current events, search for topics, ask about the current video, or use commands like *\"Summarise this video\"* and *\"Play latest Benny Johnson\"*."
            };
        }

        public static async Task<AiCommandResult?> QueryLiveWebKnowledgeAsync(string query)
        {
            try
            {
                var encodedQuery = WebUtility.UrlEncode(query);
                var searchUrl = $"https://html.duckduckgo.com/html/?q={encodedQuery}";

                var html = await _httpClient.GetStringAsync(searchUrl);
                var snippetMatches = Regex.Matches(html, @"<a class=""result__snippet[^>]*>(.*?)</a>", RegexOptions.Singleline);
                var titleMatches = Regex.Matches(html, @"<a class=""result__url[^>]*>(.*?)</a>", RegexOptions.Singleline);

                var cleanSnippets = new List<string>();
                for (int i = 0; i < snippetMatches.Count && cleanSnippets.Count < 5; i++)
                {
                    var raw = snippetMatches[i].Groups[1].Value;
                    var clean = Regex.Replace(raw, @"<[^>]+>", " ");
                    clean = WebUtility.HtmlDecode(clean).Trim();
                    clean = Regex.Replace(clean, @"\s+", " ");
                    if (clean.Length > 25 && !cleanSnippets.Contains(clean))
                    {
                        cleanSnippets.Add(clean);
                    }
                }

                if (cleanSnippets.Count == 0) return null;

                // Synthesize the primary direct answer
                var primaryAnswer = cleanSnippets[0];

                // Gather key points from subsequent snippets
                var facts = new List<string>();
                for (int i = 1; i < cleanSnippets.Count; i++)
                {
                    var s = cleanSnippets[i];
                    if (s.Length > 180) s = s.Substring(0, 177) + "...";
                    facts.Add(s);
                }

                return new AiCommandResult
                {
                    Type = AiCommandType.ChatAnswer,
                    ResponseMessage = primaryAnswer,
                    WebFacts = facts,
                    SourceCitation = "Live Web Intelligence"
                };
            }
            catch
            {
                return null;
            }
        }

        public static async Task<AiCommandResult?> AnswerVideoQuestionAsync(
            string question, 
            VideoItem video, 
            Func<string, Task<string>>? webViewTranscriptFetcher)
        {
            try
            {
                string rawTranscript = "";
                if (webViewTranscriptFetcher != null)
                {
                    try { rawTranscript = await webViewTranscriptFetcher(video.Id); } catch { }
                }

                if (string.IsNullOrWhiteSpace(rawTranscript))
                {
                    return null;
                }

                // Extract keywords from question
                var stopWords = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "what", "where", "when", "why", "how", "who", "does", "did", "say", "about", "the", "in", "this", "video", "mention", "is", "a", "an", "and", "or", "of", "to" };
                var keywords = question.Split(new[] { ' ', '?', '!', ',', '.' }, StringSplitOptions.RemoveEmptyEntries)
                                       .Where(w => w.Length > 2 && !stopWords.Contains(w))
                                       .ToList();

                if (keywords.Count == 0) return null;

                // Scan transcript sentences
                var sentences = rawTranscript.Split(new[] { '.', '!', '?' }, StringSplitOptions.RemoveEmptyEntries)
                                             .Select(s => s.Trim())
                                             .Where(s => s.Length > 20)
                                             .ToList();

                var matchingSentences = new List<(string Sentence, int Score)>();
                foreach (var s in sentences)
                {
                    int score = 0;
                    foreach (var kw in keywords)
                    {
                        if (s.IndexOf(kw, StringComparison.OrdinalIgnoreCase) >= 0) score++;
                    }
                    if (score > 0)
                    {
                        matchingSentences.Add((s, score));
                    }
                }

                if (matchingSentences.Count == 0) return null;

                var topMatches = matchingSentences.OrderByDescending(m => m.Score).Take(3).Select(m => m.Sentence).ToList();
                var answerText = $"Regarding your question about **{string.Join(", ", keywords)}** in *{video.Title}*:\n\n" + string.Join(". ", topMatches) + ".";

                return new AiCommandResult
                {
                    Type = AiCommandType.VideoQna,
                    ResponseMessage = answerText,
                    TargetVideo = video
                };
            }
            catch
            {
                return null;
            }
        }

        private static readonly List<(string Role, string Content)> _conversationHistory = new List<(string Role, string Content)>();

        public static async Task<AiCommandResult?> QueryLlmBrainAsync(string prompt, VideoItem? currentVideo, string apiKey)
        {
            try
            {
                apiKey = apiKey.Trim();

                // 1. Groq Provider (Keys starting with gsk_)
                if (apiKey.StartsWith("gsk_", StringComparison.OrdinalIgnoreCase))
                {
                    using var req = new HttpRequestMessage(HttpMethod.Post, "https://api.groq.com/openai/v1/chat/completions");
                    req.Headers.Add("Authorization", "Bearer " + apiKey);

                    var systemMsg = "You are Vixz AI, an intelligent, lightning-fast AI assistant integrated inside Vixz Desktop, a modern YouTube player app on Windows. Be direct, clear, highly accurate, concise, and helpful.";
                    if (currentVideo != null)
                    {
                        systemMsg += $"\n[Context: The user is currently watching video \"{currentVideo.Title}\" by channel \"{currentVideo.ChannelTitle}\" (Duration: {currentVideo.DurationText})]";
                    }

                    var messages = new List<object>
                    {
                        new { role = "system", content = systemMsg }
                    };

                    // Add past conversation turns for multi-turn conversational memory
                    foreach (var turn in _conversationHistory.TakeLast(6))
                    {
                        messages.Add(new { role = turn.Role, content = turn.Content });
                    }

                    messages.Add(new { role = "user", content = prompt });

                    var payload = new
                    {
                        model = "openai/gpt-oss-120b",
                        messages = messages,
                        temperature = 0.5,
                        max_tokens = 600
                    };

                    req.Content = new StringContent(Newtonsoft.Json.JsonConvert.SerializeObject(payload), Encoding.UTF8, "application/json");
                    var resp = await _httpClient.SendAsync(req);
                    if (resp.IsSuccessStatusCode)
                    {
                        var json = await resp.Content.ReadAsStringAsync();
                        var jObj = JObject.Parse(json);
                        var aiText = (string?)jObj["choices"]?[0]?["message"]?["content"];
                        if (!string.IsNullOrWhiteSpace(aiText))
                        {
                            var trimmed = aiText.Trim();
                            _conversationHistory.Add(("user", prompt));
                            _conversationHistory.Add(("assistant", trimmed));
                            if (_conversationHistory.Count > 20) _conversationHistory.RemoveRange(0, 4);

                            return new AiCommandResult
                            {
                                Type = AiCommandType.ChatAnswer,
                                ResponseMessage = trimmed,
                                SourceCitation = "Groq Llama 3 / GPT"
                            };
                        }
                    }
                }
                // 2. OpenAI Provider (Keys starting with sk-)
                else if (apiKey.StartsWith("sk-", StringComparison.OrdinalIgnoreCase))
                {
                    using var req = new HttpRequestMessage(HttpMethod.Post, "https://api.openai.com/v1/chat/completions");
                    req.Headers.Add("Authorization", "Bearer " + apiKey);

                    var systemMsg = "You are Vixz AI, an intelligent, helpful AI assistant integrated inside Vixz Desktop. Be direct, clear, concise, and helpful.";
                    if (currentVideo != null)
                    {
                        systemMsg += $"\n[Context: Currently watching \"{currentVideo.Title}\" by \"{currentVideo.ChannelTitle}\"]";
                    }

                    var messages = new List<object> { new { role = "system", content = systemMsg } };
                    foreach (var turn in _conversationHistory.TakeLast(6))
                    {
                        messages.Add(new { role = turn.Role, content = turn.Content });
                    }
                    messages.Add(new { role = "user", content = prompt });

                    var payload = new
                    {
                        model = "gpt-4o-mini",
                        messages = messages,
                        temperature = 0.5,
                        max_tokens = 600
                    };

                    req.Content = new StringContent(Newtonsoft.Json.JsonConvert.SerializeObject(payload), Encoding.UTF8, "application/json");
                    var resp = await _httpClient.SendAsync(req);
                    if (resp.IsSuccessStatusCode)
                    {
                        var json = await resp.Content.ReadAsStringAsync();
                        var jObj = JObject.Parse(json);
                        var aiText = (string?)jObj["choices"]?[0]?["message"]?["content"];
                        if (!string.IsNullOrWhiteSpace(aiText))
                        {
                            var trimmed = aiText.Trim();
                            _conversationHistory.Add(("user", prompt));
                            _conversationHistory.Add(("assistant", trimmed));
                            return new AiCommandResult
                            {
                                Type = AiCommandType.ChatAnswer,
                                ResponseMessage = trimmed,
                                SourceCitation = "OpenAI"
                            };
                        }
                    }
                }
                // 3. Google Gemini Provider (Default)
                else
                {
                    var endpoint = $"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={apiKey}";
                    var contextPrompt = "You are Vixz AI, a powerful, helpful AI assistant integrated into the Vixz Desktop YouTube player application on Windows. Be concise, informative, and friendly.\n";
                    if (currentVideo != null)
                    {
                        contextPrompt += $"Currently playing video: \"{currentVideo.Title}\" by channel \"{currentVideo.ChannelTitle}\" (Duration: {currentVideo.DurationText}).\n";
                    }

                    foreach (var turn in _conversationHistory.TakeLast(6))
                    {
                        contextPrompt += $"{turn.Role}: {turn.Content}\n";
                    }
                    contextPrompt += $"\nUser: {prompt}\n\nVixz AI:";

                    var payload = new
                    {
                        contents = new[]
                        {
                            new { parts = new[] { new { text = contextPrompt } } }
                        }
                    };

                    var content = new StringContent(Newtonsoft.Json.JsonConvert.SerializeObject(payload), Encoding.UTF8, "application/json");
                    var resp = await _httpClient.PostAsync(endpoint, content);
                    if (resp.IsSuccessStatusCode)
                    {
                        var json = await resp.Content.ReadAsStringAsync();
                        var jObj = JObject.Parse(json);
                        var aiText = (string?)jObj["candidates"]?[0]?["content"]?["parts"]?[0]?["text"];
                        if (!string.IsNullOrWhiteSpace(aiText))
                        {
                            var trimmed = aiText.Trim();
                            _conversationHistory.Add(("user", prompt));
                            _conversationHistory.Add(("assistant", trimmed));
                            return new AiCommandResult
                            {
                                Type = AiCommandType.ChatAnswer,
                                ResponseMessage = trimmed,
                                SourceCitation = "Gemini AI"
                            };
                        }
                    }
                }
            }
            catch { }
            return null;
        }

        public static async Task<VideoSummaryResult> GenerateSummaryAsync(
            VideoItem video, 
            Func<string, Task<string>>? webViewTranscriptFetcher = null)
        {
            var summary = new VideoSummaryResult
            {
                VideoId = video.Id,
                VideoTitle = video.Title,
                ChannelTitle = video.ChannelTitle
            };

            string rawTranscript = "";

            // 1. Primary: Direct In-Browser Transcript via WebView2
            if (webViewTranscriptFetcher != null)
            {
                try
                {
                    var webText = await webViewTranscriptFetcher(video.Id);
                    if (!string.IsNullOrWhiteSpace(webText) && webText.Trim().Length > 50)
                    {
                        rawTranscript = webText.Trim();
                        summary.HasTranscript = true;
                    }
                }
                catch { }
            }

            // 2. Secondary: Direct YouTube Innertube / TimedText API Extraction (Zero API Key Needed)
            if (string.IsNullOrWhiteSpace(rawTranscript))
            {
                try
                {
                    var innertubeCaptions = await FetchInnertubeCaptionsAsync(video.Id);
                    if (!string.IsNullOrWhiteSpace(innertubeCaptions))
                    {
                        rawTranscript = innertubeCaptions;
                        summary.HasTranscript = true;
                    }
                }
                catch { }
            }

            // 3. Tertiary: YoutubeExplode Closed Captions API
            if (string.IsNullOrWhiteSpace(rawTranscript))
            {
                try
                {
                    var trackManifest = await _client.Videos.ClosedCaptions.GetManifestAsync(video.Id);
                    var trackInfo = trackManifest.Tracks.FirstOrDefault(t => t.Language.Code.Equals("en", StringComparison.OrdinalIgnoreCase)) ??
                                    trackManifest.Tracks.FirstOrDefault(t => t.Language.Code.Contains("en", StringComparison.OrdinalIgnoreCase)) ??
                                    trackManifest.Tracks.FirstOrDefault(t => t.Language.Name.Contains("English", StringComparison.OrdinalIgnoreCase)) ??
                                    trackManifest.Tracks.FirstOrDefault();

                    if (trackInfo != null)
                    {
                        var track = await _client.Videos.ClosedCaptions.GetAsync(trackInfo);
                        if (track != null && track.Captions.Count > 0)
                        {
                            summary.HasTranscript = true;
                            var sb = new StringBuilder();
                            var chapterInterval = Math.Max(60.0, (track.Captions.Last().Offset.TotalSeconds) / 6.0);
                            double nextChapterMark = 0;

                            foreach (var cap in track.Captions)
                            {
                                var text = cap.Text?.Replace("\n", " ").Trim() ?? "";
                                if (string.IsNullOrWhiteSpace(text)) continue;

                                sb.Append(text).Append(" ");

                                if (cap.Offset.TotalSeconds >= nextChapterMark && summary.Chapters.Count < 6)
                                {
                                    var cleanSnippet = text.Length > 45 ? text.Substring(0, 42) + "..." : text;
                                    summary.Chapters.Add(new TimestampChapter
                                    {
                                        Seconds = cap.Offset.TotalSeconds,
                                        TimeFormatted = FormatTime(cap.Offset),
                                        Title = cleanSnippet
                                    });
                                    nextChapterMark += chapterInterval;
                                }
                            }
                            rawTranscript = sb.ToString();
                        }
                    }
                }
                catch { }
            }

            // 4. Fallback: Creator description
            string rawDescription = "";
            try
            {
                var details = await _client.Videos.GetAsync(video.Id);
                rawDescription = details.Description ?? "";
            }
            catch { }

            // Synthesize Executive Summary and Key Takeaways
            await GenerateStructuredPointsAsync(rawTranscript, rawDescription, video, summary);

            return summary;
        }

        private static async Task<string> FetchInnertubeCaptionsAsync(string videoId)
        {
            try
            {
                var payload = new
                {
                    context = new
                    {
                        client = new
                        {
                            clientName = "ANDROID_VR",
                            clientVersion = "1.61.48"
                        }
                    },
                    videoId = videoId
                };

                using var req = new HttpRequestMessage(HttpMethod.Post, "https://www.youtube.com/youtubei/v1/player");
                req.Content = new StringContent(Newtonsoft.Json.JsonConvert.SerializeObject(payload), Encoding.UTF8, "application/json");

                var resp = await _httpClient.SendAsync(req);
                if (!resp.IsSuccessStatusCode) return "";

                var json = await resp.Content.ReadAsStringAsync();
                var jObj = JObject.Parse(json);

                var captionTracks = jObj["captions"]?["playerCaptionsTracklistRenderer"]?["captionTracks"] as JArray;
                if (captionTracks == null || captionTracks.Count == 0) return "";

                // Find English or first track
                var selectedTrack = captionTracks.FirstOrDefault(t => ((string?)t["languageCode"])?.StartsWith("en", StringComparison.OrdinalIgnoreCase) == true)
                                 ?? captionTracks.FirstOrDefault();

                var baseUrl = (string?)selectedTrack?["baseUrl"];
                if (string.IsNullOrWhiteSpace(baseUrl)) return "";

                var capResp = await _httpClient.GetStringAsync(baseUrl);
                if (string.IsNullOrWhiteSpace(capResp)) return "";

                // Parse XML <p> and <s> or <text> elements
                var sb = new StringBuilder();
                var textMatches = Regex.Matches(capResp, @"<(?:text|p|s)[^>]*>(.*?)</(?:text|p|s)>", RegexOptions.Singleline);
                foreach (Match m in textMatches)
                {
                    var inner = Regex.Replace(m.Groups[1].Value, @"<[^>]+>", " ");
                    var decoded = WebUtility.HtmlDecode(inner).Trim();
                    if (!string.IsNullOrWhiteSpace(decoded) && !decoded.StartsWith("<"))
                    {
                        sb.Append(decoded).Append(" ");
                    }
                }

                return sb.ToString().Trim();
            }
            catch
            {
                return "";
            }
        }

        private static async Task GenerateStructuredPointsAsync(string transcriptText, string descriptionText, VideoItem video, VideoSummaryResult summary)
        {
            // Clean both transcript and description
            var cleanTranscriptSentences = CleanAndExtractSentences(transcriptText, video);
            var cleanDescSentences = CleanAndExtractSentences(descriptionText, video);

            // 1. If we have real spoken transcript sentences, use them directly
            if (cleanTranscriptSentences.Count >= 3)
            {
                summary.Tldr = string.Join(". ", cleanTranscriptSentences.Take(3)) + ".";
                summary.KeyTakeaways = DistributeKeyPoints(cleanTranscriptSentences, 5);
                return;
            }

            // 2. If description has legitimate content (after purging all donations/copyright disclaimers)
            if (cleanDescSentences.Count >= 2)
            {
                summary.Tldr = string.Join(". ", cleanDescSentences.Take(2)) + ".";
                summary.KeyTakeaways = DistributeKeyPoints(cleanDescSentences, 4);
                return;
            }

            // 3. High-Intelligence Fallback: Query Live Web Intelligence for the topic of the video!
            // Clean clickbait words like "BREAKING:", "🚨", "EXCLUSIVE", etc.
            var topicQuery = CleanTopicQuery(video.Title);
            var webResult = await QueryLiveWebKnowledgeAsync(topicQuery);

            if (webResult != null && !string.IsNullOrWhiteSpace(webResult.ResponseMessage))
            {
                summary.Tldr = $"**{video.Title}** — {webResult.ResponseMessage}";
                var takeaways = new List<string>();

                foreach (var fact in webResult.WebFacts.Take(4))
                {
                    if (!string.IsNullOrWhiteSpace(fact) && !takeaways.Contains(fact))
                    {
                        takeaways.Add(fact);
                    }
                }

                if (takeaways.Count == 0)
                {
                    takeaways.Add($"Topic Coverage: In-depth report by {video.ChannelTitle}.");
                    takeaways.Add($"Key Focus: {video.Title}");
                    takeaways.Add($"Broadcast Date: {video.UploadDateText}");
                }

                summary.KeyTakeaways = takeaways;
                return;
            }

            // 4. Default clean fallback (Never output boilerplate!)
            summary.Tldr = $"In-depth video report titled **{video.Title}** presented by **{video.ChannelTitle}**.";
            summary.KeyTakeaways = new List<string>
            {
                $"Comprehensive news and commentary covering: {video.Title}",
                $"Channel & Creator: {video.ChannelTitle}",
                $"Video Duration: {video.DurationText} • Uploaded: {video.UploadDateText}"
            };
        }

        private static string CleanTopicQuery(string title)
        {
            var cleaned = Regex.Replace(title, @"(?i)\b(breaking|alert|exclusive|must watch|live|update|warning|shocking|urgent)\b", " ");
            cleaned = Regex.Replace(cleaned, @"[🚨🔥💥⚠️📢🎬🔴]+", " ");
            cleaned = Regex.Replace(cleaned, @"[-—:|]+", " ");
            cleaned = Regex.Replace(cleaned, @"\s+", " ").Trim();
            return cleaned.Length > 5 ? cleaned : title;
        }

        private static List<string> CleanAndExtractSentences(string text, VideoItem video)
        {
            if (string.IsNullOrWhiteSpace(text)) return new List<string>();

            // 1. Blacklist Regex: Exclude Cashapp, Venmo, PayPal, Copyright disclaimers, Fair use boilerplate, promo codes, subscribe plugs
            var blacklistRegex = new Regex(
                @"(?i)\b(cashapp|\$|venmo|paypal|donate|donations|patreon|gofundme|crypto|bitcoin|btc|eth|wallet|zelle|" +
                @"copyright disclaimer|section 107|copyright act|fair use|criticism|commentary|news reporting|scholarship|research|" +
                @"non-profit|personal use|no copyright infringement|all rights reserved|disclaimer:|the views and opinions|" +
                @"support the channel|subscribe to|hit the bell|leave a comment|like and subscribe|thanks for watching|" +
                @"see you next time|follow me on|follow us on|social media|discount code|promo code|sponsored by|" +
                @"affiliate link|merch|store|t-shirt|expressvpn|nordvpn|betterhelp)\b"
            );

            // Strip URLs and web handles
            text = Regex.Replace(text, @"https?://\S+", " ", RegexOptions.IgnoreCase);
            text = Regex.Replace(text, @"\b[\w\-]+\.(?:com|org|net|io|gov|edu|co|tv|app|me|be|yt|link)/\S*", " ", RegexOptions.IgnoreCase);
            text = Regex.Replace(text, @"@[\w\-]+", " ", RegexOptions.IgnoreCase);

            var rawSentences = text.Split(new[] { '.', '!', '?', '\n', '\r' }, StringSplitOptions.RemoveEmptyEntries)
                                   .Select(s => s.Trim())
                                   .ToList();

            var cleanSentences = new List<string>();
            foreach (var s in rawSentences)
            {
                if (blacklistRegex.IsMatch(s)) continue;

                var cleaned = Regex.Replace(s, @"\s+", " ").Trim();
                if (cleaned.Length < 20) continue;
                if (cleaned.Equals(video.Title, StringComparison.OrdinalIgnoreCase)) continue;

                // Capitalize first letter
                if (char.IsLower(cleaned[0]))
                {
                    cleaned = char.ToUpper(cleaned[0]) + cleaned.Substring(1);
                }

                if (!cleanSentences.Contains(cleaned, StringComparer.OrdinalIgnoreCase))
                {
                    cleanSentences.Add(cleaned);
                }
            }

            return cleanSentences;
        }

        private static List<string> DistributeKeyPoints(List<string> cleanSentences, int maxCount)
        {
            var points = new List<string>();
            if (cleanSentences.Count <= maxCount)
            {
                points.AddRange(cleanSentences);
            }
            else
            {
                var step = cleanSentences.Count / (double)maxCount;
                for (int i = 0; i < maxCount; i++)
                {
                    int index = Math.Min(cleanSentences.Count - 1, (int)(i * step));
                    var pt = cleanSentences[index];
                    if (!points.Contains(pt)) points.Add(pt);
                }
            }
            return points;
        }

        private static string FormatTime(TimeSpan ts)
        {
            return ts.Hours > 0
                ? $"{ts.Hours}:{ts.Minutes:D2}:{ts.Seconds:D2}"
                : $"{ts.Minutes:D2}:{ts.Seconds:D2}";
        }
    }
}
