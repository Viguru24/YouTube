<div align="center">

# 🎬 Vixz - Premium Ad-Free YouTube Client for Android

**Zero Commercial Ads • SponsorBlock Auto-Skip • Hardware Audio Muxing • 3-Zone Gestures • 0ms SWR Startup**

[![Download APK](https://img.shields.io/badge/📥%20Download%20Latest%20APK-v1.7.0-E50914?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Viguru24/YouTube/releases/latest/download/YouTube-Player-v1.7.0.apk)
[![GitHub Release](https://img.shields.io/github/v/release/Viguru24/YouTube?style=for-the-badge&color=2ea44f)](https://github.com/Viguru24/YouTube/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

</div>

---

## ⚡ Quick 1-Minute Install Guide

Getting started takes less than 60 seconds:

1. **[Click here to Download the Latest APK](https://github.com/Viguru24/YouTube/releases/latest/download/YouTube-Player-v1.7.0.apk)** directly to your Android phone.
2. Tap the downloaded file in your browser notifications.
3. If prompted by Android, tap **"Settings" → "Allow from this source"**, then tap **Install**.
4. Open **Vixz** and enjoy pure, uninterrupted video playback with zero ads!

---

## 🛡️ How Ad-Elimination Works (Two-Layer Defense)

Unlike web browsers with ad-block extensions that often get detected or break playback, Vixz uses a native two-layer architecture:

### 1. 100% Elimination of Commercial YouTube Ads
- **How it works:** Vixz connects directly to Google's raw media distribution networks (`.mp4`, `.m3u8`, and DASH streams) and decodes video using native Android hardware (`ExoPlayer`).
- **Result:** YouTube's ad-serving JavaScript code and ad tracking containers **never execute**. There are **0 pre-roll ads, 0 mid-roll ads, 0 post-roll ads, and 0 banner popups**.

### 2. Automatic SponsorBlock Skipping
- **How it works:** When creators record sponsor promotions directly into their video footage (*"A quick word from our sponsor..."*), Vixz queries the crowd-sourced **SponsorBlock API**.
- **Result:** As soon as playback hits a sponsor timestamp, the player automatically leaps over the entire promotion in milliseconds, displaying an on-screen badge: `⏭️ Skipped Sponsor (01:15 → 02:30)`.

---

## 🎮 3-Zone Smart Player Gestures

Control your video effortlessly without hunting for tiny sliders:

| Screen Area | Gesture | Action |
| :--- | :--- | :--- |
| **Far Left Edge** (Outer 28%) | Vertical Drag Up / Down | ☀️ **Brightness Control** (0% – 100%) with animated HUD |
| **Far Right Edge** (Outer 28%) | Vertical Drag Up / Down | 🔊 **Media Volume Control** (0% – 100%) with animated HUD |
| **Middle Area** (Center) | Single Tap | ⏯️ **Play / Pause** toggle |
| **Center Left** | Double-Tap | ⏪ **Rewind 10 Seconds** (`-10s`) |
| **Center Right** | Double-Tap | ⏩ **Fast-Forward 10 Seconds** (`+10s`) |
| **Middle Area** | Swipe Up | ⏭️ **Next Video in Feed Queue** |
| **Middle Area** | Swipe Down | ⏮️ **Previous Video in Feed Queue** |

---

## 🌟 Other Key Features

- **⚡ 0ms SWR Local-First Startup**: Instant feed loading from local Room SQLite database with silent background sync.
- **💾 Offline Downloads with Muxed Audio**: Hardware MediaMuxer combines separate high-res video and high-bitrate audio streams into a single synchronized `.mp4` file for airplane mode.
- **📁 One-Tap Organization**: Star favorites, save to custom subject folders, or add to Watch Later directly on the video screen.
- **🤖 On-Device AI Summaries**: Instant video takeaways and timestamped note-taking.
- **🔒 100% Private**: No cloud telemetry, no analytics, and no Google Play Services dependencies.

---

## 📱 Compatibility
- **Android Version:** Android 8.0 (Oreo) through Android 15+
- **Architecture:** `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **Root Required:** No (100% Standalone APK)
