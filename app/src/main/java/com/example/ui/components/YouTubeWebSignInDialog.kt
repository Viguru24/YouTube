package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
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
import coil.compose.AsyncImage
import com.example.ui.theme.YouTubeRed
import org.json.JSONObject

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
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://m.youtube.com/signin") }

    var detectedName by remember { mutableStateOf(initialName) }
    var detectedEmail by remember { mutableStateOf(initialEmail) }
    var detectedAvatarUrl by remember { mutableStateOf("") }
    var hasAuthSession by remember { mutableStateOf(false) }
    var showPasteCookiesDialog by remember { mutableStateOf(false) }
    var cookiesInput by remember { mutableStateOf("") }

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
        return if (!isGeneric) trimmed else "Google User"
    }

    fun extractAccountDetails(view: WebView) {
        val extractJs = """
            (function() {
                var r = { name: '', email: '', avatarUrl: '' };
                try {
                    var els = document.querySelectorAll('[aria-label], [title], [data-email], [data-identifier]');
                    for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        var dEmail = el.getAttribute('data-email') || el.getAttribute('data-identifier') || '';
                        if (dEmail && dEmail.indexOf('@') !== -1) {
                            r.email = dEmail.trim();
                            r.name = el.getAttribute('data-name') || '';
                            break;
                        }
                        var lbl = el.getAttribute('aria-label') || el.getAttribute('title') || '';
                        var m = lbl.match(/(?:Google Account|Compte Google|Cuenta de Google|Account):\s*([^\n(]+?)(?:\s*\n|\s*\()\s*([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/i);
                        if (m) {
                            r.name = m[1].trim();
                            r.email = m[2].trim();
                            break;
                        }
                        var mEm = lbl.match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/);
                        if (mEm && !r.email) {
                            r.email = mEm[1].trim();
                        }
                    }

                    if (!r.name) {
                        var ch = document.querySelector('#channel-name, .account-name, ytm-account-item-renderer #channel-name, #channel-title, yt-formatted-string#text.ytd-channel-name');
                        if (ch && ch.innerText) r.name = ch.innerText.trim();
                    }

                    var img = document.querySelector('img[src*="googleusercontent.com"], img[src*="yt3.ggpht.com"], ytm-profile-icon img, #avatar-btn img');
                    if (img) {
                        r.avatarUrl = img.src || '';
                        if (!r.name && img.alt && !img.alt.includes('Avatar') && !img.alt.includes('profile')) {
                            r.name = img.alt.trim();
                        }
                    }
                } catch(e) {}
                return JSON.stringify(r);
            })()
        """.trimIndent()

        view.evaluateJavascript(extractJs) { jsonStr ->
            try {
                if (!jsonStr.isNullOrBlank() && jsonStr != "null" && jsonStr != "\"\"") {
                    val unescaped = if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                        org.json.JSONTokener(jsonStr).nextValue().toString()
                    } else jsonStr
                    val obj = JSONObject(unescaped)
                    val jsName = obj.optString("name", "").trim()
                    val jsEmail = obj.optString("email", "").trim()
                    val jsAvatar = obj.optString("avatarUrl", "").trim()

                    if (jsName.isNotBlank() && !jsName.equals("Google User", ignoreCase = true)) {
                        detectedName = jsName
                    }
                    if (jsEmail.isNotBlank()) {
                        detectedEmail = jsEmail
                    }
                    if (jsAvatar.isNotBlank()) {
                        detectedAvatarUrl = jsAvatar
                    }
                }
            } catch (e: Exception) {}
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
                                    text = "Google & YouTube Sign-In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (hasAuthSession) "🟢 Verified Session Ready" else "Official Google Authentication",
                                    fontSize = 11.sp,
                                    color = if (hasAuthSession) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
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
                        IconButton(onClick = { showPasteCookiesDialog = true }) {
                            Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = "Paste Cookies")
                        }
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.ifBlank { "https://m.youtube.com/signin" }))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open external browser", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(imageVector = Icons.Filled.OpenInBrowser, contentDescription = "Open in Browser")
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Reload")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Dialog for manual Cookie import fallback
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
                                    text = "If Google restricts in-app sign-in, you can copy your cookies from your mobile/desktop browser (e.g., via DevTools or cookie exporter) and paste them here:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = cookiesInput,
                                    onValueChange = { cookiesInput = it },
                                    label = { Text("Cookie String") },
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
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.setAcceptCookie(true)
                                        raw.split(";").forEach { part ->
                                            val cookie = part.trim()
                                            if (cookie.isNotEmpty()) {
                                                cookieManager.setCookie("https://www.youtube.com", cookie)
                                                cookieManager.setCookie("https://accounts.google.com", cookie)
                                            }
                                        }
                                        cookieManager.flush()

                                        val prefs = context.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putString("youtube_cookies", raw).apply()

                                        hasAuthSession = raw.contains("LOGIN_INFO") || raw.contains("SID") || raw.contains("SAPISID")
                                        webViewRef?.loadUrl("https://m.youtube.com")

                                        Toast.makeText(context, "Cookies imported successfully! 🟢", Toast.LENGTH_SHORT).show()
                                        showPasteCookiesDialog = false
                                    } else {
                                        Toast.makeText(context, "Please enter cookie data", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                            ) {
                                Text("Apply Cookies")
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

                            // 1. Strip X-Requested-With header to prevent Google from detecting embedded Android app
                            try {
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                }
                            } catch (e: Throwable) {}

                            // 2. Configure robust webview settings for modern authentication
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

                                // 3. Clean Mobile Chrome User-Agent: strip '; wv' and 'Version/X.X'
                                // Using a desktop Windows User-Agent on Android fails Google's platform fingerprint checks!
                                val defaultUa = userAgentString
                                val cleanUa = if (defaultUa.contains("; wv") || defaultUa.contains("Version/")) {
                                    defaultUa.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                                } else if (defaultUa.contains("Windows NT")) {
                                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                                } else {
                                    defaultUa
                                }
                                userAgentString = cleanUa
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            val stealthJs = """
                                (function() {
                                    try {
                                        Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true });
                                        if (!window.chrome) {
                                            window.chrome = {
                                                app: { isInstalled: false },
                                                loadTimes: function() {},
                                                csi: function() {}
                                            };
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent()

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    isLoading = newProgress < 100
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                    view?.evaluateJavascript(stealthJs, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    view?.evaluateJavascript(stealthJs, null)
                                    val checkedUrl = url ?: return
                                    currentUrl = checkedUrl

                                    val cookies = cookieManager.getCookie("https://www.youtube.com") ?: ""
                                    val googleCookies = cookieManager.getCookie("https://accounts.google.com") ?: ""
                                    val allCookies = if (cookies.isNotBlank()) cookies else googleCookies

                                    // Check if user has authenticated
                                    val hasAuth = allCookies.contains("LOGIN_INFO") ||
                                            allCookies.contains("SID") ||
                                            allCookies.contains("SSID") ||
                                            allCookies.contains("SAPISID") ||
                                            allCookies.contains("APISID")

                                    hasAuthSession = hasAuth

                                    if (hasAuth) {
                                        view?.let { extractAccountDetails(it) }

                                        // Persist cookies immediately
                                        val prefs = ctx.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putString("youtube_cookies", allCookies).apply()
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

                // Bottom Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val finalDisplayName = deriveName(detectedName, detectedEmail)

                        AnimatedVisibility(
                            visible = hasAuthSession || detectedEmail.isNotBlank(),
                            enter = fadeIn() + slideInVertically()
                        ) {
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
                                if (detectedAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = detectedAvatarUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
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
                                    text = "🟢 Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("https://www.youtube.com") ?: ""
                                val googleCookies = cookieManager.getCookie("https://accounts.google.com") ?: ""
                                val allCookies = if (cookies.isNotBlank()) cookies else googleCookies

                                val prefs = context.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE)
                                if (allCookies.isNotBlank()) {
                                    prefs.edit().putString("youtube_cookies", allCookies).apply()
                                }

                                val resolvedName = deriveName(detectedName, detectedEmail)
                                val resolvedEmail = if (detectedEmail.isNotBlank()) detectedEmail else "google.user@vixz.app"

                                Toast.makeText(context, "Welcome, $resolvedName! 🟢", Toast.LENGTH_SHORT).show()
                                onSuccess(resolvedName, resolvedEmail, allCookies, detectedAvatarUrl)
                                onDismiss()
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
                                text = if (hasAuthSession && detectedName.isNotBlank()) "✓ Use Account: $finalDisplayName" else "✓ Use This Google Account / Finish Sign-In",
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
    }
}
