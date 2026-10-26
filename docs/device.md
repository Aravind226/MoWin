# Device Baseline (verified 2026-10-26)

Model: SM-S911B (Galaxy S23) — Android 13 (5.15.189-android13-8-33413713)
ABI: aarch64 — 8 GB RAM (memoryClass ~1024 MB reported by ActivityManager)
Storage: /data ~85 GB used / 104 GB total (19 GB free)
ADB: authorized (device, not unauthorized)
Phantom-process killer: disabled (settings + device_config applied)
Rootfs sha256 (ArchLinuxARM-aarch64-latest.tar.gz): 42a4eeaa... (verified)

Notes:
- USB debugging prompt accepted (this is why adb commands now work).
- Rootfs delivered to /sdcard/Download/moWin-rootfs.tar.gz (829 MB); app extracts to filesDir/runtime/rootfs.staging.
- PRoot binaries to be shipped via jniLibs/arm64-v8a/ (not /data/local/tmp — avoids permission/symlink issues).
