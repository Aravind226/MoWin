#!/usr/bin/env python3
"""
patch_ntdll.py - Fix Hangover 11.16 pKiUserCallbackDispatcher Initialization Bug

Problem:
In Hangover 11.16 wine-11.16 ntdll.so on ARM64, KeUserModeCallback loads
pKiUserCallbackDispatcher from offset 0xa3108. However, upstream load_ntdll_functions()
populates LdrSystemDllInitBlock in the PE image but omits storing the resolved
KiUserCallbackDispatcher into Unix global 0xa3108. Consequently, 0xa3108 remains 0x0
in .bss, causing KeUserModeCallback to jump to NULL (c0000005 ACCESS_VIOLATION pc=(nil))
during any Win32 GUI initialization (win32u.NtUserInitializeClientPfnArrays ->
NtUserRegisterClassExWOW -> KeUserModeCallback).

Solution:
Hook the 64-bit export resolution loop at 0x29e80 where find_named_export() resolves
KiUserCallbackDispatcher into x0. Redirect via an unused NOP sled at 0xc2a8 to:
1. str x0, [x1, #0xd0]  ; execute displaced original instruction
2. adrp x2, 0xa3000     ; compute page of pKiUserCallbackDispatcher
3. str x0, [x2, #0x108] ; *(ntdll.so + 0xa3108) = KiUserCallbackDispatcher
4. add x3, x0, #0x20    ; compute KiUserCallbackDispatcherReturn
5. str x3, [x2, #0x110] ; *(ntdll.so + 0xa3110) = KiUserCallbackDispatcherReturn
6. b 0x29e84            ; return to cbnz x0, 0x29e94 with x0 and x1 preserved
"""

import sys
import struct

HOOK_OFFSET = 0x29e80
TRAMPOLINE_OFFSET = 0xc2a8

# Hook instruction: b 0xc2a8
# Offset: 0xc2a8 - 0x29e80 = -0x1dbd8 (signed 26-bit branch displacement)
B_TO_TRAMPOLINE = 0x17ff890a

# Trampoline instructions at 0xc2a8:
TRAMPOLINE_OPCODES = [
    0xf9006820,  # str x0, [x1, #0xd0]
    0xf00004a2,  # adrp x2, 0xa3000
    0xf9008440,  # str x0, [x2, #0x108]
    0x91008003,  # add x3, x0, #0x20
    0xf9008843,  # str x3, [x2, #0x110]
    0x140076f2,  # b 0x29e84
]

def patch_ntdll(input_path: str, output_path: str):
    with open(input_path, "rb") as f:
        data = bytearray(f.read())

    # Verify original instruction at hook site: str x0, [x1, #0xd0]
    orig_hook = struct.unpack("<I", data[HOOK_OFFSET:HOOK_OFFSET + 4])[0]
    assert orig_hook == 0xf9006820, f"Unexpected instruction at 0x{HOOK_OFFSET:x}: 0x{orig_hook:08x}"

    # Apply branch to trampoline
    data[HOOK_OFFSET:HOOK_OFFSET + 4] = struct.pack("<I", B_TO_TRAMPOLINE)

    # Apply trampoline
    tramp_bytes = struct.pack(f"<{len(TRAMPOLINE_OPCODES)}I", *TRAMPOLINE_OPCODES)
    data[TRAMPOLINE_OFFSET:TRAMPOLINE_OFFSET + len(tramp_bytes)] = tramp_bytes

    with open(output_path, "wb") as f:
        f.write(data)

    print(f"[OK] Successfully patched {input_path} -> {output_path}")

if __name__ == "__main__":
    src = sys.argv[1] if len(sys.argv) > 1 else "/home/arav/ntdll.so"
    dst = sys.argv[2] if len(sys.argv) > 2 else "/home/arav/ntdll_patched.so"
    patch_ntdll(src, dst)
