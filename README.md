# Clocarmus

**Current implementation stage (2026-11-12): Real playback and lifecycle — in progress.** Normal browsing uses the real local library and now connects to a process-owned playback controller with platform decoding, a media-playback foreground service, media-session controls, focus/output interruption handling, and atomic paused-restoration checkpoints. Reference previews retain demo fixtures. Genuine Shuffle now includes collection refresh, consumption/replay, durable collection history, linked queue/cursor recovery and virtualized Queue rows; general mixed-duplicate performance, full large-library acceptance, opt-in startup/sleep autoplay and broader hardware acceptance remain pending. See [implementation contracts and limits](docs/compatibility-and-contracts.md). Historical demo-stage validation notes below describe their original builds.

Clocarmus is a planned offline music player for aftermarket Android car infotainment systems, built with **Kotlin and Jetpack Compose**. It combines local music playback with a large digital clock in a landscape interface designed for car screens.

The app is intended to run on **Android 6.0 (API 23) and above**, install through an APK, and operate without accounts, cloud services, an internet connection, or Google Play Services.

## Current status

**Main player, menu, All Tracks, Artists, Albums, Playlists, Genres, Folders, Splash, Settings, and Track Options demo flows are implemented.** The app uses bundled Inter fonts, supplied vectors, a persistent live clock, full-screen landscape layout, media-volume controls, and local transport/shuffle/repeat state. Browsers support selection and retained navigation state, with search in the track, artist, album, genre, and folder browsers. Playlists supports built-in lists, custom playlist management, membership editing, and drag reordering. Track Options adds fixed-target membership editing and occurrence-aware next/stop scheduling. Normal launch restores the real playback queue paused; qualifying opt-in autoplay remains pending. The five-screen welcome flow (046–050) is implemented. Real library discovery and initial service-backed audio playback are connected; remaining acceptance work is listed in the implementation contracts.

The first release will be a personal-use APK validated on an Android Virtual Device (AVD) with 100 tracks. Physical head-unit testing will follow. There will be no artificial library import limit, but larger-library performance is not yet validated.

See [PLAN.md](PLAN.md) for agreed requirements, implementation phases, acceptance criteria, assumptions, and open risks.

## Planned experience

### Player and clock

- Full-screen, landscape-only interface with a fixed 50/50 split.
- Left side: album artwork, track title, artist, album, year, and playback controls.
- Right side: persistent digital clock, day, and date with configurable formats.
- Browsing and settings replace the player-side content while the clock and playback controls remain available.
- Enable “Change to right-hand drive (RHD)” in Settings > Display to place the clock on the left and the player, browsers, Settings, and player panels on the right. The 50/50 split and centered divider styles remain the same.
- After selecting a song, return to the main player after 15 seconds of inactivity. Editing playlists, typing in search, or entering settings cancels that return.
- Keep the screen awake while the app is visible.
- Default (black), Indigo, Coffee, and Grey solid backgrounds plus Default, Modern, Potion, and Lava gradients, with Inter typography and high-contrast controls.

### Local music library

- Read music from selected folders on internal storage and accessible USB drives or SD cards, including subfolders.
- Remember selected sources, refresh on detected reconnection, and offer manual rescanning.
- Browse tracks, artists, albums, genres, folders, and playlists; search songs, albums, and artists.
- Create, rename, delete, and reorder playlist tracks; mark favorites through immediately saved membership controls.
- Show up to 50 recently added tracks and 50 recently played distinct tracks.
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

## Main-player inactivity fading

On the active player, Menu, Add to playlist, Shuffle, Repeat, and both 10-second skip controls fade to 20% opacity over 500 ms after 10 seconds without touch. Other elements retain their normal appearance. A touch on either pane restores the controls immediately and still performs its normal action. Holds, drags, and multiple fingers keep them visible until the last release.

Dialogs and backgrounding suspend and reset the timer; dismissal or foreground return starts a fresh interval. The timer is transient, independent of playback and hardware keys, and disabled in static reference previews. Run the timing/cancellation tests with `./gradlew testDebugUnitTest`.

## Main screen visual comparison

Build with `./gradlew assembleDebug`, start the **headunit_a6** AVD, then run:

```sh
python3 scripts/compare-main-player.py --install
```

The script requires `adb` and Python Pillow. It checks the AVD name, installs the APK when requested, and opens the debug-only `reference_preview` fixture with the mockup's Friday / 10:30 AM / 30-01-2026 clock and sample progress. Normal launches use the device clock. Seek progress consistently reflects elapsed time divided by duration, including in the comparison fixture.

Artifacts are saved under `build/visual-comparison/final/`: the AVD screenshot, reference resized to the display dimensions, pixel difference, side-by-side comparison (reference left), and raw mean RGB difference. The 2000×1236 mockup is fitted proportionally inside each half of the AVD's native 1280×720 screen. The screen uses one uniform scale per half, square artwork, and proportional SVG icons. Text has no horizontal/vertical transforms; long clock values shrink by font size if needed. The artwork placeholder and supplied icons intentionally differ from the original mockup, so pixel differences are a visual aid rather than an acceptance threshold.

See [asset provenance and font license](design/ASSETS.md). The player uses `design/assets/main-player/no-album-art.svg` as its artwork; no audio track is bundled.

## Menu screen visual comparison

```sh
./gradlew assembleDebug
python3 scripts/compare-main-player.py --screen menu --install
```

This uses `design/02-menu-screen.png`, the same proportional fitting and fixed clock fixture as the player comparison, and the supplied vectors in `design/assets/menu-page/`. Artifacts are saved under `build/visual-comparison/menu/`. Normal launch still opens the player; the menu fixture requires a debug build and `reference_preview=true` with `menu_preview=true`.

The menu shares media volume, transport state, and the persistent clock with the player. Player fading resets when the menu opens and restarts on returning. All Tracks opens the demo track menu, Artists opens the artist browser, Albums opens the album browser, Playlists opens the playlist overview, and Genres opens its five-screen browsing flow; Folders opens its demo browser and Settings opens the settings flow.

Regenerate only the menu vectors with `python3 scripts/import-player-assets.py --group menu-page`. The importer also accepts `--group main-player` and `--group shared`; complex SVG filters are rejected by Android's vector converter and require separate asset preparation.

Validated on `headunit_a6` at 1280×720: menu screenshot comparison, all category controls exposed, category/Settings dialog dismissal, play/pause preserved through navigation, and both Back actions. `assembleDebug`, `testDebugUnitTest` (11 tests), and `lintDebug` pass. Pixel comparison remains a review aid; supplied SVG details and Inter rendering differ from the raster mockup.

