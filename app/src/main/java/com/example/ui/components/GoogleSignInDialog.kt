package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GoogleAccount
import com.example.ui.theme.YouTubeRed

@Composable
fun GoogleSignInDialog(
    account: GoogleAccount,
    onSignIn: (name: String, email: String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onSyncPlaylists: () -> Unit = {}
) {
    val context = LocalContext.current
    var nameInput by remember(account) { mutableStateOf(if (account.name.isNotBlank()) account.name else "Louis de Souza") }
    var emailInput by remember(account) { mutableStateOf(if (account.email.isNotBlank()) account.email else "louisdesouza@gmail.com") }
    var isSyncing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4285F4).copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Color(0xFF4285F4)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (account.isSignedIn) "Google Account" else "Google Sign-In",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% Native Playlists & Data Sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (account.isSignedIn) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = YouTubeRed,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = account.avatarInitials,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = account.email,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF4CAF50), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sync Active",
                                        fontSize = 11.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "ACCOUNT SYNC OPTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = {
                            isSyncing = true
                            onSyncPlaylists()
                            Toast.makeText(context, "Playlists & Subscriptions Synced 🔄", Toast.LENGTH_SHORT).show()
                            isSyncing = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = "Sync",
                                    tint = Color(0xFF4285F4)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Sync Playlists & Channels", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Fetch saved playlists & subscriptions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onSignOut()
                            onDismiss()
                            Toast.makeText(context, "Signed Out Successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_out_btn")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Sign in to sync your YouTube playlists, subscriptions, and custom recommendations across your devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("google_name_input")
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google Account Email") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Email, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("google_email_input")
                    )

                    Button(
                        onClick = {
                            val name = if (nameInput.isNotBlank()) nameInput else "Louis de Souza"
                            val email = if (emailInput.isNotBlank()) emailInput else "louisdesouza@gmail.com"
                            onSignIn(name, email)
                            onDismiss()
                            Toast.makeText(context, "Signed In as $name ($email) 🟢", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_submit_btn")
                    ) {
                        Text("G", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Google Account", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
