# ghl-add Progress Log

## v1.1.1 — revert: roll back to v1.0.4 (remove all orientation patches) (2026-03-14)
**Commit:** `0e407f2` | **Tag:** `v1.1.1`

### What changed
- Reverted build.yml to v1.0.4 state — removed all orientation patches (v1.0.5–v1.1.0).
- The startup rotation could not be fixed via manifest or smali patching.

### Files touched
- `.github/workflows/build.yml`

---

## v1.1.0 — fix: nop all setRequestedOrientation() smali calls to stop startup rotation (2026-03-14)
**Commit:** `e3e8ae0` | **Tag:** `v1.1.0`

### What changed
- Manifest patch alone (v1.0.7–v1.0.9) failed — GameHub overrides orientation at runtime.
- New smali patch: removes every `invoke-virtual ...->setRequestedOrientation(I)V` line from all smali files (5 files affected).
- Combined with the manifest sensorLandscape patch (98 activity tags), the manifest now has sole control over orientation.

### Files touched
- `.github/workflows/build.yml`

---

## v1.0.9 — fix: force sensorLandscape on ALL activities to stop startup rotation (2026-03-14)
**Commit:** `35eb596` | **Tag:** `v1.0.9`

### What changed
- Patches every `<activity>` opening tag in AndroidManifest.xml with `android:screenOrientation="sensorLandscape"`.
- v1.0.8 failed (malformed XML): regex matched both `<activity>` and `<activity />` self-closing tags; `tag[:-1]` stripped the `/` from self-closing tags. Fixed by checking `tag.endswith('/>')` and using `tag[:-2] + ' />'`.
- Skips tags already set to a landscape variant (sensorLandscape, landscape, reverseLandscape, userLandscape).

### Files touched
- `.github/workflows/build.yml`

---

## v1.0.7 — fix: force sensorLandscape on launcher activity to stop startup rotation (2026-03-14)
**Commit:** `5fff30f` | **Tag:** `v1.0.7`

### What changed
- Added `android:screenOrientation="sensorLandscape"` to the main launcher activity in AndroidManifest.xml.
- Prevents the 360° portrait→landscape rotation on first app launch.
- Patch finds the launcher activity by scanning all occurrences of `android.intent.action.MAIN` and picking the one inside an `<activity>` element (skips occurrences in `<queries>` blocks, which appeared first and caused v1.0.5/v1.0.6 to fail).
- v1.0.5 failed: regex search by `LandscapeLauncherMainActivity` class name found nothing (class name differs in decoded manifest).
- v1.0.6 failed: `android.intent.action.MAIN` in `<queries>` block appeared before the `<application>`, so `rfind('<activity')` returned -1.

### Files touched
- `.github/workflows/build.yml`

---

## v1.0.4 — refactor: drop BCI button + My Games rename, keep only component manager sidebar (2026-03-14)
**Commit:** `e16101e` | **Tag:** `v1.0.4`

### What changed
- Removed "My Games" tab rename patch (strings.xml `Dashboard` → `My Games`)
- Removed BCI launcher button: ids.xml ID, public.xml public ID, toolbar layout `iv_bci_launcher` ImageView, and `LandscapeLauncherMainActivity.initView()` smali injection
- Removed `setupBciButton()` from `ComponentManagerHelper.java` and its `Toast` import
- Kept patches 5/6/7: AndroidManifest ComponentManagerActivity registration, `addComponentsMenuItem()` injection, click intercept injection
- Kept `addComponentsMenuItem()` + `handleMenuItemClick()` in ComponentManagerHelper
- Updated `base-apk` release asset to `GameHub-Lite-v5.1.4.apk` (52,013,870 bytes)

### Files touched
- `.github/workflows/build.yml`
- `extension/ComponentManagerHelper.java`

---

## v1.0.3 — fix: TarArchiveEntry.getName() is p() in 5.1.4 + use 3-arg read() (2026-03-13)
**Commit:** `bff874d` | **Tag:** `v1.0.3`

### What changed
- **DXVK and FEXCore injection failing** with `NoSuchMethodException: TarArchiveEntry.getName []`.
  Root cause: the `ArchiveEntry` interface is fully stripped by R8 in 5.1.4, so `getName()` is not
  kept as an interface implementation. The obfuscated name is `p()` (returns field `a` = entry path).
  Fix: changed `entry.getClass().getMethod("getName")` → `getMethod("p")` in `extractTar()`.
- **`pipeReflected`**: switched from 1-arg `read(byte[])` to confirmed 3-arg `read(byte[], int, int)`.
  Only the 3-arg form is explicitly present in 5.1.4's `TarArchiveInputStream`; the 1-arg form
  might resolve via InputStream base class but the 3-arg is guaranteed.
- Updated Javadoc to document all 5.1.4 obfuscated method names.

### Files touched
- `extension/WcpExtractor.java`

---

## v1.0.2 — fix: DXVK injection + stream leaks + clearDir safety (2026-03-13)
**Commit:** `6baf5bb` | **Tag:** `v1.0.2`

