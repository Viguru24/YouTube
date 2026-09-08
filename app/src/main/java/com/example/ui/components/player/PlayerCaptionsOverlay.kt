package com.example.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerCaptionsOverlay(
    captionsEnabled: Boolean,
    activeCaptionText: String?,
    isInPipMode: Boolean,
    shouldShowControls: Boolean,
    modifier: Modifier = Modifier
) {
    if (captionsEnabled && !activeCaptionText.isNullOrBlank() && !isInPipMode) {
        Box(
            modifier = modifier
                .padding(
                    bottom = if (shouldShowControls) 50.dp else 16.dp,
                    start = 20.dp,
                    end = 20.dp
                )
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = activeCaptionText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
