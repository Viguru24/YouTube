package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.DiagnosticLogger
import com.example.util.DiagnosticStatus

@Composable
fun DiagnosticOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by DiagnosticLogger.status.collectAsState()
    val statusMessage by DiagnosticLogger.statusMessage.collectAsState()
    val logEntries by DiagnosticLogger.logEntries.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    val dotColor by animateColorAsState(
        targetValue = when (status) {
            DiagnosticStatus.HEALTHY -> Color(0xFF4CAF50)  // Bright Green
            DiagnosticStatus.FALLBACK -> Color(0xFFFFC107) // Amber/Yellow
            DiagnosticStatus.ERROR -> Color(0xFFFF5252)    // Bright Red
        },
        label = "status_dot_color"
    )

    // Floating Diagnostic Circle Badge
    Box(
        modifier = modifier
            .padding(top = 40.dp, end = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.dp, dotColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable { showDialog = true }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("diagnostic_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Text(
                text = when (status) {
                    DiagnosticStatus.HEALTHY -> "100% Direct MP4"
                    DiagnosticStatus.FALLBACK -> "Fallback Mirror"
                    DiagnosticStatus.ERROR -> "Error Logged"
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Detailed Log Console Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Diagnostic Console",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { showDialog = false }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Status Card Header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = dotColor.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Status: ${status.name}",
                                fontWeight = FontWeight.Bold,
                                color = dotColor
                            )
                            Text(
                                text = statusMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Events (${logEntries.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Row {
                            IconButton(onClick = {
                                val logText = logEntries.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message} ${it.details}" }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Diagnostic Logs", logText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Logs", modifier = Modifier.size(18.dp))
                            }

                            IconButton(onClick = { DiagnosticLogger.clearLogs() }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Clear Logs", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Console Log List
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF121212))
                            .padding(8.dp)
                    ) {
                        if (logEntries.isEmpty()) {
                            Text(
                                text = "No diagnostic events logged yet.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(logEntries) { entry ->
                                    val entryColor = when (entry.level) {
                                        "ERROR" -> Color(0xFFFF5252)
                                        "WARN" -> Color(0xFFFFC107)
                                        else -> Color(0xFF81C784)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "[${entry.timestamp}]",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "[${entry.tag}]",
                                                color = entryColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Text(
                                            text = entry.message,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (entry.details.isNotBlank()) {
                                            Text(
                                                text = entry.details,
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Close Console")
                }
            }
        )
    }
}
