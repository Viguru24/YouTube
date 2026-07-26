package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.PlaylistCategoryEntity
import com.example.ui.theme.YouTubeRed
import com.example.util.YouTubeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVideoDialog(
    categories: List<PlaylistCategoryEntity>,
    onDismiss: () -> Unit,
    onAddVideo: (
        urlOrId: String,
        title: String,
        channelName: String,
        category: String,
        durationText: String,
        initialNote: String?,
        onError: (String) -> Unit
    ) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var channelInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("10:00") }
    var noteInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember {
        mutableStateOf(categories.firstOrNull()?.name ?: "General")
    }

    val extractedVideoId = remember(urlInput) {
        YouTubeUtils.extractVideoId(urlInput)
    }

    val previewThumbnail = remember(extractedVideoId) {
        if (extractedVideoId != null) YouTubeUtils.getThumbnailUrl(extractedVideoId) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VideoCall,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add YouTube Video",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Paste a YouTube link or video ID below:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // URL Input Field
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        errorMessage = null
                    },
                    label = { Text("YouTube Link / ID") },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Link, contentDescription = null)
                    },
                    trailingIcon = {
                        if (extractedVideoId != null) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Valid Video ID",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_video_url_input")
                )

                // Thumbnail Preview Banner
                AnimatedVisibility(visible = previewThumbnail != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = previewThumbnail,
                            contentDescription = "Video Thumbnail Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ID: $extractedVideoId",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Video Title") },
                    placeholder = { Text("e.g., Kotlin Flow Crash Course") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_video_title_input")
                )

                // Channel Name & Duration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it },
                        label = { Text("Channel") },
                        placeholder = { Text("e.g., Code Academy") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )

                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text("Duration") },
                        placeholder = { Text("12:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Category Selection
                Text(
                    text = "Playlist Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                OptInCategoryChips(
                    categories = categories.map { it.name } + listOf("General"),
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )

                // Optional Initial Note
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Initial Timestamped Note (Optional)") },
                    placeholder = { Text("Key takeaway or bookmark note...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Error Message Display
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddVideo(
                        urlInput,
                        titleInput,
                        channelInput,
                        selectedCategory,
                        durationInput,
                        noteInput.ifBlank { null }
                    ) { err ->
                        errorMessage = err
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                modifier = Modifier.testTag("confirm_add_video_btn")
            ) {
                Text("Save Video", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OptInCategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    Column {
        val uniqueCategories = categories.distinct()
        val chunked = uniqueCategories.chunked(3)

        chunked.forEach { rowCategories ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                rowCategories.forEach { category ->
                    val isSelected = category.equals(selectedCategory, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouTubeRed,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}
