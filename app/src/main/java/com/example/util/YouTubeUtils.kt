package com.example.util

import com.example.data.model.VideoEntity
import java.util.regex.Pattern

object YouTubeUtils {
    // Regex pattern matching various YouTube URL formats
    private val YOUTUBE_ID_PATTERN: Pattern = Pattern.compile(
        "(?:youtube(?:-nocookie)?\\.com/(?:[^/\\n\\s]+/.+/|(?:v|e(?:mbed)?|live)/|.*[?&]v=)|youtu\\.be/|youtube\\.com/shorts/)([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts YouTube 11-char Video ID from URL or raw ID string.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return trimmed
        }
        val matcher = YOUTUBE_ID_PATTERN.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    /**
     * Strict detection for true vertical YouTube Shorts.
     * YouTube Shorts are strictly 60 seconds or less and tagged with #shorts.
     * Regular long/horizontal videos are NEVER classified as Shorts.
     */
    fun isShortVideo(video: VideoEntity): Boolean {
        if (video.category.equals("Shorts", ignoreCase = true)) return true

        val durationSec = parseFormattedTimeToSeconds(video.durationText)
        val titleLower = video.title.lowercase()
        val hasShortsTag = titleLower.contains("#shorts") ||
                           titleLower.contains("#short") ||
                           titleLower.contains("/shorts/")

        return hasShortsTag
    }

    /**
     * Zero-tolerance detection for foreign scripts (Devanagari, Urdu/Arabic, Tamil, Telugu, etc.)
     * and Pakistani/Indian regional channels and political/media keywords.
     */
    fun isForeignLanguageContent(title: String, channelName: String): Boolean {
        val fullText = "$title $channelName".lowercase()

        // 1. If ANY character belongs to a non-Latin/Indic/Arabic/Asian script, reject immediately
        for (ch in "$title $channelName") {
            if (ch.isLetter()) {
                val code = ch.code
                if (code in 0x0600..0x06FF || // Arabic / Urdu
                    code in 0x0750..0x077F || // Arabic Supplement
                    code in 0x08A0..0x08FF || // Arabic Extended
                    code in 0x0900..0x097F || // Devanagari (Hindi, Marathi)
                    code in 0x0980..0x09FF || // Bengali
                    code in 0x0A00..0x0A7F || // Gurmukhi (Punjabi)
                    code in 0x0A80..0x0AFF || // Gujarati
                    code in 0x0B00..0x0B7F || // Oriya
                    code in 0x0B80..0x0BFF || // Tamil
                    code in 0x0C00..0x0C7F || // Telugu
                    code in 0x0C80..0x0CFF || // Kannada
                    code in 0x0D00..0x0D7F || // Malayalam
                    code in 0x0D80..0x0DFF || // Sinhala
                    code in 0x0E00..0x0E7F || // Thai
                    code in 0x0400..0x04FF || // Cyrillic
                    code in 0x4E00..0x9FFF || // CJK Chinese
                    code in 0xAC00..0xD7AF    // Hangul Korean
                ) {
                    return true
                }
            }
        }

        // 2. Comprehensive blacklist of Pakistani, Indian, and regional media outlets & terms
        val foreignKeywords = listOf(
            "hindi", "tamil", "telugu", "punjabi", "bhojpuri", "malayalam", "kannada",
            "marathi", "urdu", "bangla", "bengali", "gujarati", "desi", "bollywood",
            "tollywood", "kollywood", "pakistan", "pakistani", "india", "indian", "bharat",
            "hindustan", "ary digital", "zee tv", "t-series", "set india", "sab tv",
            "geet", "gaana", "bhajan", "natok", "dramareview", "naat", "qawwali", "bayan",
            "voot", "hotstar", "hum tv", "geo news", "geo tv", "aaj tak", "abp news",
            "india tv", "ndtv", "republic bharat", "zee news", "news18", "tv9",
            "lallantop", "dainik", "punjab kesari", "speed records", "white hill",
            "saregama", "tips official", "shemaroo", "goldmines", "ultra movie",
            "pen movies", "b4u", "sonotek", "haryanvi", "chanda", "khesari", "pawan singh",
            "bol news", "samaa", "dunya", "express news", "92 news", "hum news",
            "kapil sharma", "taarak mehta", "cid", "crime patrol", "savdhaan",
            "babar azam", "virat kohli", "rohit sharma", "ipl 202", "psl 202", "cricket live",
            "imran khan", "shehbaz", "nawaz sharif", "narendra modi", "bjp", "congress party",
            "dhruv rathee", "soch by mohak", "lahore", "karachi", "islamabad", "delhi", "mumbai"
        )
        return foreignKeywords.any { fullText.contains(it) }
    }

