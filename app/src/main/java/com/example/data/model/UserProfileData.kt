package com.example.data.model

import androidx.compose.runtime.mutableStateListOf

object UserProfileData {
    val profileName = "Local Profile"
    
    // Subscriptions are loaded dynamically and stored in private local database
    val subscribedChannels = mutableStateListOf<String>()

    fun sanitizeChannelName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val name = raw.trim()
        if (name.contains("Jane Zhang", ignoreCase = true)) return "Jane Zhang"
        if (name.startsWith("Xiaomanyc", ignoreCase = true)) return "Xiaomanyc"
        if (name.startsWith("Magnus Midtb", ignoreCase = true)) return "Magnus Midtbø"
        if (name.startsWith("Andr", ignoreCase = true) && name.endsWith("Duqum", ignoreCase = true)) return "André Duqum"
        if (name.startsWith("THEE Sama", ignoreCase = true)) return "THEE Sama Sambit"
        if (name.startsWith("AI News & Strategy", ignoreCase = true)) return "AI News & Strategy Daily | Nate B Jones"
        if (name.startsWith("Zubair Trabzada", ignoreCase = true)) return "Zubair Trabzada | AI Workshop"
        if (name.contains("iQIYI", ignoreCase = true)) return "iQIYI 潮综艺 - Get the iQIYI APP"
        return name
    }

    fun isSubscribed(channelName: String): Boolean {
        if (channelName.isBlank()) return false
        val clean = sanitizeChannelName(channelName)
        val trimmed = channelName.trim()
        val listCopy = synchronized(subscribedChannels) { subscribedChannels.toList() }
        return listCopy.any { 
            it.equals(clean, ignoreCase = true) || it.equals(trimmed, ignoreCase = true) 
        }
    }

    fun addSubscribedChannel(name: String) {
        val clean = sanitizeChannelName(name)
        if (clean.isNotBlank() && !isSubscribed(clean)) {
            synchronized(subscribedChannels) {
                if (!subscribedChannels.any { it.equals(clean, ignoreCase = true) }) {
                    subscribedChannels.add(0, clean)
                }
            }
        }
    }

    fun removeSubscribedChannel(name: String) {
        val clean = sanitizeChannelName(name)
        val trimmed = name.trim()
        synchronized(subscribedChannels) {
            subscribedChannels.removeAll { 
                it.equals(clean, ignoreCase = true) || it.equals(trimmed, ignoreCase = true) 
            }
        }
    }

    fun clearAllSubscribedChannels() {
        synchronized(subscribedChannels) {
            subscribedChannels.clear()
        }
    }
}
