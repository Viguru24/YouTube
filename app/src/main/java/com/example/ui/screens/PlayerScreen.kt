package com.example.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    otherVideos: List<VideoEntity>,
    notes: List<VideoNoteEntity>,
    googleAccount: GoogleAccount,
    areAdvertsEnabled: Boolean = false,
    onBackClick: () -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onSelectOtherVideo: (VideoEntity) -> Unit,
    onNextVideo: () -> Unit = {},
    onPreviousVideo: () -> Unit = {},
    onAddNote: (timeSec: Int, timeStr: String, noteText: String) -> Unit,
    onDeleteNote: (noteId: Long) -> Unit,
    onOpenGoogleAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Up Next, 1 = Notes, 2 = AI Summary
    var webViewInstance by remember { mutableStateOf<Any?>(null) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }
    var timeInputText by remember { mutableStateOf("") }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isMaximized by remember { mutableStateOf(false) }
    val isFullscreen = isLandscape || isMaximized

    Scaffold(
        topBar = {
            if (!isFullscreen) {
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
            }
        },
        modifier = modifier
    ) { innerPadding ->
        // Detect YouTube Shorts by duration ("0:XX" = under 1 minute = Short)
        val isShort = video.durationText.matches(Regex("^0:[0-5]\\d$"))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
        ) {
            // YouTube Player View
            YouTubePlayerView(
                videoId = video.youtubeId,
                startSeconds = video.lastPositionSeconds,
                areAdvertsEnabled = areAdvertsEnabled,
                onPlayerReady = { wv ->
                    webViewInstance = wv
                },
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 40f) {
                                    isMaximized = false
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    onBackClick()
                                }
                            }
                        }
                } else if (isShort) {
                    // Shorts: vertical 9:16 aspect ratio, centred
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 40f) onBackClick()
                            }
                        }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -40f) {
                                    isMaximized = true
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else if (dragAmount > 40f) {
                                    onBackClick()
                                }
                            }
                        }
                }
            )

            // Prev / Next navigation strip
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous
                    OutlinedButton(
                        onClick = onPreviousVideo,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Previous", fontSize = 12.sp)
                    }

                    // Short badge
                    if (isShort) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = YouTubeRed
                        ) {
                            Text(
                                "⚡ Short",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Next
                    Button(
                        onClick = onNextVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Next", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {

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
                            text = { Text("Up Next", fontWeight = FontWeight.Bold) },
                            icon = { Icon(imageVector = Icons.Filled.PlaylistPlay, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Notes (${notes.size})", fontWeight = FontWeight.Bold) },
                            icon = { Icon(imageVector = Icons.Filled.StickyNote2, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("AI Summary", fontWeight = FontWeight.Bold) },
                            icon = { Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null) }
                        )
                    }

                    // Tab Content Body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp)
                            .padding(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    otherVideos.forEach { other ->
                                        PlaylistQueueItem(
                                            video = other,
                                            onClick = { onSelectOtherVideo(other) }
                                        )
                                    }
                                }
                            }
                            1 -> {
                                if (notes.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.BookmarkBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No Timestamp Notes Yet",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Tap '+ Add Note' to bookmark key moments",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        notes.forEach { note ->
                                            NoteItemRow(
                                                note = note,
                                                onJumpToTime = { sec ->
                                                    // Jump time handler
                                                },
                                                onDeleteNote = onDeleteNote
                                            )
                                        }
                                    }
                                }
                            }
                            2 -> {
                                AiTranscriptView(
                                    video = video,
                                    onSeekToTimestamp = { sec ->
                                        // Seek handler
                                    },
                                    onSaveKeyPointAsNote = { timeSec, timeStr, text ->
                                        onAddNote(timeSec, timeStr, text)
                                    }
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
                        val sec = YouTubeUtils.parseFormattedTimeToSeconds(timeInputText)
                        val formatted = if (timeInputText.isNotBlank()) timeInputText else "00:00"
                        onAddNote(sec, formatted, noteInputText.ifBlank { "Bookmark at $timeInputText" })
                        showAddNoteDialog = false
                        noteInputText = ""
                        timeInputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    modifier = Modifier.testTag("save_note_btn")
                ) {
                    Text("Save Note")
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    onClick = { onJumpToTime(note.timestampSeconds) },
                    shape = RoundedCornerShape(6.dp),
                    color = YouTubeRed
                ) {
                    Text(
                        text = note.timestampFormatted,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = note.noteText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { onDeleteNote(note.id) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete Note",
                    tint = MaterialTheme.colorScheme.error
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
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(6.dp))
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
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.channelName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
