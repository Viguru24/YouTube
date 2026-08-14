package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.TvOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YouTubeRed

import com.example.data.repository.AlgorithmSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    areAdvertsEnabled: Boolean,
    onAdvertsToggle: (Boolean) -> Unit,
    algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
    onAlgorithmSettingsChanged: (AlgorithmSettings) -> Unit = {},
    mutedChannels: List<com.example.data.model.MutedChannelEntity> = emptyList(),
    onUnmuteChannel: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = YouTubeRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("App Settings & Algorithm", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Adverts Toggle Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (areAdvertsEnabled) Icons.Outlined.TvOff else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = if (areAdvertsEnabled) Color(0xFFFF9800) else Color(0xFF1E88E5),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (areAdvertsEnabled) "Adverts Allowed" else "AdBlock Active",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (areAdvertsEnabled) "Standard YouTube ads play." else "Ads suppressed during video playback.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = areAdvertsEnabled,
                            onCheckedChange = onAdvertsToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = YouTubeRed
                            ),
                            modifier = Modifier.testTag("adverts_toggle_switch")
                        )
                    }
                }

                // Algorithm Controls Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "🤖 Algorithm Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Creator Loyalty Weight Slider
                        Text(
                            text = "Creator Loyalty Boost: ${(algorithmSettings.creatorWeight * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = algorithmSettings.creatorWeight,
                            onValueChange = { onAlgorithmSettingsChanged(algorithmSettings.copy(creatorWeight = it)) },
                            colors = SliderDefaults.colors(thumbColor = YouTubeRed, activeTrackColor = YouTubeRed)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Wild Discovery / Serendipity Slider
                        Text(
                            text = "Wild Discovery (Break the Bubble): ${(algorithmSettings.discoveryRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = algorithmSettings.discoveryRatio,
                            onValueChange = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = it)) },
                            colors = SliderDefaults.colors(thumbColor = YouTubeRed, activeTrackColor = YouTubeRed)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Freshness Decay Filter
                        Text(
                            text = "Freshness Priority (Recency):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = algorithmSettings.freshnessDecay == "Slow",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Slow")) },
                                label = { Text("Slow") }
                            )
                            FilterChip(
                                selected = algorithmSettings.freshnessDecay == "Medium",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Medium")) },
                                label = { Text("Balanced") }
                            )
                            FilterChip(
                                selected = algorithmSettings.freshnessDecay == "Fast",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Fast")) },
                                label = { Text("Fast (24h)") }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Shorts Feed Display Toggle
                        Text(
                            text = "Shorts Feed Display:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = algorithmSettings.shortsMode == "Carousel",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(shortsMode = "Carousel")) },
                                label = { Text("Show Reel") }
                            )
                            FilterChip(
                                selected = algorithmSettings.shortsMode == "Hidden",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(shortsMode = "Hidden")) },
                                label = { Text("Hide Shorts") }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Minimum Video Duration Filter
                        Text(
                            text = "Minimum Preferred Duration:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = algorithmSettings.minDurationMinutes == 0,
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(minDurationMinutes = 0)) },
                                label = { Text("Any Length") }
                            )
                            FilterChip(
                                selected = algorithmSettings.minDurationMinutes == 3,
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(minDurationMinutes = 3)) },
                                label = { Text("3+ mins") }
                            )
                            FilterChip(
                                selected = algorithmSettings.minDurationMinutes == 10,
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(minDurationMinutes = 10)) },
                                label = { Text("10+ mins") }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Auto-Delete Offline Downloads Setting
                        Text(
                            text = "Auto-Delete Offline Downloads:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = algorithmSettings.autoDeleteDownloads == "Never",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(autoDeleteDownloads = "Never")) },
                                label = { Text("Never", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = algorithmSettings.autoDeleteDownloads == "24h",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(autoDeleteDownloads = "24h")) },
                                label = { Text("24 Hours", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = algorithmSettings.autoDeleteDownloads == "48h",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(autoDeleteDownloads = "48h")) },
                                label = { Text("48 Hours", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = algorithmSettings.autoDeleteDownloads == "7d",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(autoDeleteDownloads = "7d")) },
                                label = { Text("7 Days", fontSize = 11.sp) }
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = algorithmSettings.autoDeleteDownloads == "30d",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(autoDeleteDownloads = "30d")) },
                                label = { Text("30 Days", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = algorithmSettings.autoDeleteDownloads == "Watched",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(autoDeleteDownloads = "Watched")) },
                                label = { Text("After Watched ✓", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Muted Channels Card
                if (mutedChannels.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🚫 Muted Channels (${mutedChannels.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                mutedChannels.take(5).forEach { item ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { onUnmuteChannel(item.channelName) },
                                        label = { Text("${item.channelName} ✕", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // App Version Info
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Personal YouTube Player • v1.0.0",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Custom Recommendation Engine Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                modifier = Modifier.testTag("settings_close_btn")
            ) {
                Text("Save & Close")
            }
        }
    )
}
