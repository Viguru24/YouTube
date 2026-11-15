package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val videoOnlyQualities: Set<String> = emptySet(),
    val videoOnlyUrls: Set<String> = emptySet(),
    val muxedUrls: Set<String> = emptySet(),
    val isMembersOnly: Boolean = false,
    val errorMessage: String? = null,
    val clientUserAgent: String? = null
) {
    /**
     * Determines whether the given video stream URL has no built-in audio track and requires
     * merging with a separate audio stream.
     */
    fun isVideoOnlyStream(url: String?, quality: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.contains(".m3u8") || url.contains("manifest/hls_variant") || quality == "HLS") return false
        if (url.startsWith("file://") || url.startsWith("/")) return false
        if (muxedUrls.contains(url)) return false
        if (videoOnlyUrls.contains(url)) return true
        if (quality != null && videoOnlyQualities.contains(quality)) return true
        if (!audioStreamUrl.isNullOrBlank() && !combinedMuxedUrl.isNullOrBlank() && url != combinedMuxedUrl) return true
        if (!audioStreamUrl.isNullOrBlank() && combinedMuxedUrl.isNullOrBlank()) return true
        return false
    }
}

object YouTubeStreamExtractor {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var cachedVisitorData: String? = null

    private data class CachedStream(val result: StreamExtractionResult, val timestamp: Long)
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, CachedStream>()
    private const val STREAM_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    fun prewarmStream(videoId: String, scope: kotlinx.coroutines.CoroutineScope) {
        if (videoId.isBlank() || streamCache.containsKey(videoId)) return
        scope.launch(Dispatchers.IO) {
            try {
                extractVideoStreams(videoId)
            } catch (e: Throwable) { }
        }
    }

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