Menu category icons, Back, and Settings use their supplied normal/pressed SVGs while held; releasing or dragging outside restores their normal state. Play, Pause, Previous, and Next use their supplied shared pressed SVGs while held on the player, menu, and All Tracks screens; release or cancellation restores the normal asset. Genres is centered directly below Artists. The updated artwork SVG is bundled as an Android vector with its gradients preserved; regenerate it with `python3 scripts/import-player-assets.py --group main-player --asset no-album-art`.

## Track menu

Menu → All Tracks opens the screen based on `design/03-track-screen.png`, using the existing uniform scale, fixed 50/50 split, bundled Inter, and supplied SVGs. The 28 fixed sample titles cover A–Z, with two A and two S titles. They exercise the scroll window without requiring audio files or storage access.

Swipe the list to move the scrollbar, drag the white pill to scroll quickly, or tap the rail to jump. A letter bubble follows the pill during scrolling; dragging beyond either endpoint clamps at the beginning/end. Search filters the sample titles and Show all clears the filter. A list that fits the window has no draggable pill. The scrollbar also supports accessibility progress actions.

Each row shows its title above an Artist - Album metadata line (demo placeholders). On the main player, Year remains directly below Album after selection and track changes; demo entries use a Year placeholder, while missing real year metadata displays Unknown year. Selecting a row gives it a white background with black text and a black play/pause indicator, and updates the player's demo title/queue. Previous/Next follow the displayed collection captured at selection; Play/Pause updates the selected row indicator. Screen Back and Android Back return through menu → player. Scroll position, search, and selection survive that navigation. Playback remains a visual demo; no audio is connected yet.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen tracks --install
```

The debug fixture uses `reference_preview=true` and `tracks_preview=true`. Screenshots and proportional comparisons are saved to `build/visual-comparison/tracks/`; the raster reference has different titles from the demo list. Regenerate the two new vectors with `python3 scripts/import-player-assets.py --group track-menu`.

## Return to player after selection

Selecting a track keeps browsing open, then returns directly to the player after 15 seconds without interaction. Taps anywhere (including the clock), transport/volume actions, keyboard input, and scrolling reset the countdown. Holds and ongoing scroll gestures/flings postpone it until the final release/scroll stop.

Typing in search or entering Settings/Playlists cancels the return until another track is selected. Opening the search dialog without typing suspends the countdown; closing it starts a fresh interval if still armed. Backgrounding also suspends it and foregrounding starts a fresh interval. Manual return to the player disarms it, so reopening the menu alone cannot schedule another return. The timer is transient across recreation and independent of main-player control fading; returning starts that screen's normal fading interval.

Run `./gradlew testDebugUnitTest` for exact-deadline, reset, cancellation, scrolling, multi-touch/hold, lifecycle suspension, and rearming checks.


## Artists flow

Menu → Artists opens the artist list; tapping a name opens its detail pane with Play all and Browse → Tracks / Albums. All four panes share the existing fixed split, clock, volume, transport, Inter typography, and list controls. Track Artist tags determine grouping; distinct album titles determine counts, with Unknown artist / Unknown album / Unknown year fallbacks. Disconnected entries remain in the groups. This is a demo shell with separate artist fixtures; the real offline library and audio service are still pending.

Search filters the current field (artist name, song title, or album title). Back and Android Back follow Tracks/Albums → artist detail → Artists → Menu. Queries and scroll positions are retained per list and artist, and the last-opened artist stays highlighted independently of the selected track. Album taps open shared album options for the whole album, including compilation performers. Individual track selection queues the displayed matching tracks and arms the existing 15-second return. Play all queues the artist's tracks alphabetically, disables shuffle, selects the first available entry, and returns immediately to the player. Queue selection uses track IDs so the same title in different artists' fixtures stays distinct.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen artists --install
python3 scripts/compare-main-player.py --screen artist-detail
python3 scripts/compare-main-player.py --screen artist-tracks
python3 scripts/compare-main-player.py --screen artist-albums
```

Comparisons are saved under `build/visual-comparison/<screen>/`. These debug-only fixtures reproduce Ripple and the mockups' visible Pebble–Summit rows; the album preview uses a separate sample collection to reproduce the supplied years (the artist-list mockup specifies one Ripple album while the album mockup shows several). Normal browsing uses consistent artist groups and album counts. The comparison script settles each preview after immersive-mode resizing. Supplied vectors, font rasterization, the screen divider, and a scrollbar positioned from the actual collection length introduce small differences from the PNGs.

Validated on `headunit_a6` (API 23, 1280×720): all four screenshot comparisons; Menu navigation; artist-scoped search and query restoration through Back, player return, and reentry; exact artist/track scroll restoration; last-opened artist highlighting; album highlighting; delayed individual-track return; immediate Play all return and queue advancement. The unit suite has 32 passing tests, including grouping, distinct album counts with unavailable copies, metadata fallbacks, alphabetical ordering, stable IDs, and existing inactivity/scroll checks. `assembleDebug` and `lintDebug` pass.

Artist options uses the supplied normal/pressed SVG pairs for Play all, Tracks, and Albums. Holding a button changes its icon to the supplied pressed color; release or cancellation restores the normal icon. Labels stay white, and actions run on release, matching Back and transport controls.


## Albums flow

Menu → Albums opens an alphabetical list with effective Album Artist, year, and song count. Album options provides Play album, Album Tracks, and an Artist shortcut. Album Tracks shows zero-padded numbers and durations in numeric disc/track order. Missing metadata uses Unknown album / Unknown artist / Unknown year, `--`, and `--:--`. Album years use the most frequent known year, earliest on ties. Physical copies and disconnected entries remain in counts.

Albums group by title + effective Album Artist. Artist album rows use the same identity, while main artist counts retain distinct titles. Album taps from Artists open the whole album. The Artist shortcut uses explicit Album Artist scope, including Various Artists; the main Artists menu continues using track Artist. Back retraces the entry path and restores list queries, scroll, and last-opened selection. Search filters album titles or song titles within the current collection; options search opens Album Tracks search.

