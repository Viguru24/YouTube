package com.example.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerTopBar(
    visible: Boolean,
    isTablet: Boolean,
    videoTitle: String,
    channelTitle: String,
    onBackClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDebugConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topIconBtnSize = if (isTablet) 42.dp else 36.dp
    val topIconSize = if (isTablet) 24.dp else 20.dp

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = if (isTablet) 16.dp else 8.dp, vertical = if (isTablet) 12.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(topIconBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        fontSize = if (isTablet) 15.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channelTitle.isNotBlank()) {
                        Text(
                            text = channelTitle,
                            color = Color.LightGray,
                            fontSize = if (isTablet) 12.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleDebugConsole,
                    modifier = Modifier.size(topIconBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = "Debug Stats",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(topIconSize - 2.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(topIconBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize)
                    )
                }
            }
        }
    }
}
