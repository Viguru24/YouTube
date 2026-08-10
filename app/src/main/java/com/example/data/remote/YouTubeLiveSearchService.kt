package com.example.data.remote

import android.util.Log
import com.example.data.model.VideoEntity
import com.example.util.YouTubeUtils
import kotlinx.coroutines.Dispatchers
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
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
    }

    /**
     * Fetches fresh videos from the user's subscribed profile channels (Will Ryan).
     */
    suspend fun fetchSubscribedProfileFeed(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val sampleChannels = com.example.data.model.WillRyanProfileData.subscribedChannels.shuffled().take(4)
        val results = mutableListOf<VideoEntity>()
        for (channel in sampleChannels) {
            val fetched = searchRealYouTubeVideos(channel)
            results.addAll(fetched.take(3))
        }
        return@withContext results.distinctBy { it.youtubeId }
    }
    /**
     * Searches YouTube for any query using multi-host redundant open-source search API endpoints.
     */
    suspend fun searchRealYouTubeVideos(query: String): List<VideoEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val encoded = try { URLEncoder.encode(trimmed, "UTF-8") } catch (e: Exception) { trimmed }

        // Iterate through fast open-source YouTube search API mirrors
        for (endpointTemplate in SEARCH_ENDPOINTS) {
            try {
                val url = String.format(endpointTemplate, encoded)
                logD("YouTubeLiveSearchService", "Trying search endpoint: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val results = parseJsonResponse(bodyString, trimmed)
                        if (results.isNotEmpty()) {
                            logD("YouTubeLiveSearchService", "Extracted ${results.size} real video results from endpoint: $url")
                            return@withContext results
                        }
                    }
                }
            } catch (e: Exception) {
                logD("YouTubeLiveSearchService", "Endpoint fail: ${e.message}")
            }
        }

        // Final Fallback: Direct YouTube Web HTML scraping
        return@withContext searchWebHtml(trimmed)
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
            val url = "https://www.youtube.com"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "SOCS=CAI")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val parsed = parseVideoRenderers(html, "Recommended")
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "Home feed fetch error: ${e.message}")
        }
        return@withContext emptyList()
    }

    private fun searchWebHtml(query: String): List<VideoEntity> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "SOCS=CAI")
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
            if (results.size >= 25) break
        }
        return results
    }

    /**
     * Fetches live trending YouTube Shorts feed filtered for adult topics (News, Politics, Tech, Science).
     * Exhaustively purges kids cartoons, ABCs, nursery rhymes, phonics, and clickbait.
     */
    suspend fun fetchShortsFeed(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val topics = listOf(
            "politics news analysis",
            "technology AI updates",
            "current events news",
            "science discovery break",
            "world events documentary"
        )
        val selectedTopic = topics.random()
        val shorts = searchRealYouTubeVideos("#shorts $selectedTopic")
            .filter { v ->
                val durationSec = com.example.util.YouTubeUtils.parseFormattedTimeToSeconds(v.durationText)
                val isShortDuration = durationSec == 0 || durationSec <= 90
                val lower = (v.title + " " + v.channelName).lowercase()
                isShortDuration &&
                !lower.contains("abc") &&
                !lower.contains("alphabet") &&
                !lower.contains("phonics") &&
                !lower.contains("cartoon") &&
                !lower.contains("kids") &&
                !lower.contains("cocomelon") &&
                !lower.contains("nursery") &&
                !lower.contains("rhyme") &&
                !lower.contains("toy") &&
                !lower.contains("baby") &&
                !lower.contains("toddler") &&
                !lower.contains("preschool") &&
                !lower.contains("children") &&
                !lower.contains("sing along") &&
                !lower.contains("super simple") &&
                !lower.contains("short viral") &&
                !lower.contains("spreading gyan") &&
                !lower.contains("animation for kids")
            }
            .map { it.copy(category = "Shorts", title = if (it.title.contains("#shorts", ignoreCase = true)) it.title else "${it.title} #shorts") }
        
        if (shorts.isNotEmpty()) return@withContext shorts
        
        return@withContext emptyList()
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
