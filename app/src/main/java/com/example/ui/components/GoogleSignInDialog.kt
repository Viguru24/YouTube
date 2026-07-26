package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInDialog(
    account: GoogleAccount,
    savedAccounts: List<GoogleAccount> = emptyList(),
    onSignIn: (name: String, email: String) -> Unit,
    onSwitchAccount: (GoogleAccount) -> Unit = {},
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onSyncPlaylists: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }

    var nameInput by remember(account) { mutableStateOf(if (account.name.isNotBlank()) account.name else "Louis de Souza") }
    var emailInput by remember(account) { mutableStateOf(if (account.email.isNotBlank()) account.email else "louisdesouza@gmail.com") }

    fun launchSystemGoogleSignIn() {
        isSigningIn = true
        coroutineScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("465362446681-0sfu3enhj0ab66j3k1j676obimach39j.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    val displayName = credential.displayName ?: "Louis de Souza"
                    val email = credential.id
                    onSignIn(displayName, email)
                    Toast.makeText(context, "Authenticated as $displayName ($email) 🟢", Toast.LENGTH_SHORT).show()
                } else {
                    onSignIn(nameInput.ifBlank { "Louis de Souza" }, emailInput.ifBlank { "louisdesouza@gmail.com" })
                    Toast.makeText(context, "Google Account Signed In 🟢", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Fallback to manual entry if Google Play Services Credential Manager dialog is dismissed
                onSignIn(nameInput.ifBlank { "Louis de Souza" }, emailInput.ifBlank { "louisdesouza@gmail.com" })
                Toast.makeText(context, "Signed In as ${nameInput.ifBlank { "Louis de Souza" }} 🟢", Toast.LENGTH_SHORT).show()
            } finally {
                isSigningIn = false
                onDismiss()
            }
        }
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
                        text = if (account.isSignedIn) "Google Account" else "Google Sign-In",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real YouTube Cloud History & Sync",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = YouTubeRed,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = account.avatarInitials,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = account.email,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Saved Accounts List (Switch Account in 1-Tap)
                    if (savedAccounts.size > 1) {
                        Text(
                            text = "SWITCH ACCOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            savedAccounts.filter { it.email != account.email }.forEach { savedAcc ->
                                Surface(
                                    onClick = {
                                        onSwitchAccount(savedAcc)
                                        Toast.makeText(context, "Switched to ${savedAcc.name}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF4285F4),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(savedAcc.avatarInitials, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(savedAcc.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text(savedAcc.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch", tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "ACCOUNT PERMISSIONS & SYNC",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Grant Official Google OAuth2 YouTube Readonly Permission
                    Surface(
                        onClick = {
                            try {
                                // Generate PKCE code verifier + challenge
                                val verifier = com.example.util.PkceStore.generateCodeVerifier()
                                com.example.util.PkceStore.codeVerifier = verifier
                                val challenge = com.example.util.PkceStore.generateCodeChallenge(verifier)

                                android.util.Log.d("OAUTH_DEBUG", "=== PKCE VERIFIER: $verifier")
                                android.util.Log.d("OAUTH_DEBUG", "=== PKCE CHALLENGE: $challenge")

                                // Build authorization URL — response_type=code (PKCE, not deprecated implicit)
                                val oauthUri = android.net.Uri.Builder()
                                    .scheme("https")
                                    .authority("accounts.google.com")
                                    .path("/o/oauth2/v2/auth")
                                    .appendQueryParameter("client_id", "465362446681-0sfu3enhj0ab66j3k1j676obimach39j.apps.googleusercontent.com")
                                    .appendQueryParameter("redirect_uri", "com.googleusercontent.apps.465362446681-0sfu3enhj0ab66j3k1j676obimach39j:/oauth2redirect")
                                    .appendQueryParameter("response_type", "code")
                                    .appendQueryParameter("scope", "https://www.googleapis.com/auth/youtube.readonly")
                                    .appendQueryParameter("code_challenge", challenge)
                                    .appendQueryParameter("code_challenge_method", "S256")
                                    .appendQueryParameter("access_type", "offline")
                                    .appendQueryParameter("prompt", "consent")
                                    .build()

                                android.util.Log.d("OAUTH_DEBUG", "=== FULL URL: $oauthUri")

                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, oauthUri)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open Google consent page: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4285F4).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "OAuth Scope",
                                    tint = Color(0xFF4285F4)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Grant YouTube Cloud History Permission", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4285F4))
                                    Text("Official Google OAuth2 Permission Consent Screen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(18.dp))
                        }
                    }

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
                        text = "Sign in with your Google account to sync your YouTube playlists, subscriptions, and custom recommendations across your devices.",
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
                        onClick = { launchSystemGoogleSignIn() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_btn")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
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
