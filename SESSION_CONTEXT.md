# MoWin Active Session Context

Updated: 2026-10-27

## System Status
- **Target Device:** Samsung Galaxy S23 (SM-S911B), Android 16 (API 36), ARM64. Connected via ADB (`RZCX61CSZHX`).
- **Current App Package:** `dev.mowin.app` (dynamically loaded via PRoot).
- **Core Components:**
  - Arch Linux ARM64 Rootfs in `/data/data/dev.mowin.app/files/runtime/rootfs`.
  - PRoot with `PROOT_NO_SECCOMP=1`.
  - Box64 x86-64 CPU emulator in `/usr/local/bin/box64`.
  - Hangover 11.16 Wine in `/opt/hangover/`.
  - Display Server: Xvfb + x11vnc on `:0` (`127.0.0.1:5900`), framebuffer at `/tmp/Xvfb_screen0`.
  - CAD Software: SolveSpace v3.3 (single-core x86-64 Win32+OpenGL) in `/opt/cad/solvespace.exe` + `/usr/local/bin/mowin-cad`.
  - CAD Samples: `esp32.slvs` and `caddy.slvs` in `/opt/cad/samples/` and `C:\samples\`.

## Key Features Implemented
1. **Interactive Touch-First Gestures:**
   - Single tap -> Left click (or Right click in toggle mode).
   - Long press -> Right click.
   - Two-finger tap -> Configurable shortcut (`CAD Zoom Extents` in CAD profile, `Esc` in Default profile).
   - One-finger drag -> Left drag (moves entities / rotates view).
   - Two-finger drag -> Middle click drag (pan CAD viewport).
   - Pinch-to-zoom -> Wheel Up / Down (CAD zoom in / out).
2. **Gesture Profile Manager & Persistence:**
   - JSON-backed profile management (`gesture_profiles.json`).
   - "Default Desktop", "CAD Workstation", "Productivity & Coding" profiles.
   - Interactive Compose Gesture Editor with live Test Mode sandbox.
3. **Display & Fullscreen HUD:**
   - 30 FPS memory-mapped framebuffer reader (`/tmp/Xvfb_screen0`).
   - Letterbox-aware touch coordinate mapping (`mapTouchToFb`).
   - Pinned prominent red Exit Fullscreen button (`✕`).
   - Fullscreen CAD HUD toolbar: `🖱 Left / 🚩 Right`, `⚙ Gestures`, `⌨ Keys`, `Notepad`, `Mines`, `📐 CAD`, `Esc`, `↶ Undo`, `🔍+ Zoom In`, `🔍- Zoom Out`.
   - Single-line quick launch buttons in Card mode: `[Notepad] [Mines] [📐 CAD] [Kill]`.
4. **Verified Live Applications:**
   - SolveSpace 2D/3D Parametric CAD with ESP32 board model.
   - Wine Notepad (`notepad.exe`).
   - Wine Minesweeper (`winemine.exe`).

## Test Results
- `./gradlew testDebugUnitTest`: 13/13 tests pass in 4s.
- Device APK built and installed via streamed ADB install.