Play album captures the entire ordered album queue, disables shuffle, starts the first available demo track, and immediately returns to the player. Selecting an individual track captures the displayed matching queue and keeps the existing 15-second return without changing shuffle. Unavailable selections skip only within that queue; an exhausted collection stops. This remains a visual/demo implementation: repository scanning and real audio playback are pending.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen albums --install
python3 scripts/compare-main-player.py --screen album-options
python3 scripts/compare-main-player.py --screen album-tracks
```

Comparison artifacts are saved under `build/visual-comparison/<screen>/`. Debug fixtures use `albums_preview=albums`, `album-options`, or `album-tracks` with `reference_preview=true`. The list fixture reproduces the reference album rows, while normal browsing also includes the artist fixtures and a Various Artists compilation. Album Tracks uses a proportional scrollbar thumb; the supplied normal/pressed Album Tracks vectors retain their original geometry and colors.

Validated on `headunit_a6` (API 23, 1280×720): three reference comparisons; Menu Albums entry; scoped album search and filter restoration through Back; numbered/duration rows; options search opening Album Tracks search; individual selection remaining in browsing; immediate Play album return at track 1; Artist shortcut and Back to album options; an artist album opening both performers of a compilation; Various Artists scope and Back to the originating artist. All 38 unit tests pass, including album identity, metadata fallbacks, year ties, numeric disc/track ordering, disconnected copies, scope separation, filtered ordering, and finite unavailable selection. `assembleDebug` and `lintDebug` pass. Real audio and physical-head-unit behavior remain unvalidated.

## Long music titles

Overflowing track, artist, and album text scrolls automatically on the player and all track, artist, and album browsing screens, including options headings and row metadata. Each marquee pauses for 1.5 seconds before scrolling and between continuous loops. Text that fits stays still; long text keeps its original font size and stays clipped inside its label, clear of playback indicators and the clock. Changing a title restarts its marquee from the beginning.

The demo library includes **Departing From a Station That Was Demolished Before It Was Ever Built**, by **Bartholomew Quillfeather-Ashworth & The Interdimensional Orchestra**, on **The Unabridged and Occasionally Contradictory Chronicles of Seventeen Thousand Midnight Trains**. Find it through All Tracks, Artists, or Albums to exercise long labels throughout each flow; select it to view all three marquees on the main player. Existing reference artist fixtures keep their original metadata.

## Playlists

Menu > Playlists follows the updated `011`–`017` references. Favorites, Recently added, and Recently played appear above alphabetically sorted custom playlists in one scrolling list. The supplied white/magenta plus icon appears only on the overview. Create stays on the overview; blank, reserved, and duplicate names are rejected after trimming. Long-press a custom playlist to rename or confirm removal. Removing a playlist leaves the selected track and queue intact.

Empty custom playlists show a centered, bold gray “Empty playlist” message following `016`; the playlist name remains dynamic. Empty Favorites follows `017` with a gray heart and “No Favorites yet”; both recent lists use the same centered bold gray style with “Empty” beneath their own headings. Details show title/artist/album rows and selected playback indicators, with no search or plus controls. Long-press a favorite or custom track for Track options and membership removal; custom tracks also offer Reorder. Drag the handles, then tap Done; accessibility actions also move entries up/down. Editing cancels automatic return. Both Back actions restore the overview and its scroll position; Back during reordering finishes editing first.

Custom playlist changes and built-in metadata are saved locally in a demo SharedPreferences store. New playlists start empty. Favorites and custom memberships are managed through the main-player Manage playlists popup described below. Recently added uses stable demo import order; Recently played records successful **simulated** starts, including transport resume/skip, with at most 50 distinct entries. The real scanner, transactional database, and playback-start events remain pending. Selecting a playlist track captures its displayed order without changing shuffle, and arms the existing 15-second return. Later edits and recent-list changes leave that queue snapshot intact.

Debug-only fixtures provide populated Favorites and Trip 02 for exercising removal/reordering without inventing a track-picker flow. Fixture edits do not overwrite normal saved playlist data.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen playlists-empty --install
python3 scripts/compare-main-player.py --screen playlists
python3 scripts/compare-main-player.py --screen playlist-favorites
python3 scripts/compare-main-player.py --screen playlist-added
python3 scripts/compare-main-player.py --screen playlist-custom
python3 scripts/compare-main-player.py --screen playlist-custom-empty
python3 scripts/compare-main-player.py --screen playlist-favorites-empty
```

Artifacts are written under `build/visual-comparison/<screen>/`. Debug fixtures use `reference_preview=true` and `playlists_preview` set to one of these names; `playlist-played` exercises the shared Recently played layout without a dedicated reference PNG. Regenerate the supplied plus vectors with `python3 scripts/import-player-assets.py --group playlists-screen`.

Validated on `headunit_a6` (API 23, 1280×720): all five reference comparisons; Menu entry; custom creation, rename, restart persistence, canceled/confirmed deletion; Favorites/custom-track removal; drag reorder, Done and Back restoration; and editing surviving the inactivity interval. `assembleDebug`, all 45 unit tests, and `lintDebug` pass. Real audio, real imports, and physical head-unit behavior remain unvalidated.

Debug fixtures `playlist-added-empty` and `playlist-played-empty` expose the empty recent-list states through the `playlists_preview` intent extra.


## Genres

Menu > Genres follows references `018`–`022`: alphabetical genres with Track/Tracks counts, genre options with Play all and three equally spaced Browse buttons, and scoped Tracks, Artists, and Albums lists. Existing SVG-derived normal/pressed icons, Inter, marquees, search, fast scrolling, clock, volume, and transport controls are shared with the other browsers.

Explicit genre values are trimmed and matched case-insensitively; blank/missing values use Unknown genre. Multiple stored values create separate memberships without splitting names such as Pop/Rock. Counts deduplicate repeated membership by track ID while retaining physical copies and unavailable entries. The demo library includes these metadata cases.

Artist options and children keep the genre filter and count distinct matching album titles. Qualifying albums show full-library counts and open the entire album, including nonmatching tracks. The album's Artist shortcut uses unfiltered Album Artist scope. Both Back controls retrace the entry path, restoring query, scroll, and last-opened selection per genre and nested scope. Search filters the current list's names/titles; options search opens genre Tracks search.

