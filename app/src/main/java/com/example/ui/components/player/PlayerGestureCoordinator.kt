package com.example.ui.components.player

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

@Composable
fun PlayerGestureCoordinator(
    videoId: String,
    coroutineScope: CoroutineScope,
    activity: Activity?,
    audioManager: AudioManager,
    maxAudioVolume: Int,
    zoomScale: Float,
    onZoomScaleChange: (Float) -> Unit,
    panOffsetX: Float,
    onPanOffsetXChange: (Float) -> Unit,
    panOffsetY: Float,
    onPanOffsetYChange: (Float) -> Unit,
    gestureBrightness: Float,
    onGestureBrightnessChange: (Float) -> Unit,
    isAdjustingBrightness: Boolean,
    onIsAdjustingBrightnessChange: (Boolean) -> Unit,
    gestureVolumeFraction: Float,
    onGestureVolumeFractionChange: (Float) -> Unit,
    isAdjustingVolume: Boolean,
    onIsAdjustingVolumeChange: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (isForward: Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onNextVideo: (() -> Unit)?,
    onPreviousVideo: (() -> Unit)?,
    onSwipeVideoFeedback: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapX by remember { mutableFloatStateOf(0f) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(videoId) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startTime = System.currentTimeMillis()
                        val startX = down.position.x
                        val startY = down.position.y
                        var lastY = startY
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        var isDrag = false
                        var isPinch = false
                        val dragZone = when {
                            startX < w * 0.22f -> 1 // Left: Brightness
                            startX > w * 0.78f -> 2 // Right: Volume
                            else -> 3 // Center: Next/Prev Video Swipe or Pan
                        }
                        var prevPinchDist = 0f
                        var prevCenter = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes
                            val activePointers = pointers.filter { it.pressed }

                            if (activePointers.size >= 2) {
                                // 2-FINGER PINCH & PAN
                                isPinch = true
                                isDrag = false
                                onIsAdjustingBrightnessChange(false)
                                onIsAdjustingVolumeChange(false)

                                val p1 = activePointers[0].position
                                val p2 = activePointers[1].position
                                val dist = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
                                val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                                if (prevPinchDist > 0f) {
                                    val scale = dist / prevPinchDist
                                    val newZoom = (zoomScale * scale).coerceIn(1.0f, 5.0f)
                                    onZoomScaleChange(newZoom)

                                    if (newZoom > 1.02f) {
                                        val panX = center.x - prevCenter.x
                                        val panY = center.y - prevCenter.y
                                        val maxPanX = (w * (newZoom - 1f)) / 2f
                                        val maxPanY = (h * (newZoom - 1f)) / 2f
                                        onPanOffsetXChange((panOffsetX + panX).coerceIn(-maxPanX, maxPanX))
                                        onPanOffsetYChange((panOffsetY + panY).coerceIn(-maxPanY, maxPanY))
                                    } else {
                                        onPanOffsetXChange(0f)
                                        onPanOffsetYChange(0f)
                                        onZoomScaleChange(1.0f)
                                    }
                                }
                                prevPinchDist = dist
                                prevCenter = center
                                pointers.forEach { it.consume() }
                            } else if (activePointers.size == 1 && !isPinch) {
                                val p = activePointers[0]
                                val dx = p.position.x - startX
                                val dy = p.position.y - startY
                                lastY = p.position.y

                                if (!isDrag && (abs(dy) > 18f || abs(dx) > 18f)) {
                                    isDrag = true
                                    if (dragZone == 1) {
                                        onIsAdjustingBrightnessChange(true)
                                        onIsAdjustingVolumeChange(false)
                                        val currentLp = activity?.window?.attributes?.screenBrightness ?: -1f
                                        onGestureBrightnessChange(if (currentLp in 0.01f..1.0f) currentLp else 0.5f)
                                    } else if (dragZone == 2) {
                                        onIsAdjustingVolumeChange(true)
                                        onIsAdjustingBrightnessChange(false)
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        onGestureVolumeFractionChange(curVol.toFloat() / maxAudioVolume.toFloat())
                                    }
                                }

                                if (isDrag) {
                                    val deltaY = -(p.position.y - p.previousPosition.y)
                                    val deltaX = p.position.x - p.previousPosition.x
                                    p.consume()

                                    if (dragZone == 1) {
                                        val delta = deltaY / (h * 0.40f)
                                        val newB = (gestureBrightness + delta).coerceIn(0.01f, 1.0f)
                                        onGestureBrightnessChange(newB)
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            lp.screenBrightness = newB
                                            act.window.attributes = lp
                                        }
                                    } else if (dragZone == 2) {
                                        val delta = deltaY / (h * 0.40f)
                                        val newV = (gestureVolumeFraction + delta).coerceIn(0f, 1f)
                                        onGestureVolumeFractionChange(newV)
                                        val targetVol = round(newV * maxAudioVolume).toInt().coerceIn(0, maxAudioVolume)
                                        try {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        } catch (e: Exception) { }
                                    } else if (zoomScale > 1.02f) {
                                        val maxPanX = (w * (zoomScale - 1f)) / 2f
                                        val maxPanY = (h * (zoomScale - 1f)) / 2f
                                        onPanOffsetXChange((panOffsetX + deltaX).coerceIn(-maxPanX, maxPanX))
                                        onPanOffsetYChange((panOffsetY + deltaY).coerceIn(-maxPanY, maxPanY))
                                    }
                                }
                            }
                        } while (activePointers.isNotEmpty())

                        // Touch released
                        val duration = System.currentTimeMillis() - startTime
                        if (!isDrag && !isPinch && duration < 320) {
                            val now = System.currentTimeMillis()
                            val isDouble = (now - lastTapTime < 280L) && (abs(startX - lastTapX) < 120f)
                            if (isDouble) {
                                singleTapJob?.cancel()
                                lastTapTime = 0L
                                if (startX < w * 0.35f) {
                                    onDoubleTapSeek(false)
                                } else if (startX > w * 0.65f) {
                                    onDoubleTapSeek(true)
                                } else {
                                    onToggleFullscreen()
                                }
                            } else {
                                lastTapTime = now
                                lastTapX = startX
                                singleTapJob?.cancel()
                                singleTapJob = coroutineScope.launch {
                                    delay(240)
                                    onSingleTap()
                                }
                            }
                        } else if (isDrag && dragZone == 3 && zoomScale <= 1.05f) {
                            val totalDy = startY - lastY
                            if (totalDy > 90f) {
                                onSwipeVideoFeedback("⏭️ Next Video")
                                coroutineScope.launch {
                                    delay(650)
                                    onSwipeVideoFeedback(null)
                                }
                                onNextVideo?.invoke()
                            } else if (totalDy < -90f) {
                                onSwipeVideoFeedback("⏮️ Previous Video")
                                coroutineScope.launch {
                                    delay(650)
                                    onSwipeVideoFeedback(null)
                                }
                                onPreviousVideo?.invoke()
                            }
                        }

                        if (isDrag) {
                            coroutineScope.launch {
                                delay(1200)
                                onIsAdjustingBrightnessChange(false)
                                onIsAdjustingVolumeChange(false)
                            }
                        }
                    }
                }
            }
    )
}
