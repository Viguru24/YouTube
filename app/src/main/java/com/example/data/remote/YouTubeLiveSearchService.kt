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
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val SEARCH_ENDPOINTS = listOf(
        "https://pipedapi.kavin.rocks/search?q=%s&filter=all",
        "https://api.piped.privacydev.net/search?q=%s&filter=all",
        "https://pipedapi.mha.fi/search?q=%s&filter=all",
        "https://inv.tux.pizza/api/v1/search?q=%s",
        "https://invidious.nerdvpn.de/api/v1/search?q=%s"
    )

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
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

    private fun parseJsonResponse(json: String, query: String): List<VideoEntity> {
        val results = mutableListOf<VideoEntity>()
        val seenIds = mutableSetOf<String>()

        try {
            val jsonArray = JSONArray(json)
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
                        val durationSec = item.optLong("duration", 240L)

                        results.add(
                            VideoEntity(
                                youtubeId = id,
                                title = title,
                                channelName = channel,
                                thumbnailUrl = YouTubeUtils.getThumbnailUrl(id),
                                durationText = formatSeconds(durationSec),
                                category = "Search"
                            )
                        )
                    }
                }
                if (results.size >= 20) break
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }

        return results
    }

    private fun searchWebHtml(query: String): List<VideoEntity> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val results = mutableListOf<VideoEntity>()
                    val seenIds = mutableSetOf<String>()

                    val pattern = Pattern.compile(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
                    val matcher = pattern.matcher(html)
                    var count = 1

                    while (matcher.find() && results.size < 18) {
                        val id = matcher.group(1) ?: continue
                        if (!seenIds.contains(id)) {
                            seenIds.add(id)
                            results.add(
                                VideoEntity(
                                    youtubeId = id,
                                    title = "$query - Clip #$count",
                                    channelName = "YouTube",
                                    thumbnailUrl = YouTubeUtils.getThumbnailUrl(id),
                                    durationText = "03:45",
                                    category = "Search"
                                )
                            )
                            count++
                        }
                    }
                    return results
                }
            }
        } catch (e: Exception) {
            logD("YouTubeLiveSearchService", "Search web error for '$query': ${e.message}")
        }
        return emptyList()
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
