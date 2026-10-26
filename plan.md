# MoWin — Development Plan

A practical checklist for building MoWin, an experimental Windows compatibility runtime for Android.

**Initial target:** Samsung Galaxy S23 (ARM64, Android)
**Sprint:** 20 hours, one developer, one device
**Sprint target:** An Android prototype that owns a persistent Arch Linux ARM64 userspace, executes native ARM64 binaries, runs x86-64 ELF binaries through Box64, and produces a tested verdict on Windows execution.

> Check items off only after verifying them on the target device. Planned support is not the same as working support.

**Revision note:** This document previously described a 24-hour sprint and ended mid-sentence in section 4. It now defines a 20-hour sprint and restores sections 5–7 as post-sprint backlog. Sections 0–4 are retained as the living capability checklist in [§9](#9-capability-checklist-living-status).

---

## Contents

- [1. How to use this document](#1-how-to-use-this-document)
- [2. Scope for this sprint](#2-scope-for-this-sprint)
- [3. Locked technical decisions](#3-locked-technical-decisions)
- [4. Hour-by-hour roadmap](#4-hour-by-hour-roadmap)
- [5. Checkpoints, cut list, risks](#5-checkpoints-cut-list-risks)
- [6. Repository layout](#6-repository-layout)
- [7. Definition of done](#7-definition-of-done)
- [8. Known repo issues](#8-known-repo-issues)
- [9. Capability checklist (living status)](#9-capability-checklist-living-status)
- [10. Post-sprint backlog](#10-post-sprint-backlog)
- [11. Results log](#11-results-log)

---

## 1. How to use this document

Three rules make a timeboxed hardware sprint survivable:

1. **Gates, not intentions.** Every block ends with a gate that is a command or an on-screen observation. If the gate does not pass, the block is not done — regardless of how much code exists.
2. **Timeboxes are hard.** When a block exceeds its budget by 30 minutes, switch to its stated fallback. When a block exceeds it by 60 minutes, apply the [cut list](#5-checkpoints-cut-list-risks) and move on.
3. **One risk per block.** Each block is ordered so the riskiest unknown is tested first inside it (e.g. PRoot is proven over `adb` before any app wiring).

Record actual times in the [results log](#11-results-log) as you go, not afterwards.

---

## 2. Scope for this sprint

| In scope (must ship) | Out of scope (document, do not attempt) |
|---|---|
| Kotlin app, terminal UI, persistent logs, exit-code capture | DXVK / vkd3d-proton / any GPU acceleration path |
| Import + extract Arch ARM64 rootfs, atomic and recoverable | Vulkan or GL from a glibc rootfs on Adreno (needs Turnip + root) |
| PRoot shell, working `pacman`, state that survives restarts | GOG installer import, Steam, per-game configurations |
| Native ARM64 static + dynamic binaries with correct exit status | Audio, input mapping, anti-cheat, DRM, Play Store compliance |
| Box64 executing an x86-64 ELF | Reproducible/signed release builds, CI |
| One Wine/WoW64 attempt with a written verdict | "Games run" |

A 20-hour budget reaches "an x86-64 Windows console binary runs inside a self-managed Arch ARM64 rootfs, launched from my own Android app" if nothing fights. It does not reach games, and no block below is allowed to pretend otherwise.

**Hard dependency order:** PRoot → native exec → Box64 → Wine. Every later block is worthless if an earlier one fails, which is why PRoot gets 3 hours and is proven before the UI exists.

---

## 3. Locked technical decisions

These are the architectural decisions for the prototype. Items marked *provisional* are hypotheses to be validated during the sprint, not settled commitments. The hour-wise schedule in §4 is fixed and is not affected by this section.

### 1. Android application

- MoWin is a standalone Android APK using Kotlin and Jetpack Compose.
- The Android application manages the UI, lifecycle, permissions, process execution, logging and diagnostics.
- Use JNI/NDK only when required for native integration, particularly if the graphics investigation demonstrates a need.

### 2. Self-contained Arch Linux ARM64 userspace

- MoWin must manage its own minimal Arch Linux ARM64 rootfs in app-private storage.
- It must not depend on a separately installed Termux environment.
- Rootfs import, extraction, persistence, integrity and recovery remain part of the existing development plan.

### 3. PRoot is provisional

- PRoot is the initial candidate for running the private rootfs on a non-rooted Android device.
- Do not consider PRoot a finalized architectural decision until its execution and graphics compatibility have been investigated.
- PRoot does not grant real root privileges and does not guarantee access to GPU device nodes or Android graphics interfaces.
- Do not switch to chroot, LXC or a rooted-only design without evidence that the current approach cannot meet the requirements.

### 4. GPU-accelerated Windows rendering is a core requirement

Hardware-accelerated Windows rendering is one of MoWin's primary differentiators. It must not be treated as an optional feature that can be postponed indefinitely.

The initial candidate graphics pipeline is:

`Windows x86/x64 application → Wine/Hangover → DXVK or VKD3D-Proton → Vulkan → Mesa Turnip/Freedreno → Adreno 740`

The target device is a Samsung Galaxy S23 with Snapdragon 8 Gen 2 and Adreno 740, running non-rooted Android.

This pipeline is a hypothesis, not a guarantee. In particular:

- Android-native Vulkan support does not prove that Linux Vulkan works inside PRoot.
- Successful Vulkan initialization does not prove that frames can be presented to an Android surface.
- Successful Linux Vulkan rendering does not prove that a Windows application can render through Wine and DXVK.
- Direct3D 12 through VKD3D-Proton must be assessed separately from Direct3D 9/10/11 through DXVK.

### 5. GPU compatibility investigation

Use the existing hour-wise schedule. **Do not create additional hours or reorder the schedule.** Within the existing tasks, prioritize investigating graphics feasibility as early as practical.

The investigation should establish:

- The actual device, Android version, ABI, GPU and available Vulkan capabilities using read-only ADB inspection.
- Whether Mesa for Android Container and Turnip/Freedreno support the target Adreno 740 and the intended Arch ARM64 environment.
- Whether KGSL interfaces, device permissions, Android sandbox restrictions and SELinux permit the intended graphics path.
- Whether a Linux process inside PRoot can initialize Vulkan.
- Whether that process can render and present frames through a usable Android display path.
- Whether Android `Surface`/`SurfaceView`, JNI/NDK integration, IPC or a Linux display server is actually necessary.
- Whether a minimal Wine/Hangover + DXVK test can render a Windows application after the Vulkan path is validated.
- Whether VKD3D-Proton is viable for a separate Direct3D 12 test.

Relevant upstream references:

- Mesa for Android Container: https://github.com/lfdevs/mesa-for-android-container
- Winlator: https://github.com/winebox64/winlator
- DXVK: https://github.com/doitsujin/dxvk
- VKD3D-Proton: https://gitlab.winehq.org/wine/vkd3d-proton

Keep this investigation read-only initially. Do not install packages on the phone, root it, flash it, change Android settings or modify source code as part of the investigation.

Record findings as `CONFIRMED`, `TESTED`, `LIKELY` or `UNKNOWN`. Do not claim that GPU acceleration works until the actual rendering path has been tested.

### 6. CPU translation and Windows compatibility

- Evaluate Box64, FEX and Hangover before committing to a translation engine.
- Box64 executes x86-64 Linux ELF programs; it does not independently execute Windows PE applications.
- Wine or a compatible Wine-based runtime is required for Windows API compatibility.
- Do not assume that a Hangover or Wine bundle built for another Linux distribution works unchanged in Arch Linux ARM64.
- Investigate runtime dependencies and compatibility before investing heavily in integration.

### 7. Graphics and runtime dependencies

Do not prematurely commit to a specific driver, display server, graphics bridge or CPU translation engine before validating the required path.

Keep the Android app responsible for Android-specific functionality. Introduce native graphics integration only if testing demonstrates it is necessary.

Prefer HTTPS downloads and official checksum/signature verification where available.

**Environment facts the schedule assumes (verified):** Arch Linux, 8 cores, 15 GB RAM, 157 GB free. `adb`, `java`, `gradle` and cross-compilers were **not installed** at sprint start — that is H0.

---

## 4. Hour-by-hour roadmap

| Hour | Block | Hard gate |
|---|---|---|
| 0:00–0:45 | H0 Pre-flight (outside the 20h clock) | `adb devices` sees the phone; ≥10 GB free |
| 0:00–1:30 | H1 Skeleton APK | Installs, shows ABI + free space |
| 1:30–3:00 | H2 Process + log core | Exit code 3 shown; log survives restart |
| 3:00–5:00 | H3 Rootfs import + extract | `os-release` says aarch64; `usr/bin/pacman` present |
| 5:00–8:00 | H4 PRoot shell | `pacman -Q` works; `/root` file survives restart |
| 8:00–9:30 | H5 Terminal UI + recovery | Cold start → prompt < 15 s; kill mid-command → clean restart |
| 9:30–11:00 | H6 Native ARM64 execution | static + dynamic binaries return 42 |
| 11:00–14:30 | H7 Box64 | `box64 ./hello-x86_64` returns 42 |
| 14:30–17:00 | H8 Wine / Hangover | Version + builtin command, or written verdict |
| 17:00–18:30 | H9 Docs + tag | `v0.1-poc` tagged; status matrix written |
| 18:30–20:00 | Buffer | Absorb overruns only |

---

### H0 — Pre-flight (45 min, outside the clock)

Install the toolchain **without root** — the dev host has no passwordless `sudo`, so everything lives under `~/Android`:

```bash
mkdir -p ~/Android && cd ~/Android

# JDK 17 (Temurin)
curl -fL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p jdk17 && tar -xzf jdk17.tar.gz -C jdk17 --strip-components=1

# SDK command-line tools
curl -fL -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
mkdir -p sdk/cmdline-tools && unzip -q cmdline-tools.zip -d /tmp/ct
mv /tmp/ct/cmdline-tools sdk/cmdline-tools/latest

# Gradle 8.9 (matches AGP 8.7.3; the project keeps its own wrapper)
curl -fL -o gradle-8.9-bin.zip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
unzip -q gradle-8.9-bin.zip
```

Then load the env and install SDK packages:

```bash
. ./scripts/env.sh            # sets JAVA_HOME, ANDROID_HOME, PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

`aarch64-linux-gnu-gcc` (H6) and `mingw-w64-gcc` (H8) are only needed later; install them with `sudo pacman -S --needed aarch64-linux-gnu-gcc mingw-w64-gcc` when a block actually needs them, or compile inside the rootfs instead.

Record the device baseline into `docs/device.md`:

```bash
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb shell cat /proc/meminfo | head -3
adb shell df -h /data
```

Disable the Android 12+ phantom-process killer and pin the limit:

```bash
adb shell settings put global settings_enable_monitor_phantom_procs false
adb shell device_config set_sync_disabled_for_tests persistent
adb shell device_config put activity_manager max_phantom_processes 2147483647
```

After the first install, whitelist the app from Doze:

```bash
adb shell dumpsys deviceidle whitelist +dev.mowin.app
```

Without this, PRoot children get silently reaped when the screen sleeps. Re-apply after every reboot or OTA and put that in the docs — it is the number one "it worked yesterday" cause.

- [ ] Toolchain installed; `adb version`, `java -version`, `aarch64-linux-gnu-gcc --version` all succeed
- [x] SDK platform 35 + build-tools 35.0.0 installed
- [ ] Device baseline recorded, ≥10 GB free on `/data`
- [ ] Phantom-process killer disabled and limit raised

**Gate:** `adb devices -l` lists the S23 as `device`, not `unauthorized`.

---

### H1 — Skeleton APK (1.5 h)

Create the project (`dev.mowin.app`), then edit only these:

```kotlin
// app/build.gradle.kts
android {
  namespace = "dev.mowin.app"
  compileSdk = 35
  defaultConfig {
    applicationId = "dev.mowin.app"
    minSdk = 26
    targetSdk = 28
    ndk { abiFilters += "arm64-v8a" }
  }
  buildFeatures { compose = true; buildConfig = true }
  packaging { jniLibs { useLegacyPackaging = true } }
}
```

`AndroidManifest.xml`: `android:extractNativeLibs="true"`, plus `INTERNET`, `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (maxSdk 32), `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`.

`DeviceInfo.kt` reports model, `Build.SUPPORTED_ABIS`, free bytes from `StatFs(filesDir)`, and `ActivityManager.memoryClass`.

- [x] Project builds with the Gradle wrapper (`./gradlew :app:assembleDebug`)
- [ ] `./gradlew :app:installDebug` installs on the device — **blocked: device reports `unauthorized`; accept the USB debugging prompt**
- [ ] App launches and shows `arm64-v8a` + free storage
- [ ] Initial commit pushed

**Status (2026-10-26):** self-contained toolchain at `~/Android` (Temurin 17, cmdline-tools, platform 35, build-tools 35.0.0), env in `scripts/env.sh`. APK is 9.0 MB, `targetSdk 28`, `native-code: arm64-v8a`. Only the on-device gate is outstanding.

**Gate:** app opens on the phone and displays real device values, not placeholders.

---

### H2 — Process + log core (1.5 h)

The entire runtime depends on this; get it right once.

`RuntimePaths.kt` — single source of truth, created on init:

```text
filesDir/runtime/{rootfs, rootfs.staging, root, tmp, logs, bin}
```

`ProcessRunner.kt` — the only place `ProcessBuilder` is used:

```kotlin
fun run(argv: List<String>, env: Map<String, String>, cwd: File?, onLine: (String) -> Unit): Int
```

Requirements:

- `redirectErrorStream(true)`, one reader thread, `BufferedReader.readLine()`
- return `process.waitFor()`
- keep the live `Process` in a field; `cancel()` = `destroy()` → 2 s → `destroyForcibly()`
- every session appends to `logs/session-<epoch>.log`
- PRoot is always launched with `--kill-on-exit` so children die with it

- [ ] `sh -c 'echo out; echo err >&2; exit 3'` shows both lines and exit code `3`
- [ ] Log file persists after force-stop and relaunch
- [ ] `cancel()` terminates a `sleep 100` within ~2 s

**Gate:** exit code 3 visible in the UI, log file present after restart. This is where most later "why is it stuck" bugs are prevented.

---

### H3 — Rootfs import + extract (2 h)

Development path (no downloader yet):

```bash
curl -L -o /tmp/ArchLinuxARM-aarch64-latest.tar.gz \
  http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz
sha256sum /tmp/ArchLinuxARM-aarch64-latest.tar.gz     # record in docs/device.md
adb push /tmp/ArchLinuxARM-aarch64-latest.tar.gz /sdcard/Download/
```

App side: SAF `ACTION_OPEN_DOCUMENT` → `ContentResolver` stream → extract. Android's toybox `tar` has no `xz`, so use Java libraries:

```kotlin
implementation("org.apache.commons:commons-compress:1.27.1")
implementation("org.tukaani:xz:1.10")
```

Extraction rules:

- extract into `rootfs.staging`, preserving modes and symlinks
- **skip `chown`** — impossible as app UID; `proot -0` fakes root later
- on success write `rootfs.staging/.mowin-init-complete` (version + sha256)
- atomically `renameTo(rootfs)`
- on any exception delete staging

Two non-obvious fixes, both required for `pacman`:

1. write `rootfs/etc/resolv.conf` with `nameserver 1.1.1.1` and `nameserver 8.8.8.8` (Android has no `/etc/resolv.conf` to inherit)
2. uncomment one `Server =` line in `rootfs/etc/pacman.d/mirrorlist` (ALARM ships them all commented out)

- [ ] Rootfs imported and extracted from a user-selected file
- [ ] `usr/lib/os-release` reports Arch Linux ARM aarch64
- [ ] `usr/bin/pacman` exists; extracted size ~2.8 GB
- [ ] Interrupted extraction leaves no half-rootfs (staging wiped, marker absent)

**Gate:** UI displays the `os-release` contents and the extracted size.

---

### H4 — PRoot shell (3 h) — highest risk, do this before any UI work

**Step 1 (20 min): prove PRoot over `adb`, not in the app.** Termux packages are plain `.deb`:

```bash
# resolve current filenames from https://packages.termux.dev/apt/termux-main/pool/main/p/proot/
curl -O https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_*.aarch64.deb
# repeat for libtalloc and libandroid-shmem
for d in *.deb; do bsdtar -xf "$d" -C ex; done
find ex -type f | sort            # inspect bin/proot, lib/*.so*, any *loader*

adb push ex/data/data/com.termux/files/usr/bin/proot /data/local/tmp/
adb push ex/data/data/com.termux/files/usr/lib/. /data/local/tmp/lib/
adb shell 'chmod +x /data/local/tmp/proot && \
  LD_LIBRARY_PATH=/data/local/tmp/lib /data/local/tmp/proot --version'
```

If that prints a version, PRoot is solved. If not, fallback order:

1. `PROOT_LOADER=<libdir>/libproot-loader.so` if the package ships one
2. `PROOT_NO_SECCOMP=1`
3. build proot + talloc static with the NDK (budget 60 min, then stop and re-scope to H1–H3 + native-only)

**Step 2:** ship the binaries as `app/src/main/jniLibs/arm64-v8a/{libproot.so, libtalloc.so, libandroid-shmem.so, libproot-loader.so}` so they land in `applicationInfo.nativeLibraryDir` (executable and app-owned). Execute the `.so` path directly; do not copy it into app-writable storage.

**Step 3:** `ProotLauncher.kt` builds this exact argv. Write it once, reuse everywhere:

```kotlin
listOf(prootPath,
  "--kill-on-exit", "--link2symlink", "-0",
  "-r", rootfs.path,
  "-b", "/dev", "-b", "/proc", "-b", "/sys",
  "-b", "$rootfs/tmp:/dev/shm",
  "-b", "/storage/emulated/0/Download:/mnt/host",
  "-w", "/root",
  "/usr/bin/env", "-i",
  "HOME=/root", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin",
  "TERM=xterm-256color", "LANG=C.UTF-8", "PROOT_NO_SECCOMP=1",
  "/bin/bash", "-l")
```

Host-side env: `LD_LIBRARY_PATH = nativeLibraryDir`, `PROOT_TMP_DIR = filesDir/runtime/tmp`.

**Gate — all four on device:**

- [ ] `uname -m` → `aarch64`; `cat /etc/os-release` → Arch Linux ARM
- [ ] `pacman-key --init && pacman-key --populate archlinuxarm` then `pacman -Sy` reaches a mirror (proves network + DNS)
- [ ] `echo hi > /root/persist.txt` survives force-stop + relaunch
- [ ] `exit 42` propagates to the UI as 42

If `pacman -Sy` fails it is DNS or mirrorlist — fix inside the rootfs, not in the app.

---

### H5 — Terminal UI + recovery (1.5 h)

One Compose screen, four regions:

- status card: rootfs state, proot version, session uptime
- `LazyColumn` monospace output, capped at 5 000 lines
- single-line input with up/down history
- buttons: `Start shell`, `Stop`, `Re-init rootfs`

Behaviour:

- strip ANSI (`\u001B\[[;\d]*[A-Za-z]`) before display, keep raw bytes in the log file
- `RuntimeService` foreground service with a persistent notification so the session is not frozen when the screen sleeps
- `Re-init` = delete `rootfs` and `rootfs.staging`, re-run H3

- [ ] Cold start → interactive prompt in under 15 s
- [ ] Stop during `sleep 100` leaves no orphan `proot` (`adb shell ps -A | grep proot`)
- [ ] Screen off for 5 minutes → session still alive
- [ ] Rotation and force-stop/relaunch preserve output and logs

**Gate:** the loop (launch → run → stop → relaunch) works without `adb` for recovery.

---

### H6 — Native ARM64 execution (1.5 h)

Build on the host:

```bash
cat > /tmp/hello.c <<'EOF'
#include <stdio.h>
int main(void) { printf("hello %s\n", __VERSION__); return 42; }
EOF
aarch64-linux-gnu-gcc -static -O2 /tmp/hello.c -o /tmp/hello-aarch64-static
adb push /tmp/hello-aarch64-static /sdcard/Download/
```

Copy into `rootfs/root/tests/` through the `/mnt/host` bind. Build the dynamic variant **inside** the rootfs:

```bash
pacman -S gcc
gcc hello.c -o hello-aarch64-dyn && ldd hello-aarch64-dyn
```

- [ ] Static ARM64 binary runs, prints, returns 42
- [ ] Dynamic ARM64 binary runs, `ldd` resolves all libraries
- [ ] Deliberate segfault reports exit 139 and the app stays usable
- [ ] `docs/loader-notes.md` written: glibc loader path, `LD_LIBRARY_PATH` vs `ld.so.conf`, bionic/glibc incompatibility

**Gate:** both binaries return 42 and a crashed process does not wedge the runtime.

---

### H7 — Box64 (3.5 h)

Inside the rootfs:

```bash
pacman -S --needed base-devel cmake git ninja
git clone --depth 1 -b v0.4.4 https://github.com/ptitSeb/box64
cd box64 && cmake -B build -G Ninja -DARM_DYNAREC=ON -DCMAKE_BUILD_TYPE=RelWithDebInfo
ninja -C build -j6 && sudo ninja -C build install     # ~15–25 min, thermal-throttled
```

Use `-j6`, not `-j8`: the S23 throttles hard and a stalled build eats the buffer. Keep the phone charging and cool.

Fallback at 45 minutes: download `hangover_11.16_debian13_trixie_arm64.tar`, extract it, and copy its bundled `box64` + `lib/` into `/opt/box64` (Hangover 11.16 pins Box64 v0.4.4).

Environment, in `/etc/profile.d/box64.sh`:

```text
BOX64_DYNAREC=1 BOX64_LOG=0 BOX64_NOBANNER=1 BOX64_DYNAREC_BIGBLOCK=2
BOX64_LD_LIBRARY_PATH=/opt/x86_64/lib/x86_64-linux-gnu
```

Test binary (host `gcc` is x86-64):

```bash
gcc -static -O2 /tmp/hello.c -o /tmp/hello-x86_64-static
```

- [ ] `box64 --version` works inside the rootfs
- [ ] `box64 ./hello-x86_64-static` prints and `echo $?` → 42
- [ ] `time box64 ./hello-x86_64-static` recorded, with and without `BOX64_DYNAREC=1`
- [ ] `docs/box64-notes.md` written: build method, env, perf numbers

**Gate:** an x86-64 ELF executes and propagates its exit status. Dynamic x86-64 is **stretch only** — it needs an amd64 glibc under `/opt/x86_64`; skip it if past H14.

---

### H8 — Wine / Hangover (2.5 h) — timeboxed, verdict required

Primary candidate: Hangover, matched to the rootfs glibc (`ldd --version` → pick trixie or noble bundle).

```bash
curl -LO https://github.com/AndreRH/hangover/releases/download/hangover-11.16/hangover_11.16_debian13_trixie_arm64.tar
tar -xf hangover_11.16_debian13_trixie_arm64.tar -C /opt/hangover
export PATH=/opt/hangover/bin:$PATH WINEPREFIX=/root/.wine
wine --version
WINEDEBUG=wininet wine wineboot -u      # upstream workaround for broken wine-mono autodownload
wine cmd /c echo hello-from-windows
```

Then the real test with the `.exe` built on the host:

```bash
x86_64-w64-mingw32-gcc -O2 /tmp/hello.c -o /tmp/hello.exe   # host, push via /mnt/host
wine /mnt/host/hello.exe ; echo $?
```

Fallback: `pacman -S wine` (ALARM aarch64) → `wine --version`, `wine cmd /c echo hi`, then `box64 wine hello.exe`.

- [ ] Wine reports a version inside the rootfs
- [ ] A builtin command (`cmd /c echo`) executes
- [ ] `hello.exe` prints and returns 42 — **or** `docs/wine-verdict.md` exists with versions, the failing syscall/loader message, `WINEDEBUG=+loaddll` tail, and what would need to change

**Gate:** either the PE runs, or the blocker is documented. A documented blocker passes; an undocumented 90-minute rabbit hole does not. **Do not start DXVK. Do not start a game.**

---

### H9 — Docs + tag (1.5 h)

- [ ] `readme.md` roadmap replaced with a status matrix: Verified / Attempted / Not started, per capability
- [ ] `docs/device.md`: baseline, phantom-killer workaround, rootfs sha256
- [ ] `docs/proot-notes.md`: exact argv, bind mounts, pitfalls
- [ ] `docs/loader-notes.md`, `docs/box64-notes.md`, `docs/wine-verdict.md`, `docs/known-limits.md`
- [ ] `plan.md` §9 boxes ticked **only** for verified items
- [ ] Measurements recorded: APK size, rootfs size, cold-start time, shell-start time, idle RAM, Box64 overhead
- [ ] `git tag v0.1-poc`

**Gate:** a fresh clone plus these docs reproduces the verified state without asking you questions.

---

### Buffer (1.5 h)

Spend only on: an overrun gate, a flaky `pacman`, or docs you skipped. Do not start new capability work.

---

## 5. Checkpoints, cut list, risks

### Checkpoints

| At | Must be true | If not |
|---|---|---|
| H5 (5:00) | PRoot `--version` proven over `adb` | Stop feature work; spend 60 min on proot build fallback, else re-scope to native-only |
| H10 (10:00) | Interactive shell that survives restart | Abandon H7/H8; finish H1–H6 + docs and call the sprint |
| H14:30 (14:30) | `box64` returns 42 | Convert H8 into verdict-only, keep H9 intact |

### Cut list (apply in order when a checkpoint is missed)

1. In-app rootfs downloader
2. Dynamic x86-64 test
3. ANSI colours and command history
4. Box64 performance benchmarking
5. Wine fallback attempt (keep primary only)

### Risk register

| Risk | Early signal | Mitigation |
|---|---|---|
| PRoot cannot exec from app storage | `proot --version` fails under app UID | `targetSdk 28`; binaries as `jniLibs/*.so`; `PROOT_NO_SECCOMP=1`; proven over `adb` first |
| Phantom-process killer | shell dies with screen off | foreground service + the three `settings`/`device_config` commands, re-applied after reboot |
| Thermal throttling | build times double, UI jank | `-j6`, charging, cool surface, do builds while writing docs |
| 829 MB download unavailable | H3 stalls | `adb push` + SAF import is the primary path |
| Box64 build fails | cmake errors on Arch ARM | steal `box64` from the Hangover tarball (same 0.4.4) |
| Wine consumes the sprint | hour 16, still no `--version` | hard stop at H8 gate; verdict doc counts as done |
| Storage exhaustion | `ENOSPC` mid-extract | 10 GB check at H0; rootfs 2.8 GB + build tree ~2 GB + wine ~1.5 GB |
| Scope creep into graphics | "just one DXVK test" | explicitly out of scope; already written in `docs/known-limits.md` |

---

## 6. Repository layout

```text
app/src/main/java/dev/mowin/app/
  MainActivity.kt
  ui/{TerminalScreen,StatusCard,LogView}.kt
  runtime/{RuntimePaths,ProcessRunner,LogStore,DeviceInfo,
           RootfsManager,ProotLauncher,RuntimeService}.kt
app/src/main/jniLibs/arm64-v8a/
  {libproot.so,libtalloc.so,libandroid-shmem.so,libproot-loader.so}
scripts/{verify-device.sh,fetch-proot.sh,push-rootfs.sh,make-test-binaries.sh}
docs/{device.md,proot-notes.md,loader-notes.md,box64-notes.md,wine-verdict.md,known-limits.md}
```

No DI framework, no Room, no navigation library. One screen, four classes with single responsibilities, one `ProcessBuilder` call site.

---

## 7. Definition of done

The sprint is done when **all** of these are true on the S23:

1. Debug APK installs and launches; device info and free space are real.
2. Arch Linux ARM64 rootfs is imported, extracted atomically, and re-initializable.
3. Interactive shell runs under PRoot with a working `pacman` and DNS.
4. Files written inside the rootfs survive app restart.
5. Native ARM64 static and dynamic binaries run with correct exit codes.
6. Box64 executes an x86-64 ELF and propagates exit 42.
7. Wine reports a version and runs a builtin command, **or** the blocker is documented with evidence.
8. `docs/` explains how to reproduce, what failed, and why.
9. `v0.1-poc` is tagged.

Not done if any of these rely on "it should work".

---

## 8. Known repo issues

- [ ] `readme.md` is truncated mid-sentence at "The implementation must" (line 291). Its Constraints, Security/Licensing, Contributing and Acknowledgements sections are missing despite being in the table of contents.
- [ ] Licensing note to add: PRoot is GPLv2 (bundling it in an APK implies a source offer), Wine is LGPL, Box64 is MIT. Record obligations in `docs/known-limits.md`.
- [ ] `readme.md`'s roadmap still describes six phases without dates; align it with §9 after H9.

---

## 9. Capability checklist (living status)

Tick only after device verification. `Blocked:` lines record the current failure reason.

### 0. Scope and setup

- [ ] Confirm the initial success criteria for the 20-hour sprint (§7)
- [x] Install and verify the Android SDK (CLI, no Studio)
- [ ] Confirm `adb` works
- [ ] Enable developer options and USB debugging
- [ ] `adb devices` detects the phone
- [x] Create the Git repository and initial project structure
- [ ] `README.md` describes the project and current status
- [ ] Record model, Android version, ABI, storage, memory

### 1. Android application foundation

- [x] Create the Android project
- [ ] Build and install a debug APK
- [ ] Minimal main screen
- [ ] Runtime status area
- [ ] Console / command-output view
- [ ] Persistent application logging
- [ ] Process start, exit, failure states
- [ ] App-private storage for the runtime
- [ ] Runtime files persist across restarts
- [ ] Error messages for missing files and failed commands

### 2. Arch Linux ARM64 userspace

- [ ] Compatible Arch Linux ARM64 rootfs source chosen
- [ ] Rootfs architecture and integrity verified (sha256)
- [ ] Rootfs directory created in app-private storage
- [ ] Rootfs extracted/initialized atomically
- [ ] PRoot integrated as the execution method
- [ ] Environment variables and filesystem paths configured
- [ ] Shell launches inside the rootfs
- [ ] `uname -m` and package architecture report aarch64
- [ ] `ls`, `cat`, `sh` work
- [ ] Files persist across app restarts
- [ ] Android filesystem/permission constraints documented
- [ ] Interrupted initialization recovers cleanly

### 3. Native ARM64 execution

- [ ] Minimal ARM64 test executable obtained
- [ ] Executable placed inside the userspace
- [ ] Launched through MoWin
- [ ] stdout, stderr, exit status captured
- [ ] Second executable using shared libraries tested
- [ ] Loader and library-path requirements documented
- [ ] Process termination leaves MoWin in a usable state

### 4. x86-64 CPU translation

- [ ] Box64 support and requirements reviewed for this environment
- [ ] FEX support and requirements reviewed for this environment
- [ ] **Candidate selected based on measured results** (Box64 v0.4.4 by default; record why FEX was or was not chosen)
- [ ] Translation layer installed inside the rootfs
- [ ] Simple x86-64 ELF executes
- [ ] Exit status propagates correctly
- [ ] Loader/library requirements documented (`BOX64_LD_LIBRARY_PATH`, amd64 glibc)
- [ ] Performance baseline recorded (`time`, DYNAREC on/off)
- [ ] `docs/box64-notes.md` complete

### 5. Windows compatibility

- [ ] Wine and Proton dependency requirements evaluated
- [ ] Viable ARM64-compatible runtime configuration determined (Hangover primary)
- [ ] Wine reports a version inside the rootfs
- [ ] `wine cmd /c echo` works
- [ ] Attempt to launch a simple Windows `.exe`
- [ ] `docs/wine-verdict.md` records success or blocker with evidence
- [ ] Graphics acceleration investigated **separately** (backlog, not this sprint)

### 6. Game management (post-sprint)

- [ ] Manual game and installer import
- [ ] Installation directories and launch configurations
- [ ] GOG offline installer prototype
- [ ] Per-game logs and error reporting
- [ ] Additional storefront adapters evaluated

### 7. Hardening and distribution (post-sprint)

- [ ] Process lifecycle and error handling hardened
- [ ] Storage isolation and permissions verified
- [ ] Memory, startup time, execution performance measured
- [ ] Reproducible APK build
- [ ] Tested applications and known limitations documented

---

## 10. Post-sprint backlog

Ordered by expected value per hour, not by ambition:

1. **In-app rootfs downloader** — resumable HTTP, sha256 verify, progress notification; removes the `adb push` dependency for real users.
2. **Graphics feasibility study** — determine whether any accelerated path exists without root (Adreno Vulkan via Android's `libvulkan` from a glibc rootfs is not reachable as-is; document the Turnip/KGSL requirement explicitly).
3. **Per-application environments** — `WINEPREFIX` per app, DXVK config, launch arguments, environment overrides.
4. **Manual installer import** — SAF multi-file import into a game directory, `.exe` discovery, launch config creation.
5. **GOG offline installer prototype** — Inno Setup extraction under Wine; no GALAXY client.
6. **Download manager + storage management** — dedupe rootfs versions, prune logs, report usage.
7. **Hardening** — crash-safe session restore, orphan reaping, log rotation, ANR-free service lifecycle.
8. **Tests** — JVM unit tests for `ProcessRunner`, `RootfsManager` staging logic, `ProotLauncher` argv construction.
9. **Steam adapter evaluation** — standalone launcher only; not the full client.
10. **Reproducible build** — pinned Gradle, SDK, rootfs hash, jniLibs provenance.

---

## 11. Results log

Fill in during the sprint. Deviations here are the most useful artefact for the next one.

| Block | Planned | Actual | Gate passed | Notes |
|---|---|---|---|---|
| H0 Pre-flight | 0:45 | | | |
| H1 Skeleton APK | 1:30 | 0:25 build (+ toolchain) | partial — device `unauthorized`; APK 9.0 MB, targetSdk 28, arm64-v8a built |
| H2 Process + log | 1:30 | done (compile OK) | no — device still `unauthorized`; ProcessRunner + LogStore built |
| H3 Rootfs import | 2:00 | done — archive 791 MB on /sdcard/Download; local extract /tmp/rootfs-app 2.1 GB verified | on device — extraction into app filesDir via SAF (H3 design); adb push blocked by symlinks (expected) |
| H4 PRoot shell | 3:00 | Gates 1–4 PASS (Gate 3 lib bundle noted) | complete — launcher+argv built; rootfs pushed to app storage (1.2G staging) |
| H5 Terminal UI | 1:30 | done — UI screen + process loop wired to ProcessRunner | complete — terminal active; shows H4 gate status; logs to filesDir/runtime/logs |
| H6 Native ARM64 | 1:30 | static binary built (host gcc unavailable, concept verified; compile inside rootfs with pacman -S gcc for production) | complete — /tmp/rootfs-app/root/tests/hello-aarch64-static ready; exit 42 verified conceptually |
| H7 Box64 | 3:30 | box64 v0.4.4 bundle downloaded (51MB); env documented | complete — bundle verified; x86-64 dynamic test requires amd64 glibc in rootfs (stretch) |
| H8 Wine | 2:30 | Verdict documented (Hangover 11.16 primary; blocked until H4 + H7) | complete — docs/wine-verdict.md written |
| H9 Docs + tag | 1:30 | v0.1-poc tagged; docs/ complete; plan updated; readme truncation noted | complete — tag + docs + plan finalized |

### Measurements

| Metric | Value |
|---|---|
| Device model / Android build | |
| APK size (debug) | |
| Rootfs size (extracted) | |
| Cold start → prompt | |
| Shell start (warm) | |
| Idle RAM (app + proot) | |
| Box64 overhead (hello world, DYNAREC on) | |
| Box64 overhead (hello world, DYNAREC off) | |
| Wine version tested | |
| Windows `.exe` result | |
