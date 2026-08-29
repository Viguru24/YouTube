package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TvOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MutedChannelEntity
import com.example.data.repository.AlgorithmSettings
import com.example.ui.theme.YouTubeRed
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
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    val scrollState = rememberScrollState()

    var newBlockedKeyword by remember { mutableStateOf("") }
    var newBoostedTopic by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // AI API Keys state
    var selectedAiProvider by remember { mutableStateOf(com.example.data.remote.AiSummarizerClient.getAiProvider(context)) }
    var geminiKeyInput by remember { mutableStateOf(com.example.data.remote.AiSummarizerClient.getGeminiApiKey(context)) }
    var groqKeyInput by remember { mutableStateOf(com.example.data.remote.AiSummarizerClient.getGroqApiKey(context)) }
    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var isGroqKeyVisible by remember { mutableStateOf(false) }

    val isAdBlockActive = !areAdvertsEnabled
    val hasApiKey = com.example.data.remote.AiSummarizerClient.hasApiKeyConfigured(context)
    val playerPrefs = remember(context) { context.getSharedPreferences("vixz_player_prefs", android.content.Context.MODE_PRIVATE) }
    var autoPipEnabled by remember { mutableStateOf(playerPrefs.getBoolean("auto_pip_enabled", false)) }

    if (showLanguageDialog) {
        LanguageSelectionDialog(onDismiss = { showLanguageDialog = false })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(YouTubeRed.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = YouTubeRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.settingsTitle.ifEmpty { "App Settings" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personalize features, privacy & algorithm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. 🛡️ AdBlock & Privacy Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAdBlockActive) Color(0xFF1B5E20).copy(alpha = 0.16f) else YouTubeRed.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAdBlockActive) Color(0xFF4CAF50).copy(alpha = 0.4f) else YouTubeRed.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            if (isAdBlockActive) Color(0xFF4CAF50).copy(alpha = 0.2f) else YouTubeRed.copy(alpha = 0.2f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAdBlockActive) Icons.Filled.Shield else Icons.Outlined.TvOff,
                                        contentDescription = null,
                                        tint = if (isAdBlockActive) Color(0xFF4CAF50) else YouTubeRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = if (isAdBlockActive) "🛡️ AdBlock Active (Blocked)" else "📺 Ads Allowed (AdBlock Off)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAdBlockActive) Color(0xFF4CAF50) else YouTubeRed
                                    )
                                    Text(
                                        text = if (isAdBlockActive) "All YouTube ads & popups suppressed." else "Standard YouTube ads play.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isAdBlockActive,
                                onCheckedChange = { active ->
                                    onAdvertsToggle(!active)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4CAF50),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = YouTubeRed
                                ),
                                modifier = Modifier.testTag("adverts_toggle_switch")
                            )
                        }
                    }

                    // 2. ✨ AI Copilot & API Keys Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasApiKey) Color(0xFF8E24AA).copy(alpha = 0.4f) else Color(0xFFFFB300).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✨", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "AI Copilot & Summary API",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (hasApiKey) "🟢 API Key Active & Ready" else "🟡 Key Required for AI Features",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (hasApiKey) Color(0xFF4CAF50) else Color(0xFFFFB300),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Provider Selector
                            Text(
                                text = "Select AI Provider:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedAiProvider == "gemini",
                                    onClick = {
                                        selectedAiProvider = "gemini"
                                        com.example.data.remote.AiSummarizerClient.setAiProvider(context, "gemini")
                                    },
                                    label = { Text("Google Gemini (Flash)", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = selectedAiProvider == "groq",
                                    onClick = {
                                        selectedAiProvider = "groq"
                                        com.example.data.remote.AiSummarizerClient.setAiProvider(context, "groq")
                                    },
                                    label = { Text("Groq (Llama 3.3)", fontSize = 12.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (selectedAiProvider == "gemini") {
                                OutlinedTextField(
                                    value = geminiKeyInput,
                                    onValueChange = { geminiKeyInput = it },
                                    label = { Text("Google Gemini API Key", fontSize = 12.sp) },
                                    placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                                    singleLine = true,
                                    visualTransformation = if (isGeminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isGeminiKeyVisible = !isGeminiKeyVisible }) {
                                            Text(if (isGeminiKeyVisible) "🙈" else "👁️", fontSize = 16.sp)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                OutlinedTextField(
                                    value = groqKeyInput,
                                    onValueChange = { groqKeyInput = it },
                                    label = { Text("Groq API Key", fontSize = 12.sp) },
                                    placeholder = { Text("gsk_...", fontSize = 12.sp) },
                                    singleLine = true,
                                    visualTransformation = if (isGroqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isGroqKeyVisible = !isGroqKeyVisible }) {
                                            Text(if (isGroqKeyVisible) "🙈" else "👁️", fontSize = 16.sp)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (selectedAiProvider == "gemini") {
                                        com.example.data.remote.AiSummarizerClient.setGeminiApiKey(context, geminiKeyInput)
                                    } else {
                                        com.example.data.remote.AiSummarizerClient.setGroqApiKey(context, groqKeyInput)
                                    }
                                    com.example.data.remote.AiSummarizerClient.setAiProvider(context, selectedAiProvider)
                                    android.widget.Toast.makeText(context, "✅ AI API Key Saved!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save AI Key", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 3. 🌐 App Language Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(currentLang.flagEmoji, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = strings.appLanguageTitle.ifEmpty { "App Language" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentLang.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { showLanguageDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Change 🌐", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 4. 🤖 Algorithm & Feed Controls
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "🤖 Feed Algorithm & Discovery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )

                            // 1. Subscribed Creators vs New Discoveries
                            val discoveryPercent = (algorithmSettings.discoveryRatio * 100).toInt()
                            val subscribedPercent = 100 - discoveryPercent
                            Column {
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
                                        shape = RoundedCornerShape(8.dp),
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
                                    text = "Ratio between your subscribed creators and smart recommendations.",
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // 2. Video Upload Recency (Freshness)
                            Column {
                                Text(
                                    text = "Upload Recency (Freshness):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = algorithmSettings.freshnessDecay == "Fast",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Fast")) },
                                        label = { Text("⚡ Newest (Today)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.freshnessDecay == "Medium",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Medium")) },
                                        label = { Text("⚖️ Balanced (This Week)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.freshnessDecay == "Slow",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Slow")) },
                                        label = { Text("🌟 All-Time Best", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // 3. Floating Pop-out (Picture-in-Picture)
                            Column {
                                Text(
                                    text = "Floating Mini-Player (Picture-in-Picture):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Automatically pops out to a floating window when switching apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = !autoPipEnabled,
                                        onClick = {
                                            autoPipEnabled = false
                                            playerPrefs.edit().putBoolean("auto_pip_enabled", false).apply()
                                        },
                                        label = { Text("Disabled (Stay in App)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = autoPipEnabled,
                                        onClick = {
                                            autoPipEnabled = true
                                            playerPrefs.edit().putBoolean("auto_pip_enabled", true).apply()
                                        },
                                        label = { Text("Enabled (Auto Pop-out)", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // 4. Preferred Download Quality
                            Column {
                                Text(
                                    text = "Preferred Offline Download Quality:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // 5. Manage Topics & Creators Link
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenManageTopicsAndCreators()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🏷️ Manage Topics & Creators", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 5. 🚫 Blocked Keywords & Channels
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "🚫 Blocked Keywords & Channels",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935)
                            )
                            Text(
                                text = "Hides any video containing these keywords or channel names.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = newBlockedKeyword,
                                    onValueChange = { newBlockedKeyword = it },
                                    placeholder = { Text("e.g. drama, spoilers, clickbait", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+ Block", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (algorithmSettings.blockedKeywords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Save & Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_close_btn")
                ) {
                    Text("Save & Close", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
