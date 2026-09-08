package com.example.ui.components.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.unit.dp
import com.example.ui.theme.YouTubeRed

/**
 * Premium white options pill displayed when the player is paused:
 * [ 👍 Like ] | [ 👎 Dislike ] | [ ↗️ Share ] | [ ✨ AI Summary ] | [ ⬇️ Download ]
 */
@Composable
fun PlayerPauseActionStrip(
    visible: Boolean,
    isFullscreen: Boolean,
    context: Context,
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
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White.copy(alpha = 0.93f),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE0E0E0))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                // 1. 👍 Like
                IconButton(
                    onClick = {
                        onFavoriteToggle()
                        Toast.makeText(context, if (!isFavorite) "Liked 👍" else "Unliked", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (isFavorite) YouTubeRed else Color(0xFF444444),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(modifier = Modifier.width(0.5.dp).height(16.dp).background(Color(0xFFDDDDDD)))

                // 2. 👎 I Don't Like (Lower in Algorithm & Skip to Next)
                IconButton(
                    onClick = {
                        onDislikeToggle()
                        Toast.makeText(context, "👎 I don't like • Next video", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isDisliked) YouTubeRed.copy(alpha = 0.20f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "I don't like",
                        tint = if (isDisliked) YouTubeRed else Color(0xFF444444),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(modifier = Modifier.width(0.5.dp).height(16.dp).background(Color(0xFFDDDDDD)))

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
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Box(modifier = Modifier.width(0.5.dp).height(16.dp).background(Color(0xFFDDDDDD)))

                // 4. ✨ AI Summary
                IconButton(
                    onClick = onAiSummaryClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF8E24AA).copy(alpha = 0.10f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI Summary",
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Box(modifier = Modifier.width(0.5.dp).height(16.dp).background(Color(0xFFDDDDDD)))

                // 5. ⬇️ Download
                IconButton(
                    onClick = { if (isDownloaded) onDeleteDownloadClick() else onDownloadClick() },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (downloadProgress in 1..99) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(18.dp),
                            color = YouTubeRed,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download",
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
