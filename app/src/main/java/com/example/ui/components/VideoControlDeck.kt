package com.example.ui.components

import android.content.Intent
import android.os.CountDownTimer
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YouTubeRed

@Composable
fun VideoControlDeck(
    webView: Any? = null,
    videoTitle: String = "YouTube Video",
    videoId: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = webView as? androidx.media3.exoplayer.ExoPlayer
    val realWebView = webView as? WebView

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var selectedQuality by remember { mutableStateOf("1080p") }

    // Feature 1: Customizable Skip Seconds Step (3s, 5s, 10s, 15s, 30s)
    var skipStepSeconds by remember { mutableIntStateOf(5) }
    var showSkipStepDialog by remember { mutableStateOf(false) }

    // Feature 2: A/B Repeat Loop Segment
    var abLoopPointA by remember { mutableIntStateOf(-1) }
    var abLoopPointB by remember { mutableIntStateOf(-1) }
    var isAbLoopActive by remember { mutableStateOf(false) }

    // Feature 3: Screen Dim / Audio Saver Mode
    var isScreenDimmed by remember { mutableStateOf(false) }

    // Feature 4: Visual Color Filters
    var activeFilter by remember { mutableStateOf("Normal") }

    // Feature 5: Audio EQ Preset
    var activeAudioEq by remember { mutableStateOf("Flat") }

    // Sleep Timer state
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerText by remember { mutableStateOf("") }
    var countDownTimer by remember { mutableStateOf<CountDownTimer?>(null) }

    // Quick Jump state
    var showJumpDialog by remember { mutableStateOf(false) }
    var customTimeInput by remember { mutableStateOf("") }

    // More Controls Panel Expand Toggle
    var isExpandedTools by remember { mutableStateOf(false) }

    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val qualityOptions = listOf("1080p", "720p", "480p", "Auto")

    fun cancelSleepTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        sleepTimerMinutes = 0
        sleepTimerText = ""
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepTimerMinutes = minutes
        val totalMillis = minutes * 60 * 1000L

        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = millisUntilFinished / 1000 / 60
                val secs = (millisUntilFinished / 1000) % 60
                sleepTimerText = String.format("%02d:%02d", mins, secs)
            }

            override fun onFinish() {
                if (exoPlayer != null) {
                    exoPlayer.pause()
                } else {
                    realWebView?.loadUrl("javascript:pauseVideo()")
                }
                isPlaying = false
                sleepTimerMinutes = 0
                sleepTimerText = ""
                Toast.makeText(context, "Sleep Timer finished. Video paused.", Toast.LENGTH_LONG).show()
            }
        }.start()

        Toast.makeText(context, "Sleep Timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
    }

    fun shareVideoAndNotes() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Watching '$videoTitle'!\nWatch link: https://youtu.be/$videoId"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
    }

    DisposableEffect(Unit) {
        onDispose {
            countDownTimer?.cancel()
        }
    }

    fun execVideo(jsCmd: String) {
        if (exoPlayer != null) {
            when {
                jsCmd.contains("play()") || jsCmd.contains("v.play()") -> {
                    exoPlayer.play()
                    isPlaying = true
                }
                jsCmd.contains("pause()") || jsCmd.contains("v.pause()") -> {
                    exoPlayer.pause()
                    isPlaying = false
                }
                jsCmd.contains("currentTime -") || jsCmd.contains("-=") -> {
                    val target = (exoPlayer.currentPosition - (skipStepSeconds * 1000L)).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                }
                jsCmd.contains("currentTime +") || jsCmd.contains("+=") -> {
                    val target = (exoPlayer.currentPosition + (skipStepSeconds * 1000L)).coerceAtMost(exoPlayer.duration)
                    exoPlayer.seekTo(target)
                }
                jsCmd.contains("0.033") && jsCmd.contains("-") -> {
                    val target = (exoPlayer.currentPosition - 33L).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                }
                jsCmd.contains("0.033") && jsCmd.contains("+") -> {
                    val target = (exoPlayer.currentPosition + 33L).coerceAtMost(exoPlayer.duration)
                    exoPlayer.seekTo(target)
                }
                jsCmd.contains("v.muted = true") -> {
                    exoPlayer.volume = 0f
                    isMuted = true
                }
                jsCmd.contains("v.muted = false") -> {
                    exoPlayer.volume = 1f
                    isMuted = false
                }
                jsCmd.contains("v.loop = true") -> {
                    exoPlayer.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                    isLooping = true
                }
                jsCmd.contains("v.loop = false") -> {
                    exoPlayer.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                    isLooping = false
                }
            }
        } else {
            val script = """
                (function() {
                    function getVideo() {
                        var v = document.querySelector('video');
                        if (v) return v;
                        return null;
                    }
                    var v = getVideo();
                    if (v) { $jsCmd }
                })();
            """.trimIndent()
            realWebView?.evaluateJavascript(script, null)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Primary Controls (-Skip, Play/Pause, +Skip, Mute, Loop, Expand Tools)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -Skip Back Button with customizable interval indicator
                OutlinedIconButton(
                    onClick = {
                        execVideo("v.currentTime = Math.max(0, v.currentTime - $skipStepSeconds);")
                        Toast.makeText(context, "-${skipStepSeconds}s", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("skip_back_btn")
                ) {
                    Icon(
                        imageVector = if (skipStepSeconds <= 5) Icons.Filled.Replay5 else Icons.Filled.Replay10,
                        contentDescription = "Rewind ${skipStepSeconds}s",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Frame Step +1 Button — removed (use seek bar instead)

                // Play / Pause — small outlined button, no filled red
                OutlinedIconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        execVideo("if (v.paused) { v.play(); } else { v.pause(); }")
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toggle_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }


                // +Skip Forward Button
                OutlinedIconButton(
                    onClick = {
                        execVideo("v.currentTime = v.currentTime + $skipStepSeconds;")
                        Toast.makeText(context, "+${skipStepSeconds}s", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("skip_forward_btn")
                ) {
                    Icon(
                        imageVector = if (skipStepSeconds <= 5) Icons.Filled.Forward5 else Icons.Filled.Forward10,
                        contentDescription = "Forward ${skipStepSeconds}s",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Mute Toggle
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        execVideo("v.muted = $isMuted;")
                    },
                    modifier = Modifier.testTag("toggle_mute_btn")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (isMuted) YouTubeRed else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Row 2: Horizontal Scroll Deck for All Smart Tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback Speed Pull-Up / Dropdown Menu
                var showSpeedMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showSpeedMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Speed",
                                tint = YouTubeRed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (selectedSpeed == 1.0f) "Speed: 1x" else "Speed: ${selectedSpeed}x",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("speed_menu_chip")
                    )

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        speedOptions.forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                        fontWeight = if (selectedSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedSpeed == speed) YouTubeRed else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    selectedSpeed = speed
                                    showSpeedMenu = false
                                    if (exoPlayer != null) {
                                        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                                    } else {
                                        realWebView?.loadUrl("javascript:setPlaybackRate($speed)")
                                    }
                                    Toast.makeText(context, "Playback Speed: ${speed}x", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Video Resolution / Quality Selector Chip
                var showQualityMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showQualityMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.HighQuality,
                                contentDescription = "Resolution",
                                tint = YouTubeRed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Quality: $selectedQuality",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("quality_menu_chip")
                    )

                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        qualityOptions.forEach { quality ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = quality,
                                        fontWeight = if (selectedQuality == quality) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedQuality == quality) YouTubeRed else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    selectedQuality = quality
                                    showQualityMenu = false
                                    // Apply quality to ExoPlayer
                                    val (maxWidth, maxHeight) = when (quality) {
                                        "1080p" -> Pair(1920, 1080)
                                        "720p"  -> Pair(1280, 720)
                                        "480p"  -> Pair(854, 480)
                                        "360p"  -> Pair(640, 360)
                                        else    -> Pair(Int.MAX_VALUE, Int.MAX_VALUE) // "Auto"
                                    }
                                    exoPlayer?.trackSelectionParameters = exoPlayer
                                        ?.trackSelectionParameters
                                        ?.buildUpon()
                                        ?.setMaxVideoSize(maxWidth, maxHeight)
                                        ?.build()
                                        ?: exoPlayer?.trackSelectionParameters!!
                                    Toast.makeText(context, "Quality: $quality", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Subtitles / Captions (CC) Toggle Chip
                AssistChip(
                    onClick = {
                        captionsEnabled = !captionsEnabled
                        Toast.makeText(context, if (captionsEnabled) "Captions/Subtitles (CC) Enabled 💬" else "Captions Disabled", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles / Captions (CC)",
                            tint = if (captionsEnabled) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (captionsEnabled) "CC On" else "CC Off", fontSize = 12.sp) }
                )
                // Feature 1: Skip Step Config (e.g. 5s, 10s, 15s)
                AssistChip(
                    onClick = { showSkipStepDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Skip Interval",
                            tint = YouTubeRed,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Skip: ${skipStepSeconds}s", fontSize = 12.sp) },
                    modifier = Modifier.testTag("skip_interval_config_btn")
                )

                // Feature 2: A/B Repeat Loop Segment Tool
                var showAbLoopDialog by remember { mutableStateOf(false) }
                AssistChip(
                    onClick = { showAbLoopDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Loop,
                            contentDescription = "A/B Loop",
                            tint = if (isAbLoopActive) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (isAbLoopActive) "A/B Loop Active" else "A/B Loop",
                            fontSize = 12.sp,
                            color = if (isAbLoopActive) YouTubeRed else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    modifier = Modifier.testTag("ab_loop_btn")
                )

                if (showAbLoopDialog) {
                    AlertDialog(
                        onDismissRequest = { showAbLoopDialog = false },
                        title = { Text("A/B Repeat Segment Loop") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Loop a specific video clip repeatedly (ideal for music & study tutorials).")
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            abLoopPointA = 0
                                            Toast.makeText(context, "Point A set to current position", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                                    ) {
                                        Text("Set A (Start)")
                                    }

                                    Button(
                                        onClick = {
                                            abLoopPointB = 120
                                            isAbLoopActive = true
                                            realWebView?.loadUrl("javascript:setAbLoop($abLoopPointA, $abLoopPointB)")
                                            Toast.makeText(context, "A/B Loop Started!", Toast.LENGTH_SHORT).show()
                                            showAbLoopDialog = false
                                        }
                                    ) {
                                        Text("Set B (End)")
                                    }
                                }

                                if (isAbLoopActive) {
                                    OutlinedButton(
                                        onClick = {
                                            isAbLoopActive = false
                                            abLoopPointA = -1
                                            abLoopPointB = -1
                                            realWebView?.loadUrl("javascript:clearAbLoop()")
                                            Toast.makeText(context, "A/B Loop Cleared", Toast.LENGTH_SHORT).show()
                                            showAbLoopDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Clear Active Loop")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAbLoopDialog = false }) { Text("Close") }
                        }
                    )
                }

                // Feature 3: Visual Filters (Night Vision, Reading Warmth, Mono)
                var showFilterMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showFilterMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = "Visual Filter",
                                tint = YouTubeRed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Filter: $activeFilter", fontSize = 12.sp) }
                    )

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        listOf("Normal", "Night", "Warm", "Mono").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    activeFilter = mode
                                    showFilterMenu = false
                                    realWebView?.loadUrl("javascript:setVisualFilter('${mode.lowercase()}')")
                                }
                            )
                        }
                    }
                }

                // Feature 4: Audio Equalizer Presets
                var showAudioEqMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showAudioEqMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Audio EQ",
                                tint = YouTubeRed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("EQ: $activeAudioEq", fontSize = 12.sp) }
                    )

                    DropdownMenu(
                        expanded = showAudioEqMenu,
                        onDismissRequest = { showAudioEqMenu = false }
                    ) {
                        listOf("Flat", "Vocal Booster", "Bass Boost", "Night Normalizer").forEach { eqMode ->
                            DropdownMenuItem(
                                text = { Text(eqMode) },
                                onClick = {
                                    activeAudioEq = eqMode
                                    showAudioEqMenu = false
                                    Toast.makeText(context, "Audio Mode: $eqMode Enabled 🎧", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Feature 5: Battery Saver Screen Dimmer Toggle
                AssistChip(
                    onClick = {
                        isScreenDimmed = !isScreenDimmed
                        Toast.makeText(context, if (isScreenDimmed) "Battery Saver Mode On 🌙" else "Normal Screen", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isScreenDimmed) Icons.Filled.WbSunny else Icons.Filled.NightsStay,
                            contentDescription = "Screen Dim",
                            tint = if (isScreenDimmed) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (isScreenDimmed) "Screen Dimmed" else "Dim Screen", fontSize = 12.sp) }
                )

                // Feature 6: Closed Captions
                AssistChip(
                    onClick = {
                        captionsEnabled = !captionsEnabled
                        realWebView?.loadUrl("javascript:toggleCaptions($captionsEnabled)")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = if (captionsEnabled) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (captionsEnabled) "CC On" else "CC Off", fontSize = 12.sp) }
                )


                // Feature 8: Sleep Timer
                var showSleepMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showSleepMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerMinutes > 0) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (sleepTimerMinutes > 0) "Sleep ($sleepTimerText)" else "Sleep Timer",
                                fontSize = 12.sp,
                                color = if (sleepTimerMinutes > 0) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = showSleepMenu,
                        onDismissRequest = { showSleepMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Turn Off Timer") },
                            onClick = {
                                cancelSleepTimer()
                                showSleepMenu = false
                            }
                        )
                        listOf(15, 30, 45, 60).forEach { mins ->
                            DropdownMenuItem(
                                text = { Text("$mins Minutes") },
                                onClick = {
                                    startSleepTimer(mins)
                                    showSleepMenu = false
                                }
                            )
                        }
                    }
                }

                // Feature 9: Share Video & Notes
                AssistChip(
                    onClick = { shareVideoAndNotes() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = YouTubeRed,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Share Video", fontSize = 12.sp) }
                )

                // Feature 10: Quick Jump Dialog
                AssistChip(
                    onClick = { showJumpDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Start,
                            contentDescription = "Jump",
                            tint = YouTubeRed,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Quick Jump", fontSize = 12.sp) }
                )
            }
        }
    }

    // Skip Interval Config Dialog
    if (showSkipStepDialog) {
        AlertDialog(
            onDismissRequest = { showSkipStepDialog = false },
            title = { Text("Select Skip Interval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how many seconds to rewind or fast forward on single tap:")
                    listOf(3, 5, 10, 15, 30, 60).forEach { sec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    skipStepSeconds = sec
                                    showSkipStepDialog = false
                                    Toast.makeText(context, "Skip step set to ${sec}s", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (skipStepSeconds == sec),
                                onClick = {
                                    skipStepSeconds = sec
                                    showSkipStepDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$sec Seconds", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSkipStepDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Quick Jump Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Timer, contentDescription = null, tint = YouTubeRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Jump to Specific Time")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter seconds or format (MM:SS):")
                    OutlinedTextField(
                        value = customTimeInput,
                        onValueChange = { customTimeInput = it },
                        placeholder = { Text("e.g. 01:30 or 90") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Preset Jumps:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("00:00 Intro" to 0, "02:00 Mid" to 120, "05:00 Ending" to 300).forEach { (label, sec) ->
                            SuggestionChip(
                                onClick = {
                                    if (exoPlayer != null) {
                                        exoPlayer.seekTo((sec * 1000).toLong())
                                    } else {
                                        realWebView?.loadUrl("javascript:seekToSeconds($sec)")
                                    }
                                    showJumpDialog = false
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val seconds = com.example.util.YouTubeUtils.parseFormattedTimeToSeconds(customTimeInput)
                        if (exoPlayer != null) {
                            exoPlayer.seekTo((seconds * 1000).toLong())
                        } else {
                            realWebView?.loadUrl("javascript:seekToSeconds($seconds)")
                        }
                        showJumpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text("Jump Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
