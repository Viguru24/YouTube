package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
        return when {
            trimmed.equals("Benny Johnson", ignoreCase = true) -> "Benny J"
            trimmed.equals("Tal Oran - TheTraveler", ignoreCase = true) -> "Tal Oran"
            trimmed.equals("Warren Smith - Secret Scholar", ignoreCase = true) -> "Warren Smith"
            trimmed.equals("LARRY with Larry Elder", ignoreCase = true) -> "Larry Elder"
            trimmed.equals("The Podcast of the Lotus Eaters", ignoreCase = true) -> "Lotus Eaters"
            trimmed.equals("Dr. Steve Turley", ignoreCase = true) -> "Steve Turley"
            trimmed.length > 18 -> trimmed.take(16) + "…"
            else -> trimmed
        }
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
            val hasValidTime = video.publishedTimeText.isNotBlank() &&
                    !video.publishedTimeText.equals("Recent", ignoreCase = true) &&
                    !video.publishedTimeText.equals("Recently", ignoreCase = true)

            Column(modifier = Modifier.fillMaxWidth()) {
                // Thumbnail container with Duration Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isShort) 9f / 16f else 16f / 9f)
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

                    // Published Time Badge Top Left (compact: 6H, 1D, 2M)
                    if (hasValidTime) {
                        val compactTime = com.example.util.YouTubeUtils.formatCompactTime(video.publishedTimeText)
                        if (compactTime.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.TopStart)
                                    .background(
                                        Color.Black.copy(alpha = 0.75f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = compactTime,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Duration Badge Bottom Right
                    if (video.durationText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    Color.Black.copy(alpha = 0.75f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = video.durationText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Top Right Quick Actions (Favorite & Watch Later)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { onWatchLaterToggle(video) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (video.isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                                contentDescription = "Watch Later",
                                tint = if (video.isWatchLater) YouTubeRed else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onFavoriteToggle(video) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (video.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (video.isFavorite) GoldStar else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Video Meta Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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

                            if (video.viewCountText.isNotBlank()) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = video.viewCountText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        if (recommendationReason.isNotBlank()) {
                            Text(
                                text = "✨ $recommendationReason",
                                style = MaterialTheme.typography.labelSmall,
                                color = YouTubeRed,
                                maxLines = 1,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // 3-Dots Dropdown Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
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
                                text = { Text("🚫 Mute '${video.channelName}'") },
                                onClick = {
                                    showMenu = false
                                    onMuteChannel(video.channelName)
                                    android.widget.Toast.makeText(context, "Muted ${video.channelName} 🚫", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
