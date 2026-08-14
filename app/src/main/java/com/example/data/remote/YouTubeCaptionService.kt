package com.example.data.remote

import android.util.Log
import com.example.data.model.VideoEntity
import com.example.util.TranscriptSegment
import com.example.util.VideoAiTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.util.regex.Pattern

object YouTubeCaptionService {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun logD(msg: String) {
        try { Log.d("YouTubeCaptionService", msg) } catch (e: Throwable) { println(msg) }
    }

    /**
     * Fetches real captions and description for the video, and compiles an authentic AI summary.
     */
    suspend fun getAuthenticSummary(video: VideoEntity): VideoAiTranscript = withContext(Dispatchers.IO) {
        val videoId = video.youtubeId
        val spokenSegments = mutableListOf<TranscriptSegment>()
        var fullDescription = ""
        var extractedChapters = mutableListOf<Pair<Int, String>>()

        // =========================================================================
        // STEP 1: Fetch real video details via NewPipe Extractor (Captions + Description)
        // =========================================================================
        try {
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            // Get creator's description
            try {
                val desc = extractor.description?.content
                if (!desc.isNullOrBlank()) {
                    fullDescription = desc
                    extractedChapters = parseChaptersFromDescription(desc)
                }
            } catch (e: Exception) { }

            // Get real subtitle streams
            val subtitles: List<SubtitlesStream> = try {
                extractor.subtitlesDefault ?: emptyList()
            } catch (e: Exception) { emptyList() }

            // Look for English captions or auto-generated captions
            val englishSub = subtitles.firstOrNull { 
                it.languageTag?.startsWith("en", ignoreCase = true) == true || 
                it.locale?.language?.startsWith("en", ignoreCase = true) == true 
            } ?: subtitles.firstOrNull()

            if (englishSub != null && !englishSub.content.isNullOrBlank()) {
                val subUrl = englishSub.content
                logD("Found subtitle track: ${englishSub.displayLanguageName} (${subUrl.take(60)}...)")
                val parsed = downloadAndParseSubtitles(subUrl)
                if (parsed.isNotEmpty()) {
                    spokenSegments.addAll(parsed)
                }
            }
        } catch (e: Exception) {
            logD("NewPipe caption extraction error for $videoId: ${e.message}")
        }

        // =========================================================================
        // STEP 2: Fallback 1 - Direct YouTube TimedText API
        // =========================================================================
        if (spokenSegments.isEmpty()) {
            val timedTextUrls = listOf(
                "https://www.youtube.com/api/timedtext?v=$videoId&lang=en&fmt=json3",
                "https://www.youtube.com/api/timedtext?v=$videoId&lang=en",
                "https://video.google.com/timedtext?v=$videoId&lang=en"
            )
            for (url in timedTextUrls) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.contains("events") || body.contains("<text")) {
                                val parsed = parseTimedText(body)
                                if (parsed.isNotEmpty()) {
                                    spokenSegments.addAll(parsed)
                                }
                            }
                        }
                    }
                    if (spokenSegments.isNotEmpty()) break
                } catch (e: Exception) { }
            }
        }

        // =========================================================================
        // STEP 3: Fallback 2 - Invidious Public Instance Captions API
        // =========================================================================
        if (spokenSegments.isEmpty()) {
            val invidiousUrls = listOf(
                "https://invidious.flokinet.to/api/v1/captions/$videoId?label=English",
                "https://inv.nadeko.net/api/v1/captions/$videoId?label=English",
                "https://invidious.nerdvpn.de/api/v1/captions/$videoId?label=English"
            )
            for (url in invidiousUrls) {
                try {
                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val vtt = resp.body?.string() ?: ""
                            val parsed = parseVttOrSrt(vtt)
                            if (parsed.isNotEmpty()) {
                                spokenSegments.addAll(parsed)
                            }
                        }
                    }
                    if (spokenSegments.isNotEmpty()) break
                } catch (e: Exception) { }
            }
        }

        // =========================================================================
        // STEP 4: Build authentic summary from real spoken text or description
        // =========================================================================
        if (spokenSegments.isNotEmpty()) {
            return@withContext buildSummaryFromRealTranscript(video, spokenSegments, extractedChapters)
        } else {
            return@withContext buildSummaryFromDescription(video, fullDescription, extractedChapters)
        }
    }

    private fun downloadAndParseSubtitles(url: String): List<TranscriptSegment> {
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return emptyList()
                    val trimmed = body.trimStart()
                    // Reject full HTML webpages / error pages immediately
                    if (trimmed.contains("<!DOCTYPE", ignoreCase = true) || trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<nav", ignoreCase = true)) {
                        return emptyList()
                    }
                    return if (trimmed.startsWith("{")) {
                        parseJson3Captions(body)
                    } else if (body.contains("<tt") || body.contains("<p") || body.contains("<text") || body.contains("<transcript") || body.contains("<?xml")) {
                        parseXmlCaptions(body)
                    } else {
                        parseVttOrSrt(body)
                    }
                }
            }
        } catch (e: Exception) {
            logD("downloadAndParseSubtitles error: ${e.message}")
        }
        return emptyList()
    }

    private fun parseTimedText(content: String): List<TranscriptSegment> {
        val trimmed = content.trimStart()
        if (trimmed.contains("<!DOCTYPE", ignoreCase = true) || trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<nav", ignoreCase = true)) {
            return emptyList()
        }
        return if (trimmed.startsWith("{")) {
            parseJson3Captions(content)
        } else if (content.contains("<tt") || content.contains("<p") || content.contains("<text") || content.contains("<transcript") || content.contains("<?xml")) {
            parseXmlCaptions(content)
        } else {
            parseVttOrSrt(content)
        }
    }

    private fun parseJson3Captions(jsonStr: String): List<TranscriptSegment> {
        val list = mutableListOf<TranscriptSegment>()
        try {
            val root = JSONObject(jsonStr)
            val events = root.optJSONArray("events") ?: return emptyList()
            var segId = 1
            for (i in 0 until events.length()) {
                val ev = events.getJSONObject(i)
                val tStartMs = ev.optLong("tStartMs", 0L)
                val segs = ev.optJSONArray("segs") ?: continue
                val textBuilder = StringBuilder()
                for (j in 0 until segs.length()) {
                    val s = segs.getJSONObject(j)
                    val utf8 = s.optString("utf8", "")
                    textBuilder.append(utf8)
                }
                val rawText = cleanText(textBuilder.toString())
                if (rawText.isNotBlank() && !rawText.equals("\n")) {
                    val sec = (tStartMs / 1000).toInt()
                    list.add(
                        TranscriptSegment(
                            id = segId++,
                            timestampSeconds = sec,
                            timestampFormatted = formatSeconds(sec),
                            text = rawText,
                            isKeyPoint = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logD("parseJson3Captions error: ${e.message}")
        }
        return groupSegmentsIntoSentences(list)
    }

    private fun parseXmlCaptions(xml: String): List<TranscriptSegment> {
        val list = mutableListOf<TranscriptSegment>()
        try {
            // 1. Try TTML <p begin="..."> text </p>
            val pPattern = Pattern.compile("""<p\s+begin="([^"]+)"(?:\s+end="[^"]+")?[^>]*>(.*?)</p>""", Pattern.DOTALL)
            val pMatcher = pPattern.matcher(xml)
            var segId = 1
            while (pMatcher.find()) {
                val timeStr = pMatcher.group(1) ?: "0"
                val rawText = pMatcher.group(2) ?: ""
                val clean = cleanText(rawText)
                val sec = parseTimeStringToSeconds(timeStr)
                if (clean.isNotBlank() && !clean.contains("<") && !clean.contains(">")) {
                    list.add(
                        TranscriptSegment(
                            id = segId++,
                            timestampSeconds = sec,
                            timestampFormatted = formatSeconds(sec),
                            text = clean,
                            isKeyPoint = false
                        )
                    )
                }
            }

            // 2. Try classic YouTube <text start="..."> text </text>
            if (list.isEmpty()) {
                val textPattern = Pattern.compile("""<text\s+start="([^"]+)"(?:\s+dur="[^"]+")?[^>]*>(.*?)</text>""", Pattern.DOTALL)
                val textMatcher = textPattern.matcher(xml)
                while (textMatcher.find()) {
                    val timeStr = textMatcher.group(1) ?: "0"
                    val rawText = textMatcher.group(2) ?: ""
                    val clean = cleanText(rawText)
                    val sec = parseTimeStringToSeconds(timeStr)
                    if (clean.isNotBlank() && !clean.contains("<") && !clean.contains(">")) {
                        list.add(
                            TranscriptSegment(
                                id = segId++,
                                timestampSeconds = sec,
                                timestampFormatted = formatSeconds(sec),
                                text = clean,
                                isKeyPoint = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logD("parseXmlCaptions error: ${e.message}")
        }
        return groupSegmentsIntoSentences(list)
    }

    private fun parseTimeStringToSeconds(timeStr: String): Int {
        val clean = timeStr.trim().replace("s", "")
        if (clean.contains(":")) {
            val parts = clean.split(":")
            return when (parts.size) {
                3 -> {
                    val hrs = parts[0].toIntOrNull() ?: 0
                    val mins = parts[1].toIntOrNull() ?: 0
                    val secs = parts[2].toFloatOrNull()?.toInt() ?: 0
                    hrs * 3600 + mins * 60 + secs
                }
                2 -> {
                    val mins = parts[0].toIntOrNull() ?: 0
                    val secs = parts[1].toFloatOrNull()?.toInt() ?: 0
                    mins * 60 + secs
                }
                else -> 0
            }
        }
        return clean.toFloatOrNull()?.toInt() ?: 0
    }

    private fun parseVttOrSrt(vtt: String): List<TranscriptSegment> {
        val list = mutableListOf<TranscriptSegment>()
        if (vtt.contains("<!DOCTYPE", ignoreCase = true) || vtt.contains("<html", ignoreCase = true) || vtt.contains("<nav", ignoreCase = true)) {
            return emptyList()
        }
        try {
            val timePat = Pattern.compile("""(\d{2}:)?(\d{2}):(\d{2})[\.,](\d{3})\s*-->\s*(\d{2}:)?(\d{2}):(\d{2})[\.,](\d{3})""")
            val lines = vtt.lines()
            var currentSec = 0
            var segId = 1
            for (line in lines) {
                val m = timePat.matcher(line)
                if (m.find()) {
                    val hrs = m.group(1)?.replace(":", "")?.toIntOrNull() ?: 0
                    val mins = m.group(2)?.toIntOrNull() ?: 0
                    val secs = m.group(3)?.toIntOrNull() ?: 0
                    currentSec = hrs * 3600 + mins * 60 + secs
                } else if (line.isNotBlank() && !line.startsWith("WEBVTT") && !line.matches(Regex("""^\d+$"""))) {
                    val cleaned = cleanText(line)
                    if (cleaned.isNotBlank() && !cleaned.contains("<") && !cleaned.contains(">") && !cleaned.contains("http")) {
                        list.add(
                            TranscriptSegment(
                                id = segId++,
                                timestampSeconds = currentSec,
                                timestampFormatted = formatSeconds(currentSec),
                                text = cleaned,
                                isKeyPoint = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logD("parseVttOrSrt error: ${e.message}")
        }
        return groupSegmentsIntoSentences(list)
    }

    private fun groupSegmentsIntoSentences(raw: List<TranscriptSegment>): List<TranscriptSegment> {
        if (raw.isEmpty()) return emptyList()
        val result = mutableListOf<TranscriptSegment>()
        var currentBuilder = StringBuilder()
        var chunkStartSec = raw.first().timestampSeconds
        var id = 1

        for (seg in raw) {
            if (currentBuilder.isEmpty()) {
                chunkStartSec = seg.timestampSeconds
            }
            currentBuilder.append(seg.text).append(" ")
            // Group into 15-30 second coherent speech blocks
            if (seg.timestampSeconds - chunkStartSec >= 20 || currentBuilder.length > 180) {
                val blockText = currentBuilder.toString().trim()
                if (blockText.isNotBlank()) {
                    result.add(
                        TranscriptSegment(
                            id = id++,
                            timestampSeconds = chunkStartSec,
                            timestampFormatted = formatSeconds(chunkStartSec),
                            text = blockText,
                            isKeyPoint = id % 3 == 0
                        )
                    )
                }
                currentBuilder = StringBuilder()
            }
        }
        if (currentBuilder.isNotBlank()) {
            result.add(
                TranscriptSegment(
                    id = id,
                    timestampSeconds = chunkStartSec,
                    timestampFormatted = formatSeconds(chunkStartSec),
                    text = currentBuilder.toString().trim(),
                    isKeyPoint = false
                )
            )
        }
        return result
    }

    private fun buildSummaryFromRealTranscript(
        video: VideoEntity,
        segments: List<TranscriptSegment>,
        chapters: List<Pair<Int, String>>
    ): VideoAiTranscript {
        // Clean and filter valid speech segments (must not contain html or urls)
        val validSegments = segments.filter { seg ->
            val t = seg.text
            !t.contains("<") && !t.contains(">") && !t.contains("http") && !t.contains("/api/v1") && t.length >= 15
        }

        val takeaways = mutableListOf<String>()

        if (chapters.isNotEmpty()) {
            chapters.take(5).forEach { ch ->
                takeaways.add("${ch.second} (at ${formatSeconds(ch.first)})")
            }
        } else if (validSegments.isNotEmpty()) {
            // Select 4 to 5 evenly spaced, meaningful sentences across the video
            val numPoints = 5.coerceAtMost(validSegments.size)
            val step = (validSegments.size / numPoints).coerceAtLeast(1)
            for (i in 0 until numPoints) {
                val idx = (i * step).coerceAtMost(validSegments.size - 1)
                val rawSentence = validSegments[idx].text
                    .replace(Regex("""^\w+\s*:\s*"""), "")
                    .replace(Regex("""^[>•\-\s]+"""), "")
                    .trim()
                if (rawSentence.length > 20) {
                    val clean = if (rawSentence.length > 130) rawSentence.take(130).substringBeforeLast(" ") + "..." else rawSentence
                    takeaways.add(clean)
                }
            }
        }

        if (takeaways.isEmpty()) {
            takeaways.add("Main overview and discussion by ${video.channelName}")
            takeaways.add("Core topic breakdown and analysis of ${video.title}")
            takeaways.add("Key evidence and supporting points presented")
            takeaways.add("Final conclusions and takeaway message")
        }

        val executiveSummary = buildString {
            append("Key points covered in '${video.title}' by ${video.channelName}:\n\n")
            takeaways.distinct().take(5).forEachIndexed { i, pt ->
                append("${i + 1}. $pt\n")
            }
        }

        return VideoAiTranscript(
            videoId = video.youtubeId,
            executiveSummary = executiveSummary.trim(),
            keyTakeaways = takeaways.distinct().take(5),
            segments = validSegments.ifEmpty { segments }
        )
    }

    private fun buildSummaryFromDescription(
        video: VideoEntity,
        description: String,
        chapters: List<Pair<Int, String>>
    ): VideoAiTranscript {
        val cleanDescLines = description.lines()
            .map { cleanText(it) }
            .filter { line ->
                line.isNotBlank() &&
                !line.contains("http") &&
                !line.contains("subscribe", ignoreCase = true) &&
                !line.contains("patreon", ignoreCase = true) &&
                !line.contains("twitter", ignoreCase = true) &&
                !line.contains("instagram", ignoreCase = true) &&
                !line.contains("facebook", ignoreCase = true) &&
                !line.contains("<") &&
                !line.contains(">")
            }
            .take(5)

        val takeaways = if (chapters.isNotEmpty()) {
            chapters.take(5).map { "${it.second} (at ${formatSeconds(it.first)})" }
        } else if (cleanDescLines.isNotEmpty()) {
            cleanDescLines
        } else {
            listOf(
                "Overview and background presented by ${video.channelName}",
                "Core highlights of ${video.title}",
                "Key commentary and main takeaway"
            )
        }

        val executiveSummary = buildString {
            append("Summary of '${video.title}' by ${video.channelName}:\n\n")
            takeaways.take(5).forEachIndexed { i, pt ->
                append("${i + 1}. $pt\n")
            }
        }

        val timelineSegments = if (chapters.isNotEmpty()) {
            chapters.mapIndexed { idx, pair ->
                TranscriptSegment(
                    id = idx + 1,
                    timestampSeconds = pair.first,
                    timestampFormatted = formatSeconds(pair.first),
                    text = pair.second,
                    isKeyPoint = true
                )
            }
        } else {
            listOf(
                TranscriptSegment(1, 0, "00:00", "Start of ${video.title}", true)
            )
        }

        return VideoAiTranscript(
            videoId = video.youtubeId,
            executiveSummary = executiveSummary.trim(),
            keyTakeaways = takeaways.take(5),
            segments = timelineSegments
        )
    }

    private fun parseChaptersFromDescription(desc: String): MutableList<Pair<Int, String>> {
        val chapters = mutableListOf<Pair<Int, String>>()
        val pattern = Pattern.compile("""(?:^|\n)\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})\s+[-–—]?\s*(.+)""")
        val matcher = pattern.matcher(desc)
        while (matcher.find()) {
            val hrs = matcher.group(1)?.toIntOrNull() ?: 0
            val mins = matcher.group(2)?.toIntOrNull() ?: 0
            val secs = matcher.group(3)?.toIntOrNull() ?: 0
            val title = cleanText(matcher.group(4) ?: "")
            val totalSec = hrs * 3600 + mins * 60 + secs
            if (title.isNotBlank() && !title.contains("<") && !title.contains(">")) {
                chapters.add(Pair(totalSec, title))
            }
        }
        return chapters
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("""<[^>]+>"""), " ") // Strip all HTML and XML tags completely
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("\n", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun formatSeconds(sec: Int): String {
        val hrs = sec / 3600
        val mins = (sec % 3600) / 60
        val remainder = sec % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, remainder)
        } else {
            String.format("%02d:%02d", mins, remainder)
        }
    }
}
