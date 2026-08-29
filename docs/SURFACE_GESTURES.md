# Vixz Player Surface & Swipe Gestures Guide

This document outlines the architecture, spatial zones, and behavior of the **Surface & Multi-Touch Gesture Engine** in the Vixz YouTube Player application.

---

## 1. Spatial Touch Zones Diagram

The video playback surface is divided into three horizontal zones and two multi-touch modes:

```
+-----------------------------------------------------------------------------------------+
|                                    VIDEO PLAYER SURFACE                                 |
|                                                                                         |
|  [ 0% to 22% WIDTH ]             [ 22% to 78% WIDTH ]             [ 78% to 100% WIDTH ] |
|  LEFT BORDER EDGE                CENTER CANVAS                    RIGHT BORDER EDGE     |
|                                                                                         |
|  1-Finger Vertical Drag:         1-Finger Vertical Drag:          1-Finger Vertical Drag: |
|  ☀️ SCREEN BRIGHTNESS             ▲ Swipe Up   -> Next Video       🔊 MEDIA VOLUME       |
|  (1% to 100%)                    ▼ Swipe Down -> Previous Video   (0% to 100%)          |
|                                                                                         |
|  Double-Tap:                     1-Finger Single Tap:             Double-Tap:           |
|  ⏪ -10s Rewind                  ⏯️ Instant Play / Pause          ⏩ +10s Fast-Forward  |
|                                                                                         |
|                                  Double-Tap:                                            |
|                                  ⛶ Toggle Fullscreen                                    |
|                                                                                         |
|                          ==================================                             |
|                          2-FINGER MULTI-TOUCH (ANYWHERE):                               |
|                          🔍 Pinch-to-Zoom (1.0x to 5.0x)                                |
|                          ✥ Multi-Touch Smooth Pan                                       |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Gesture Breakdown & Behavior

### 🔍 A. Two-Finger Pinch-to-Zoom & Pan
* **Trigger:** Place 2 fingers anywhere on the video screen.
* **Zoom Range:** Fluidly scales between **1.0x (original fit)** and **5.0x (ultra zoom)**.
* **Pan:** Moving both fingers glides the viewport smoothly around the enlarged video.
* **Reset:** Pinching all the way back down resets `scale` to `1.0x` and centers the video offset back to `(0, 0)`.
* **Clean Surface:** Zero intrusive pop-up badges or obstructive buttons while zooming.

---

### ☀️ B. Left Border (Brightness Control)
* **Active Zone:** Leftmost **22%** of the screen.
* **Gesture:** Slide 1 finger up or down along the left edge.
* **Action:** Direct window brightness adjustment from **1%** to **100%**.
* **Visual HUD:** Displays a sleek, animated glassmorphism brightness bar on the left with live percentage readout.
* **Pan & Zoom State:** Always active, even when zoomed in.

---

### 🔊 C. Right Border (Volume Control)
* **Active Zone:** Rightmost **22%** of the screen.
* **Gesture:** Slide 1 finger up or down along the right edge.
* **Action:** Directly sets the Android system `STREAM_MUSIC` audio volume from **0%** to **100%**.
* **Visual HUD:** Displays an animated glassmorphism volume bar on the right with live percentage readout.
* **Pan & Zoom State:** Always active, even when zoomed in.

---

### ⏭️ / ⏮️ D. Center Canvas (Next & Previous Video Swipe)
* **Active Zone:** Center **56%** of the screen (between 22% and 78%).
* **Swipe Up (▲):** Automatically navigates to the **Next Video** in the feed or playlist.
* **Swipe Down (▼):** Automatically navigates to the **Previous Video**.
* **When Zoomed In (>1.0x):** 1-finger drag in the center switches to panning the enlarged video without triggering video changes.

---

### ⏯️ E. Single Tap (Play / Pause & Controls)
* **Trigger:** Single tap anywhere on the screen.
* **Debounce Delay:** `240ms` (ensures double-taps never accidentally trigger pause).
* **When Playing:** Instantly hides on-screen controls (0s linger).
* **When Paused:** Displays all controls and keeps them visible until you resume playback.
* **Zoom Compatibility:** Works reliably at any zoom level (1x to 5x).

---

### ⏩ / ⏪ F. Double-Tap (Seek & Fullscreen)
* **Double-Tap Right (65% to 100% width):** Skips **+10 seconds forward** and displays `⏩ +10s` HUD.
* **Double-Tap Left (0% to 35% width):** Skips **-10 seconds backward** and displays `⏪ -10s` HUD.
* **Double-Tap Center (35% to 65% width):** Toggles **Fullscreen / Orientation Lock**.
* **Playback Continuity:** Double-tapping cancels any pending single-tap action, ensuring the video continues playing smoothly without pausing.

---

## 3. Gesture Priority & Conflict Resolution Matrix

| Event Detected | Touch Count | Position | Action Executed | Conflicts Prevented |
|---|---|---|---|---|
| **Multi-Touch Pinch** | 2 fingers | Anywhere | Pinch-to-Zoom & Pan | Disables volume/brightness so pinch is clean |
| **Edge Drag Left** | 1 finger | `x < 0.22w` | Screen Brightness | Isolated from center swipe & tap |
| **Edge Drag Right** | 1 finger | `x > 0.78w` | Media Volume | Isolated from center swipe & tap |
| **Center Drag Up/Down** | 1 finger | `0.22w < x < 0.78w` | Next / Prev Video (or Pan if zoomed) | Will not trigger brightness or volume |
| **Fast Double Tap** | 1 finger | Left / Right / Center | -10s / +10s / Fullscreen | Single-tap pause job cancelled before firing |
| **Single Tap** | 1 finger | Anywhere | Toggle Play / Pause | Waits 240ms to verify no second tap occurs |