Genre/artist Play all snapshots alphabetically ordered matching tracks, disables shuffle, and returns immediately after a successful simulated start. Individual selection snapshots displayed results, preserves shuffle, and uses the existing 15-second return. Empty/unavailable collections never substitute unrelated tracks. Real scanning and audio playback remain pending.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen genres --install
python3 scripts/compare-main-player.py --screen genre-options
python3 scripts/compare-main-player.py --screen genre-tracks
python3 scripts/compare-main-player.py --screen genre-artists
python3 scripts/compare-main-player.py --screen genre-albums
```

Debug-only `genres_preview` fixtures reproduce the reference labels and selection; normal launch uses consistent shared demo-library metadata. Comparisons are saved to `build/visual-comparison/<screen>/`. Scrollbar visibility and position follow actual collection length; font rasterization and supplied vector geometry retain small differences from the PNGs. All 50 unit tests pass, including genre normalization, explicit multi-value membership, unavailable copies, partial-album qualification with full counts, scoped artists, and exhausted queues. `assembleDebug` and `lintDebug` pass.

Validated on `headunit_a6` (API 23, 1280×720): five reference comparisons; Menu entry; genre search; genre-scoped artist Tracks; whole-album entry including nonmatching tracks; unfiltered Album Artist shortcut; nested Android Back route and retained genre query; individual selection remaining in browsing and returning after 15 seconds; immediate Genre Play all return.


## Manage playlists

The main player's top-right Manage playlists control opens the overlay from references `023`–`026`. The panel keeps its size in the empty state, covers the seek bar and metadata, and leaves Menu, volume, clock, and transport usable. All geometry scales uniformly within the existing half-screen grid; supplied SVG-derived button gradients, checkbox colors, and open/closed membership icons retain their proportions.

Favorites, the divider, and alphabetically sorted custom playlists scroll together. Row and checkbox taps toggle membership once and save immediately. Create playlist shares the overview's name validation and Create/Cancel prompt, but adds the selected track automatically; overview creation still starts empty. The demo player's existing Superman display now has an explicit stable selected-track identity so it can be managed while paused or playing.

Close, the toggle, and Back dismiss the overlay; Back in the naming dialog first cancels the draft. Clock/blank touches leave it open. Changing track identity dismisses both popup and naming draft; pause/resume and volume retain the editing target. The popup suspends fading indefinitely, and dismissal starts a fresh fade interval. Membership edits leave the queue untouched. Audio playback and automatic service-driven advancement remain future integration; dismissal is bound to selected-track identity for those future events as well.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen collections-favorites --install
python3 scripts/compare-main-player.py --screen collections-empty
python3 scripts/compare-main-player.py --screen collections-custom
python3 scripts/compare-main-player.py --screen collections-member
```

Debug-only `collections_preview` fixtures reproduce the four reference states without overwriting normal saved playlists. Comparison artifacts are saved under `build/visual-comparison/<screen>/`. Regenerate vectors with `python3 scripts/import-player-assets.py --group track-collections`.

Validated on `headunit_a6` (API 23, 1280×720): four reference comparisons, both pressed backgrounds, single toggles through rows and checkboxes, hidden-control exclusion, clock touches, pause/resume, nested Back, Close, Menu, Next-track dismissal, saved membership restoration after restart, creation with the selected track checked, and popup fade suspension/dismissal reset. Previous demo playlist data was restored after the interaction checks. `assembleDebug`, all 52 unit tests, and `lintDebug` pass (lint reports no errors). Physical hardware and real playback remain unvalidated.


## Folders

Menu > Folders opens a virtual root containing the demo selected sources. Directory panes use the supplied folder, audio-file, and parent-arrow SVGs converted to Android vectors. The fixed halves and all assets scale uniformly. Rows display filenames with extensions, retain overflow marquees, and keep folders before naturally sorted audio filenames. Previous directory scrolls with the list. Both Back controls clear an active search first, then follow the actual directory parent to the source root, virtual root, and Menu. Directory scroll positions and last-opened folder highlights are retained.

Normal file selection snapshots only the current directory’s audio. Recursive search matches folder names and audio filenames, adds relative paths, and snapshots matching audio across descendants. Opening a folder result exits search and follows that folder’s actual parent afterward. Both selection modes preserve shuffle and use the existing 15-second return; typing cancels a pending return. Player labels continue to use metadata. Empty folders remain navigable. Disconnected-source handling and source-boundary behavior are modeled; real storage grants, scanning, connection monitoring, and audio playback remain pending.

