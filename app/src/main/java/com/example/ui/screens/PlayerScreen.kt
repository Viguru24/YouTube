package com.example.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GoogleAccount
import com.example.data.model.VideoEntity
import com.example.data.model.VideoNoteEntity
import com.example.ui.components.AiTranscriptView
import com.example.ui.components.VideoControlDeck
import com.example.ui.components.YouTubePlayerView
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed
import com.example.util.YouTubeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    video: VideoEntity,
    notes: List<VideoNoteEntity>,
    playlistVideos: List<VideoEntity>,
    googleAccount: GoogleAccount,
    onBackClick: () -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onAddNote: (timestampSeconds: Int, timestampFormatted: String, noteText: String) -> Unit,
    onDeleteNote: (noteId: Long) -> Unit,
    onSelectOtherVideo: (VideoEntity) -> Unit,
    onOpenGoogleAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: AI Transcript, 1: Bookmarks & Notes, 2: Playlist Queue

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }
    var timeInputText by remember { mutableStateOf("01:00") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("player_back_btn")
                    ) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onFavoriteToggle(video) }) {
                        Icon(
                            imageVector = if (video.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (video.isFavorite) GoldStar else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { onWatchLaterToggle(video) }) {
                        Icon(
                            imageVector = if (video.isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                            contentDescription = "Watch Later"
                        )
                    }

                    // Google Account Avatar Button
                    IconButton(
                        onClick = onOpenGoogleAuth,
                        modifier = Modifier.testTag("player_google_auth_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (googleAccount.isSignedIn) Color(0xFF4285F4) else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = googleAccount.avatarInitials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // YouTube IFrame Player View
            YouTubePlayerView(
                videoId = video.youtubeId,
                startSeconds = video.lastPositionSeconds,
                onPlayerReady = { wv ->
                    webViewInstance = wv
                }
            )

            // Video Easy-Control Deck (10 Smart Features)
            VideoControlDeck(
                webView = webViewInstance,
                videoTitle = video.title,
                videoId = video.youtubeId
            )

            // Video Header Title & Channel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { },
                            label = { Text(video.category, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AssistChip(
                            onClick = { },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "Ad-Free",
                                    tint = Color(0xFF1E88E5),
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = { Text("Ad-Free", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    Button(
                        onClick = { showAddNoteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("add_note_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Note", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Tab Row for AI Transcript, Bookmarks, and Queue
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = YouTubeRed
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("AI Transcript") },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.testTag("tab_ai_transcript")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bookmarks (${notes.size})") },
                    icon = { Icon(Icons.Filled.StickyNote2, contentDescription = null) },
                    modifier = Modifier.testTag("tab_bookmarks")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Queue") },
                    icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                    modifier = Modifier.testTag("tab_queue")
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // AI Transcript View
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            AiTranscriptView(
                                video = video,
                                onSeekToTimestamp = { seconds ->
                                    webViewInstance?.loadUrl("javascript:seekToSeconds($seconds)")
                                },
                                onSaveKeyPointAsNote = { sec, timeStr, text ->
                                    onAddNote(sec, timeStr, text)
                                }
                            )
                        }
                    }
                }

                1 -> {
                    // Bookmarks & Timestamp Notes Tab
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Custom Bookmarks",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap timestamp to jump video",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (notes.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No notes saved for this video yet. Tap 'Add Note' or save key points from the AI Transcript tab!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(notes, key = { it.id }) { note ->
                                NoteItemRow(
                                    note = note,
                                    onJumpToTime = { seconds ->
                                        webViewInstance?.loadUrl("javascript:seekToSeconds($seconds)")
                                    },
                                    onDeleteNote = onDeleteNote
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // Playlist Queue Tab
                    val otherVideos = playlistVideos.filter { it.youtubeId != video.youtubeId }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Next in Playlist",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (otherVideos.isEmpty()) {
                            item {
                                Text(
                                    text = "No other videos in queue.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(otherVideos, key = { "rel_${it.youtubeId}" }) { other ->
                                PlaylistQueueItem(
                                    video = other,
                                    onClick = { onSelectOtherVideo(other) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Timestamped Note Dialog
    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.BookmarkAdd, contentDescription = null, tint = YouTubeRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Timestamp Note")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = timeInputText,
                        onValueChange = { timeInputText = it },
                        label = { Text("Timestamp (MM:SS or HH:MM:SS)") },
                        placeholder = { Text("02:15") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Timer, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("note_timestamp_input")
                    )

                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
                        label = { Text("Note / Bookmark Comment") },
                        placeholder = { Text("e.g. Important code demo here...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("note_text_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteInputText.isNotBlank()) {
                            val seconds = YouTubeUtils.parseFormattedTimeToSeconds(timeInputText)
                            onAddNote(seconds, timeInputText, noteInputText)
                            noteInputText = ""
                            showAddNoteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    modifier = Modifier.testTag("confirm_save_note_btn")
                ) {
                    Text("Save Bookmark Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NoteItemRow(
    note: VideoNoteEntity,
    onJumpToTime: (Int) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp Chip (Click to jump!)
            Surface(
                onClick = { onJumpToTime(note.timestampSeconds) },
                shape = RoundedCornerShape(8.dp),
                color = YouTubeRed,
                modifier = Modifier.testTag("jump_time_btn_${note.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Jump",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = note.timestampFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = note.noteText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { onDeleteNote(note.id) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete Note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistQueueItem(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(65.dp)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
