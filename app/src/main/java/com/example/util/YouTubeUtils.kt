package com.example.util

import com.example.data.model.VideoEntity
import java.util.regex.Pattern

object YouTubeUtils {
    // Regex pattern matching various YouTube URL formats
    private val YOUTUBE_ID_PATTERN: Pattern = Pattern.compile(
        "(?:youtube(?:-nocookie)?\\.com/(?:[^/\\n\\s]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/|youtube\\.com/shorts/)([a-zA-Z0-9_-]{11})",
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
     * Accurately determines whether a video entity is a YouTube Short.
     * Enforces strict duration limits (<= 90s) so long videos are never misclassified as Shorts,
     * and ensures true Shorts (<= 60s or marked with #shorts) play in the Shorts player.
     */
    fun isShortVideo(video: VideoEntity): Boolean {
        val durationSec = parseFormattedTimeToSeconds(video.durationText)

        // Rule 1: Any video longer than 90 seconds (1m 30s) CANNOT be a Short
        if (durationSec > 90) return false

        // Rule 2: Explicitly tagged category "Shorts" with short duration (<= 90s or unknown)
        if (video.category.equals("Shorts", ignoreCase = true)) return true

        // Rule 3: Has explicit hashtag #shorts in title
        if (video.title.contains("#shorts", ignoreCase = true)) return true

        // Rule 4: Explicit short duration between 1 and 60 seconds (standard YouTube Short length)
        if (durationSec in 1..60) return true

        return false
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
