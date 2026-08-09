package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToSubjectDialog(
    video: VideoEntity,
    categories: List<PlaylistCategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveToSubject: (subjectName: String) -> Unit,
    onAddNewSubject: (subjectName: String) -> Unit = {}
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(video.category.ifBlank { "General" }) }

    val defaultCategories = listOf("Tech & Code", "Music", "Tutorials", "Gaming", "Focus & Ambient", "General")
    val allCategoryNames = (defaultCategories + categories.map { it.name }).distinct()

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
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
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
                            .heightIn(max = 200.dp),
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
                    if (isCreatingNew) {
                        val trimmed = newSubjectName.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddNewSubject(trimmed)
                            onSaveToSubject(trimmed)
                        }
                    } else {
                        onSaveToSubject(selectedSubject)
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
