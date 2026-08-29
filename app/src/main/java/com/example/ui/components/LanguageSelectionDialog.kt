package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YouTubeRed
import com.example.util.AppLanguage
import com.example.util.LanguageManager
import com.example.util.LocalAppStrings

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()
    val languages = remember { AppLanguage.values().toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🌐", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = strings.appLanguageTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.appLanguageSub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(languages, key = { it.name }) { lang ->
                    val isSelected = lang == currentLang
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) YouTubeRed.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) YouTubeRed else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                LanguageManager.setLanguage(context, lang)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lang.flagEmoji,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lang.englishName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(strings.closeBtn, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
