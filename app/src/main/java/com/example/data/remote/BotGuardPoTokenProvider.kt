package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Generates genuine YouTube BotGuard Proof-of-Origin (poToken) credentials using an offscreen
 * network-blocked Android WebView to execute the BotGuard VM challenge returned by /api/jnn/v1.
 */
object BotGuardPoTokenProvider : PoTokenProvider {

    private const val TAG = "BotGuardPoToken"
    // Public YouTube Web BotGuard JNN API key (same key used by youtube.com desktop player)
    private const val JNN_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val JNN_REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val JS_INTERFACE = "BotGuardBridge"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var webViewBroken = false

    private val genLock = Any()
    private val ioExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeSession: BotGuardSession? = null
    private var cachedVisitorData: String? = null
    private var cachedStreamingPot: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (webViewBroken) return null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "getWebClientPoToken called on main thread; skipping to avoid UI block")
            return null
        }
        val ctx = appContext ?: run {
            Log.w(TAG, "BotGuardPoTokenProvider not initialized with Context")
            return null
        }

        return try {
            obtainTokensInternal(ctx, videoId, forceRecreate = false)
        } catch (e: BadWebViewException) {
            Log.e(TAG, "WebView BotGuard JS environment incompatible: ${e.message}")
            webViewBroken = true
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain BotGuard poToken for $videoId: ${e.message}")
            null
        }
    }

    private fun obtainTokensInternal(
        ctx: Context,
        videoId: String,
        forceRecreate: Boolean
    ): PoTokenResult {
        val (session, visitorData, streamingPot, wasRecreated) = synchronized(genLock) {
            val current = activeSession
            val shouldRecreate =
                current == null || forceRecreate || current.isExpired() || cachedVisitorData.isNullOrBlank() || cachedStreamingPot.isNullOrBlank()

            if (shouldRecreate) {
                current?.let { mainHandler.post { it.close() } }
                val freshVisitorData = fetchCleanVisitorData()
                val freshSession = BotGuardSession.createAndWait(ctx)
                val freshStreamingPot = freshSession.generatePoToken(freshVisitorData)
                activeSession = freshSession
                cachedVisitorData = freshVisitorData
                cachedStreamingPot = freshStreamingPot
                Log.d(TAG, "BotGuard session ready (visitorData=${freshVisitorData.take(18)}..., streamingPotLen=${freshStreamingPot.length})")
            }

            Quadruple(activeSession!!, cachedVisitorData!!, cachedStreamingPot!!, shouldRecreate)
        }

        val playerPot = try {
            session.generatePoToken(videoId)
        } catch (e: Exception) {
            if (wasRecreated || e is BadWebViewException) throw e
            Log.w(TAG, "BotGuard session retry for $videoId after mint error: ${e.message}")
            return obtainTokensInternal(ctx, videoId, forceRecreate = true)
        }

        Log.d(
            TAG,
            "Minted poToken for $videoId (playerPot=${playerPot.take(16)}..., streamingPot=${streamingPot.take(16)}...)"
        )
        return PoTokenResult(visitorData, playerPot, streamingPot)
    }

    private fun fetchCleanVisitorData(): String {
        val clientVersion = try {
            YoutubeParsingHelper.getClientVersion()
        } catch (_: Exception) {
            "2.20250312.01.00"
        }
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", clientVersion)
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }.toString()

        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", clientVersion)
            .build()

        NetworkClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("visitor_id HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("Empty visitor_id body")
            val json = JSONObject(body)
            val visitorData = json.optJSONObject("responseContext")?.optString("visitorData")
            if (visitorData.isNullOrBlank()) {
                throw IllegalStateException("Missing visitorData in InnerTube response")
            }
            return visitorData
        }
    }

    private fun postJnnJson(url: String, bodyJson: String): String {
        val req = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json+protobuf".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json+protobuf")
            .header("x-goog-api-key", JNN_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .build()

        NetworkClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("JNN HTTP ${resp.code} from $url: ${body.take(200)}")
            }
            return body
        }
    }

    private fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = JSONArray(rawChallengeData)
        val challengeData = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
            JSONArray(descramble(scrambled.getString(1)))
        } else {
            scrambled.getJSONArray(0)
        }

        val messageId = challengeData.optString(0)
        val safeScript = firstStringInArray(challengeData.optJSONArray(1))
        val trustedUrl = firstStringInArray(challengeData.optJSONArray(2))
        val interpreterHash = challengeData.optString(3)
        val program = challengeData.optString(4)
        val globalName = challengeData.optString(5)
        val clientExperimentsStateBlob = challengeData.optString(7)

        return JSONObject().apply {
            put("messageId", messageId)
            put("interpreterJavascript", JSONObject().apply {
                put("privateDoNotAccessOrElseSafeScriptWrappedValue", safeScript)
                put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", trustedUrl)
            })
            put("interpreterHash", interpreterHash)
            put("program", program)
            put("globalName", globalName)
            put("clientExperimentsStateBlob", clientExperimentsStateBlob)
        }.toString()
    }

    private fun firstStringInArray(arr: JSONArray?): String? {
        if (arr == null) return null
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is String) return v
        }
        return null
    }

    private fun descramble(scrambledChallenge: String): String {
        val decoded = decodeYtBase64(scrambledChallenge)
        val unscrambled = ByteArray(decoded.size) { idx ->
            (decoded[idx].toInt() + 97).toByte()
        }
        return unscrambled.toString(Charsets.UTF_8)
    }

    private fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
        val arr = JSONArray(rawIntegrityTokenData)
        val tokenU8Js = toJsUint8Array(decodeYtBase64(arr.getString(0)))
        val expirationSeconds = arr.getLong(1)
        return tokenU8Js to expirationSeconds
    }

    private fun decodeYtBase64(base64: String): ByteArray {
        val normalized = base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
        return Base64.decode(normalized, Base64.DEFAULT)
    }

    private fun toJsUint8Array(bytes: ByteArray): String {
        val joined = bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }
        return "new Uint8Array([$joined])"
    }

    private fun u8ListToYtBase64(u8CommaSeparated: String): String {
        val parts = u8CommaSeparated.split(",").filter { it.isNotBlank() }
        val bytes = ByteArray(parts.size) { i ->
            parts[i].trim().toInt().toByte()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
            .replace('+', '-')
            .replace('/', '_')
    }

    private class BadWebViewException(message: String) : Exception(message)

    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    @SuppressLint("SetJavaScriptEnabled")
    private class BotGuardSession private constructor(
        private val context: Context
    ) {
        private val initFuture = CompletableFuture<BotGuardSession>()
        private val pendingMints = ConcurrentHashMap<String, CompletableFuture<String>>()
        private val closed = AtomicBoolean(false)

        @Volatile
        private var expirationEpochMs: Long = Long.MAX_VALUE

        private lateinit var webView: WebView

        fun startInitialization() {
            mainHandler.post {
                if (closed.get()) return@post
                try {
                    val wv = WebView(context)
                    webView = wv
                    val settings = wv.settings
                    settings.javaScriptEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.blockNetworkLoads = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = false
                    }
                    wv.addJavascriptInterface(this, JS_INTERFACE)
                    wv.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                            if (cm.message().contains("Uncaught") || cm.message().contains("SyntaxError")) {
                                val err = BadWebViewException(
                                    "${cm.message()} (${cm.sourceId()}:${cm.lineNumber()})"
                                )
                                failInitialization(err)
                            }
                            return super.onConsoleMessage(cm)
                        }
                    }

                    val html = context.assets.open("botguard_potoken.html")
                        .bufferedReader()
                        .use { it.readText() }
                    val injectedHtml = html.replaceFirst(
                        "</script>",
                        "\nwindow.$JS_INTERFACE.onHtmlReady();\n</script>"
                    )
                    wv.loadDataWithBaseURL("https://www.youtube.com", injectedHtml, "text/html", "utf-8", null)
                } catch (e: Exception) {
                    failInitialization(e)
                }
            }
        }

        @JavascriptInterface
        fun onHtmlReady() {
            if (closed.get()) return
            ioExecutor.execute {
                try {
                    val rawChallenge = postJnnJson(
                        "https://www.youtube.com/api/jnn/v1/Create",
                        """["$JNN_REQUEST_KEY"]"""
                    )
                    val challengeJson = parseChallengeData(rawChallenge)
                    val quoted = JSONObject.quote(challengeJson)
                    mainHandler.post {
                        if (closed.get()) return@post
                        webView.evaluateJavascript(
                            """
                            (function() {
                              try {
                                var data = JSON.parse($quoted);
                                runBotGuard(data).then(function(res) {
                                  window.__webPoSignalOutput = res.webPoSignalOutput;
                                  window.$JS_INTERFACE.onBotGuardResult(res.botguardResponse);
                                }, function(err) {
                                  window.$JS_INTERFACE.onJsInitError(String(err));
                                });
                              } catch (err) {
                                window.$JS_INTERFACE.onJsInitError(String(err));
                              }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                } catch (e: Exception) {
                    failInitialization(e)
                }
            }
        }

        @JavascriptInterface
        fun onBotGuardResult(botguardResponse: String) {
            if (closed.get()) return
            ioExecutor.execute {
                try {
                    val rawIt = postJnnJson(
                        "https://www.youtube.com/api/jnn/v1/GenerateIT",
                        """["$JNN_REQUEST_KEY", "$botguardResponse"]"""
                    )
                    val (integrityTokenJs, ttlSeconds) = parseIntegrityTokenData(rawIt)
                    // Renew 10 minutes ahead of server TTL
                    expirationEpochMs = System.currentTimeMillis() +
                        ((ttlSeconds - 600L).coerceAtLeast(60L)) * 1000L

                    mainHandler.post {
                        if (closed.get()) return@post
                        webView.evaluateJavascript(
                            "window.__integrityToken = $integrityTokenJs;"
                        ) {
                            initFuture.complete(this@BotGuardSession)
                        }
                    }
                } catch (e: Exception) {
                    failInitialization(e)
                }
            }
        }

        @JavascriptInterface
        fun onJsInitError(error: String) {
            val ex = if (error.contains("SyntaxError")) {
                BadWebViewException(error)
            } else {
                IllegalStateException("BotGuard JS init error: $error")
            }
            failInitialization(ex)
        }

        fun isExpired(): Boolean = closed.get() || System.currentTimeMillis() >= expirationEpochMs

        fun generatePoToken(identifier: String): String {
            if (closed.get()) throw IllegalStateException("BotGuardSession closed")
            val reqId = UUID.randomUUID().toString()
            val future = CompletableFuture<String>()
            pendingMints[reqId] = future

            val u8Js = toJsUint8Array(identifier.toByteArray(Charsets.UTF_8))
            val quotedReqId = JSONObject.quote(reqId)

            mainHandler.post {
                if (closed.get()) {
                    pendingMints.remove(reqId)?.completeExceptionally(IllegalStateException("Session closed"))
                    return@post
                }
                webView.evaluateJavascript(
                    """
                    (function() {
                      try {
                        var u8 = $u8Js;
                        var pot = obtainPoToken(window.__webPoSignalOutput, window.__integrityToken, u8);
                        var parts = [];
                        for (var i = 0; i < pot.length; i++) parts.push(pot[i]);
                        window.$JS_INTERFACE.onPoTokenResult($quotedReqId, parts.join(','));
                      } catch (err) {
                        window.$JS_INTERFACE.onPoTokenError($quotedReqId, String(err));
                      }
                    })();
                    """.trimIndent(),
                    null
                )
            }

            return future.get(8, TimeUnit.SECONDS)
        }

        @JavascriptInterface
        fun onPoTokenResult(reqId: String, poTokenU8: String) {
            val future = pendingMints.remove(reqId) ?: return
            try {
                future.complete(u8ListToYtBase64(poTokenU8))
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        @JavascriptInterface
        fun onPoTokenError(reqId: String, error: String) {
            val future = pendingMints.remove(reqId) ?: return
            val ex = if (error.contains("SyntaxError")) BadWebViewException(error) else IllegalStateException(error)
            future.completeExceptionally(ex)
        }

        private fun failInitialization(t: Throwable) {
            initFuture.completeExceptionally(t)
            mainHandler.post { close() }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            pendingMints.forEach { (k, f) ->
                pendingMints.remove(k)
                f.completeExceptionally(IllegalStateException("BotGuardSession closed"))
            }
            if (this::webView.isInitialized) {
                try {
                    webView.clearHistory()
                    webView.loadUrl("about:blank")
                    webView.onPause()
                    webView.removeAllViews()
                    webView.destroy()
                } catch (_: Exception) {
                }
            }
        }

        companion object {
            fun createAndWait(context: Context): BotGuardSession {
                val session = BotGuardSession(context.applicationContext)
                session.startInitialization()
                return try {
                    session.initFuture.get(15, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    mainHandler.post { session.close() }
                    val cause = e.cause ?: e
                    if (cause is BadWebViewException) throw cause
                    throw IllegalStateException("BotGuard session init failed: ${cause.message}", cause)
                }
            }
        }
    }
}
