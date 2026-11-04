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
| Shipped as | `assets/AuroraStore.apk` inside `/product/priv-app/MackAppInstaller/MackAppInstaller.apk`, byte for byte |
| Installed as | a normal **user app** (in `/data/app`), once per user, by MackAppInstaller |

### Removable: how it is installed

Aurora Store is **not a system app**. The pinned APK is an asset of the small
helper `MackAppInstaller` (package `io.github.macktherealonereally.appinstaller`,
privileged app in `/product/priv-app`, no UI, no launcher entry, no network,
no INTERNET permission; source in `MackAppInstaller/`). The package manager
does not scan APKs inside other APKs, so nothing on the system image is
`com.aurora.store`.

On `BOOT_COMPLETED` (sent once per user, after that user is unlocked, also on
the very first boot and for every new user) the helper:

1. stops if this user is marked done;
2. marks done without installing when the user is a profile (work profile,
   clone, private space: the parent user has Aurora), when app installs are
   disallowed for the user (`DISALLOW_INSTALL_APPS`), when `com.aurora.store`
   is already installed for the user (any build: GitLab, F-Droid, an update
   from an older ROM), or after 3 failed attempts;
3. otherwise counts an attempt and installs the asset with a `PackageInstaller`
   session (`INSTALL_PACKAGES`, allowlisted in
   `MackAppInstaller/privapp_allowlist.xml`, so no prompt). On success the
   user is marked done. On failure it tries again on the next boot, at most 3
   boots in total. Logs: `adb logcat -s MackAppInstaller`.

"Done" is a flag in the helper's own data (`/data/user/<id>/<package>`), so
it survives reboots and OTA updates: uninstall Aurora and it stays gone;
downgrade it or replace it with the F-Droid build (different signer: uninstall
first) and the helper leaves it alone. It installs again only after a factory
reset, for a new user, or when the helper's storage is cleared. If the helper
is disabled, nothing is installed.

The installed copy keeps Aurora's developer signature, so Aurora's own
self-updates install over it as usual. The installer of record is the helper;
it requests no update ownership.

**Dirty flash from lineage-23.2-20261031** (Aurora was a system app in
`/product/app`): on the first boot of the new build the system copy is gone.

- If Aurora had updated itself (the update lives in `/data/app`), the package
  manager keeps that update as a normal user app ("Updated system package
  com.aurora.store no longer exists; rescanning package on data"), with its
  data. The helper sees it installed and only marks done.
- If Aurora still ran the built-in 4.8.4, the package manager removes the
  package and its data ("System package com.aurora.store no longer exists;
  its data will be wiped"). The helper then installs 4.8.4 fresh: Aurora's
  settings (and an anonymous or Google login) reset once. Apps installed
  through Aurora are not affected.
- Users who had disabled or `pm uninstall --user 0`'d the system Aurora get
  it once more; from now on it can really be uninstalled.

**Licence and source (GPL-3.0).** `LICENSE.GPL-3.0` is the licence text. The
`license` module in `Android.bp` (used by MackAppInstaller, the APK that
carries Aurora) adds it to the product NOTICE file, so Aurora is
listed under Settings > About phone > Legal information > Third-party licences.
**Every GitHub release that ships Aurora must attach the matching source
tarball**: for 4.8.4 that is `AuroraStore-4.8.4.tar.gz` from
https://gitlab.com/AuroraOSS/AuroraStore/-/archive/4.8.4/AuroraStore-4.8.4.tar.gz.
Name it in the release notes. A link to GitLab alone would oblige us to keep the
source available ourselves (GPLv3 section 6).

Not used: the `preload` flavour. It asks for `INSTALL_PACKAGES` and
`DELETE_PACKAGES`, which only work as a privileged app with an allowlist.

## Files

- `blobs.txt`: the pinned APKs (path, sha256, size, signer certificate sha256,
  download URL). The APKs are git-ignored; this repo holds no binaries.
- `fetch-blobs.sh`: downloads every APK from its URL (or copies it from
  `MACK_APPS_BLOB_CACHE=<dir>`) and keeps it only if all pins match.
- `tools/check-blobs.py`: the check itself (standard library only). It reads the
  signer from the APK Signature Scheme v3.1/v3/v2 block, the value `apksigner
  verify --print-certs` prints as "certificate SHA-256 digest". It also fails
  when blobs.txt pins nothing, or when an `apk:` path or a file in an
  `asset_dirs` directory of an `Android.bp` here has no blobs.txt line, so a
  new module cannot ship an unpinned APK.
- `mack-apps.mk`: runs the check while the product config is read, so `lunch`
  and every build stop when an APK is missing or is not the pinned file.
- `Android.bp`: MackAppInstaller (own Soong namespace) and the `license`
  modules.
- `MackAppInstaller/`: the helper's manifest, privapp allowlist and Java source
  (Apache-2.0).
- `LICENSE.GPL-3.0`: GNU GPL version 3 (the licence of Aurora Store).

Soong reads `Android.bp` for every product in the tree, also when this namespace
is not used. So with vendor/mack-apps present but `fetch-blobs.sh` not run, any
other product stops with `module source path ... does not exist`. Run
`./fetch-blobs.sh` or remove the directory.

**Release signing.** `sign_target_files_apks` re-signs every file whose name
ends in `.apk` and needs an apkcerts.txt entry for each. The only new `.apk`
is `MackAppInstaller.apk` (default certificate, so `-d <keys>` maps it to
`releasekey`). Aurora is a zip entry inside it; re-signing the outer APK keeps the
content of every entry, so Aurora keeps its own signature.

## Updating Aurora Store

1. Download the new vanilla `AuroraStore-<version>.apk` from the GitLab release.
2. `apksigner verify --print-certs` must show the same signer certificate
   SHA-256 (the old signer pin stays in `blobs.txt`). With another signer the
   bundled copy and Aurora's self-updates would conflict. Exception: if Aurora
   rotates its key (APK Signature Scheme v3.1 with a lineage), self-updates keep
   working, but the check reads the newest scheme's signer, so the pin becomes
   the new key. Confirm the rotation with `apksigner verify -v --print-certs`
   before you change the signer pin.
3. Put the new sha256, size and URL into `blobs.txt`, update this README
   (version, tag), run `./fetch-blobs.sh` and `tools/check-blobs.py .`.
4. Attach the new source tarball to the next GitHub release.
5. Only users without Aurora get the new bundled version (new installs, new
   users). Users already marked done keep theirs and Aurora updates itself.
