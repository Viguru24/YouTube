package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import coil.compose.AsyncImage
import com.example.data.remote.GoogleAccountSyncService
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebSignInDialog(
    initialName: String = "",
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onSuccess: (name: String, email: String, cookies: String, avatarUrl: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isAutoCompleting by remember { mutableStateOf(false) }
    var autoCompletionMessage by remember { mutableStateOf("Connecting to your YouTube profile...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val initialHasAuth = remember {
        val cookies = com.example.util.CookieHelper.getAggregatedCookies()
        com.example.util.CookieHelper.hasAuthCookies(cookies)
    }

    val signInEntryUrl = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"
    var currentUrl by remember { mutableStateOf(if (initialHasAuth) "https://m.youtube.com/" else signInEntryUrl) }

    var detectedName by remember { mutableStateOf(initialName) }
    var detectedEmail by remember { mutableStateOf(initialEmail) }
    var detectedAvatarUrl by remember { mutableStateOf("") }
    var hasAuthSession by remember { mutableStateOf(initialHasAuth) }
    var showPasteCookiesDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var cookiesInput by remember { mutableStateOf("") }
    var hasFinalized by remember { mutableStateOf(false) }

    fun deriveName(rawName: String, rawEmail: String): String {
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
        return if (!isGeneric) trimmed else "YouTube User"
    }

    fun completeSignInWithProfile(cookies: String) {
        if (hasFinalized) return
        hasFinalized = true
        isAutoCompleting = true
        autoCompletionMessage = "Authentication confirmed! Setting up profile..."

        coroutineScope.launch {
            // Persist session tokens immediately
            com.example.util.CookieHelper.syncAndPersistCookies(context)
            com.example.data.remote.NPDownloader.savedCookies = cookies

            // Attempt to query real profile metadata via Innertube
            var finalName = detectedName
            var finalEmail = detectedEmail
            var finalAvatar = detectedAvatarUrl

            try {
                val profile = GoogleAccountSyncService.fetchUserProfileFromInnertube(cookies)
                if (profile != null) {
                    if (profile.name.isNotBlank()) finalName = profile.name
                    if (profile.email.isNotBlank()) finalEmail = profile.email
                    if (profile.avatarUrl.isNotBlank()) finalAvatar = profile.avatarUrl
                }
            } catch (e: Exception) { }

            val cleanName = deriveName(finalName, finalEmail).ifBlank { "YouTube User" }
            val cleanEmail = if (finalEmail.isNotBlank()) finalEmail else "google.user@vixz.app"

            autoCompletionMessage = "Welcome, $cleanName! 🟢"
            delay(500L)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Welcome, $cleanName! 🟢 Subscriptions are syncing.", Toast.LENGTH_SHORT).show()
                onSuccess(cleanName, cleanEmail, cookies, finalAvatar)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Secure",
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Sign In to YouTube",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (hasAuthSession) "🟢 Verified Session Active" else "Secure Google Authentication",
                                        fontSize = 11.sp,
                                        color = if (hasAuthSession) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Reload")
                            }
                            Box {
                                IconButton(onClick = { showOptionsMenu = true }) {
                                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("🧹 Clear Cookies & Reset") },
                                        onClick = {
                                            showOptionsMenu = false
                                            val cm = CookieManager.getInstance()
                                            cm.removeAllCookies {
                                                cm.flush()
                                                val prefs = context.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().remove("youtube_cookies").apply()
                                                com.example.data.remote.NPDownloader.savedCookies = ""
                                                webViewRef?.clearCache(true)
                                                webViewRef?.clearHistory()
                                                hasAuthSession = false
                                                hasFinalized = false
                                                Toast.makeText(context, "🧹 Cookies cleared! Reloading sign-in...", Toast.LENGTH_SHORT).show()
                                                currentUrl = signInEntryUrl
                                                webViewRef?.loadUrl(signInEntryUrl)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("📋 Paste Cookies Manually") },
                                        onClick = {
                                            showOptionsMenu = false
                                            showPasteCookiesDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🌐 Open in External Browser") },
                                        onClick = {
                                            showOptionsMenu = false
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.ifBlank { "https://m.youtube.com" }))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Manual Cookie import dialog
                    if (showPasteCookiesDialog) {
                        AlertDialog(
                            onDismissRequest = { showPasteCookiesDialog = false },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = null, tint = YouTubeRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Paste YouTube Cookies", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Paste your browser cookies (LOGIN_INFO, SID, SAPISID) to authenticate directly:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = cookiesInput,
                                        onValueChange = { cookiesInput = it },
                                        label = { Text("Cookie Header String") },
                                        placeholder = { Text("LOGIN_INFO=...; SID=...; SAPISID=...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val raw = cookiesInput.trim()
                                        if (raw.isNotBlank()) {
                                            com.example.util.CookieHelper.injectCookies(raw)
                                            com.example.util.CookieHelper.syncAndPersistCookies(context)
                                            com.example.data.remote.NPDownloader.savedCookies = raw
                                            completeSignInWithProfile(raw)
                                            showPasteCookiesDialog = false
                                        } else {
                                            Toast.makeText(context, "Please enter cookie string", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                                ) {
                                    Text("Apply & Sign In")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPasteCookiesDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = YouTubeRed
                        )
                    }

                    // Embedded Web Browser
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this

                                // Strip X-Requested-With header to bypass Google embedded app detector
                                try {
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                    }
                                } catch (e: Throwable) {}

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(false)
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                    // Clean Mobile Safari User-Agent: Universally accepted by Google Accounts without webview restriction
                                    userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        isLoading = newProgress < 100
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        url?.let { currentUrl = it }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        val checkedUrl = url ?: return
                                        currentUrl = checkedUrl

                                        val allCookies = com.example.util.CookieHelper.getAggregatedCookies()
                                        val hasAuth = com.example.util.CookieHelper.hasAuthCookies(allCookies)
                                        hasAuthSession = hasAuth

                                        val isReturnedToYouTube = (checkedUrl.contains("youtube.com") || checkedUrl.contains("m.youtube.com")) &&
                                                !checkedUrl.contains("accounts.google.com")

                                        // Auto-detect successful Google sign-in and finalize without requiring user to hunt for buttons
                                        if (hasAuth && isReturnedToYouTube && !hasFinalized) {
                                            completeSignInWithProfile(allCookies)
                                        }
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        val checkedUrl = url ?: return
                                        currentUrl = checkedUrl

                                        val allCookies = com.example.util.CookieHelper.getAggregatedCookies()
                                        val hasAuth = com.example.util.CookieHelper.hasAuthCookies(allCookies)
                                        val isReturnedToYouTube = (checkedUrl.contains("youtube.com") || checkedUrl.contains("m.youtube.com")) &&
                                                !checkedUrl.contains("accounts.google.com")

                                        if (hasAuth && isReturnedToYouTube && !hasFinalized) {
                                            completeSignInWithProfile(allCookies)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }

                                loadUrl(currentUrl)
                            }
                        },
                        update = { view ->
                            webViewRef = view
                        }
                    )

                    // Bottom Action Bar - Displayed if user desires manual confirmation
                    AnimatedVisibility(
                        visible = hasAuthSession && !isAutoCompleting,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val finalDisplayName = deriveName(detectedName, detectedEmail)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = YouTubeRed,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = finalDisplayName.take(2).uppercase().ifEmpty { "U" },
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = finalDisplayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (detectedEmail.isNotBlank()) detectedEmail else "Google Account Authenticated",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = "🟢 Verified",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }

                                Button(
                                    onClick = {
                                        val allCookies = com.example.util.CookieHelper.getAggregatedCookies()
                                        completeSignInWithProfile(allCookies)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✓ Complete Sign-In as $finalDisplayName",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Fullscreen Auto-Completing Glassmorphic Success Overlay
                AnimatedVisibility(
                    visible = isAutoCompleting,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(0.85f)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = YouTubeRed,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Signing You In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = autoCompletionMessage,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
