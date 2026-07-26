package com.example.data.remote

import android.util.Log
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeStreamExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
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

    suspend fun getDirectStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val visitorData = fetchVisitorData()
        val visitorJson = if (!visitorData.isNullOrEmpty()) {
            """, "visitorData": "$visitorData""""
        } else ""

        val clientProfiles = listOf(
            Triple("ANDROID_VR", "1.59.19", false),
            Triple("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", true),
            Triple("ANDROID_TESTSUITE", "1.9.0", false),
            Triple("ANDROID", "19.09.37", false),
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
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
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
                            logD("YouTubeStreamExtractor", "[$clientName] Found progressive stream URL for $videoId: $rawUrl")
                            DiagnosticLogger.logHealthy("StreamExtractor", "[$clientName] Native MP4 extracted for $videoId")
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
                            logD("YouTubeStreamExtractor", "[$clientName] Found cipher stream URL for $videoId: $cipherUrl")
                            DiagnosticLogger.logHealthy("StreamExtractor", "[$clientName] Cipher MP4 extracted for $videoId")
                            return@withContext cipherUrl
                        }
                    }
                }
            } catch (e: Exception) {
                logD("YouTubeStreamExtractor", "Error extracting stream for $videoId: ${e.message}")
            }
        }

        // Fallback: Open-source Piped stream extractor API mirror for 100% video resolution
        val fallbackUrl = fetchPipedDirectStreamUrl(videoId)
        if (!fallbackUrl.isNullOrEmpty()) {
            DiagnosticLogger.logWarning("StreamExtractor", "Piped fallback active for $videoId")
            return@withContext fallbackUrl
        }

        DiagnosticLogger.logError("StreamExtractor", "Failed to extract stream for $videoId")
        return@withContext null
    }

    private fun fetchPipedDirectStreamUrl(videoId: String): String? {
        val pipedStreamEndpoints = listOf(
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://api.piped.privacydev.net/streams/$videoId",
            "https://pipedapi.mha.fi/streams/$videoId"
        )

        for (url in pipedStreamEndpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: return@use
                        val jsonObj = JSONObject(bodyString)
                        val videoStreams = jsonObj.optJSONArray("videoStreams")
                        if (videoStreams != null && videoStreams.length() > 0) {
                            for (i in 0 until videoStreams.length()) {
                                val stream = videoStreams.getJSONObject(i)
                                val streamUrl = stream.optString("url", "")
                                if (streamUrl.contains("googlevideo.com")) {
                                    logD("YouTubeStreamExtractor", "Extracted direct MP4 stream URL via Piped API mirror for $videoId")
                                    return streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logD("YouTubeStreamExtractor", "Piped stream endpoint $url error: ${e.message}")
            }
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