The demo includes nested duplicate filenames, numeric filenames, an empty folder, and a long directory label with a scrollable collection under Music oriental.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen folders --install
python3 scripts/compare-main-player.py --screen folder-directory
python3 scripts/import-player-assets.py --group folders-screen
```

Debug-only `folders_preview=folders` and `folders_preview=folder-directory` with `reference_preview=true` provide the two visual fixtures. The root fixture retains the mockup’s drawn source order; normal browsing uses natural sorting. Reference 028 supplies the changed directory contents; its comparison retains the shared header, clock, and transport from 027 as required by the plan. The root scrollbar thumb appears only when contents overflow, so the three-row fixture has a rail without the mockup’s decorative thumb. Artifacts are saved under `build/visual-comparison/folders/` and `build/visual-comparison/folder-directory/`.

Validated on `headunit_a6` (API 23, 1280×720): both visual comparisons; Menu entry; recursive filename/path search and Back clearing; opening a folder search result with actual parent navigation; empty-folder navigation; scroll restoration; Back to Menu; direct-directory queue boundaries; recursive-search queues; metadata on the player; and the 15-second return. `assembleDebug`, all 58 unit tests, and `lintDebug` pass (0 lint errors). Six new unit tests cover source boundaries, natural ordering, duplicate relative paths, supported-file filtering, search scope, direct/search queue snapshots, and disconnected-source behavior. Real storage and physical-device behavior remain unvalidated.

### Splash screen and startup

Fresh process starts show the full-screen branding from `design/029-Splash-screen.png`: the supplied warm-glow background, circular logo, and peach wordmark in bundled Inter Bold. A single 2000×1236 canvas fits uniformly into the display with black letterboxing. The logo stays square, text uses font size and tracking without geometric transforms, and no clock or player controls appear on the splash.

`ClocarmusApplication` owns local startup initialization and a one-use branding gate. The current demo loads its saved playlist state on the IO dispatcher, then renders the player paused and fades the splash away over 300 ms after branding has drawn. There is no minimum branding delay. Resume and same-process Activity recreation cannot reclaim the gate. Initialization failures fall back to defaults with a dismissible destination message. Saved playback sessions, scanning, boot launch, and the playback service remain future integrations; readiness must not wait for discovery or artwork when those are added.

Android 12+ uses a black system launch palette and the supplied circular logo, padded into the system icon's safe area. MainActivity removes the system surface without adding an exit animation or hold, then lets the ready, drawn custom composition perform its exit fade. Window focus also releases this handoff for launches that do not provide a system splash exit callback. API 23 uses a black launch window with a proportionate centered logo before Compose draws. This uses the platform API behind an API-31 check and adds no dependency or minimum-SDK change. The OS may still show its launch surface on warm starts; the fresh-start restriction applies to the app-controlled composition. See the [official Android launch migration guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).

Use the existing capture/compare loop:

```bash
python3 scripts/compare-main-player.py --screen splash --install
```

This debug fixture uses `reference_preview=true` and `splash_preview=true` to keep branding static. Other reference fixtures bypass startup branding. The splash comparison fits the entire reference canvas, and handles Android's physical-display letterboxing when `wm size` overrides are active. Native, small, and wide comparison artifacts are under `build/visual-comparison/splash`, `splash-1024x600`, and `splash-1920x720`. Small rendering differences remain between the supplied background SVG and the reference raster, and between Android and the reference's text rasterization.

Regenerate the checked-in splash PNG resources with `python3 scripts/import-splash-assets.py`. This exports the original SVG gradient transforms faithfully through Inkscape; normal builds need no asset-conversion tools. It does not change the launcher assets or the existing shared brand vector.

Validated on API 23 (`headunit_a6`) and API 31 (`clocarmus_splash_api31`): static reference comparisons, fresh launch to paused player, background return, and same-session Activity recreation. API 23 also verifies simulated playing state survives recreation and a killed/restored process returns paused; the pre-test demo preferences were restored byte for byte. Recordings are in `build/visual-comparison/startup-api23/` and `startup-api31/`; the latter also contains a manual-launcher recording verifying the uncropped system icon. The API 23 fade recording temporarily enabled system animator duration (the existing AVD had animations disabled), then restored its original setting. Native splash mean RGB difference is 2.721/255 on API 23 and 2.831/255 on API 31; this is a comparison aid, not a claim of pixel identity. `assembleDebug`, all 63 unit tests (including five startup lifecycle/readiness tests), and `lintDebug` pass. Physical ignition/boot, actual background audio, onboarding, and durable playback-session restoration remain unvalidated/unimplemented.

To compare another emulator explicitly, add `--serial emulator-5556 --output build/visual-comparison/splash-api31` to the splash comparison command.


### Drag-only seek and volume bars

Both sliders require a horizontal drag starting within the current thumb/filled endpoint's invisible target (48 dp, or the seek thumb's diameter when larger, clipped to the slider bounds). Taps, holds, jitter, vertical gestures, and drags starting elsewhere leave the value unchanged. Relative movement preserves the finger's grab offset; the artwork is unchanged and volume has no visible thumb.

Seeking previews the thumb, fill, and elapsed time, then commits the demo position once on release. Cancellation, loss of the initiating finger while another remains, and track changes discard the preview. Unavailable tracks and tracks without a known positive duration cannot scrub. Volume updates Android's media stream during dragging and retains its last applied level on cancellation, including in browsing screens. Hardware volume changes refresh the endpoint through a settings observer and on resume. Accessibility progress actions and player touch/inactivity observation remain available.

Real audio playback and playback-service seek commands remain pending; seek behavior here updates the existing demo position. Validation: `assembleDebug`, 71 unit tests (including eight slider gesture tests), and `lintDebug` pass. API 23 (`headunit_a6`) checks cover ignored touches, off-center dragging, seek range limits, playing-state preservation, held seek previews and cancellation, immediate/canceled volume changes on the player, volume dragging in the menu, cancellation when the initiating finger lifts with another held over the clock, and cancellation when another finger changes tracks during a drag. Physical-device drag-target tuning and audible playback validation remain pending.


## Track Options from long-press

Long-press a track in All Tracks, artist/album/genre track lists and search results, recent lists, or a Folders audio row to open the shared popup based on references `030`–`031`. Favorites and custom playlists retain their action menu, with Track options above removal. Close and either Back action dismiss one level. The source query, scroll, and selection are retained; its rows and heading are hidden and cannot receive popup touches. Clock, media volume, and transport remain available. Opening this flow cancels automatic return until another ordinary track selection.

Save to playlist reuses the existing collections popup for both adding and removing membership. Its target stays fixed while playback advances, including during playlist creation; creation includes that track. Close/Back returns to Track Options, then to the source or retained playlist action menu. The main-player collections entry still dismisses on a playing-track change.

Play next moves the earliest upcoming occurrence in effective playback order, or inserts one if absent. Scheduling the current track uses a separate occurrence. The latest request goes next; untouched queue occurrences keep their order, shuffle stays enabled, and playing/paused state and position are preserved. Stop after this song schedules the target and attaches the sole stop marker to that occurrence. Other Play next requests retain that marker. Marked completion consumes it and stops despite repeat, preserving the rest of the queue and the user's modes. Success returns to the source list with a toast. Add to queue and Remove from queue remain disabled. Track info opens the read-only tag panel.

The demo queue now has stable occurrence IDs, effective shuffle order, explicit scheduling order, and a stop marker. A local demo-session snapshot persists these with position and modes; process restart restores paused. Known-duration demo tracks advance a simulated position and exercise completion, including the one-shot stop. This is still a visual demo; real audio/service integration remains pending.

Validation: `assembleDebug`, all 85 unit tests, and `lintDebug` pass. Fourteen scheduling tests cover repeated identities, current-track targeting, latest-first requests, untouched order, no-current selection, repeat precedence, stop-marker replacement/consumption, unavailable targets, queue replacement, and occurrence snapshot serialization. Interaction checks and screenshots are recorded under `build/visual-comparison/track-options/`.

Validated on `headunit_a6` (API 23, 1280×720): all eight track-browser entry types, retained Favorites/custom-playlist menu nesting, hidden-row input blocking, disabled actions, blank taps, one-level Back/Close (including Folders), fixed-target collections after transport changes, playlist creation with the target included, paused scheduling and process restoration, stop-marker consumption despite Repeat all with the remaining queue retained, pressed styling, and cancellation of an armed return timer without rearming on close. The emulator’s original preferences were restored byte for byte after testing.


## Track info

Track Options > Track info opens the read-only panel from reference `032`. Its fixed heading and Close button sit above ten scrolling tag fields with wrapped values and subdued Empty placeholders. Search and source rows stay hidden; clock, volume, and transport remain usable. Close, app Back, and system Back return to Track Options; holding Back returns home. The target stays fixed through transport changes, and opening/closing does not re-arm automatic browsing return.

`TrackTags` keeps raw artists, album, release date, comment, description, album total, track number, composer, copyright, and publisher separate from browser fallbacks. Compound `3/12` tags provide both numbers, full dates display their year, and separately stored artists join with commas without splitting `/` or `&`. An unavailable track with cached tags shows the saved-tags notice; without a snapshot it shows an unavailable message. The snapshot type supports serialization; real scanning, persistent library metadata storage, and hardware source-disconnection integration remain pending.

Demo fixtures in All Tracks: **Amber** matches the reference tags, **Anchor** shows Empty fields, and the long-title track exercises wrapped artists, multiline Comment/Description, and the lower fields.


Validation: `assembleDebug`, all 94 unit tests, and `lintDebug` pass (0 lint errors). API 23 (`headunit_a6`) checks cover reference layout, wrapped long values, scrolling to the final fields, fixed target after Next, blank taps, Close/app Back/system Back, long-press Back to home, and cancellation of an armed return timer without rearming on close. Screenshots, comparison, and interaction scripts are under `build/visual-comparison/track-info/`. Cache/unavailable states and raw snapshot serialization are unit-tested; real source detachment and physical-head-unit testing remain pending.

## Long-press Back to main player

Hold any existing top-left in-app Back button to return directly to the main player, closing nested Track Options and collections popups. The shortcut uses the platform long-press threshold and exposes a “Return to main player” accessibility action. Release does not perform an additional Back tap. Ordinary taps and Android system Back retain their existing hierarchy.

This only changes navigation: current track, position, queue, shuffle/repeat, and playing/paused state remain intact. Unfinished playlist actions, text drafts, and prompts are canceled before browser state is saved; immediately applied changes remain. Browsers retain their existing query, selection, and scroll restoration rules. The browsing-return timer is canceled, and player controls return fully visible, waiting for any held fingers to lift before restarting the existing fade interval. No Back buttons are added to screens or dialogs.

Implementation uses the existing Compose version and its standard `combinedClickable` gesture handling; see [Android’s tap and press guidance](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press).

Validation: `assembleDebug`, all 88 unit tests, and `lintDebug` pass (0 lint errors). Three new unit tests cover cleanup before navigation, disposal of cancellation handlers, and returning home while a finger remains held. On API 23 (`headunit_a6`), checked Menu, All Tracks, artist tracks, album tracks, genre albums, folder directories, custom playlists, nested Track Options/collections, ordinary tap and system Back hierarchy, navigation before release, no extra release tap, and paused-state preservation. Captures and the local interaction-check script are under `build/visual-comparison/back-shortcut/`. Physical-head-unit validation remains pending.


## Main player redesign

The player adds Queues beneath −10 seconds and Track info beneath +10 seconds, using the supplied queue and shared info SVGs. Queues opens playback Queue management, including when the queue is empty. Track info is disabled without a selected track; otherwise it opens the existing read-only tag panel while playing or paused. Its saved request captures the track ID and an explicit return destination, retaining that target through transport changes. Close and either Back action return directly home from player entry; Track Options entry retains its existing parent. Holding Back still returns home.

The new controls share the existing inactivity opacity and touch restoration. Disabled opacity multiplies that fade without enabling the buttons. Overlays hide and block the underlying controls; Track info suspends fading, and closing it starts a fresh interval. Previous/Next now sit at the seek bar's ends across the shared player/browser/popup transport. Elapsed/total labels sit above the existing seek bar; drag-only slider behavior is retained.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen player --install --output build/visual-comparison/player-redesign
python3 scripts/import-player-assets.py --group main-player --asset queue-button
python3 scripts/import-player-assets.py --group shared --asset info-icon
```

