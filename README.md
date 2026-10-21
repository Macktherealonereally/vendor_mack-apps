# vendor/mack-apps: apps for every o1s variant

Apps that ship in all three builds of the unofficial LineageOS 23.2 for the
Galaxy S21 Exynos (o1s): NG (no GApps), MTG (MindTheGapps) and AMTG.
`device/samsung/o1s/device.mk` inherits `mack-apps.mk` when this directory is
present, outside the `WITHOUT_GAPPS` / `WITH_AMTG` gates.

## Aurora Store

| | |
|---|---|
| Package | `com.aurora.store`, 4.8.4 (versionCode 76), vanilla flavour |
| Upstream | https://gitlab.com/AuroraOSS/AuroraStore |
| Release | https://gitlab.com/AuroraOSS/AuroraStore/-/releases/4.8.4 |
| Source | tag `4.8.4`: https://gitlab.com/AuroraOSS/AuroraStore/-/archive/4.8.4/AuroraStore-4.8.4.tar.gz |
| Licence | GPL-3.0-or-later (Aurora OSS). Shipped unmodified. |
| Signer | certificate SHA-256 `4c626157ad02bda3401a7263555f68a79663fc3e13a4d4369a12570941aa280f` (the fingerprint in Aurora's README; developer-signed, not the F-Droid build) |
| Install | `/product/app/AuroraStore/AuroraStore.apk`, presigned, byte for byte |

It is a normal app, not a privileged one: no `priv-app`, no privapp
allowlist, no pre-granted permissions. Aurora installs and updates apps through
the system package installer, so the user allows "Install unknown apps" for
Aurora and confirms installs (or uses Aurora's Shizuku/root installers). It
keeps the developer signature, so Aurora's own self-updates install over the
built-in copy as an update of a system app. "Uninstall updates" goes back to
the built-in version. Play Store (MTG/AMTG) is a separate package, the two
coexist.

Not used: the `preload` flavour. It asks for `INSTALL_PACKAGES` and
`DELETE_PACKAGES`, which only work as a privileged app with an allowlist.

## Files

- `blobs.txt`: the pinned APKs (path, sha256, size, signer certificate sha256,
  download URL). The APKs are git-ignored; this repo holds no binaries.
- `fetch-blobs.sh`: downloads every APK from its URL (or copies it from
  `MACK_APPS_BLOB_CACHE=<dir>`) and keeps it only if all pins match.
- `tools/check-blobs.py`: the check itself (standard library only). It reads the
  signer from the APK Signature Scheme v3.1/v3/v2 block, the value `apksigner
  verify --print-certs` prints as "certificate SHA-256 digest".
- `mack-apps.mk`: runs the check while the product config is read, so `lunch`
  and every build stop when an APK is missing or is not the pinned file.
- `Android.bp`: the `android_app_import` modules (own Soong namespace).

## Updating Aurora Store

1. Download the new vanilla `AuroraStore-<version>.apk` from the GitLab release.
2. `apksigner verify --print-certs` must show the same signer certificate
   SHA-256 (the old signer pin stays in `blobs.txt`). With another signer the
   built-in copy and Aurora's self-updates would conflict.
3. Put the new sha256, size and URL into `blobs.txt`, update this README
   (version, tag), run `./fetch-blobs.sh` and `tools/check-blobs.py .`.
