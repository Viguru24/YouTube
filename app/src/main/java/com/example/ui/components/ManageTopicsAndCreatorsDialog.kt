package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTopicsAndCreatorsDialog(
    subscribedCreators: List<String>,
    categories: List<PlaylistCategoryEntity>,
    onAddCreator: (String) -> Unit,
    onRemoveCreator: (String) -> Unit,
    onRenameCreator: (oldName: String, newName: String) -> Unit,
    onAddCategory: (name: String, icon: String, colorHex: String) -> Unit,
    onRemoveCategory: (PlaylistCategoryEntity) -> Unit,
    onRenameCategory: (category: PlaylistCategoryEntity, newName: String) -> Unit,
    mutedChannels: List<com.example.data.model.MutedChannelEntity> = emptyList(),
    onBlockChannel: (String) -> Unit = {},
    onUnblockChannel: (String) -> Unit = {},
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0: Creators, 1: Topics, 2: Blocked
    val strings = com.example.util.LocalAppStrings.current

    // Add / Search State
    var newCreatorName by remember { mutableStateOf("") }
    var creatorSearchQuery by remember { mutableStateOf("") }
    var newTopicName by remember { mutableStateOf("") }
    var newBlockedChannelName by remember { mutableStateOf("") }
    var blockedSearchQuery by remember { mutableStateOf("") }

    // Rename Dialog State
    var itemToRename by remember { mutableStateOf<String?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var isRenamingCategory by remember { mutableStateOf(false) }
    var categoryBeingRenamed by remember { mutableStateOf<PlaylistCategoryEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.manageTopicsCreators,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                // Tab Selector (Creators vs Topics)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "👤 Creators (${subscribedCreators.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "🏷️ Topics (${categories.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "🚫 Blocked (${mutedChannels.size})",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        // ================= CREATORS TAB =================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Add New Creator Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newCreatorName,
                                    onValueChange = { newCreatorName = it },
                                    placeholder = { Text("Add creator / channel...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val trimmed = newCreatorName.trim()
                                        if (trimmed.isNotBlank()) {
                                            onAddCreator(trimmed)
                                            newCreatorName = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("+ Add", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Search Creators Filter
                            OutlinedTextField(
                                value = creatorSearchQuery,
                                onValueChange = { creatorSearchQuery = it },
                                placeholder = { Text("Search your subscribed creators...", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val filteredCreators = if (creatorSearchQuery.isBlank()) {
                                subscribedCreators
                            } else {
                                subscribedCreators.filter { it.contains(creatorSearchQuery.trim(), ignoreCase = true) }
                            }

                            Text(
                                text = "${filteredCreators.size} Creator(s) found",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(filteredCreators, key = { it }) { creator ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AccountCircle,
                                                    contentDescription = null,
                                                    tint = YouTubeRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = creator,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Edit / Rename Button
                                                IconButton(
                                                    onClick = {
                                                        itemToRename = creator
                                                        renameInputText = creator
                                                        isRenamingCategory = false
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "Rename Creator",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(2.dp))

                                                // Delete / Remove Button
                                                IconButton(
                                                    onClick = { onRemoveCreator(creator) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Remove Creator",
                                                        tint = Color(0xFFE53935),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // ================= TOPICS / CATEGORIES TAB =================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Add New Topic Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newTopicName,
                                    onValueChange = { newTopicName = it },
                                    placeholder = { Text("Add topic (e.g. AI News)...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val trimmed = newTopicName.trim()
                                        if (trimmed.isNotBlank()) {
                                            onAddCategory(trimmed, "📁", "#FF0000")
                                            newTopicName = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("+ Add", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Active Feed Topics (${categories.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(categories, key = { it.id }) { cat ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = cat.iconName.ifBlank { "🏷️" },
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = cat.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Edit / Rename Button
                                                IconButton(
                                                    onClick = {
                                                        itemToRename = cat.name
                                                        renameInputText = cat.name
                                                        isRenamingCategory = true
                                                        categoryBeingRenamed = cat
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "Rename Topic",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(2.dp))

                                                // Delete / Remove Button
                                                IconButton(
                                                    onClick = { onRemoveCategory(cat) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Remove Topic",
                                                        tint = Color(0xFFE53935),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // ================= BLOCKED CHANNELS TAB =================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Add Channel to Block Permanently Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newBlockedChannelName,
                                    onValueChange = { newBlockedChannelName = it },
                                    placeholder = { Text("Block channel name...", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val trimmed = newBlockedChannelName.trim()
                                        if (trimmed.isNotBlank()) {
                                            onBlockChannel(trimmed)
                                            newBlockedChannelName = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("🚫 Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Search Blocked Channels Filter
                            OutlinedTextField(
                                value = blockedSearchQuery,
                                onValueChange = { blockedSearchQuery = it },
                                placeholder = { Text("Search blocked channels...", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val filteredBlocked = if (blockedSearchQuery.isBlank()) {
                                mutedChannels
                            } else {
                                mutedChannels.filter { it.channelName.contains(blockedSearchQuery.trim(), ignoreCase = true) }
                            }

                            Text(
                                text = "${filteredBlocked.size} Channel(s) permanently blocked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            if (filteredBlocked.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (mutedChannels.isEmpty())
                                            "No permanently blocked channels.\nWhen you permanently delete a channel, it will appear here and never be recommended to you again."
                                        else
                                            "No blocked channels match '$blockedSearchQuery'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(filteredBlocked, key = { it.channelName }) { channel ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Block,
                                                        contentDescription = null,
                                                        tint = YouTubeRed,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = channel.channelName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1
                                                        )
                                                        Text(
                                                            text = "Permanently blocked",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = { onUnblockChannel(channel.channelName) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                ) {
                                                    Text("Unblock", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
            ) {
                Text(strings.closeBtn)
            }
        }
    )

    // Modal Rename Dialog
    if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = {
                Text(
                    text = if (isRenamingCategory) "Rename Topic" else "Rename Creator",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Current: $itemToRename",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInputText.trim()
                        if (trimmed.isNotBlank()) {
                            if (isRenamingCategory && categoryBeingRenamed != null) {
                                onRenameCategory(categoryBeingRenamed!!, trimmed)
                            } else if (!isRenamingCategory && itemToRename != null) {
                                onRenameCreator(itemToRename!!, trimmed)
                            }
                        }
                        itemToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}
