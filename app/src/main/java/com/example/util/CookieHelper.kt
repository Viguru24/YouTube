package com.example.util

import android.content.Context
import android.util.Log
import android.webkit.CookieManager

object CookieHelper {
    private const val TAG = "CookieHelper"

    val AUTH_DOMAINS = listOf(
        "https://www.youtube.com",
        "https://youtube.com",
        "https://m.youtube.com",
        "https://accounts.google.com",
        "https://google.com",
        "https://www.google.co.uk",
        "https://google.co.uk",
        "https://accounts.google.co.uk"
    )

    /**
     * Aggregates and deduplicates all cookies across YouTube and Google authentication domains.
     * Guarantees that LOGIN_INFO, SID, SSID, HSID, SAPISID, and __Secure-* cookies are unified
     * into a single complete cookie header string.
     */
    fun getAggregatedCookies(): String {
        return try {
            val cm = CookieManager.getInstance()
            val cookieMap = LinkedHashMap<String, String>()

            for (url in AUTH_DOMAINS) {
                val cookieStr = cm.getCookie(url) ?: continue
                cookieStr.split(";").forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty() && trimmed.contains("=")) {
                        val key = trimmed.substringBefore("=").trim()
                        val value = trimmed.substringAfter("=").trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            cookieMap[key] = value
                        }
                    }
                }
            }
            cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to aggregate cookies: ${e.message}")
            ""
        }
    }

    /**
     * Checks whether the given cookie string contains genuine Google/YouTube authentication tokens.
     */
    fun hasAuthCookies(cookies: String): Boolean {
        if (cookies.isBlank()) return false
        return cookies.contains("LOGIN_INFO") ||
                cookies.contains("SID") ||
                cookies.contains("SAPISID") ||
                cookies.contains("SSID") ||
                cookies.contains("APISID") ||
                cookies.contains("__Secure-1PSID") ||
                cookies.contains("__Secure-3PSID")
    }

    /**
     * Extracts SAPISID from aggregated cookies if present.
     */
    fun extractSapisid(cookies: String): String? {
        val match = Regex("""(?:^|;\s*)SAPISID=([^;]+)""").find(cookies)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * Computes the SAPISIDHASH Authorization header required by YouTube Innertube
     * for verified, authenticated requests that bypass bot-detection challenges.
     */
    fun generateSapisidHash(sapisid: String, origin: String = "https://www.youtube.com"): String {
        return try {
            val timestamp = System.currentTimeMillis() / 1000
            val toHash = "$timestamp $sapisid $origin"
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val digest = md.digest(toHash.toByteArray(Charsets.UTF_8))
            val hash = digest.joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_$hash"
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Injects a cookie string into Android's CookieManager across all YouTube and Google domains.
     */
    fun injectCookies(cookieStr: String) {
        if (cookieStr.isBlank()) return
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val parts = cookieStr.split(";")
            for (url in AUTH_DOMAINS) {
                for (part in parts) {
                    val cookie = part.trim()
                    if (cookie.isNotEmpty()) {
                        cm.setCookie(url, cookie)
                    }
                }
            }
            cm.flush()
            Log.d(TAG, "Injected cookies into CookieManager across ${AUTH_DOMAINS.size} domains")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to inject cookies: ${e.message}")
        }
    }

    /**
     * Harvests cookies from CookieManager or SharedPreferences and synchronizes them across both.
     * Returns the active cookie string.
     */
    fun syncAndPersistCookies(context: Context): String {
        val prefs = context.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE)
        val liveCookies = getAggregatedCookies()

        val effective = if (hasAuthCookies(liveCookies)) {
            // Live CookieManager has authenticated tokens: persist to SharedPreferences
            prefs.edit().putString("youtube_cookies", liveCookies).apply()
            Log.d(TAG, "Synced live auth cookies to SharedPreferences (${liveCookies.length} chars)")
            liveCookies
        } else {
            // Live CookieManager does not have tokens: check SharedPreferences
            val saved = prefs.getString("youtube_cookies", "") ?: ""
            if (hasAuthCookies(saved)) {
                // Re-inject saved tokens into CookieManager
                injectCookies(saved)
                Log.d(TAG, "Restored saved auth cookies from SharedPreferences into CookieManager (${saved.length} chars)")
                saved
            } else {
                liveCookies
            }
        }

        return effective
    }
}
