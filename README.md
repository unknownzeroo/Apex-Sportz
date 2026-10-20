# Apex Sportz

A minimal native Android live-sports app using the supplied API for event schedules and stream choices.

**Supported OS:** Android 7 (API 24) through Android 16 (API 36). The app compiles and targets API 36 and handles Android edge-to-edge system bars.

## API integration and cache

The app requests `https://events.ivan-flux.online/api/v1/user?username=ATV` over HTTPS. It displays live/upcoming/finished counts, event category and name, participants, status, scheduled time and feed update time. Channel names and HTTPS media URLs are read at runtime from `channels_data`.

A private on-device cache stores only sanitized event metadata and counts for offline schedule viewing. It does not store the raw API response, channel URLs, DRM/license data, or custom headers. If the API is unavailable, the last saved schedule is shown and stream buttons have no cached sources. Video is streamed with the player’s normal temporary buffer; media files are not saved offline.

## Playback

The in-app Media3 player supports clear HTTPS HLS (`.m3u8`), DASH (`.mpd`) and MP4 stream URLs. It uses the URL portion before any pipe-delimited request options. It does not use custom User-Agent/Referer/Cookie headers, DRM schemes, license URLs, decryption keys, or API/token fields. HTTP-only sources, unsupported formats, and streams that require DRM or those custom headers may not play; playback of every feed entry is not guaranteed.

## APK builds and automatic releases

The GitHub Actions workflow builds a debug APK on pushes and pull requests to `main`, or when run manually. Download `apex-sportz-debug-apk` from the workflow run's artifacts. To create an automatic GitHub Release with the APK attached, push a version tag such as `v1.0.1`; the workflow builds the APK and publishes release notes and the APK asset. Releases are made only for tags matching `v*`.

Builds use Java 17, Android SDK 36 and Gradle 8.11.1.

## Local build

With Android SDK 36 and Java 17 installed:

```sh
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Icon

The launcher uses the user-provided `DynamicSportsStreamingPlayIcon.png` artwork, resized into Android's standard density buckets.
