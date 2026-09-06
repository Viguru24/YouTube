package com.example.data.model

data class GoogleAccount(
    val name: String = "Guest",
    val email: String = "",
    val avatarInitials: String = "",
    val avatarUrl: String = "",
    val isSignedIn: Boolean = false,
    val lastSyncTime: String = "Just now"
)
