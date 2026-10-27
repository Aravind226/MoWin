# Box64 Cross-Architecture Translation Notes

## 1. Candidate Selection: Box64 vs FEX
* **Box64 (Selected):**
  * Fully runs in userspace without requiring root, kernel modules, or `binfmt_misc` registration.
  * Features native ARM64 dynamic recompiler (Dynarec) with support for ARMv8 extensions (ASIMD, AES, CRC32, PMULL, ATOMICS, SHA1, SHA2).
  * Wraps system libraries (`libc.so.6`, `libm.so.6`, etc.) and bridges them directly to ARM64 native calls, saving CPU cycles on library routines.
* **FEX-Emu (Rejected for Prototype):**
  * Requires strict 48-bit virtual address space and specific kernel configs/features often restricted on non-rooted Android devices.
  * Higher complexity to bundle and execute within an unprivileged PRoot userspace.

## 2. Installation & Environment
* **Binary Location:** `/usr/local/bin/box64` inside rootfs.
* **Version:** Box64 arm64 v0.4.3 with Dynarec (built for standard `/lib/ld-linux-aarch64.so.1` glibc loader).
* **Recommended Environment:**
  ```bash
  export BOX64_DYNAREC=1
  export BOX64_NOBANNER=1
  export BOX64_LOG=0
  export BOX64_DYNAREC_BIGBLOCK=2
  export BOX64_LD_LIBRARY_PATH=/usr/lib/box64-x86_64-linux-gnu:/lib64
  ```

## 3. Library & Loader Findings
* **Static vs Dynamic Binaries:**
  * Box64 relies on ELF dynamic symbol tables to wrap and redirect library calls to ARM64 host implementations.
  * Statically linked x86-64 binaries trigger `[BOX64] Warning, box64 is not really compatible with staticaly linked binaries. Expect crash!` and exit with code 128.
  * Dynamically linked x86-64 binaries (`PIE` or non-PIE targeting `libc.so.6`) execute seamlessly with zero external x86-64 glibc dependencies required for basic binaries.

## 4. Execution & Performance Verification
* **Test Executable:** `/root/tests/hello-x86_64-dynamic`
  * Architecture: `ELF 64-bit LSB pie executable, x86-64`
  * Dynamic Interpreter: `/lib64/ld-linux-x86-64.so.2`
  * Needed Libraries: `libc.so.6`
* **Output:** `Hello from x86_64 on ARM64!`
* **Exit Status:** `42` (Verified via CLI and MoWin interactive Compose UI on Samsung Galaxy S23)

### Benchmark Results (Samsung Galaxy S23 / Snapdragon 8 Gen 2)
| Mode | Real Time | User Time | Sys Time | Status Code |
|---|---|---|---|:---:|
| `BOX64_DYNAREC=1` (Dynarec Enabled) | 0m0.712s | 0m0.040s | 0m0.283s | 42 |
| `BOX64_DYNAREC=0` (Interpreter Only) | 0m0.725s | 0m0.031s | 0m0.305s | 42 |
