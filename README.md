# ArenaLive

A minimal native Android live-sports app using the supplied API for event schedules and stream choices.

**Supported OS:** Android 7 (API 24) through Android 16 (API 36). The app compiles and targets API 36 and handles Android edge-to-edge system bars.

## API integration

The app requests `https://events.ivan-flux.online/api/v1/user?username=ATV` over HTTPS. It displays live/upcoming/finished counts, event category and name, participants, status, scheduled time and feed update time. For each event, it reads channel display names and HTTPS media URLs from `channels_data`.

## Playback

The in-app Media3 player supports clear HTTPS HLS (`.m3u8`), DASH (`.mpd`) and MP4 stream URLs. It uses the URL portion before any pipe-delimited request options. It does not use custom User-Agent/Referer/Cookie headers, DRM schemes, license URLs, decryption keys, or the API/token fields. HTTP-only sources, unsupported formats, and streams that require DRM or those custom headers may not play; this project does not promise playback of every feed entry.

## Build APK

GitHub Actions workflow `.github/workflows/android-apk.yml` builds a debug APK on pushes and pull requests to `main`, and can also be started manually with **Actions → Android APK → Run workflow**. Download `arenalive-debug-apk` from the completed run's artifacts. It uses Java 17, Android SDK 36 and Gradle 8.11.1.

## Local build

With Android SDK 36 and Java 17 installed:

```sh
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Icon

The launcher uses the user-provided `DynamicSportsStreamingPlayIcon.png` artwork, resized into Android's standard density buckets.
