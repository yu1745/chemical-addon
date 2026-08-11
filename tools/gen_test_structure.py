#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate an empty 15x15x15 gametest structure template
(data/chemicaladdon/structures/empty_15.nbt, gzip-compressed NBT)."""
import gzip
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/data/chemicaladdon/structures/empty_15.nbt")


def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def named(type_byte, name, payload):
    """Named NBT tag: type byte + name + payload."""
    return bytes([type_byte]) + nbt_string(name) + payload


def int32(value):
    return struct.pack(">i", value)


def int_list(vals):
    """Payload of a TAG_List of TAG_Int: element type + count + elements."""
    return bytes([0x03]) + struct.pack(">i", len(vals)) + b"".join(int32(v) for v in vals)


def empty_compound_list():
    """Payload of a TAG_List of TAG_Compound with zero elements."""
    return bytes([0x0A]) + struct.pack(">i", 0)


def build():
    payload = b""
    payload += named(0x03, "DataVersion", int32(3465))
    payload += named(0x09, "size", int_list([15, 15, 15]))
    payload += named(0x09, "blocks", empty_compound_list())
    payload += named(0x09, "palette", empty_compound_list())
    payload += named(0x09, "entities", empty_compound_list())
    root = bytes([0x0A]) + nbt_string("") + payload + b"\x00"
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(root)
    print(f"OK: {OUT} ({os.path.getsize(OUT)} bytes)")


if __name__ == "__main__":
    build()
