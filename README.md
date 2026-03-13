# ghl-add

Patches for **GameHub Lite 5.1.4** — applies the BannerHub component management features to the vanilla (non-ReVanced) GameHub Lite APK.

## Applied Patches

| Patch | Description |
|---|---|
| My Games tab | Renames the "Dashboard" sidebar tab to "My Games" |
| BCI launcher button | Adds an open-in-new icon to the top-right toolbar; opens BannersComponentInjector if installed |
| Component Manager | Adds a "Components" entry to the side menu; opens a built-in Component Manager activity |

## Component Manager Features

- Lists all component folders from `files/usr/home/components/`
- Two-level UI: component list → Inject file / Backup / Back
- WCP/ZIP extraction with auto-detection by magic bytes:
  - **ZIP** (Turnip / adrenotools) — flat extraction
  - **zstd tar** (DXVK / VKD3D / Box64) — preserves `system32/`/`syswow64/` structure
  - **XZ tar** (FEXCore nightlies) — flat extraction to component root
- Backup component to `Downloads/BannerHub/<name>/`
- Shows `[-> filename]` label for injected components (persisted across restarts)

## Build

CI builds on every `v*` tag push. The patched APK is uploaded as a release asset.

Signed with AOSP testkey (v1 + v2 + v3). Must uninstall the original GameHub Lite before installing (signature mismatch).

## Base APK

The original `GameHub-Lite-v5.1.4.apk` is stored in the [`base-apk`](../../releases/tag/base-apk) release.
