package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.dao.VideoDao
import com.example.data.model.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VpsSyncManager {
    private const val TAG = "VpsSyncManager"
    private const val PREFS_NAME = "vixz_vps_sync"
    private const val KEY_SERVER_URL = "vps_server_url"
    private const val KEY_API_KEY = "vps_api_key"
    private const val KEY_SYNC_ENABLED = "vps_sync_enabled"
    private const val KEY_LAST_SYNC_TIME = "vps_last_sync_time"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SERVER_URL, "")?.trim().orEmpty()
        if (saved.isNotBlank()) return saved

        val defaultUrl = try {
            com.example.BuildConfig.VPS_DEFAULT_URL.trim()
        } catch (e: Throwable) { "" }
        if (defaultUrl.isNotBlank()) {
            prefs.edit().putString(KEY_SERVER_URL, defaultUrl).apply()
            return defaultUrl
        }
        return ""
    }

    fun setServerUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_API_KEY, "")?.trim().orEmpty()
        if (saved.isNotBlank()) return saved

        val defaultKey = try {
            com.example.BuildConfig.VPS_DEFAULT_KEY.trim()
        } catch (e: Throwable) { "" }
        if (defaultKey.isNotBlank()) {
            prefs.edit().putString(KEY_API_KEY, defaultKey).apply()
            return defaultKey
        }
        return ""
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun isSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SYNC_ENABLED, true)
    }

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    fun getLastSyncTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    private fun setLastSyncTime(context: Context, time: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }

    private fun buildRequest(url: String, apiKey: String, builderAction: Request.Builder.() -> Unit = {}): Request {
        val reqBuilder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("X-API-Key", apiKey)
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        reqBuilder.builderAction()
        return reqBuilder.build()
    }

    suspend fun testConnection(context: Context, serverUrl: String, apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) {
            return@withContext Pair(false, "Please enter a VPS server URL.")
        }
        try {
            val req = buildRequest("$cleanUrl/health", apiKey) { get() }
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, "Connected successfully! VPS sync server is healthy.")
                } else {
                    Pair(false, "Server returned HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed", e)
            Pair(false, "Connection error: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun notifyWatched(
        context: Context,
        youtubeId: String,
        title: String = "",
        channel: String = "",
        duration: String = "",
        thumbnail: String = "",
        positionSeconds: Int = 0
    ) = withContext(Dispatchers.IO) {
        if (!isSyncEnabled(context)) return@withContext
        val serverUrl = getServerUrl(context)
        if (serverUrl.isBlank() || youtubeId.isBlank()) return@withContext

        try {
            val apiKey = getApiKey(context)
            val json = JSONObject().apply {
                put("video_id", youtubeId)
                put("title", title)
                put("channel", channel)
                put("duration", duration)
                put("thumbnail", thumbnail)
                put("position", positionSeconds)
                put("watched_at", System.currentTimeMillis())
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = buildRequest("$serverUrl/api/v1/sync/watched", apiKey) { post(body) }
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "notifyWatched HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "notifyWatched failed: ${e.message}")
        }
    }

    suspend fun syncWithServer(
        context: Context,
        videoDao: VideoDao,
        subscribedChannels: List<String> = emptyList(),
        onSubscriptionsUpdated: ((List<String>) -> Unit)? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isSyncEnabled(context)) {
            return@withContext Pair(false, "VPS Sync is disabled in settings.")
        }
        val serverUrl = getServerUrl(context)
        if (serverUrl.isBlank()) {
            return@withContext Pair(false, "VPS server URL is not configured.")
        }
        val apiKey = getApiKey(context)

        try {
            // 1. Gather local watched videos and preferences
            val allVideos = videoDao.getAllVideosDirect()
            val watchedEntities = allVideos.filter { it.lastWatchedTimestamp > 0L || it.lastPositionSeconds > 0 }
            val favorites = allVideos.filter { it.isFavorite }.map { it.youtubeId }

            val watchedJsonArray = JSONArray()
            watchedEntities.forEach { v ->
                val obj = JSONObject().apply {
                    put("video_id", v.youtubeId)
                    put("title", v.title)
                    put("channel", v.channelName)
                    put("duration", v.durationText)
                    put("thumbnail", v.thumbnailUrl)
                    put("position", v.lastPositionSeconds)
                    put("watched_at", v.lastWatchedTimestamp)
                }
                watchedJsonArray.put(obj)
            }

            val favJsonArray = JSONArray()
            favorites.forEach { favJsonArray.put(it) }

            val subJsonArray = JSONArray()
            subscribedChannels.forEach { subJsonArray.put(it) }

            val payload = JSONObject().apply {
                put("client_id", "vixz-android")
                put("watched", watchedJsonArray)
                put("favorites", favJsonArray)
                put("disliked_videos", JSONArray())
                put("disliked_channels", JSONArray())
                put("subscribed_channels", subJsonArray)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = buildRequest("$serverUrl/api/v1/sync/full", apiKey) { post(body) }

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Pair(false, "Server returned HTTP ${response.code}: ${response.message}")
                }
                val respStr = response.body?.string().orEmpty()
                val respJson = JSONObject(respStr)

                var mergedCount = 0
                val serverWatched = respJson.optJSONArray("watched")
                if (serverWatched != null) {
                    for (i in 0 until serverWatched.length()) {
                        val item = serverWatched.optJSONObject(i) ?: continue
                        val vid = item.optString("video_id")
                        if (vid.isBlank()) continue

                        val pos = item.optInt("position", 0)
                        val watchedAt = item.optLong("watched_at", System.currentTimeMillis())
                        val title = item.optString("title", "Video $vid")
                        val channel = item.optString("channel", "YouTube")
                        val thumb = item.optString("thumbnail", "https://img.youtube.com/vi/$vid/hqdefault.jpg")
                        val duration = item.optString("duration", "10:00")

                        val existing = videoDao.getVideoById(vid)
                        if (existing != null) {
                            val newTimestamp = maxOf(existing.lastWatchedTimestamp, watchedAt)
                            val newPos = if (pos > 0) maxOf(existing.lastPositionSeconds, pos) else existing.lastPositionSeconds
                            videoDao.updateWatchHistory(vid, newTimestamp, newPos)
                        } else {
                            videoDao.insertVideo(
                                VideoEntity(
                                    youtubeId = vid,
                                    title = title,
                                    channelName = channel,
                                    thumbnailUrl = thumb,
                                    durationText = duration,
                                    lastWatchedTimestamp = watchedAt,
                                    lastPositionSeconds = pos
                                )
                            )
                            mergedCount++
                        }
                    }
                }
                val serverSubs = respJson.optJSONArray("subscribed_channels") ?: respJson.optJSONArray("subscribedChannels")
                if (serverSubs != null && serverSubs.length() > 0) {
                    val incoming = mutableListOf<String>()
                    for (i in 0 until serverSubs.length()) {
                        val s = serverSubs.optString(i, "").trim()
                        if (s.isNotBlank()) incoming.add(s)
                    }
                    if (incoming.isNotEmpty()) {
                        onSubscriptionsUpdated?.invoke(incoming)
                    }
                }

                setLastSyncTime(context, System.currentTimeMillis())
                val totalWatched = watchedEntities.size + mergedCount
                Pair(true, "Synchronized $totalWatched watched videos with VPS.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncWithServer failed", e)
            Pair(false, "Sync error: ${e.message ?: "Unknown error"}")
        }
    }
}
