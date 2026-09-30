# 📋 Vixz Update Log

All notable changes, fixes, and improvements across the Android client and Windows Desktop companion.

---

## ⚡ Release v2.1.0 — TypeSafe AI Jev System-One Integration (September 21, 2026)

### ⚡ 1. Jev System-One Decision Engine (New Feature)
- **TypeSafe AI Jev Integrated:** Vixz Desktop now ships with the ultra-fast [TypeSafe AI Jev](https://console.typesafe.ai) System-One decision model — a non-generative AI that returns probabilistic typed decisions in 70–300ms. Unlike chat LLMs it never generates text, only decisive, structured actions.
- **Completely Optional:** A new **⚡ Jev AI** toggle button in the top navbar lets users enable or disable the engine at any time. If no API key is provided, the app behaves exactly as before.
- **Free to Start:** New accounts at [console.typesafe.ai](https://console.typesafe.ai) receive $5 in free credit (~120 million input tokens). Cost is only $0.042 per million input tokens; output is completely free.

### 🔐 2. Autonomous Account Chooser & Consent Resolver (Jev Feature 1)
- When the Google sign-in flow lands on an account chooser page, cookie consent wall, or `accountchooser.google.com`, Jev automatically extracts all visible interactive elements from the page DOM and asks: *"Which button selects the user's account or dismisses this consent wall?"*
- Jev returns the correct element ID in <300ms. Vixz instantly clicks it — no user interaction needed to navigate past cookie banners or the account picker.
- Status feedback shown in `SignInWindow` as **"⚡ Jev Auto-Action: Selecting 'joeblack10810@gmail.com'..."**

### 📺 3. Smart Viewport Quality Arbitrator (Jev Feature 2)
- Before every video load, Jev evaluates the current window width and height against the user's preferred quality setting and the available quality options (`hd1080`, `hd720`, `large`, `medium`).
- It returns the single optimal quality variant in <200ms, preventing unnecessary bandwidth waste on small windows or applying maximum quality when the window fills the screen.
- Falls back to `StorageService.Settings.PreferredQuality` instantly if Jev is disabled or has no key.

### 🎵 4. Autonomous Content Curator & AI DJ (Jev Feature 3)
- When a video ends and Autoplay fires, Jev evaluates up to 12 candidate videos from the current feed.
- Jev is given the current video title and channel as context, and asked: *"Select the best next video to play that matches the theme without repeating content."*
- Chosen videos show a **"⚡ Jev AI DJ: Playing '...'"** toast instead of the standard autoplay message.
- Falls back to sequential index autoplay if Jev is off or unavailable.

### ⚙️ 5. Jev Settings Panel (New UI)
- **⚡ Jev AI** button in the top navbar opens a dedicated settings dialog with:
  - Enable/Disable toggle checkbox
  - One-click **"🌐 Get Jev API Key"** button → opens [console.typesafe.ai](https://console.typesafe.ai) in the system browser
  - API key text field with live validation ("Test Jev Key" button)
  - Clear Key and Save & Apply buttons
  - Live status indicator (🟢 ACTIVE / ⚪ DISABLED)
- Button label changes to **"⚡ Jev ON"** (gold) or **"⚡ Jev OFF"** (grey) depending on state.

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

## 🚀 Release v2.0.0 — Authentication & Navigation Redesign (September 30, 2026)

### 🔐 1. Full-Screen Web Sign-In & Authentication Overhaul
- **Full-Screen Unobstructed Login:** `YouTubeWebSignInDialog` now presents a responsive full-screen experience with clean headers ("Sign In to YouTube"), eliminating cramped toolbars and truncated buttons.
- **Unobstructed Interaction:** Removed the floating confirmation button that blocked Google's login fields. The confirmation card now smoothly slides up only once authentication is verified.
- **Unified Cookie Engine:** Created `CookieHelper` to automatically aggregate, deduplicate, and validate critical session tokens across all YouTube and Google authentication domains (`LOGIN_INFO`, `SID`, `SAPISID`, `__Secure-*`), eliminating cookie-check failures.
- **Clean Overflow Menu:** Integrated an overflow menu (`⋮`) for advanced operations ("Clear Cookies & Reset", "Paste Cookies Manually", "Open in External Browser"), keeping the main screen distraction-free.

### 👤 2. Avatar Branding & Guest Flow Perfection
- **Accurate Initials Avatar:** Removed faulty third-party channel letter image scraping that was overriding the user's avatar. Genuine initials (`LO`) now render consistently with a gradient badge.
- **Dedicated Settings Navigation:** Replaced the overlapping settings badge on the avatar with a dedicated Settings icon button in the top navigation bar. Direct tap on the avatar opens account management immediately.
- **Zero Developer Clutter:** Eliminated raw text inputs ("ENTER PROFILE MANUALLY", raw cookie text areas) from the primary login modal for a seamless out-of-the-box user experience.
- **100% Clean Slate for Fresh Installs:** Fresh installs start with empty subscriptions, zero cookies, and a pure guest feed ready for immediate playback without requiring a sign-in.

---

## 🚀 Release v1.9.9 (September 30, 2026)

### 📊 1. Live Channel Subscriber Counts & View Count Parser Restoration
- **Live Channel Subscriber Count:** Added live channel subscriber counts directly into the portrait video player header (e.g. `1.75M subscribers • 138K views • 1 day ago`), matching the official YouTube layout.
- **In-Memory Zero-Lag Subscriber Cache:** Live subscriber counts are extracted directly from creator channel page headers during existing video fetches without adding any network latency.
- **Asynchronous Stats Resolver:** If a video was opened with missing subscriber or view count metadata, an asynchronous background task resolves and updates the counts without interrupting video playback.
- **Modern YouTube View Count Parser:** Upgraded `lockupViewModel` parsing in `YouTubeLiveSearchService` to recognize YouTube's updated view count format (e.g. `"138K"`, `"1.2M"` and accessibility labels), ensuring view counts never disappear.
- **Zero-Migration Database Safety:** Retained strict database schema stability with zero Room migrations required, preserving all existing user favorites, history, and notes.

---

## 🚀 Release v1.9.8 (September 28, 2026)

### ⚡ 1. Shorts Feed Engine Overhaul & True Latest Sorting
- **Direct Creator `/shorts` Tab Endpoint:** Direct connection to `https://www.youtube.com/@handle/shorts` endpoints ensures user subscriptions and feed channels pull genuine, chronological Shorts in exact order of upload (0ms latency, latest uploads first).
- **Modern `shortsLockupViewModel` Support:** Native parsing of modern YouTube Shorts containers extracts true video IDs, dynamic view counts, and clean metadata directly from channel shelves.
- **Strict 180-Day Freshness Ceiling:** Eliminates stale, multi-year-old search result artifacts (hard ceiling rejects any Short older than 180 days).
- **Automatic SQLite Cache Purge:** Background maintenance cleanses historical database cache of outdated shorts on startup so old shorts never linger in the queue.

### 🔍 2. Complete Pinch-to-Zoom & Pan Engine Overhaul
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
