#!/usr/bin/env python3
"""Regression guard for Google proprietary SDK code and metadata in a FOSS APK.

This supplements dependency/license review; it is not a universal license scanner.
"""
import sys
import struct
import zipfile

PREFIXES = (
    "com/google/android/gms/", "com/google/firebase/",
    "com/google/android/play/", "com/google/ads/",
)

def defined_classes(data):
    if not data.startswith(b"dex\n"):
        raise SystemExit("Unsupported DEX format")
    _, strings_offset = struct.unpack_from("<II", data, 56)
    _, types_offset = struct.unpack_from("<II", data, 64)
    count, classes_offset = struct.unpack_from("<II", data, 96)
    for index in range(count):
        type_index = struct.unpack_from("<I", data, classes_offset + index * 32)[0]
        string_index = struct.unpack_from("<I", data, types_offset + type_index * 4)[0]
        offset = struct.unpack_from("<I", data, strings_offset + string_index * 4)[0]
        # Skip the ULEB128 string length. Class descriptors use ASCII here.
        while data[offset] & 128:
            offset += 1
        offset += 1
        yield data[offset:data.index(0, offset)].decode("utf-8")


def check(path):
    with zipfile.ZipFile(path) as apk:
        dex_files = [name for name in apk.namelist() if name.endswith(".dex")]
        if not dex_files:
            raise SystemExit("No DEX files found")
        for name in dex_files:
            for descriptor in defined_classes(apk.read(name)):
                if any(descriptor.startswith("L" + prefix) for prefix in PREFIXES):
                    raise SystemExit(f"Forbidden SDK class in {name}: {descriptor}")
        manifest = apk.read("AndroidManifest.xml")
        for prefix in PREFIXES:
            for encoding in ("utf-8", "utf-16le"):
                if prefix.replace("/", ".").encode(encoding) in manifest:
                    raise SystemExit(f"Forbidden SDK manifest entry: {prefix}")
    print(f"PASS: no prohibited Google SDK classes or manifest entries in {path}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check-foss-apk.py path/to/foss.apk")
    check(sys.argv[1])
