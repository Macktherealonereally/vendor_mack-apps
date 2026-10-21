#!/bin/bash
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Get the prebuilt APKs listed in blobs.txt into ./prebuilt and check them
# (size, sha256, signer certificate) with tools/check-blobs.py.
# MACK_APPS_BLOB_CACHE=<dir>: copy <dir>/<file name of the URL> instead of downloading.
set -euo pipefail
cd "$(dirname "$0")"
grep -v '^#' blobs.txt | while IFS='|' read -r dest sha size signer url; do
  [ -n "$dest" ] || continue
  if python3 -I tools/check-blobs.py --file "$dest" "$dest" . >/dev/null; then echo "ok   $dest"; continue; fi
  mkdir -p "$(dirname "$dest")"
  cached=${MACK_APPS_BLOB_CACHE:-}/$(basename "$url")
  if [ -n "${MACK_APPS_BLOB_CACHE:-}" ] && [ -f "$cached" ]; then
    cp "$cached" "$dest.part"
  else
    curl -fsSL -o "$dest.part" "$url"
  fi
  if ! python3 -I tools/check-blobs.py --file "$dest.part" "$dest" .; then
    rm -f "$dest.part"; echo "rejected: $dest" >&2; exit 1
  fi
  mv "$dest.part" "$dest"; echo "got  $dest"
done
