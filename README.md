# 🧩 Miguel's Patches

Morphe patches by [@MiguelNinja19](https://github.com/MiguelNinja19).
Multiple apps supported — see the patch list below.

## 📥 Add to Morphe Manager

Tap this link on your Android device:

[Add Miguel's Patches to Morphe](https://morphe.software/add-source?github=MiguelNinja19/miguel-morphe-patches)

Or open Morphe Manager → **Sources → Add** and paste:

```
https://github.com/MiguelNinja19/miguel-morphe-patches
```

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.6.2](https://github.com/MiguelNinja19/miguel-morphe-patches/releases/tag/v1.6.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details open>
<summary>📦 Hunter Assassin&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 2.041 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Auto reward ads](#auto-reward-ads) | When a rewarded ad is closed, the game always thinks you watched the entire ad. You get the reward even if you skip or close the ad immediately. |  |
| [Free in-app purchases](#free-in-app-purchases) | Skips Google Play Billing and reports the purchase as successful immediately with fake receipt data. When you tap buy, the item is granted without payment or Play Store popup. |  |

</details>

<details open>
<summary>📦 Plants vs. Zombies FREE&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 3.16.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Free in-app purchases](#free-in-app-purchases) | When you try to buy something, the purchase is immediately marked as successful without contacting Google Play. The EA Nimble MTX system receives a null error (success) and grants the item. |  |
| [Free reward ads](#free-reward-ads) | When the game tries to show a rewarded ad, it immediately fires the ad-clicked and ad-closed callbacks without actually showing the ad. You get the reward instantly. |  |
| [Remove ads](#remove-ads) | Prevents video ads from loading. When the game tries to load an ad, nothing happens. Ads will not be shown. |  |

</details>

<details open>
<summary>📦 Anger of Stick 5&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.1.94 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Free in-app purchases](#free-in-app-purchases) | Skips Google Play Billing and credits IAP items (gem packs, coin packs, starter packs) directly. Lets the original failure path run after crediting to close the Contacting screen naturally. Also disables the startup purchase-restore flow. |  |

</details>

<details open>
<summary>📦 CubeX Solver&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.1.1 | 4.1.0 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock premium](#unlock-premium) | Unlocks all premium-gated features (Advanced Solver / Kociemba, custom color schemes, VIP support, no relaunch screens). Returns true from PremiumHelper.hasActivePurchase() so the entire app behaves as if a premium subscription is active. |  |

</details>

<!-- PATCHES_END -->

## 🚀 How to use

1. Install [Morphe Manager](https://morphe.software) on your Android device.
2. Add this repo as a source (see above).
3. Select the app you want to patch (from APKMirror, APKPure, or pulled from your device).
4. Choose the patches you want.
5. Tap **Patch** and install the resulting APK.

## 🛠️ Development

### Project structure

```
miguel-morphe-patches/
├── patches/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── <package>/                  # one folder per supported app
│       │   ├── shared/
│       │   │   ├── Constants.kt         # Compatibility / target version
│       │   │   └── Fingerprints.kt      # All fingerprints for the app
│       │   └── patches/
│       │       └── <category>/
│       │           └── <PatchName>.kt
│       └── util/
│           └── PatchListGenerator.kt    # From the Morphe template
├── extensions/                          # Optional .mpe extensions
├── settings.gradle.kts
└── gradle.properties
```

### Adding a new app

1. Create a new folder under `patches/src/main/kotlin/<package>/` for the new app.
2. Add a `Constants.kt` with the `Compatibility` declaration (package name, supported versions).
3. Add a `Fingerprints.kt` with fingerprints for the methods you want to patch.
4. Add patch files under `patches/<category>/` (e.g. `unlock/`, `ads/`, `protection/`).
5. Commit with `feat: add patches for <app name>` to trigger a new release.

### Building locally

```bash
git clone https://github.com/MiguelNinja19/miguel-morphe-patches.git
cd miguel-morphe-patches

# Set up a GitHub PAT with read:packages scope
export GITHUB_ACTOR=<your-github-username>
export GITHUB_TOKEN=<your-pat>

# Build the patch bundle
./gradlew buildAndroid

# Patch an APK using Morphe CLI
java -jar morphe-cli.jar patch \
    -a app.apk \
    -p patches/build/libs/patches-*.mpp \
    -o app_patched.apk
```

### Reversing notes

Patches are derived by decompiling the target APKs with `jadx` and `apktool`:

```bash
jadx --no-res -d jadx-out app.apk
apktool d -f -r -o apk-smali app.apk
```

Fingerprints are anchored on:

- Stable string literals (SharedPreferences keys, assertion strings, layout names)
- Unique method calls (e.g. SDK internal callbacks)
- Resource name strings (`ph_ad_close_view`, `activity_relaunch_premium`, etc.)

We avoid pinning `definingClass` when possible because obfuscated class names
can change between APK versions and build configurations.

## 📜 License

GPL-3.0 — see [LICENSE](LICENSE). Derived from the
[Morphe Patches template](https://github.com/MorpheApp/morphe-patches-template).
