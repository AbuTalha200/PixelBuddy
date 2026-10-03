# PixelBuddy – floating pixel AI companion

The Android app lives in `android-app/`. The root-level React/Vite files are only the web
source-browser demo and are **not** part of the APK.

## Build the APK

**Option A – no PC needed (GitHub Actions)**
1. Create a GitHub repo and upload the contents of this folder (keep `.github/` and `android-app/`).
2. Open the **Actions** tab → **Build APK** → run it (it also runs on every push).
3. Download the `PixelBuddy-debug-apk` artifact, unzip, install `app-debug.apk`.

**Option B – Android Studio**
1. Open the `android-app/` folder (Ladybug / 2024.2 or newer, JDK 17).
2. Let Gradle sync (it downloads Gradle 8.9 from `gradle-wrapper.properties`).
3. **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**.
   Output: `android-app/app/build/outputs/apk/debug/app-debug.apk`

**Option C – command line** (Gradle 8.9+, JDK 17, Android SDK 35 installed)
```
cd android-app
gradle assembleDebug        # or assembleRelease (signed with the debug key)
```

## Features added in this version
- Male / Female smiley: switch in the app, or with the swap button in the floating menu.
- Female: when shy, both hands slide in and cover her face.
- Male: when disappointed, a hand comes up for a facepalm and he shakes his head.
- Triggers: long-press the floating smiley, the "Make her shy / Make him facepalm" button,
  or AI advice (HAPPY/EXCITED → she gets shy, SAD/ANGRY → he facepalms).
- Idle bobbing and blinking, redesigned app screen with live preview.

## Fixes
- Overlay service could crash (`startForeground` was skipped when overlay permission was missing).
- Gradle: pinned wrapper version, release build now signed so it installs.
- Android 13+ notification permission is now requested.
- Speech bubble auto-hides; smiley stays fully visible while it speaks.
- Share/Stop buttons refreshed before the capture service updated its state.
- Mouth sprite redrawn as a true smile (the old shape read as a frown/mustache).
