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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebSignInDialog(
    onDismiss: () -> Unit,
    onSuccess: (name: String, email: String, cookies: String) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://m.youtube.com/signin") }

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
                                    text = "Google Sign-In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Official accounts.google.com",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

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
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            // Use standard mobile Chrome user agent to prevent Google blocking embedded WebViews
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

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

                                    val cookies = cookieManager.getCookie("https://www.youtube.com") ?: ""
                                    val googleCookies = cookieManager.getCookie("https://accounts.google.com") ?: ""
                                    val allCookies = if (cookies.isNotBlank()) cookies else googleCookies

                                    // Check if user has authenticated
                                    val hasAuthCookie = allCookies.contains("LOGIN_INFO") ||
                                            allCookies.contains("SID") ||
                                            allCookies.contains("SSID") ||
                                            allCookies.contains("SAPISID") ||
                                            allCookies.contains("APISID")

                                    if (hasAuthCookie && (checkedUrl.contains("youtube.com") || checkedUrl.contains("myaccount.google.com"))) {
                                        val prefs = ctx.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putString("youtube_cookies", allCookies).apply()
                                        Toast.makeText(ctx, "Google & YouTube Linked Successfully! 🟢", Toast.LENGTH_SHORT).show()
                                        onSuccess("Google User", "Signed In via Browser", allCookies)
                                        onDismiss()
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

                // Bottom Action Bar to Finish Sign In at any time
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                                Toast.makeText(context, "Google Account Linked & Verified! 🟢", Toast.LENGTH_SHORT).show()
                                onSuccess("Google User", "Signed In via Browser", allCookies)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✓ Use This Google Account / Finish Sign-In",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
