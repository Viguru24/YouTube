package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    startSeconds: Int = 0,
    modifier: Modifier = Modifier,
    onPlayerReady: (WebView) -> Unit = {}
) {
    val context = LocalContext.current

    val webView = remember(videoId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: ""
                    if (isAdUrl(url)) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAdBlockScript(view)
                }
            }

            val htmlContent = buildYouTubeIFrameHtml(videoId, startSeconds)
            loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlContent, "text/html", "utf-8", null)
        }
    }

    DisposableEffect(videoId) {
        onPlayerReady(webView)
        onDispose {
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.matchParentSize()
        )

        // AdBlock Active Status Badge
        Surface(
            shape = RoundedCornerShape(bottomEnd = 8.dp),
            color = Color(0xFF1E88E5).copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .testTag("adblock_active_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "AdBlock Active",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "AdBlock Active",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Checks whether a network request URL belongs to an advertisement server or analytics tracking domain.
 */
private fun isAdUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("googleads") ||
            lower.contains("doubleclick.net") ||
            lower.contains("pagead2") ||
            lower.contains("adservice.google") ||
            lower.contains("pubads") ||
            lower.contains("/pagead/") ||
            lower.contains("/api/stats/ads") ||
            lower.contains("get_midroll_info") ||
            lower.contains("youtube.com/ptracking") ||
            lower.contains("googlesyndication.com")
}

/**
 * Injects CSS and JavaScript to suppress ad overlays and auto-skip any pre-roll/mid-roll video ads.
 */
private fun injectAdBlockScript(view: WebView?) {
    val js = """
        (function() {
            var css = `
                .video-ads, .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text,
                .ytp-ad-skip-button-slot, .ytp-ad-image-overlay, .annotation,
                .ytp-pause-overlay, .ytp-paid-content-overlay, .ytp-ad-progress-bar-container,
                ytd-action-companion-ad-renderer, ytd-display-ad-renderer,
                ytd-promoted-sparkles-web-renderer, ytd-compact-promoted-item-renderer,
                ytd-in-feed-ad-layout-renderer, .ytp-ad-overlay-image {
                    display: none !important;
                    visibility: hidden !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
            `;
            var style = document.createElement('style');
            style.type = 'text/css';
            style.appendChild(document.createTextNode(css));
            document.head.appendChild(style);

            setInterval(function() {
                var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-container');
                if (skipBtn) {
                    skipBtn.click();
                }
                var video = document.querySelector('video');
                var adShowing = document.querySelector('.ad-showing, .video-ads, .ytp-ad-text');
                if (video && adShowing) {
                    video.muted = true;
                    video.playbackRate = 16.0;
                    if (video.duration && !isNaN(video.duration)) {
                        video.currentTime = video.duration - 0.1;
                    }
                }
            }, 300);
        })();
    """.trimIndent()

    view?.evaluateJavascript(js, null)
}

private fun buildYouTubeIFrameHtml(videoId: String, startSeconds: Int): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
            .video-container { position: relative; width: 100%; height: 100%; }
            iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
            
            /* Built-in AdBlock Styles */
            .video-ads, .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text,
            .ytp-ad-skip-button-slot, .ytp-ad-image-overlay, .annotation,
            .ytp-pause-overlay, .ytp-paid-content-overlay, ytd-action-companion-ad-renderer {
              display: none !important;
              visibility: hidden !important;
            }
          </style>
        </head>
        <body>
          <div class="video-container">
            <iframe id="ytplayer"
              src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&fs=1&rel=0&iv_load_policy=3&modestbranding=1&controls=1&showinfo=0&start=$startSeconds"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowfullscreen>
            </iframe>
          </div>
          <script>
            function seekToSeconds(seconds) {
              var iframe = document.getElementById('ytplayer');
              if (iframe && iframe.contentWindow) {
                iframe.contentWindow.postMessage('{"event":"command","func":"seekTo","args":[' + seconds + ',true]}', '*');
                iframe.contentWindow.postMessage('{"event":"command","func":"playVideo","args":[]}', '*');
              }
            }

            // Auto-Skip Ad listener inside iframe parent
            setInterval(function() {
              var iframe = document.getElementById('ytplayer');
              if (iframe && iframe.contentWindow) {
                try {
                  var doc = iframe.contentDocument || iframe.contentWindow.document;
                  if (doc) {
                    var skipBtn = doc.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
                    if (skipBtn) skipBtn.click();
                  }
                } catch(e) {}
              }
            }, 300);
          </script>
        </body>
        </html>
    """.trimIndent()
}
