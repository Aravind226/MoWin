# MoWin Post-Sprint Validation Report

Audit date: 2026-10-27
Device: SM-S911B (Samsung Galaxy S23), Android 16 (API 36), ARM64 (aarch64)
App Package: `dev.mowin.app`

## Phase 1, Phase 2, & Phase 3 Classification (Evidence-Based)

PASS (verified on device):
- **H1:** APK builds, installs, runs (targetSdk 28, native-code arm64-v8a).
- **H2:** ProcessRunner / LogStore compiled; commands and exit codes captured and persisted in logs.
- **H3:** Arch Linux ARM64 rootfs (791 MB compressed tar.gz) imported and extracted into app storage (`/data/data/dev.mowin.app/files/runtime/rootfs`), verified `/etc/os-release` and `/usr/bin/pacman`.
- **H4:** PRoot binaries packaged in `jniLibs/arm64-v8a/` (`libproot.so`, `libtalloc.so`, `libandroid-shmem.so`, `libproot-loader.so`, `libproot-loader32.so`) and extracted to `applicationInfo.nativeLibraryDir`. Executed under app UID (`u0_a515`) with `PROOT_NO_SECCOMP=1`.
  - `pacman -Q` succeeds inside PRoot rootfs.
  - File persistence verified (`/root/phase2_test.txt` survives restarts).
  - Network DNS and online repository synchronization verified (`pacman -Sy`).
- **H5:** RuntimeScreen terminal UI connects directly to PRoot execution in `Dispatchers.IO` with live streaming output. Live commands (`ls`, `uname -m`, etc.) verified through interactive screen taps.
- **H6:** Both static (`exit42`) and dynamic (`dynamic42` linked against `/usr/lib/libc.so.6`) ARM64 native executables execute successfully under PRoot inside the app and return exit status 42.
- **H7:** Box64 CPU translation runtime installed at `/usr/local/bin/box64`. Dynamic x86-64 test binary (`hello-x86_64-dynamic`) executed on ARM64 via Box64, prints string, and propagates exit status 42. Benchmarked with Dynarec on (0.712s) vs off (0.725s). Verified through the MoWin Android app terminal UI.

## Phase 4 (Windows Compatibility / Wine & Hangover PE GUI) — UNBLOCKED & PASS

- **Wine GUI Unblocked via Virtual Desktop Container:**
  - Resolved `nodrv_CreateWindow` and `mprotect` restrictions by executing Wine inside a managed virtual desktop container (`wine explorer.exe /desktop=shell,1280x720`).
  - Native ARM64 and x86-64 PE binaries run cleanly with `winex11.drv` inside PRoot Xvfb + VNC server (`:0`).
  - Applications verified running and interactive on device:
    - `notepad.exe` (Win32 GUI text editor)
    - `winemine.exe` (Minesweeper)
    - `solvespace.exe` (SolveSpace v3.3 2D/3D parametric CAD workstation)

## Phase 5 (Touch-First Windows Application Runtime & CAD Workstation) — COMPLETE

- **Gesture Engine & Detection (`app/src/main/java/dev/mowin/app/input/gesture/`):**
  - Multi-touch gesture recognition: Single Tap, Long Press, Two-Finger Tap, One-Finger Drag, Two-Finger Drag, and Multi-touch Pinch-to-Zoom.
  - Profile persistence with JSON backing (`gesture_profiles.json`): includes "Default Desktop", "CAD Workstation", and "Productivity & Coding" profiles with editable action mappings.
  - Interactive Gesture Editor Compose UI with live Test Sandbox mode for safe offline gesture calibration.
- **VNC RFB Input Bridge & Workflow Engine:**
  - Real-time VNC RFB protocol client (`VncInputClient`) dispatching PointerEvents (masks 1, 2, 4, 8, 16) and KeyEvents over localhost socket to X11/Xvfb.
  - Support for multi-step workflow shortcuts (`Ctrl+S`, `Ctrl+Z`, `Ctrl+Y`, `Ctrl+A`, `Esc`, `CAD Zoom Extents`).
  - Wheel zoom integration mapping pinch gesture deltas to RFB Wheel Up / Down packets.
- **Parametric 2D/3D CAD Workstation:**
  - Deployed SolveSpace v3.3 (single-core x86-64 portable build) to `/opt/cad/solvespace.exe` in rootfs.
  - Packaged parametric 3D sample models (`esp32.slvs`, `caddy.slvs`) in `C:\samples\`.
  - Created automated launcher `/usr/local/bin/mowin-cad` that boots display services and initializes SolveSpace with preloaded 3D parts.
  - Added Floating CAD HUD in fullscreen mode with pinned exit button (`✕`), quick CAD buttons (`Esc`, `↶ Undo`, `🔍+ Zoom In`, `🔍- Zoom Out`), and single-line quick launch buttons in card mode.
- **Verification Summary:**
  - 13/13 unit tests pass (`./gradlew testDebugUnitTest` in 4s).
  - Live on-device verification on Samsung Galaxy S23 (`SM-S911B`) across portrait and landscape orientations.
