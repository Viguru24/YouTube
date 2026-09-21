# 📋 Vixz Update Log

All notable changes, fixes, and improvements across the Android client and Windows Desktop companion.

---

## 🔐 Release v2.0.0 — Authentication Overhaul (September 21, 2026)

### 🔑 1. Complete Sign-In / Sign-Out System Rebuild
- **Sign-In Window Rewrite:** `SignInWindow.xaml.cs` completely rebuilt from scratch. Previous version had an infinite polling loop that wrote to `account_sync.log` every 5 seconds and never auto-detected success. New version is fully event-driven — watches `NavigationCompleted` for a `youtube.com` landing after Google sign-in completes, checks cookies once, and closes automatically. Zero timers, zero polling.
- **Sign-Out Now Actually Works:** `SignOutAccount_Click` previously only cleared the in-memory `UserAccount` object, leaving all YouTube/Google cookies intact in the WebView2 profile. The next background sync immediately re-detected the old session and re-set `HasAuth=True`, making sign-out appear broken. Fixed: sign-out now calls `CookieManager.DeleteAllCookies()` to wipe the entire profile cookie store, then navigates to `accounts.google.com/Logout` to invalidate the server-side session.
- **Removed Auto Sign-In Loop:** `SyncAccountProfileAsync` was auto-opening the sign-in window when account extraction failed due to YouTube's bot-throttle page (111KB thin page). This caused a loop: sign-in window opens → user closes → `PlayVideoAsync` retries → player loads → Error 150 → stream fallback → sign-in window again, forever. Replaced with a silent log entry — the user can manually sign in from the account button.

### 🛡️ 2. YouTube Embed Error Loop Fix
- **`_fallbackFired` Guard:** YouTube's embed player was spamming `onError(150)` (embedding disabled) dozens of times per second. Each call sent `PLAYER_STREAM_FALLBACK` to C#, which opened the sign-in window, which re-played the video, which fired Error 150 again — an infinite loop. Now a `_fallbackFired` boolean guard ensures only a single fallback message fires per video load.
- **`onError` vs Sign-In Wall Separated:** The 6-second "stuck unstarted" sign-in wall detector and the `onError(150)` handler were both sending `PLAYER_STREAM_FALLBACK`, colliding with each other. They now send distinct messages:
  - `PLAYER_STREAM_FALLBACK:videoid` — embedding disabled (Error 150/101). C# tries stream engine, shows "owner disabled embedding" toast. No sign-in window.
  - `PLAYER_SIGNINWALL:videoid` — 6-second timeout detected the YouTube session expired sign-in wall. C# opens the sign-in window cleanly, once.
- **Guard resets on new video load:** `_fallbackFired` is reset to `false` at the top of `loadVideo()` so each new video gets a fresh guard.

### 🔍 3. Improved Sign-In Wall Detection
- **6-Second Player State Timeout:** Added to `onReady` callback — if `player.getPlayerState()` is still `-1` (unstarted) 6 seconds after the embed reports ready, the YouTube session has expired and the sign-in wall is blocking playback silently. YouTube never fires `onError` in this case. Now detected automatically.

---

## 🚀 Release v1.9.8 (September 8, 2026)

### 🔍 1. Complete Pinch-to-Zoom & Pan Engine Overhaul
- **Smooth 1.0x – 5.0x Multi-Touch Scaling:** Completely re-engineered the gesture engine in `PlayerGestureModifier.kt` using Compose `@Composable` state wrappers (`rememberUpdatedState`) to eliminate stale closures.
- **Zero Jitter & Elimination of Wild Jumps:** Fixed multi-pointer tracking so the previous pinch distance resets immediately when finger count drops below 2, preventing catastrophic multiplication spikes and erratic leaps.
- **Dedicated 1-Finger 2D Panning:** When zoomed in (`> 1.05x`), single-finger drags now smoothly pan around the video frame with hardware boundary clamping (`maxPan = (dimension * (zoom - 1)) / 2`). Prevents accidental volume, brightness, or timeline scrubbing while zoomed.
- **Unobstructed Pure Viewing (Zero Clutter):** Completely removed all intrusive on-screen zoom overlays and buttons—no floating re-center button and no top-center zoom badge blocking video content.
- **Automatic Squeeze-In Snap-to-Original:** When pinching in or squeezing back down, the video automatically snaps cleanly back to its original 1.0x centered layout without needing any button.
- **Double-Tap Quick Reset:** Double-tapping anywhere on the video frame also instantly restores original 1.0x zoom and centers the view.
- **WebView Fallback Parity:** Added matching `graphicsLayer` hardware transformations to the WebView fallback player.

### 🤖 2. On-Demand AI Summaries & Tight Chat Interface
- **Explicit-Only Summarization:** Video summaries now only run when the user taps the **AI Summary** button, preventing unwanted automatic summaries.
- **Compact "Tight" Chat Modal:** Upgraded the AI chat dialog to a responsive, half-screen bottom sheet with draggable handles, keeping video controls and playback visible.
- **Instant Non-Blocking Interaction:** Users can start typing and sending questions immediately while transcript captions stream in the background.

### 🌌 3. Sovereign Cosmo Software Suite Integration
- **In-App Showcase Card:** Added a dedicated, glassmorphic **Cosmo Software Suite** card in Settings with 1-tap direct launchers for Cosmo Whisper and Cosmo Symphony.

### 📱 4. Multi-Device Simultaneous Installer
- **Parallel Multi-Device Push:** Batch installer detects all connected Android devices via ADB and installs/updates across all of them in parallel.

### ⚙️ 5. Settings Dialog Obsidian Theme Overhaul
- **Pure Dark Obsidian Glassmorphism:** Deep obsidian background gradient, neon bevel border, and frosted glass cards.

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
