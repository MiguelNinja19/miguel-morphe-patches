# 🧩 diozz-cubex-patches

Morphe patches for **CubeX Solver** (package: `diozz.cubex`, also published as
"Cubex Solver Timer 3D Cube" by Pipi Chick Studio / ZipoApps on Google Play).

These patches target the **PremiumHelper SDK** and the **AdManager** shipped
inside the APK. They unlock premium features, disable non-rewarded ads,
disable the relaunch/"Baixe este app na Play Store" protection screen, and
force-allow rewarded ads.

## 📦 Compatible app

| Field | Value |
|-------|-------|
| App name | CubeX Solver |
| Package | `diozz.cubex` |
| Version | `4.1.0` (versionCode `8000101`) |
| Min SDK | 22 (Android 5.1) |
| Architectures | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Source | [APKMirror — Cubex Solver Timer 3D Cube 4.1.0](https://www.apkmirror.com/apk/pipi-chick-studio/cubex-solver-timer-3d-cube/cubex-solver-timer-3d-cube-4-1-0-release/) |

Other versions of the same major line are declared as experimental — the
patches target SDK framework code (the `zc.*` and `rc.*` packages) which is
shared across all ZipoApps titles and is generally stable across releases.

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->

<!-- PATCHES_END -->

| # | Patch name | What it does |
|---|------------|--------------|
| 1 | **Unlock premium** | Returns `true` from `PremiumHelper.hasActivePurchase()` (`zc.g.i()`). Unlocks the Advanced Solver (the "last cube" option that was greyed out at the end of the solver selector), custom color schemes, VIP support email, and disables the in-app "Remove Ads" prompt. |
| 2 | **Remove non-rewarded ads** | Patches `AdManager.isAdEnabled(adType, ...)` (`rc.a.f(...)`) to return `FALSE` for every ad type except `REWARDED`. Also patches the exit-ad gate (`rc.a.k(Activity)`) to always return `false` so the back-press exit ad is skipped. |
| 3 | **Remove relaunch protection** | No-ops `zc.k.n(String)` so the `RelaunchPremiumActivity` ("Baixe este app na Play Store") can never be started, and forces `zc.k.h()` to return `false` so the relaunch/start-like-pro flow is suppressed. |
| 4 | **Auto-reward ads** | Forces `AdManager.isAdEnabled(REWARDED)` to return `true`. In this app version there is no user-visible "watch ad to skip" button, so this patch is mainly defensive. Use the **Unlock premium** patch for the practical equivalent of "every reward granted". |

All four patches are enabled by default. You can disable any of them in
Morphe Manager before applying.

## 🚀 How to use

### Option A — Add this repo to Morphe Manager (recommended)

1. Install [Morphe Manager](https://morphe.software) on your Android device.
2. Open this link on your device:
   ```
   https://morphe.software/add-source?github=<your-username>/morphe-patches
   ```
   (Replace `<your-username>` with the GitHub user/org where you published
   this repo.)
3. Morphe Manager will list CubeX Solver as a supported app.
4. Select the APK file (from APKMirror, APKPure, or pulled from your device),
   choose the patches you want, and tap **Patch**.
5. Install the resulting patched APK.

### Option B — Build the bundle locally and use Morphe CLI

This is useful for development or for testing changes before publishing a
release.

```bash
# 1. Clone this repo
git clone https://github.com/<your-username>/morphe-patches.git
cd morphe-patches

# 2. Set up a GitHub Personal Access Token (PAT) with `read:packages` scope
#    so Gradle can download the Morphe patches Gradle plugin from
#    GitHub Packages.
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-pat>

# 3. Build the patch bundle (.mpp)
./gradlew buildAndroid

# The bundle is at: patches/build/libs/patches-*.mpp

# 4. Patch the APK using Morphe CLI
java -jar morphe-cli.jar patch \
    -a diozz.cubex_4.1.0.apk \
    -p patches/build/libs/patches-1.0.0.mpp \
    -o diozz.cubex_4.1.0_patched.apk

# 5. Install the patched APK on your device
adb install -r diozz.cubex_4.1.0_patched.apk
```

## 🔬 How the patches work (technical details)

### Premium unlock

The entire ZipoApps PremiumHelper SDK gates every premium feature behind a
single boolean:

```java
// zc.g (Preferences.kt) — obfuscated class
public final boolean i() {
    return sharedPreferences.getBoolean("has_active_purchase", false);
}
```

This getter is called from `zc.k.h()` (the relaunch decision),
`PHSplashActivity` (the "Start Like Pro" gate), `SettingsActivity.onResume()`
(to toggle the "Remove Ads" preference row), `PHMessagingService`, and a
few other places. Patching this single method to always return `true`
unlocks:

- **Advanced Solver** (Kociemba two-phase) — the option that was greyed out
  at the end of the solver selector in `v_cube_solver_selector.xml`.
- **Custom color schemes** (`PaletteSettings`, `CustomSchemeEnabled`).
- **VIP customer support** email address.
- **No "Remove Ads" prompt** in `SettingsActivity`.
- **No "Start Like Pro" / "Relaunch Premium"** interruption screens, because
  the splash activity checks `zc.g.i()` before launching them.

### Non-rewarded ad removal

The AdManager (`rc.a`) defines five ad types as an enum:

```java
public enum AdType {
    INTERSTITIAL, BANNER, NATIVE, REWARDED, BANNER_MEDIUM_RECT
}
```

The method `rc.a.f(adType, ...)` (`isAdEnabled`) is called before any ad
is loaded or shown. We short-circuit it to:

- Return `Boolean.FALSE` for `INTERSTITIAL`, `BANNER`, `NATIVE`,
  `BANNER_MEDIUM_RECT` — no banner, interstitial, or native ad ever loads.
- Return `Boolean.TRUE` for `REWARDED` — the rewarded ad loader is still
  allowed (this matters if a future app update wires up a "watch ad to
  skip" button).

We also patch `rc.a.k(Activity)` (`shouldShowExitAd`) to always return
`false`, so the back-press exit-ad overlay never appears.

### Relaunch / pairip-style protection removal

This app does NOT ship the native `libpairipcore.so` from Google Play's app
integrity SDK. Instead, it uses the ZipoApps PremiumHelper "relaunch"
flow which has the same user-facing effect: after the app is detected as
modified, the next launch yanks the user into `RelaunchPremiumActivity`,
which says (in effect) "Baixe este app na Play Store".

The flow has three trigger points:

1. **`zc.k.n(String)`** — static method that builds and starts an Intent
   targeting `RelaunchPremiumActivity`. Called from the preference screens
   and (transitively) from the splash activity. We make it a no-op.
2. **`zc.k.h()`** — public boolean gate used by `SettingsActivity` and
   `MainActivity` to decide whether to show the "Remove Ads" / "Personalized
   Ads" rows and whether to intercept the back button. We force it to
   return `false`.
3. **`PHSplashActivity`** — the entry-point activity that, on first launch
   after install, decides whether to route the user to
   `StartLikeProActivity` instead of `MainActivity`. The decision is based
   on `zc.g.i()` (already patched to `true` by the "Unlock premium"
   patch), so we don't need to touch the splash directly.

### Auto-reward ads

In this version of the app, the rewarded-ad code path is **dead** — the
AdManager enum declares `REWARDED` and the configuration registers a
rewarded ad unit ID (`ca-app-pub-4563216819962244/8488859254`), but no UI
code ever calls the rewarded-ad loader. The string resource
`R.string.rewarded` ("You've been rewarded! Ads will be removed shortly.")
is defined in `strings.xml` but never referenced from any smali method.

This patch:

- Forces `isAdEnabled(REWARDED)` to return `true` (defensive — useful if
  a future app update adds a rewarded-ad button).
- Forces `isAdEnabled(<every other type>)` to return `false` (same as
  the "Remove non-rewarded ads" patch — included here so this patch is
  self-contained).

For the practical equivalent of "every reward granted permanently", use
the **Unlock premium** patch.

## 🛠️ Development

### Project structure

```
morphe-patches/
├── patches/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── diozz/cubex/
│       │   ├── shared/
│       │   │   ├── Constants.kt          # Compatibility / target version
│       │   │   └── Fingerprints.kt       # All fingerprints
│       │   └── patches/
│       │       ├── unlock/
│       │       │   └── UnlockPremiumPatch.kt
│       │       ├── ads/
│       │       │   └── RemoveNonRewardedAdsPatch.kt
│       │       ├── protection/
│       │       │   └── RemoveRelaunchProtectionPatch.kt
│       │       └── rewarded/
│       │           └── AutoRewardAdsPatch.kt
│       └── util/
│           └── PatchListGenerator.kt     # From the template
├── extensions/                           # (Empty — no .mpe extensions used)
├── settings.gradle.kts
└── gradle.properties
```

### Reversing notes (how the patches were derived)

The APK was decompiled with `jadx` and disassembled with `apktool`:

```bash
jadx --no-res -d jadx-out diozz.cubex_4.1.0-*.apk
apktool d -f -r -o apk-smali diozz.cubex_4.1.0-*.apk
```

Key findings:

| Original name | Obfuscated | Smali signature |
|---------------|-----------|-----------------|
| `Preferences.hasActivePurchase()` | `zc.g.i()` | `()Z` |
| `PremiumHelper.shouldShowRelaunch()` | `zc.k.h()` | `()Z` |
| `PremiumHelper.launchRelaunchActivity(source)` | `zc.k.n(String)` | `(Lrc/k;Ljava/lang/String;)V` |
| `AdManager.isAdEnabled(adType, ...)` | `rc.a.f(...)` | `(Lrc/a$a;ZLje/d;)Ljava/lang/Boolean;` |
| `AdManager.shouldShowExitAd(activity)` | `rc.a.k(Activity)` | `(Landroid/app/Activity;)Z` |

Fingerprints are anchored on:

- The literal string `"has_active_purchase"` (the SharedPreferences key —
  very stable across ZipoApps SDK versions).
- The full set of enum constants `INTERSTITIAL, BANNER, NATIVE, REWARDED,
  BANNER_MEDIUM_RECT` (the AdManager enum, stable for years).
- The literal string `"disabled"` (the sentinel return value used by
  `isAdEnabled`).
- The literal string `"ph_ad_close_view"` (the resource ID used by the
  exit-ad overlay layout).
- The static call `Lrc/v;->b()Z` (`PhConsentManager.isConsentRequired()`,
  invoked exactly once inside `zc.k.h()`).
- The Kotlin compiler-emitted assertion string
  `"Intent(context, Relaunch…ctivity.ARG_THEME, theme)"` (uniquely
  identifies `zc.k.n()`).

### Testing patches locally

After building the bundle:

```bash
java -jar morphe-cli.jar patch \
    -a diozz.cubex_4.1.0.apk \
    -p patches/build/libs/patches-1.0.0.mpp \
    -o diozz.cubex_4.1.0_patched.apk
```

Then install the patched APK on an Android device or emulator and verify:

- [ ] App launches directly into `MainActivity` (no "Start Like Pro" screen).
- [ ] Advanced Solver (Kociemba) is selectable in the solver selector.
- [ ] Custom color schemes work in `PaletteSettings`.
- [ ] No banner ad at the bottom of any screen.
- [ ] No interstitial ad on navigation.
- [ ] Back-press exits the app cleanly (no exit-ad overlay).
- [ ] Relaunching the app after a "modified" detection does NOT show
      the "Baixe este app na Play Store" screen.

## 📜 License

GPL-3.0 — see [LICENSE](LICENSE). The patches are derived from the
[Morphe Patches template](https://github.com/MorpheApp/morphe-patches-template)
which is also GPL-3.0.