The player comparison now uses reference `033` for the redesign. Existing demo metadata, artwork geometry, top controls, and clock remain as implemented, so the complete image difference includes those reference differences. Real playback and physical-head-unit validation remain pending.

Validation: `assembleDebug`, all 94 unit tests, and `lintDebug` pass (0 lint errors, 39 warnings). API 23 (`headunit_a6`, 1280×720) checks cover disabled Queues, disabled Track info without a selection, playing/paused entry, fixed nonempty tags after Next, scrolling through Publisher, Close/app Back/system Back/held Back, retained Track Options parent, hidden-control exclusion, collections coverage, both new buttons fading/restoring together, clock-touch restoration, and panel fade suspension/close reset. Original emulator preferences were restored after interaction checks. Captures and local interaction scripts are under `build/visual-comparison/player-redesign/`. Physical-head-unit and real-playback validation remain pending.


## Playback Queue

Queues on the player opens reference `034` using the shared Back, volume, transport, clock, heading, and scrollbar components. The list shows every occurrence in effective playback order, including earlier entries, shuffled order, and duplicates. It opens at the current occurrence, with the existing white highlight and playback indicator. Empty queues show “Queue is empty”.

Tap a row to play that exact occurrence without rebuilding the queue or changing shuffle/repeat. Drag from the supplied handle to preview a move, including moving the current occurrence. Only a completed drop commits; it preserves the current occurrence, playback position, and playing/paused state. Edge scrolling reaches offscreen destinations. A changed/replaced queue cancels the preview; a playback advance retains its current occurrence. Accessibility actions also move entries up or down.

Long-press a row for shared Track Options. Queue entry disables Add to queue and enables exact-occurrence removal; Play next is disabled on the current occurrence, and Stop after this song marks it without duplication. Other browsers enable Add to queue, appending a distinct occurrence without starting playback, and disable Remove. Adding to an empty queue selects the new entry paused. Removing the current entry selects the next playable occurrence without wrapping, preserving paused/playing state or stopping if none remains. Successful actions close the popup stack with a toast.

Manual ordering clears old scheduling priorities while retaining the stop marker on its occurrence. Marked completion consumes the marker and stops despite repeat; explicit Play continues at the next playable entry, if present. The snapshot persists occurrence order, current selection, modes, marker, and pending continuation; process restarts restore paused.

Queue arms the shared 15-second return timer on entry. Touch, scrolling, and handle dragging reset/suspend it. Track Options and nested popups suspend return; returning to Queue starts a fresh interval. Popup scroll position is retained. Ordinary Back returns home, and the existing held-Back shortcut applies throughout.

