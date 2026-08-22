package com.example.data.remote

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.model.VideoEntity
import com.example.util.MediaMuxerHelper
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
    private const val TAG = "VideoDownloadManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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

        val targetFile = getLocalVideoFile(context, vidId)
        val tempVideoFile = File(getDownloadDir(context), "${vidId}_video.tmp")
        val tempAudioFile = File(getDownloadDir(context), "${vidId}_audio.tmp")
        val tempDirectFile = File(getDownloadDir(context), "${vidId}.tmp")

        try {
            // 1. Extract direct stream URLs
            val extractionResult = YouTubeStreamExtractor.extractVideoStreams(vidId)

            val combinedUrl = extractionResult.combinedMuxedUrl
            val videoOnlyUrl = extractionResult.qualityUrlMap["720p"]
                ?: extractionResult.qualityUrlMap["1080p"]
                ?: extractionResult.qualityUrlMap["480p"]
                ?: extractionResult.primaryStreamUrl
            val audioUrl = extractionResult.audioStreamUrl

            val isVideoOnly = extractionResult.isVideoOnlyStream(videoOnlyUrl) ||
                    (combinedUrl.isNullOrBlank() && !audioUrl.isNullOrBlank())

            Log.d(TAG, "Download plan for $vidId: combinedUrl=${!combinedUrl.isNullOrBlank()}, videoOnly=$isVideoOnly, audioUrl=${!audioUrl.isNullOrBlank()}")

            // Strategy A: Dedicated Combined (Muxed Video + Audio) Stream exists
            if (!combinedUrl.isNullOrBlank()) {
                Log.d(TAG, "Downloading combined muxed stream directly for $vidId")
                downloadToFile(
                    url = combinedUrl,
                    destFile = tempDirectFile,
                    startProgress = 1,
                    endProgress = 99,
                    onProgress = { p -> updateProgress(vidId, p) }
                )
                if (tempDirectFile.exists() && tempDirectFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempDirectFile.renameTo(targetFile)
                }
            }
            // Strategy B: Separate Video and Audio tracks (Download both + Native Mux to MP4)
            else if (!videoOnlyUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                Log.d(TAG, "Downloading separate video (0..75%) and audio (75..92%) streams for $vidId")

                // Download video track
                downloadToFile(
                    url = videoOnlyUrl,
                    destFile = tempVideoFile,
                    startProgress = 1,
                    endProgress = 75,
                    onProgress = { p -> updateProgress(vidId, p) }
                )

                // Download audio track
                downloadToFile(
                    url = audioUrl,
                    destFile = tempAudioFile,
                    startProgress = 75,
                    endProgress = 92,
                    onProgress = { p -> updateProgress(vidId, p) }
                )

                // Mux video and audio together into targetFile
                updateProgress(vidId, 95)
                Log.d(TAG, "Muxing video + audio into target MP4...")
                val muxSuccess = MediaMuxerHelper.muxVideoAndAudio(tempVideoFile, tempAudioFile, targetFile)

                // Clean up temp files
                if (tempVideoFile.exists()) tempVideoFile.delete()
                if (tempAudioFile.exists()) tempAudioFile.delete()

                if (!muxSuccess || !targetFile.exists() || targetFile.length() == 0L) {
                    throw Exception("Failed to mux audio and video streams.")
                }
            }
            // Strategy C: Direct fallback to best available stream
            else if (!videoOnlyUrl.isNullOrBlank()) {
                Log.d(TAG, "Downloading fallback direct stream for $vidId")
                downloadToFile(
                    url = videoOnlyUrl,
                    destFile = tempDirectFile,
                    startProgress = 1,
                    endProgress = 99,
                    onProgress = { p -> updateProgress(vidId, p) }
                )
                if (tempDirectFile.exists() && tempDirectFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempDirectFile.renameTo(targetFile)
                }
            } else {
                throw Exception("Unable to extract offline stream URL for this video.")
            }

            val fileSizeMb = String.format(java.util.Locale.US, "%.1f", targetFile.length().toFloat() / (1024 * 1024)).toFloatOrNull() ?: 0.0f
            activeDownloads.remove(vidId)
            updateProgress(vidId, 100)

            withContext(Dispatchers.Main) {
                onSuccess(targetFile.absolutePath, fileSizeMb)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $vidId: ${e.message}", e)
            activeDownloads.remove(vidId)
            updateProgress(vidId, -1)
            if (tempVideoFile.exists()) tempVideoFile.delete()
            if (tempAudioFile.exists()) tempAudioFile.delete()
            if (tempDirectFile.exists()) tempDirectFile.delete()

            withContext(Dispatchers.Main) {
                onError("Download error: ${e.message}")
            }
        }
    }

    private fun downloadToFile(
        url: String,
        destFile: File,
        startProgress: Int,
        endProgress: Int,
        onProgress: (Int) -> Unit
    ) {
        if (destFile.exists()) destFile.delete()

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Cookie", "PREF=f6=40000000&hl=en&gl=US; SOCS=CAI")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastProgressUpdate = 0L

            outputStream.use { out ->
                inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (contentLength > 0 && (now - lastProgressUpdate > 250)) {
                            val fraction = totalBytesRead.toFloat() / contentLength
                            val scaledProgress = (startProgress + fraction * (endProgress - startProgress)).toInt().coerceIn(startProgress, endProgress)
                            onProgress(scaledProgress)
                            lastProgressUpdate = now
                        }
                    }
                }
            }
        }
    }

    fun deleteDownloadedVideo(context: Context, youtubeId: String): Boolean {
        val file = getLocalVideoFile(context, youtubeId)
        val tempFile = File(getDownloadDir(context), "${youtubeId}.tmp")
        val tempVideoFile = File(getDownloadDir(context), "${youtubeId}_video.tmp")
        val tempAudioFile = File(getDownloadDir(context), "${youtubeId}_audio.tmp")

        if (tempFile.exists()) tempFile.delete()
        if (tempVideoFile.exists()) tempVideoFile.delete()
        if (tempAudioFile.exists()) tempAudioFile.delete()

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
