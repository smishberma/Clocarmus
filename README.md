# Clocarmus

Clocarmus is a planned offline music player for aftermarket Android car infotainment systems, built with **Kotlin and Jetpack Compose**. It combines local music playback with a large digital clock in a landscape interface designed for car screens.

The app is intended to run on **Android 6.0 (API 23) and above**, install through an APK, and operate without accounts, cloud services, an internet connection, or Google Play Services.

## Current status

**Main player visual shell implemented.** The app renders the reference player with bundled Inter fonts, sample album artwork and metadata, a live clock/date, full-screen landscape layout, seek and media-volume sliders, and local transport/shuffle/repeat state. Music playback, library discovery, playlists, and browsing/settings screens are not connected yet. Play/pause currently changes the visual control state; it does not play audio. Normal launch starts paused.

The first release will be a personal-use APK validated on an Android Virtual Device (AVD) with 100 tracks. Physical head-unit testing will follow. There will be no artificial library import limit, but larger-library performance is not yet validated.

See [PLAN.md](PLAN.md) for agreed requirements, implementation phases, acceptance criteria, assumptions, and open risks.

## Planned experience

### Player and clock

- Full-screen, landscape-only interface with a fixed 50/50 split.
- Left side: album artwork, track title, artist, album, year, and playback controls.
- Right side: persistent digital clock, day, and date with configurable formats.
- Browsing and settings replace the left-side content while the clock and playback controls remain available.
- After selecting a song, return to the main player after 15 seconds of inactivity. Editing playlists, typing in search, or entering settings cancels that return.
- Keep the screen awake while the app is visible.
- Black, dark gray, dark blue, and dark burgundy background presets, with Inter typography and high-contrast controls.

### Local music library

- Read music from selected folders on internal storage and accessible USB drives or SD cards, including subfolders.
- Remember selected sources, refresh on detected reconnection, and offer manual rescanning.
- Browse tracks, artists, albums, genres, folders, and playlists; search songs, albums, and artists.
- Create, rename, delete, and reorder playlist tracks; mark favorites through immediately saved membership controls.
- Show up to 100 recently added tracks and 100 recently played distinct tracks.
- Preserve unavailable tracks and playlist references when a drive disconnects, and recognize renamed or moved files where identity can be established safely.
- Keep identical copies at different storage locations as separate entries.
- Remove selected sources only after confirmation, without deleting music files.

### Playback

- Target formats: MP3, M4A containing AAC, OPUS, FLAC, OGG Vorbis, and WAV.
- Background playback, seeking, 10-second backward/forward controls, shuffle, repeat one, and repeat all.
- Play the selected collection in displayed order, including all matching songs for search results.
- Control Android media volume and respond to supported steering-wheel/media controls.
- Restore the selected track, position, queue, shuffle order, and repeat mode **paused** after restart or sleep.
- Leave an ongoing background playback session unchanged when returning to the app.
- Temporarily lower volume for navigation prompts; pause for calls or another music app and wait for explicit Play afterward.
- Skip unplayable queued tracks with a brief message; pause if the drive holding the current track is removed.
- Attempt automatic UI launch after boot without starting playback, where the device permits it.

### Offline operation and diagnostics

All library data, preferences, and playback state will stay on the device. Local diagnostics will support manually exported text reports, with no automatic uploads and file paths omitted by default.

Streaming, cloud sync, accounts, online metadata/artwork lookup, metadata editing, portrait layouts, image backgrounds, and playlist/favorites import/export are outside v1. Crossfade, equalizer, and guaranteed gapless playback are deferred.

## Design references

The [design directory](design/) contains the screen mockups and branding assets:

- [Main player](design/01-main-player.png): primary visual reference.
- [App design overview](design/app-design-ref.png): onboarding, browsing, playlist, and settings flows.
- [Design system](design/design-system-ref.png): typography, colors, icons, and controls.

![Clocarmus main player design mockup](design/01-main-player.png)

*Design reference, not a screenshot of the current implementation.*

## Development setup

### Current configuration

| Setting | Value |
| --- | --- |
| Language / UI | Kotlin / Jetpack Compose |
| Application ID and namespace | `com.example.clocarmus` |
| Minimum Android SDK | 23 (Android 6.0) |
| Compile / target SDK | 35 |
| Java source / target compatibility | 17 |

Dependency versions are declared in [gradle/libs.versions.toml](gradle/libs.versions.toml). Exact playback, persistence, and background-work dependencies will be verified for Android 6 compatibility before implementation.

### Run in Android Studio

1. Open the project directory in Android Studio.
2. Configure an Android SDK installation with Platform 35 and a Gradle JDK compatible with the checked-in build tooling.
3. Let Gradle sync and install any requested SDK components.
4. Select an AVD or connected Android device and run the `app` configuration.

The current app displays the main player visual shell. The offline requirement applies to the finished app; initial development setup may need internet access to download dependencies and SDK components.

### Build from the command line

Configure the Android SDK using `ANDROID_HOME` or `sdk.dir` in your local `local.properties`, and use a JDK compatible with the checked-in Gradle and Android Gradle Plugin versions.

```sh
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To install on a running emulator or connected device:

```sh
./gradlew installDebug
```

Use the checked-in Gradle wrapper. Keep `local.properties`, generated build directories, and signing secrets out of version control.

## Project layout

```text
app/src/main/          Android manifest, Kotlin source, and resources
app/src/main/java/com/example/clocarmus/
                       App entry point and Compose theme
design/               UI mockups, design system, and logo assets
gradle/               Gradle wrapper and dependency version catalog
PLAN.md               Agreed specification and implementation plan
```

## Validation and device limitations

Initial acceptance testing will cover 100 mixed-format tracks, nested folders, missing metadata, unavailable sources, playlists, background playback, paused restoration, and the fixed landscape layout on minimum and modern Android AVDs.

Real USB/SD behavior, steering-wheel controls, ignition and sleep cycles, boot launch, audio interruptions, and OEM process management require physical head-unit testing. Boot launch is a best-effort convenience, not a universal compatibility guarantee. Codec variants, file-identity ambiguity, and performance on low-powered hardware remain validation risks documented in [PLAN.md](PLAN.md).

## Main screen visual comparison

Build with `./gradlew assembleDebug`, start the **headunit_a6** AVD, then run:

```sh
python3 scripts/compare-main-player.py --install
```

The script requires `adb` and Python Pillow. It checks the AVD name, installs the APK when requested, and opens the debug-only `reference_preview` fixture with the mockup's Friday / 10:30 AM / 30-01-2026 clock and sample progress. Normal launches use the device clock. The debug fixture retains the mockup's progress-marker placement, which differs slightly from its elapsed/duration ratio.

Artifacts are saved under `build/visual-comparison/final/`: the AVD screenshot, reference resized to the display dimensions, pixel difference, side-by-side comparison (reference left), and raw mean RGB difference. The 2000×1236 mockup is adapted to the AVD's native 1280×720 screen. Text rasterization can differ from the mockup; the numeric difference is a comparison aid, not a claim of pixel identity.

See [asset provenance and font license](design/ASSETS.md). The album cover is a sample fixture, not a bundled audio track.
