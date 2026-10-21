#!/usr/bin/env python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
"""Check the prebuilt APKs of vendor/mack-apps against blobs.txt.

For every line: the file exists, has the pinned size and sha256, and its APK
Signature Scheme v3.1/v3/v2 signer certificate has the pinned sha256 (the same
value `apksigner verify --print-certs` shows as "certificate SHA-256 digest").
The sha256 pin already fixes the content; the signer pin makes an update to a
new version fail unless the developer key stays the same (self-updates need it).
This reads the certificate, it does not verify signatures: apksigner does that.

  check-blobs.py [--quiet] <vendor/mack-apps dir>     check every blob
  check-blobs.py --file <apk> <dest> <dir>            check a download for <dest>

Prints OK (exit 0) or one line per problem (exit 1). Standard library only.
"""

import argparse
import hashlib
import pathlib
import struct
import sys

SIG_BLOCK_MAGIC = b"APK Sig Block 42"
# Newest scheme first: Android uses v3.1, then v3, then v2.
SCHEME_IDS = ((0x1B93AD61, "v3.1"), (0xF05368C0, "v3"), (0x7109871A, "v2"))


def read_blobs(top: pathlib.Path):
    for line in (top / "blobs.txt").read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        dest, sha, size, signer, url = line.split("|")
        yield dest, sha, int(size), signer, url


def _prefixed(buf: bytes, off: int):
    (n,) = struct.unpack_from("<I", buf, off)
    if off + 4 + n > len(buf):
        raise ValueError("truncated length-prefixed field")
    return buf[off + 4:off + 4 + n], off + 4 + n


def signing_block(data: bytes) -> bytes:
    eocd = data.rfind(b"PK\x05\x06", max(0, len(data) - 65557))
    if eocd < 0:
        raise ValueError("not a zip file")
    (cd_off,) = struct.unpack_from("<I", data, eocd + 16)
    if data[cd_off - 16:cd_off] != SIG_BLOCK_MAGIC:
        raise ValueError("no APK Signing Block (v1-only APK?)")
    (size,) = struct.unpack_from("<Q", data, cd_off - 24)
    start = cd_off - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        raise ValueError("bad APK Signing Block size")
    return data[start + 8:cd_off - 24]


def signer_cert_sha256(data: bytes) -> tuple[str, str]:
    """sha256 of the first signer's first certificate, newest scheme first."""
    block = signing_block(data)
    pairs = {}
    off = 0
    while off < len(block):
        (n,) = struct.unpack_from("<Q", block, off)
        (pid,) = struct.unpack_from("<I", block, off + 8)
        pairs[pid] = block[off + 12:off + 8 + n]
        off += 8 + n
    for pid, scheme in SCHEME_IDS:
        if pid not in pairs:
            continue
        signers, _ = _prefixed(pairs[pid], 0)
        signer, _ = _prefixed(signers, 0)
        signed_data, _ = _prefixed(signer, 0)
        _digests, o = _prefixed(signed_data, 0)
        certs, _ = _prefixed(signed_data, o)
        cert, _ = _prefixed(certs, 0)
        return hashlib.sha256(cert).hexdigest(), scheme
    raise ValueError("no v2/v3 signature in the APK Signing Block")


def check(path: pathlib.Path, dest, sha, size, signer) -> list[str]:
    if not path.is_file():
        return [f"{dest}: missing"]
    data = path.read_bytes()
    problems = []
    if len(data) != size:
        problems.append(f"{dest}: size {len(data)}, expected {size}")
    got = hashlib.sha256(data).hexdigest()
    if got != sha:
        problems.append(f"{dest}: sha256 {got}, expected {sha}")
    try:
        cert, scheme = signer_cert_sha256(data)
        if cert != signer:
            problems.append(f"{dest}: signer ({scheme}) {cert}, expected {signer}")
    except (ValueError, struct.error) as e:
        problems.append(f"{dest}: cannot read signer: {e}")
    return problems


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--quiet", action="store_true", help="print only OK or the problems")
    p.add_argument("--file", nargs=2, metavar=("APK", "DEST"),
                   help="check APK against the pins of blobs.txt entry DEST")
    p.add_argument("top", help="the vendor/mack-apps directory")
    args = p.parse_args()
    top = pathlib.Path(args.top)
    blobs = list(read_blobs(top))
    problems = []
    if args.file:
        apk, want = args.file
        match = [b for b in blobs if b[0] == want]
        if not match:
            sys.exit(f"{want}: not in blobs.txt")
        _, sha, size, signer, _ = match[0]
        problems = check(pathlib.Path(apk), want, sha, size, signer)
    else:
        for dest, sha, size, signer, _url in blobs:
            found = check(top / dest, dest, sha, size, signer)
            problems += found
            if not found and not args.quiet:
                print(f"ok   {dest}")
    print("\n".join(problems) if problems else "OK")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