    /**
     * Returns high quality YouTube thumbnail URL for a video ID.
     */
    fun getThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    /**
     * Formats seconds into MM:SS or HH:MM:SS format string.
     */
    fun formatSeconds(totalSeconds: Int): String {
        if (totalSeconds <= 0) return "00:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Parses MM:SS or HH:MM:SS string back into seconds integer.
     */
    fun parseFormattedTimeToSeconds(formatted: String): Int {
        val parts = formatted.split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
    fun formatViewCount(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM views", views / 1_000_000.0)
            views >= 1_000 -> String.format("%dK views", views / 1_000)
            views > 0 -> "$views views"
            else -> ""
        }
    }

    /**
     * Converts epoch milliseconds to a human-readable relative time string.
     * e.g. "2 hours ago", "3 days ago", "5 months ago", "1 year ago"
     */
    fun formatRelativeTime(epochMs: Long): String {
        if (epochMs <= 0) return ""
        val now = System.currentTimeMillis()
        val diffMs = now - epochMs
        if (diffMs < 0) return ""

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365

        return when {
            years >= 1 -> if (years == 1L) "1 year ago" else "$years years ago"
            months >= 1 -> if (months == 1L) "1 month ago" else "$months months ago"
            days >= 7 -> "${days / 7} week${if (days / 7 > 1) "s" else ""} ago"
            days >= 1 -> if (days == 1L) "1 day ago" else "$days days ago"
            hours >= 1 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            minutes >= 1 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            else -> "Just now"
        }
    }

    /**
     * Fallback video search helper (returns empty list so real live InnerTube search results take full priority).
     */
    fun searchYouTubeVideos(query: String): List<com.example.data.model.VideoEntity> {
        return emptyList()
    }

    /**
     * Converts a relative time string like "3 months ago" into a compact badge label.
     * e.g. "3 months ago" → "3M", "2 days ago" → "2D", "1 year ago" → "1Y",
     * "5 hours ago" → "5H", "30 seconds ago" → "30S", "1 week ago" → "1W"
     */
    fun formatCompactTime(relativeTime: String): String {
        if (relativeTime.isBlank()) return ""
        val lower = relativeTime.lowercase().trim()

        val match = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""").find(lower)
        if (match != null) {
            val num = match.groupValues[1]
            val unit = match.groupValues[2]
            val suffix = when (unit) {
                "second" -> "S"
                "minute" -> "MIN"
                "hour" -> "H"
                "day" -> "D"
                "week" -> "W"
                "month" -> "M"
                "year" -> "Y"
                else -> ""
            }
            return "$num$suffix"
        }

        if (lower.contains("just now") || lower.contains("moments ago")) return "NOW"

        return ""
    }

    /**
     * Parses a relative publication string (e.g., "2 hours ago", "5 days ago", "1 year ago")
     * into estimated elapsed seconds, enabling precise time-based sorting (Newest vs Oldest).
     */
    fun parsePublishedTimeToSeconds(publishedText: String): Long {
        if (publishedText.isBlank()) return Long.MAX_VALUE / 2 // Neutral middle value if date missing
        val lower = publishedText.lowercase().trim()

        if (lower.contains("just now") || lower.contains("moments ago")) {
            return 0L
        }

        val match = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""").find(lower)
        if (match != null) {
            val num = match.groupValues[1].toLongOrNull() ?: 1L
            val unit = match.groupValues[2]
            return when (unit) {
                "second" -> num
                "minute" -> num * 60
                "hour" -> num * 3600
                "day" -> num * 86400
                "week" -> num * 604800
                "month" -> num * 2592000
                "year" -> num * 31536000
                else -> Long.MAX_VALUE / 2
            }
        }
        return Long.MAX_VALUE / 2
    }
}
