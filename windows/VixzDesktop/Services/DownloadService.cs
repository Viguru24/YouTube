using System;
using System.IO;
using System.Linq;
using System.Net;
using System.Threading.Tasks;
using YoutubeExplode;
using YoutubeExplode.Videos.Streams;

namespace VixzDesktop.Services
{
    public class DownloadService
    {
        private static readonly YoutubeClient _client = new YoutubeClient();

        public static async Task<string> DownloadVideoAsync(string videoId, string title, IProgress<double>? progress = null)
        {
            if (string.IsNullOrWhiteSpace(videoId))
            {
                throw new ArgumentException("Video ID cannot be empty", nameof(videoId));
            }

            StreamManifest manifest;
            try
            {
                manifest = await _client.Videos.Streams.GetManifestAsync(videoId);
            }
            catch (Exception ex)
            {
                throw new Exception($"Failed to fetch video streams from YouTube ({ex.Message}). The video might be restricted or require sign-in.", ex);
            }

            // 1. Try muxed streams (combined audio & video)
            IStreamInfo? streamInfo = manifest.GetMuxedStreams().GetWithHighestVideoQuality();

            if (streamInfo == null)
            {
                // 2. Fallback to any muxed stream
                streamInfo = manifest.GetMuxedStreams().FirstOrDefault();
            }

            if (streamInfo == null)
            {
                // 3. Fallback to video-only or audio-only
                streamInfo = (IStreamInfo?)manifest.GetVideoOnlyStreams().GetWithHighestVideoQuality() ??
                             (IStreamInfo?)manifest.GetAudioOnlyStreams().GetWithHighestBitrate() ??
                             manifest.Streams.FirstOrDefault();
            }

            if (streamInfo == null)
            {
                throw new Exception("No downloadable media streams available for this video.");
            }

            var downloadsFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "Vixz");
            Directory.CreateDirectory(downloadsFolder);

            // Clean title for Windows filename safety
            var decodedTitle = WebUtility.HtmlDecode(title ?? "");
            var invalidChars = Path.GetInvalidFileNameChars();
            var cleanTitle = new string(decodedTitle.Where(c => !invalidChars.Contains(c)).ToArray()).Trim('.', ' ');
            if (string.IsNullOrWhiteSpace(cleanTitle)) cleanTitle = $"Video_{videoId}";
            if (cleanTitle.Length > 60) cleanTitle = cleanTitle.Substring(0, 60).Trim('.', ' ');

            var ext = streamInfo.Container.Name.ToLowerInvariant();
            if (string.IsNullOrWhiteSpace(ext)) ext = "mp4";

            var filePath = Path.Combine(downloadsFolder, $"{cleanTitle}.{ext}");

            // Avoid file collisions
            int counter = 1;
            while (File.Exists(filePath))
            {
                filePath = Path.Combine(downloadsFolder, $"{cleanTitle}_{counter}.{ext}");
                counter++;
            }

            try
            {
                await _client.Videos.Streams.DownloadAsync(streamInfo, filePath, progress);
            }
            catch (Exception ex)
            {
                // If partial download file exists, remove it
                if (File.Exists(filePath))
                {
                    try { File.Delete(filePath); } catch { }
                }
                throw new Exception($"Stream download interrupted: {ex.Message}", ex);
            }

            return filePath;
        }
    }
}
