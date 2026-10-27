# Wine / Hangover Execution Verdict (Phase 4 / Gate H8)

Audit date: 2026-10-26
Device: Samsung Galaxy S23 (SM-S911B), Android 13, ARM64 (aarch64)
Kernel: 5.15.189-android13-8-33413713
Runtime: Arch Linux ARM64 rootfs under PRoot 5.1.107.101

---

## 1. Candidate Evaluated
* **Package:** Hangover 11.16 (`hangover_11.16_debian13_trixie_arm64.tar`)
  * Includes Wine 11.16 (Hangover ARM64 build), wineserver, and `wowbox64.dll` (bridge between Wine WOW64 and Box64 for x86_64 PE execution).
* **Installed Location:** `/opt/hangover/bin/wine`, `/opt/hangover/lib/wine/`

---

## 2. Test Execution & Verified Evidence

### Test 1: Version Check
* **Command:**
  ```bash
  export PATH=/opt/hangover/bin:$PATH
  export WINEDLLPATH=/opt/hangover/lib/wine
  export WINESERVER=/opt/hangover/bin/wineserver
  wine --version
  ```
* **Result (PASS):**
  ```text
  wine-11.16 (Hangover)
  ```
* **Observation:** The Wine frontend, preloader, and ELF dynamic linker successfully initialize and run natively on ARM64.

---

### Test 2: Wineboot & PE Mapping
* **Command:**
  ```bash
  wineboot -u
  # and
  wine cmd.exe /c echo hello-world
  ```
* **Result (BLOCKED at PE memory protection):**
  ```text
  wine: created the configuration directory '/root/.wine'
  002c:err:virtual:map_image_into_view failed to set 60000020 protection on L"\\??\\C:\\windows\\system32\\ntdll.dll" section .text, noexec filesystem?
  002c:err:virtual:map_image_into_view failed to set 60000020 protection on L"\\??\\C:\\windows\\system32\\ntdll.dll" section .hexpthk, noexec filesystem?
  002c:err:virtual:virtual_setup_exception stack overflow 272 bytes addr 0x6fff9d7940 stack 0x77945f0ef0 (0x77945f0000-0x77945f1000-0x77946f0000)
  ```

---

## 3. Syscall & Root Cause Analysis

1. **Failure Point:**
   Inside Wine's `dlls/ntdll/unix/virtual.c` at `map_image_into_view()`:
   ```c
   if (mprotect((char *)base + sec->VirtualAddress, sec->Misc.VirtualSize,
                get_unix_prot(sec->Characteristics)) == -1)
   {
       ERR("failed to set %08x protection on %s section %s, noexec filesystem?\n",
           sec->Characteristics, debugstr_us(&name), sec->Name);
   }
   ```
2. **Mechanism:**
   * Wine maps Windows PE binaries (`ntdll.dll`, `kernel32.dll`) via file-backed memory views (`mmap`).
   * When setting executable permissions on the `.text` and `.hexpthk` sections (`IMAGE_SCN_MEM_EXECUTE | IMAGE_SCN_MEM_READ` = `0x60000020`), `mprotect()` returns `-1` (`EACCES`/`EPERM`).
   * On modern Android, the Linux kernel enforces W^X memory restrictions preventing unprivileged app UIDs from applying `PROT_EXEC` to file-backed mappings originating from app-private data paths (`/data/data/dev.mowin.app/...`).
3. **Contrast with Box64:**
   * In Phase 3, Box64 successfully executed dynamic x86-64 ELF binaries because Box64 allocates memory as `MAP_ANONYMOUS | MAP_PRIVATE`, copies the code, and applies `mprotect(PROT_EXEC)` to the anonymous pages (which Android allows for JITs).
   * Upstream desktop Wine relies directly on file-backed PE section mappings without anonymous copy fallbacks.

---

## 4. What Would Need to Change (Remediation Paths)

1. **Wine Patch (Anonymous Section Staging):**
   Modify Wine's `dlls/ntdll/unix/virtual.c` to detect non-privileged Android container environments: when file-backed `mprotect(PROT_EXEC)` fails with `EACCES`, fallback to allocating `MAP_ANONYMOUS` memory, copying the section payload, and applying `mprotect(PROT_READ | PROT_EXEC)`. This is the exact mechanism utilized in custom Android Wine forks (e.g. Winlator's custom ntdll).
2. **Ashmem/memfd Backing:**
   Redirect Wine's shared file views to use Android's ashmem driver or `libandroid-shmem` wrappers with executable ashmem regions (`ASHMEM_SET_PROT_MASK`).
3. **Wine Prefix in Native Lib Directory:**
   Ship critical PE system DLLs inside the APK's native library path or unpackaged OBB storage where Android allows `PROT_EXEC` file mappings.

---

## 5. Gate H8 Verdict & Sprint 2 Resolution
* **Initial Status:** BLOCKED (Documented with Evidence)
* **Sprint 2 Resolution (2026-10-26):** **RESOLVED & VERIFIED ON TARGET DEVICE** (Samsung Galaxy S23)
  * Implemented and deployed `/usr/local/lib/libmprotect_hook.so` with `SYS_mprotect` anonymous staging fallback.
  * Added `/usr/local/lib/libmprotect_hook.so` to `/etc/ld.so.preload` in rootfs.
  * `wine cmd.exe /c "ver"` outputs:
    ```text
    Microsoft Windows 10.0.19045
    ```
  * `wine hostname.exe` outputs: `LOCALHOST`
  * Execution exits with code `0`. PE mapping blocker is completely resolved.

---

## 6. Windows GUI Subsystem Resolution (`user32.dll` / `KeUserModeCallback`)
* **Status:** **PASS / VERIFIED ON TARGET DEVICE**
* **Root Cause:** Hangover 11.16 `ntdll.so` `load_ntdll_functions()` failed to store the resolved `KiUserCallbackDispatcher` into Unix global `0xa3108`. In `KeUserModeCallback`, `br x3` jumped to `0x0` during window class registration.
* **Fix Applied:** Injected a 6-instruction trampoline via NOP sled at `0xc2a8` hooking `0x29e80` to populate `pKiUserCallbackDispatcher` (`0xa3108`) and `pKiUserCallbackDispatcherReturn` (`0xa3110`).
* **Verified Evidence:**
  - `notepad.exe`: Process launches, registers window classes, enters message loop, and renders a 676x486 window to the X11 framebuffer (`/tmp/Xvfb_screen0`, bounding box `(4, 23, 680, 509)`). Zero SEH crashes.
  - `winemine.exe`: Process launches and renders minesweeper grid and UI controls (`(3, 22, 649, 369)` with 10 unique colors).
  - All regression tests (`hello_arm64.exe`, `hello_x64.exe`, `cmd.exe /c ver`) continue to exit with status 0.
