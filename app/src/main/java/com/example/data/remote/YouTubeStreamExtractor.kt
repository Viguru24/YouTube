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

    suspend fun getDirectStreamUrl(videoId: String, targetQuality: String = "Auto"): String? = withContext(Dispatchers.IO) {
        // =========================================================================
        // STRATEGY 1: NewPipe Extractor (PRIMARY — actively maintained, handles
        //             cipher/signature decryption, PO tokens, and YouTube changes)
        // =========================================================================
        try {
            logD("YouTubeStreamExtractor", "[NewPipe] Attempting extraction for videoId: $videoId, quality: $targetQuality")
            val service = org.schabi.newpipe.extractor.ServiceList.YouTube
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val targetRes = targetQuality.replace("p", "").toIntOrNull() ?: 0

            // Try video streams (combined audio+video) first
            val videoStreams = try { extractor.videoStreams } catch (e: Exception) { emptyList() }
            if (videoStreams.isNotEmpty()) {
                val preferred = if (targetRes > 0) {
                    videoStreams
                        .filter { !it.isVideoOnly }
                        .minByOrNull { stream ->
                            val res = stream.resolution?.replace("p", "")?.toIntOrNull() ?: 0
                            kotlin.math.abs(res - targetRes)
                        }
                } else {
                    videoStreams
                        .filter { !it.isVideoOnly }
                        .sortedByDescending { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }
                        .firstOrNull { stream ->
                            val res = stream.resolution?.replace("p", "")?.toIntOrNull() ?: 0
                            res in 360..1080
                        }
                        ?: videoStreams.firstOrNull { !it.isVideoOnly }
                } ?: videoStreams.firstOrNull()

                if (preferred != null && preferred.content.isNotBlank()) {
                    logD("YouTubeStreamExtractor", "[NewPipe] Found stream for $targetQuality: ${preferred.resolution} ${preferred.format?.name} — ${preferred.content.take(80)}...")
                    return@withContext preferred.content
                }
            }

            // Try video-only streams (higher resolution options)
            val videoOnlyStreams = try { extractor.videoOnlyStreams } catch (e: Exception) { emptyList() }
            if (videoOnlyStreams.isNotEmpty()) {
                val best = if (targetRes > 0) {
                    videoOnlyStreams.minByOrNull { stream ->
                        val res = stream.resolution?.replace("p", "")?.toIntOrNull() ?: 0
                        kotlin.math.abs(res - targetRes)
                    }
                } else {
                    videoOnlyStreams
                        .sortedByDescending { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }
                        .firstOrNull { stream ->
                            val res = stream.resolution?.replace("p", "")?.toIntOrNull() ?: 0
                            res in 360..1080
                        }
                } ?: videoOnlyStreams.firstOrNull()

                if (best != null && best.content.isNotBlank()) {
                    logD("YouTubeStreamExtractor", "[NewPipe] Found video-only stream for $targetQuality: ${best.resolution} — ${best.content.take(80)}...")
                    return@withContext best.content
                }
            }

            // Try HLS URL
            val hlsUrl = try { extractor.hlsUrl } catch (e: Exception) { null }
            if (!hlsUrl.isNullOrBlank()) {
                logD("YouTubeStreamExtractor", "[NewPipe] Found HLS stream: ${hlsUrl.take(80)}...")
                return@withContext hlsUrl
            }

            logD("YouTubeStreamExtractor", "[NewPipe] No streams found for $videoId")
        } catch (e: Exception) {
            logD("YouTubeStreamExtractor", "[NewPipe] Extraction failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // =========================================================================
        // STRATEGY 2: HTML Scraping (ytInitialPlayerResponse from YouTube web page)
        // =========================================================================
        val htmlPageUrls = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://www.youtube.com/shorts/$videoId"
        )
        for (pageUrl in htmlPageUrls) {
            try {
                val watchRequest = Request.Builder()
                    .url(pageUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .build()

                client.newCall(watchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        
                        // Match hlsManifestUrl
                        val hlsPattern = Pattern.compile(""""hlsManifestUrl"\s*:\s*"([^"]+)"""")
                        val hlsMatcher = hlsPattern.matcher(html)
                        if (hlsMatcher.find()) {
                            val hlsUrl = hlsMatcher.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
                            if (hlsUrl.isNotBlank()) {
                                logD("YouTubeStreamExtractor", "[HTML Scraper] Found HLS stream URL: $hlsUrl")
                                return@withContext hlsUrl
                            }
                        }

                        // Extract streamingData from ytInitialPlayerResponse JSON
                        val playerResponsePattern = Pattern.compile("""ytInitialPlayerResponse\s*=\s*(\{.+?\})\s*;""")
                        val prMatcher = playerResponsePattern.matcher(html)
                        if (prMatcher.find()) {
                            val prJson = prMatcher.group(1) ?: ""
                            // Find direct googlevideo URLs in streamingData
                            val urlPattern = Pattern.compile(""""url"\s*:\s*"(https?://[^"]*googlevideo\.com/videoplayback[^"]+)"""")
                            val urlMatcher = urlPattern.matcher(prJson)
                            while (urlMatcher.find()) {
                                val rawUrl = urlMatcher.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
                                if (rawUrl.contains("mime=video") || rawUrl.contains("mime%3Dvideo")) {
                                    logD("YouTubeStreamExtractor", "[HTML Scraper] Found direct stream URL: ${rawUrl.take(80)}...")
                                    return@withContext rawUrl
                                }
                            }
                        }

                        // Fallback: match any googlevideo videoplayback URL
                        val urlPattern = Pattern.compile(""""url"\s*:\s*"([^"]+)"""")
                        val urlMatcher = urlPattern.matcher(html)
                        while (urlMatcher.find()) {
                            val rawUrl = urlMatcher.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
                            if (rawUrl.contains("googlevideo.com/videoplayback") && (rawUrl.contains("mime=video") || rawUrl.contains("mime=audio"))) {
                                logD("YouTubeStreamExtractor", "[HTML Scraper] Found googlevideo URL: ${rawUrl.take(80)}...")
                                return@withContext rawUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logD("YouTubeStreamExtractor", "HTML Scraper error for $pageUrl: ${e.message}")
            }
        }

        // =========================================================================
        // STRATEGY 3: Multi-Profile InnerTube Client Queries (may resume working)
        // =========================================================================
        val visitorData = fetchVisitorData()
        val visitorJson = if (!visitorData.isNullOrEmpty()) {
            """, "visitorData": "$visitorData""""
        } else ""

        val clientProfiles = listOf(
            Triple("ANDROID_TESTSUITE", "1.9", false),
            Triple("MWEB", "2.20240101.00.00", true),
            Triple("ANDROID", "19.11.38", false),
            Triple("ANDROID_VR", "1.59.19", false),
            Triple("WEB_EMBEDDED_PLAYER", "5.20240101.00.00", true),
            Triple("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", true),
            Triple("IOS", "19.09.3", false)
        )

        for ((clientName, clientVersion, isEmbedded) in clientProfiles) {
            try {
                val thirdPartyJson = if (isEmbedded) {
                    """, "thirdParty": { "embedUrl": "https://www.youtube.com/watch?v=$videoId" }"""
                } else ""

                val jsonPayload = """
                    {
                      "videoId": "$videoId",
                      "contentCheckOk": true,
                      "racyCheckOk": true,
                      "context": {
                        "client": {
                          "clientName": "$clientName",
                          "clientVersion": "$clientVersion",
                          "hl": "en",
                          "gl": "US"$visitorJson
                        }$thirdPartyJson
                      }
                    }
                """.trimIndent()

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player")
                    .post(jsonPayload.toRequestBody(mediaType))
                    .addHeader("User-Agent", "com.google.android.youtube/19.11.38 (Linux; U; Android 14; US) gzip")
                    .addHeader("Referer", "https://www.youtube.com")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyString = response.body?.string() ?: return@use

                    // 1. Direct googlevideo.com URLs
                    val urlPattern = Pattern.compile(""""url"\s*:\s*"([^"]+)"""")
                    val matcher = urlPattern.matcher(bodyString)
                    while (matcher.find()) {
                        val rawUrl = matcher.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
                        if (rawUrl.contains("googlevideo.com")) {
                            logD("YouTubeStreamExtractor", "[$clientName] Found progressive stream URL: $rawUrl")
                            return@withContext rawUrl
                        }
                    }

                    // 2. Cipher URLs
                    val cipherPattern = Pattern.compile(""""(signatureCipher|cipher)"\s*:\s*"([^"]+)"""")
                    val cipherMatcher = cipherPattern.matcher(bodyString)
                    while (cipherMatcher.find()) {
                        val rawCipher = cipherMatcher.group(2)?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
                        val cipherUrl = parseCipher(rawCipher)
                        if (cipherUrl.isNotEmpty() && cipherUrl.contains("googlevideo.com")) {
                            logD("YouTubeStreamExtractor", "[$clientName] Found cipher stream URL: $cipherUrl")
                            return@withContext cipherUrl
                        }
                    }
                }
            } catch (e: Exception) {
                logD("YouTubeStreamExtractor", "Error extracting stream for $videoId: ${e.message}")
            }
        }

        // =========================================================================
        // STRATEGY 4: Fallback Public Instance APIs (Invidious with proxy)
        // =========================================================================
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

                        // Try to parse as JSON and extract formatStreams
                        try {
                            val json = org.json.JSONObject(b)
                            val formatStreams = json.optJSONArray("formatStreams")
                            if (formatStreams != null && formatStreams.length() > 0) {
                                for (i in 0 until formatStreams.length()) {
                                    val stream = formatStreams.getJSONObject(i)
                                    val streamUrl = stream.optString("url", "")
                                    if (streamUrl.isNotBlank()) {
                                        logD("YouTubeStreamExtractor", "[Invidious API] Found stream URL: ${streamUrl.take(80)}...")
                                        return@withContext streamUrl
                                    }
                                }
                            }
                            val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                            if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                                for (i in 0 until adaptiveFormats.length()) {
                                    val stream = adaptiveFormats.getJSONObject(i)
                                    val streamUrl = stream.optString("url", "")
                                    val mimeType = stream.optString("type", "")
                                    if (streamUrl.isNotBlank() && mimeType.contains("video")) {
                                        logD("YouTubeStreamExtractor", "[Invidious API] Found adaptive stream URL: ${streamUrl.take(80)}...")
                                        return@withContext streamUrl
                                    }
                                }
                            }
                        } catch (jsonErr: Exception) {
                            // Fall back to regex
                            val urlPat = Pattern.compile(""""url"\s*:\s*"([^"]+)"""")
                            val m = urlPat.matcher(b)
                            while (m.find()) {
                                val stream = m.group(1)?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
                                if (stream.contains("googlevideo.com") || stream.contains("piped") || stream.contains("googlevideo")) {
                                    logD("YouTubeStreamExtractor", "[Fallback API] Found stream URL: $stream")
                                    return@withContext stream
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logD("YouTubeStreamExtractor", "Fallback error for $apiUrl: ${e.message}")
            }
        }

        return@withContext null
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
