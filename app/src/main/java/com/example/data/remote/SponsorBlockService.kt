package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class SponsorSegment(
    val category: String,
    val startMs: Long,
    val endMs: Long
)

object SponsorBlockService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
    }

    /**
     * Fetches crowd-sourced sponsor skip segments for a YouTube video from SponsorBlock API.
     */
    suspend fun getSponsorSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        try {
            val categoriesJson = """["sponsor","selfpromo","interaction","intro","outro","preview","filler"]"""
            val encodedCategories = java.net.URLEncoder.encode(categoriesJson, "UTF-8")
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$encodedCategories"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "YouTubeAdFreeAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logD("SponsorBlockService", "No sponsor segments found for $videoId (HTTP ${response.code})")
                    return@use emptyList()
                }

                val bodyString = response.body?.string() ?: return@use emptyList()
                val jsonArray = JSONArray(bodyString)
                val resultSegments = mutableListOf<SponsorSegment>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val category = obj.optString("category", "sponsor")
                    val segmentArray = obj.getJSONArray("segment")
                    if (segmentArray.length() >= 2) {
                        val startSec = segmentArray.getDouble(0)
                        val endSec = segmentArray.getDouble(1)
                        val startMs = (startSec * 1000).toLong()
                        val endMs = (endSec * 1000).toLong()

                        resultSegments.add(
                            SponsorSegment(
                                category = category,
                                startMs = startMs,
                                endMs = endMs
                            )
                        )
                    }
                }

                logD("SponsorBlockService", "Successfully fetched ${resultSegments.size} SponsorBlock segments for $videoId")
                return@withContext resultSegments
            }
        } catch (e: Exception) {
            logD("SponsorBlockService", "SponsorBlock fetch exception for $videoId: ${e.message}")
            emptyList()
        }
    }
}
