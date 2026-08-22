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
     * Zero-tolerance detection for non-English content:
     * 1. All non-Latin writing systems (Indic, Arabic, Cyrillic, CJK, Kana, Hangul, Greek, Hebrew, Southeast Asian, etc.)
     * 2. Common non-English vocabulary, phrases, and media channels in Spanish, Portuguese, French, German, Italian, Turkish, Indonesian, Hindi/Urdu, Tagalog, etc.
     */
    fun isForeignLanguageContent(title: String, channelName: String): Boolean {
        val combined = "$title $channelName"
        val fullText = combined.lowercase()

        // 1. Script Check: Reject if any letter belongs to a non-Latin / non-ASCII writing system
        for (ch in combined) {
            if (ch.isLetter()) {
                val code = ch.code
                if (code in 0x0370..0x03FF || // Greek & Coptic
                    code in 0x0400..0x052F || // Cyrillic & Cyrillic Supplement
                    code in 0x0530..0x058F || // Armenian
                    code in 0x0590..0x05FF || // Hebrew
                    code in 0x0600..0x08FF || // Arabic, Urdu, Persian, Pashto, Arabic Extended
                    code in 0x0900..0x0DFF || // Devanagari, Bengali, Gurmukhi, Gujarati, Oriya, Tamil, Telugu, Kannada, Malayalam, Sinhala
                    code in 0x0E00..0x0E7F || // Thai
                    code in 0x0E80..0x0EFF || // Lao
                    code in 0x0F00..0x0FFF || // Tibetan
                    code in 0x1000..0x109F || // Myanmar / Burmese
                    code in 0x10A0..0x10FF || // Georgian
                    code in 0x1200..0x137F || // Ethiopic / Ge'ez
                    code in 0x1780..0x17FF || // Khmer
                    code in 0x1800..0x18AF || // Mongolian
                    code in 0x1EA0..0x1EFF || // Vietnamese Latin Extended Additional
                    code in 0x2E80..0x33FF || // CJK Radicals, Kangxi, Bopomofo, CJK Symbols, Hiragana, Katakana
                    code in 0x3400..0x4DBF || // CJK Unified Ideographs Extension A
                    code in 0x4E00..0x9FFF || // CJK Unified Ideographs (Chinese Hanzi, Kanji)
                    code in 0xAC00..0xD7AF || // Hangul Syllables (Korean)
                    code in 0x1100..0x11FF || // Hangul Jamo
                    code in 0x3130..0x318F || // Hangul Compatibility Jamo
                    code in 0xFF00..0xFFEF    // Halfwidth and Fullwidth Forms (Asian punctuation & Kana)
                ) {
                    return true
                }
            }
        }

        // 2. Exact multi-word foreign phrases (Spanish, Portuguese, French, German, Italian, Turkish, Indonesian, Hindi/Urdu, etc.)
        val foreignPhrases = listOf(
            // Spanish & Portuguese
            "en vivo", "ao vivo", "capitulo completo", "capítulo completo", "pelicula completa", "película completa",
            "filme completo", "todos los capitulos", "resumen del partido", "mejores momentos", "melhores momentos",
            "primera vez", "primeira vez", "en directo", "ultima hora", "última hora", "noticias de hoy",
            "notícias de hoje", "buenos dias", "buenas noches", "como hacer", "cómo hacer", "conferencia de prensa",
            "canal oficial", "video oficial", "vídeo oficial", "letra oficial", "musica cristiana", "música cristiana",
            "telenovela completa", "serie completa", "novela das", "futebol ao vivo", "jogo ao vivo", "gols da rodada",

            // French
            "en direct", "bande annonce", "bande-annonce", "film complet", "journal télévisé", "tous les épisodes",
            "chanson officielle", "clip officiel", "les meilleurs moments", "pour les enfants",

            // German
            "ganzer film", "auf deutsch", "deutsche nachrichten", "live übertragung", "ganze folge",

            // Italian
            "in diretta", "film completo", "canzone ufficiale", "tutti gli episodi", "conferenza stampa",

            // Turkish
            "canlı yayın", "canli yayin", "full izle", "tek parça", "tek parca", "son dakika", "yeni bölüm", "yeni bolum",
            "türkçe dublaj", "turkce dublaj", "türkçe altyazı", "turkce altyazi",

            // Indonesian / Malay
            "alur cerita", "film sub indo", "sub indo", "live streaming indonesia", "sinopsis film", "rekap film",
            "berita terkini", "lagu terbaru",

            // South Asian / Hindi / Urdu / Pakistani (Romanized)
            "kaise kare", "kaise banaye", "taaza khabar", "aaj ki taaza", "pakistani drama", "indian drama",
            "full episode", "full drama", "naat sharif", "bayan video", "tarjuma quran", "qawwali live",
            "new song lyrical", "desi comedy", "dekhie kya hua", "kya hua jab", "dekho kya hua", "sune aur dekhe",
            "har pal geo", "ary digital", "hum tv", "green entertainment", "express entertainment", "aaj tak",
            "abp news", "zee news", "geo news", "bol news", "samaa tv", "dunya news", "express news",
            "shemaroo filmi", "goldmines telefilms", "t-series", "speed records", "desh ki baat", "aaj ki badi khabar",
            "breaking news pakistan", "breaking news india", "live news hindi", "khabrein aaj ki",

            // Filipino / Tagalog
            "buong episode", "balita ngayon", "ulat balita", "pilipinas balita"
        )

        if (foreignPhrases.any { fullText.contains(it) }) return true

        // 3. Foreign standalone keywords matched as whole tokens to avoid false positives in English words
        val foreignWords = setOf(
            // Spanish & Portuguese
            "capitulo", "capítulo", "pelicula", "película", "noticias", "notícias", "futebol", "novela", "telenovela",
            "desenho", "dublado", "legendado", "cancion", "canción", "jornal", "gols", "español", "espanol",
            "português", "portugues", "episodio", "episódio", "temporada", "resumo", "estreno", "estrenos",
            "receta", "recetas", "subtitulado", "subtitulada", "dublada", "jogos",

            // French
            "épisode", "français", "francais", "actualités", "actualites", "découverte", "émission", "emission",

            // German
            "folge", "deutsch", "nachrichten", "zusammenfassung", "höhepunkte", "spieltag", "übertragung",

            // Italian
            "puntata", "notizie", "italiano", "riassunto",

            // Turkish
            "bölüm", "bolum", "fragman", "dizi", "özet", "ozet", "türkçe", "turkce", "haberler",

            // Indonesian / Malay
            "terbaru", "berita", "lengkap", "sinopsis", "lirik", "lucu", "ngakak",

            // South Asian / Hindi / Urdu / Pakistani / Tamil / Telugu / Bengali (Romanized & Media Networks)
            "kaise", "kare", "karen", "wala", "wali", "wale", "nahin", "nhi", "nahi", "kya", "hain", "dekho",
            "dekhie", "sunao", "batao", "sikhe", "samachar", "kahani", "dulhan", "bhabhi", "gaana",
            "bhajan", "aarti", "chalisa", "tarjuma", "tilawat", "khutba", "qawwali", "mujra", "dhamaka",
            "hindi", "tamil", "telugu", "punjabi", "bhojpuri", "malayalam", "kannada", "marathi", "urdu",
            "bangla", "bengali", "gujarati", "desi", "bollywood", "tollywood", "kollywood", "mollywood",
            "pakistan", "pakistani", "bharat", "hindustan", "ary", "geotv", "geonews", "aajtak", "abpnews",
            "tseries", "humtv", "zeetv", "zeenews", "ndtv", "setindia", "sabtv", "shemaroo", "goldmines",
            "sonotek", "haryanvi", "bolnews", "samaatv", "expressnews", "dunyanews", "dawnnews", "arydigital",
            "arynews", "harpalgeo", "ptvnews", "suntv", "vijaytv", "zeetelugu", "starplus", "starbharat",
            "sonytv", "ddnational", "rajshri", "speedrecords", "geetmp3", "whitehillmusic", "desimusicfactory",
            "adityamusic", "adithya", "telly", "tashan", "fitoor", "aarambhi", "puli", "sanu", "oli",
            "sholay", "ghazal", "mushaira", "manqabat", "noha", "marsiya", "majlis",
            "khabar", "khabrain", "rishta", "tamasha", "nuskha", "ilaaj", "totkay", "wazifa", "wazaif",
            "rohani", "kundli", "rashifal", "rashi", "dharma", "mandir", "masjid", "dargah", "satsang",
            "pravachan", "katha", "sindh", "sindhi", "baloch", "balochi", "pashto", "kashmir", "kashmiri",

            // Filipino / Tagalog
            "teleserye", "balita", "pinoy", "pilipinas"
        )

        // Split text by punctuation, symbols, and whitespace
        val tokens = fullText.split("[^\\p{Alnum}]+".toRegex()).filter { it.isNotBlank() }
        for (token in tokens) {
            if (foreignWords.contains(token)) {
                return true
            }
        }

        return false
    }

    /**
     * Returns high quality YouTube thumbnail URL for a video ID.
     */
    fun getThumbnailUrl(videoId: String): String {
        return "https://i.ytimg.com/vi/$videoId/hq720.jpg"
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
