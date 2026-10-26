# MoWin Post-Sprint Validation Report

Audit date: 2026-10-26
Device: SM-S911B (Samsung Galaxy S23), Android 13, ARM64 (aarch64)
Sprint: 20-hour, v0.1-poc tag pushed

## Phase 1 & Phase 2 Classification (evidence-based)

PASS (verified on device):
- H1: APK builds, installs, runs (targetSdk 28, native-code arm64-v8a).
- H2: ProcessRunner / LogStore compiled; commands and exit codes captured and persisted in logs.
- H3: Arch Linux ARM64 rootfs (791 MB compressed tar.gz) imported and extracted into app storage (`/data/data/dev.mowin.app/files/runtime/rootfs`), verified `/etc/os-release` and `/usr/bin/pacman`.
- H4: PRoot binaries packaged in `jniLibs/arm64-v8a/` (`libproot.so`, `libtalloc.so`, `libandroid-shmem.so`, `libproot-loader.so`, `libproot-loader32.so`) and extracted to `applicationInfo.nativeLibraryDir`. Executed under app UID (`u0_a515`) with `PROOT_NO_SECCOMP=1`.
  - `pacman -Q` succeeds inside PRoot rootfs.
  - File persistence verified (`/root/phase2_test.txt` survives restarts).
- H5: RuntimeScreen terminal UI connects directly to PRoot execution in `Dispatchers.IO` with live streaming output. Live commands (`ls`, `uname -m`, etc.) verified through interactive screen taps.
- H6: Both static (`exit42`) and dynamic (`dynamic42` linked against `/usr/lib/libc.so.6`) ARM64 native executables execute successfully under PRoot inside the app and return exit status 42.

## Phase 2 — COMPLETE
Phase 2 objectives are completely met and verified on hardware:
1. Arch Linux ARM64 rootfs initialized in app storage.
2. PRoot userspace isolation environment operational without root.
3. Interactive shell and terminal execution working.
4. Native ARM64 static and dynamic binary execution validated.

## Next Up: Phase 3 (Cross-architecture execution / Box64)
- H7 Box64 execution: Box64 v0.4.4 bundle downloaded; next gate is executing an x86-64 ELF binary inside the rootfs.
- H8 Wine/Hangover PE: Execution of simple Windows binaries once Box64 library paths are established.
