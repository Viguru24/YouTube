package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppUpdateManager
import com.example.data.UpdateInfo
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.launch

@Composable
fun OtaUpdateDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context, "youtube") }

    var isChecking by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    fun runUpdateCheck() {
        isChecking = true
        errorMessage = null
        scope.launch {
            try {
                val result = updateManager.checkForUpdates()
                updateInfo = result
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to connect to sovereign VPS"
            } finally {
                isChecking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runUpdateCheck()
    }

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = BorderStroke(1.2.dp, Color(0xFF333333)),
            shadowElevation = 24.dp,
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("ota_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Icon with gradient ring
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF201614),
                    border = BorderStroke(2.dp, YouTubeRed.copy(alpha = 0.6f)),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (updateInfo != null) Icons.Default.RocketLaunch else Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Title and App Version Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Over-The-Air (OTA) Updates",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 19.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current: v${updateManager.currentVersionName} (Build ${updateManager.currentVersionCode})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                // Dynamic Status Box
                when {
                    isChecking -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = YouTubeRed,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Checking Sovereign VPS for builds...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    errorMessage != null -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF2A1515),
                            border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Update Server Notice",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = errorMessage!!,
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    updateInfo != null -> {
                        val info = updateInfo!!
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E1714),
                            border = BorderStroke(1.2.dp, YouTubeRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.RocketLaunch,
                                            contentDescription = null,
                                            tint = YouTubeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "New Update: v${info.versionName}",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFCCBC),
                                                fontSize = 15.sp
                                            )
                                        )
                                    }
                                    Surface(
                                        color = YouTubeRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Build ${info.versionCode}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = YouTubeRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFE0E0E0),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                )

                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = YouTubeRed,
                                            trackColor = DarkSurfaceVariant
                                        )
                                        Text(
                                            text = "Downloading update package: ${(downloadProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFFFFCCBC),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF142416),
                            border = BorderStroke(1.dp, Color(0xFF15803D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "You're completely up to date!",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF86EFAC),
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "Running the latest sovereign build (v${updateManager.currentVersionName} code ${updateManager.currentVersionCode}).",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (updateInfo != null && !isDownloading) {
                        Button(
                            onClick = {
                                isDownloading = true
                                scope.launch {
                                    val success = updateManager.downloadAndInstallUpdate(
                                        apkUrl = updateInfo!!.apkUrl,
                                        onProgress = { p -> downloadProgress = p }
                                    )
                                    isDownloading = false
                                    if (success) {
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Failed to download update package.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("download_and_install_update_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Install Update Now (v${updateInfo!!.versionName})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { runUpdateCheck() },
                            enabled = !isChecking && !isDownloading,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = YouTubeRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Again", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }

                        TextButton(
                            onClick = onDismiss,
                            enabled = !isDownloading
                        ) {
                            Text("Close", color = TextSecondary, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}
