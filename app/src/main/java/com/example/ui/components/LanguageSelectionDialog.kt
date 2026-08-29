package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YouTubeRed
import com.example.util.AppLanguage
import com.example.util.LanguageManager
import com.example.util.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val currentLang by LanguageManager.currentLanguage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌐", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(strings.appLanguageTitle, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.appLanguageSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = lang == currentLang
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                LanguageManager.setLanguage(context, lang)
                                onDismiss()
                            },
                            label = {
                                Text(
                                    text = lang.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YouTubeRed.copy(alpha = 0.22f),
                                selectedLabelColor = YouTubeRed
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
            ) {
                Text(strings.closeBtn)
            }
        }
    )
}