    private fun fetchInnertubePlayer(
        videoId: String,
        clientName: String = "ANDROID_VR",
        useAuthCookies: Boolean = false,
        poToken: org.schabi.newpipe.extractor.services.youtube.PoTokenResult? = null
    ): org.json.JSONObject? {
        try {
            val isNativeClient = clientName == "ANDROID_VR" || clientName == "IOS" || clientName == "ANDROID"
            val visitorData = when {
                !poToken?.visitorData.isNullOrBlank() -> poToken?.visitorData
                clientName == "ANDROID" || clientName == "WEB" -> fetchVisitorData()
                else -> null
            }
            val visitorDataField = if (!visitorData.isNullOrBlank()) """, "visitorData": "$visitorData"""" else ""

            val (clientObj, userAgent) = when (clientName) {
                "IOS" -> Pair(
                    """{"clientName":"IOS","clientVersion":"20.10.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.3.2.22D82","hl":"en","gl":"US"}""",
                    "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
                )
                "TVHTML5" -> Pair(
                    """{"clientName":"TVHTML5","clientVersion":"7.20240820.01.00","hl":"en","gl":"US"$visitorDataField}""",
                    "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/6.0 TV Safari/538.1"
                )
                "WEB" -> Pair(
                    """{"clientName":"WEB","clientVersion":"2.20250312.01.00","hl":"en","gl":"US"$visitorDataField}""",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                "ANDROID" -> Pair(
                    """{"clientName":"ANDROID","clientVersion":"20.10.38","androidSdkVersion":35,"hl":"en","gl":"US"$visitorDataField}""",
                    "com.google.android.youtube/20.10.38 (Linux; U; Android 15; US) gzip"
                )
                else -> Pair(
                    """{"clientName":"ANDROID_VR","clientVersion":"1.62.27","deviceMake":"Oculus","deviceModel":"Quest 3","androidSdkVersion":32,"hl":"en","gl":"US"}""",
                    "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                )
            }

            val sts = if (!isNativeClient) {
                try {
                    org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
                } catch (_: Exception) {
                    null
                }
            } else null

            val playbackContextBlock = if (sts != null) {
                """, "playbackContext": {"contentPlaybackContext": {"html5Preference": "HTML5_PREF_WANTS", "signatureTimestamp": $sts}}"""
            } else ""

            val poTokenBlock = if (!poToken?.playerRequestPoToken.isNullOrBlank()) {
                """, "serviceIntegrityDimensions": {"poToken": "${poToken?.playerRequestPoToken}"}"""
            } else ""

            val payload = """
                {
                  "context": {
                    "client": $clientObj
                  },
                  "videoId": "$videoId",
                  "contentCheckOk": true,
                  "racyCheckOk": true$playbackContextBlock$poTokenBlock
                }
            """.trimIndent()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toRequestBody(mediaType))
                .addHeader("User-Agent", userAgent)
                .addHeader("Content-Type", "application/json")

            if (!isNativeClient) {
                reqBuilder.addHeader("Origin", "https://www.youtube.com")
                reqBuilder.addHeader("Referer", "https://www.youtube.com/")
            }
            if (useAuthCookies && !isNativeClient) {
                val cookieHeader = com.example.util.CookieHelper.getAggregatedCookies().ifBlank {
                    com.example.data.remote.NPDownloader.savedCookies
                }
                if (cookieHeader.isNotBlank()) {
                    reqBuilder.addHeader("Cookie", cookieHeader)
                    val sapisid = com.example.util.CookieHelper.extractSapisid(cookieHeader)
                    if (!sapisid.isNullOrBlank()) {
                        val auth = com.example.util.CookieHelper.generateSapisidHash(sapisid)
                        if (auth.isNotBlank()) {
                            reqBuilder.addHeader("Authorization", auth)
                            reqBuilder.addHeader("X-Origin", "https://www.youtube.com")
                        }
                    }
                }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    return org.json.JSONObject(body)
                } else {
                    logD("YouTubeStreamExtractor", "fetchInnertubePlayer ($clientName, auth=$useAuthCookies) HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            logD("YouTubeStreamExtractor", "fetchInnertubePlayer ($clientName) error: ${e.message}")
        }
        return null
    }

    /**
     * Extracts all available stream resolutions and their direct URLs in a single network pass.
     */
    suspend fun extractVideoStreams(videoId: String): StreamExtractionResult = withContext(Dispatchers.IO) {
        val cached = streamCache[videoId]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < STREAM_CACHE_TTL_MS) {
            if (!cached.result.primaryStreamUrl.isNullOrEmpty()) {
                logD("YouTubeStreamExtractor", "⚡ Instant in-memory stream cache hit for $videoId")
                return@withContext cached.result
            }
        }

        val qualityMap = mutableMapOf<String, String>()
        val videoOnlyQualities = mutableSetOf<String>()
        val videoOnlyUrls = mutableSetOf<String>()
        val muxedUrls = mutableSetOf<String>()
        var bestAudioUrl: String? = null
        var bestCombinedUrl: String? = null
        var extractedClientUa: String? = null

        // 1. PRIMARY: NewPipe Extractor (Resolves direct Apple HLS Master Playlist & adaptive streams)
        try {
            kotlinx.coroutines.withTimeout(8000L) {
                logD("YouTubeStreamExtractor", "[NewPipe] Extracting streams for videoId: $videoId")
                val service = org.schabi.newpipe.extractor.ServiceList.YouTube
                val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                extractor.fetchPage()

                val audioStreams = try { extractor.audioStreams } catch (e: Exception) { emptyList() }
                val sortedAudioStreams = audioStreams.filter { !it.content.isNullOrBlank() }.sortedWith { a, b ->
                    fun score(s: org.schabi.newpipe.extractor.stream.AudioStream): Int {
                        var score = 100
                        val lang = s.audioLocale?.language?.lowercase().orEmpty()
                        val trackName = s.audioTrackName?.lowercase().orEmpty()
                        val trackType = try { s.audioTrackType?.name?.uppercase().orEmpty() } catch (e: Throwable) { "" }
                        val fmt = s.format?.name?.uppercase().orEmpty()
                        if (lang == "en" || lang.startsWith("en-") || trackName.contains("english")) score += 500
                        if (trackType == "ORIGINAL" || trackName.contains("original") || trackName.contains("default")) score += 300
                        if (fmt.contains("M4A") || fmt.contains("AAC") || fmt.contains("MP4")) score += 50
                        score += (s.averageBitrate / 10).coerceIn(0, 30)
                        return score
                    }
                    score(b).compareTo(score(a))
                }
                if (bestAudioUrl == null) {
                    bestAudioUrl = sortedAudioStreams.firstOrNull()?.content
                }

                val videoStreams = try { extractor.videoStreams } catch (e: Exception) { emptyList() }
                for (s in videoStreams) {
                    if (!s.isVideoOnly && !s.content.isNullOrBlank()) {
                        val r = s.resolution?.trim()
                        if (!r.isNullOrBlank()) {
                            val key = if (r.endsWith("p", ignoreCase = true)) r.lowercase() else "${r}p"
                            if (!qualityMap.containsKey(key)) {
                                qualityMap[key] = s.content
                                muxedUrls.add(s.content)
                                if (bestCombinedUrl == null) bestCombinedUrl = s.content
                            }
                        }
                    }
                }

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
                                    videoOnlyUrls.add(s.content)
                                }
                            }
                        }
                    }
                }
                val hlsUrl = try { extractor.hlsUrl } catch (e: Exception) { null }
                if (!hlsUrl.isNullOrBlank() && hlsUrl.startsWith("http")) {
                    qualityMap["HLS"] = hlsUrl
                    logD("YouTubeStreamExtractor", "[NewPipe] Found HLS Master Playlist: ${hlsUrl.take(60)}...")
                }
                if (qualityMap.isNotEmpty() && extractedClientUa == null) {
                    extractedClientUa = try {
                        org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent(
                            org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization()
                        )
                    } catch (_: Exception) {
                        "com.google.visionos.youtube/1.4(RealityDevice14,1; U; CPU visionOS 2_0 like Mac OS X; GB)"
                    }
                }
            }
        } catch (e: Exception) {
            val errMsg = e.message.orEmpty()
            logD("YouTubeStreamExtractor", "[NewPipe] Extraction error for $videoId: $errMsg")
            // Immediate fast-fail ONLY for true members-only, paywalled, private, or unavailable videos.
            // Do NOT fast-fail on "sign in to confirm" / bot-check or age-restriction so InnerTube clients (IOS, ANDROID_VR, TVHTML5) can still extract streams!
            if (errMsg.contains("members", ignoreCase = true) ||
                errMsg.contains("private", ignoreCase = true) ||
                errMsg.contains("unavailable", ignoreCase = true) ||
                errMsg.contains("paid", ignoreCase = true) ||
                errMsg.contains("join this channel", ignoreCase = true)) {
                logD("YouTubeStreamExtractor", "🔒 Members-only / unavailable video identified for $videoId - fast failing immediately")
                return@withContext StreamExtractionResult(
                    primaryStreamUrl = null,
                    isMembersOnly = errMsg.contains("members", ignoreCase = true) || errMsg.contains("join this channel", ignoreCase = true),
                    errorMessage = errMsg
                )
            }
        }

        // 2. SECONDARY: Innertube Fallback (ANDROID_VR / IOS / TVHTML5 / ANDROID / WEB+BotGuard poToken)
        if (qualityMap.isEmpty()) {
            val clients = listOf("ANDROID_VR", "IOS", "TVHTML5", "ANDROID", "WEB")
            for (c in clients) {
                try {
                    val webPoToken = if (c == "WEB") {
                        BotGuardPoTokenProvider.getWebClientPoToken(videoId)
                    } else null
                    val streamingPot = webPoToken?.streamingDataPoToken

                    var playerJson = fetchInnertubePlayer(videoId, c, useAuthCookies = false, poToken = webPoToken)
                    var playability = playerJson?.optJSONObject("playabilityStatus")
                    var status = playability?.optString("status", "")
                    var reason = playability?.optString("reason", "") ?: ""

                    // Retry browser clients with user auth cookies ONLY if anonymous attempt hit age/login gate
                    if ((c == "TVHTML5" || c == "WEB") &&
                        (status == "LOGIN_REQUIRED" || status == "UNPLAYABLE") &&
                        (reason.contains("age", ignoreCase = true) || reason.contains("sign in", ignoreCase = true))
                    ) {
                        playerJson = fetchInnertubePlayer(videoId, c, useAuthCookies = true, poToken = webPoToken)
                        playability = playerJson?.optJSONObject("playabilityStatus")
                        status = playability?.optString("status", "")
                        reason = playability?.optString("reason", "") ?: ""
                    }

                    if (playerJson != null) {
                        if (status == "UNPLAYABLE" && (reason.contains("member", ignoreCase = true) || reason.contains("paid", ignoreCase = true))) {
                            return@withContext StreamExtractionResult(
                                primaryStreamUrl = null,
                                isMembersOnly = true,
                                errorMessage = reason
                            )
                        }
                        if ((status == "LOGIN_REQUIRED" || status == "UNPLAYABLE") &&
                            (reason.contains("age", ignoreCase = true) || reason.contains("sign in", ignoreCase = true) || reason.contains("bot", ignoreCase = true))) {
                            logD("YouTubeStreamExtractor", "⚠️ Client $c returned $status ($reason) - trying next InnerTube client")
                            continue
                        }

                        val streamingData = playerJson.optJSONObject("streamingData")
                        if (streamingData != null) {
                            val rawHlsUrl = streamingData.optString("hlsManifestUrl", "")
                            if (rawHlsUrl.isNotBlank() && rawHlsUrl.startsWith("http") && !qualityMap.containsKey("HLS")) {
                                val hlsUrl = if (!streamingPot.isNullOrBlank() && !rawHlsUrl.contains("pot=")) {
                                    if (rawHlsUrl.contains("?")) "$rawHlsUrl&pot=$streamingPot" else "$rawHlsUrl?pot=$streamingPot"
                                } else rawHlsUrl
                                qualityMap["HLS"] = hlsUrl
                                if (extractedClientUa == null) {
                                    extractedClientUa = when (c) {
                                        "IOS" -> "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
                                        "TVHTML5" -> "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/6.0 TV Safari/538.1"
                                        "WEB" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                                        "ANDROID" -> "com.google.android.youtube/20.10.38 (Linux; U; Android 15; US) gzip"
                                        else -> "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                                    }
                                }
                                logD("YouTubeStreamExtractor", "[Innertube:$c] Found HLS Master Playlist: ${hlsUrl.take(60)}...")
                            }
                            val formats = streamingData.optJSONArray("formats")
                            if (formats != null) {
                                for (i in 0 until formats.length()) {
                                    val f = formats.getJSONObject(i)
                                    val url = resolveFormatUrl(videoId, f, streamingPot)
                                    val qLabel = f.optString("qualityLabel", "")
                                    val height = f.optInt("height", 0)
                                    if (url.isNotBlank() && url.startsWith("http")) {
                                        if (extractedClientUa == null) {
                                            extractedClientUa = when (c) {
                                                "IOS" -> "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
                                                "TVHTML5" -> "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/6.0 TV Safari/538.1"
                                                "WEB" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                                                "ANDROID" -> "com.google.android.youtube/20.10.38 (Linux; U; Android 15; US) gzip"
                                                else -> "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                                            }
                                        }
                                        val key = when {
                                            qLabel.isNotBlank() -> if (qLabel.endsWith("p", true)) qLabel.lowercase() else "${qLabel}p"
                                            height > 0 -> "${height}p"
                                            else -> "360p"
                                        }
                                        qualityMap[key] = url
                                        muxedUrls.add(url)
                                        if (bestCombinedUrl == null || height >= 720) {
                                            bestCombinedUrl = url
                                        }
                                    }
                                }
                            }
                            val adaptive = streamingData.optJSONArray("adaptiveFormats")
                            if (adaptive != null) {
                                for (i in 0 until adaptive.length()) {
                                    val f = adaptive.getJSONObject(i)
                                    val mime = f.optString("mimeType", "")
                                    val url = resolveFormatUrl(videoId, f, streamingPot)
                                    if (url.isNotBlank() && url.startsWith("http")) {
                                        if (extractedClientUa == null) {
                                            extractedClientUa = when (c) {
                                                "IOS" -> "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
                                                "TVHTML5" -> "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/6.0 TV Safari/538.1"
                                                "WEB" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                                                "ANDROID" -> "com.google.android.youtube/20.10.38 (Linux; U; Android 15; US) gzip"
                                                else -> "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                                            }
                                        }
                                        if (mime.contains("video")) {
                                            val qLabel = f.optString("qualityLabel", "")
                                            val height = f.optInt("height", 0)
                                            val key = when {
                                                qLabel.isNotBlank() -> if (qLabel.endsWith("p", true)) qLabel.lowercase() else "${qLabel}p"
                                                height > 0 -> "${height}p"
                                                else -> "720p"
                                            }
                                            qualityMap[key] = url
                                            videoOnlyQualities.add(key)
                                            videoOnlyUrls.add(url)
                                        } else if (mime.contains("audio") && bestAudioUrl == null) {
                                            bestAudioUrl = url
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (qualityMap.isNotEmpty()) break
                } catch (e: Exception) { }
            }
        }

        // 3. TERTIARY: Quick Single Invidious Fallback (Strict 2s limit)
        if (qualityMap.isEmpty()) {
            val fallbackApis = listOf(
                "https://invidious.flokinet.to/api/v1/videos/$videoId?local=true",
                "https://inv.nadeko.net/api/v1/videos/$videoId?local=true"
            )
            for (apiUrl in fallbackApis) {
                try {
                    val req = Request.Builder().url(apiUrl).get().build()
                    val call = client.newBuilder()
                        .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        .newCall(req)

                    call.execute().use { resp ->
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
                                        muxedUrls.add(streamUrl)
                                        if (bestCombinedUrl == null) bestCombinedUrl = streamUrl
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

        // Prioritize Apple HLS Master adaptive streams (Zero-stall, no A/V desync), then HD muxed/adaptive
        val bestUrl = qualityMap["HLS"]
            ?: sortedQualities.firstOrNull { it == "1080p" || it == "720p" }?.let { qualityMap[it] }
            ?: bestCombinedUrl
            ?: sortedQualities.firstOrNull()?.let { qualityMap[it] }
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

        val result = StreamExtractionResult(
            primaryStreamUrl = bestUrl,
            audioStreamUrl = bestAudioUrl,
            combinedMuxedUrl = bestCombinedUrl,
            availableQualities = finalQualitiesList,
            qualityUrlMap = qualityMap,
            videoOnlyQualities = videoOnlyQualities,
            videoOnlyUrls = videoOnlyUrls,
            muxedUrls = muxedUrls,
            clientUserAgent = extractedClientUa
        )

        if (!bestUrl.isNullOrEmpty()) {
            streamCache[videoId] = CachedStream(result, System.currentTimeMillis())
        }

        return@withContext result
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

    private fun extractStreamsFromPlayerResponse(playerResponse: org.json.JSONObject): Pair<String?, String?> {
        var videoUrl: String? = null
        var audioUrl: String? = null
        try {
            val streamingData = playerResponse.optJSONObject("streamingData") ?: return Pair(null, null)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val url = f.optString("url")
                    if (url.isNotEmpty() && url.startsWith("http")) {
                        // formats has muxed video+audio
                        return Pair(url, null)
                    }
                }
            }
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null && adaptive.length() > 0) {
                var bestAudioScore = -1000
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.getJSONObject(i)
                    val mime = f.optString("mimeType")
                    val url = f.optString("url")
                    if (url.isNotEmpty() && url.startsWith("http")) {
                        if (mime.contains("video") && videoUrl == null) {
                            videoUrl = url
                        } else if (mime.contains("audio")) {
                            val audioTrack = f.optJSONObject("audioTrack")
                            val displayName = audioTrack?.optString("displayName", "").orEmpty().lowercase()
                            val isDefault = audioTrack?.optBoolean("audioIsDefault", false) == true
                            var score = 100
                            if (displayName.contains("english") || displayName.contains("original") || isDefault) score += 500
                            if (displayName.contains("dubbed") && !displayName.contains("english")) score -= 500
                            if (score > bestAudioScore) {
                                bestAudioScore = score
                                audioUrl = url
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logD("YouTubeStreamExtractor", "extractStreamsFromPlayerResponse error: ${e.message}")
        }
        return Pair(videoUrl, audioUrl)
    }

    private fun resolveFormatUrl(
        videoId: String,
        formatJson: org.json.JSONObject,
        streamingPot: String?
    ): String {
        var rawUrl = formatJson.optString("url", "")
        if (rawUrl.isBlank()) {
            val cipher = formatJson.optString("signatureCipher", formatJson.optString("cipher", ""))
            if (cipher.isNotBlank()) {
                rawUrl = parseCipher(cipher, videoId)
            }
        }
        if (rawUrl.isBlank()) return ""

        val nResolvedUrl = try {
            org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
                .getUrlWithThrottlingParameterDeobfuscated(videoId, rawUrl)
        } catch (_: Exception) {
            rawUrl
        }

        return if (!streamingPot.isNullOrBlank() && !nResolvedUrl.contains("pot=")) {
            if (nResolvedUrl.contains("?")) "$nResolvedUrl&pot=$streamingPot" else "$nResolvedUrl?pot=$streamingPot"
        } else {
            nResolvedUrl
        }
    }

    private fun parseCipher(cipher: String, videoId: String = ""): String {
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
                    val deobfSig = if (videoId.isNotBlank()) {
                        try {
                            org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
                                .deobfuscateSignature(videoId, sig)
                        } catch (_: Exception) {
                            sig
                        }
                    } else sig
                    "$url&$sp=${java.net.URLEncoder.encode(deobfSig, "UTF-8")}"
                } else {
                    url
                }
            } else ""
        } catch (e: Exception) {
            ""
        }
    }
}
