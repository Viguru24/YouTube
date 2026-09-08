package com.example.ui.components.player

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldStar

@Composable
fun PlayerSleepTimerDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    sleepTimerMinutes: Int,
    sleepTimerEndOfVideo: Boolean,
    isSleepTimerActive: Boolean,
    onStartTimer: (minutes: Int, endOfVideo: Boolean) -> Unit,
    onTurnOffTimer: () -> Unit
) {
    if (!visible) return

    var tempMinutes by remember(visible, sleepTimerMinutes) {
        mutableFloatStateOf(if (sleepTimerMinutes in 5..60) sleepTimerMinutes.toFloat() else 30f)
    }
    var tempEndOfVideo by remember(visible, sleepTimerEndOfVideo) {
        mutableStateOf(sleepTimerEndOfVideo)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismissRequest() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF18181A).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(270.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Title + Close X
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bedtime,
                            contentDescription = "Sleep",
                            tint = GoldStar,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Sleep Timer",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val chosenMins = ((tempMinutes / 5f).toInt() * 5).coerceIn(5, 60)
                val readoutText = if (tempEndOfVideo) "End of Video" else "${chosenMins} min"

                Text(
                    text = readoutText,
                    color = GoldStar,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // End of Video Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tempEndOfVideo = !tempEndOfVideo }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = tempEndOfVideo,
                        onCheckedChange = { tempEndOfVideo = it },
                        colors = CheckboxDefaults.colors(checkedColor = GoldStar, checkmarkColor = Color.Black)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop at End of Video", color = Color.White, fontSize = 12.sp)
                }

                // Slider (disabled if End of Video selected)
                Slider(
                    value = tempMinutes,
                    onValueChange = {
                        tempMinutes = it
                        tempEndOfVideo = false
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    enabled = !tempEndOfVideo,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldStar,
                        activeTrackColor = GoldStar,
                        inactiveTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Presets: 15m, 30m, 45m, 60m
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { p ->
                        val sel = !tempEndOfVideo && chosenMins == p
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) GoldStar else Color(0xFF2A2A2C),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    tempMinutes = p.toFloat()
                                    tempEndOfVideo = false
                                }
                        ) {
                            Text(
                                text = if (p == 60) "1h" else "${p}m",
                                color = if (sel) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSleepTimerActive) {
                        Button(
                            onClick = {
                                onTurnOffTimer()
                                Toast.makeText(context, "Timer Off ⏹️", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333336)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Text("Turn Off", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            val finalMins = ((tempMinutes / 5f).toInt() * 5).coerceIn(5, 60)
                            onStartTimer(finalMins, tempEndOfVideo)
                            if (tempEndOfVideo) {
                                Toast.makeText(context, "🌙 Sleep: End of Video", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "🌙 Sleep set for $finalMins min", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldStar),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            text = if (isSleepTimerActive) "Reset (${readoutText})" else "Start (${readoutText})",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
