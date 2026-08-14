package com.example.data.remote

import android.content.Context
import android.os.Environment
import com.example.data.model.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object VideoDownloadManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Map of youtubeId -> download progress (0..100) or -1 (error)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    private val activeDownloads = ConcurrentHashMap<String, Boolean>()

    fun getDownloadDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) 
            ?: File(context.filesDir, "offline_videos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getLocalVideoFile(context: Context, youtubeId: String): File {
        return File(getDownloadDir(context), "${youtubeId}.mp4")
    }

    fun isVideoDownloadedLocally(context: Context, youtubeId: String): Boolean {
        val file = getLocalVideoFile(context, youtubeId)
        return file.exists() && file.length() > 1024 * 100 // At least 100KB
    }

    suspend fun downloadVideo(
        context: Context,
        video: VideoEntity,
        onSuccess: (localPath: String, sizeMb: Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val vidId = video.youtubeId
        if (activeDownloads.containsKey(vidId)) {
            return@withContext // Already downloading
        }

        activeDownloads[vidId] = true
        updateProgress(vidId, 1)

        try {
            // 1. Extract direct stream URL
            val streamUrl = YouTubeStreamExtractor.getDirectStreamUrl(vidId, "720p")
                ?: YouTubeStreamExtractor.getDirectStreamUrl(vidId, "Auto")

            if (streamUrl.isNullOrEmpty()) {
                activeDownloads.remove(vidId)
                updateProgress(vidId, -1)
                withContext(Dispatchers.Main) {
                    onError("Unable to extract offline stream URL for this video.")
                }
                return@withContext
            }

            val targetFile = getLocalVideoFile(context, vidId)
            val tempFile = File(getDownloadDir(context), "${vidId}.tmp")

            val request = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressUpdate = 0L

                outputStream.use { out ->
                    inputStream.use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (contentLength > 0 && (now - lastProgressUpdate > 300)) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(1, 99)
                                updateProgress(vidId, progress)
                                lastProgressUpdate = now
                            }
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }
            }

            val fileSizeMb = String.format(java.util.Locale.US, "%.1f", targetFile.length().toFloat() / (1024 * 1024)).toFloatOrNull() ?: 0.0f
            activeDownloads.remove(vidId)
            updateProgress(vidId, 100)

            withContext(Dispatchers.Main) {
                onSuccess(targetFile.absolutePath, fileSizeMb)
            }
        } catch (e: Exception) {
            activeDownloads.remove(vidId)
            updateProgress(vidId, -1)
            withContext(Dispatchers.Main) {
                onError("Download error: ${e.message}")
            }
        }
    }

    fun deleteDownloadedVideo(context: Context, youtubeId: String): Boolean {
        val file = getLocalVideoFile(context, youtubeId)
        val tempFile = File(getDownloadDir(context), "${youtubeId}.tmp")
        if (tempFile.exists()) tempFile.delete()
        val deleted = if (file.exists()) file.delete() else true
        val current = _downloadProgressMap.value.toMutableMap()
        current.remove(youtubeId)
        _downloadProgressMap.value = current
        return deleted
    }

    /**
     * Automatically deletes downloaded offline videos according to user-configured expiry period or completion state.
     */
    suspend fun cleanExpiredDownloads(
        context: Context,
        autoDeleteSetting: String,
        downloadedVideos: List<VideoEntity>,
        onVideoDeleted: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (autoDeleteSetting.equals("Never", ignoreCase = true)) return@withContext
        val now = System.currentTimeMillis()
        val thresholdMs = when (autoDeleteSetting) {
            "24h" -> 24 * 3600 * 1000L
            "48h" -> 48 * 3600 * 1000L
            "7d"  -> 7 * 24 * 3600 * 1000L
            "30d" -> 30 * 24 * 3600 * 1000L
            else  -> Long.MAX_VALUE
        }

        for (video in downloadedVideos) {
            var shouldDelete = false
            val file = getLocalVideoFile(context, video.youtubeId)
            val fileModified = if (file.exists()) file.lastModified() else video.addedTimestamp

            if (autoDeleteSetting.equals("Watched", ignoreCase = true)) {
                // If user watched >= 90% of the video or reached last 15 seconds
                val durSec = com.example.util.YouTubeUtils.parseFormattedTimeToSeconds(video.durationText)
                if (durSec > 0 && video.lastPositionSeconds >= (durSec * 0.9f)) {
                    shouldDelete = true
                }
            } else if (fileModified > 0 && (now - fileModified) >= thresholdMs) {
                shouldDelete = true
            }

            if (shouldDelete) {
                deleteDownloadedVideo(context, video.youtubeId)
                onVideoDeleted(video.youtubeId)
            }
        }
    }

    private fun updateProgress(youtubeId: String, progress: Int) {
        val current = _downloadProgressMap.value.toMutableMap()
        if (progress in 0..100) {
            current[youtubeId] = progress
        } else {
            current.remove(youtubeId)
        }
        _downloadProgressMap.value = current
    }
}
