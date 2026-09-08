# 📋 Vixz Update Log

All notable changes, fixes, and improvements across the Android client and Windows Desktop companion.

---

## 🚀 Release v1.9.8 (September 8, 2026)

### 🔍 1. Complete Pinch-to-Zoom & Pan Engine Overhaul
- **Smooth 1.0x – 5.0x Multi-Touch Scaling:** Completely re-engineered the gesture engine in `PlayerGestureModifier.kt` using Compose `@Composable` state wrappers (`rememberUpdatedState`) to eliminate stale closures.
- **Zero Jitter & Elimination of Wild Jumps:** Fixed multi-pointer tracking so the previous pinch distance resets immediately when finger count drops below 2, preventing catastrophic multiplication spikes and erratic leaps.
- **Dedicated 1-Finger 2D Panning:** When zoomed in (`> 1.05x`), single-finger drags now smoothly pan around the video frame with hardware boundary clamping (`maxPan = (dimension * (zoom - 1)) / 2`). Prevents accidental volume, brightness, or timeline scrubbing while zoomed.
- **Snap-to-Reset & Auto-Centering:** Double-tapping anywhere on the video frame instantly restores original `1.0x` zoom and centers the view. Minor pinches below `1.01x` smoothly snap back to `1.0x`.
- **Real-Time Zoom Feedback HUD:** Added a dynamic glassmorphic HUD pill at the top-center of the screen showing current magnification in real time (e.g., `2.3x Zoom`).
- **WebView Fallback Parity:** Added matching `graphicsLayer` hardware transformations to the WebView fallback player.

### 🤖 2. On-Demand AI Summaries & Tight Chat Interface
- **Explicit-Only Summarization:** Video summaries now only run when the user taps the **AI Summary** button, preventing unwanted automatic summaries.
- **Compact "Tight" Chat Modal:** Upgraded the AI chat dialog to a responsive, half-screen bottom sheet with draggable handles, keeping video controls and playback visible.
- **Instant Non-Blocking Interaction:** Users can start typing and sending questions immediately while transcript captions stream in the background.
- **Search Header Polish:** Removed visual impediments and banner overlays from search results so queries display cleanly without clipping.

### 📱 3. Multi-Device Simultaneous Installer (`YouTube_Install_On_Phone.bat`)
- **Parallel Multi-Device Push:** Batch installer detects all connected Android devices (phones, tablets, car units) via ADB and installs/updates the latest APK across all of them in parallel.

### 📦 4. Updated Release Binaries
- **Android APK:** [`release/Vixz-YouTube-Player-latest.apk`](release/Vixz-YouTube-Player-latest.apk) (and `v1.9.7.apk`) compiled and verified.
- **Windows Desktop Executable:** [`release/VixzDesktop-latest.exe`](release/VixzDesktop-latest.exe) built via .NET 9 single-file publish with WPF & WebView2 runtime.

---

## 📅 Previous Releases Summary

### v1.9.7
- Dedicated paused action strip with Prev/Next, 1-tap PiP pop-out, and auto-skip Thumbs Down.
- Dual visible PiP triggers (top header & bottom utility bar).
- 180° hardware display flip button.
- Voice search speech-to-text input.
- Background channel upload sync with recency boosting.

### v1.9.6
- SWR feed caching with 0ms instantaneous cold startup.
- Full creator and video link interception inside player view.
- SponsorBlock precision skip integration.
