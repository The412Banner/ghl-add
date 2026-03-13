# ghl-add Progress Log

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
