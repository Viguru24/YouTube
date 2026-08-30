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
        if (context != null) {
            val fullTranscriptText = spokenSegments.joinToString(" ") { it.text }
            val llmSummary = AiSummarizerClient.generateLlmSummary(
                context = context,
                videoId = video.youtubeId,
                title = video.title,
                channelName = video.channelName,
                rawTranscriptText = fullTranscriptText.ifBlank { fullDescription },
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

    private val DANGLING_END_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "because", "of", "in", "to", "for", "with", "at", "by", "from",
        "that", "this", "which", "as", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
        "having", "so", "if", "then", "than", "when", "while", "where", "into", "onto", "about", "like", "such",
        "their", "his", "her", "its", "our", "your", "my", "we", "they", "he", "she", "it", "you", "i"
    )

    private val SPONSOR_PLUGS_REGEX = Regex(
        "(?i)\\b(sponsored by|sponsor of today's video|use code|discount code|link in description|expressvpn|nordvpn|surfshark|betterhelp|audible|manscaped|squarespace|raycon|ag1|athletic greens|patreon\\.com)\\b"
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
        var lastSegEndSec = 0

        for (i in segments.indices) {
            val seg = segments[i]
            val raw = seg.text.trim()
            if (raw.isEmpty() || raw.contains("<") || raw.contains(">") || raw.contains("http")) continue

            if (currentText.isEmpty()) {
                currentStartSec = seg.timestampSeconds
            }

            val pauseGap = if (lastSegEndSec > 0) (seg.timestampSeconds - lastSegEndSec) else 0
            lastSegEndSec = seg.timestampSeconds

            if (currentText.isNotEmpty() && !currentText.endsWith(" ")) {
                currentText.append(" ")
            }
            currentText.append(raw)

            val currentStr = currentText.toString().trim()
            val words = currentStr.split(Regex("""\s+"""))
            val lastWord = words.lastOrNull()?.lowercase()?.replace(Regex("""[^a-z]"""), "") ?: ""

            val hasPunctuation = currentStr.endsWith(".") || currentStr.endsWith("?") || currentStr.endsWith("!")
            val isNaturalBoundary = (pauseGap >= 2 && currentStr.length >= 70) || (hasPunctuation && currentStr.length >= 60) || currentStr.length >= 160
            val isDangling = DANGLING_END_WORDS.contains(lastWord)

            if (isNaturalBoundary && !isDangling && words.size >= 8) {
                var clean = currentStr
                    .replace(INTRO_GREETING_REGEX, "")
                    .replace(Regex("(?i)\\b(uh|um|you know|i mean|like I said|and I mean|so basically|kind of|sort of)\\b,?\\s*"), "")
                    .replace(Regex("(?i)\\b(all right,? we're going to do a little bit of a)\\b\\s*"), "")
                    .replace(Regex("""^\s*[>•\-\s]+"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                // Strip leading conjunctions if starting standalone sentence
                clean = clean.replace(Regex("""^(?i)(and|but|so|because|or|also)\s+"""), "")

                if (clean.isNotEmpty() && clean.length >= 40) {
                    clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    if (!clean.endsWith(".") && !clean.endsWith("?") && !clean.endsWith("!")) {
                        clean += "."
                    }

                    val isBoilerplate = OUTRO_BOILERPLATE_REGEX.containsMatchIn(clean) || SPONSOR_PLUGS_REGEX.containsMatchIn(clean)
                    if (!isBoilerplate) {
                        val sentenceWords = clean.lowercase().split(Regex("""\W+""")).filter { it.length > 2 }
                        val contentWordCount = sentenceWords.count { it !in STOPWORDS }
                        val titleMatches = sentenceWords.count { it in titleWords }

                        var score = (contentWordCount * 2.5f) + (titleMatches * 9.0f)

                        // Semantic significance signals
                        if (Regex("(?i)\\b(because|explains|discovered|revealed|the reason|the truth|problem is|result of|in reality|turns out|crucial|significant|major factor|evidence shows|analysis indicates|conclusion is|important to realize|shows that|demonstrates|key point|breakthrough|announced|engineered)\\b").containsMatchIn(clean)) {
                            score += 25.0f
                        }

                        sentences.add(ScoredSentence(currentStartSec, clean, score))
                    }
                }
                currentText.clear()
            }
        }
        return sentences
    }

    private fun buildSummaryFromRealTranscript(
        video: VideoEntity,
        segments: List<TranscriptSegment>,
        chapters: List<Pair<Int, String>>
    ): VideoAiTranscript {
        val host = video.channelName.ifBlank { "Creator" }
        val rawText = segments.joinToString(" ") { it.text }

        // Clean spoken fillers and disfluencies
        val cleanedText = cleanSpokenDisfluencies(rawText)
        val sentences = cleanedText.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 25 && !isBoilerplateOutro(it) }

        // Synthesize Topic & Executive Premise
        val topicPremise = if (sentences.isNotEmpty()) {
            val introSentences = sentences.take((sentences.size * 0.25).toInt().coerceIn(1, 4))
            val premiseCore = introSentences.filter { !it.contains("subscribe", ignoreCase = true) && !it.contains("channel", ignoreCase = true) }
                .maxByOrNull { it.length } ?: sentences.first()
            "In this video, $host breaks down '${video.title}'. The discussion explores: ${premiseCore.replace(Regex("^(so|and|now|well|today|in this video)\\s+", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.uppercase() }}"
        } else {
            "In this video, $host analyzes '${video.title}', reviewing key developments, practical methods, and core insights."
        }

        // Synthesize Key Insights & Chapters
        val keyInsights = mutableListOf<String>()
        if (chapters.isNotEmpty()) {
            chapters.forEach { ch ->
                keyInsights.add("📌 **${ch.second}** (at ${formatSeconds(ch.first)})")
            }
        }

        // Extract and synthesize core topical takeaways
        val middleSentences = sentences.drop((sentences.size * 0.15).toInt()).dropLast((sentences.size * 0.15).toInt())
        val informativeSentences = middleSentences
            .filter { s -> s.length in 40..220 && !s.contains("sponsor", ignoreCase = true) }
            .distinctBy { it.take(20) }

        if (informativeSentences.isNotEmpty()) {
            val step = (informativeSentences.size / 4).coerceAtLeast(1)
            for (i in 0 until 4) {
                val idx = (i * step).coerceAtMost(informativeSentences.size - 1)
                val s = informativeSentences[idx]
                    .replace(Regex("^(so|and|now|well|basically|like|you know)\\s+", RegexOption.IGNORE_CASE), "")
                    .replaceFirstChar { it.uppercase() }
                val bullet = "💡 $s"
                if (!keyInsights.contains(bullet)) {
                    keyInsights.add(bullet)
                }
            }
        }

        if (keyInsights.isEmpty()) {
            keyInsights.add("💡 Core technical breakdown and demonstration of concepts in '${video.title}'.")
            keyInsights.add("💡 In-depth walkthrough of practical methods and real-world implications.")
            keyInsights.add("💡 Comparative analysis and strategic insights highlighted by $host.")
        }

        // Synthesize Substantive Conclusion & Takeaways
        val lateSentences = sentences.takeLast((sentences.size * 0.25).toInt().coerceIn(1, 6))
            .filter { !isBoilerplateOutro(it) }
        val conclusion = if (lateSentences.isNotEmpty()) {
            val coreConclusion = lateSentences.maxByOrNull { it.length } ?: lateSentences.last()
            "Overall, $host concludes that ${coreConclusion.replace(Regex("^(so|and|now|in conclusion|to wrap up)\\s+", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.lowercase() }}"
        } else {
            "$host emphasizes the key takeaways from '${video.title}' and highlights the practical outcomes for viewers."
        }

        val executiveSummary = buildString {
            append("🎯 **Executive Summary**\n\n")
            append("$topicPremise\n\n")
            append("🔍 **Key Insights & Breakdown:**\n")
            keyInsights.forEach { insight ->
                append("$insight\n")
            }
            append("\n🏁 **Final Takeaway & Verdict:**\n$conclusion")
        }

        return VideoAiTranscript(
            videoId = video.youtubeId,
            hostName = host,
            topicPremise = topicPremise,
            discussionPoints = keyInsights,
            conclusion = conclusion,
            executiveSummary = executiveSummary.trim(),
            keyTakeaways = keyInsights,
            segments = segments
        )
    }

    private fun cleanSpokenDisfluencies(text: String): String {
        return text
            .replace(Regex("\\b(um|uh|you know|sort of|kind of|like I said|as you can see|basically)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isBoilerplateOutro(sentence: String): Boolean {
        val lower = sentence.lowercase()
        return lower.contains("like and subscribe") ||
                lower.contains("leave a comment below") ||
                lower.contains("hit that notification bell") ||
                lower.contains("link in the description") ||
                lower.contains("patreon") ||
                lower.contains("sponsored by") ||
                lower.contains("thank you for watching") ||
                lower.contains("see you in the next video")
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