### What changed
- **DXVK/VKD3D/Box64/XZ injection was failing** with `NoSuchMethodException: TarArchiveInputStream.s`.
  Root cause: `getNextTarEntry()` is obfuscated to `f()` in 5.1.4 (not `s()` like in 5.3.5 ReVanced).
  Fix: changed `tarClass.getMethod("s")` → `tarClass.getMethod("f")` in `extractTar()`.
- **Stream leaks** — a failed tar extraction left 4 streams unclosed (`raw`, `bis`, `zstd`/`xz`, `tar`),
  causing 4× `"A resource failed to call close"` warnings in logcat. Fixed by wrapping all streams in
  try-finally throughout `extract()`.
- **clearDir safety** — `clearDir()` was called before the format branches, so an unrecognised file or
  early I/O error would wipe the component folder with nothing to replace it. Moved `clearDir()` inside
  each format branch (ZIP / zstd-tar / XZ-tar) so it only runs immediately before a real extraction.
- **clearDir logging** — `File.delete()` now logs `W/BannerHub: clearDir: failed to delete <path>` when
  it returns false, to diagnose if GameHub has component files locked.

### Files touched
- `extension/WcpExtractor.java`

---

## v1.0.1 — fix: Components item visible in sidebar (2026-03-13)
**Commit:** `e81190a` | **Tag:** `v1.0.1`

### What changed
- Fixed Components item not appearing in the GameHub Lite sidebar after install.
- Root cause: patch 6 injected `addComponentsMenuItem(this.m)` at the end of `Z0()`, after
  `MultiViewHolderAdapterKt.f(adapter, this.m)` had already submitted the list to the
  RecyclerView adapter (line 1529). The adapter copies the list at submit time, so items added
  afterward are invisible.
- New injection point: immediately before the `if-eqz v3, :cond_1` / `iget-object v4` /
  `invoke-static MultiViewHolderAdapterKt.f()` block in `Z0()`. At that point `v0` holds the
  `HomeLeftMenuDialog` instance (set by `move-object/from16 v0, p0` at line 1256). We load
  `this.m` into `v4`, call `addComponentsMenuItem`, then the original code re-loads and submits
  the now-complete list to the adapter.

### Files touched
- `.github/workflows/build.yml` — replaced patch 6 anchor and injection smali

---

## v1.0.0 — feat: initial GameHub Lite 5.1.4 patcher (2026-03-13)
**Commit:** `602e143` | **Tag:** `v1.0.0`

### What changed
Initial repo — applies three BannerHub-equivalent patches to `GameHub-Lite-v5.1.4.apk` via an
apktool decompile/recompile CI pipeline (GitHub Actions).

**Patches applied:**
1. **My Games tab rename** — `strings.xml`: `"Dashboard"` → `"My Games"`
2. **BCI launcher button** — adds `iv_bci_launcher` FocusableImageView to the toolbar layout
   (after `iv_search`); registers it in `ids.xml` + `public.xml`; wires a click listener in
   `LandscapeLauncherMainActivity.initView()` via `ComponentManagerHelper.setupBciButton()`
3. **Component Manager** — registers `ComponentManagerActivity` in `AndroidManifest.xml`;
   injects "Components" menu item in `HomeLeftMenuDialog.Z0()` via
   `ComponentManagerHelper.addComponentsMenuItem()`; intercepts sidebar clicks in
   `HomeLeftMenuDialog$init$1$9$2.a(MenuItem)` via `ComponentManagerHelper.handleMenuItemClick()`

**Extension classes (compiled javac + d8 → classes11.dex):**
- `ComponentManagerActivity.java` — two-level ListView UI matching BannerHub v2.2.0
  (component list → Inject / Backup / Back); SharedPrefs `"bh_injected"` for `[-> filename]` label
- `ComponentManagerHelper.java` — 5.1.4 MenuItem constructor signature:
  `<init>(int id, int iconRes, String name, String rightContent, int mask, DefaultConstructorMarker)`
- `WcpExtractor.java` — magic-byte ZIP/zstd-tar/XZ-tar detection; uses GameHub's built-in
  commons-compress, zstd-jni, tukaani xz via reflection (no class conflicts)

**Smali injection points (5.1.4 obfuscated names):**
- `HomeLeftMenuDialog.Z0()V` — list builder (was `u1()V` in 5.3.5)
- `HomeLeftMenuDialog$init$1$9$2.a(MenuItem)V` — click handler lambda (was static `o1()` in 5.3.5)
- `LandscapeLauncherMainActivity.initView()V` — same name as 5.3.5

**Signing:** AOSP testkey (`testkey.pk8` / `testkey.x509.pem`), v1 + v2 + v3 signatures.

### Files touched
- `.github/workflows/build.yml` — full CI pipeline
- `.gitignore`
- `README.md`
- `extension/ComponentManagerActivity.java`
- `extension/ComponentManagerHelper.java`
- `extension/WcpExtractor.java`
- `testkey.pk8`, `testkey.x509.pem`
