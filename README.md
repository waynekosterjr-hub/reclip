# <span style="color:#17C7FF;">ReClip</span> <span style="color:#EC168E;">Local Media Engine</span> 🎧

<p align="center">
  <b>Fast, private media downloads on Android.</b><br/>
  Paste or share links, fetch metadata, choose quality, and download on-device.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-Native-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img alt="Engine" src="https://img.shields.io/badge/Engine-yt--dlp%20%2B%20FFmpeg-111827?style=for-the-badge"/>
  <img alt="Desktop Mode" src="https://img.shields.io/badge/Desktop%20Mode-LAN-0EA5E9?style=for-the-badge"/>
  <img alt="License" src="https://img.shields.io/badge/License-MIT-A855F7?style=for-the-badge"/>
</p>

## ✨ Why ReClip

- ⚡ Instant fetch + local processing
- 🔒 On-device workflow (privacy-first)
- 🎵 Pro audio profiles (MP3/FLAC options)
- 🎬 Video quality selection with saved preference
- 🖥️ Desktop Mode (phone-hosted over LAN)
- 📥 Unified download history across UI surfaces
- 🧪 Runtime diagnostics for engine transparency

## 🚀 What It Supports

- YouTube + YouTube Music
- Spotify (with native entitlement gating)
- TikTok, Instagram, X/Twitter, Reddit, Twitch, Vimeo, SoundCloud
- Additional sources supported by `yt-dlp`

## 🧠 Core Architecture

- **Android host app**: Java/Kotlin + WebView UI
- **Media engine**: Python (`yt-dlp`) + bundled `ffmpeg/ffprobe`
- **Billing/entitlements**: RevenueCat (native-enforced)
- **Desktop Mode**: local HTTP server + PIN pairing

## 🛠️ Build (Windows)

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Wireless ADB install:

```powershell
adb devices -l
adb -t <transport_id> install -r app\build\outputs\apk\debug\app-debug.apk
```

## ✅ Quick Validation

1. Paste/share URL and verify auto-fetch.
2. Confirm quality/profile selection.
3. Start download and verify progress + completion.
4. Verify file in `Downloads\ReClip`.
5. Open Runtime Status and confirm engine health.

## 📂 Key Files

- Android entry: [MainActivity.java](C:/Users/micry/Documents/Codex/2026-05-16/pull-my-latest-update-on-from/app/src/main/java/com/reclip/app/MainActivity.java)
- Desktop server: [DesktopServerManager.java](C:/Users/micry/Documents/Codex/2026-05-16/pull-my-latest-update-on-from/app/src/main/java/com/reclip/app/DesktopServerManager.java)
- Desktop UI: [desktop.html](C:/Users/micry/Documents/Codex/2026-05-16/pull-my-latest-update-on-from/app/src/main/assets/www/desktop.html)
- Mobile UI: [index.html](C:/Users/micry/Documents/Codex/2026-05-16/pull-my-latest-update-on-from/app/src/main/assets/www/index.html)
- Engine: [reclip_engine.py](C:/Users/micry/Documents/Codex/2026-05-16/pull-my-latest-update-on-from/app/src/main/python/reclip_engine.py)

## 📝 Release Workflow Note

Before each push, refresh this README summary to reflect:
- latest UX changes,
- supported flows,
- build/install status,
- any new Settings/Desktop behavior.

That keeps `Codex` branch docs aligned with shipped behavior.
