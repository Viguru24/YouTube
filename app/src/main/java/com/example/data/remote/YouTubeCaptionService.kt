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
    suspend fun getAuthenticSummary(video: VideoEntity, context: android.content.Context? = null): VideoAiTranscript = withContext(Dispatchers.IO) {
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
        // STEP 4: Real LLM Summarizer (Gemini / Groq)
        // =========================================================================
        if (context != null && spokenSegments.isNotEmpty()) {
            val fullTranscriptText = spokenSegments.joinToString(" ") { it.text }
            val llmSummary = AiSummarizerClient.generateLlmSummary(
                context = context,
                videoId = video.youtubeId,
                title = video.title,
                channelName = video.channelName,
                rawTranscriptText = fullTranscriptText,
                spokenSegments = spokenSegments
            )
            if (llmSummary != null) {
                return@withContext llmSummary
            }
        }

        // =========================================================================
        // STEP 5: Fallback Cleaned Synthesizer
        // =========================================================================
        if (spokenSegments.isNotEmpty()) {
            return@withContext buildSummaryFromRealTranscript(video, spokenSegments, extractedChapters)
        } else {
            return@withContext buildSummaryFromDescription(video, fullDescription, extractedChapters)
        }
    }

    /**
     * Fetches real timed captions (subtitles) for Closed Captions (CC) playback.
     */
    suspend fun fetchTimedCaptions(videoId: String): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val spokenSegments = mutableListOf<TranscriptSegment>()
        // 1. NewPipe Extractor
        try {
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val subtitles: List<SubtitlesStream> = try {
                extractor.subtitlesDefault ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val englishSub = subtitles.firstOrNull { 
                it.languageTag?.startsWith("en", ignoreCase = true) == true || 
                it.locale?.language?.startsWith("en", ignoreCase = true) == true 
            } ?: subtitles.firstOrNull()

            if (englishSub != null && !englishSub.content.isNullOrBlank()) {
                val parsed = downloadAndParseSubtitles(englishSub.content)
                if (parsed.isNotEmpty()) {
                    spokenSegments.addAll(parsed)
                }
            }
        } catch (e: Exception) { }

        // 2. Direct TimedText API fallback
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

        // 3. Invidious / Piped VTT captions fallback
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
        spokenSegments
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

    private val OUTRO_BOILERPLATE_REGEX = Regex(
        "(?i)\\b(like and subscribe|subscribe to (the|my|our) channel|leave a comment|let me know in the comments|hit (the|that) (like|subscribe|bell) button|thanks for watching|thank you for watching|see you (next time|in the next (video|one))|until next time|this has been|peace out|don't forget to like|patreon\\.com|link in the description|check out (my|our) (merch|website|store|patreon)|sponsor(ed)? by|follow (me|us) on (twitter|instagram|tiktok)|ring the (bell|notification)|give (it|this) a (like|thumbs up)|help(s)? (us|me) out|bye\\.?)\\b"
    )

    private val INTRO_GREETING_REGEX = Regex(
        "(?i)^(\\s*(hey|hi|hello|what's up|welcome back|welcome to|good morning|good evening|yo)\\b[^.?!]*[.?!]?\\s*)+"
    )

    private val STOPWORDS = setOf(
        "the", "and", "that", "this", "with", "from", "have", "they", "will", "what",
        "been", "were", "there", "their", "about", "which", "would", "these", "other",
        "into", "more", "some", "could", "then", "them", "also", "just", "like", "know",
        "your", "when", "than", "over", "even", "most", "only", "come", "very", "much",
        "really", "going", "think", "said", "make", "well", "look", "want", "give", "take",
        "video", "youtube", "channel", "thing", "things", "stuff", "guys", "today", "show"
    )

    private data class ScoredSentence(
        val timestampSeconds: Int,
        val text: String,
        val score: Float
    )

    private fun assembleAndScoreSentences(
        segments: List<TranscriptSegment>,
        title: String
    ): List<ScoredSentence> {
        val titleWords = title.lowercase().split(Regex("""\W+""")).filter { it.length > 2 && it !in STOPWORDS }.toSet()
        val sentences = mutableListOf<ScoredSentence>()

        val currentText = StringBuilder()
        var currentStartSec = 0
        var segmentCount = 0

        for (seg in segments) {
            val raw = seg.text.trim()
            if (raw.isEmpty() || raw.contains("<") || raw.contains(">") || raw.contains("http")) continue

            if (currentText.isEmpty()) {
                currentStartSec = seg.timestampSeconds
            }

            if (currentText.isNotEmpty() && !currentText.endsWith(" ")) {
                currentText.append(" ")
            }
            currentText.append(raw)
            segmentCount++

            val str = currentText.toString()
            val hasTerminalPunctuation = str.endsWith(".") || str.endsWith("?") || str.endsWith("!")
            val isLongEnough = str.length >= 100 || segmentCount >= 4

            if (hasTerminalPunctuation || isLongEnough) {
                var clean = str
                    .replace(INTRO_GREETING_REGEX, "")
                    .replace(Regex("(?i)\\b(uh|um|you know|i mean|like I said|and I mean|so basically|kind of|sort of)\\b,?\\s*"), "")
                    .replace(Regex("(?i)\\b(all right,? we're going to do a little bit of a)\\b\\s*"), "")
                    .replace(Regex("""^\s*[>•\-\s]+"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                if (clean.isNotEmpty()) {
                    // Ensure capitalization
                    clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    if (!clean.endsWith(".") && !clean.endsWith("?") && !clean.endsWith("!")) {
                        clean += "."
                    }

                    // Score sentence based on content depth & lack of boilerplate
                    val isBoilerplate = OUTRO_BOILERPLATE_REGEX.containsMatchIn(clean)
                    if (!isBoilerplate && clean.length >= 35) {
                        val words = clean.lowercase().split(Regex("""\W+""")).filter { it.length > 2 }
                        val contentWordCount = words.count { it !in STOPWORDS }
                        val titleMatches = words.count { it in titleWords }

                        var score = (contentWordCount * 2.0f) + (titleMatches * 8.0f)

                        // Semantic insight markers boost
                        if (Regex("(?i)\\b(because|explains|discovered|revealed|the reason|the truth|problem is|result of|in reality|turns out|crucial|significant|major factor|evidence shows|analysis indicates|conclusion is|important to realize|shows that|demonstrates|key point)\\b").containsMatchIn(clean)) {
                            score += 25.0f
                        }

                        sentences.add(ScoredSentence(currentStartSec, clean, score))
                    }
                }
                currentText.clear()
                segmentCount = 0
            }
        }
        return sentences
    }

    private fun buildSummaryFromRealTranscript(
        video: VideoEntity,
        segments: List<TranscriptSegment>,
        chapters: List<Pair<Int, String>>
    ): VideoAiTranscript {
        val host = video.channelName.ifBlank { "Creator / Host" }
        val scoredSentences = assembleAndScoreSentences(segments, video.title)

        // 1. Topic & Premise: Highest scoring informative sentence in first 20% of video
        val earlySentences = scoredSentences.take((scoredSentences.size * 0.25).toInt().coerceAtLeast(3))
        val topicPremise = earlySentences.maxByOrNull { it.score }?.text
            ?: "In this video, $host analyzes and breaks down '${video.title}' with in-depth commentary and evidence."

        // 2. Key Discussion Highlights (3 to 4 chronological acts across the video body)
        val discussionPoints = mutableListOf<String>()
        if (chapters.isNotEmpty()) {
            chapters.take(4).forEach { ch ->
                discussionPoints.add("${ch.second} (at ${formatSeconds(ch.first)})")
            }
        } else if (scoredSentences.size >= 4) {
            val bodySentences = scoredSentences.drop((scoredSentences.size * 0.15).toInt()).dropLast((scoredSentences.size * 0.15).toInt())
            if (bodySentences.isNotEmpty()) {
                val chunkSize = (bodySentences.size / 3).coerceAtLeast(1)
                for (chunk in bodySentences.chunked(chunkSize).take(3)) {
                    val best = chunk.maxByOrNull { it.score }
                    if (best != null && best.text != topicPremise) {
                        discussionPoints.add(best.text)
                    }
                }
            }
        }

        if (discussionPoints.isEmpty()) {
            discussionPoints.add("Background context and key facts establishing the premise of '${video.title}'.")
            discussionPoints.add("Primary evidence, core arguments, and critical analysis presented by $host.")
            discussionPoints.add("Broader implications, key takeaways, and real-world impact.")
        }

        // 3. Substantive Conclusion & Wrap-up: Scan substantive ending window (70% to 92%), strictly avoiding outro
        val lateWindowStart = (scoredSentences.size * 0.65).toInt().coerceAtMost(scoredSentences.size - 1)
        val lateWindowEnd = (scoredSentences.size * 0.95).toInt().coerceAtLeast(lateWindowStart + 1)
        val endingSentences = scoredSentences.subList(lateWindowStart.coerceAtLeast(0), lateWindowEnd.coerceAtMost(scoredSentences.size))

        val bestConclusion = endingSentences
            .filter { it.text != topicPremise && !discussionPoints.contains(it.text) }
            .maxByOrNull { it.score }?.text

        val conclusion = bestConclusion
            ?: scoredSentences.lastOrNull { !discussionPoints.contains(it.text) }?.text
            ?: "Final analysis and concluding perspectives presented by $host on '${video.title}'."

        val executiveSummary = buildString {
            append("🎙️ Host: $host\n\n")
            append("🎯 Topic & Premise:\n$topicPremise\n\n")
            append("💬 Key Discussion Points:\n")
            discussionPoints.distinct().forEach { pt ->
                append("• $pt\n")
            }
            append("\n🏁 Conclusion:\n$conclusion")
        }

        return VideoAiTranscript(
            videoId = video.youtubeId,
            hostName = host,
            topicPremise = topicPremise,
            discussionPoints = discussionPoints.distinct(),
            conclusion = conclusion,
            executiveSummary = executiveSummary.trim(),
            keyTakeaways = discussionPoints.distinct(),
            segments = segments
        )
    }

    private fun buildSummaryFromDescription(
        video: VideoEntity,
        description: String,
        chapters: List<Pair<Int, String>>
    ): VideoAiTranscript {
        val host = video.channelName.ifBlank { "Creator / Host" }

        val cleanDescLines = description.lines()
            .map { cleanText(it) }
            .filter { line ->
                line.isNotBlank() &&
                line.length >= 25 &&
                !line.contains("http") &&
                !OUTRO_BOILERPLATE_REGEX.containsMatchIn(line) &&
                !line.contains("<") &&
                !line.contains(">")
            }

        val topicPremise = cleanDescLines.firstOrNull()
            ?: "Official release of '${video.title}' presented by $host in the ${video.category} category."

        val discussionPoints = if (chapters.isNotEmpty()) {
            chapters.take(4).map { "${it.second} (at ${formatSeconds(it.first)})" }
        } else if (cleanDescLines.size > 2) {
            cleanDescLines.drop(1).take(3)
        } else {
            listOf(
                "Introduction, background context, and core breakdown by $host.",
                "Main analysis, demonstration, and perspectives on '${video.title}'.",
                "Key takeaways and strategic impact discussed in this video."
            )
        }

        val conclusion = cleanDescLines.drop(1).lastOrNull { it != topicPremise && !discussionPoints.contains(it) }
            ?: "Final conclusions and summary perspectives by $host on '${video.title}'."

        val executiveSummary = buildString {
            append("🎙️ Host: $host\n\n")
            append("🎯 Topic & Premise:\n$topicPremise\n\n")
            append("💬 Key Discussion Points:\n")
            discussionPoints.forEach { pt ->
                append("• $pt\n")
            }
            append("\n🏁 Conclusion:\n$conclusion")
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
            hostName = host,
            topicPremise = topicPremise,
            discussionPoints = discussionPoints,
            conclusion = conclusion,
            executiveSummary = executiveSummary.trim(),
            keyTakeaways = discussionPoints,
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
