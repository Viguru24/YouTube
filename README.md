# Vixz - Privacy-First, Fast YouTube Client for Android

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Language](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Toolkit](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVI%20%2F%20MVVM%20%2B%20SWR-FF6F00)](https://developer.android.com/topic/architecture)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A lightweight, ad-free, high-performance YouTube client for Android built with **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)**, and a **Stale-While-Revalidate (SWR) Local-First** caching architecture. 

Designed for speed, battery efficiency, and complete user privacy—without relying on Google Play Services or invasive cloud telemetry.

---

## 🌟 Key Features

### ⚡ 1. Stale-While-Revalidate (SWR) Architecture
- **Instant 0ms Startup**: Renders cached videos and subscribed feeds immediately from a local Room (SQLite) database with zero loading spinners.
- **Silent Background Sync**: Concurrently connects to YouTube to fetch fresh uploads, seamlessly merging new videos to the top of the feed without screen jumping or disrupting scroll position.
- **In-Memory TTL Caching**: High-speed memory caching prevents redundant network calls during rapid tab and screen navigation.

### 🔴 2. Real-Time Creator Upload Tracking
- **Bypasses RSS Lag**: Scrapes channel tabs (`/@creator/videos`) in real-time to surface uploads published minutes or hours ago (avoiding the 24–48 hour delay typical of traditional RSS feeds).
- **Exact Duration & Metadata Parsing**: Custom JSON tree-walker extracts accurate video durations and high-resolution thumbnail badge overlays.

### 🛡️ 3. 100% Private, On-Device Recommendation Engine
- **Zero Cloud Profiling**: Your viewing habits and favorites stay strictly on your device.
- **Transparent Relevance Scoring**: Content is ranked using client-side algorithms based on your saved favorites, creator preferences, and recency weighting.
- **Boredom & Fatigue Detection**: Automatically detects repetitive content patterns and injects fresh exploration topics.

### 🎬 4. Native Media Playback & Background Audio
- **Ad-Free Streaming**: Clean, uninterrupted video playback powered by AndroidX Media3.
- **Picture-in-Picture (PiP)**: Smooth PiP mode for seamless multitasking.
- **Background Audio**: Keep listening to podcasts, music, and lectures with your screen off.
- **Precision Controls**: Dual-slider audio controls, playback speed toggles (0.5x to 2.0x), and swipe-to-dismiss gesture deck.

### 📱 5. Dedicated Shorts Experience
- **Horizontal Shorts Carousel**: Embedded directly in the main feed for quick browsing.
- **Vertical Reel Player**: Full-screen vertical swipe player with gesture navigation.
- **Customizable Modes**: Choose between *Carousel*, *Separate Tab*, or *Completely Hidden* in Settings.

### 💾 6. Offline Downloads & Media Management
- **Local Storage**: Save full videos or audio tracks directly to your device.
- **Automated Lifecycle Expiry**: Configure custom auto-delete policies (*24h*, *48h*, *7d*, *30d*, or *Watched*).
- **Playlist & Subject Organization**: Categorize saved videos into custom subjects and playlists with one tap.

---

## 🏗️ Architecture & Technology Stack

```
┌─────────────────────────────────────────────────────────┐
│                     Jetpack Compose UI                  │
│       HomeScreen  •  LibraryScreen  •  PlayerDeck       │
└────────────────────────────┬────────────────────────────┘
                             │ StateFlow / Events
┌────────────────────────────▼────────────────────────────┐
│                    YouTubeViewModel                     │
│      Feed Buffering  •  SWR Coordination  •  Playback   │
└──────────────┬───────────────────────────┬──────────────┘
               │                           │
┌──────────────▼─────────────┐ ┌───────────▼──────────────┐
│     YouTubeRepository      │ │ YouTubeLiveSearchService │
│  Room DB (SQLite)  • Cache │ │   OkHttp  •  Stream Engine│
└────────────────────────────┘ └──────────────────────────┘
```

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0+ (Coroutines, Flow, StateFlow) |
| **UI Framework** | Jetpack Compose (Material Design 3, Accompanist, Foundation) |
| **Media Player** | AndroidX Media3 (ExoPlayer, HLS, DASH, Progressive Streams) |
| **Data Persistence** | Room Database (SQLite), DataStore Preferences |
| **Image Loading** | Coil Compose |
| **Networking** | OkHttp 4, JSON Tree Walking Parser |

---

## 📂 Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt               # Main entry point & bottom navigation
├── data/
│   ├── dao/VideoDao.kt           # Room SQLite Data Access Object
│   ├── db/AppDatabase.kt         # Room Database Configuration & Migrations
│   ├── model/                    # VideoEntity, GoogleAccount, Subscriptions
│   ├── remote/                   # YouTubeLiveSearchService, Stream Extractor
│   └── repository/               # YouTubeRepository, RecommendationEngine
├── ui/
│   ├── components/               # VideoCard, YouTubePlayerView, ShortsPlayer
│   ├── screens/                  # HomeScreen, LibraryScreen, SettingsDialog
│   ├── theme/                    # Color schemes, Typography, Shapes
│   └── viewmodel/                # YouTubeViewModel (State holder & SWR logic)
└── util/
    └── YouTubeUtils.kt           # Formatting, duration, and stream helpers
```

---

## 🚀 Getting Started & Building

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17** or **JDK 21**
- **Android SDK** API level 35 (compileSdk 35, minSdk 26)

### Build Instructions

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/Viguru24/YouTube.git
   cd YouTube
   ```

2. **Build Debug APK**:
   ```powershell
   ./gradlew assembleDebug
   ```

3. **Install Directly to Connected Device via ADB**:
   ```powershell
   ./gradlew installDebug
   ```

4. **Launch the App**:
   ```powershell
   adb shell am start -n com.aistudio.youtubeplayer.vixz/com.example.MainActivity
   ```

---

## 🔒 Privacy & Local-First Philosophy

- **No Remote Telemetry**: Zero analytics, crash tracking, or user telemetry.
- **Local-Only Database**: Your favorites, watch history, subscriptions, and downloads never leave your device.
- **Optional Account Sign-In**: Browse and watch anonymously without signing in, or connect your account via open OAuth.

---

## ⚖️ Legal Disclaimer

This application is an independent, open-source third-party client built for personal fair use, accessibility, and privacy research. It is not affiliated with, endorsed by, or sponsored by YouTube, Google LLC, or Alphabet Inc. All trademarks and brand names belong to their respective owners.
