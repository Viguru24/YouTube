package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GoogleAccount
import com.example.ui.theme.YouTubeRed

@Composable
fun GoogleSignInDialog(
    account: GoogleAccount,
    savedAccounts: List<GoogleAccount> = emptyList(),
    onSignIn: (name: String, email: String, avatarUrl: String) -> Unit,
    onSwitchAccount: (GoogleAccount) -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onSyncPlaylists: () -> Unit = {}
) {
    val context = LocalContext.current
    var showWebSignInDialog by remember { mutableStateOf(false) }

    fun deriveCleanName(rawName: String, rawEmail: String): String {
        val trimmed = rawName.trim()
        val isGeneric = trimmed.isBlank() ||
                trimmed.equals("Guest User", ignoreCase = true) ||
                trimmed.equals("Google User", ignoreCase = true) ||
                trimmed.equals("Local User", ignoreCase = true) ||
                trimmed.equals("Guest", ignoreCase = true)

        if (!isGeneric) return trimmed

        if (rawEmail.isNotBlank() && rawEmail.contains("@")) {
            val user = rawEmail.substringBefore("@")
                .replace(Regex("\\d+$"), "")
                .replace(Regex("[._\\-+]+"), " ")
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            val words = user.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isNotEmpty()) {
                return words.joinToString(" ") { w ->
                    w.lowercase().replaceFirstChar { if (it.isJavaIdentifierStart()) it.titlecase() else it.toString() }
                }
            }
        }
        return ""
    }

    val initialName = remember(account) {
        deriveCleanName(account.name, account.email)
    }

    var nameInput by remember(account) { mutableStateOf(initialName) }
    var emailInput by remember(account) { mutableStateOf(if (account.email != "local@vixz.app") account.email else "") }

    if (showWebSignInDialog) {
        YouTubeWebSignInDialog(
            initialName = nameInput,
            initialEmail = emailInput,
            onDismiss = { showWebSignInDialog = false },
            onSuccess = { name, email, cookies, avatarUrl ->
                com.example.data.remote.NPDownloader.savedCookies = cookies
                nameInput = name
                emailInput = email
                onSignIn(name, email, avatarUrl)
                showWebSignInDialog = false
                onDismiss()
            }
        )
    }

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
                        text = if (account.isSignedIn) "User Profile & Accounts" else "Sign In to YouTube / Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Vixz YouTube Player • Profile & Cloud Sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Active Profile Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (account.isSignedIn)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (account.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = if (account.isSignedIn) YouTubeRed else Color.Gray,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (account.isSignedIn && account.avatarInitials.isNotBlank()) {
                                        Text(
                                            text = account.avatarInitials,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = "Guest",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (account.isSignedIn) account.name.ifBlank { "Signed-In User" } else "Guest Profile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (account.isSignedIn) account.email.ifBlank { "Connected" } else "Not signed in",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (account.isSignedIn) "🟢 Active Account" else "⚪ Local Guest Mode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (account.isSignedIn) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
                }

                // Saved Accounts Multi-Account Switcher
                val otherAccounts = savedAccounts.filter { it.email.isNotBlank() && it.email != "local@vixz.app" }
                if (otherAccounts.isNotEmpty()) {
                    Text(
                        text = "SWITCH ACCOUNT (${otherAccounts.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (acc in otherAccounts) {
                            val isActive = acc.email.equals(account.email, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isActive) {
                                            onSwitchAccount(acc)
                                            Toast.makeText(context, "Switched to ${acc.name} 🟢", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isActive) YouTubeRed else Color(0xFF4285F4),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = acc.avatarInitials.ifBlank { "U" },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = acc.email,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isActive) {
                                        Text(
                                            text = "Active",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { onRemoveAccount(acc.email) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove Account",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Web Browser Sign-In Button (Primary YouTube Link)
                Button(
                    onClick = { showWebSignInDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("google_web_sign_in_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = "Sign-In via YouTube",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🌐 Sign In via YouTube / Google Web",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                HorizontalDivider()

                Text(
                    text = "OR ENTER PROFILE MANUALLY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { newEmail ->
                        emailInput = newEmail
                        if (nameInput.isBlank() || nameInput == initialName) {
                            val derived = deriveCleanName("", newEmail)
                            if (derived.isNotBlank()) nameInput = derived
                        }
                    },
                    label = { Text("Email Address") },
                    placeholder = { Text("your.name@gmail.com") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Email, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_email_input")
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Joe Black") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_name_input")
                )

                OutlinedButton(
                    onClick = {
                        val finalEmail = emailInput.trim()
                        val finalName = deriveCleanName(nameInput, finalEmail).ifBlank {
                            if (finalEmail.isNotBlank()) finalEmail.substringBefore("@") else "User"
                        }
                        onSignIn(finalName, finalEmail, account.avatarUrl)
                        Toast.makeText(context, "Profile Saved as $finalName 🟢", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_local_profile_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "💾 Save Custom Profile",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                if (account.isSignedIn) {
                    TextButton(
                        onClick = {
                            onSignOut()
                            Toast.makeText(context, "Signed out / Guest profile active", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Sign Out / Switch to Guest Mode", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
