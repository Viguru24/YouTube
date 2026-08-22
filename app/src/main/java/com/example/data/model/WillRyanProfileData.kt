package com.example.data.model

import androidx.compose.runtime.mutableStateListOf

object WillRyanProfileData {
    val profileName = "Local Profile"
    
    // Subscriptions are loaded dynamically and stored in private local database
    val subscribedChannels = mutableStateListOf<String>()

    fun isSubscribed(channelName: String): Boolean {
        if (channelName.isBlank()) return false
        val trimmed = channelName.trim()
        return subscribedChannels.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun addSubscribedChannel(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && !isSubscribed(trimmed)) {
            subscribedChannels.add(0, trimmed)
        }
    }

    fun removeSubscribedChannel(name: String) {
        val trimmed = name.trim()
        subscribedChannels.removeAll { it.equals(trimmed, ignoreCase = true) }
    }

    fun clearAllSubscribedChannels() {
        subscribedChannels.clear()
    }
}
