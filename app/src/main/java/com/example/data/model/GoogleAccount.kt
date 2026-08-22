package com.example.data.model

data class GoogleAccount(
    val name: String = "Local User",
    val email: String = "local@vixz.app",
    val avatarInitials: String = "U",
    val isSignedIn: Boolean = true,
    val lastSyncTime: String = "Just now"
)
