using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public class SeriesInfo
    {
        public string SeriesTitle { get; set; } = string.Empty;
        public string Season { get; set; } = string.Empty;
        public string Episode { get; set; } = string.Empty;
        public int? SeasonNumber { get; set; }
        public int? EpisodeNumber { get; set; }
        public string ChannelTitle { get; set; } = string.Empty;
        public List<string> SuggestedKeywords { get; set; } = new();

        public string DisplayLabel
        {
            get
            {
                if (!string.IsNullOrWhiteSpace(SeriesTitle) && !string.IsNullOrWhiteSpace(Season))
                    return $"{SeriesTitle} ({Season})";
                if (!string.IsNullOrWhiteSpace(SeriesTitle))
                    return SeriesTitle;
                return ChannelTitle;
            }
        }
    }

    public static class SeriesDetectionService
    {
        private static readonly Regex SeasonPattern = new(
            @"\b(?:Season|Series|Book|Campaign|Part|Vol\.?|Volume)\s*(\d+)\b",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static readonly Regex SePattern = new(
            @"\bS(\d{1,2})\s*E(\d{1,2})\b",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static readonly Regex SOnlyPattern = new(
            @"\bS(\d{1,2})\b",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static readonly Regex EpisodePattern = new(
            @"\b(?:Episode|Ep\.?|E)\s*(\d+)\b",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static readonly Regex StripTagsPattern = new(
            @"[\[\(](?:full\s*episode|full\s*show|clip|official|hd|4k|trailer|teaser|recap|compilation|remastered)[\]\)]",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static readonly Regex VideoSuffixPattern = new(
            @"\b(?:full\s*episode|full\s*show|official\s*video|compilation|preview|trailer|recap)\b",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static readonly Regex StripSeasonEpTrailPattern = new(
            @"\b(?:Season|Series|Book|Campaign|Episode|Ep|Part|S\d{1,2}\s*E\d{1,2}|S\d+|E\d+)\b.*$",
            RegexOptions.IgnoreCase | RegexOptions.Compiled);

        public static SeriesInfo DetectSeries(VideoItem? video)
        {
            if (video == null) return new SeriesInfo();
            return DetectSeries(video.Title, video.ChannelTitle);
        }

        public static SeriesInfo DetectSeries(string? videoTitle, string? channelTitle)
        {
            var channel = channelTitle?.Trim() ?? "";
            var title = videoTitle?.Trim() ?? "";

            if (string.IsNullOrWhiteSpace(title))
            {
                return new SeriesInfo
                {
                    SeriesTitle = channel,
                    ChannelTitle = channel,
                    SuggestedKeywords = new List<string> { channel, "Full Episodes", "Playlists" }
                };
            }

            // 1. Clean bracketed noise
            var clean = StripTagsPattern.Replace(title, "").Trim();

            // 2. Extract Season & Episode
            string seasonStr = "";
            int? seasonNum = null;
            var sMatch = SeasonPattern.Match(clean);
            var seMatch = SePattern.Match(clean);
            var sOnlyMatch = SOnlyPattern.Match(clean);

            if (sMatch.Success && int.TryParse(sMatch.Groups[1].Value, out var sn1))
            {
                seasonNum = sn1;
                seasonStr = sMatch.Value.Trim();
            }
            else if (seMatch.Success && int.TryParse(seMatch.Groups[1].Value, out var sn2))
            {
                seasonNum = sn2;
                seasonStr = $"Season {seasonNum}";
            }
            else if (sOnlyMatch.Success && int.TryParse(sOnlyMatch.Groups[1].Value, out var sn3))
            {
                seasonNum = sn3;
                seasonStr = $"Season {seasonNum}";
            }

            string epStr = "";
            int? epNum = null;
            var epMatch = EpisodePattern.Match(clean);
            if (epMatch.Success && int.TryParse(epMatch.Groups[1].Value, out var en))
            {
                epNum = en;
                epStr = $"Episode {epNum}";
            }
            else if (seMatch.Success && int.TryParse(seMatch.Groups[2].Value, out var en2))
            {
                epNum = en2;
                epStr = $"Episode {epNum}";
            }

            // 3. Extract series name candidate
            var cleanedTitle = VideoSuffixPattern.Replace(clean, "").Trim();
            var parts = Regex.Split(cleanedTitle, @"\s*[|\-—–•~]\s*")
                             .Select(p => p.Trim())
                             .Where(p => !string.IsNullOrWhiteSpace(p))
                             .ToList();

            string candidate = "";
            foreach (var p in parts)
            {
                var noSe = StripSeasonEpTrailPattern.Replace(p, "").Trim().Trim(' ', ':', '-', '—', '–', '|', '•', ',');
                if (noSe.Length >= 3 && !Regex.IsMatch(noSe, @"^(?:S\d|E\d|Season|Episode|Clip|Full)", RegexOptions.IgnoreCase))
                {
                    candidate = noSe;
                    break;
                }
            }

            if (string.IsNullOrWhiteSpace(candidate) && parts.Count > 0)
            {
                candidate = StripSeasonEpTrailPattern.Replace(parts[0], "").Trim().Trim(' ', ':', '-', '—', '–', '|', '•', ',');
            }

            if (string.IsNullOrWhiteSpace(candidate) || candidate.Length < 3)
            {
                candidate = channel;
            }

            candidate = candidate.Trim(' ', ':', '-', '—', '–', '|', '•', ',');

            // 4. Build smart suggested search chips/keywords
            var suggestions = new List<string>();
            if (!string.IsNullOrWhiteSpace(candidate))
            {
                suggestions.Add(candidate);
                if (!string.IsNullOrWhiteSpace(seasonStr))
                {
                    suggestions.Add($"{candidate} {seasonStr}");
                }
                suggestions.Add($"{candidate} Full Episode");
            }
            else
            {
                suggestions.Add(channel);
            }

            // Also add season specific pills if a series is detected
            if (seasonNum.HasValue)
            {
                for (int s = 1; s <= Math.Max(seasonNum.Value + 2, 5); s++)
                {
                    var sPill = $"Season {s}";
                    if (!suggestions.Contains(sPill) && suggestions.Count < 8)
                    {
                        suggestions.Add(sPill);
                    }
                }
            }
            else
            {
                suggestions.Add("Season 1");
                suggestions.Add("Season 2");
                suggestions.Add("Season 3");
            }

            if (!suggestions.Contains("Playlists"))
            {
                suggestions.Add("Playlists");
            }

            return new SeriesInfo
            {
                SeriesTitle = candidate,
                Season = seasonStr,
                Episode = epStr,
                SeasonNumber = seasonNum,
                EpisodeNumber = epNum,
                ChannelTitle = channel,
                SuggestedKeywords = suggestions.Distinct().ToList()
            };
        }

        public static int? ExtractEpisodeNumber(string? title)
        {
            if (string.IsNullOrWhiteSpace(title)) return null;
            var m = EpisodePattern.Match(title);
            if (m.Success && int.TryParse(m.Groups[1].Value, out var ep))
            {
                return ep;
            }
            var se = SePattern.Match(title);
            if (se.Success && int.TryParse(se.Groups[2].Value, out var ep2))
            {
                return ep2;
            }
            return null;
        }

        public static int? ExtractSeasonNumber(string? title)
        {
            if (string.IsNullOrWhiteSpace(title)) return null;
            var m = SeasonPattern.Match(title);
            if (m.Success && int.TryParse(m.Groups[1].Value, out var s))
            {
                return s;
            }
            var se = SePattern.Match(title);
            if (se.Success && int.TryParse(se.Groups[1].Value, out var s2))
            {
                return s2;
            }
            return null;
        }
    }
}
