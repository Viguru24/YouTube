package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.regex.Pattern

data class StreamExtractionResult(
    val primaryStreamUrl: String?,
    val audioStreamUrl: String? = null,
    val combinedMuxedUrl: String? = null,
    val availableQualities: List<String> = emptyList(),
    val qualityUrlMap: Map<String, String> = emptyMap(),
    val videoOnlyQualities: Set<String> = emptySet()
)

object YouTubeStreamExtractor {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var cachedVisitorData: String? = null

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun fetchVisitorData(): String? {
        if (cachedVisitorData != null) return cachedVisitorData
        try {
            val jsonPayload = """
                {
                  "context": {
                    "client": {
                      "clientName": "ANDROID",
                      "clientVersion": "19.09.37",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id")
                .post(jsonPayload.toRequestBody(mediaType))
                .addHeader("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14; US) gzip")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val pattern = Pattern.compile(""""visitorData"\s*:\s*"([^"]+)"""")
                val matcher = pattern.matcher(bodyString)
                if (matcher.find()) {
                    val visitorData = matcher.group(1)
                    logD("YouTubeStreamExtractor", "Fetched YouTube visitorData poToken: $visitorData")
                    cachedVisitorData = visitorData
                    return visitorData
                }
            }
        } catch (e: Exception) {
            logD("YouTubeStreamExtractor", "Error fetching visitorData: ${e.message}")
        }
        return null
    }

    /**
     * Extracts all available stream resolutions and their direct URLs in a single network pass.
     */
    suspend fun extractVideoStreams(videoId: String): StreamExtractionResult = withContext(Dispatchers.IO) {
        val qualityMap = mutableMapOf<String, String>()
        val videoOnlyQualities = mutableSetOf<String>()
        var bestAudioUrl: String? = null
        var bestCombinedUrl: String? = null

        // 1. PRIMARY: NewPipe Extractor
        try {
            logD("YouTubeStreamExtractor", "[NewPipe] Extracting streams for videoId: $videoId")
            val service = org.schabi.newpipe.extractor.ServiceList.YouTube
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            // Best Audio Stream (prioritize M4A/AAC for seamless MP4 container muxing)
            val audioStreams = try { extractor.audioStreams } catch (e: Exception) { emptyList() }
            val m4aAudio = audioStreams.firstOrNull { 
                val fmt = it.format?.name.orEmpty()
                fmt.contains("M4A", ignoreCase = true) || fmt.contains("AAC", ignoreCase = true) || fmt.contains("MP4", ignoreCase = true)
            }
            bestAudioUrl = m4aAudio?.content ?: audioStreams.firstOrNull { !it.content.isNullOrBlank() }?.content

            // Video + Audio combined streams (Muxed - guaranteed instant audio!)
            val videoStreams = try { extractor.videoStreams } catch (e: Exception) { emptyList() }
            for (s in videoStreams) {
                if (!s.isVideoOnly && !s.content.isNullOrBlank()) {
                    val r = s.resolution?.trim()
                    if (!r.isNullOrBlank()) {
                        val key = if (r.endsWith("p", ignoreCase = true)) r.lowercase() else "${r}p"
                        if (!qualityMap.containsKey(key)) {
                            qualityMap[key] = s.content
                            if (bestCombinedUrl == null) {
                                bestCombinedUrl = s.content
                            }
                        }
                    }
                }
            }

            // Video-only streams (e.g. 1080p, 1440p, 4K) - only include if audio stream is available for merging
            if (!bestAudioUrl.isNullOrBlank()) {
                val videoOnlyStreams = try { extractor.videoOnlyStreams } catch (e: Exception) { emptyList() }
                for (s in videoOnlyStreams) {
                    if (!s.content.isNullOrBlank()) {
                        val r = s.resolution?.trim()
                        if (!r.isNullOrBlank()) {
                            val key = if (r.endsWith("p", ignoreCase = true)) r.lowercase() else "${r}p"
                            if (!qualityMap.containsKey(key)) {
                                qualityMap[key] = s.content
                                videoOnlyQualities.add(key)
                            }
                        }
                    }
                }
            }

            // HLS stream
            val hlsUrl = try { extractor.hlsUrl } catch (e: Exception) { null }
            if (!hlsUrl.isNullOrBlank()) {
                qualityMap["HLS"] = hlsUrl
            }
        } catch (e: Exception) {
            logD("YouTubeStreamExtractor", "[NewPipe] Extraction error for $videoId: ${e.message}")
        }

        // 2. SECONDARY: HTML scraping fallback if NewPipe returned no streams
        if (qualityMap.isEmpty()) {
            val htmlPageUrls = listOf(
                "https://www.youtube.com/watch?v=$videoId",
                "https://www.youtube.com/shorts/$videoId"
            )
            for (pageUrl in htmlPageUrls) {
                try {
                    val watchRequest = Request.Builder()
                        .url(pageUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                        .addHeader("Accept-Language", "en-US,en;q=0.9")
                        .addHeader("Cookie", "PREF=f6=40000000&hl=en&gl=US; SOCS=CAI")
                        .addHeader("Sec-Ch-Ua-Mobile", "?0")
                        .addHeader("Sec-Ch-Ua-Platform", "\"Windows\"")
                        .build()
                    client.newCall(watchRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            val pattern = Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});")
                            val matcher = pattern.matcher(html)
                            if (matcher.find()) {
                                val jsonStr = matcher.group(1)
                                if (!jsonStr.isNullOrEmpty()) {
                                    val jsonObj = org.json.JSONObject(jsonStr)
                                    val streamUrl = extractUrlFromPlayerResponse(jsonObj)
                                    if (!streamUrl.isNullOrEmpty()) {
                                        qualityMap["720p"] = streamUrl
                                        qualityMap["Auto"] = streamUrl
                                    }
                                }
                            }
                        }
                    }
                    if (qualityMap.isNotEmpty()) break
                } catch (e: Exception) { }
            }
        }

        // 3. TERTIARY: Invidious Public Instance API Fallback
        if (qualityMap.isEmpty()) {
            val fallbackApis = listOf(
                "https://invidious.flokinet.to/api/v1/videos/$videoId?local=true",
                "https://inv.nadeko.net/api/v1/videos/$videoId?local=true",
                "https://invidious.nerdvpn.de/api/v1/videos/$videoId?local=true",
                "https://yewtu.be/api/v1/videos/$videoId?local=true"
            )
            for (apiUrl in fallbackApis) {
                try {
                    val req = Request.Builder().url(apiUrl).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val b = resp.body?.string() ?: ""
                            val json = org.json.JSONObject(b)
                            val formatStreams = json.optJSONArray("formatStreams")
                            if (formatStreams != null && formatStreams.length() > 0) {
                                for (i in 0 until formatStreams.length()) {
                                    val stream = formatStreams.getJSONObject(i)
                                    val streamUrl = stream.optString("url", "")
                                    val res = stream.optString("qualityLabel", stream.optString("resolution", ""))
                                    if (streamUrl.isNotBlank()) {
                                        val key = if (res.isNotBlank()) (if (res.endsWith("p", true)) res.lowercase() else "${res}p") else "720p"
                                        qualityMap[key] = streamUrl
                                    }
                                }
                            }
                        }
                    }
                    if (qualityMap.isNotEmpty()) break
                } catch (e: Exception) { }
            }
        }

        val sortedQualities = qualityMap.keys
            .filter { it != "HLS" && it != "Auto" }
            .sortedByDescending { it.replace("p", "").toIntOrNull() ?: 0 }

        // Prioritize non-throttled HLS adaptive stream or stable combined muxed stream
        val bestUrl = qualityMap["HLS"]
            ?: bestCombinedUrl
            ?: sortedQualities.firstOrNull()?.let { qualityMap[it] }
            ?: qualityMap["Auto"]
            ?: qualityMap.values.firstOrNull()

        if (bestUrl != null) {
            qualityMap["Auto"] = bestUrl
        }

        val finalQualitiesList = if (sortedQualities.isNotEmpty()) {
            (listOf("Auto") + sortedQualities).distinct()
        } else {
            listOf("Auto")
        }

        logD("YouTubeStreamExtractor", "Extracted qualities for $videoId: $finalQualitiesList (Primary: ${bestUrl?.take(60)}..., Audio: ${bestAudioUrl?.take(60)}...)")

        return@withContext StreamExtractionResult(
            primaryStreamUrl = bestUrl,
            audioStreamUrl = bestAudioUrl,
            combinedMuxedUrl = bestCombinedUrl,
            availableQualities = finalQualitiesList,
            qualityUrlMap = qualityMap,
            videoOnlyQualities = videoOnlyQualities
        )
    }

    suspend fun getAvailableStreamQualities(videoId: String): List<String> = withContext(Dispatchers.IO) {
        val result = extractVideoStreams(videoId)
        return@withContext result.availableQualities
    }

    suspend fun getDirectStreamUrl(videoId: String, targetQuality: String = "Auto"): String? = withContext(Dispatchers.IO) {
        val result = extractVideoStreams(videoId)
        if (targetQuality != "Auto" && result.qualityUrlMap.containsKey(targetQuality)) {
            return@withContext result.qualityUrlMap[targetQuality]
        }
        return@withContext result.primaryStreamUrl
    }

    private fun extractUrlFromPlayerResponse(playerResponse: org.json.JSONObject): String? {
        try {
            val streamingData = playerResponse.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val url = f.optString("url")
                    if (url.isNotEmpty()) return url
                    val cipher = f.optString("signatureCipher", f.optString("cipher"))
                    if (cipher.isNotEmpty()) {
                        val parsed = parseCipher(cipher)
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            }
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null && adaptive.length() > 0) {
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.getJSONObject(i)
                    val mime = f.optString("mimeType")
                    if (mime.contains("video")) {
                        val url = f.optString("url")
                        if (url.isNotEmpty()) return url
                    }
                }
            }
        } catch (e: Exception) {
            logD("YouTubeStreamExtractor", "extractUrlFromPlayerResponse error: ${e.message}")
        }
        return null
    }

    private fun parseCipher(cipher: String): String {
        return try {
            val params = cipher.split("&")
            var url = ""
            var sig = ""
            var sp = "sig"
            for (param in params) {
                val keyValue = param.split("=")
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = URLDecoder.decode(keyValue[1], "UTF-8")
                    if (key == "url") url = value
                    if (key == "s") sig = value
                    if (key == "sp") sp = value
                }
            }
            if (url.isNotEmpty()) {
                if (sig.isNotEmpty()) {
                    "$url&$sp=$sig"
                } else {
                    url
                }
            } else ""
        } catch (e: Exception) {
            ""
        }
    }
}
