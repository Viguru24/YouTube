<div align="center">

# 🎬 Vixz - Premium Ad-Free YouTube Client for Android

**Zero Commercial Ads • SponsorBlock Auto-Skip • Hardware Audio Muxing • 3-Zone Gestures • 0ms SWR Startup**

<br/>

[![Download APK](https://img.shields.io/badge/📥%20Download%20Latest%20APK-v1.9.7-E50914?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Viguru24/YouTube/releases/download/v1.9.7/Vixz-YouTube-Player-v1.9.7.apk)
[![Obtainium](https://img.shields.io/badge/⚡%20Auto--Update-Obtainium-9C27B0?style=for-the-badge&logo=android&logoColor=white)](https://apps.obtainium.imranr.dev/add?r=https://github.com/Viguru24/YouTube)
[![Windows PC App](https://img.shields.io/badge/🖥️%20Windows%20PC%20App-Vixz%20Desktop-0078D6?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/Viguru24/VixzDesktop)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <a href="#-quick-install-guide">📥 <b>Download & Install</b></a> •
  <a href="#-whats-new-in-v197">🆕 <b>What's New (v1.9.7)</b></a> •
  <a href="#-vixz-desktop-windows-pc-version">🖥️ <b>Windows PC Version</b></a> •
  <a href="#-key-features">✨ <b>Features</b></a> •
  <a href="#-gestures-reference">🎮 <b>Gestures</b></a> •
  <a href="#-tech-stack">🛠️ <b>Tech Stack</b></a>
</p>

</div>

---

## 🆕 What's New in v1.9.7

- 🎮 **Dedicated Paused Action Strip:** Sleek new pause overlay featuring Prev/Next buttons, 1-tap PiP pop-out, and Thumbs Down with immediate auto-skip to the next video.
- ⧉ **Dual Visible PiP Triggers:** Pop-Out Picture-in-Picture buttons available on both the top bar and the bottom utility deck.
- 🔄 **180° Hardware Reverse Rotation Flip:** Instant display flip button that overrides Android system orientation locks.
- 🎙️ **Voice Search Support:** Speak naturally with in-app microphone voice recognition for instant video searching.
- 🔍 **Pinch-to-Zoom up to 5x:** Fluid multi-touch video zooming paired with precision outer-border brightness & volume gestures.
- ⚡ **Dominant Recency Feed Sync:** Parallel background channel fetching (30 uploads each) with heavy recency boosting (+1200 pts) for ultra-fresh uploads.
- 🔗 **Full Link Interception:** Tapping creator channels or video links inside player cards stays strictly within Vixz.

---

## 🖥️ Vixz Desktop (Windows PC Version)

Prefer watching on your laptop or desktop? Experience **[Vixz Desktop](https://github.com/Viguru24/VixzDesktop)** — the ultimate Windows 10/11 YouTube companion:

- 🎨 **Fluent Acrylic Glassmorphism:** Rich, dynamic Windows design with zero generic web wrappers.
- 🛡️ **Zero Commercial Ads & SponsorBlock:** Automatically skips pre-rolls, banner pop-ups, and in-video sponsor segments with millisecond precision.
- 🤖 **AI Copilot with Voice & TTS:** Ask questions about any video, summarize key takeaways with interactive timestamps, and listen with **🔊 Read Aloud (Text-to-Speech)**.
- 🎙️ **Voice Search & Commands:** Use speech recognition to find videos or execute commands hands-free.
- 📥 **1-Click 1080p MP4 Downloader:** Save pristine video and audio directly to your local drive for offline viewing.
- ⧉ **Always-On-Top Floating PiP Mini-Player:** Keep videos floating while working in other apps.
- 🌙 **Sleep Timer with Audio Fade-Out:** Automatically winds down playback with smooth audio attenuation.

👉 **[📥 Download Vixz Desktop for Windows (.MSIX)](https://github.com/Viguru24/VixzDesktop/releases/download/v1.0.0/VixzDesktop-v1.0.0.msix)** • **[Explore the GitHub Repo](https://github.com/Viguru24/VixzDesktop)**

---

## ⚡ Quick Install Guide

### Option A: Direct APK Install
1. **[📥 Download the Latest Vixz APK (v1.9.7)](https://github.com/Viguru24/YouTube/releases/download/v1.9.7/Vixz-YouTube-Player-v1.9.7.apk)** directly to your Android device.
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

## 📱 Device Compatibility

- **Android Versions:** Android 8.0 (Oreo) through Android 15+
- **Architectures:** `arm64-v8a`, `armeabi-v7a`, `x86_64`

---

## 🌟 Try Our Other Products

Discover more tools developed by our team:

- 🎙️ **[Cosmo Whisper](https://github.com/Viguru24/CosmoWhisper-Native)** — Ultra-fast, on-device AI speech-to-text, audio transcription, and voice intelligence.
- 🎬 **[Cosmo Symphony on Microsoft Store](https://apps.microsoft.com/detail/9P4DFBGWGFF6?hl=en-us&gl=GB&ocid=pdpshare)** / **[GitHub](https://github.com/Viguru24/Video)** — Video, photo, and media studio editing & organization on Windows.
- 🖥️ **[Vixz Desktop](https://github.com/Viguru24/VixzDesktop)** — Windows 10/11 Glassmorphic YouTube Player & AI Copilot.

---

<div align="center">

**[⭐ Star this repository on GitHub](https://github.com/Viguru24/YouTube)** to support active development!

</div>
