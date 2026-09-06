package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoEntity
import com.example.data.remote.ChatMessage
import com.example.data.remote.AiSummarizerClient
import com.example.data.remote.YouTubeCaptionService
import kotlinx.coroutines.launch
import com.example.ui.theme.YouTubeRed
import com.example.util.VideoAiTranscript

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummaryModal(
    video: VideoEntity,
    onDismiss: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onSaveToNotes: (text: String) -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.90f)
                    .clickable(enabled = false) {}, // prevent closing when clicking inside
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AiSummaryContentBody(
                    video = video,
                    onDismiss = onDismiss,
                    onSeekTo = onSeekTo,
                    onSaveToNotes = onSaveToNotes
                )
            }
        }
    }
}

@Composable
private fun AiSummaryContentBody(
    video: VideoEntity,
    onDismiss: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onSaveToNotes: (text: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var transcript by remember(video.youtubeId) { mutableStateOf<VideoAiTranscript?>(null) }
    var isLoading by remember(video.youtubeId) { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Chatbot, 1 = Summary, 2 = Timeline
    var showApiKeyCard by remember { mutableStateOf(!com.example.data.remote.AiSummarizerClient.hasApiKeyConfigured(context)) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var chatMessages by remember(video.youtubeId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputQuery by remember { mutableStateOf("") }
    var isBotThinking by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    fun sendMessage(queryText: String) {
        if (queryText.isBlank() || isBotThinking) return
        val userMsg = ChatMessage(isUser = true, text = queryText)
        chatMessages = chatMessages + userMsg
        isBotThinking = true
        coroutineScope.launch {
            try {
                chatListState.animateScrollToItem((chatMessages.size - 1).coerceAtLeast(0))
                val rawText = transcript?.segments?.joinToString(" ") { it.text } ?: transcript?.executiveSummary.orEmpty()
                val reply = AiSummarizerClient.askChatbot(
                    context = context,
                    videoId = video.youtubeId,
                    title = video.title,
                    channelName = video.channelName,
                    rawTranscriptText = rawText,
                    history = chatMessages,
                    userQuestion = queryText
                )
                chatMessages = chatMessages + ChatMessage(isUser = false, text = reply)
                isBotThinking = false
                chatListState.animateScrollToItem((chatMessages.size - 1).coerceAtLeast(0))
            } catch (e: Exception) {
                chatMessages = chatMessages + ChatMessage(isUser = false, text = "⚠️ Sorry, I encountered an issue analyzing the video. Please check your connection or AI API key.")
                isBotThinking = false
            }
        }
    }

    var selectedProvider by remember { mutableStateOf(com.example.data.remote.AiSummarizerClient.getAiProvider(context)) }
    var geminiKeyInput by remember { mutableStateOf(com.example.data.remote.AiSummarizerClient.getGeminiApiKey(context)) }
    var groqKeyInput by remember { mutableStateOf(com.example.data.remote.AiSummarizerClient.getGroqApiKey(context)) }

    LaunchedEffect(video.youtubeId, refreshTrigger) {
        isLoading = true
        try {
            val result = YouTubeCaptionService.getAuthenticSummary(video, context)
            transcript = result
        } catch (e: Exception) {
            // Fallback
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
            // Header: Sparkle Badge + Title + Copy / Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF8E24AA), Color(0xFFE91E63), YouTubeRed)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI Video Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val provider = com.example.data.remote.AiSummarizerClient.getAiProvider(context)
                        val hasKey = com.example.data.remote.AiSummarizerClient.hasApiKeyConfigured(context)
                        Text(
                            text = if (isLoading) "Generating AI summary..." else if (hasKey) "✨ Powered by ${if (provider == "groq") "Groq Llama 3" else "Google Gemini"}" else "Real Caption & Spoken Content AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showApiKeyCard = !showApiKeyCard }) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "AI Model Settings",
                            tint = if (com.example.data.remote.AiSummarizerClient.hasApiKeyConfigured(context)) Color(0xFF4CAF50) else Color(0xFFFFB300)
                        )
                    }
                    if (transcript != null) {
                        IconButton(
                            onClick = {
                                val t = transcript!!
                                val copyText = buildString {
                                    append("📝 AI Summary: ${video.title}\n\n")
                                    append("Summary:\n${t.executiveSummary}\n\n")
                                    append("Key Takeaways:\n")
                                    t.keyTakeaways.forEach { append("• $it\n") }
                                    append("\nTimeline Chapters:\n")
                                    t.segments.forEach { append("[${it.timestampFormatted}] ${it.text}\n") }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Summary", copyText))
                                Toast.makeText(context, "AI Summary Copied to Clipboard 📋", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Expandable AI Settings Card
            if (showApiKeyCard) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Connect AI Key for Live Summaries", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { showApiKeyCard = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedProvider == "gemini",
                                onClick = { selectedProvider = "gemini" },
                                label = { Text("Google Gemini (Free)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedProvider == "groq",
                                onClick = { selectedProvider = "groq" },
                                label = { Text("Groq Llama 3 (Free)") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Direct Browser Link to Get Key for Free
                        if (selectedProvider == "gemini") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = YouTubeRed.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                        context.startActivity(intent)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Get Free Gemini Key (aistudio.google.com) ↗", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                        Text("100% Free forever • 1,500 summaries/day • No credit card needed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = geminiKeyInput,
                                onValueChange = { geminiKeyInput = it },
                                label = { Text("Gemini API Key") },
                                placeholder = { Text("Paste AQ... or AIza... key here") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                        if (clip.isNotBlank()) {
                                            geminiKeyInput = clip
                                            Toast.makeText(context, "Pasted from Clipboard! 📋", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = YouTubeRed.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                                        context.startActivity(intent)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Get Free Groq Key (console.groq.com) ↗", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                        Text("100% Free • Blistering fast Llama 3 • Instant answers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = groqKeyInput,
                                onValueChange = { groqKeyInput = it },
                                label = { Text("Groq API Key") },
                                placeholder = { Text("Paste gsk_... key here") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                        if (clip.isNotBlank()) {
                                            groqKeyInput = clip
                                            Toast.makeText(context, "Pasted from Clipboard! 📋", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showApiKeyCard = false }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    com.example.data.remote.AiSummarizerClient.setAiProvider(context, selectedProvider)
                                    if (selectedProvider == "gemini") {
                                        com.example.data.remote.AiSummarizerClient.setGeminiApiKey(context, geminiKeyInput)
                                    } else {
                                        com.example.data.remote.AiSummarizerClient.setGroqApiKey(context, groqKeyInput)
                                    }
                                    Toast.makeText(context, "AI Model Saved! Generating Summary... ✨", Toast.LENGTH_SHORT).show()
                                    showApiKeyCard = false
                                    refreshTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save & Activate ✨", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp)
                        Text(
                            text = "Extracting video subtitles & analyzing speech...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (transcript != null) {
                val t = transcript!!

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = YouTubeRed,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = YouTubeRed
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("💬 Chatbot", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📝 Summary", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("⏱️ Timeline (${t.segments.size})", fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // ==========================================
                        // TAB 0: 💬 AI Chatbot with Video Knowledge
                        // ==========================================
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Quick Suggestion Prompt Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "💡 Summarize in 3 bullets",
                                    "❓ Explain like I'm 5",
                                    "🔥 Key arguments made",
                                    "🎯 Who is this video for?",
                                    "⏱️ What happens at the conclusion?"
                                ).forEach { prompt ->
                                    SuggestionChip(
                                        onClick = { sendMessage(prompt) },
                                        label = { Text(prompt, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Chat Stream
                            LazyColumn(
                                state = chatListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                if (chatMessages.isEmpty()) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color(0xFFAB47BC),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    text = "Ask me anything about this video!",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "I have extracted the spoken dialogue and analyzed the content. Ask for summaries, quotes, explanations, or specific timestamps.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                items(chatMessages) { msg ->
                                    ChatMessageBubble(
                                        message = msg,
                                        onSeekTo = onSeekTo,
                                        onSaveToNotes = onSaveToNotes,
                                        context = context
                                    )
                                }

                                if (isBotThinking) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFFAB47BC)
                                            )
                                            Text(
                                                text = "AI is analyzing video...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Input Box
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inputQuery,
                                    onValueChange = { inputQuery = it },
                                    placeholder = { Text("Ask about this video...", fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(24.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        val q = inputQuery.trim()
                                        if (q.isNotEmpty()) {
                                            inputQuery = ""
                                            sendMessage(q)
                                        }
                                    })
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (inputQuery.isNotBlank() && !isBotThinking) YouTubeRed else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable(enabled = inputQuery.isNotBlank() && !isBotThinking) {
                                            val q = inputQuery.trim()
                                            if (q.isNotEmpty()) {
                                                inputQuery = ""
                                                sendMessage(q)
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (inputQuery.isNotBlank() && !isBotThinking) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // ==========================================
                        // TAB 1: 📝 Full Structured AI Summary
                        // ==========================================
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                        // 1. Host / Creator Info Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = YouTubeRed.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Host / Creator",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = t.hostName.ifBlank { video.channelName },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Topic & Premise Card
                        if (t.topicPremise.isNotBlank()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Info,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "What This Video Is About",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = t.topicPremise,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Key Discussion Highlights
                        val points = if (t.discussionPoints.isNotEmpty()) t.discussionPoints else t.keyTakeaways
                        if (points.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Key Discussion Highlights",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }

                            itemsIndexed(points) { index, point ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            color = YouTubeRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = point,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Conclusion & Takeaway Card
                        if (t.conclusion.isNotBlank()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(imageVector = Icons.Filled.Flag, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                            Text(text = "Final Verdict / Conclusion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = t.conclusion, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                                    }
                                }
                            }
                        }

                        // 5. Save to Notes Action Button
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    val summaryNote = buildString {
                                        append("📋 AI Summary: ${video.title}\n")
                                        append("Creator: ${video.channelName}\n\n")
                                        if (t.topicPremise.isNotBlank()) {
                                            append("📌 Premise:\n${t.topicPremise}\n\n")
                                        }
                                        if (t.executiveSummary.isNotBlank()) {
                                            append("📝 Executive Summary:\n${t.executiveSummary}\n\n")
                                        }
                                        if (t.keyTakeaways.isNotEmpty()) {
                                            append("💡 Key Takeaways:\n")
                                            t.keyTakeaways.forEach { append("• $it\n") }
                                            append("\n")
                                        }
                                        if (t.conclusion.isNotBlank()) {
                                            append("🏁 Conclusion:\n${t.conclusion}\n")
                                        }
                                    }
                                    onSaveToNotes(summaryNote)
                                    Toast.makeText(context, "Saved to Video Notes! 📝", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Summary to Notes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    }
                    else -> {
                        // ==========================================
                        // TAB 2: ⏱️ Timeline Chapters
                        // ==========================================
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(t.segments, key = { it.id }) { seg ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSeekTo(seg.timestampSeconds)
                                            Toast.makeText(context, "Jumped to ${seg.timestampFormatted} ⏩", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        },
                                    color = if (seg.isKeyPoint) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = YouTubeRed,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = seg.timestampFormatted,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = seg.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Play",
                                            tint = YouTubeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Unable to generate summary for this video.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onSeekTo: (Int) -> Unit,
    onSaveToNotes: (String) -> Unit,
    context: Context
) {
    val isUser = message.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) YouTubeRed else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFAB47BC), modifier = Modifier.size(13.dp))
                            Text("AI Video Analyst", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFAB47BC))
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Answer", message.text))
                                    Toast.makeText(context, "Copied! 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = {
                                    onSaveToNotes(message.text)
                                    Toast.makeText(context, "Saved to Video Notes! 📝", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Save Note", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Text(
                    text = message.text,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Check for timestamps in AI reply, e.g. [02:30]
                if (!isUser) {
                    val timestampRegex = Regex("\\[(\\d{1,2}:\\d{2})\\]")
                    val matches = timestampRegex.findAll(message.text).toList()
                    if (matches.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            matches.take(3).forEach { match ->
                                val timeStr = match.groupValues[1]
                                val parts = timeStr.split(":")
                                val totalSec = if (parts.size == 2) (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0) else 0
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = YouTubeRed.copy(alpha = 0.15f),
                                    modifier = Modifier.clickable {
                                        onSeekTo(totalSec)
                                        Toast.makeText(context, "Jumped to $timeStr ⏩", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "▶ Jump to $timeStr",
                                        color = YouTubeRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
