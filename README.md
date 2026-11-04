# Clocarmus

Clocarmus is a planned offline music player for aftermarket Android car infotainment systems, built with **Kotlin and Jetpack Compose**. It combines local music playback with a large digital clock in a landscape interface designed for car screens.

The app is intended to run on **Android 6.0 (API 23) and above**, install through an APK, and operate without accounts, cloud services, an internet connection, or Google Play Services.

## Current status

**Main player, menu, All Tracks, Artists, Albums, Playlists, and Genres demo flows implemented.** The app uses bundled Inter fonts, supplied vectors, a persistent live clock, full-screen landscape layout, media-volume controls, and local transport/shuffle/repeat state. Browsers support selection and retained navigation state; the track, artist, and album browsers also support search. Playlists includes built-in lists, custom playlist management, track removal, and drag reordering. Normal launch starts paused. Real library discovery and audio playback, the main-player Add to playlist popup, Folders, and Settings remain pending.

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

The menu shares media volume, transport state, and the persistent clock with the player. Player fading resets when the menu opens and restarts on returning. All Tracks opens the demo track menu, Artists opens the artist browser, Albums opens the album browser, Playlists opens the playlist overview, and Genres opens its five-screen browsing flow; Folders and Settings remain placeholders.

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

Empty custom playlists show a centered, bold gray “Empty playlist” message following `016`; the playlist name remains dynamic. Empty Favorites follows `017` with a gray heart and “No Favorites yet”; both recent lists use the same centered bold gray style with “Empty” beneath their own headings. Details show title/artist/album rows and selected playback indicators, with no search or plus controls. Long-press a favorite to remove its membership; long-press a custom track to remove it or enter Reorder. Drag the handles, then tap Done; accessibility actions also move entries up/down. Editing cancels automatic return. Both Back actions restore the overview and its scroll position; Back during reordering finishes editing first.

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

`ClocarmusApplication` owns local startup initialization and a one-use branding gate. The current demo loads its saved playlist state on the IO dispatcher, then renders the player paused and fades the splash away over 300 ms after branding has drawn. There is no minimum branding delay. Resume and same-process Activity recreation cannot reclaim the gate. Initialization failures fall back to defaults with a dismissible destination message. Onboarding, saved playback sessions, scanning, boot launch, and the playback service remain future integrations; readiness must not wait for discovery or artwork when those are added.

Android 12+ uses a black system launch palette and the supplied circular logo, padded into the system icon's safe area. MainActivity removes the system surface without adding an exit animation or hold, then lets the ready, drawn custom composition perform its exit fade. Window focus also releases this handoff for launches that do not provide a system splash exit callback. API 23 uses a black launch window with a proportionate centered logo before Compose draws. This uses the platform API behind an API-31 check and adds no dependency or minimum-SDK change. The OS may still show its launch surface on warm starts; the fresh-start restriction applies to the app-controlled composition. See the [official Android launch migration guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).

Use the existing capture/compare loop:

```bash
python3 scripts/compare-main-player.py --screen splash --install
```

This debug fixture uses `reference_preview=true` and `splash_preview=true` to keep branding static. Other reference fixtures bypass startup branding. The splash comparison fits the entire reference canvas, and handles Android's physical-display letterboxing when `wm size` overrides are active. Native, small, and wide comparison artifacts are under `build/visual-comparison/splash`, `splash-1024x600`, and `splash-1920x720`. Small rendering differences remain between the supplied background SVG and the reference raster, and between Android and the reference's text rasterization.

Regenerate the checked-in splash PNG resources with `python3 scripts/import-splash-assets.py`. This exports the original SVG gradient transforms faithfully through Inkscape; normal builds need no asset-conversion tools. It does not change the launcher assets or the existing shared brand vector.

Validated on API 23 (`headunit_a6`) and API 31 (`clocarmus_splash_api31`): static reference comparisons, fresh launch to paused player, background return, and same-session Activity recreation. API 23 also verifies simulated playing state survives recreation and a killed/restored process returns paused; the pre-test demo preferences were restored byte for byte. Recordings are in `build/visual-comparison/startup-api23/` and `startup-api31/`; the latter also contains a manual-launcher recording verifying the uncropped system icon. The API 23 fade recording temporarily enabled system animator duration (the existing AVD had animations disabled), then restored its original setting. Native splash mean RGB difference is 2.721/255 on API 23 and 2.831/255 on API 31; this is a comparison aid, not a claim of pixel identity. `assembleDebug`, all 63 unit tests (including five startup lifecycle/readiness tests), and `lintDebug` pass. Physical ignition/boot, actual background audio, onboarding, and durable playback-session restoration remain unvalidated/unimplemented.

To compare another emulator explicitly, add `--serial emulator-5556 --output build/visual-comparison/splash-api31` to the splash comparison command.
