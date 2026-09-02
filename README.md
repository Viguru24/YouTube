<div align="center">

# 🎬 Vixz - Premium Ad-Free YouTube Client for Android

**Zero Commercial Ads • SponsorBlock Auto-Skip • Hardware Audio Muxing • 3-Zone Gestures • 0ms SWR Startup**

[![Download APK](https://img.shields.io/badge/📥%20Download%20Latest%20APK-v1.9.6-E50914?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Viguru24/YouTube/releases/download/v1.9.6/Vixz-YouTube-Player-v1.9.6.apk)
[![Obtainium](https://img.shields.io/badge/⚡%20Auto--Update-Obtainium-9C27B0?style=for-the-badge&logo=android&logoColor=white)](https://apps.obtainium.imranr.dev/add?r=https://github.com/Viguru24/YouTube)
[![Windows App](https://img.shields.io/badge/🖥️%20Windows%20App-Vixz%20Desktop-0078D6?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/Viguru24/VixzDesktop)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <a href="#-quick-install-guide">📥 <b>Download & Install</b></a> •
  <a href="#-whats-new-in-v196">🆕 <b>What's New</b></a> •
  <a href="#-key-features">✨ <b>Features</b></a> •
  <a href="#-feature-comparison">📊 <b>Comparison</b></a> •
  <a href="#-gestures-reference">🎮 <b>Gestures</b></a> •
  <a href="#-tech-stack">🛠️ <b>Tech Stack</b></a>
</p>

</div>

---

## ⚡ Quick 1-Minute Install Guide

Getting started takes less than 60 seconds:

---

## ⚡ Quick Install Guide

### Option A: Direct APK Install
1. **[📥 Download the Latest Vixz APK (v1.9.6)](https://github.com/Viguru24/YouTube/releases/download/v1.9.6/Vixz-YouTube-Player-v1.9.6.apk)** directly to your Android device.
2. Open the downloaded `.apk` file from your browser downloads or notification tray.
3. If prompted by Android, tap **"Settings" → "Allow from this source"**, then tap **Install**.
4. Open **Vixz** and enjoy pure, uninterrupted video playback with zero ads!

### Option B: Automatic Background Updates (Recommended)
Track and auto-update Vixz seamlessly using [Obtainium](https://github.com/ImranR98/Obtainium):
- Tap **[⚡ Add to Obtainium](https://apps.obtainium.imranr.dev/add?r=https://github.com/Viguru24/YouTube)** on your device to install and receive instant background update notifications whenever a new version is released.

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
