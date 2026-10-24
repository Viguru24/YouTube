package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.VideoEntity
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VideoCard(
    video: VideoEntity,
    onVideoClick: (VideoEntity) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteClick: (VideoEntity) -> Unit,
    recommendationReason: String = "",
    onMuteChannel: (String) -> Unit = {},
    onSaveToSubject: (VideoEntity) -> Unit = {},
    onNotInterested: (VideoEntity) -> Unit = {},
    onChannelClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteChannelConfirm by remember { mutableStateOf(false) }

    val density = LocalDensity.current.density
    val viewConfig = LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop
    val offsetX = remember(video.youtubeId) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isDismissed by remember(video.youtubeId) { mutableStateOf(false) }

    if (isDismissed) return

    val thresholdPx = 90f * density
    val edgeThresholdPx = 55f * density

    fun formatDisplayChannelName(name: String): String {
        val trimmed = name.trim()
        return if (trimmed.length > 20) trimmed.take(18) + "…" else trimmed
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        // 1. Background revealed during rightward swipe ("Not Interested" action)
        if (offsetX.value > 10f) {
            val swipeProgress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (swipeProgress >= 1f) YouTubeRed else YouTubeRed.copy(alpha = 0.85f))
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = swipeProgress
                        scaleX = 0.8f + (0.2f * swipeProgress)
                        scaleY = 0.8f + (0.2f * swipeProgress)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "Not Interested",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Not Interested",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 1b. Background revealed during leftward swipe ("Delete Video" action)
        if (offsetX.value < -10f) {
            val swipeProgress = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (swipeProgress >= 1f) Color(0xFFD32F2F) else Color(0xFFD32F2F).copy(alpha = 0.85f))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = swipeProgress
                        scaleX = 0.8f + (0.2f * swipeProgress)
                        scaleY = 0.8f + (0.2f * swipeProgress)
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Video",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. Foreground Video Tile Card
        Card(
            onClick = { onVideoClick(video) },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(video.youtubeId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val cardWidth = size.width.toFloat()

                        val isLeftEdge = startX <= edgeThresholdPx
                        val isRightEdge = startX >= (cardWidth - edgeThresholdPx)

                        // Never intercept touches in the top-right button area (Star & Watch Later)
                        if (startX >= (cardWidth - 55f * density) && down.position.y <= 35f * density) {
                            return@awaitEachGesture
                        }

                        // If touch didn't start at the outer edges, do not intercept -> allow 100% fluid vertical scrolling
                        if (!isLeftEdge && !isRightEdge) {
                            return@awaitEachGesture
                        }

                        var totalDx = 0f
                        var totalDy = 0f
                        var isHorizontalLocked = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // Gesture release
                                if (isHorizontalLocked) {
                                    if (offsetX.value >= thresholdPx) {
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                targetValue = 600f * density,
                                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                            )
                                            isDismissed = true
                                            onNotInterested(video)
                                            android.widget.Toast.makeText(context, "Marked Not Interested 🚫", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (offsetX.value <= -thresholdPx) {
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                targetValue = -600f * density,
                                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                            )
                                            isDismissed = true
                                            onDeleteClick(video)
                                            android.widget.Toast.makeText(context, "Video Deleted 🗑️", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                    }
                                }
                                break
                            }

                            val drag = change.positionChange()
                            totalDx += drag.x
                            totalDy += drag.y

                            if (!isHorizontalLocked) {
                                // If vertical movement is dominant, cancel and let LazyGrid scroll freely
                                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                                    break
                                } else if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * 1.4f) {
                                    // Verify swipe direction corresponds with outer edge
                                    if ((isLeftEdge && totalDx > 0) || (isRightEdge && totalDx < 0)) {
                                        isHorizontalLocked = true
                                        change.consume()
                                    } else {
                                        break
                                    }
                                }
                            } else {
                                change.consume()
                                val newOffset = offsetX.value + drag.x
                                coroutineScope.launch { offsetX.snapTo(newOffset) }
                            }
                        }
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .testTag("video_card_${video.youtubeId}"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val isShort = com.example.util.YouTubeUtils.isShortVideo(video)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 1. Thumbnail on the LEFT (16:9 ratio, width = 138.dp, height = 78.dp)
                Box(
                    modifier = Modifier
                        .width(if (isShort) 84.dp else 138.dp)
                        .aspectRatio(if (isShort) 9f / 16f else 16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    val sanitizedThumb = remember(video.youtubeId, video.thumbnailUrl) {
                        val raw = video.thumbnailUrl.orEmpty()
                        when {
                            raw.isBlank() -> com.example.util.YouTubeUtils.getThumbnailUrl(video.youtubeId)
                            raw.contains("hq720.jpg") -> raw.replace("hq720.jpg", "hqdefault.jpg")
                            else -> raw
                        }
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(sanitizedThumb)
                            .crossfade(100)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Published Time / Recency Badge Top Left (e.g. 1S, 5M, 9H, 1D, LIVE 🔴)
                    val compactTime = remember(video.publishedTimeText) {
                        com.example.util.YouTubeUtils.formatCompactTime(video.publishedTimeText, defaultIfMissing = "")
                    }
                    if (compactTime.isNotBlank()) {
                        val isLive = compactTime.equals("LIVE", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .align(Alignment.TopStart)
                                .background(
                                    if (isLive) YouTubeRed.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 3.5.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = if (isLive) "LIVE 🔴" else compactTime,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Watched progress bar along bottom edge of thumbnail
                    val durationSec = remember(video.durationText) {
                        com.example.util.YouTubeUtils.parseFormattedTimeToSeconds(video.durationText)
                    }
                    val progressFraction = remember(video.lastPositionSeconds, video.lastWatchedTimestamp, durationSec) {
                        when {
                            durationSec > 0 && video.lastPositionSeconds > 0 -> {
                                (video.lastPositionSeconds.toFloat() / durationSec.toFloat()).coerceIn(0.05f, 1f)
                            }
                            video.lastWatchedTimestamp > 0L -> 1f
                            else -> 0f
                        }
                    }

                    // Duration Badge Bottom Right
                    if (video.durationText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 3.dp, bottom = if (progressFraction > 0f) 5.dp else 3.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    Color.Black.copy(alpha = 0.8f),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = video.durationText,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (progressFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .align(Alignment.BottomStart)
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .background(YouTubeRed)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 2. Video Info on the RIGHT
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 1.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = formatDisplayChannelName(video.channelName),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (onChannelClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        modifier = if (onChannelClick != null) {
                            Modifier.clickable { onChannelClick(video.channelName) }
                        } else {
                            Modifier
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (video.viewCountText.isNotBlank()) {
                            Text(
                                text = com.example.util.YouTubeUtils.formatViewCountText(video.viewCountText),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (video.viewCountText.isNotBlank() && video.publishedTimeText.isNotBlank() && !video.publishedTimeText.equals("Latest", ignoreCase = true)) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (video.publishedTimeText.isNotBlank() && !video.publishedTimeText.equals("Latest", ignoreCase = true)) {
                            Text(
                                text = video.publishedTimeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (recommendationReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = recommendationReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = YouTubeRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                }

                // 3. Right side Actions (3-dots menu & quick Favorite star)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (video.isFavorite) "Remove from Favorites" else "Save to Favorites") },
                                onClick = {
                                    showMenu = false
                                    onFavoriteToggle(video)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (video.isWatchLater) "Remove from Watch Later" else "Save to Watch Later") },
                                onClick = {
                                    showMenu = false
                                    onWatchLaterToggle(video)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📁 Save as...") },
                                onClick = {
                                    showMenu = false
                                    onSaveToSubject(video)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🚫 Not Interested") },
                                onClick = {
                                    showMenu = false
                                    onNotInterested(video)
                                    android.widget.Toast.makeText(context, "Marked as Not Interested 👎", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("↗️ Share Video Link") },
                                onClick = {
                                    showMenu = false
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, video.title)
                                        val link = if (isShort) "https://youtube.com/shorts/${video.youtubeId}" else "https://youtu.be/${video.youtubeId}"
                                        putExtra(android.content.Intent.EXTRA_TEXT, "${video.title}\n$link")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video Link"))
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "🚫 Delete Channel Permanently",
                                        color = YouTubeRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteChannelConfirm = true
                                }
                            )
                        }
                    }

                    // Quick Favorite Star Toggle
                    IconButton(
                        onClick = {
                            onFavoriteToggle(video)
                            val msg = if (!video.isFavorite) "Saved to Favorites ⭐" else "Removed from Favorites"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (video.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (video.isFavorite) GoldStar else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteChannelConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteChannelConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Permanently Delete Channel?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "All videos from '${video.channelName}' will be removed and this channel will be permanently blocked from your feed, search, and recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteChannelConfirm = false
                        onMuteChannel(video.channelName)
                        android.widget.Toast.makeText(context, "'${video.channelName}' permanently deleted and blocked 🚫", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChannelConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
