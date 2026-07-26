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
    webView: WebView?,
    videoTitle: String = "YouTube Video",
    videoId: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(true) }
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
                webView?.loadUrl("javascript:pauseVideo()")
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
                "Watching '$videoTitle' on Ad-Free YouTube Deck!\nWatch link: https://youtu.be/$videoId"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
    }

    DisposableEffect(Unit) {
        onDispose {
            countDownTimer?.cancel()
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
                        webView?.loadUrl("javascript:seekRelative(-$skipStepSeconds)")
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

                // Frame Step -1 Button
                IconButton(
                    onClick = {
                        webView?.loadUrl("javascript:stepFrame(-0.033)")
                        Toast.makeText(context, "-1 Frame", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("step_frame_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FirstPage,
                        contentDescription = "-1 Frame",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play / Pause Large Central Button
                FilledIconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            webView?.loadUrl("javascript:playVideo()")
                        } else {
                            webView?.loadUrl("javascript:pauseVideo()")
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = YouTubeRed),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("toggle_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Frame Step +1 Button
                IconButton(
                    onClick = {
                        webView?.loadUrl("javascript:stepFrame(0.033)")
                        Toast.makeText(context, "+1 Frame", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("step_frame_forward_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.LastPage,
                        contentDescription = "+1 Frame",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // +Skip Forward Button
                OutlinedIconButton(
                    onClick = {
                        webView?.loadUrl("javascript:seekRelative($skipStepSeconds)")
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
                        webView?.loadUrl("javascript:toggleMute($isMuted)")
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

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Row 2: Playback Speed Control & Fine Stepper (+0.1x / -0.1x)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Fine speed down button (-0.1x)
                IconButton(
                    onClick = {
                        selectedSpeed = (selectedSpeed - 0.1f).coerceAtLeast(0.25f)
                        val formatted = String.format("%.2f", selectedSpeed)
                        webView?.loadUrl("javascript:setPlaybackRate($formatted)")
                        Toast.makeText(context, "Speed: ${formatted}x", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Remove, contentDescription = "Speed Down", modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${String.format("%.2f", selectedSpeed)}x",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = YouTubeRed,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Fine speed up button (+0.1x)
                IconButton(
                    onClick = {
                        selectedSpeed = (selectedSpeed + 0.1f).coerceAtMost(3.0f)
                        val formatted = String.format("%.2f", selectedSpeed)
                        webView?.loadUrl("javascript:setPlaybackRate($formatted)")
                        Toast.makeText(context, "Speed: ${formatted}x", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Speed Up", modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    speedOptions.forEach { speed ->
                        val isSelected = (selectedSpeed == speed)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSpeed = speed
                                webView?.loadUrl("javascript:setPlaybackRate($speed)")
                            },
                            label = {
                                Text(
                                    text = if (speed == 1.0f) "1x" else "${speed}x",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YouTubeRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Row 3: Horizontal Scroll Deck for All Smart Tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                                            webView?.loadUrl("javascript:setAbLoop($abLoopPointA, $abLoopPointB)")
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
                                            webView?.loadUrl("javascript:clearAbLoop()")
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
                                    webView?.loadUrl("javascript:setVisualFilter('${mode.lowercase()}')")
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
                        webView?.loadUrl("javascript:toggleCaptions($captionsEnabled)")
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

                // Feature 7: Quality Selector
                var showQualityMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showQualityMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Hd,
                                contentDescription = "Quality",
                                tint = YouTubeRed,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(selectedQuality, fontSize = 12.sp) }
                    )

                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        qualityOptions.forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(quality) },
                                onClick = {
                                    selectedQuality = quality
                                    showQualityMenu = false
                                    webView?.loadUrl("javascript:setPlaybackQuality('${quality.lowercase()}')")
                                }
                            )
                        }
                    }
                }

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
                                    webView?.loadUrl("javascript:seekToSeconds($sec)")
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
                        webView?.loadUrl("javascript:seekToSeconds($seconds)")
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
