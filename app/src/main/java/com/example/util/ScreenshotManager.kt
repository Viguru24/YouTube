package com.example.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object ScreenshotManager {

    private const val PREFS_NAME = "vixz_screenshot_prefs"
    private const val KEY_ACTIVE_FOLDER = "active_screenshot_folder"
    private const val KEY_FOLDER_LIST = "custom_screenshot_folders"

    val DEFAULT_FOLDERS = listOf("Default", "Screenshots", "Favorites", "Recipes", "Notes", "Tutorials")

    fun getActiveFolder(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_FOLDER, "Default") ?: "Default"
    }

    fun setActiveFolder(context: Context, folderName: String) {
        val sanitized = folderName.trim().ifBlank { "Default" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_FOLDER, sanitized).apply()
        // Ensure folder is in folder list
        val currentList = getFolders(context).toMutableList()
        if (!currentList.contains(sanitized)) {
            currentList.add(sanitized)
            saveFolderList(context, currentList)
        }
    }

    fun getFolders(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_FOLDER_LIST, null)
        return if (raw.isNullOrBlank()) {
            DEFAULT_FOLDERS
        } else {
            val list = raw.split("|||").filter { it.isNotBlank() }
            if (list.isEmpty()) DEFAULT_FOLDERS else list
        }
    }

    fun addFolder(context: Context, folderName: String) {
        val sanitized = folderName.trim().ifBlank { return }
        val list = getFolders(context).toMutableList()
        if (!list.contains(sanitized)) {
            list.add(sanitized)
            saveFolderList(context, list)
        }
    }

    fun deleteFolder(context: Context, folderName: String) {
        if (folderName == "Default") return
        val list = getFolders(context).toMutableList()
        list.remove(folderName)
        saveFolderList(context, list)
        if (getActiveFolder(context) == folderName) {
            setActiveFolder(context, "Default")
        }
    }

    private fun saveFolderList(context: Context, list: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FOLDER_LIST, list.joinToString("|||")).apply()
    }

    /**
     * Captures high-resolution frame from PlayerView using PixelCopy or TextureView bitmap.
     */
    suspend fun capturePlayerFrame(
        playerView: PlayerView?,
        activity: Activity?
    ): Bitmap? = withContext(Dispatchers.Main) {
        if (playerView == null) return@withContext null

        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        val textureView = playerView.videoSurfaceView as? TextureView

        // 1. TextureView direct bitmap
        if (textureView != null) {
            val bmp = textureView.bitmap
            if (bmp != null) return@withContext bmp
        }

        // 2. SurfaceView PixelCopy (Hardware accelerated API 26+)
        if (surfaceView != null && surfaceView.holder.surface.isValid && surfaceView.width > 0 && surfaceView.height > 0) {
            val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            val result = suspendCancellableCoroutine<Boolean> { cont ->
                try {
                    PixelCopy.request(
                        surfaceView,
                        bitmap,
                        { copyResult ->
                            if (cont.isActive) {
                                cont.resume(copyResult == PixelCopy.SUCCESS)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (result) return@withContext bitmap
        }

        // 3. Fallback Window PixelCopy
        if (activity != null && activity.window != null && playerView.width > 0 && playerView.height > 0) {
            val location = IntArray(2)
            playerView.getLocationInWindow(location)
            val rect = Rect(
                location[0],
                location[1],
                location[0] + playerView.width,
                location[1] + playerView.height
            )
            val bitmap = Bitmap.createBitmap(playerView.width, playerView.height, Bitmap.Config.ARGB_8888)
            val result = suspendCancellableCoroutine<Boolean> { cont ->
                try {
                    PixelCopy.request(
                        activity.window,
                        rect,
                        bitmap,
                        { copyResult ->
                            if (cont.isActive) {
                                cont.resume(copyResult == PixelCopy.SUCCESS)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (result) return@withContext bitmap
        }

        // 4. View Canvas draw fallback
        try {
            if (playerView.width > 0 && playerView.height > 0) {
                val bitmap = Bitmap.createBitmap(playerView.width, playerView.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                playerView.draw(canvas)
                return@withContext bitmap
            }
        } catch (e: Exception) { }

        return@withContext null
    }

    /**
     * Saves captured bitmap to user-controlled folder in Pictures/Vixz/[Folder]
     */
    suspend fun saveScreenshot(
        context: Context,
        bitmap: Bitmap,
        videoTitle: String,
        timestampMs: Long,
        targetFolder: String? = null
    ): Pair<Uri?, String> = withContext(Dispatchers.IO) {
        val folder = (targetFolder ?: getActiveFolder(context)).trim().ifBlank { "Default" }
        val sanitizedTitle = videoTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).trim('_')
        val timeFormatted = formatTimestampForFileName(timestampMs)
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (sanitizedTitle.isNotBlank()) {
            "Vixz_${sanitizedTitle}_${timeFormatted}_$dateStamp.jpg"
        } else {
            "Vixz_Capture_${timeFormatted}_$dateStamp.jpg"
        }

        val relativeSubPath = if (folder.equals("Default", ignoreCase = true)) {
            "Pictures/Vixz"
        } else {
            "Pictures/Vixz/$folder"
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeSubPath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return@withContext Pair(uri, relativeSubPath)
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = if (folder.equals("Default", ignoreCase = true)) {
                    File(picturesDir, "Vixz")
                } else {
                    File(picturesDir, "Vixz/$folder")
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val file = File(targetDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                return@withContext Pair(Uri.fromFile(file), relativeSubPath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext Pair(null, relativeSubPath)
    }

    private fun formatTimestampForFileName(ms: Long): String {
        if (ms <= 0) return "00m00s"
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format("%02dm%02ds", mins, secs)
    }
}
