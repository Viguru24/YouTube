using System;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using YoutubeExplode;
using YoutubeExplode.Videos.Streams;

namespace VixzDesktop.Services
{
    public class DownloadService
    {
        private static readonly YoutubeClient _explodeClient = new YoutubeClient();
        private static readonly HttpClient _httpClient = new HttpClient(new HttpClientHandler
        {
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate
        })
        {
            Timeout = TimeSpan.FromMinutes(10)
        };

        public static string ExtractVideoId(string input)
        {
            if (string.IsNullOrWhiteSpace(input)) return "";
            input = input.Trim();
            if (input.Length == 11 && Regex.IsMatch(input, @"^[a-zA-Z0-9_-]{11}$"))
            {
                return input;
            }

            var match = Regex.Match(input, @"(?:v=|\/v\/|youtu\.be\/|\/embed\/|\/shorts\/|^)([a-zA-Z0-9_-]{11})");
            if (match.Success)
            {
                return match.Groups[1].Value;
            }

            return input;
        }

        public static async Task<string> DownloadVideoAsync(string videoInput, string title, IProgress<double>? progress = null)
        {
            var videoId = ExtractVideoId(videoInput);
            if (string.IsNullOrWhiteSpace(videoId))
            {
                throw new ArgumentException("Invalid or empty Video ID", nameof(videoInput));
            }

            var downloadsFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "Vixz");
            if (!Directory.Exists(downloadsFolder))
            {
                Directory.CreateDirectory(downloadsFolder);
            }

            Exception? lastError = null;

            // Strategy 1: High-Speed yt-dlp Python / Native Engine (Primary)
            try
            {
                var ytdlpPath = await TryDownloadWithYtDlpAsync(videoId, downloadsFolder, progress);
                if (!string.IsNullOrWhiteSpace(ytdlpPath) && File.Exists(ytdlpPath))
                {
                    progress?.Report(1.0);
                    return ytdlpPath;
                }
            }
            catch (Exception ex)
            {
                lastError = ex;
                Debug.WriteLine($"[DownloadService] yt-dlp strategy failed: {ex.Message}");
            }

            // Strategy 2: YoutubeExplode Stream Manifest Engine (Fallback)
            try
            {
                var explodePath = await TryDownloadWithYoutubeExplodeAsync(videoId, title, downloadsFolder, progress);
                if (!string.IsNullOrWhiteSpace(explodePath) && File.Exists(explodePath))
                {
                    progress?.Report(1.0);
                    return explodePath;
                }
            }
            catch (Exception ex)
            {
                lastError = ex;
                Debug.WriteLine($"[DownloadService] YoutubeExplode strategy failed: {ex.Message}");
            }

            // Strategy 3: Direct Innertube HTTP Range Engine (Fallback)
            try
            {
                var innertubePath = await TryDownloadWithInnertubeAsync(videoId, title, downloadsFolder, progress);
                if (!string.IsNullOrWhiteSpace(innertubePath) && File.Exists(innertubePath))
                {
                    progress?.Report(1.0);
                    return innertubePath;
                }
            }
            catch (Exception ex)
            {
                lastError = ex;
                Debug.WriteLine($"[DownloadService] Innertube strategy failed: {ex.Message}");
            }

            throw new Exception($"All download engines failed for video '{videoId}'. Last error: {lastError?.Message}", lastError);
        }

