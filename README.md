# ArenaLive

A minimal native Android sports companion for live and upcoming event schedules. The app uses the supplied FluX-oW endpoint for event metadata and shows live/upcoming/finished counts, category, event name, participants, status, start time and feed update time.

**Supported OS:** Android 7 (API 24) through Android 16 (API 36). The project compiles and targets API 36 and includes edge-to-edge system-bar handling for Android 15/16.

## Data source

The app requests `https://events.ivan-flux.online/api/v1/user?username=ATV` over HTTPS. It intentionally parses only the top-level event summary/count fields and each event's `id`, `title`, `cat` and `eventInfo` metadata. It does not access, save, display, or play `channels_data`, stream manifests, request headers, cookies, DRM schemes, or license/key fields found in the source response.

## Official broadcaster links

For events for which you have permission to link, you may add a broadcaster's official HTTPS web or app link in `app/src/main/assets/official_broadcasts.json`. Keys are event IDs from the feed, and values must be regular HTTPS URLs (not stream manifests). Example:

```json
{
  "4527": "https://official-broadcaster.example/watch"
}
```

The app opens these curated links with Android's standard app/browser chooser. No broadcaster URLs are included by default because the supplied API's channel entries do not establish authorization.

## Build APK

GitHub Actions workflow `.github/workflows/android-apk.yml` builds a debug APK on pushes and pull requests to `main`, and can also be started manually with **Actions → Android APK → Run workflow**. Download `arenalive-debug-apk` from the completed run's artifacts. It builds with Java 17, Android SDK 36 and Gradle 8.11.1.

## Local build

With Android SDK 36 and Java 17 installed:

```sh
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Icon

The launcher uses the user-provided `DynamicSportsStreamingPlayIcon.png` artwork, resized into Android's standard density buckets.
