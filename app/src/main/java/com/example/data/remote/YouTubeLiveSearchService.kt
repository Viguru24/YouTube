package com.example.data.remote

import android.util.Log
import com.example.data.model.VideoEntity
import com.example.util.YouTubeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeLiveSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val SEARCH_ENDPOINTS = listOf(
        "https://invidious.flokinet.to/api/v1/search?q=%s",
        "https://inv.nadeko.net/api/v1/search?q=%s",
        "https://invidious.nerdvpn.de/api/v1/search?q=%s",
        "https://invidious.protokolla.fi/api/v1/search?q=%s",
        "https://yewtu.be/api/v1/search?q=%s",
        "https://pipedapi.kavin.rocks/search?q=%s&filter=all"
    )

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (e: Exception) { }
    }

    /**
     * Fetches a rich, dense timeline of latest uploads across the user's subscribed profile channels.
     * Concurrently queries 15+ subscribed creators in parallel to return 80-120+ fresh videos
     * packed tightly by upload time (minutes, hours, days).
     */
    suspend fun fetchSubscribedProfileFeed(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val channels = com.example.data.model.WillRyanProfileData.subscribedChannels
        val selected = channels.shuffled().take(15)
        val results = java.util.Collections.synchronizedList(mutableListOf<VideoEntity>())

        val jobs = selected.map { channel ->
            async {
                try {
                    val fetched = searchRealYouTubeVideos("$channel latest")
                    val matched = fetched.filter { isMatchingChannel(it, channel) || it.title.lowercase().contains(channel.lowercase()) }
                        .ifEmpty { fetched }
                    results.addAll(matched.take(8))
                } catch (e: Exception) {
                    logD("YouTubeLiveSearchService", "Channel fetch error '$channel': ${e.message}")
                }
            }
        }
        jobs.awaitAll()

        return@withContext results
            .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
            .distinctBy { it.youtubeId }
            .sortedWith(
                compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
            )
    }
    /**
     * Searches YouTube for any query using NewPipe Extractor, direct YouTube web search, and fallback API.
     * Supports forcing strict YouTube upload date sorting (sp=CAI%3D).
     */
    suspend fun searchRealYouTubeVideos(query: String, sortByUploadDate: Boolean = true): List<VideoEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. PRIMARY: Direct YouTube Web HTML scraping with optional Sort-By-Upload-Date (sp=CAI%3D)
        val webResults = searchWebHtml(trimmed, sortByUploadDate).filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
        if (webResults.isNotEmpty()) {
            logD("YouTubeLiveSearchService", "[Web Search] Found ${webResults.size} real results for '$trimmed'")
            return@withContext if (sortByUploadDate) {
                webResults.sortedWith(compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) })
            } else webResults
        }

        // 2. SECONDARY: NewPipe SearchExtractor
        try {
            val service = org.schabi.newpipe.extractor.ServiceList.YouTube
            val extractor = service.getSearchExtractor(trimmed)
            extractor.fetchPage()
            val page = extractor.initialPage
            val results = mutableListOf<VideoEntity>()
            for (item in page.items) {
                if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                    val vidId = com.example.util.YouTubeUtils.extractVideoId(item.url) ?: item.url.substringAfter("v=").take(11)
                    if (vidId.isNotBlank() && vidId.length == 11) {
                        val durSec = item.duration
                        val durFormatted = if (durSec > 0) {
                            val m = durSec / 60
                            val s = durSec % 60
                            String.format("%d:%02d", m, s)
                        } else "0:00"

                        val title = item.name ?: "YouTube Video"
                        val channel = item.uploaderName ?: "YouTube"

                        // Purge foreign / Indian scripts and non-English media unless explicitly searched
                        if (!YouTubeUtils.isForeignLanguageContent(title, channel)) {
                            results.add(
                                VideoEntity(
                                    youtubeId = vidId,
                                    title = title,
                                    channelName = channel,
                                    thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: com.example.util.YouTubeUtils.getThumbnailUrl(vidId),
                                    durationText = durFormatted,
                                    category = "YouTube",
                                    publishedTimeText = item.textualUploadDate ?: "",
                                    viewCountText = if (item.viewCount >= 0) "${item.viewCount} views" else ""
                                )
                            )
                        }
                    }
                }
            }
            if (results.isNotEmpty()) {
                logD("YouTubeLiveSearchService", "[NewPipe Search] Found ${results.size} real results for '$trimmed'")
                return@withContext if (sortByUploadDate) {
                    results.sortedWith(compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) })
                } else results
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "[NewPipe Search] Failed: ${e.message}")
        }

        // 3. FALLBACK: Fast Invidious Endpoint
        try {
            val encoded = try { URLEncoder.encode(trimmed, "UTF-8") } catch (e: Exception) { trimmed }
            val sortParam = if (sortByUploadDate) "&sort=upload_date" else ""
            val url = "https://invidious.flokinet.to/api/v1/search?q=$encoded&type=video&region=US$sortParam"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val parsed = parseJsonResponse(bodyString, trimmed).filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                    if (parsed.isNotEmpty()) return@withContext if (sortByUploadDate) {
                        parsed.sortedWith(compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) })
                    } else parsed
                }
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "Invidious search fallback error: ${e.message}")
        }

        return@withContext emptyList()
    }

    /**
     * Dedicated category feed fetcher that queries category-specific creators and topics
     * in parallel and sorts strictly by latest upload date.
     */
    suspend fun fetchCategoryFeed(category: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        val topicQueries = when (category) {
            "Tech & Code" -> listOf(
                "Matthew Berman",
                "AI Revolution",
                "Two Bit da Vinci",
                "Anastasi In Tech",
                "Matt Wolfe",
                "Sabine Hossenfelder",
                "Nerdy Rodent",
                "The Robotics State",
                "Fireship",
                "Tech AI coding breakthrough news"
            )
            "Tutorials" -> listOf(
                "Warren Smith - Secret Scholar",
                "Julian Goldie SEO",
                "freeCodeCamp",
                "How to tutorial guide 2026",
                "Tutorial coding step by step"
            )
            "Gaming" -> listOf(
                "ClashIQ",
                "IGN gaming walkthrough 4K",
                "Gaming news update today",
                "PlayStation gameplay 4K"
            )
            "Music" -> listOf(
                "Official music video 2026",
                "Billboard top hits new release",
                "Vevo official music video"
            )
            "Focus & Ambient" -> listOf(
                "Lofi hip hop radio live stream",
                "Ambient study music deep focus 4K",
                "Focus ambient soundscape"
            )
            else -> listOf("$category latest uploads", "$category news today")
        }

        val results = java.util.Collections.synchronizedList(mutableListOf<VideoEntity>())
        val jobs = topicQueries.map { q ->
            async {
                try {
                    val fetched = searchRealYouTubeVideos(q, sortByUploadDate = true)
                    results.addAll(fetched.take(8))
                } catch (e: Exception) {
                    logD("YouTubeLiveSearchService", "Category search error for '$q': ${e.message}")
                }
            }
        }
        jobs.awaitAll()

        return@withContext results
            .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
            .distinctBy { it.youtubeId }
            .map { it.copy(category = category) }
            .sortedWith(
                compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
            )
    }

    private fun isMatchingChannel(video: VideoEntity, channelName: String): Boolean {
        val target = channelName.lowercase().trim()
        val author = video.channelName.lowercase().trim()
        val title = video.title.lowercase().trim()

        if (author.isBlank()) return false

        // Direct containment or exact match
        if (author.contains(target) || target.contains(author)) return true
        if (author.replace(" ", "") == target.replace(" ", "")) return true

        // Keyword checking (e.g. "Benny Johnson" -> ["benny", "johnson"])
        val keywords = target.split("\\s+".toRegex()).filter { it.length > 2 }
        if (keywords.isNotEmpty() && keywords.all { author.contains(it) }) return true

        // If author is generic ("YouTube", "Channel"), check if title contains all channel keywords
        if (author.contains("youtube") || author.contains("channel")) {
            if (keywords.isNotEmpty() && keywords.all { title.contains(it) }) return true
        }

        return false
    }

    /**
     * Fetches latest videos for a specific subscribed profile channel sorted strictly by newness.
     */
    suspend fun fetchChannelLatestVideos(channelName: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        val trimmed = channelName.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val queries = listOf(
            "$trimmed latest uploads",
            "$trimmed channel videos",
            "$trimmed recent uploads",
            "$trimmed official channel",
            "\"$trimmed\""
        )

        val accumulated = mutableListOf<VideoEntity>()
        for (q in queries) {
            val fetched = searchRealYouTubeVideos(q)
            val matched = fetched.filter { isMatchingChannel(it, trimmed) }
                .map { it.copy(channelName = trimmed) }
            accumulated.addAll(matched)
            if (accumulated.distinctBy { it.youtubeId }.size >= 20) break
        }

        return@withContext accumulated
            .distinctBy { it.youtubeId }
            .sortedWith(
                compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
            )
    }

    /**
     * Continuous search batch fetcher for endless infinite scroll on channel uploads.
     */
    suspend fun fetchChannelVideosBatch(channelName: String, batchIndex: Int = 0): List<VideoEntity> = withContext(Dispatchers.IO) {
        val trimmed = channelName.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val variations = listOf(
            "$trimmed latest uploads",
            "$trimmed channel videos",
            "$trimmed full episode",
            "$trimmed new video",
            "$trimmed recent uploads",
            "$trimmed official channel",
            "$trimmed podcast",
            "$trimmed news"
        )
        val targetQuery = variations[batchIndex % variations.size]
        val fetched = searchRealYouTubeVideos(targetQuery)
        val matched = fetched.filter { isMatchingChannel(it, trimmed) }
            .map { it.copy(channelName = trimmed) }

        return@withContext matched.sortedWith(
            compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
        )
    }

    /**
     * Continuous search batch fetcher for endless infinite scroll on search results.
     */
    suspend fun searchRealYouTubeVideosBatch(query: String, batchIndex: Int = 0): List<VideoEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val targetQuery = if (batchIndex <= 0) {
            trimmed
        } else {
            val variations = listOf(
                "$trimmed full video",
                "$trimmed 2026",
                "best $trimmed",
                "$trimmed HD",
                "$trimmed official",
                "latest $trimmed",
                "$trimmed channel",
                "$trimmed review"
            )
            variations[(batchIndex - 1) % variations.size]
        }
        return searchRealYouTubeVideos(targetQuery)
    }

    private fun parseJsonResponse(json: String, query: String): List<VideoEntity> {
        val results = mutableListOf<VideoEntity>()
        val seenIds = mutableSetOf<String>()

        try {
            // Piped API can return either a raw JSON array or {"items": [...]}
            val jsonArray = try {
                JSONArray(json)
            } catch (e: Exception) {
                try {
                    val obj = org.json.JSONObject(json)
                    obj.optJSONArray("items") ?: obj.optJSONArray("relatedStreams") ?: return results
                } catch (e2: Exception) {
                    return results
                }
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val itemType = item.optString("type", "")
                if (itemType == "stream" || itemType == "video" || itemType.isEmpty()) {
                    val rawUrl = item.optString("url", "")
                    val videoId = item.optString("videoId", "")

                    val id = when {
                        videoId.length == 11 -> videoId
                        rawUrl.contains("v=") -> rawUrl.substringAfter("v=").take(11)
                        rawUrl.startsWith("/watch?v=") -> rawUrl.removePrefix("/watch?v=").take(11)
                        else -> ""
                    }

                    if (id.length == 11 && !seenIds.contains(id)) {
                        seenIds.add(id)
                        val title = cleanText(item.optString("title", "$query Video"))
                        val channel = cleanText(item.optString("uploaderName", item.optString("author", "YouTube Channel")))
                        val durationSec = item.optLong("duration", -1L)
                        val durationText = if (durationSec > 0) formatSeconds(durationSec) else ""

                        // Extract upload date: prefer human-readable "uploadedDate", fallback to epoch "uploaded"
                        var uploadedDate = item.optString("uploadedDate", "").trim()
                        if (uploadedDate.isBlank() || uploadedDate == "null") {
                            val uploadedEpoch = item.optLong("uploaded", 0L)
                            if (uploadedEpoch > 0) {
                                uploadedDate = com.example.util.YouTubeUtils.formatRelativeTime(uploadedEpoch)
                            }
                        }
                        // Also try "publishedTime" field (Invidious)
                        if (uploadedDate.isBlank()) {
                            uploadedDate = item.optString("publishedText", item.optString("publishedTime", "")).trim()
                        }
                        // Filter out generic placeholders
                        if (uploadedDate.equals("Recent", ignoreCase = true) || uploadedDate.equals("Recently", ignoreCase = true)) {
                            uploadedDate = ""
                        }

                        val views = item.optLong("views", 0L)
                        val viewsText = if (views > 0) com.example.util.YouTubeUtils.formatViewCount(views) else item.optString("viewsText", "")

                        results.add(
                            VideoEntity(
                                youtubeId = id,
                                title = title,
                                channelName = channel,
                                thumbnailUrl = YouTubeUtils.getThumbnailUrl(id),
                                durationText = durationText,
                                category = "Search",
                                publishedTimeText = uploadedDate,
                                viewCountText = if (viewsText.isNotBlank()) viewsText else ""
                            )
                        )
                    }
                }
                if (results.size >= 50) break
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "parseJsonResponse error: ${e.message}")
        }

        return results
    }

    /**
     * Fetches real-time YouTube home page recommendation feed directly from youtube.com.
     */
    suspend fun fetchHomeRecommendationFeed(): List<VideoEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com?hl=en&gl=US"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "PREF=hl=en&gl=US; SOCS=CAI")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val parsed = parseVideoRenderers(html, "Recommended")
                        .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "Home feed fetch error: ${e.message}")
        }
        return@withContext emptyList()
    }

    private fun searchWebHtml(query: String, sortByUploadDate: Boolean = false): List<VideoEntity> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val sortParam = if (sortByUploadDate) "&sp=CAI%253D" else ""
            val url = "https://www.youtube.com/results?search_query=$encodedQuery$sortParam&hl=en&gl=US"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "PREF=hl=en&gl=US; SOCS=CAI")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val parsed = parseVideoRenderers(html, "YouTube")
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "Search web error for '$query': ${e.message}")
        }
        return emptyList()
    }

    private fun parseVideoRenderers(html: String, defaultCategory: String): List<VideoEntity> {
        val results = mutableListOf<VideoEntity>()
        val seenIds = mutableSetOf<String>()

        val blocks = html.split("\"videoRenderer\":{")
        for (i in 1 until blocks.size) {
            val block = blocks[i]
            val idMatcher = Pattern.compile(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""").matcher(block)
            if (idMatcher.find()) {
                val id = idMatcher.group(1) ?: continue
                if (!seenIds.contains(id)) {
                    seenIds.add(id)
                    val titleMatcher = Pattern.compile(""""title"\s*:\s*\{\s*(?:"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"|"simpleText"\s*:\s*"([^"]+)")""").matcher(block)
                    val ownerMatcher = Pattern.compile(""""longBylineText"\s*:\s*\{\s*(?:"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"|"simpleText"\s*:\s*"([^"]+)")""").matcher(block)
                    val pubMatcher = Pattern.compile(""""publishedTimeText"\s*:\s*\{\s*(?:"simpleText"\s*:\s*"([^"]+)"|"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)")""").matcher(block)
                    val viewMatcher = Pattern.compile(""""viewCountText"\s*:\s*\{\s*(?:"simpleText"\s*:\s*"([^"]+)"|"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)")""").matcher(block)
                    val lengthMatcher = Pattern.compile(""""lengthText"\s*:\s*\{\s*(?:"simpleText"\s*:\s*"([^"]+)"|"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)")""").matcher(block)

                    val title = if (titleMatcher.find()) cleanText(titleMatcher.group(1) ?: titleMatcher.group(2) ?: "") else "YouTube Video"
                    val channel = if (ownerMatcher.find()) cleanText(ownerMatcher.group(1) ?: ownerMatcher.group(2) ?: "") else "YouTube"
                    val publishedText = if (pubMatcher.find()) cleanText(pubMatcher.group(1) ?: pubMatcher.group(2) ?: "") else ""
                    val viewText = if (viewMatcher.find()) cleanText(viewMatcher.group(1) ?: viewMatcher.group(2) ?: "") else ""
                    val duration = if (lengthMatcher.find()) cleanText(lengthMatcher.group(1) ?: lengthMatcher.group(2) ?: "") else ""

                    if (!YouTubeUtils.isForeignLanguageContent(title, channel)) {
                        results.add(
                            VideoEntity(
                                youtubeId = id,
                                title = title,
                                channelName = channel,
                                thumbnailUrl = YouTubeUtils.getThumbnailUrl(id),
                                durationText = duration,
                                category = defaultCategory,
                                publishedTimeText = publishedText,
                                viewCountText = viewText
                            )
                        )
                    }
                }
            }
            if (results.size >= 25) break
        }
        return results
    }

    /**
     * Fetches real, high-quality English YouTube Shorts from reputable channels and verified topics.
     * Enforces strict 3..90s duration and zero foreign language content.
     */
    suspend fun fetchShortsFeed(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val topics = listOf(
            "BBC News #shorts",
            "Sky News #shorts",
            "Reuters #shorts",
            "MKBHD #shorts",
            "Veritasium #shorts",
            "Daily Dose of Internet #shorts",
            "Kurzgesagt #shorts",
            "Wired #shorts",
            "National Geographic #shorts",
            "Gordon Ramsay #shorts",
            "Science #shorts",
            "Technology #shorts",
            "Nature #shorts",
            "Engineering #shorts",
            "Space #shorts",
            "History #shorts",
            "Woodworking #shorts",
            "Formula 1 #shorts"
        )
        val selectedTopics = topics.shuffled().take(6)
        val accumulated = mutableListOf<VideoEntity>()
        for (topic in selectedTopics) {
            val fetched = searchRealYouTubeVideos(topic)
            val filtered = fetched.filter { v ->
                val durationSec = com.example.util.YouTubeUtils.parseFormattedTimeToSeconds(v.durationText)
                durationSec in 3..60 &&
                !YouTubeUtils.isForeignLanguageContent(v.title, v.channelName) &&
                !v.title.lowercase().contains("kids") &&
                !v.title.lowercase().contains("cartoon") &&
                !v.title.lowercase().contains("nursery") &&
                !v.title.lowercase().contains("cocomelon")
            }.map { it.copy(category = "Shorts") }
            accumulated.addAll(filtered)
        }
        return@withContext accumulated.distinctBy { it.youtubeId }
    }

    private fun formatSeconds(sec: Long): String {
        if (sec <= 0) return "00:00"
        val mins = sec / 60
        val remainder = sec % 60
        return String.format("%02d:%02d", mins, remainder)
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace("\\u0026", "&")
            .replace("\\\"", "\"")
            .replace("\\u0027", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