Use the established build → capture/compare → adjust loop, retaining shared controls:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 scripts/compare-main-player.py --screen queue --install
python3 scripts/import-player-assets.py --group queue-screen --asset handle
```

Comparison artifacts and local interaction checks are under `build/visual-comparison/queue/`. This remains simulated demo playback; real audio/service and physical-head-unit validation are pending.

Validation: `assembleDebug`, all 104 unit tests, and `lintDebug` pass (0 errors, 39 warnings). API 23 (`headunit_a6`, 1280×720) checks cover exact duplicate selection, current-entry scheduling restrictions, handle reordering with preserved modes/position/marker, paused restart, exact removal while playing/paused, unavailable-entry skipping, end removal without wrapping, empty-queue addition and contextual disabled actions, entry timeout, popup suspension/rearming, marked completion with Repeat all and explicit Play continuation, playback advancement during a drag, and navigation cancellation without committing. The build → install → capture/compare → adjust loop corrected immersive-layout entry scrolling; final comparison artifacts and interaction scripts are under `build/visual-comparison/queue/`. The scrollbar reflects the full demo queue rather than the shorter reference fixture; shared chrome remains unchanged. Original emulator preferences were restored after tests. Real audio/service and physical-head-unit validation remain pending.


### Settings

Menu > Settings implements references 035–039 using the supplied settings SVGs. The existing Back button (including hold to return home), media volume, transport controls, and persistent clock remain shared with other screens. The list scrolls through Library, Playback, Display, and Appearance; returning from a detail preserves its scroll position. Entering Settings cancels the pending browsing return.

Preferences save immediately across launches: autoplay (off by default), status bar visibility, disabled main-player fading, Default/Indigo/Coffee/Grey solid backgrounds or Default/Modern/Potion/Lava gradients, Default/Vertical/LCD/Dot matrix clock, 24-hour time, and Default/Hidden/Dotted/Squiggly/Beam divider styles. Clock references 043–045 define the stacked Vertical face and the supplied alarm clock (LCD) and Register (Dot matrix) fonts for all clock text. Autoplay currently advances demo transport state; real audio playback remains pending.

Manage folders shares real selected sources and subfolder exclusions with onboarding. Add folder opens Android’s system picker; removal/reset requires confirmation. Reset also clears the demo queue and music memberships while retaining empty custom playlists and appearance settings. Manual rescan explains that scanning is not connected yet. Real music discovery remains part of the library stage.

Debug visual fixtures are available through `--es settings_preview settings`, `settings-scrolled`, `settings-folders`, `settings-background`, or `settings-clock`, alongside `--ez reference_preview true`. They use isolated default preferences and never save settings. Capture comparisons with `python3 scripts/compare-main-player.py --screen settings --install` (substitute any of those fixture names). Regenerate settings vectors with `python3 scripts/import-player-assets.py --group settings-screen`.

Validation: `assembleDebug`, all 107 unit tests, and `lintDebug` pass (0 errors, 40 warnings). API 23 interaction checks cover menu entry, nested Back, held Back, preserved transport controls, folder removal cancellation/reset/re-add, exclusions, all clock faces, 24-hour time, backgrounds/divider, persisted toggles, and opt-in demo autoplay after a process restart. Original emulator preferences were restored. This emulator intermittently clears unchanged content during redraws; the same behavior was reproduced with an independently built, unchanged HEAD APK, so visual comparisons for affected captures are incomplete.

Divider references 040–042 add Dotted, Squiggly, and Beam using the supplied SVGs. The saved style applies to the shared player/browser/settings split, with the original Default and Hidden behavior retained. Divider strokes are outlined during vector import so the dotted dash pattern survives Android conversion. Debug comparison fixtures: `settings-divider-dotted`, `settings-divider-squiggly`, and `settings-divider-beam`.

Divider validation: `assembleDebug`, all 107 unit tests, and `lintDebug` pass (0 errors, 42 warnings). API 23 captures verify the supplied dotted, squiggly, and beam divider shapes. Interaction checks verify saving each of the five choices and a checked Beam radio after process restart. Original emulator preferences were restored byte for byte.

Clock settings now follow references 043–045: the 24-hour switch appears above four larger style choices, and Segmented is labeled LCD. The existing `SEGMENTED` preference key remains compatible. LCD uses the bundled `alarm clock.ttf` and an abbreviated uppercase weekday; Dot matrix uses `REGISTER.TTF` with the full weekday. Both typefaces apply to the time, AM/PM, weekday, and date. Vertical aligns stacked hours/minutes and moves the weekday/date around them. Shared transport, Back, volume, and divider controls remain unchanged. New debug fixtures: `settings-clock-vertical`, `settings-clock-lcd`, and `settings-clock-dotmatrix`.

Clock redesign validation: `assembleDebug`, all 107 unit tests, and `lintDebug` pass (0 errors, 42 warnings). API 23 checks cover selection/saving and 12/24-hour mode for every face, restoration of the existing `SEGMENTED` key as LCD after process restart, retained settings scroll on Back, and held Back to return home. Updated comparison captures are under `build/visual-comparison/settings-clock-{vertical,lcd,dotmatrix}/`; the previously documented emulator redraw limitation still affects some shared content. Original emulator preferences were restored byte for byte.

### Welcome / onboarding (046–050)

Fresh installs enter Welcome → Drive safely → Storage permission → Select folders → Loading after the splash. Completion is persisted; later launches enter the player. Back navigates earlier steps; Back on Welcome offers skipping setup. Continuing with no folders requires confirmation.

Buttons use the supplied `design/assets/welcome-onboarding` SVGs, including pressed states. The flat-color onboarding logo is derived from the shared brand SVG by `scripts/import-welcome-logo.py`. Layouts fit the 2000 × 1236 reference canvas with black letterboxing. Loading shows only the supplied `loading-icon.json` Lottie animation centered on black, looping during the three-second interval. The bundled raw resource is an unchanged copy of the design asset. Debug screenshots can select any step with `--ez reference_preview true --es welcome_preview welcome|warning|permission|folders|loading`; only the folders fixture inserts a sample row. The loading preview also proceeds to the player after three seconds without changing saved onboarding completion.

Storage permission requests use READ_EXTERNAL_STORAGE through API 32 and READ_MEDIA_AUDIO on API 33+. Denial offers Android settings or individual folder selection. Add folder opens Android's directory picker and retains read access. Selected tree URIs, display labels, and per-subfolder exclusions persist separately from the existing demo player's source IDs. Exclusions browses real subfolders off the UI thread; removing a source clears its exclusions. The loading screen runs the Lottie animation for three seconds, then saves setup and opens the player even when no folders were selected. Write errors remain retryable. **This build does not scan selected folders or import audio into the demo player.** Settings’ Manage folders now uses the same saved real sources, system picker, and exclusions as onboarding. Music discovery and playback remain demo data until the library and playback stages.

Validated with `assembleDebug`, all 109 unit tests, and `lintDebug`. API 23 screenshots of all five steps are in `build/visual-comparison/welcome/`; lifecycle captures verify fresh entry, warning navigation, permission denial and recovery, system folder picker cancellation, empty-folder confirmation, completion persistence, and process relaunch. Emulator animation scales were temporarily enabled to capture animation, then restored. API 33+ permission behavior, successful grants on removable storage, and real library imports remain unvalidated on hardware.

The empty-folder loading regression was verified on API 23: the loading indicator remains visible at one second, the player appears after the three-second interval, and completion is saved with an empty folder list. The loading preview also exits without changing onboarding preferences. Captures are in `build/visual-comparison/welcome/three-second-loading/`.

The Lottie replacement was verified on API 23 using two distinct frames: only the centered animation appears against black, and the player opens after the agreed three-second interval. Captures are in `build/visual-comparison/welcome/lottie-loading/`.


The updated Background screen (`design/038-settings-screen-background.png`) separates Normal and Gradients tabs. Gradients are offered in Default, Modern, Potion, Lava order, using the supplied SVGs for row previews and the full shared player background. Changing tabs does not alter the applied background; choosing a row saves immediately. The active background determines the initial tab after a fresh launch. Existing solid-color preference keys remain compatible. Import these vectors with `python3 scripts/import-player-assets.py --group settings-screen --asset <name>-gradient`.

Background tab validation: `assembleDebug`, all 109 unit tests, and `lintDebug` pass (0 errors, 32 warnings). API 23 checks verify all eight choices, gradient ordering/previews, saved selections and initial tab after process restart, unchanged applied background while browsing tabs, and retained Settings position on Back. Captures are under `build/visual-comparison/settings-background/` and `build/visual-comparison/settings-background-gradients/`. Original emulator preferences were restored byte for byte.


The RHD toggle sits directly below “Disable buttons fade out in main player” and saves immediately (off by default). Pane order changes by placement, preserving the active screen, scroll position, playback state, and control orientation. Backgrounds and all five divider choices remain independent of this setting. Existing preferences without the new key retain the original player-left layout.

RHD validation: `assembleDebug`, all 109 unit tests, and `lintDebug` pass (0 errors, 32 warnings). API 23 checks verify the toggle's position and retained Settings scroll/paused transport, clock/player bounds and unchanged control orientation, all six browser roots, Background/Clock/Divider details, queue, Track info, collections popup, all five divider styles, and persistence after enabling/disabling across process restarts. Captures are under `build/visual-comparison/rhd/`. Original emulator preferences were restored byte for byte.


### Optional Launcher mode and app drawer

Settings > Advanced > Launcher mode enables an optional Home entry and asks Android to choose the default Home app. The mode starts off. Cancelling selection keeps the drawer available and displays the actual default status; Choose Home app reopens selection. Disabling a selected Home entry first asks for another launcher and verifies the switch. Normal app launching remains available. Open app after boot is a separate, saved best-effort request; Android's selected Home startup disables that control while preserving its value. Neither Home navigation nor boot opening requests demo autoplay. The full previously-playing autoplay/service policy remains pending.

The supplied drawer, Back, and Android Settings assets accompany real installed app icons and alphabetically sorted labels in nine-entry vertical pages. The drawer replaces the clock pane, follows it in RHD, and preserves the other pane's screen and controls. Successful external launches close it. Back or 15 seconds of inactivity restores only the clock; touches in either pane reset the independent timer, holds defer it, and backgrounding suspends it. Home returns to the player and clock through the existing cancellation path. Page selection lasts for the process session, and package changes/foreground returns refresh discovery off the UI thread.

The optional Home component is a nonvisual routing Activity. An alias can duplicate a singleTask player under a different component, and forwarding CATEGORY_HOME can create a separate Home-type player task. Forwarding an explicit Home navigation extra to the ordinary player task avoids both cases. Android 10+ selection uses the [Home role request](https://developer.android.com/reference/android/app/role/RoleManager) through the Activity Result API; earlier versions use Home settings. Manifest [package visibility queries](https://developer.android.com/training/package-visibility) cover launchable apps and Home candidates without broad package access.

Validation: `assembleDebug`, all 112 unit tests, and `lintDebug` pass. API 23 checks cover cancellation, paging/page retention, external Settings round trips, drawer timeout/background retention, Back priority, RHD, fresh Home staying paused with autoplay enabled, and Home retaining live playing state while closing Settings/drawer. API 31 checks cover the role chooser, cancelled disable, verified switch-away/disable on return, and Home routing to the existing playing player. Local captures and interaction scripts are in `build/visual-comparison/launcher/`. Actual audio focus, device boot/wake behavior, and physical head-unit validation remain pending. Regenerate drawer vectors with `python3 scripts/import-player-assets.py --group app-drawer`. Debug Settings fixtures isolate player appearance preferences; the Launcher controls still change Android Home capability and selection.


### Shell and onboarding integration (2026-11-12)

Onboarding durably records the first unfinished step separately from final completion. Back does not undo a completed driving acknowledgement. Process restart resumes setup; successfully saved sources and exclusions remain available. Completing setup opens the player without triggering demo autoplay. Splash and onboarding stay full-screen even with the normal status-bar preference enabled.

Manage folders in Settings shares onboarding's actual Android tree grants, labels, picker, and exclusions. Reference previews retain their deterministic demo fixtures. The Folders music browser still uses demo tracks; selecting a real source does not scan or import it yet.

Settings changes apply immediately, commit off the UI thread, and restore the previous value with an error on write failure. Writes are serialized while pending. Open app after boot remains independent of autoplay and defaults off.

Confirmed Remove all libraries clears real source configuration and the existing demo queue, Favorites, recent lists, and custom-playlist memberships, retaining empty custom playlists, appearance, and completed onboarding. A persisted pending-reset record lets startup replay interrupted cleanup before restoring state. Automatic demo recent-list seeding stays disabled after reset. Music files are never modified. No scanner or queue-preparation worker exists yet; those future integrations must honor the reset barrier.

Storage and persistence implementation follows the [Android document-access guide](https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions) and [SharedPreferences commit contract](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit()). No dependency or SDK changes were required.

Validation: `:app:assembleDebug`, all 117 unit tests, and `:app:lintDebug` pass (0 lint errors, 38 warnings). API 23 (`headunit_a6`, 1280×720) checks cover unfinished-step routing, acknowledgement retention after Back and process restart, empty setup completion, full-screen setup with status bar enabled, paused setup completion with autoplay enabled, shared real-source display, reset confirmation/cancellation, retained appearance and empty custom playlists, restart after reset, and pending-reset recovery. Emulator preference files were restored byte for byte. Local check scripts and UI dumps: `build/visual-comparison/shell-integration/`. Real scanning/audio, modern-Android runtime checks for these changes, and physical-head-unit validation remain pending.
