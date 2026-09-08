package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToSubjectDialog(
    video: VideoEntity,
    categories: List<PlaylistCategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveToSubject: (subjectName: String) -> Unit,
    onAddNewSubject: (subjectName: String) -> Unit = {},
    onSaveWithTitle: ((subjectName: String, updatedTitle: String) -> Unit)? = null
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(video.category.ifBlank { "General" }) }
    var editableTitle by remember { mutableStateOf(video.title) }
    var isAiCleaningTitle by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val allCategoryNames = (categories.map { it.name } + listOf("General")).filter { it.isNotBlank() }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FolderSpecial,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Video to Subject",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Editable Video Title with 1-Click AI Cleanup
                OutlinedTextField(
                    value = editableTitle,
                    onValueChange = { editableTitle = it },
                    label = { Text("Video Title", fontSize = 12.sp) },
                    maxLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        if (isAiCleaningTitle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = YouTubeRed
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    isAiCleaningTitle = true
                                    coroutineScope.launch {
                                        try {
                                            val clean = com.example.data.remote.AiSummarizerClient.generateCleanVideoTitle(
                                                context,
                                                editableTitle.ifBlank { video.title },
                                                video.channelName
                                            )
                                            if (clean.isNotBlank()) {
                                                editableTitle = clean
                                            }
                                        } finally {
                                            isAiCleaningTitle = false
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Clean title with AI",
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "✨ Tap the sparkle to generate a clean title (strips clickbait & emojis)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        label = { Text("New Subject / Playlist Name") },
                        placeholder = { Text("e.g. Science, Fitness, Favorites") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_subject_name_input")
                    )
                } else {
                    Text(
                        text = "Choose Subject / Playlist:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allCategoryNames) { cat ->
                            val isSelected = cat.equals(selectedSubject, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) YouTubeRed.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSubject = cat }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = cat,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { isCreatingNew = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = YouTubeRed)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Create New Subject", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = editableTitle.trim().ifBlank { video.title }
                    val targetSubject = if (isCreatingNew) newSubjectName.trim() else selectedSubject
                    if (targetSubject.isNotEmpty()) {
                        if (isCreatingNew) {
                            onAddNewSubject(targetSubject)
                        }
                        if (onSaveWithTitle != null) {
                            onSaveWithTitle(targetSubject, finalTitle)
                        } else {
                            onSaveToSubject(targetSubject)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
            ) {
                Text(if (isCreatingNew) "Create & Save" else "Save to Subject")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
