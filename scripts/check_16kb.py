#!/usr/bin/env python3
"""Fail if a 64-bit native library inside an AAB/APK is not 16 KB page-size aligned.

Usage: python3 scripts/check_16kb.py path/to/app.aab   (also works on .apk)
Play requires 16 KB support on 64-bit ABIs (arm64-v8a, x86_64), so only those can fail the check;
32-bit libraries are listed for information.
"""
import struct
import sys
import zipfile

PT_LOAD = 1
MIN_ALIGN = 16384
HINTS = {
    "libimage_processing_util_jni": "CameraX camera-core: use CameraX 1.4.0 or newer",
    "libsurface_util_jni": "CameraX camera-core",
    "libandroidx.graphics.path": "Compose ui-graphics (androidx.graphics:graphics-path)",
    "libdatastore_shared_counter": "androidx.datastore",
    "libjp2-android": "gemalto jp2-android (optional PdfBox-Android JPEG2000 codec)",
    "libbarhopper_v3": "ML Kit barcode scanning: use 17.3.0 or newer",
}


def min_load_align(data):
    if data[:4] != b"\x7fELF":
        return None
    is64 = data[4] == 2
    e = "<" if data[5] == 1 else ">"
    aligns = []
    if is64:
        (phoff,) = struct.unpack_from(e + "Q", data, 0x20)
        phentsize, phnum = struct.unpack_from(e + "HH", data, 0x36)
        for i in range(phnum):
            off = phoff + i * phentsize
            (p_type,) = struct.unpack_from(e + "I", data, off)
            if p_type == PT_LOAD:
                aligns.append(struct.unpack_from(e + "Q", data, off + 0x30)[0])
    else:
        (phoff,) = struct.unpack_from(e + "I", data, 0x1C)
        phentsize, phnum = struct.unpack_from(e + "HH", data, 0x2A)
        for i in range(phnum):
            off = phoff + i * phentsize
            (p_type,) = struct.unpack_from(e + "I", data, off)
            if p_type == PT_LOAD:
                aligns.append(struct.unpack_from(e + "I", data, off + 0x1C)[0])
    return min(aligns) if aligns else None


def main(path):
    bad = []
    total = 0
    with zipfile.ZipFile(path) as z:
        for name in sorted(z.namelist()):
            if not name.endswith(".so") or "/lib/" not in "/" + name:
                continue
            abi = name.split("/lib/")[-1].split("/")[0] if "/lib/" in name else name.split("/")[1]
            lib = name.rsplit("/", 1)[-1]
            align = min_load_align(z.read(name))
            if align is None:
                continue
            total += 1
            is64 = abi in ("arm64-v8a", "x86_64")
            ok = align >= MIN_ALIGN
            print(f"{'OK ' if ok else 'BAD'} {abi:12s} align={align // 1024:>3d} KB  {name}")
            if not ok and is64:
                bad.append((name, lib))
    print(f"\nChecked {total} native libraries.")
    if bad:
        print("\nNOT 16 KB aligned (Play will reject this bundle):")
        for name, lib in bad:
            hint = next((h for k, h in HINTS.items() if lib.startswith(k)), "third-party SDK: update it to a 16 KB-compatible release")
            print(f"  - {name}\n      -> {hint}")
        return 1
    print("All 64-bit native libraries are 16 KB aligned.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1]))
