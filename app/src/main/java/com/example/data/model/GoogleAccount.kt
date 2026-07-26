package com.example.data.model

data class GoogleAccount(
    val name: String = "Louis de Souza",
    val email: String = "louisdesouza@gmail.com",
    val avatarInitials: String = "LS",
    val isSignedIn: Boolean = true,
    val lastSyncTime: String = "Just now"
)
