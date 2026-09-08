package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TvOff
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
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    areAdvertsEnabled: Boolean,
    onAdvertsToggle: (Boolean) -> Unit,
    algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
    onAlgorithmSettingsChanged: (AlgorithmSettings) -> Unit = {},
    mutedChannels: List<MutedChannelEntity> = emptyList(),
    onUnmuteChannel: (String) -> Unit = {},
    onOpenManageTopicsAndCreators: () -> Unit = {},
    onResetAlgorithm: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    val scrollState = rememberScrollState()

    var newBlockedKeyword by remember { mutableStateOf("") }
    var newBoostedTopic by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showResetAlgorithmConfirm by remember { mutableStateOf(false) }

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

    // VPS Cloud Sync state
    var vpsUrlInput by remember { mutableStateOf(com.example.data.remote.VpsSyncManager.getServerUrl(context)) }
    var vpsKeyInput by remember { mutableStateOf(com.example.data.remote.VpsSyncManager.getApiKey(context)) }
    var vpsSyncEnabled by remember { mutableStateOf(com.example.data.remote.VpsSyncManager.isSyncEnabled(context)) }
    var vpsSyncStatusText by remember {
        val lastTime = com.example.data.remote.VpsSyncManager.getLastSyncTime(context)
        mutableStateOf(if (lastTime > 0) "Last Synced: " + java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastTime)) else "Not synced yet")
    }
    var isVpsTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
            color = Color(0xFF0E0D16),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color(0xFF9C27B0).copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF171524),
                                Color(0xFF0F0E18),
                                Color(0xFF0A0A10)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header Row (Tidy, Unclipped & Professional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.radialGradient(listOf(Color(0xFFFF3333), YouTubeRed)),
                                        CircleShape
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Settings & Algorithms",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Personalize playback, privacy & suite",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0xFF222032), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 🌌 Sovereign Cosmo Software Suite Card (TOP-LEVEL SPOTLIGHT)
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF18132C).copy(alpha = 0.85f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFBA68C8).copy(alpha = 0.6f),
                                        Color(0xFF7E57C2).copy(alpha = 0.3f),
                                        Color(0xFF26C6DA).copy(alpha = 0.45f)
                                    )
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                // Suite Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("🌌", fontSize = 15.sp)
                                        Text(
                                            text = "Cosmo Software Suite",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    // Portal Direct Pill
                                    Surface(
                                        onClick = {
                                            try {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://viguru24.github.io")))
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF00ACC1).copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26C6DA).copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("🌐 Portal", fontSize = 10.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(10.dp))
                                        }
                                    }
                                }

                                // App 1: Cosmo Whisper Tile
                                Surface(
                                    onClick = {
                                        try {
                                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Viguru24/CosmoWhisper-Native")))
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF221B3B).copy(alpha = 0.8f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("🎙️", fontSize = 16.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Cosmo Whisper",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFFF9800).copy(alpha = 0.25f)
                                                    ) {
                                                        Text(
                                                            text = "Win / Mac",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFFFFB74D),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Native AI voice dictation with Whisper AI",
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.65f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open",
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                // App 2: Cosmo Symphony Tile
                                Surface(
                                    onClick = {
                                        try {
                                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Viguru24/Video")))
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF221B3B).copy(alpha = 0.8f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB388FF).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("🌌", fontSize = 16.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Cosmo Symphony",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF7E57C2).copy(alpha = 0.25f)
                                                    ) {
                                                        Text(
                                                            text = "PC / Desktop",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFFB388FF),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "GPU video orchestrator, framing & 4K upscaler",
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.65f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open",
                                            tint = Color(0xFFB388FF),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 🛡️ AdBlock & Privacy Card (Streamlined)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAdBlockActive) Color(0xFF122818).copy(alpha = 0.85f) else Color(0xFF2C1318).copy(alpha = 0.85f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAdBlockActive) Color(0xFF4CAF50).copy(alpha = 0.4f) else YouTubeRed.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isAdBlockActive) "🛡️ AdBlock Active (Blocked)" else "📺 Ads Allowed (AdBlock Off)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAdBlockActive) Color(0xFF81C784) else Color(0xFFFF8A80)
                                        )
                                        Text(
                                            text = if (isAdBlockActive) "All YouTube ads & popups suppressed" else "Standard YouTube ads play",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.6f)
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

                    // 3. ✨ AI Copilot & API Keys Card (Streamlined 3D Dark)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF171328).copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasApiKey) Color(0xFFAB47BC).copy(alpha = 0.4f) else Color(0xFFFFB300).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("✨", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = "AI Copilot & Summary API",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (hasApiKey) "🟢 API Key Active & Ready" else "🟡 Key Required for AI Features",
                                            fontSize = 10.sp,
                                            color = if (hasApiKey) Color(0xFF81C784) else Color(0xFFFFB300),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Provider Selector
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = selectedAiProvider == "gemini",
                                    onClick = {
                                        selectedAiProvider = "gemini"
                                        com.example.data.remote.AiSummarizerClient.setAiProvider(context, "gemini")
                                    },
                                    label = { Text("Google Gemini (Flash)", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedAiProvider == "groq",
                                    onClick = {
                                        selectedAiProvider = "groq"
                                        com.example.data.remote.AiSummarizerClient.setAiProvider(context, "groq")
                                    },
                                    label = { Text("Groq (Llama 3.3)", fontSize = 11.sp) }
                                )
                            }

                            if (selectedAiProvider == "gemini") {
                                OutlinedTextField(
                                    value = geminiKeyInput,
                                    onValueChange = { geminiKeyInput = it },
                                    label = { Text("Google Gemini API Key", fontSize = 11.sp) },
                                    placeholder = { Text("AIzaSy...", fontSize = 11.sp) },
                                    singleLine = true,
                                    visualTransformation = if (isGeminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isGeminiKeyVisible = !isGeminiKeyVisible }) {
                                            Text(if (isGeminiKeyVisible) "🙈" else "👁️", fontSize = 14.sp)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                OutlinedTextField(
                                    value = groqKeyInput,
                                    onValueChange = { groqKeyInput = it },
                                    label = { Text("Groq API Key", fontSize = 11.sp) },
                                    placeholder = { Text("gsk_...", fontSize = 11.sp) },
                                    singleLine = true,
                                    visualTransformation = if (isGroqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isGroqKeyVisible = !isGroqKeyVisible }) {
                                            Text(if (isGroqKeyVisible) "🙈" else "👁️", fontSize = 14.sp)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save AI Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 4. 🌐 App Language Card (Streamlined 3D Dark)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF14131E).copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(currentLang.flagEmoji, fontSize = 20.sp)
                                Column {
                                    Text(
                                        text = strings.appLanguageTitle.ifEmpty { "App Language" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = currentLang.displayName,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Button(
                                onClick = { showLanguageDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2844)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Change 🌐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // 5. 🤖 Algorithm & Feed Controls (Streamlined 3D Dark)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF14131E).copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🤖 Feed Algorithm & Discovery",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )

                            // 1. Subscribed Creators vs New Discoveries
                            val discoveryPercent = (algorithmSettings.discoveryRatio * 100).toInt()
                            val subscribedPercent = 100 - discoveryPercent
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Feed Content Mix:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = YouTubeRed.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$subscribedPercent% Subscribed / $discoveryPercent% New",
                                            color = YouTubeRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Ratio between your subscribed creators and smart recommendations.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                Slider(
                                    value = algorithmSettings.discoveryRatio,
                                    onValueChange = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = it)) },
                                    valueRange = 0.05f..0.60f,
                                    colors = SliderDefaults.colors(thumbColor = YouTubeRed, activeTrackColor = YouTubeRed)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = discoveryPercent <= 15,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = 0.10f)) },
                                        label = { Text("Focused (90/10)", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = discoveryPercent in 16..34,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = 0.25f)) },
                                        label = { Text("Balanced (75/25)", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = discoveryPercent >= 35,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(discoveryRatio = 0.45f)) },
                                        label = { Text("Discovery (55/45)", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Divider(color = Color.White.copy(alpha = 0.08f))

                            // 2. Video Upload Recency (Freshness)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "Upload Recency (Freshness):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = algorithmSettings.freshnessDecay == "Fast",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Fast")) },
                                        label = { Text("⚡ Newest (Today)", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.freshnessDecay == "Medium",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Medium")) },
                                        label = { Text("⚖️ Balanced (This Week)", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.freshnessDecay == "Slow",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(freshnessDecay = "Slow")) },
                                        label = { Text("🌟 All-Time Best", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Divider(color = Color.White.copy(alpha = 0.08f))

                            // 2b. Active Subscription Limit
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Subscription Feed Limit:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (algorithmSettings.subscriptionLimit in 1..20) Color(0xFF4CAF50).copy(alpha = 0.15f) else YouTubeRed.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (algorithmSettings.subscriptionLimit == 0) "All (Uncapped)" else "Top ${algorithmSettings.subscriptionLimit} Channels",
                                            color = if (algorithmSettings.subscriptionLimit in 1..20) Color(0xFF81C784) else YouTubeRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Limits parallel channel queries to keep feed loading ultra-fast, smooth, and lag-free.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = algorithmSettings.subscriptionLimit == 10,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(subscriptionLimit = 10)) },
                                        label = { Text("⚡ Top 10", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.subscriptionLimit == 20,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(subscriptionLimit = 20)) },
                                        label = { Text("🚀 Top 20", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.subscriptionLimit == 35,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(subscriptionLimit = 35)) },
                                        label = { Text("🎯 Top 35", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.subscriptionLimit == 0,
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(subscriptionLimit = 0)) },
                                        label = { Text("♾️ All", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Divider(color = Color.White.copy(alpha = 0.08f))

                            // 3. Floating Pop-out (Picture-in-Picture)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "Floating Mini-Player (Picture-in-Picture):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Automatically pops out to a floating window when switching apps.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = !autoPipEnabled,
                                        onClick = {
                                            autoPipEnabled = false
                                            playerPrefs.edit().putBoolean("auto_pip_enabled", false).apply()
                                        },
                                        label = { Text("Disabled", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = autoPipEnabled,
                                        onClick = {
                                            autoPipEnabled = true
                                            playerPrefs.edit().putBoolean("auto_pip_enabled", true).apply()
                                        },
                                        label = { Text("Auto Pop-out", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Divider(color = Color.White.copy(alpha = 0.08f))

                            // 4. Preferred Download Quality
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "Preferred Offline Download Quality:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = algorithmSettings.downloadResolution == "1080p",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(downloadResolution = "1080p")) },
                                        label = { Text("1080p FHD", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.downloadResolution == "720p",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(downloadResolution = "720p")) },
                                        label = { Text("720p HD", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = algorithmSettings.downloadResolution == "480p" || algorithmSettings.downloadResolution == "360p",
                                        onClick = { onAlgorithmSettingsChanged(algorithmSettings.copy(downloadResolution = "480p")) },
                                        label = { Text("480p Saver", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Divider(color = Color.White.copy(alpha = 0.08f))

                            // 5. Manage Topics & Creators Link
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenManageTopicsAndCreators()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2844)),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🏷️ Manage Topics & Creators", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // 6. Reset Algorithm Database Action
                            OutlinedButton(
                                onClick = { showResetAlgorithmConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🤖 Reset Algorithm Database", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 6. 🚫 Blocked Keywords & Channels (Streamlined 3D Dark)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF14131E).copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🚫 Blocked Keywords & Channels",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                            Text(
                                text = "Hides any video containing these keywords or channel names.",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = newBlockedKeyword,
                                    onValueChange = { newBlockedKeyword = it },
                                    placeholder = { Text("e.g. drama, clickbait", fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
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
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (algorithmSettings.blockedKeywords.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    algorithmSettings.blockedKeywords.forEach { kw ->
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                onAlgorithmSettingsChanged(
                                                    algorithmSettings.copy(blockedKeywords = algorithmSettings.blockedKeywords.filter { it != kw })
                                                )
                                            },
                                            label = { Text("🚫 $kw ✕", fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. ☁️ VPS Cross-Device Cloud Sync (Streamlined 3D Dark)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF14131E).copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "☁️ VPS Cross-Device Cloud Sync",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64B5F6)
                            )
                            Text(
                                text = "Synchronize watched videos, playback positions, and preferences with your private VPS sync server.",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )

                            OutlinedTextField(
                                value = vpsUrlInput,
                                onValueChange = {
                                    vpsUrlInput = it
                                    com.example.data.remote.VpsSyncManager.setServerUrl(context, it)
                                },
                                label = { Text("VPS Server Address", fontSize = 10.sp) },
                                placeholder = { Text("http://192.168.1.100:8089 or https://...", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = vpsKeyInput,
                                onValueChange = {
                                    vpsKeyInput = it
                                    com.example.data.remote.VpsSyncManager.setApiKey(context, it)
                                },
                                label = { Text("Optional API Token / Key", fontSize = 10.sp) },
                                placeholder = { Text("Leave empty if auth is disabled", fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Auto-sync on startup & watch",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                Switch(
                                    checked = vpsSyncEnabled,
                                    onCheckedChange = {
                                        vpsSyncEnabled = it
                                        com.example.data.remote.VpsSyncManager.setSyncEnabled(context, it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4CAF50),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFF555566)
                                    )
                                )
                            }

                            if (vpsSyncStatusText.isNotBlank()) {
                                Text(
                                    text = vpsSyncStatusText,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }

                            Button(
                                onClick = {
                                    val db = com.example.data.db.AppDatabase.getInstance(context)
                                    isVpsTesting = true
                                    vpsSyncStatusText = "Connecting & syncing with VPS..."
                                    coroutineScope.launch {
                                        val res = com.example.data.remote.VpsSyncManager.syncWithServer(context, db.videoDao())
                                        isVpsTesting = false
                                        vpsSyncStatusText = if (res.first) "✅ " + res.second else "❌ " + res.second
                                    }
                                },
                                enabled = !isVpsTesting && vpsUrlInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                if (isVpsTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isVpsTesting) "Syncing..." else "⚡ Test Connection & Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Save & Close Button (3D Vibrant Gradient Button)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF3333), YouTubeRed, Color(0xFFC62828))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .testTag("settings_close_btn")
                ) {
                    Text("Save & Close", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

    if (showResetAlgorithmConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAlgorithmConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Reset Algorithm Database?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This will reset your watch history, disliked videos, and learned topic affinity back to default factory weights.\n\n" +
                           "🛡️ Your Subscriptions, Saved Favorites, and Downloads are safe and will NOT be touched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetAlgorithmConfirm = false
                        onResetAlgorithm()
                        android.widget.Toast.makeText(context, "✨ Algorithm reset to defaults!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text("Yes, Reset Algorithm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAlgorithmConfirm = false }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}
