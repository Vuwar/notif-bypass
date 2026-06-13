# NotifBypass

A lightweight, single-purpose Android utility that intercepts notifications from
**WhatsApp**, **Instagram**, and **TikTok**, and fires a unique custom vibration
(and optional sound) **only** when the notification is from a specific person —
even while the phone is in **Silent** or **Do Not Disturb**.

Target device: **Honor 70 / MagicOS (Android 12–13+)**. `minSdk 26`, `targetSdk 34`.

---

## Project layout

```
NotifBypass/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/notifbypass/
        │   ├── MainActivity.kt          # tiny toggle/status UI
        │   ├── MyNotificationListener.kt # core listener + bypass vibration
        │   ├── KeepAliveService.kt       # foreground service for persistence
        │   └── BootReceiver.kt           # restarts keep-alive after reboot
        └── res/
            ├── values/{strings,colors,themes}.xml
            ├── drawable/{ic_launcher_foreground,ic_stat_bypass}.xml
            ├── mipmap-anydpi-v26/ic_launcher.xml
            └── raw/README_PLACEHOLDER.txt # drop emergency_tone.mp3 here
```

---

## Opening / building

This scaffold contains every **text** file. Three things are **binary** and were
intentionally NOT generated — let Android Studio create them on first open:

1. **`gradle/wrapper/gradle-wrapper.jar`** — Android Studio regenerates the wrapper
   automatically (or run `gradle wrapper` if you have a system Gradle). The matching
   `gradle-wrapper.properties` is already provided.
2. **`local.properties`** — created automatically; it stores your machine's
   `sdk.dir` path. (Git-ignored.)
3. **`res/raw/emergency_tone.mp3`** — optional. Only needed if you enable
   `playEmergencySound()`. See `res/raw/README_PLACEHOLDER.txt`.

Steps:
1. **Android Studio → Open** → select this folder.
2. Let it sync Gradle (it will fetch the wrapper + dependencies).
3. Plug in the Honor 70 (USB debugging on) → **Run**.

> No `gradlew` scripts are committed because they are useless without the binary
> `gradle-wrapper.jar`; Android Studio recreates the full wrapper on import.

---

## Customizing

In `MyNotificationListener.kt`:
- `SPECIFIC_PERSON_NAME` → set to the real contact name to match (case-insensitive `contains`).
- `VIBRATION_PATTERN` / `VIBRATION_AMPLITUDES` → tweak the haptic signature.
- `TARGET_PACKAGES` → add/remove apps.

---

## Manual on-device setup (required)

The listener permission cannot be auto-granted, and MagicOS aggressively kills
background apps. After installing:

**Required**
1. **Notification Access** — open the app → *Grant Notification Access* → enable
   **NotifBypass**. Without this, nothing fires.

**Recommended for MagicOS persistence**
2. **Disable Battery Optimization** — app button → set NotifBypass to *Don't optimize*.
3. **Lock app in Recents** — open Recents, long-press the NotifBypass card → lock icon.
4. **App launch** — *Settings → Apps → NotifBypass → Launch*: turn off "Manage
   automatically"; enable Auto-launch, Secondary launch, Run in background.
5. **Startup apps** — *Phone Manager → Startup apps*: allow NotifBypass.

**DND / Silent**
6. The `USAGE_NOTIFICATION_RINGTONE` haptic is exempt from most suppression. If a
   MagicOS build still blocks it, enable `playEmergencySound()` (routes through the
   ALARM stream, which is almost always exempt).

---

## CI/CD — automatic APK builds & GitHub Releases

Workflow: `.github/workflows/release.yml`

**What it does**
- Triggers on every push to `main`/`master`, on `v*` tags, and manually
  (*Actions → Build & Release APK → Run workflow*).
- Builds a release APK with Gradle (JDK 17, Android SDK, Gradle 8.7).
- Uploads the APK as a workflow **artifact** and publishes it to **GitHub Releases**.
  - **Tag push** (e.g. `git tag v1.1.0 && git push --tags`) → a normal release named after the tag.
  - **Branch push** → a rolling **pre-release** tagged `ci-<run-number>`.

**Signing — two modes (zero config works out of the box):**

| Mode | When | Result |
|------|------|--------|
| Debug-signed | No secrets set | APK installs fine for personal sideloading |
| Release-signed | Keystore secrets set | Properly signed release APK |

To enable real release signing, generate a keystore once:

```bash
keytool -genkey -v -keystore release.keystore \
  -alias notifbypass -keyalg RSA -keysize 2048 -validity 10000

# Base64-encode it for the GitHub secret:
base64 -w0 release.keystore   # (macOS: base64 -i release.keystore)
```

Then add these in **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | the base64 string from above |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `notifbypass` (or your alias) |
| `KEY_PASSWORD` | key password |

The workflow auto-detects the secrets: if present it signs with your keystore,
otherwise it falls back to the debug key. **Keep `release.keystore` private — do
not commit it.**

> Note: CI runs Gradle directly (`gradle assembleRelease`) rather than `./gradlew`,
> since the binary `gradle-wrapper.jar` isn't committed.

---

## Caveats (honest)

- **Silent/DND bypass is best-effort.** The framework honors ringtone-class
  vibrations, but OEM skins can add their own suppression. The ALARM-stream sound
  path is the more bulletproof fallback.
- **Background survival is the real fragility point**, not the code — the
  lock-in-recents + battery-optimization-off steps matter most.
