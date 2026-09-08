package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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

    private const val PREFS_DOWNLOAD_NAME = "vixz_download_prefs"
    private const val KEY_LOCATION_TYPE = "download_location_type"
    private const val KEY_CUSTOM_PATH = "download_location_custom_path"
    private const val KEY_CUSTOM_URI = "download_location_custom_uri"

    data class DownloadLocationInfo(
        val type: String, // "DEFAULT", "MOVIES", "DOWNLOADS", "SD_CARD", "CUSTOM"
        val displayName: String,
        val path: String,
        val isAvailable: Boolean = true
    )

    fun getAvailableLocations(context: Context): List<DownloadLocationInfo> {
        val list = mutableListOf<DownloadLocationInfo>()

        // 1. Default App Storage (.offline_videos)
        val defaultDir = File(context.getExternalFilesDir(null) ?: context.filesDir, ".offline_videos")
        list.add(
            DownloadLocationInfo(
                type = "DEFAULT",
                displayName = "App Storage (.offline_videos)",
                path = defaultDir.absolutePath,
                isAvailable = true
            )
        )

        // 2. Movies Directory
        val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "Vixz_Movies")
        list.add(
            DownloadLocationInfo(
                type = "MOVIES",
                displayName = "Movies Folder",
                path = moviesDir.absolutePath,
                isAvailable = true
            )
        )

        // 3. Downloads Directory
        val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Vixz_Downloads")
        list.add(
            DownloadLocationInfo(
                type = "DOWNLOADS",
                displayName = "Downloads Folder",
                path = downloadsDir.absolutePath,
                isAvailable = true
            )
        )

        // 4. MicroSD Card / Secondary External Storage (if detected)
        try {
            val extDirs = context.getExternalFilesDirs(null)
            if (extDirs.size > 1 && extDirs[1] != null) {
                val sdDir = File(extDirs[1], "Vixz_Offline")
                list.add(
                    DownloadLocationInfo(
                        type = "SD_CARD",
                        displayName = "MicroSD Card",
                        path = sdDir.absolutePath,
                        isAvailable = true
                    )
                )
            }
        } catch (e: Throwable) { }

        // 5. Custom selected path (if user configured one)
        val prefs = context.getSharedPreferences(PREFS_DOWNLOAD_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_PATH, null)
        val customUriStr = prefs.getString(KEY_CUSTOM_URI, null)
            ?: context.contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }?.uri?.toString()

        if (!customPath.isNullOrBlank() || !customUriStr.isNullOrBlank()) {
            var isAvail = false
            var folderName = "Selected Folder"
            if (!customUriStr.isNullOrBlank()) {
                try {
                    val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(customUriStr))
                    if (treeDoc != null && treeDoc.exists() && treeDoc.canWrite()) {
                        isAvail = true
                        folderName = treeDoc.name ?: "Selected Folder"
                    }
                } catch (e: Throwable) { }
            }
            if (!isAvail && !customPath.isNullOrBlank()) {
                val customFile = File(customPath)
                isAvail = try { (customFile.exists() || customFile.mkdirs()) && customFile.canWrite() } catch (e: Exception) { false }
                if (customFile.name.isNotBlank()) folderName = customFile.name
            }

            val displayLabel = if (folderName.isNotBlank() && folderName != "Selected Folder") {
                "Custom: $folderName"
            } else if (!customPath.isNullOrBlank()) {
                "Custom: ${File(customPath).name.ifBlank { "Selected Folder" }}"
            } else {
                "Custom Folder"
            }

            list.add(
                DownloadLocationInfo(
                    type = "CUSTOM",
                    displayName = displayLabel,
                    path = customPath ?: customUriStr ?: "Custom Folder",
                    isAvailable = isAvail || !customUriStr.isNullOrBlank()
                )
            )
        }

        return list
    }

    fun getActiveLocation(context: Context): DownloadLocationInfo {
        val prefs = context.getSharedPreferences(PREFS_DOWNLOAD_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_LOCATION_TYPE, "DEFAULT") ?: "DEFAULT"
        val customPath = prefs.getString(KEY_CUSTOM_PATH, null)
        val customUriStr = prefs.getString(KEY_CUSTOM_URI, null)
            ?: context.contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }?.uri?.toString()

        val locations = getAvailableLocations(context)
        if (type == "CUSTOM" && (!customPath.isNullOrBlank() || !customUriStr.isNullOrBlank())) {
            val customLoc = locations.firstOrNull { it.type == "CUSTOM" }
            if (customLoc != null && customLoc.isAvailable) return customLoc
        }

        return locations.firstOrNull { it.type == type && it.isAvailable }
            ?: locations.firstOrNull { it.type == "DEFAULT" }
            ?: DownloadLocationInfo("DEFAULT", "App Storage (.offline_videos)", File(context.getExternalFilesDir(null) ?: context.filesDir, ".offline_videos").absolutePath)
    }

    fun setDownloadLocation(context: Context, type: String, customPath: String? = null, customUri: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_DOWNLOAD_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_LOCATION_TYPE, type)
        if (!customPath.isNullOrBlank()) {
            editor.putString(KEY_CUSTOM_PATH, customPath)
        }
        if (!customUri.isNullOrBlank()) {
            editor.putString(KEY_CUSTOM_URI, customUri)
        }
        editor.apply()
    }

    fun getDownloadDir(context: Context): File {
        val active = try { getActiveLocation(context) } catch (e: Throwable) { null }
        if (active != null && active.type != "CUSTOM") {
            val targetDir = File(active.path)
            if (!targetDir.exists()) {
                try {
                    targetDir.mkdirs()
                } catch (e: Exception) { }
            }
            if (targetDir.exists() && targetDir.canWrite()) {
                val noMedia = File(targetDir, ".nomedia")
                if (!noMedia.exists()) {
                    try { noMedia.createNewFile() } catch (e: Exception) { }
                }
                return targetDir
            }
        }

        val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, ".offline_videos")
        if (!fallback.exists()) {
            try { fallback.mkdirs() } catch (e: Exception) { }
        }
        val noMedia = File(fallback, ".nomedia")
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (e: Exception) { }
        }
        return fallback
    }

    fun hideLegacyMoviesFromGallery(context: Context) {
        try {
            val legacyDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (legacyDir != null && legacyDir.exists()) {
                val noMedia = File(legacyDir, ".nomedia")
                if (!noMedia.exists()) {
                    noMedia.createNewFile()
                }
            }
        } catch (e: Throwable) { }
    }

    fun getLocalVideoFile(context: Context, youtubeId: String, knownPath: String? = null): File {
        if (!knownPath.isNullOrBlank() && !knownPath.startsWith("content://")) {
            try {
                val directFile = File(knownPath)
                if (directFile.exists() && directFile.length() > 1024 * 100) {
                    return directFile
                }
            } catch (e: Throwable) { }
        }

        val primary = File(getDownloadDir(context), "${youtubeId}.mp4")
        if (primary.exists() && primary.length() > 1024 * 100) return primary

        // Check all other configured/available download locations
        try {
            for (loc in getAvailableLocations(context)) {
                if (loc.type != "CUSTOM") {
                    val candidate = File(loc.path, "${youtubeId}.mp4")
                    if (candidate.exists() && candidate.length() > 1024 * 100) return candidate
                }
            }
        } catch (e: Throwable) { }

        try {
            val legacyDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (legacyDir != null) {
                val legacy = File(legacyDir, "${youtubeId}.mp4")
                if (legacy.exists() && legacy.length() > 1024 * 100) return legacy
            }
        } catch (e: Throwable) { }

        try {
            val internalFile = File(context.filesDir, "${youtubeId}.mp4")
            if (internalFile.exists() && internalFile.length() > 1024 * 100) return internalFile
        } catch (e: Throwable) { }

        return primary
    }

    fun getLocalVideoUriString(context: Context, youtubeId: String, knownPath: String? = null): String? {
        if (!knownPath.isNullOrBlank()) {
            if (knownPath.startsWith("content://")) {
                try {
                    val uri = Uri.parse(knownPath)
                    val doc = DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists() && doc.length() > 1024 * 100) {
                        return knownPath
                    }
                } catch (e: Throwable) { }
            } else {
                try {
                    val directFile = File(knownPath)
                    if (directFile.exists() && directFile.length() > 1024 * 100) {
                        return Uri.fromFile(directFile).toString()
                    }
                } catch (e: Throwable) { }
            }
        }

        // Primary download directory
        val primary = File(getDownloadDir(context), "${youtubeId}.mp4")
        if (primary.exists() && primary.length() > 1024 * 100) {
            return Uri.fromFile(primary).toString()
        }

        // Available locations
        try {
            for (loc in getAvailableLocations(context)) {
                if (loc.type != "CUSTOM") {
                    val candidate = File(loc.path, "${youtubeId}.mp4")
                    if (candidate.exists() && candidate.length() > 1024 * 100) {
                        return Uri.fromFile(candidate).toString()
                    }
                }
            }
        } catch (e: Throwable) { }

        // Custom SAF tree location
        try {
            val prefs = context.getSharedPreferences(PREFS_DOWNLOAD_NAME, Context.MODE_PRIVATE)
            val customUriStr = prefs.getString(KEY_CUSTOM_URI, null)
                ?: context.contentResolver.persistedUriPermissions.firstOrNull { it.isReadPermission }?.uri?.toString()
            if (!customUriStr.isNullOrBlank()) {
                val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(customUriStr))
                val targetDoc = treeDoc?.findFile("${youtubeId}.mp4")
                if (targetDoc != null && targetDoc.exists() && targetDoc.length() > 1024 * 100) {
                    return targetDoc.uri.toString()
                }
            }
        } catch (e: Throwable) { }

        // Legacy Movies directory
        try {
            val legacyDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (legacyDir != null) {
                val legacy = File(legacyDir, "${youtubeId}.mp4")
                if (legacy.exists() && legacy.length() > 1024 * 100) {
                    return Uri.fromFile(legacy).toString()
                }
            }
        } catch (e: Throwable) { }

        // Internal app storage
        try {
            val internalFile = File(context.filesDir, "${youtubeId}.mp4")
            if (internalFile.exists() && internalFile.length() > 1024 * 100) {
                return Uri.fromFile(internalFile).toString()
            }
        } catch (e: Throwable) { }

        return null
    }

    fun isVideoDownloadedLocally(context: Context, youtubeId: String, knownPath: String? = null): Boolean {
        return getLocalVideoUriString(context, youtubeId, knownPath) != null
    }

    suspend fun downloadVideo(
        context: Context,
        video: VideoEntity,
        targetResolution: String = "720p",
        onSuccess: (localPath: String, sizeMb: Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val vidId = video.youtubeId
        if (activeDownloads.containsKey(vidId)) {
            return@withContext // Already downloading
        }

        activeDownloads[vidId] = true
        updateProgress(vidId, 1)

        // All intermediate downloading and muxing uses context.cacheDir
        // Guarantees zero EACCES permission issues across all Android versions
        val cacheFolder = context.cacheDir
        val tempVideoFile = File(cacheFolder, "${vidId}_video.tmp")
        val tempAudioFile = File(cacheFolder, "${vidId}_audio.tmp")
        val completedTempFile = File(cacheFolder, "${vidId}_complete.mp4")

        try {
            if (tempVideoFile.exists()) tempVideoFile.delete()
            if (tempAudioFile.exists()) tempAudioFile.delete()
            if (completedTempFile.exists()) completedTempFile.delete()

            // 1. Extract direct stream URLs
            val extractionResult = YouTubeStreamExtractor.extractVideoStreams(vidId)

            val combinedUrl = extractionResult.combinedMuxedUrl
            val targetResLower = targetResolution.lowercase().trim()
            val videoOnlyUrl = extractionResult.qualityUrlMap[targetResLower]
                ?: extractionResult.qualityUrlMap["1080p"]
                ?: extractionResult.qualityUrlMap["720p"]
                ?: extractionResult.qualityUrlMap["480p"]
                ?: extractionResult.primaryStreamUrl
            val audioUrl = extractionResult.audioStreamUrl

            val isVideoOnly = extractionResult.isVideoOnlyStream(videoOnlyUrl) ||
                    (combinedUrl.isNullOrBlank() && !audioUrl.isNullOrBlank())

            Log.d(TAG, "Download plan for $vidId (target $targetResolution): combinedUrl=${!combinedUrl.isNullOrBlank()}, videoOnly=$isVideoOnly, audioUrl=${!audioUrl.isNullOrBlank()}")

            // Strategy A: Dedicated Combined (Muxed Video + Audio) Stream exists for 720p/360p
            if (!combinedUrl.isNullOrBlank() && (targetResLower == "720p" || targetResLower == "360p" || videoOnlyUrl == combinedUrl)) {
                Log.d(TAG, "Downloading combined muxed stream directly for $vidId")
                downloadToFile(
                    url = combinedUrl,
                    destFile = completedTempFile,
                    startProgress = 1,
                    endProgress = 98,
                    onProgress = { p -> updateProgress(vidId, p) }
                )
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

                // Mux video and audio together into completedTempFile
                updateProgress(vidId, 95)
                Log.d(TAG, "Muxing video + audio into completed MP4...")
                val muxSuccess = MediaMuxerHelper.muxVideoAndAudio(tempVideoFile, tempAudioFile, completedTempFile)

                // Clean up temp files
                if (tempVideoFile.exists()) tempVideoFile.delete()
                if (tempAudioFile.exists()) tempAudioFile.delete()

                if (!muxSuccess || !completedTempFile.exists() || completedTempFile.length() == 0L) {
                    throw Exception("Failed to mux audio and video streams.")
                }
            }
            // Strategy C: Direct fallback to best available stream
            else if (!videoOnlyUrl.isNullOrBlank()) {
                Log.d(TAG, "Downloading fallback direct stream for $vidId")
                downloadToFile(
                    url = videoOnlyUrl,
                    destFile = completedTempFile,
                    startProgress = 1,
                    endProgress = 98,
                    onProgress = { p -> updateProgress(vidId, p) }
                )
            } else {
                throw Exception("Unable to extract offline stream URL for this video.")
            }

            if (!completedTempFile.exists() || completedTempFile.length() == 0L) {
                throw Exception("Downloaded file is empty or missing.")
            }

            updateProgress(vidId, 99)

            // Now move/save completedTempFile to active location
            val active = getActiveLocation(context)
            var finalDestinationPath = ""
            val rawFileLength = completedTempFile.length()

            if (active.type == "CUSTOM") {
                val prefs = context.getSharedPreferences(PREFS_DOWNLOAD_NAME, Context.MODE_PRIVATE)
                val customUriStr = prefs.getString(KEY_CUSTOM_URI, null)
                    ?: context.contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }?.uri?.toString()

                var safSaveSuccess = false
                if (!customUriStr.isNullOrBlank()) {
                    try {
                        val treeUri = Uri.parse(customUriStr)
                        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                        if (treeDoc != null && treeDoc.exists()) {
                            val existing = treeDoc.findFile("${vidId}.mp4")
                            existing?.delete()
                            val newDoc = treeDoc.createFile("video/mp4", "${vidId}.mp4")
                            if (newDoc != null) {
                                context.contentResolver.openOutputStream(newDoc.uri)?.use { outStream ->
                                    completedTempFile.inputStream().use { inStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                                finalDestinationPath = newDoc.uri.toString()
                                safSaveSuccess = true
                                Log.d(TAG, "Successfully wrote video via SAF to: $finalDestinationPath")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed writing via SAF DocumentFile: ${e.message}", e)
                    }
                }

                if (!safSaveSuccess) {
                    val targetFile = File(active.path, "${vidId}.mp4")
                    try {
                        if (targetFile.parentFile?.exists() == false) targetFile.parentFile?.mkdirs()
                        if (targetFile.exists()) targetFile.delete()
                        completedTempFile.inputStream().use { inStream ->
                            FileOutputStream(targetFile).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        finalDestinationPath = targetFile.absolutePath
                    } catch (e: Exception) {
                        // Fallback to App Storage so the user never loses their download
                        val fallbackDir = getDownloadDir(context)
                        val fallbackFile = File(fallbackDir, "${vidId}.mp4")
                        if (fallbackFile.exists()) fallbackFile.delete()
                        completedTempFile.inputStream().use { inStream ->
                            FileOutputStream(fallbackFile).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        finalDestinationPath = fallbackFile.absolutePath
                        Log.w(TAG, "Custom storage was inaccessible via SAF/POSIX; fell back safely to App Storage: $finalDestinationPath")
                    }
                }
            } else {
                val targetDir = getDownloadDir(context)
                val targetFile = File(targetDir, "${vidId}.mp4")
                if (targetFile.exists()) targetFile.delete()
                completedTempFile.inputStream().use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                finalDestinationPath = targetFile.absolutePath
            }

            if (completedTempFile.exists()) completedTempFile.delete()

            val fileSizeMb = String.format(java.util.Locale.US, "%.1f", rawFileLength.toFloat() / (1024 * 1024)).toFloatOrNull() ?: 0.0f
            activeDownloads.remove(vidId)
            updateProgress(vidId, 100)

            withContext(Dispatchers.Main) {
                onSuccess(finalDestinationPath, fileSizeMb)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $vidId: ${e.message}", e)
            activeDownloads.remove(vidId)
            updateProgress(vidId, -1)
            if (tempVideoFile.exists()) tempVideoFile.delete()
            if (tempAudioFile.exists()) tempAudioFile.delete()
            if (completedTempFile.exists()) completedTempFile.delete()

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

    fun deleteDownloadedVideo(context: Context, youtubeId: String, knownPath: String? = null): Boolean {
        var deletedAny = false

        if (!knownPath.isNullOrBlank()) {
            if (knownPath.startsWith("content://")) {
                try {
                    val uri = Uri.parse(knownPath)
                    val doc = DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists() && doc.delete()) {
                        deletedAny = true
                    }
                } catch (e: Throwable) { }
            } else {
                try {
                    val directFile = File(knownPath)
                    if (directFile.exists() && directFile.delete()) {
                        deletedAny = true
                    }
                } catch (e: Throwable) { }
            }
        }

        // Also check custom tree if exists
        try {
            val prefs = context.getSharedPreferences(PREFS_DOWNLOAD_NAME, Context.MODE_PRIVATE)
            val customUriStr = prefs.getString(KEY_CUSTOM_URI, null)
                ?: context.contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }?.uri?.toString()
            if (!customUriStr.isNullOrBlank()) {
                val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(customUriStr))
                val targetDoc = treeDoc?.findFile("${youtubeId}.mp4")
                if (targetDoc != null && targetDoc.exists() && targetDoc.delete()) {
                    deletedAny = true
                }
            }
        } catch (e: Throwable) { }

        // Clean temp cache files
        val tempFile = File(context.cacheDir, "${youtubeId}.tmp")
        val tempVideoFile = File(context.cacheDir, "${youtubeId}_video.tmp")
        val tempAudioFile = File(context.cacheDir, "${youtubeId}_audio.tmp")
        val tempCompleteFile = File(context.cacheDir, "${youtubeId}_complete.mp4")
        if (tempFile.exists()) tempFile.delete()
        if (tempVideoFile.exists()) tempVideoFile.delete()
        if (tempAudioFile.exists()) tempAudioFile.delete()
        if (tempCompleteFile.exists()) tempCompleteFile.delete()

        val file = getLocalVideoFile(context, youtubeId)
        if (file.exists() && file.delete()) deletedAny = true

        val legacyFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "${youtubeId}.mp4")
        if (legacyFile.exists()) {
            legacyFile.delete()
            try {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(legacyFile.absolutePath), null, null)
            } catch (e: Exception) { }
            deletedAny = true
        }

        val current = _downloadProgressMap.value.toMutableMap()
        current.remove(youtubeId)
        _downloadProgressMap.value = current
        return deletedAny || !isVideoDownloadedLocally(context, youtubeId, knownPath)
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
        val setting = autoDeleteSetting.trim()
        if (setting.isBlank() || setting.equals("Never", ignoreCase = true) || setting.equals("Permanently", ignoreCase = true)) {
            return@withContext
        }
        val now = System.currentTimeMillis()
        val thresholdMs = when (setting.lowercase()) {
            "24h", "1d" -> 24 * 3600 * 1000L
            "48h", "2d" -> 48 * 3600 * 1000L
            "7d", "1w"  -> 7 * 24 * 3600 * 1000L
            "30d", "1m" -> 30 * 24 * 3600 * 1000L
            else        -> Long.MAX_VALUE
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
                deleteDownloadedVideo(context, video.youtubeId, video.localFilePath)
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
