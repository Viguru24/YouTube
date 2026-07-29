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

    suspend fun getDirectStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val visitorData = fetchVisitorData()
        val visitorJson = if (!visitorData.isNullOrEmpty()) {
            """, "visitorData": "$visitorData""""
        } else ""

        val clientProfiles = listOf(
            Triple("ANDROID_VR", "1.59.19", false),
            Triple("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", true),
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
