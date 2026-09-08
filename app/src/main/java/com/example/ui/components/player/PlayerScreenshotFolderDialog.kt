package com.example.ui.components.player

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldStar
import com.example.util.ScreenshotManager

@Composable
fun PlayerScreenshotFolderDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    activeFolder: String,
    onFolderSelected: (String) -> Unit
) {
    if (!visible) return

    var newFolderInput by remember { mutableStateOf("") }
    var isCreatingFolder by remember { mutableStateOf(false) }
    val folders = remember(visible) {
        mutableStateListOf(*ScreenshotManager.getFolders(context).toTypedArray())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissRequest() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF18181A).copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .width(290.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Folder",
                            tint = GoldStar,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Screenshot Folder",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "Save in: Pictures/Vixz/$activeFolder",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Folder List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(folders) { fName ->
                        val isSelected = fName == activeFolder
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldStar.copy(alpha = 0.2f) else Color(0xFF242426),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, GoldStar) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ScreenshotManager.setActiveFolder(context, fName)
                                    onFolderSelected(fName)
                                    Toast.makeText(context, "Active folder: $fName", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.FolderSpecial else Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) GoldStar else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = fName,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isSelected) {
                                    Text("Active", color = GoldStar, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // New Folder Input or Button
                if (isCreatingFolder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderInput,
                            onValueChange = { newFolderInput = it },
                            placeholder = { Text("Folder name...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GoldStar,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        IconButton(
                            onClick = {
                                val clean = newFolderInput.trim()
                                if (clean.isNotBlank()) {
                                    ScreenshotManager.addFolder(context, clean)
                                    ScreenshotManager.setActiveFolder(context, clean)
                                    folders.clear()
                                    folders.addAll(ScreenshotManager.getFolders(context))
                                    onFolderSelected(clean)
                                    newFolderInput = ""
                                    isCreatingFolder = false
                                    Toast.makeText(context, "Created '$clean'", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Confirm", tint = GoldStar)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { isCreatingFolder = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
