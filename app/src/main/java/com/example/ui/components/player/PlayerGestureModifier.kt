package com.example.ui.components.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

/**
 * Encapsulates the complete 5-zone touch, gesture, and pinch-to-zoom engine for the video player:
 * - Left flank (< 25% width): Vertical drag for Brightness
 * - Right flank (> 75% width): Vertical drag for Volume
 * - Middle area (25% - 75% width): Vertical drag strictly ignored
 * - Left zone (< 38% width): Single / repeated tap seeks back (-10s, -20s...)
 * - Right zone (> 62% width): Single / repeated tap seeks forward (+10s, +20s...)
 * - Center zone (38% - 62% width): Single tap toggles Play / Pause; double tap resets zoom or toggles fullscreen
 * - Two-finger pinch: Smooth zoom (1.0x to 5.0x) and 2D pan
 * - Horizontal swipe: Scrub / Seek across video duration
 */
fun Modifier.playerGestureEngine(
    context: Context,
    videoId: String,
    exoPlayer: ExoPlayer,
    streamUrl: String?,
    useWebPlayerFallback: Boolean,
    webViewRef: android.webkit.WebView?,
    totalDurationMs: Long,
    zoomScale: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    onZoomChange: (scale: Float, panX: Float, panY: Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    isPlayingState: Boolean,
    onPlayingStateChange: (Boolean) -> Unit,
    areControlsVisible: Boolean,
    onControlsVisibilityChange: (Boolean) -> Unit,
    onPlayPauseFeedback: (Boolean?) -> Unit,
    onSeekFeedback: (String?) -> Unit,
    onAdjustingBrightness: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onAdjustingVolume: (Boolean) -> Unit,
    onVolumeFractionChange: (Float) -> Unit,
    activity: Activity?,
    audioManager: AudioManager,
    maxAudioVolume: Int,
    coroutineScope: CoroutineScope
): Modifier = this.pointerInput(videoId) {
    val viewConfig = ViewConfiguration.get(context)
    val touchSlop = viewConfig.scaledTouchSlop.toFloat()

    awaitPointerEventScope {
        var lastTapTime = 0L
        var lastTapX = 0f
        var consecutiveSeekCount = 0
        var lastSeekDirection = 0 // -1 for rewind, 1 for fast-forward
        var singleTapJob: Job? = null
        var lastAppliedVolume = -1

        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downTime = System.currentTimeMillis()
            val startPos = down.position
            val w = size.width.toFloat()
            val h = size.height.toFloat()

            // If a second tap starts within 320ms in middle, cancel single-tap toggle so play/pause NEVER fires on double-tap!
            if (downTime - lastTapTime < 320L && abs(startPos.x - lastTapX) < w * 0.25f) {
                singleTapJob?.cancel()
                singleTapJob = null
            }

            var totalDx = 0f
            var totalDy = 0f
            var isDragging = false
            var dragMode = 0 // 0: None, 1: Brightness (Left), 2: Volume (Right), 3: Scrub (Horizontal)
            var scrubStartPos = 0L
            var scrubTargetPos = 0L
            var isPinching = false
            var prevPinchDist = 0f
            var prevCentroid: Offset? = null
            var localBrightness = 0.5f
            var localVolume = 0.5f

            while (true) {
                val event = awaitPointerEvent()
                val activePointers = event.changes.filter { it.pressed }

                if (activePointers.isEmpty()) {
                    // Fingers lifted
                    val duration = System.currentTimeMillis() - downTime
                    val movedDist = hypot(totalDx, totalDy)
                    prevPinchDist = 0f
                    prevCentroid = null

                    val wasConsumedByChild = event.changes.any { it.isConsumed } || down.isConsumed

                    if (!isDragging && !isPinching && movedDist < touchSlop && duration < 450 && !wasConsumedByChild) {
                        val now = System.currentTimeMillis()
                        val xRatio = if (w > 0f) startPos.x / w else 0.5f

                        if (xRatio < 0.38f) {
                            // FASTBACK (REWIND) ZONE
                            singleTapJob?.cancel()
                            singleTapJob = null

                            val isConsecutive = (now - lastTapTime < 600L) && (lastSeekDirection == -1)
                            if (isConsecutive) {
                                consecutiveSeekCount++
                            } else {
                                consecutiveSeekCount = 1
                                lastSeekDirection = -1
                            }
                            lastTapTime = now
                            lastTapX = startPos.x

                            val currentPos = exoPlayer.currentPosition
                            val newPos = (currentPos - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            val totalSec = consecutiveSeekCount * 10
                            onSeekFeedback("⏪ -${totalSec}s")
                            coroutineScope.launch {
                                delay(800)
                                if (System.currentTimeMillis() - lastTapTime >= 750L) {
                                    onSeekFeedback(null)
                                    consecutiveSeekCount = 0
                                    lastSeekDirection = 0
                                }
                            }
                        } else if (xRatio > 0.62f) {
                            // FAST FORWARD ZONE
                            singleTapJob?.cancel()
                            singleTapJob = null

                            val isConsecutive = (now - lastTapTime < 600L) && (lastSeekDirection == 1)
                            if (isConsecutive) {
                                consecutiveSeekCount++
                            } else {
                                consecutiveSeekCount = 1
                                lastSeekDirection = 1
                            }
                            lastTapTime = now
                            lastTapX = startPos.x

                            val currentPos = exoPlayer.currentPosition
                            val dur = if (totalDurationMs > 0) totalDurationMs else Long.MAX_VALUE
                            val newPos = (currentPos + 10_000L).coerceAtMost(dur)
                            exoPlayer.seekTo(newPos)
                            val totalSec = consecutiveSeekCount * 10
                            onSeekFeedback("⏩ +${totalSec}s")
                            coroutineScope.launch {
                                delay(800)
                                if (System.currentTimeMillis() - lastTapTime >= 750L) {
                                    onSeekFeedback(null)
                                    consecutiveSeekCount = 0
                                    lastSeekDirection = 0
                                }
                            }
                        } else {
                            // MIDDLE AREA (0.38f .. 0.62f) - PAUSE / PLAY ONLY!
                            consecutiveSeekCount = 0
                            lastSeekDirection = 0

                            val isDoubleTap = (now - lastTapTime < 320L) && (abs(startPos.x - lastTapX) < w * 0.25f)
                            if (isDoubleTap) {
                                singleTapJob?.cancel()
                                singleTapJob = null
                                lastTapTime = 0L

                                if (zoomScale > 1.05f) {
                                    onZoomChange(1f, 0f, 0f)
                                } else {
                                    onToggleFullscreen()
                                }
                            } else {
                                lastTapTime = now
                                lastTapX = startPos.x

                                singleTapJob?.cancel()
                                singleTapJob = coroutineScope.launch {
                                    delay(220L)

                                    val wasPlaying = if (streamUrl != null && !useWebPlayerFallback) {
                                        exoPlayer.isPlaying
                                    } else {
                                        isPlayingState
                                    }
                                    val willPlay = !wasPlaying

                                    if (streamUrl != null && !useWebPlayerFallback) {
                                        if (willPlay) {
                                            exoPlayer.play()
                                        } else {
                                            exoPlayer.pause()
                                        }
                                    } else {
                                        webViewRef?.evaluateJavascript(
                                            "var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); }",
                                            null
                                        )
                                    }
                                    onPlayingStateChange(willPlay)
                                    onControlsVisibilityChange(!willPlay || !areControlsVisible)
                                    onPlayPauseFeedback(willPlay)
                                    coroutineScope.launch {
                                        delay(650)
                                        onPlayPauseFeedback(null)
                                    }
                                }
                            }
                        }
                    }

                    if (isDragging) {
                        if (dragMode == 3) {
                            exoPlayer.seekTo(scrubTargetPos)
                            coroutineScope.launch {
                                delay(800)
                                onSeekFeedback(null)
                            }
                        } else {
                            coroutineScope.launch {
                                delay(1000)
                                onAdjustingBrightness(false)
                                onAdjustingVolume(false)
                            }
                        }
                    }

                    isPinching = false
                    isDragging = false
                    dragMode = 0
                    break
                }

                if (activePointers.size >= 2) {
                    isPinching = true
                    isDragging = false
                    val p1 = activePointers[0].position
                    val p2 = activePointers[1].position
                    val dist = hypot(p1.x - p2.x, p1.y - p2.y)
                    val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                    if (prevPinchDist > 0f) {
                        val scale = dist / prevPinchDist
                        val newZoom = (zoomScale * scale).coerceIn(1f, 5f)

                        if (prevCentroid != null && newZoom > 1.02f) {
                            val panDelta = centroid - prevCentroid!!
                            val maxPanX = (w * (newZoom - 1f)) / 2f
                            val maxPanY = (h * (newZoom - 1f)) / 2f
                            val newPanX = (panOffsetX + panDelta.x).coerceIn(-maxPanX, maxPanX)
                            val newPanY = (panOffsetY + panDelta.y).coerceIn(-maxPanY, maxPanY)
                            onZoomChange(newZoom, newPanX, newPanY)
                        } else if (newZoom <= 1.02f) {
                            onZoomChange(1f, 0f, 0f)
                        }
                    }
                    prevPinchDist = dist
                    prevCentroid = centroid
                    event.changes.forEach { it.consume() }
                } else if (activePointers.size == 1) {
                    val change = activePointers[0]
                    val dx = change.position.x - startPos.x
                    val dy = change.position.y - startPos.y
                    totalDx = dx
                    totalDy = dy

                    if (zoomScale > 1.05f && !isPinching) {
                        val maxPanX = (w * (zoomScale - 1f)) / 2f
                        val maxPanY = (h * (zoomScale - 1f)) / 2f
                        val dragChange = change.positionChange()
                        val newPanX = (panOffsetX + dragChange.x).coerceIn(-maxPanX, maxPanX)
                        val newPanY = (panOffsetY + dragChange.y).coerceIn(-maxPanY, maxPanY)
                        onZoomChange(zoomScale, newPanX, newPanY)
                        change.consume()
                    } else if (!isPinching) {
                        val absX = abs(dx)
                        val absY = abs(dy)

                        if (!isDragging) {
                            val xRatio = if (w > 0f) startPos.x / w else 0.5f
                            // Vertical Drag (Left Edge <= 25% = Brightness, Right Edge >= 75% = Volume)
                            if (absY > touchSlop && absY > absX * 1.15f) {
                                if (xRatio <= 0.25f) {
                                    isDragging = true
                                    dragMode = 1 // Brightness strictly on Left side
                                    onAdjustingBrightness(true)
                                    val currentLpBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                    localBrightness = if (currentLpBrightness in 0.01f..1.0f) {
                                        currentLpBrightness
                                    } else {
                                        try {
                                            val sysVal = Settings.System.getInt(
                                                context.contentResolver,
                                                Settings.System.SCREEN_BRIGHTNESS,
                                                128
                                            )
                                            (sysVal / 255f).coerceIn(0.01f, 1.0f)
                                        } catch (e: Exception) {
                                            0.5f
                                        }
                                    }
                                    onBrightnessChange(localBrightness)
                                } else if (xRatio >= 0.75f) {
                                    isDragging = true
                                    dragMode = 2 // Volume strictly on Right side
                                    onAdjustingVolume(true)
                                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    localVolume = (currentVol.toFloat() / maxAudioVolume.toFloat()).coerceIn(0f, 1f)
                                    onVolumeFractionChange(localVolume)
                                    lastAppliedVolume = currentVol
                                }
                                // When 0.25f < xRatio < 0.75f (middle area), swiping up/down is strictly ignored!
                            }
                            // Horizontal Drag (Left/Right Swiping = Scrub / Seek across video player)
                            else if (absX > touchSlop && absX > absY * 1.15f) {
                                isDragging = true
                                dragMode = 3 // Horizontal Scrub
                                scrubStartPos = exoPlayer.currentPosition
                                scrubTargetPos = scrubStartPos
                            }
                        }

                        if (isDragging) {
                            val dragDeltaY = change.position.y - change.previousPosition.y
                            change.consume()

                            val effectiveHeight = (h * 0.45f).coerceAtLeast(180f)
                            when (dragMode) {
                                1 -> {
                                    // Brightness adjustment (Left Up/Down)
                                    val delta = -dragDeltaY / effectiveHeight
                                    localBrightness = (localBrightness + delta).coerceIn(0.01f, 1.0f)
                                    onBrightnessChange(localBrightness)
                                    activity?.let { act ->
                                        act.runOnUiThread {
                                            try {
                                                val lp = act.window.attributes
                                                lp.screenBrightness = localBrightness
                                                act.window.attributes = lp
                                            } catch (e: Exception) { }
                                        }
                                    }
                                }
                                2 -> {
                                    // Volume adjustment (Right Up/Down)
                                    val delta = -dragDeltaY / effectiveHeight
                                    localVolume = (localVolume + delta).coerceIn(0f, 1f)
                                    onVolumeFractionChange(localVolume)
                                    exoPlayer.volume = localVolume
                                    val targetVol = round(localVolume * maxAudioVolume).toInt().coerceIn(0, maxAudioVolume)
                                    if (targetVol != lastAppliedVolume) {
                                        lastAppliedVolume = targetVol
                                        try {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        } catch (e: Exception) { }
                                    }
                                }
                                3 -> {
                                    // Scrub / Seek (Left/Right Swiping)
                                    val maxDur = if (totalDurationMs > 0) totalDurationMs else (exoPlayer.duration.takeIf { it > 0 } ?: 3600_000L)
                                    val scrubSpanMs = if (maxDur < 90_000L) maxDur else 90_000L
                                    val seekOffsetMs = ((totalDx / w) * scrubSpanMs).toLong()
                                    scrubTargetPos = (scrubStartPos + seekOffsetMs).coerceIn(0L, maxDur)
                                    val diffSec = (scrubTargetPos - scrubStartPos) / 1000L
                                    val sign = if (diffSec >= 0) "+" else ""
                                    val icon = if (diffSec >= 0) "⏩" else "⏪"
                                    val formatHelper = { ms: Long ->
                                        val totalSec = (ms / 1000).coerceAtLeast(0)
                                        String.format("%02d:%02d", totalSec / 60, totalSec % 60)
                                    }
                                    onSeekFeedback("$icon ${sign}${diffSec}s (${formatHelper(scrubTargetPos)} / ${formatHelper(maxDur)})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
