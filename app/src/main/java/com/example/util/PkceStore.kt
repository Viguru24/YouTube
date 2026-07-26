package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Holds the PKCE code_verifier across the OAuth2 authorization flow.
 * Generated before the auth request, consumed during token exchange.
 */
object PkceStore {
    var codeVerifier: String = ""

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
