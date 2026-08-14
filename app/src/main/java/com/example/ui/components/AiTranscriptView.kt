package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoEntity
import com.example.ui.theme.YouTubeRed
import com.example.util.TranscriptGenerator
import com.example.util.VideoAiTranscript

@Composable
fun AiTranscriptView(
    video: VideoEntity,
    onSeekToTimestamp: (Int) -> Unit,
    onSaveKeyPointAsNote: (timestampSec: Int, timestampFormatted: String, text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var transcript by remember(video.youtubeId) { mutableStateOf<VideoAiTranscript?>(null) }
    var isLoading by remember(video.youtubeId) { mutableStateOf(true) }

    LaunchedEffect(video.youtubeId) {
        isLoading = true
        try {
            transcript = com.example.data.remote.YouTubeCaptionService.getAuthenticSummary(video)
        } catch (e: Exception) {
            // Ignore
        } finally {
            isLoading = false
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSummaryExpanded by remember { mutableStateOf(true) }

    val filteredSegments = remember(searchQuery, transcript) {
        val segs = transcript?.segments ?: emptyList()
        if (searchQuery.isBlank()) {
            segs
        } else {
            segs.filter {
                it.text.contains(searchQuery, ignoreCase = true) ||
                        it.timestampFormatted.contains(searchQuery)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Badge Header & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF7C4DFF).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gemini AI Transcript",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C4DFF)
                        )
                    }
                }
            }

            // Copy Full Transcript Button
            IconButton(
                onClick = {
                    transcript?.let { t ->
                        val fullText = buildString {
                            append("AI Executive Summary:\n${t.executiveSummary}\n\n")
                            append("Transcript:\n")
                            t.segments.forEach {
                                append("[${it.timestampFormatted}] ${it.text}\n")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AI Transcript", fullText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Full AI transcript copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("copy_transcript_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy Transcript",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search in Transcript
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search transcript keywords...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transcript_search_input")
        )

        // Executive Summary Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSummaryExpanded = !isSummaryExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = YouTubeRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Overview & Highlights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = if (isSummaryExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle"
                    )
                }

                AnimatedVisibility(visible = isSummaryExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = transcript?.executiveSummary ?: "Analyzing video subtitles and spoken dialogue...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Key Takeaways:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        (transcript?.keyTakeaways ?: emptyList()).forEach { takeaway ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    fontWeight = FontWeight.Bold,
                                    color = YouTubeRed
                                )
                                Text(
                                    text = takeaway,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Interactive Transcript Lines Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Interactive Timestamps (${filteredSegments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap time to jump player",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Transcript Lines
        if (filteredSegments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transcript matches '$searchQuery'",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredSegments.forEach { segment ->
                    TranscriptSegmentRow(
                        segment = segment,
                        onSeekToTimestamp = onSeekToTimestamp,
                        onSaveKeyPointAsNote = onSaveKeyPointAsNote
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptSegmentRow(
    segment: com.example.util.TranscriptSegment,
    onSeekToTimestamp: (Int) -> Unit,
    onSaveKeyPointAsNote: (Int, String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (segment.isKeyPoint)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clickable Timestamp Chip
            Surface(
                onClick = { onSeekToTimestamp(segment.timestampSeconds) },
                shape = RoundedCornerShape(6.dp),
                color = YouTubeRed,
                modifier = Modifier.testTag("seek_time_${segment.timestampFormatted}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Jump",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = segment.timestampFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    onSaveKeyPointAsNote(
                        segment.timestampSeconds,
                        segment.timestampFormatted,
                        segment.text
                    )
                },
                modifier = Modifier.testTag("save_segment_note_${segment.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.BookmarkAdd,
                    contentDescription = "Bookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
