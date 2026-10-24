package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

class AppUpdateManager(
    private val context: Context,
    private val appSlug: String = "youtube"
) {

    val currentVersionCode: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }

    val currentVersionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.0"
    } catch (_: Exception) {
        "2.0.0"
    }

    private val baseUrl = "https://api.cosmowhisper.com/updates"
    private val groupToken = "81e5632c_pin_group"

    /**
     * Checks if a newer APK is available on the sovereign VPS.
     */
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/$appSlug/version.json?token=$groupToken")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("x-group-token", groupToken)
            connection.setRequestProperty("x-app-id", appSlug)

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val serverVersionCode = json.getInt("versionCode")

                if (serverVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(
                        versionCode = serverVersionCode,
                        versionName = json.getString("versionName"),
                        apkUrl = json.getString("apkUrl"),
                        releaseNotes = json.optString("releaseNotes", "Bug fixes and performance updates.")
                    )
                }
            }
        } catch (_: Exception) {
            // Safe fallback
        }
        return@withContext null
    }

    /**
     * Downloads the APK to app cache and passes the URI to Android's native PackageInstaller.
     */
    suspend fun downloadAndInstallUpdate(
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val updateFolder = File(context.cacheDir, "updates")
            if (!updateFolder.exists()) updateFolder.mkdirs()
            val destinationFile = File(updateFolder, "$appSlug-update.apk")
            if (destinationFile.exists()) destinationFile.delete()

            val downloadUrl = if (apkUrl.contains("?")) {
                "$apkUrl&token=$groupToken"
            } else {
                "$apkUrl?token=$groupToken"
            }

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.setRequestProperty("x-group-token", groupToken)
            connection.setRequestProperty("x-app-id", appSlug)

            if (connection.responseCode != 200) return@withContext false

            val fileLength = connection.contentLength
            connection.inputStream.use { input ->
                destinationFile.outputStream().use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            onProgress(total.toFloat() / fileLength.toFloat())
                        }
                        output.write(data, 0, count)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                triggerPackageInstallation(destinationFile)
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun triggerPackageInstallation(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val manageIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(manageIntent)
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        context.startActivity(intent)
    }
}
