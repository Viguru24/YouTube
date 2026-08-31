package com.example.ui.components.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.theme.YouTubeRed

@Composable
fun PlayerPauseActionStrip(
    visible: Boolean,
    isTablet: Boolean,
    videoId: String,
    videoTitle: String,
    isFavorite: Boolean,
    isDisliked: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    onFavoriteToggle: () -> Unit,
    onDislikeToggle: () -> Unit,
    onAiSummaryClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    onPreviousVideo: (() -> Unit)? = null,
    onNextVideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val actionBtnSize = if (isTablet) 28.dp else 24.dp
    val actionIconSize = if (isTablet) 15.dp else 13.dp

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.Black.copy(alpha = 0.65f),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(
                    horizontal = if (isTablet) 5.dp else 4.dp,
                    vertical = if (isTablet) 2.dp else 1.dp
                )
            ) {
                // 0. ⏮️ Previous Video
                if (onPreviousVideo != null) {
                    IconButton(
                        onClick = { onPreviousVideo() },
                        modifier = Modifier.size(actionBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous Video",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                    Box(modifier = Modifier.width(0.75.dp).height(if (isTablet) 12.dp else 10.dp).background(Color.White.copy(alpha = 0.25f)))
                }

                // 1. 👍 Like
                IconButton(
                    onClick = {
                        onFavoriteToggle()
                        Toast.makeText(context, if (!isFavorite) "Liked 👍" else "Unliked", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(actionBtnSize)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (isFavorite) YouTubeRed else Color.White,
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                Box(modifier = Modifier.width(0.75.dp).height(if (isTablet) 12.dp else 10.dp).background(Color.White.copy(alpha = 0.25f)))

                // 2. 👎 Dislike & Auto-Skip
                IconButton(
                    onClick = { onDislikeToggle() },
                    modifier = Modifier.size(actionBtnSize)
                ) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        tint = if (isDisliked) YouTubeRed else Color.White,
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                Box(modifier = Modifier.width(0.75.dp).height(if (isTablet) 12.dp else 10.dp).background(Color.White.copy(alpha = 0.25f)))

                // 3. ↗️ Share
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, videoTitle)
                            putExtra(Intent.EXTRA_TEXT, "$videoTitle\nhttps://youtu.be/$videoId")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    },
                    modifier = Modifier.size(actionBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                Box(modifier = Modifier.width(0.75.dp).height(if (isTablet) 12.dp else 10.dp).background(Color.White.copy(alpha = 0.25f)))

                // 4. ✨ AI Summary
                IconButton(
                    onClick = { onAiSummaryClick() },
                    modifier = Modifier
                        .size(actionBtnSize)
                        .background(Color(0xFF8E24AA).copy(alpha = 0.35f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI Summary",
                        tint = Color(0xFFCE93D8),
                        modifier = Modifier.size(actionIconSize)
                    )
                }

                Box(modifier = Modifier.width(0.75.dp).height(if (isTablet) 12.dp else 10.dp).background(Color.White.copy(alpha = 0.25f)))

                // 5. ⬇️ Download
                IconButton(
                    onClick = {
                        if (isDownloaded) onDeleteDownloadClick() else onDownloadClick()
                    },
                    modifier = Modifier.size(actionBtnSize)
                ) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(actionIconSize)
                        )
                    } else if (downloadProgress in 1..99) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(actionIconSize),
                            color = YouTubeRed,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download Video",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // 6. ⏭️ Next Video
                if (onNextVideo != null) {
                    Box(modifier = Modifier.width(0.75.dp).height(if (isTablet) 12.dp else 10.dp).background(Color.White.copy(alpha = 0.25f)))
                    IconButton(
                        onClick = { onNextVideo() },
                        modifier = Modifier.size(actionBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Video",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }
            }
        }
    }
}
