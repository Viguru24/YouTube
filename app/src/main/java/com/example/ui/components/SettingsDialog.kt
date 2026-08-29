package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.TvOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MutedChannelEntity
import com.example.data.repository.AlgorithmSettings
import com.example.ui.theme.YouTubeRed
import com.example.util.AppLanguage
import com.example.util.LanguageManager
import com.example.util.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    areAdvertsEnabled: Boolean,
    onAdvertsToggle: (Boolean) -> Unit,
    algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
    onAlgorithmSettingsChanged: (AlgorithmSettings) -> Unit = {},
    mutedChannels: List<MutedChannelEntity> = emptyList(),
    onUnmuteChannel: (String) -> Unit = {},
    onOpenManageTopicsAndCreators: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    var isLanguageExpanded by remember { mutableStateOf(false) }
    var newBlockedKeyword by remember { mutableStateOf("") }
    var newBoostedTopic by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                Text(strings.settingsTitle, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // 1. App Language Selection Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = Color(0xFF00ACC1),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = strings.appLanguageTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentLang.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF00ACC1),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            TextButton(onClick = { isLanguageExpanded = !isLanguageExpanded }) {
                                Text(if (isLanguageExpanded) "▲" else "▼ Change")
                            }
                        }

                        if (isLanguageExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppLanguage.entries.forEach { lang ->
                                    val isSelected = lang == currentLang
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            LanguageManager.setLanguage(context, lang)
                                        },
                                        label = {
                                            Text(
                                                text = lang.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = YouTubeRed.copy(alpha = 0.2f),
                                            selectedLabelColor = YouTubeRed
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Adverts Toggle Card
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
                            text = "🤖 Feed Algorithm & Discovery Tuning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // 1. Subscribed Creators vs New Discoveries Ratio
                        val discoveryPercent = (algorithmSettings.discoveryRatio * 100).toInt()
                        val subscribedPercent = 100 - discoveryPercent
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Feed Content Mix:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = YouTubeRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$subscribedPercent% Subscribed / $discoveryPercent% New",
                                    color = YouTubeRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Controls how much of your feed comes from your subscribed creators vs. intelligent new recommendations matching your interests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = algorithmSettings.discoveryRatio,
                            onValueChange = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = it)) },
                            valueRange = 0.05f..0.60f,
                            colors = SliderDefaults.colors(thumbColor = YouTubeRed, activeTrackColor = YouTubeRed)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = discoveryPercent <= 15,
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = 0.10f)) },
                                label = { Text("Focused (90/10)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = discoveryPercent in 16..34,
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = 0.25f)) },
                                label = { Text("Balanced (75/25)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = discoveryPercent >= 35,
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = 0.45f)) },
                                label = { Text("Discovery (55/45)", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Subscribed Creator Pinning / Priority
                        val creatorWeightPercent = (algorithmSettings.creatorWeight * 100).toInt()
                        val creatorWeightLabel = when {
                            creatorWeightPercent >= 80 -> "Maximum Priority"
                            creatorWeightPercent >= 60 -> "High Priority"
                            else -> "Standard"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subscribed Creator Priority:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$creatorWeightPercent% ($creatorWeightLabel)",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Determines how strongly new uploads from your subscribed creators are placed near the top of your feed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = algorithmSettings.creatorWeight,
                            onValueChange = { onAlgorithmSettingsChanged(algorithmSettings.copy(creatorWeight = it)) },
                            valueRange = 0.3f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = YouTubeRed, activeTrackColor = YouTubeRed)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Freshness / Recency
                        Text(
                            text = "Video Upload Recency (Freshness):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose whether you prefer brand new uploads from today or timeless high-quality videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = algorithmSettings.freshnessDecay == "Fast",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Fast")) },
                                label = { Text("⚡ Newest (Today)") }
                            )
                            FilterChip(
                                selected = algorithmSettings.freshnessDecay == "Medium",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Medium")) },
                                label = { Text("⚖️ Balanced (This Week)") }
                            )
                            FilterChip(
                                selected = algorithmSettings.freshnessDecay == "Slow",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Slow")) },
                                label = { Text("🌟 All-Time Best") }
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

                        // Floating Pop-out Window (Picture-in-Picture) Setting
                        val playerPrefs = remember(context) { context.getSharedPreferences("vixz_player_prefs", android.content.Context.MODE_PRIVATE) }
                        var autoPipEnabled by remember { mutableStateOf(playerPrefs.getBoolean("auto_pip_enabled", false)) }

                        Text(
                            text = "Floating Pop-out Player (Picture-in-Picture):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pop out video to a mini-window when exiting app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !autoPipEnabled,
                                onClick = {
                                    autoPipEnabled = false
                                    playerPrefs.edit().putBoolean("auto_pip_enabled", false).apply()
                                },
                                label = { Text("Disabled (Stay in App)") }
                            )
                            FilterChip(
                                selected = autoPipEnabled,
                                onClick = {
                                    autoPipEnabled = true
                                    playerPrefs.edit().putBoolean("auto_pip_enabled", true).apply()
                                },
                                label = { Text("Enabled (Pop-out)") }
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

                        // Preferred Download Resolution Setting
                        Text(
                            text = "Preferred Offline Download Quality:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = algorithmSettings.downloadResolution == "1080p",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(downloadResolution = "1080p")) },
                                label = { Text("1080p (Full HD)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = algorithmSettings.downloadResolution == "720p",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(downloadResolution = "720p")) },
                                label = { Text("720p (HD - Best)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = algorithmSettings.downloadResolution == "480p" || algorithmSettings.downloadResolution == "360p",
                                onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(downloadResolution = "480p")) },
                                label = { Text("480p (Data Saver)", fontSize = 11.sp) }
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full Manager Launch Button
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenManageTopicsAndCreators()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🏷️ Manage Topics & Creators (Add/Remove/Edit)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ⚡ Boosted Topics & Creators Manager
                        Text(
                            text = "⚡ Boosted Topics & Creators (Top Priority):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed
                        )
                        Text(
                            text = "Floats matching topics and creators to the very top of your feed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newBoostedTopic,
                                onValueChange = { newBoostedTopic = it },
                                placeholder = { Text("e.g. AI Tech, Finance, SpaceX", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val trimmed = newBoostedTopic.trim()
                                    if (trimmed.isNotEmpty() && trimmed !in algorithmSettings.boostedTopics) {
                                        onAlgorithmSettingsChanged(
                                            algorithmSettings.copy(boostedTopics = algorithmSettings.boostedTopics + trimmed)
                                        )
                                        newBoostedTopic = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("+ Boost", fontSize = 12.sp)
                            }
                        }
                        if (algorithmSettings.boostedTopics.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                algorithmSettings.boostedTopics.forEach { topic ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            onAlgorithmSettingsChanged(
                                                algorithmSettings.copy(boostedTopics = algorithmSettings.boostedTopics.filter { it != topic })
                                            )
                                        },
                                        label = { Text("⚡ $topic ✕", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 🚫 Blocked Keywords & Channels Manager
                        Text(
                            text = "🚫 Blocked Keywords & Channels (Permanent Exclude):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                        Text(
                            text = "Hides any video containing these keywords or channel names.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newBlockedKeyword,
                                onValueChange = { newBlockedKeyword = it },
                                placeholder = { Text("e.g. drama, spoilers, gossip", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val trimmed = newBlockedKeyword.trim()
                                    if (trimmed.isNotEmpty() && trimmed !in algorithmSettings.blockedKeywords) {
                                        onAlgorithmSettingsChanged(
                                            algorithmSettings.copy(blockedKeywords = algorithmSettings.blockedKeywords + trimmed)
                                        )
                                        newBlockedKeyword = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("+ Block", fontSize = 12.sp)
                            }
                        }
                        if (algorithmSettings.blockedKeywords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                algorithmSettings.blockedKeywords.forEach { kw ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            onAlgorithmSettingsChanged(
                                                algorithmSettings.copy(blockedKeywords = algorithmSettings.blockedKeywords.filter { it != kw })
                                            )
                                        },
                                        label = { Text("🚫 $kw ✕", fontSize = 11.sp) }
                                    )
                                }
                            }
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
                Text(strings.closeBtn)
            }
        }
    )
}