        private static async Task<string?> TryDownloadWithYtDlpAsync(string videoId, string downloadsFolder, IProgress<double>? progress)
        {
            var startInfo = new ProcessStartInfo
            {
                FileName = "python",
                Arguments = $"-m yt_dlp -f \"bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best\" --no-playlist --newline --progress-template \"PROGRESS:%(progress._percent_str)s\" -P \"{downloadsFolder}\" \"https://www.youtube.com/watch?v={videoId}\" --print \"after_move:filepath\"",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            using var process = new Process { StartInfo = startInfo };
            string? resultingFilePath = null;
            var errorOutput = new System.Text.StringBuilder();

            process.OutputDataReceived += (s, e) =>
            {
                if (string.IsNullOrWhiteSpace(e.Data)) return;
                var line = e.Data.Trim();

                if (line.StartsWith("PROGRESS:"))
                {
                    var pStr = line.Substring(9).Replace("%", "").Trim();
                    if (double.TryParse(pStr, NumberStyles.Any, CultureInfo.InvariantCulture, out var percent))
                    {
                        progress?.Report(Math.Clamp(percent / 100.0, 0.0, 0.99));
                    }
                }
                else if (File.Exists(line) || (line.Contains(downloadsFolder, StringComparison.OrdinalIgnoreCase) && line.EndsWith(".mp4", StringComparison.OrdinalIgnoreCase)))
                {
                    resultingFilePath = line;
                }
            };

            process.ErrorDataReceived += (s, e) =>
            {
                if (!string.IsNullOrWhiteSpace(e.Data))
                {
                    errorOutput.AppendLine(e.Data);
                }
            };

            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            await process.WaitForExitAsync();

            if (process.ExitCode != 0 && string.IsNullOrWhiteSpace(resultingFilePath))
            {
                throw new Exception($"yt-dlp process exited with code {process.ExitCode}: {errorOutput}");
            }

            // If path wasn't captured from stdout, look for newly created matching MP4 in downloads folder
            if (string.IsNullOrWhiteSpace(resultingFilePath) || !File.Exists(resultingFilePath))
            {
                var candidates = Directory.GetFiles(downloadsFolder, $"*{videoId}*.mp4");
                if (candidates.Length > 0)
                {
                    resultingFilePath = candidates.OrderByDescending(File.GetLastWriteTime).First();
                }
            }

            return resultingFilePath;
        }

        private static async Task<string?> TryDownloadWithYoutubeExplodeAsync(string videoId, string title, string downloadsFolder, IProgress<double>? progress)
        {
            var manifest = await _explodeClient.Videos.Streams.GetManifestAsync(videoId);

            // 1. Try muxed streams (combined audio & video)
            IStreamInfo? streamInfo = manifest.GetMuxedStreams().GetWithHighestVideoQuality() ??
                                      manifest.GetMuxedStreams().FirstOrDefault() ??
                                      (IStreamInfo?)manifest.GetVideoOnlyStreams().GetWithHighestVideoQuality() ??
                                      (IStreamInfo?)manifest.GetAudioOnlyStreams().GetWithHighestBitrate() ??
                                      manifest.Streams.FirstOrDefault();

            if (streamInfo == null) return null;

            var decodedTitle = WebUtility.HtmlDecode(title ?? "");
            var invalidChars = Path.GetInvalidFileNameChars();
            var cleanTitle = new string(decodedTitle.Where(c => !invalidChars.Contains(c)).ToArray()).Trim('.', ' ');
            if (string.IsNullOrWhiteSpace(cleanTitle)) cleanTitle = $"Video_{videoId}";
            if (cleanTitle.Length > 60) cleanTitle = cleanTitle.Substring(0, 60).Trim('.', ' ');

            var ext = streamInfo.Container.Name.ToLowerInvariant();
            if (string.IsNullOrWhiteSpace(ext)) ext = "mp4";

            var filePath = Path.Combine(downloadsFolder, $"{cleanTitle}_{videoId}.{ext}");

            await _explodeClient.Videos.Streams.DownloadAsync(streamInfo, filePath, progress);
            return filePath;
        }

        private static async Task<string?> TryDownloadWithInnertubeAsync(string videoId, string title, string downloadsFolder, IProgress<double>? progress)
        {
            var decodedTitle = WebUtility.HtmlDecode(title ?? "");
            var invalidChars = Path.GetInvalidFileNameChars();
            var cleanTitle = new string(decodedTitle.Where(c => !invalidChars.Contains(c)).ToArray()).Trim('.', ' ');
            if (string.IsNullOrWhiteSpace(cleanTitle)) cleanTitle = $"Video_{videoId}";
            if (cleanTitle.Length > 60) cleanTitle = cleanTitle.Substring(0, 60).Trim('.', ' ');

            var filePath = Path.Combine(downloadsFolder, $"{cleanTitle}_{videoId}.mp4");

            // Query Invidious API instance for direct fallback stream URL
            var invidiousInstances = new[]
            {
                "https://invidious.flokinet.to",
                "https://inv.nadeko.net",
                "https://yewtu.be",
                "https://invidious.nerdvpn.de"
            };

            string? streamUrl = null;
            foreach (var instance in invidiousInstances)
            {
                try
                {
                    using var req = new HttpRequestMessage(HttpMethod.Get, $"{instance}/api/v1/videos/{videoId}");
                    using var resp = await _httpClient.SendAsync(req);
                    if (resp.IsSuccessStatusCode)
                    {
                        var json = await resp.Content.ReadAsStringAsync();
                        var obj = Newtonsoft.Json.Linq.JObject.Parse(json);
                        var formats = obj["formatStreams"] as Newtonsoft.Json.Linq.JArray;
                        if (formats != null && formats.Count > 0)
                        {
                            streamUrl = formats[0]?["url"]?.ToString();
                            if (!string.IsNullOrWhiteSpace(streamUrl)) break;
                        }
                    }
                }
                catch { }
            }

            if (string.IsNullOrWhiteSpace(streamUrl)) return null;

            using (var response = await _httpClient.GetAsync(streamUrl, HttpCompletionOption.ResponseHeadersRead))
            {
                response.EnsureSuccessStatusCode();
                var totalBytes = response.Content.Headers.ContentLength ?? -1L;

                using var stream = await response.Content.ReadAsStreamAsync();
                using var fileStream = new FileStream(filePath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);

                var buffer = new byte[81920];
                long totalRead = 0;
                int bytesRead;

                while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length)) > 0)
                {
                    await fileStream.WriteAsync(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (totalBytes > 0)
                    {
                        progress?.Report((double)totalRead / totalBytes);
                    }
                }
            }

            return filePath;
        }
    }
}
