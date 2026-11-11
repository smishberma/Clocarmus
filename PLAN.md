# Clocarmus — v1 plan

Status: main player, menu, All Tracks, Artists, the three-screen Albums demo flow, Playlists browsing/management, the five-screen Genres flow, the main-player Manage playlists popup, the two-screen Folders demo flow, the fresh-start Splash screen, shared Track Options with next/stop scheduling and Track info, the main-player Queues/Track info button redesign, playback Queue management, and the Settings demo flow (035–045) are implemented on `headunit_a6`. Browsers use demo metadata with search, retained list state, selection, interactive fast scrolling, and the 15-second inactivity return after track selection. Artist Play all and Play album update the demo queue, disable shuffle, and return immediately. Shared album options support entry from Artists and an explicitly scoped Album Artist shortcut. Automatic marquees for overflowing music titles and metadata are implemented across the player and all eight track, artist, and album browsing panes. Welcome onboarding (046–050) is implemented with Android permission requests, persisted tree grants and exclusions, completion routing, and the centered Lottie loading animation (three seconds on black). Optional Launcher mode and the app drawer (051–052) are implemented with real Android app discovery and Home selection; the existing clock and player UI are reused. Real library data, audio playback, and remaining platform/library integrations remain planned.

## 1. Product and constraints

Build a personal-use, entirely offline music player for aftermarket Android car head units using Kotlin and Jetpack Compose. Deliver an installable APK, support Android 6.0 (API 23) and above, and require neither accounts, internet, cloud services, nor Google Play Services.

The first release is validated on an Android Virtual Device (AVD) with 100 tracks. Physical head-unit validation follows. Do not impose an artificial library import limit or claim large-library performance based on this initial test set. Prioritize reliable background playback, responsive discovery, and low/medium-powered devices. No deadline has been specified.

### Existing project baseline

The current project is a minimal Compose shell using package/application ID `com.example.clocarmus`. Preserve this identity. Repository configuration currently declares minimum SDK 23, compile/target SDK 35, Java compatibility 17, AGP 9.2.1, Kotlin Compose plugin 2.2.10, Compose BOM 2024.09.03, and Activity Compose 1.8.2. These are recorded configuration values, not a verified compatibility endorsement. Verify exact dependency versions and current official documentation before implementation; do not silently replace the user's stack or raise the minimum SDK.

## 2. Agreed interface and journeys

### Design

Use `design/01-main-player.png` as the primary reference, the remaining screen mockups for flows, and `design/design-system-ref.png` for styling. Final name: **Clocarmus**. Use the supplied logo assets and Inter typography; confirm font asset availability and licensing before bundling.

- Landscape-only, fixed 50/50 left/right division across supported landscape sizes. Full-screen by default; the opt-in status-bar setting applies during normal use, while splash/onboarding remain full-screen.
- Left side: album artwork, title, artist, album, year directly below album, playback controls, progress, volume, shuffle, repeat, and +/-10-second controls. Browsing/settings replace left-side content.
- Right side by default: persistent day, digital clock, and date during normal app use, including browsing/settings. Settings > Display > Change to right-hand drive (RHD) swaps the clock to the left and the player/browsers/settings/panels to the right, preserving the split and centered divider styles. Splash and onboarding follow their separate full-screen mockups.
- Retain previous, play/pause, and next controls across browsing/settings, as shown.
- Keep the screen awake while the app UI is visible, even when paused; do not hold the display awake from the background.
- Preset backgrounds only: Normal colors Default (black), Indigo, Coffee, Grey, and Gradients Default, Modern, Potion, Lava (updated reference 038). Preserve readable white controls/text. No image backgrounds.
- Configurable 12/24-hour time, default 12-hour. Date remains fixed at DD-MM-YYYY; additional date formats are deferred.
- English-only interface; preserve music metadata in its original language.

### Long-press Back to main player — implemented in the demo

- Long-pressing the top-left in-app Back button returns directly to the main player from every screen or popup where that button is displayed, dismissing the open popup stack.
- Ordinary taps retain each screen's existing Back hierarchy. Android system Back remains unchanged. The shortcut does not add Back buttons where none currently exist.
- Use the platform's normal long-press threshold. Navigate once when that threshold is reached; releasing the finger must not also trigger the ordinary Back action.
- Preserve the current track, playback position, queue, shuffle/repeat settings, and playing/paused state. This is navigation only and must not restart or pause playback.
- Cancel unsaved text input and dismiss unfinished prompts. Retain changes already applied immediately, including playlist membership; do not implicitly submit an unfinished edit.
- Preserve browsing location, selection, query, and scroll state according to each section's existing restoration rules. Do not introduce a separate restoration policy for this shortcut.
- Cancel any pending browsing-return timer when returning home. Restore the main-player controls to full opacity and follow the existing fading/reset rules.
- Implemented through the shared Back asset button using Compose `combinedClickable`, with a “Return to main player” accessibility action. Unfinished playlist state is cleared before the existing browser state holder saves it. Held fingers defer the player fade countdown until release.
- Validation: `assembleDebug`, all 88 unit tests, and `lintDebug` pass (0 lint errors). API 23 (`headunit_a6`) checks cover all seven browsing sections, nested Track Options/collections, ordinary tap/system Back hierarchy, navigation before release with no extra release tap, and preserved paused state. Artifacts: `build/visual-comparison/back-shortcut/`. Physical-head-unit validation remains pending.

### Long music titles — implemented

- Apply automatic marquee scrolling only when text exceeds its available label width. Text that fits retains its existing alignment and stays still; overflowing text remains on one line at its original font size.
- Cover all nine reference screens: main player (`01`), All Tracks (`03`), Artists list/options/tracks/albums (`04`–`07`), and Albums list/options/tracks (`08`–`010`). Scroll the player's track, artist, and album labels; browser row titles and metadata subtitles; and artist/album context headings, including combined `Album by Artist` headings.
- Pause for 1.5 seconds before the first scroll and between subsequent loops; repeat continuously without requiring focus, selection, or playback. Changing the label text restarts from its beginning with a fresh pause.
- Clip scrolling text to its label viewport so it stays clear of row playback indicators and the clock. Preserve existing selection colors, font sizes, navigation, and touch actions.
- Reuse the opt-in marquee in `PlayerLabel`, backed by Compose `basicMarquee`. Speed is `size × 0.8` dp/second and the gap between copies is `size × 1.5` dp, where `size` is the label's scaled font-size value.
- Include this selectable demo-library entry in All Tracks, Artists, and Albums to exercise the flows and all three player labels:
  - Artist: Bartholomew Quillfeather-Ashworth & The Interdimensional Orchestra
  - Album: The Unabridged and Occasionally Contradictory Chronicles of Seventeen Thousand Midnight Trains
  - Track: Departing From a Station That Was Demolished Before It Was Ever Built
- Validated on `headunit_a6` (API 23): long player labels, browser rows, and context headings animate while short rows remain stationary. `assembleDebug`, all 38 unit tests, and `lintDebug` passed; animation was checked on the emulator separately from the unit suite.

### Main player redesign — Queues and Track info buttons — implemented in the demo

Reference: `design/033-main-player-redesign.png`. This reference supersedes older mockups for the new main-player buttons and shared transport/time-label positions.

- Add Queues below the left −10-second button and Track info below the right +10-second button, flanking the album artwork. Use `design/assets/main-player/queue-button.svg` and reuse `design/assets/shared/info-icon.svg`.
- Keep Queues visible but disabled until the planned Queue screen below is implemented; then enable it even for an empty queue.
- Main-player Track info opens the existing read-only panel for the currently selected track, whether playing or paused. Disable the entry when no track is selected.
- Capture the selected track ID when opening; keep that target fixed if playback advances or transport controls change the playing track. Reuse all existing tag formatting, cached/unavailable handling, scrolling, and read-only behavior.
- For entry from the main player, Close and Back return directly to the main player. Entry from Track Options continues returning to Track Options. Carry the entry destination explicitly; do not introduce a synthetic Track Options level for main-player entry.
- Keep the new buttons covered/noninteractive while the info panel or other player overlays occupy their area. Suspend fading while the panel is open and restore full visibility with the existing timer reset on close.
- Align Previous and Next below the seek bar with its left and right ends respectively; retain centered Play/Pause. Apply these shared transport positions everywhere transport controls already appear, including browsers and popup flows.
- Move elapsed time and total duration above the seek bar, aligned to its respective ends, on every screen that already displays these labels and the seek bar. Do not add seek bars to screens that do not have them.
- Preserve top Menu, volume, and Manage playlists controls, the fixed 50/50 split, and the clock pane. Existing transport actions and drag-only slider behavior remain unchanged.
- Add both new buttons to the existing inactivity fading group, using the same timing, opacity, animation, and touch restoration. Preserve disabled styling and semantics when fading Queues or a Track info button without a target.

Validation: `assembleDebug`, all 94 unit tests, and `lintDebug` pass (0 lint errors, 39 warnings). API 23 (`headunit_a6`, 1280×720) checks cover disabled Queues, disabled Track info without a selection, playing/paused entry, fixed nonempty tags after Next, scrolling through Publisher, Close/app Back/system Back/held Back, retained Track Options parent, hidden-control exclusion, collections coverage, both new buttons fading/restoring together, clock-touch restoration, and panel fade suspension/close reset. Original emulator preferences were restored after interaction checks. Captures and local interaction scripts are under `build/visual-comparison/player-redesign/`. Physical-head-unit and real-playback validation remain pending.

### Main-player inactivity fading

- Menu, Manage playlists, Shuffle, Repeat, Skip forward 10 seconds, Skip backward 10 seconds, Queues, and Track info fade. The optional Launcher-mode drawer button joins this group when visible (nine controls with Launcher mode enabled, otherwise eight). All other elements retain their normal appearance.
- While the main player is active, 10 seconds without touch starts a 500 ms animation from 100% to 20% opacity, regardless of whether music is playing or paused.
- Apply opacity to each control's existing appearance, including inactive Shuffle/Repeat styling; preserve active/inactive distinctions, hit areas, and accessibility semantics.
- Touch anywhere on the player, including the clock pane, immediately restores all visible fading-group controls to full opacity. The same touch still performs its normal button or slider action; do not consume it merely to restore visibility.
- Keep controls fully visible while any finger remains down. Start a fresh 10-second countdown when the last finger lifts, including after holds, drags, or multi-touch gestures.
- Hardware/media buttons, physical volume keys, clock updates, playback progress, and track changes do not reset the countdown.
- Suspend and reset fading while another screen or dialog is active or the app is backgrounded. Returning to the active player, including dismissing a dialog, restores full opacity and starts a fresh interval.
- Keep this timer independent of the browsing-to-player return timer below. Its state is transient and is not persisted across screen recreation.
- Keep static reference-preview mode fully opaque for deterministic mockup comparisons.

### Drag-only seek and volume sliders — implemented in the demo

Implemented in the shared slider component, replacing tap-to-coordinate interaction on the player and browsing screens. Seek previews are separate from the committed demo position; Android media volume changes continuously. Real playback-service seeks remain pending with audio playback. Tracks without a known positive duration, or unavailable tracks, cannot scrub; the main reference track now declares its existing 225-second demo duration.

- Neither slider changes value on pointer down, a tap, a stationary hold, or movement below the horizontal drag threshold. Use the platform touch-slop threshold to distinguish an intentional horizontal drag from finger jitter; vertical-only gestures do not adjust values.
- A drag must begin within the slider's current thumb/endpoint hit area. Starting elsewhere on the bar and later crossing that area must not activate adjustment.
- Use a generous invisible hit target without expanding the visible artwork. Implementation default: at least 48 dp wide and high, or the visible seek thumb size if larger, centered on the current thumb/fill endpoint and constrained to the slider's bounds. Keep it reachable at minimum and maximum values.
- Capture the value and pointer location at gesture start. Once horizontal drag is recognized, apply horizontal displacement relative to that starting value; never snap to the initial touch coordinate. Preserve the finger's grab offset and clamp values to the slider range.

#### Track seek bar

- Require dragging from the visible thumb's hit area. During a valid drag, show the preview position through the thumb, filled progress, and elapsed-time label, without issuing playback seeks.
- On normal release, commit the preview position with one seek. Keep the current playing/paused state; dragging does not implicitly pause or resume playback.
- On gesture cancellation, discard the preview and display the actual playback position without seeking. If the selected track changes during the gesture, cancel it rather than applying an old preview to a new track.
- Do not allow scrubbing without a valid seekable track/duration. Preserve the existing +/-10-second controls and intentional accessibility progress adjustments; the drag-only requirement applies to pointer interaction.

#### Volume slider

- Keep its existing visual design with no visible thumb. The filled bar's endpoint is the invisible drag target, using the generous hit area above.
- Update Android media volume continuously after a valid horizontal drag begins, giving audible feedback. Do not defer volume changes until release, and do not adjust volume merely because the pointer touches the bar.
- On cancellation, retain the last applied volume; do not unexpectedly revert an audible adjustment. Preserve hardware volume keys and intentional accessibility progress adjustments.

#### Shared interaction rules

- Maintain existing inactivity observation: touching/holding a slider still restores player-control visibility and prevents fading while contact remains, even when the touch does not qualify as an adjustment.
- Track only the initiating pointer for the gesture; additional fingers must not retarget the slider or cause coordinate jumps. Losing the initiating pointer cancels that gesture rather than transferring it to another finger.
- The 48 dp target and platform drag threshold are implementation defaults; no visual thumb is added to volume and no new user setting is introduced.
- Validation: `assembleDebug`, all 71 unit tests (including eight gesture tests), and `lintDebug` pass. On API 23 (`headunit_a6`), verified ignored taps/holds/jitter/vertical and outside-start gestures, relative off-center seeking, range limits, preserved playing state, held previews and cancellation, continuous volume with cancellation retention on the player and dragging in the menu, initiating-pointer loss with another finger held elsewhere, and cancellation on a mid-drag track change. Real playback-service seeks, audible playback feedback, and physical-head-unit tuning remain pending.

### Splash screen and startup routing

The splash composition and process-scoped startup gate are implemented. Follow `design/029-Splash-screen.png`: a full-screen landscape composition with the supplied warm glow on black, centered logo, and peach Clocarmus wordmark underneath in the bundled Inter Bold font. Use `design/assets/splash-screen/splash-screen-background.svg` for the background and the matching supplied brand logo. Preserve proportions across landscape sizes; there is no player/clock split on this screen.

- Show the app-controlled splash only on a fresh app start, including a fresh boot-triggered launch. Do not replay it on a return to an already-running app, wake of an existing session, ordinary recomposition, or activity recreation within that session.
- Display static branding with no sound or logo animation. Once essential startup state is ready, begin a 300 ms fade into the destination. There is no forced minimum display duration or artificial branding delay.
- Essential readiness means local settings/onboarding status and sufficient saved session state to select and render the destination. Do not wait for a full library scan, removable storage to mount, artwork decoding, or metadata/content fingerprint processing. Continue discovery after showing the player.
- First-time users go to onboarding. Users who completed or explicitly skipped onboarding go to the main player with playback restored paused by default; subsequent qualifying startups may use the explicit opt-in autoplay policy below. The welcome flow (046–050) now owns first-time routing; completed or skipped setup routes to the player.
- Splash completion itself never requests playback; qualifying startup autoplay is owned by the separate service policy. Returning to an existing background playback session skips the custom splash and leaves that session's playback unchanged.
- Keep startup initialization separate from screen composition and gate navigation once per fresh startup. Recoverable storage/scan failures should appear in the destination UI rather than holding branding indefinitely.

#### Android launch integration

- Retain the single MainActivity architecture; coordinate launch-window appearance with the in-app branded composition instead of introducing a dedicated splash Activity or a second timed startup sequence.
- Android 12+ can show its own system splash on cold and warm starts. The fresh-start-only requirement governs the app-controlled branding screen; do not claim suppression of every platform launch surface. Match the system launch palette/logo as closely as supported, with no extra hold before the custom composition.
- The full mockup background/wordmark may require an in-app composition beyond the platform splash's styling. Validate the handoff on API 23 and Android 12+, avoiding blank flashes, unnecessary duplicate branding delays, and a cropped/masked custom logo.
- Verify the exact compatible splash API/library version before adding dependencies; preserve minimum SDK 23. Reference: [Android splash-screen migration guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate), reviewed 2026-11-04.
- Debug fixtures: existing debug screen-reference intents bypass startup branding so prior screenshot comparisons remain deterministic; `reference_preview=true` with `splash_preview=true` provides a dedicated static splash reference. Production startup remains readiness-driven.
- Validated with the capture/compare loop on API 23 at native, small, and wide landscape viewport sizes, and on API 31 at native size. Fresh launch, resume, recreation, and paused fresh-session behavior are exercised; 63 unit tests, `assembleDebug`, and `lintDebug` pass. See README for artifacts and the remaining real-playback/onboarding/boot integration limits.

### First use and navigation

1. Show branding and welcome flow, explain storage access, and let users select music folders.
2. Allow setup to be skipped into an empty player/clock with an Add music folder action.
3. Remember selected folders and expose source management and manual rescan in Settings.
4. Browse All Tracks, Artists, Albums, Genres, Folders, and Playlists. Search songs, albums, and artists.
5. Selecting a song starts its collection in displayed order; search queues all matching songs. Recursive folder playback remains the general plan except in the Folders browser: normal file taps queue only direct-directory audio, while recursive search queues all matching audio results.
6. Remain in browsing after selection, then return to the player after 15 seconds of inactivity. Taps, scrolling, and typing reset inactivity; entering settings, playlist editing, or typing in search cancels the automatic return until another song is selected. Cancellation takes precedence over resetting.

### Artists browsing flow

The visual/demo flow is implemented using the shared All Tracks list, search, selection, fast-scroll, and inactivity-return components. Repository and playback-service integration remain planned. Keep the fixed 50/50 layout, persistent clock, volume slider, and transport controls across all four screens.

#### Artists list — `design/04-artists-screen.png`

- Open from Menu > Artists. List artists alphabetically by name, grouping on the track's Artist tag rather than Album Artist. Retain the existing Unknown artist fallback for missing tags.
- Show artist name with album count underneath, using singular/plural labels such as `1 Album` and `2 Albums`. Do not show a year on artist rows.
- Count distinct album titles for each artist across selected sources; duplicate copies do not inflate the count. Retain counts for disconnected sources, and group missing album tags into one Unknown album entry per artist.
- Search filters artist names only, using the existing case-insensitive search interaction.
- Tapping an artist opens its detail screen and retains that artist as the white highlighted, last-opened selection when returning to the list. Browsing an artist does not itself start playback or arm the browsing-return timer.

#### Artist detail — `design/05-artists-screen-artist-options.png`

- Display the selected artist name beneath the Artists heading, with Play all and a Browse section containing Tracks and Albums.
- Play all replaces the current queue with all tracks belonging to this artist in alphabetical song-title order, disables shuffle, begins playback from the first playable entry, and immediately returns to the main player. This is an explicit exception to the normal 15-second browsing return.
- Preserve the established unavailable/unplayable-track handling; do not start unrelated tracks if no artist tracks can play.
- Tracks opens the selected artist's track browser; Albums opens the selected artist's album browser. These navigation actions do not initiate playback.

#### Artist tracks — `design/06-artists-screen-artist-all-tracks.png`

- Show ALL TRACKS with the selected artist name underneath. List only that artist's tracks alphabetically by song title, with `Artist - Album` below each title, matching the reference and existing track browser interaction.
- Search filters song titles within this artist only. Selecting a song replaces the queue with the displayed artist tracks (the matching subset while filtered), starts the selected track, and retains the existing selected-track/playback indicator behavior.
- After individual track selection, remain in browsing until 15 seconds of inactivity, using the existing interaction resets and cancellation on search typing/settings/playlist editing. Unlike Play all, individual selection does not introduce a new shuffle-off rule.

#### Artist albums — `design/07-artists-screen-artist-all-albums.png`

- Show ALL ALBUMS with the selected artist name underneath. List only albums associated with that artist, alphabetically by album title; show year below each title, or Unknown year when unavailable. Year subtitles belong to album rows, not artist or track rows in this flow.
- List album identities (album title + effective Album Artist) containing tracks by this track Artist. Keep the previously agreed distinct-title count on the Artists list; two same-titled albums with different Album Artists can therefore produce two rows but one counted title. Search filters album titles within this artist only.
- Tapping an album retains its highlight and opens the shared album-options screen below, replacing the earlier highlight-only behavior. Open the whole album, including other performers on compilations; navigation itself does not start playback or arm an inactivity return.

#### Back navigation and retained state

- On-screen Back and Android Back use the same hierarchy: artist Tracks/Albums > artist detail > Artists > Menu.
- Restore each list's search query and scroll position when navigating back. Keep track and album list state scoped to the selected artist so filters do not leak between artists.
- Preserve the last-opened artist highlight independently of playback state. Reuse existing scrolling and search patterns while following the supplied screen-specific headings and rows.

### Albums browsing flow

Agreed scope: the Menu Albums list, shared album options, Album Tracks, and an Artist shortcut to the existing artist-options layout. Keep the fixed split, persistent clock, volume slider, transport controls, and established browser interaction patterns.

#### Album identity and metadata

- Group albums by album title + effective Album Artist across selected sources. Effective Album Artist is Album Artist when present, otherwise track Artist, otherwise Unknown artist. Missing album title uses Unknown album.
- The main Artists list continues grouping by track Artist. Do not globally switch it to Album Artist grouping.
- Duplicate physical copies remain separate tracks inside the grouped album and count separately: two 13-track copies produce 26 tracks. Include disconnected entries in counts and retain normal unavailable-track handling during playback.
- Year is descriptive metadata, not an additional album grouping key. Display Unknown year when unavailable.

#### Albums list — `design/08-albums-screen.png`

- Open from Menu > Albums. Sort alphabetically by album title and show `Artist - Year - N songs` beneath each title (singular `1 song`), with effective Album Artist as Artist.
- Tapping an album opens album options and retains the last-opened album highlight. Preserve query and scroll position when returning.
- Provisional search filters album titles only, using the existing case-insensitive search interaction.

#### Album options — `design/09-albums-screen-album-options.png`

- Show ALBUMS and `Album by Artist`, using the same effective Album Artist as the Albums list and Album Tracks subtitle, followed by Play album and Browse actions Album Tracks and Artist.
- Play album replaces the queue with the entire album, disables shuffle, starts the first playable track in disc/track order, and immediately returns to the main player. Use existing unplayable-track handling; do not play unrelated tracks if none can play.
- Album Tracks opens the album's track browser without starting playback.
- Use `design/assets/albums-screen/album-tracks.svg` for the Album Tracks button at rest and `album-tracks-pressed.svg` while pressed. Android vector resources are `album_tracks.xml` and `album_tracks_pressed.xml`; preserve their supplied geometry and white/magenta colors without substituting the existing generic Tracks or Albums icons.
- Artist opens the existing artist-options layout scoped to effective Album Artist, including Various Artists for compilations. Its Play all, Tracks, and Albums operate over that Album Artist's collection. Carry an explicit artist scope (track Artist versus Album Artist) through navigation and child browsers so equal display names do not conflate these collections.
- Provisional options-screen search opens Album Tracks search within the selected album.

#### Album Tracks — `design/010-albums-screen-album-tracks.png`

- Show ALBUM TRACKS and `Album by Artist`, with effective Album Artist in the subtitle. Rows show zero-padded track number and title, with duration beneath, matching the mockup.
- Sort by disc number, then numeric track number. Within each disc, tracks missing a valid track number follow numbered tracks and sort alphabetically by title. Do not sort track numbers lexically.
- Selecting a track replaces the queue with the displayed album tracks, starts that track, and uses the existing 15-second inactivity return rather than immediate navigation. It does not add a new shuffle-disable rule; that rule belongs to Play album.
- Provisional search filters song titles within this album, retaining disc/track order. Selecting a result queues matching tracks only.

#### Navigation and search scope

- Both on-screen Back and Android Back return along the actual entry path: Album Tracks > album options > originating album list. When Artist is opened from album options, Back from that artist-options screen returns to the album options rather than the main Artists list.
- Albums reached through Artists now open the same album options and full album, including compilation tracks by other performers. Preserve the originating artist context for Back.
- Preserve each browser's query, scroll position, and last-opened album highlight, keyed by album/artist identity and scope. Navigation alone does not arm the browsing-return timer.
- Search rules in this section are interim decisions. A dedicated app-wide search interview will revisit them; do not treat this as the final search specification.

### Playlists browsing and management

The visual/demo flow is implemented against the updated playlist mockups, retaining the fixed split, persistent clock, volume, and transport controls. Custom edits and built-in demo metadata persist locally in a temporary SharedPreferences store. Real library imports, transactional repository persistence, and playback-start integration remain planned. The main-player Manage playlists popup below is implemented against this shared demo store.

#### Overview and creation

- Menu > Playlists opens the overview. Show Favorites, Recently added, and Recently played in that order above a horizontal divider, with custom playlists alphabetically below it. All entries and the divider form one scrolling list, not pinned sections.
- Use `design/011-playlists-screen-no-custom-playlists.png` for the empty overview and `design/012-playlists-screen-populated-custom-playlists.png` for the populated overview. With no custom playlists, show “Tap + to create a playlist” below the divider; built-in lists remain available.
- The plus button appears only on the overview, including its empty state. Use `design/assets/playlists-screen/add-playlist.svg` normally and `add-playlist-pressed.svg` while pressed, preserving the supplied white/magenta geometry. Android drawable resources: `add_playlist.xml` and `add_playlist_pressed.xml`.
- Plus opens a name dialog with Create and Cancel. Trim surrounding whitespace; reject blank names and case-insensitive duplicates, including the reserved names Favorites, Recently added, and Recently played. Apply the same rules to rename, excluding the playlist being renamed from duplicate checking.
- Create an empty playlist and remain on the overview. Do not navigate to a track picker or automatically add the current track.
- Long-press a custom playlist to offer Rename and Remove. Rename edits its name and repositions it alphabetically while preserving its identity and tracks. Remove opens “Remove ‘playlist name’?” with Remove and Cancel. Confirmation deletes only the playlist and membership records, never audio files or the current playback queue.
- Built-in playlists cannot be renamed or removed.

#### Details and built-in lists

- Use `design/013-playlists-favorites-list.png` for Favorites, `design/014-playlists-recently-added-list.png` for Recently added, and `design/015-playlists-user-custom-playlist.png` for custom playlists. Recently played reuses the Favorites/Recently added layout with its own icon and heading; `015` is not its reference.
- Follow the updated detail mockups: no plus button in any detail view. Show track title and Artist - Album subtitle with the existing selected/playing indicator.
- Favorites is populated through the main-player Manage playlists popup specified below and sorts newest favorited first.
- Recently added contains the 50 most recently imported tracks, newest first, whether imported individually or as part of an album. Unchanged rescans/reconnections and identity-preserving moves do not reset import time.
- Recently played contains the 50 most recently played distinct track identities, newest first. Record actual new playback starts, including explicit replays and each Repeat One iteration; move that track to the top without duplication. Ordinary pause/resume, seeking within a track, failed/unavailable playback, and paused restoration do not count. This replaces both the previous 100-track requirement and the interim 20-track proposal.
- Long-press a Favorites track to offer Track options above Remove from Favorites. Long-press a custom-playlist track to offer Track options above Remove from playlist, retaining Reorder. Track options opens the shared nested popup specified below. Do not offer clearing, manual removal, or manual reordering for either recent list. Favorites retains its timestamp ordering rather than manual ordering.
- New custom-playlist tracks append at the bottom; duplicate membership remains disallowed. Reorder enters an editing mode with drag handles and a Done button. Creating, renaming, removing, and reordering cancel any pending automatic return so editing cannot be interrupted.
- Empty detail messages: “No Favorites yet” for Favorites, “Empty” for Recently added and Recently played, and “Empty playlist” for custom playlists. All use centered bold gray text, with the actual playlist name above. Favorites follows `design/017-playlists-user-favorites-empty.png`, including the gray heart above its message (the mockup’s placeholder name is replaced by Favorites). For empty custom playlists, follow `design/016-playlists-user-custom-playlist-empty.png`: center the bold gray message in the left pane beneath the playlist heading, with no search, plus button, or scrollbar. Keep clock and transport controls available.

#### Playback, navigation, and scope

- Selecting a song in any of the four list types replaces the queue with a snapshot of displayed tracks in their current order, starts that song, preserves shuffle, and uses the existing 15-second inactivity return. Keep the queue stable as Recently played updates; playlist removal and subsequent membership/reordering edits do not rewrite the active snapshot.
- Normal unavailable-track rules continue to apply; keep disconnected references instead of silently deleting them from playlists.
- Both on-screen Back and Android Back follow details > Playlists overview > Menu, restoring each list's scroll position. Merely opening a playlist does not start playback or arm a return timer.
- No search controls are added in this feature. App-wide search remains a separate interview topic. The main-player Manage playlists popup is specified below.

### Genres browsing flow

The visual/demo flow is implemented using the five genre mockups below. Real library and playback-service integration remain planned. Retain the fixed split, persistent clock, volume slider, transport controls, existing fast scrolling, and long-text behavior.

#### Genre membership and counts

- Trim surrounding whitespace and group genre values case-insensitively: Rock, rock, and spaced variants belong together. Missing/blank genre tags appear under Unknown genre.
- A track with multiple explicitly stored genres belongs to each genre. Count each track identity once within a genre, even if its tag repeats the same normalized value. Do not infer multiple genres by arbitrarily splitting names such as Pop/Rock.
- Track counts include disconnected entries and distinct physical copies, consistent with the library. Playback retains normal unavailable/unplayable-track handling.
- Derive album membership from tracks: an album qualifies when at least one track has the selected genre. Do not propagate that genre to the album's other tracks. Preserve album identity as title + effective Album Artist.

#### Genres list — `design/018-genres-screen.png`

- Menu > Genres shows genre names alphabetically with matching track counts beneath, using singular/plural Track/Tracks labels.
- Tapping a genre opens its options and retains the last-opened selection. Navigation alone does not start playback or arm an inactivity return.
- Provisional search filters genre names case-insensitively.

#### Genre options — `design/019-genre-options.png`

- Show GENRES with the selected genre beneath, Play all, and three equally spaced Browse buttons: Tracks, Artists, Albums.
- Reuse existing normal/pressed assets for Tracks (`menu_all_tracks`), Artists (`menu_artists`), Albums (`player_albums`), and Play all (`player_play_all`), including their corresponding `_pressed` drawables. Reuse the existing Genres icon for headings. No replacement SVGs or new icon artwork are required.
- Play all replaces the queue with the genre's matching tracks alphabetically by title, disables shuffle, starts the first playable track, and immediately returns to the player. Do not substitute unrelated music if none can play.
- Browse buttons open the respective genre-scoped lists without initiating playback. Provisional options-screen search opens the selected genre's Tracks search.

#### Genre tracks — `design/020-genre-browse-tracks.png`

- Show the selected genre heading and TRACKS. List matching tracks alphabetically by title with Artist - Album subtitles and the existing selected/playing indicator.
- Selecting a track snapshots the displayed matching tracks into the queue, starts that track, preserves shuffle, and uses the existing 15-second inactivity return.
- Provisional search filters song titles within this genre; selected search results queue the matching subset in displayed order.

#### Genre artists — `design/021-genre-browse-artists.png`

- Show the selected genre heading and ARTISTS. Group matching tracks by track Artist, not Album Artist, and sort artist names alphabetically.
- Show below each name the number of associated albums containing that artist's matching tracks. Retain the main Artists list's distinct-title count convention, including Unknown album; duplicate physical copies do not inflate album counts.
- Opening an artist reuses artist options with the genre filter retained. Its Play all and Tracks use only that artist's genre-matching tracks; its Albums lists only associated albums containing those matching tracks.
- Artist Play all retains its established queue replacement, alphabetical ordering, shuffle-off, and immediate-return behavior, applied to this filtered collection.
- Provisional search filters artist names within the selected genre.

#### Genre albums — `design/022-genre-browse-albums.png`

- Show the selected genre heading and ALBUMS. List qualifying albums alphabetically by title with effective Album Artist, year (or Unknown year), and the full album track count underneath.
- An album with 12 tracks and only 3 genre matches displays 12 tracks. Count physical copies and disconnected entries as in the normal Albums browser.
- Opening an album deliberately leaves the genre filter and opens the shared options for the whole album. This also applies to albums reached through a genre-scoped artist. Album Tracks and Play album include all album tracks in normal disc/track order.
- The whole album's Artist shortcut opens normal, unfiltered Album Artist options. Do not silently reapply the prior genre filter there.
- Provisional search filters album titles within the genre-qualified album list.

#### Back navigation and retained scope

- Both Back controls retrace the actual route: genre Tracks/Artists/Albums > genre options > Genres > Menu, with nested artist/album screens returning to their actual originating screen.
- Preserve query, scroll position, and last-opened selection per genre and nested artist/album scope. Returning from a whole album restores the prior genre-filtered browser rather than losing the genre context.
- Carry genre context explicitly while browsing artist options and children; drop the filter for whole-album entry but retain its originating navigation entry for Back.
- Search choices here are provisional pending the dedicated app-wide search interview. Typing in search continues to cancel pending automatic return under the established rules.

### Main-player Manage playlists popup

This replaces the previously named Add to playlist UI. Implemented using the shared playlist state and a reusable creation/name-validation prompt also used by the Playlists overview. Membership changes persist in the temporary local demo store; real playback/service integration remains planned.

#### Appearance and assets

- Follow `design/023-track-collections-popup-add-to-favorites.png`, `024-track-collections-popup-no-custom-playlists.png`, and `025-track-collections-popup-add-to-custom-playlists.png`. Use `026-track-collections-track-present-in-playlists.png` for the closed-player membership indicator.
- Overlay the left player area, covering artwork, metadata, and the seek bar. Retain the same panel bounds with or without custom playlists; never allow touches through the panel to hidden controls. Keep clock, Menu, volume, and bottom transport controls visible.
- Keep Previous, Play/Pause, and Next at their main-player sizes while the popup is open.
- Place Create playlist and Close above the panel. Inside, show the ADD/REMOVE TO heading, Favorites, a divider, and custom playlists alphabetically. Favorites, divider, and custom entries scroll together; the heading and top buttons stay outside that scrolling region.
- With no custom playlists, retain the full panel and divider and show “No custom playlists”.
- Use assets from `design/assets/track-collections/`: `playlist-button-toggle-on.svg` while open, `playlist-button-untoggled.svg` when closed without membership, and `track-present-in-playlists.svg` when closed and the selected track belongs to Favorites or any custom playlist. Open/toggled state takes precedence over membership; Recently added/played do not count as memberships.
- Use `checkbox-favorites-active.svg` for checked Favorites, `checkbox-active.svg` for checked custom playlists, and `checkbox-inactive.svg` for unchecked entries. Preserve supplied colors and geometry at consistent visual sizes.
- Create playlist and Close share the supplied `button-unpressed.svg` / `button-pressed.svg` backgrounds with their respective text labels. Import this asset group with the `collections_` resource prefix, preserving gradients and transparency. Use Manage playlists as the top-right control's accessibility label and expose its expanded state.

#### Track identity and membership

- Enable Manage playlists when a track is selected, whether playing or paused; disable it when there is no selected track.
- On opening, show the selected track's existing memberships. Tapping either a checkbox or its row toggles membership exactly once, persists immediately, and leaves the popup open. No Save or removal-confirmation step.
- Adding appends to a custom playlist or records a new Favorites membership timestamp. Removing affects only that membership, never music files or the active playback queue. Existing memberships remain unique.
- Close the popup whenever selected track identity changes, including automatic advancement or Previous/Next. Do not silently retarget the visible checkboxes to another track. Playback state/position changes alone do not dismiss it.

#### Create playlist from the popup

- Reuse the overview's Create/Cancel name prompt and validation: trim surrounding whitespace; reject blank, case-insensitive duplicate, and reserved built-in names.
- Unlike overview creation, successful creation here automatically includes the selected track. Return to the same popup with the new alphabetically positioned playlist checked; keep the top-right control toggled on.
- Cancel returns to the popup without creating anything. If the selected track changes while the name dialog is open, close both dialog and popup and discard the draft without creating a playlist. Validate track identity again before committing creation/membership.
- A completed membership edit or creation is not undone when the popup subsequently closes.

#### Dismissal, controls, and inactivity

- Close, tapping Manage playlists again, and Android Back dismiss the popup and reveal the main player. Back while the nested naming prompt is open dismisses that prompt first, returning to the popup.
- Tapping the clock or blank space outside the popup does not dismiss it. Volume and play/pause remain usable without closing. Menu closes the popup and opens Menu; Previous/Next closes it when track identity changes.
- Suspend player inactivity fading while the popup or its naming prompt is open; retain full opacity for the popup and toggled button indefinitely. Closing restores the player fully visible and starts a fresh normal fading interval.
- Keep popup membership edits independent of playback queue contents and the browsing-return timer; no automatic popup dismissal based on inactivity.

Validation: compared all four references on `headunit_a6` (API 23, 1280×720) and checked both pressed backgrounds. Emulator interaction checks cover single row/checkbox toggles, hidden controls, pause/resume, clock touches, nested Back, Close/Menu/Next, persistence after restart, creation with selected-track membership, and fade suspension/reset. `assembleDebug`, all 52 unit tests, and `lintDebug` pass. Automatic advancement and hardware/media-driven changes still require the real playback service; target-identity invalidation is wired in the demo shell.

### Folders browser

The visual/demo flow is implemented against `design/027-folders-screen-root-directory.png` and `design/028-folders-screen-subdirectory-playing-file.png`, retaining the fixed split, persistent clock, volume, transport controls, scrolling, and long-label behavior.

This uses an explicit in-memory selected-source tree, shared demo playback, and SVG-derived Android vectors. Real setup-selected grants, provider traversal, source monitoring, and audio playback remain pending. Normal browsing sorts naturally; the debug root fixture preserves the reference drawing’s three-row order.

#### Root and directory contents

- Menu > Folders opens a virtual root containing only the music folders selected during setup/source management. It is not the device filesystem root or a list of all storage devices. Do not browse above granted source boundaries.
- At virtual root, show FOLDERS without a directory subtitle or Previous directory row. Within a selected folder or its descendants, show the open directory's name beneath FOLDERS.
- Display only directories and supported audio files; hide other files. Keep accessible empty folders visible. If an opened directory has no eligible contents, show “No folders or audio files”, retaining the parent navigation row.
- Show filenames with extensions rather than metadata titles. Prefix folders with `design/assets/folders-screen/folder-icon.svg` and audio files with `audio-file-icon.svg` from the same directory. Preserve the selected/playing file highlight and indicator from the reference.
- Below virtual root, Previous directory is the first list row, using `design/assets/folders-screen/previous-folder.svg` and the text “Previous directory”. Its icon and label scroll with the other rows; it is not pinned.
- Order Previous directory first, then folders, then audio files. Sort each group by name using natural numeric order so 2-song.mp3 precedes 10-song.mp3.

#### Navigation and unavailable sources

- Tapping a folder opens it without changing playback or arming the automatic return timer.
- On-screen Back and Android Back go to the parent directory, returning to Menu from virtual root. Previous directory performs the same parent navigation. From a selected source root, parent navigation returns to virtual root rather than accessing its physical parent outside the grant.
- Restore directory scroll positions when returning. After opening a folder from recursive search, parent navigation follows its actual directory parent within the selected source.
- Keep selected sources on disconnected USB/SD storage visible at virtual root as unavailable. Tapping one shows a reconnect message. If the browsed source is removed, return to virtual root; preserve the existing playback source-loss rules (pause if it held the active track).

#### Playback queues

- Tapping an audio file during normal directory browsing replaces the queue with audio files directly in that directory, in displayed natural filename order, starting at the selected file. Exclude audio in descendant directories from this queue.
- This is an explicit Folders-screen exception to the earlier recursive folder-playback rule. It does not change other screens' queue behavior or recursive library discovery.
- Preserve shuffle and stay in the browser until the existing 15-second inactivity return. Main-player labels use track metadata; the browser continues to use filenames. Preserve existing unavailable/unplayable-track handling and snapshot queue behavior.

#### Recursive search — provisional

- Search folder names and audio filenames recursively beneath the current directory. At virtual root, search across selected music folders; stay within granted sources. Do not search unrelated metadata or non-audio files.
- Show relative paths beneath search results to distinguish duplicate names. Retain folders-before-audio grouping and natural name ordering; ties use relative path, then stable identity for deterministic ordering.
- Opening a folder result navigates to that folder and exits search. Back first clears search and returns to normal contents before performing parent navigation on a subsequent press.
- Tapping an audio search result queues all matching audio results in displayed order, including matches from multiple descendant directories. Folder results never become queue entries. This search-only exception is distinct from direct-directory playback.
- Search typing retains the existing cancellation of a pending inactivity return. These rules remain provisional until the dedicated app-wide search interview.

### Track Options from long-press — implemented in the demo

The shared popup, fixed-target collections nesting, and occurrence-aware scheduling are implemented against the demo library/queue. Real audio and playback-service integration remain pending. Use `design/030-long-press-main.png` for the normal popup and `design/031-long-press-main-button-pressed.png` for pressed styling. Hide the underlying track list and its heading while the popup is foregrounded; prevent interaction with hidden rows. Retain the clock, volume, and bottom transport controls. Show the target track title and Artist - Album subtitle in the popup, independent of the currently playing track.

#### Entry points and popup navigation

- Support long-press on track rows in All Tracks, artist/album/genre track browsers, search results, Favorites, recent lists, custom playlists, and audio-file rows in Folders. Do not add this action to artist, album, genre, directory, or playlist-name rows. Existing playlist-name Rename/Remove behavior remains unchanged.
- In Favorites and custom playlists, retain the existing track action popup and all previous entries. Add Track options above Remove from Favorites or Remove from playlist; retain Reorder for custom playlists. Selecting Track options opens the new popup as a nested level.
- Other eligible track lists open Track Options directly. Recognizing a long-press must not also fire the row's normal play action or replace the active queue.
- Capture a stable target track ID. Playback advancement does not retarget or dismiss this track-specific flow; the header continues to identify the long-pressed track.
- Close, on-screen Back, and Android Back dismiss one popup level. Blank-space taps leave the popup open. Restore the originating list's query, scroll, and selection when leaving the popup flow.
- Cancel pending browsing-return timing on entry, including nested dialogs; no popup can time out. Merely closing a popup does not re-arm the timer; a later normal track selection follows existing behavior.
- A successful scheduling action shows its confirmation toast and closes the popup stack back to the originating list. Scheduling changes do not navigate to the main player or disturb the current track's playback position.

#### Add/remove playlist membership

- The mockup's Save to playlist action opens the existing track-collections UI for the long-pressed track, supporting immediate add/remove to Favorites and custom playlists. Preserve the mockup label while documenting its bidirectional membership behavior.
- Opening this child popup must not start the target track or change the currently selected/playing track. Close/Back returns to Track Options rather than the main player.
- Reuse existing checkboxes, colors, sorting, scrolling, creation/name validation, and immediate persistence. Creating a playlist here automatically includes the fixed target track and returns with that playlist checked.
- This is an explicit exception to the main-player collections popup's close-on-playing-track-change rule: the long-press flow and its creation prompt retain their captured target when playback advances. The main-player entry retains its existing behavior.
- Playlist edits never rewrite the active queue. If the captured library track no longer exists, cancel actions requiring it and show an unavailable-target message rather than editing a different track.

#### Play next

- Schedule the target directly after the current queue occurrence, overriding the next choice under shuffle without turning shuffle off. After the requested occurrence, resume the remaining queue's established order.
- If that track already has an upcoming occurrence, move its earliest occurrence in effective playback order into the next slot. Otherwise insert a new occurrence. Do not move the currently playing occurrence or restart it when the target is the current track; use an upcoming occurrence or insert one.
- Latest request is next: choosing A and then B before advancement yields current > B > A > the remaining queue. Preserve relative ordering of untouched occurrences.
- While paused, schedule without starting playback. With no current track, make the requested song first in the queue and remain paused.
- Show a confirmation toast only after successful scheduling, such as “Will play next”.

#### Stop after this song

- Apply the same next-track scheduling operation, then mark that specific scheduled occurrence to stop playback after it completes. The target is the long-pressed song, not implicitly the currently playing song.
- Maintain one stop marker at a time; a new Stop after this song request replaces the previous marker. Ordinary Play next requests leave the marker attached to its existing occurrence, even if that occurrence moves later.
- At marked completion, stop automatic playback without clearing the remaining queue. The stop overrides repeat at that completion; retain the user's shuffle/repeat settings for later use. Treat the stop marker as one-shot and consume it after it takes effect.
- Show a confirmation toast only after scheduling and marking succeeds, such as “Will stop after this song”.

#### Queue actions

- Add to queue and Remove from queue are specified in the planned Queue screen section below. Keep them disabled until implemented, then apply its entry-context rules and confirmation toasts. Track info opens its read-only child panel. Never simulate success for disabled actions.

Demo scheduling uses stable occurrence IDs in effective playback order, a latest-first explicit scheduling list, and one optional stop marker. The local demo-session snapshot restores the queue, position, shuffle/repeat, scheduling, and marker paused on process restart. Known-duration demo tracks use a simulated position/completion clock; no audio playback is claimed. Validation: `assembleDebug`, all 85 unit tests (including 14 scheduling tests), and `lintDebug` pass. Device interaction artifacts are in `build/visual-comparison/track-options/`.

Validated on `headunit_a6` (API 23, 1280×720): all eight track-browser entry types, retained Favorites/custom-playlist menu nesting, hidden-row input blocking, disabled actions, blank taps, one-level Back/Close (including Folders), fixed-target collections after transport changes, playlist creation with the target included, paused scheduling and process restoration, stop-marker consumption despite Repeat all with the remaining queue retained, pressed styling, and cancellation of an armed return timer without rearming on close. The emulator’s original preferences were restored byte for byte after testing.

### Queue screen — implemented in the demo

Reference: `design/034-queues-screen.png`. Accessible from the main-player Queue button using `design/assets/main-player/queue-button.svg`; enable this entry when the screen is implemented, including for an empty queue.

#### Layout and navigation

- Reuse existing Back, volume, transport, and clock components and their shared positions. Implement only the queue-specific list, QUEUE title/icon, and drag handles; do not duplicate shared controls or create unique variants. Preserve the fixed 50/50 split.
- Show the entire active queue in actual playback order: earlier, current, and upcoming occurrences, including shuffle and explicit Play next scheduling. Queue entries are tracks only; duplicates have distinct stable occurrence IDs.
- On entry, scroll to the current occurrence. Show title and Artist - Album subtitle, the mockup's white current-entry highlight, existing playback indicators, and existing drag-handle asset. Use “Queue is empty” when there are no entries, retaining shared controls.
- Back returns to the main player under the existing shared navigation rules. Returning from Track Options preserves queue scroll where possible.
- Unlike other browsers, start the 15-second inactivity return on opening Queue, without requiring song selection. Reset on interaction; suspend throughout dragging and Track Options or nested popup flows. Start a fresh interval after the drag ends or the popup flow returns to Queue. Do not let a stale timer interrupt interaction. This explicitly overrides the normal popup rule that closing does not re-arm a browser timer, for Queue only.

#### Selection and reordering

- Tapping a different entry starts that exact occurrence from its beginning without rebuilding/reordering the queue; preserve shuffle/repeat. Tapping the current entry resumes if paused and otherwise leaves playback uninterrupted.
- Drag only from the handle; long-pressing the track row opens the existing Track Options pane. A drag must not also play the track or open options.
- Allow moving any occurrence, including the current one. Preview its position during dragging and apply the order immediately on drop without Save. Preserve the currently playing occurrence, position, and playing/paused state.
- Reorder the actual playback sequence without disabling shuffle. The resulting visible order governs subsequent playback; old shuffle or Play next scheduling must not silently undo a manual reorder.
- If playback advances during dragging, keep the dragged occurrence identified by its stable ID. If the queue is replaced or the dragged entry disappears, cancel safely. A cancelled drag commits no ordering changes and must not roll back unrelated playback/queue changes.
- Persist the resulting occurrence order and stop marker through the playback snapshot; restore paused by default, subject only to the opt-in autoplay policy.

#### Add and remove queue entries

- Enable Add to queue from other eligible track-list Track Options entries. Append a new occurrence to the end of the actual playback order, allowing duplicates, without starting playback or changing shuffle. If the queue was empty, select the added entry but remain paused.
- Keep Add to queue greyed out/disabled when Track Options was opened from Queue. Enable Remove from queue only for Queue entry; disable it in other browsers to avoid ambiguous duplicate removal.
- Remove the exact long-pressed occurrence immediately, without confirmation. Never modify library entries, playlists, or music files.
- Removing the current occurrence advances to the next playable entry, continuing if playing and remaining paused if paused. If no next playable entry remains, stop and clear the current selection without wrapping automatically. Retain other queue entries.
- Successful addition shows “Added to queue” and closes the popup stack to the originating list. Successful removal shows “Removed from queue” and returns to Queue, preserving scroll where possible and restarting its inactivity interval. Disabled/failed actions never show false success.

#### Exact-occurrence scheduling and stop marker

- From Queue, Play next and Stop after this song target the exact long-pressed occurrence, not another copy. Move that occurrence next when appropriate; preserve the existing latest-request-first scheduling semantics.
- For the current occurrence, disable Play next. Stop after this song marks its completion without moving or duplicating it.
- Retain the existing track-based scheduling behavior when entered from other browsers (move earliest upcoming matching occurrence, otherwise insert).
- Keep one stop marker attached to its occurrence through reordering. Removing the marked occurrence clears it. Marked completion stops automatic playback despite repeat and consumes the marker while preserving the remaining queue.
- After that stop, explicit Play continues with the next playable entry. The 2026-11-09 lifecycle interview extends boundary behavior: wrap only with Repeat All, generating a fresh session when Shuffle is enabled; otherwise remain stopped. Do not invent or duplicate an entry. This boundary extension is not yet implemented/validated in the demo.

Implementation: `QueuePane` uses the shared chrome and supplied `queue-screen/handle.svg`. Stable occurrence IDs own selection, drag previews, exact scheduling, and removal. Preview drops validate against the live occurrence sequence, retain playback advances, and cancel after queue replacement/membership changes. The entry scroll recalculates when immersive layout changes the actual row size. The existing snapshot now also retains continuation after a consumed stop; no dependency or minimum-SDK changes were introduced.

Validation: `assembleDebug`, all 104 unit tests, and `lintDebug` pass (0 errors, 39 warnings). API 23 (`headunit_a6`, 1280×720) checks cover exact duplicate selection, current-entry scheduling restrictions, handle reordering with preserved modes/position/marker, paused restart, exact removal while playing/paused, unavailable-entry skipping, end removal without wrapping, empty-queue addition and contextual disabled actions, entry timeout, popup suspension/rearming, marked completion with Repeat all and explicit Play continuation, playback advancement during a drag, and navigation cancellation without committing. The build → install → capture/compare → adjust loop corrected immersive-layout entry scrolling; final comparison artifacts and interaction scripts are under `build/visual-comparison/queue/`. The scrollbar reflects the full demo queue rather than the shorter reference fixture; shared chrome remains unchanged. Original emulator preferences were restored after tests. Real audio/service and physical-head-unit validation remain pending.

### Track info — implemented in the demo

Reference: `design/032-track-info-tags.png`. Open this read-only panel from Track Options for its captured track ID; enable that action when the panel is implemented.

- Replace the foreground Track Options content with the Track info panel in the left half, keeping underlying track rows hidden and noninteractive. Preserve the 50/50 split, clock, volume, and bottom transport controls. Hide the search icon while this panel is shown.
- Keep Close above the panel and the Track info heading fixed. Display all ten fields in one vertically scrolling list in this order: Artist(s), Album, Year, Comment, Description, Total album tracks, Track number, Composer, Copyright, Publisher. Use the mockup's label Year for the release year.
- Show labels and values with the mockup's hierarchy, including subdued “Empty” values. A missing, nonexistent, or blank tag displays “Empty”; never omit its row.
- Wrap long values over multiple lines, including comments, descriptions, and artists, so the full value can be read through vertical scrolling rather than truncation or a single-line marquee.
- Display file metadata only. Do not substitute library fallback names or infer album totals from the library. A compound track tag such as `3/12` supplies Track number 3 and Total album tracks 12; without a tagged total, show “Empty”.
- Join separately stored artist values with commas, preserving their order and spelling. Do not split artist names on `/` or `&`.
- Show only the year from a full release date, for example `2004-06-15` becomes `2004`. Keep Comment and Description as separate fields.
- Keep the captured target fixed even when playback advances or transport controls change the playing track. Opening, reading, and closing this panel do not select a track, start playback, or alter the queue. Tag editing is out of scope.
- Close, a normal top-left Back tap, and Android Back return to the entry destination: Track Options for the existing nested flow, or the main player for the planned direct entry. Long-pressing the top-left Back returns directly to the main player under the existing shortcut rules. Blank-space taps do not dismiss the panel.
- Keep automatic browsing return cancelled throughout this nested flow; closing the panel does not re-arm it. Preserve the originating browser's existing restoration behavior.
- If the file is unavailable, show cached tags when available with “File unavailable — showing saved tags”. If no metadata was cached, show an unavailable message instead of presenting every field as “Empty”; unavailable metadata is not evidence of absent tags.

Implementation: the existing Track Options target ID owns the panel; Close/Back only removes its child state. The source input shield and cancelled browsing timer remain active. Raw `TrackTags` snapshots are separate from browser fallback labels and serializable for future metadata storage. Demo All Tracks: Amber matches the reference tags, Anchor has empty tags, and the long-title track exercises multiple artists, a compound track number, and wrapped multiline Comment/Description. Real file scanning, source disconnection events, and persistent library metadata storage remain pending with library integration.

Validation: `assembleDebug`, all 94 unit tests, and `lintDebug` pass (0 lint errors). API 23 (`headunit_a6`) checks cover reference layout, wrapped long values, scrolling to the final fields, fixed target after Next, blank taps, Close/app Back/system Back, long-press Back to home, and cancellation of an armed return timer without rearming on close. Screenshots, comparison, and interaction scripts are under `build/visual-comparison/track-info/`. Cache/unavailable states and raw snapshot serialization are unit-tested; real source detachment and physical-head-unit testing remain pending.

### Sorting and metadata

- All Tracks and song search results: alphabetical by title.
- Album lists: alphabetical by title. Tracks within albums: disc/track number, with unnumbered tracks after numbered tracks within each disc, alphabetically by title.
- Folders browser: Previous directory first, folders before audio files, each group in natural numeric name order. Normal playback queues only direct-directory audio; recursive search queues matching audio results across descendants.
- Custom playlist names: alphabetical. Custom playlist tracks: user-controlled order, new tracks appended. Favorites: newest favorited first. Recent lists: newest import/playback first.
- Missing title: filename. Missing artist/album: Unknown artist/Unknown album. Missing year: Unknown year; always retain the year row directly below Album on the main player. Missing artwork: local placeholder.
- No online metadata/artwork lookups or metadata editor.

### Settings, onboarding and head-unit behavior — functionality interview complete, integration pending

Agreed on 2026-11-11 against implemented references 035–050, including gradient backgrounds and RHD. This supersedes earlier unconditional paused-startup/wake requirements only for the explicitly enabled autoplay policy below. Existing Settings/demo autoplay is not an implementation of that conditional policy. Real library, service, boot, and hardware integration remain pending.

#### Onboarding and shared source management

- Retain Welcome → Drive safely → Storage permission → Select folders → Loading. Back navigates earlier steps; Back on Welcome offers confirmed setup skipping. Continuing without folders requires confirmation. Completing or skipping setup persists completion and prevents onboarding on later ordinary launches.
- Retain the centered three-second loading animation on black, then open the player while discovery continues in the background. The animation does not certify scan completion; publish discovered tracks progressively under the library rules. Setup completion must be saved successfully before reporting it complete; saving errors remain retryable. This onboarding animation is distinct from the readiness-gated splash, which has no forced delay.
- Neither completing/skipping setup nor changing the autoplay switch starts music. Empty setup remains valid and folder management is available later in Settings.
- Onboarding and Settings must share the same saved real sources and per-source exclusions. Replace Settings' separate demo-source list during library integration; folders selected in onboarding immediately appear in Manage folders. Apply the previously agreed scope, overlap, exclusion, confirmation, permission recovery, and rescan rules.
- Individual folder selection remains available when broad audio permission is denied. Granting broad permission never imports unselected device folders. Cancelling a picker leaves existing sources unchanged.
- If onboarding is interrupted by process termination or power loss, preserve successfully saved folders/exclusions and resume at the first unfinished step. Show the driving acknowledgement again if unfinished. Persist step completion independently from final onboarding completion; do not treat persisted Android grants alone as a completed setup.
- Remove all libraries requires confirmation and removes all sources/exclusions, imported track records, and associated playlist/Favorite/recent memberships. Stop playback and clear the queue. Retain empty custom playlists and appearance preferences, never delete music files, and do not restart onboarding. Invalidate pending scans/queue preparation so stale work cannot repopulate removed state.

#### Settings and display behavior

- Apply changes immediately without Save and persist across restarts. On saving failure, show an error and restore the last saved value; do not report an unsaved change as durable success. Preserve playback, current screen, and scroll position during display changes.
- Show status bar defaults off. When enabled, show Android's status bar during ordinary player, browsing, and Settings use; splash/onboarding stay full-screen.
- Disable buttons fade out in main player defaults off. When enabled, keep the affected controls fully visible; when disabled, use the existing inactivity-fading rules.
- Change to right-hand drive (RHD) defaults off. Swap clock and content panes by placement, retaining control orientation/actions, screen state, and scrolling. Background/divider selections remain independent.
- Keep 12-hour time as the default, with the existing 24-hour switch and Default, Vertical, LCD, and Dot matrix clock styles. Date is fixed at DD-MM-YYYY for this release; other date formats are deferred.
- Normal backgrounds: Default (black), Indigo, Coffee, Grey. Gradients: Default, Modern, Potion, Lava. Divider choices: Default, Hidden, Dotted, Squiggly, Beam. Initial selections are solid Default, Default clock, and Default divider.
- Switching Normal/Gradients tabs only browses choices; selecting a row changes the background. On opening Background, initially show the tab containing the applied selection.
- Keep the display awake while Clocarmus is visible, including paused playback and Settings, and release that request in the background. This must not prevent actual head-unit sleep; display-off alone is not a sleep signal.

#### Opt-in autoplay and boot opening

- Allow music auto-playback on start remains available, off by default. With it off, fresh launches, boot, and detected sleep/wake restore paused under the existing policy.
- With it enabled, a qualifying fresh manual launch, boot startup, or reliably detected head-unit wake may resume the restored selected track at its saved position only if playback was active before the preceding unexpected termination/shutdown or detected sleep. Preserve the exact queue/session; this is not a reshuffle or a new browser selection.
- Previously paused playback, natural queue completion, and a triggered Stop after never qualify. Distinguish a deliberate pause/stop from the app's pause caused by a reliable sleep signal; record whether playback was active immediately before sleep. Unknown or inconsistent eligibility must not be treated as evidence of active playback.
- Calls, audio-focus loss, output disconnection, and drive removal clear autoplay eligibility and require explicit Play. A later restart/wake cannot bypass those interruption pauses. Navigation ducking alone is not such a pause; a user pause during ducking is.
- Make one autoplay attempt for a qualifying startup/wake. If the selected track is unavailable or focus cannot be obtained, remain paused without skipping to another track or retaining a delayed retry. Later mounting, permission recovery, focus gain, and output reconnection must not trigger another attempt. Implementation requirement: deduplicate boot/launch/wake callbacks and clear failed eligibility; do not let a subsequent restart revive a denied resume request without new explicit playback.
- Enabling autoplay applies to subsequent qualifying events, never the toggle action or onboarding completion. Reconnecting an Activity to a live playback service preserves its current state and does not trigger a startup attempt.
- Open app after boot is a separate setting, off by default. It requests best-effort UI opening where Android allows; it does not enable autoplay. If boot opening is blocked or disabled, manual launch remains available and follows the separate autoplay policy. No guarantee of forced foreground opening on every Android/head-unit build. When Clocarmus is the selected Home app, Android Home startup is independent of this setting; the Launcher section specifies the disabled control and explanatory state.
- Persist autoplay eligibility, interruption/stop reason, and sleep-resume intent consistently with the selected occurrence and session version. Default new/missing preference keys to off. Startup recovery initializes a coherent paused state before any explicitly eligible policy action; corrupted-snapshot fallback itself remains paused and never starts audio as recovery.

### Optional Launcher mode and app drawer — implemented

Agreed on 2026-11-11. References: `design/051-app-drawer.png` (closed) and `design/052-app-drawer-opened.png` (open), with supplied assets under `design/assets/app-drawer/`. Launcher mode is optional and off by default; ordinary music-player operation remains available.

Implementation: Advanced Launcher mode and best-effort boot-opening settings, optional nonvisual Home entry, Android Home selection and safe disable reconciliation, off-thread launchable-app/icon discovery, nine-app vertical pages, session page retention, RHD placement, and independent drawer inactivity are implemented. The existing clock and content-pane UI are reused. A Home entry forwards an explicit navigation event to the existing singleTask player; it does not forward CATEGORY_HOME, which can create a separate Home-type player task on newer Android versions. Home and boot-opening entry never request demo autoplay. Real service/autoplay eligibility and physical-head-unit boot/wake integration remain pending under their existing sections.

Validation: `assembleDebug`, all 112 unit tests, and `lintDebug` pass. API 23 checks cover chooser cancellation, nine-entry pages and page retention, external Settings launch/return, timeout/background retention, Back priority, RHD, fresh Home staying paused with autoplay enabled, and Home closing Settings/drawer while retaining the live playing player. API 31 checks cover the role chooser, cancelled disable, verified switch-away/disable on return, and Home routing to the existing playing player. Captures and local interaction checks are under `build/visual-comparison/launcher/`. Package-change edge cases, boot restrictions, and OEM hardware behavior still require broader validation.

#### Settings, Home eligibility, and default selection

- Add an Advanced section below Appearance in Settings. Add the Launcher mode toggle with this exact description beneath it: “Make Clocarmus behave as a launcher for persistent app boot.”
- Enabling the toggle makes Clocarmus eligible in Android's Home-app chooser and opens the conventional Android default-Home selection workflow. The app must not silently assign itself as default. When off, its optional Home entry is disabled; retain ordinary app launching independently.
- If the user cancels or chooses another launcher, leave Launcher mode enabled and keep Clocarmus eligible. Display actual default status, including “Not the default Home app”, and provide Choose Home app to revisit Android's selection workflow.
- If another launcher becomes default through system Settings, retain the enabled preference and update the displayed status on return. Never reopen the chooser repeatedly or try to reclaim the default automatically.
- If disabling while Clocarmus is default, guide the user to choose another Home app first. Disable Home capability only after verifying the switch succeeded. Cancellation leaves Launcher mode enabled. If no other usable Home app exists, retain the enabled state and explain that another launcher must be enabled or installed first. If Clocarmus is not default, disable immediately.
- Treat enabled capability, requested disable, and actual default-Home status as separate states. Reconcile on return from system UI rather than assuming a successful chooser launch means a changed default. Keep ordinary access to Clocarmus and Android Settings throughout the workflow.

#### Home, boot, and playback

- Once selected as Home, pressing Android Home returns directly to the main player and clock, closing the drawer, browsing, Settings, and popup stack. Preserve playback, position, queue, modes, and already committed edits; discard unfinished UI input under the existing return-home rules.
- A Home-button activation never triggers autoplay, including when Android must recreate the process. Qualifying boot/reliably detected wake events still use the agreed opt-in previously-playing policy. Merely entering/re-entering Home is not evidence of boot/wake; implementation must distinguish qualifying events without turning ordinary Home navigation into a resume request.
- Android may open its selected Home app regardless of Open app after boot. While Clocarmus is the actual default Home app, disable that setting's control, retain its saved value, and explain that Android opens the selected Home app independently. Restore the control with its retained value when Clocarmus ceases to be default. Launcher mode enabled but not default does not disable the boot control.
- Keep autoplay independent of launcher selection. Launcher status grants no entitlement to bypass focus, availability, interruption, or restoration rules. Returning from another app preserves the existing service state.
- Launcher mode does not promise an immortal process, forced foreground takeover, kiosk restrictions, or reliable ignition/wake behavior on every OEM. Follow Android's conventional Home workflow and validate it on the actual head unit. No device-owner provisioning or kiosk mode is part of this feature.

#### App drawer access, contents, and paging

- When Launcher mode is enabled, show the drawer button beneath the clock wherever the clock pane normally appears, including music browsers and Settings, regardless of whether Clocarmus is the current default Home app. Hide it when Launcher mode is off and during splash/onboarding.
- The drawer button joins the main-player inactivity fading group. Apply the same 10-second delay, 500 ms fade to 20%, touch restoration, and Disable buttons fade out override. Existing screen-specific fade suspension still applies; do not introduce fading across browsing/Settings. Opening the drawer suspends main-player fading as another active panel; closing it restores controls and starts their normal fresh interval.
- Opening the drawer replaces only the clock pane with the reference grid. Keep player/content-pane controls usable and preserve its screen/state. With RHD enabled, the drawer follows the clock pane to the left without mirroring its controls.
- Show launchable apps alphabetically by displayed name with actual icons. Exclude Clocarmus itself; provide Android Settings via the dedicated top shortcut. No custom ordering, app favorites, or additional app-management actions are included initially.
- Show nine apps per page, arranged as the reference three-by-three grid. Use vertical swipes and tappable vertical page indicators to navigate. Remember the last page during the current app session, including external-app round trips; start at page one after a fresh process start. If the list shrinks, clamp to the nearest remaining page.
- Refresh after app install/removal/disable changes and when returning to Clocarmus. Discover only entries that can actually be launched through supported Android mechanisms; validate package visibility and launch behavior for supported API levels during implementation. Avoid blocking the UI while loading icons/list data.
- Launching an app or Android Settings successfully closes the drawer. Music continues unless the external app causes an audio interruption under existing focus rules. Returning shows the clock. If launch fails, keep the drawer open, show a brief explanation, and leave playback unchanged.
- The drawer Back arrow and Android Back close the drawer and restore the clock without navigating the content pane. Android Home follows the main-player-and-clock rule above when delivered to Clocarmus as Home.

#### Drawer inactivity and background lifecycle

- Use the same configured duration as browsing's automatic main-player return: currently 15 seconds. Start on drawer opening. User activity anywhere in Clocarmus, including the drawer, player, browsing, and Settings, resets this drawer timer.
- Holds/drags anywhere in Clocarmus defer timeout while a finger remains down; begin a fresh full interval after release. Clock ticks, progress updates, and automatic track changes are not activity.
- Drawer timeout closes only the drawer and restores the clock, leaving the content pane unchanged. Preserve each screen's independent browsing-return rules; opening the drawer must not arm a main-player return from Settings or override its cancellation policy.
- Backgrounding without a successful drawer app launch suspends the timer and retains the open drawer. Ordinary return preserves it with a fresh full interval. Home activation instead closes it and returns to the main player. Successful drawer launches already close it and therefore do not reopen on return.
- Keep drawer timeout, browsing return, and main-player fading as distinct state machines sharing activity observations where appropriate. Cancel stale drawer callbacks on close, mode disable, Home, or screen lifecycle changes; never let an old timer close a newly opened drawer.

## 3. Library, playlists, and persistence

### Sources and discovery — functionality interview complete, implementation pending

Agreed on 2026-11-07. The following rules govern the planned real library; existing demo browsers and persistence do not implement them yet.

#### Source scope and exclusions

- Index only folders explicitly selected by the user on internal storage or accessible USB/SD storage. Include subfolders recursively; do not automatically import unrelated device audio.
- Support excluded subfolders within each selected source. Exclusions apply to that source only: a location excluded from one source remains eligible if another selected source includes it without exclusion.
- Overlapping selected folders must index the same physical location only once. Identical copies at different locations remain separate library entries.
- Include supported audio regardless of duration, filename, or Android music classification. Source selection and exclusions determine membership; do not apply a short-track filter.

#### Scanning and refresh

- Run incremental background refresh on app startup and selected-drive reconnection. When supported, detected changes within selected folders also trigger refresh; combine bursts into one incremental refresh. Do not promise reliable live detection on every provider/device; manual rescan remains available.
- Offer both Rescan this source and Rescan all sources. Both update existing records incrementally, preserving identity and memberships; neither is a destructive rebuild.
- Keep all traversal, metadata reads, artwork work, and content inspection off the UI thread. Existing indexed music remains browsable and playback remains usable during refresh; startup must not wait for a complete scan.
- During the first scan, publish completed imports progressively so tracks can be browsed and explicitly played before the scan finishes. Scanning never starts playback automatically.
- Use one coordinated scan at a time. Combine repeated requests for a source already being scanned; queue newly requested sources. Implementation detail: do not lose a change reported after its directory was already visited—retain dirty state for a follow-up pass where needed.
- Allow cancellation. Preserve completed imports/updates, mark the scan incomplete, and never mark unvisited files missing because of cancellation, failure, permission loss, or disconnection. Confirm absence only from successful traversal of the relevant accessible scope.
- Report added, updated, confirmed-missing, and failed-file counts in an in-app summary, with failure details and an explicit incomplete/cancelled state. No scan-completion notification is requested.
- Cache identity and metadata work instead of rereading every unchanged file on every refresh. Keep scan/reconciliation results versioned for stable queue snapshots and Genuine Shuffle's cached membership access.

#### Availability, cleanup, and source removal

- Disconnected-source tracks remain visible as unavailable, retaining favorites and playlist memberships. Playback skips unavailable queued entries under the existing finite-skip rule; disconnecting the drive containing the playing track pauses instead of advancing. Reconnection refreshes availability without starting playback.
- If a completed scan confirms a file missing from an accessible source, retain its record as unavailable until explicitly removed. Preserve references for a confidently matched return. A failed/incomplete scan is never evidence that all missing files were deleted.
- Lost folder permission preserves the source and records, marks it inaccessible, and offers Grant access again. Restored access triggers refresh, never playback.
- Provide Remove unavailable tracks with a preview and confirmation. Despite its broad label, only files confirmed missing by completed scans qualify; exclude disconnected/permission-blocked sources and read failures without confirmed absence. Remove eligible library records and playlist/favorite references, never physical files. Revalidate eligibility before committing cleanup.
- Explicit source removal and adding an exclusion require confirmation explaining the affected library and playlist/favorite entries. Remove entries no longer covered by any selected, non-excluding source; do not delete physical files. Tracks still covered elsewhere retain identity and references, with availability reflecting actual access.
- If confirmed source removal or exclusion removes the currently playing track from the library, stop playback and clear that selection without automatically starting another song. This is distinct from temporary drive disconnection, which preserves restoration state. Tracks retained through another source are unaffected by the removal.
- Undoing an exclusion or re-adding a removed source imports eligible files again. Deleted playlist/favorite memberships do not automatically return. Reconnecting an existing source instead restores its preserved records and memberships.

#### Identity, metadata, and scan errors

- Confidently identified moves/renames within selected sources preserve stable track identity, favorites, playlist memberships, and history. Use background content inspection and cached evidence; do not infer identity from matching titles alone.
- If multiple identical copies make a relocation ambiguous, retain the old entry as unavailable and import the new location separately. Never guess which copy should inherit references. Matching details still require implementation validation.
- Refresh externally edited tags at the same location while preserving identity and memberships. Moving and editing a file simultaneously may prevent confident identification; that limitation is accepted. This does not establish a rule for an unrelated file replacing the original at the same location.
- Import otherwise supported tracks without tags or artwork. Use filename for missing title, Unknown artist/Unknown album, and local placeholder artwork; preserve the existing Unknown year rule. Store raw tags separately so Track info does not misrepresent display fallbacks as file metadata.
- Continue scanning after individual unreadable/corrupt-file errors and record them in the summary. Keep already-known unreadable tracks as unavailable with cached metadata. Newly found files that cannot be identified as supported audio appear in failure details rather than as playable entries; ordinary unrelated non-audio files are simply outside the import scope.
- Refresh displayed tags/artwork without restarting playback. Keep active queue membership and order stable; discoveries become eligible when the next queue/session snapshot is created. Removed/unavailable queued tracks follow the existing skip rules, and explicit source removal of the current track follows the stop rule above.

### Playlists, favorites and recent lists — functionality interview complete, implementation pending

Agreed on 2026-11-09. Existing demo interfaces remain applicable; transactional real-library storage and actual playback-start integration remain pending.

- Custom playlists have unique track membership and user-controlled order. New additions append; playback queues may independently contain duplicate occurrences. Create, rename, delete, membership changes, and reorder preserve stable playlist identity except on deletion.
- Trim surrounding whitespace from names; reject blank names, case-insensitive duplicates, and reserved names Favorites, Recently added, and Recently played. Rename preserves membership and order. Built-in collections cannot be renamed or deleted.
- Removing an individual playlist entry or favorite is immediate and needs no confirmation. Deleting a custom playlist requires confirmation and removes its membership records, never files or the active playback queue.
- Playlist edits/deletion leave an existing queue snapshot unchanged. A future shuffled Repeat All session uses updated membership when the source playlist exists. If that playlist was deleted, use the retained collection snapshot until another collection is selected; do not silently substitute the entire library or recreate the deleted playlist. Preserve that snapshot independently of temporary queue edits and exclude unavailable/removed library tracks under the existing library/playback rules. Recreating a playlist with the same name does not restore its former identity.
- Keep one shared Favorites collection, ordered newest-favorited first. Removing and re-adding a favorite gives it a new timestamp and moves it to the top. Membership changes never affect playback.
- Retained unavailable tracks can be added to or removed from custom playlists and Favorites. Availability restrictions on playback must not disable metadata-only membership management. These actions do not restore audio access.
- Manage playlists checkboxes apply changes immediately without Save or membership-removal confirmation. Persist playlist/Favorites edits transactionally. If saving fails, retain the last successfully saved state and show an error rather than reporting success for a change that disappears on restart. Keep the UI responsive while saving; any pending presentation must resolve to committed state, not false success.
- Recently added holds 50 distinct tracks, newest initial-import time first. Unchanged rescans, reconnections, and identity-preserving moves do not reset import time. Explicit deletion/re-import follows the library identity rules.
- Recently played holds 50 distinct tracks, newest playback start first. Count actual new playback starts, including explicit replays and every Repeat One iteration, moving the track to the top without duplicates. Ordinary pause/resume, seeks within a track, failed starts, unavailable selections, and paused restoration do not count. Detect actual playback iterations rather than equating every playing-state callback with a new start.
- Both recent lists are read-only: no Clear action, per-entry manual removal, or reordering. They update through import/playback events, the 50-track limit, or explicit removal of underlying library records. Disconnection alone preserves retained entries.
- No local playlist/favorites export/import in v1. Keep existing overview-versus-player-popup creation behavior and membership UI/navigation rules.

### Logical data model

Keep all data on the device:

- Selected source: stable ID, granted folder reference, display name, access/scan status, and source-specific excluded subtree references. Track source coverage separately so overlapping sources share one location record.
- Track: stable app ID, source/location reference, availability with reason (including source disconnected, access lost, confirmed missing, or unreadable), metadata, duration, artwork reference, file attributes/content identity, first-import timestamp, and scan evidence needed to distinguish confirmed absence from incomplete traversal.
- Scan state: source/scope and membership version, progress, pending refresh/dirty state, successful traversal evidence, completion/cancellation status, and summary/failure details. Prevent stale scans from undoing source/exclusion changes.
- Track genre memberships: original display values and normalized matching keys, supporting multiple explicit values per track without duplicate membership.
- Playlist and ordered playlist membership, with unique track membership per playlist.
- Favorites with membership timestamps, and distinct-track recent playback-start timestamps.
- Playback snapshot: queue with stable occurrence IDs (distinct from track IDs), selected occurrence/index, position, shuffle order, repeat mode; extend with explicit next-track scheduling order and the optional stop-after occurrence marker when Track Options scheduling is implemented. Restore paused by default, subject to the opt-in autoplay policy; include eligibility and interruption/sleep intent consistently with session state.
- Shuffle collection provenance: external scope/query identity or manual collection identity, retained collection snapshot/original order, occurrence multiplicities, deliberate replay intent separate from consumption, previous generated order/history, and comparison inputs for prepared queues. A transient browser query or a generated order must not replace the retained source definition.
- Preferences: onboarding completion and first unfinished step, autoplay and boot-opening switches, appearance/display settings, 12/24-hour clock mode (date fixed DD-MM-YYYY), and shared source configuration.
- Bounded local diagnostic events.

A location identifies a physical copy; a content fingerprint is evidence for relocation, not a global uniqueness constraint. Use transactional persistence for playlist changes and identity reconciliation. Deleting a playlist never deletes audio files.

## 4. Playback and platform behavior

Validate MP3, M4A containing AAC, OPUS, FLAC, OGG Vorbis, and WAV using representative fixtures. Do not promise every codec profile or container variant merely because an extension is recognized.

- Playback runs independently of UI navigation and continues in the background.
- Support thumb-drag-only seeking with preview and release-to-commit, +/-10 seconds, shuffle, repeat one, repeat all, and repeat off. Taps on the seek bar never seek.
- Previous restarts the current track after three seconds of playback; otherwise selects the previous track.
- Volume slider controls Android media volume continuously during valid drags from its invisible fill-endpoint target; taps never change volume. Integrate standard media-session controls for supported steering-wheel/media buttons.
- Persist track, position, entire queue, shuffle order, and repeat mode. Restore paused by default after fresh app restart, boot, or head-unit sleep/wake; the Settings interview defines the opt-in, previously-playing exception.
- Returning to a still-running background session leaves playback unchanged. Do not equate ordinary UI backgrounding with head-unit sleep.
- Navigation prompts temporarily lower volume. Calls and another music app taking over pause playback; remain paused until explicit Play.
- Skip unavailable/unplayable queued entries with a brief message; stop when no playable entries remain. Avoid infinite skipping under repeat.
- Removing the drive containing the actively playing track pauses instead of automatically advancing. Preserve its restoration state.
- Boot UI opening is best-effort and opt-in/off by default, separate from opt-in playback resumption. Manual launch is acceptable on unsupported devices; apply the same autoplay eligibility rules.

### Playback and queue lifecycle — functionality interview complete, implementation pending

Agreed on 2026-11-09. These rules specify real playback and supersede conflicting demo behavior or older queue wording. Existing UI/demo tests do not establish real-engine compliance. Persistence, restoration, and interruption details are specified in the completed interview section below.

#### Starting playback and collection snapshots

- Selecting an available track from a browser replaces the queue with a snapshot of that browser's displayed tracks and active search/filter scope, and immediately plays the chosen song. Replace existing playback without confirmation. Preserve the existing explicit Play all/Play album shuffle-off rules and collection-scope exceptions.
- With Shuffle off, retain the full collection in displayed order, start at the selected track, and continue toward the end. Earlier entries remain accessible through Previous or Queue; they play automatically only after wrapping with Repeat All.
- Selecting the currently playing song from a browser is a new playback request: rebuild the scoped queue and restart that song. With Shuffle on, apply the agreed user-selected-opening exception and shuffle the remainder.
- Selecting a different Queue occurrence starts that exact occurrence from the beginning without rebuilding the queue. Tapping the current occurrence resumes if paused and otherwise leaves playback uninterrupted; completion/consumed-stop continuation follows the dedicated rules below rather than treating every stopped state as an ordinary pause.
- Play with an empty queue leaves playback stopped and shows “Select a track to start playback.” Do not automatically select library music or reconstruct an old queue.

#### Transport and boundary behavior

- Previous restarts the current track when its position is more than three seconds; at three seconds or less, select the previous playable queue entry. Preserve playing/paused state.
- Next immediately selects the next playable entry, overriding Repeat One, and preserves playing/paused state. Manual advancement consumes the skipped track under the Genuine Shuffle rules.
- At the last entry, Next wraps only with Repeat All; otherwise stop at the end. At the first entry, Previous wraps only with Repeat All (when the three-second rule calls for a previous entry); otherwise restart that first track.
- With Shuffle and Repeat All enabled, forward wrapping starts a fresh shuffle session. Backward wrapping uses the existing queue order and is an explicit navigation exception to automatic shuffle consumption. A paused boundary action must not start playback when background queue preparation finishes.
- Natural completion with Repeat Off retains the queue and final track selection. Explicit Play restarts that final track from the beginning; choosing another Queue entry starts there instead. Distinguish this completed state from scheduled-stop continuation and ordinary pause.

#### Queue edits and scheduling

- Reordering, including moving the current occurrence, preserves current playback and position. Subsequent playback follows the current occurrence's new position in the edited order, including while Shuffle is enabled. Existing rules preventing stale shuffle/scheduling from undoing a manual reorder still apply.
- Removing the current entry selects the next playable entry after it from the beginning, preserving playing/paused state. If none remains after it, stop and clear selection without wrapping, even with Repeat All. Other entries remain in the queue.
- Add to queue appends a new occurrence, allows duplicates, and never starts playback. Adding to an empty queue selects the added track but remains paused. Queue changes do not modify the underlying collection.
- Track-based Play next moves the earliest upcoming occurrence of that track directly after the current entry, or inserts one if none is ahead. Requests are newest-first: A then B gives current → B → A. Preserve paused state, Shuffle, and the relative order of untouched entries. Exact-occurrence Queue actions retain their existing targeting and current-entry restrictions.
- Repeat All with Shuffle off repeats the edited queue, including manually added duplicates and excluding removed entries. Repeat All with Shuffle on builds the next session from the active collection's latest membership and generates a fresh order. For externally sourced collections, temporary queue additions, removals, and reordering affect only the current shuffle session; actual playlist/library edits affect the next collection snapshot. A queue assembled without an external source instead treats its manually maintained contents, including duplicate occurrences, as its collection across shuffled Repeat All. If the source playlist was deleted, use its retained collection snapshot until another collection is selected, as specified in the Playlists interview; do not substitute library-wide membership or carry temporary queue edits into that fallback.

#### Repeat and scheduled stopping

- Cycle the repeat control Off → Repeat All → Repeat One → Off. Default to Off on first use and remember subsequent choices.
- Pending explicit Play next requests override Repeat One. Once those requests are consumed, Repeat One repeats the track reached last. Manual Next also overrides Repeat One.
- Keep one Stop after marker attached to a specific occurrence. A new stop request replaces the marker; ordinary scheduling/reordering preserves it. At marked completion, stop even with repeat enabled, consume the marker, and retain the remaining queue and repeat/shuffle settings.
- Remove the marker when its occurrence is removed or skipped as unplayable. Manually pressing Next before the marked current song finishes clears its marker and advances normally. Explicitly selecting a different Queue entry cancels the pending stop. Pausing or seeking within the marked song preserves it. Queue replacement clears stale scheduling/markers under the existing rules.
- After a scheduled stop, explicit Play continues to the next playable entry rather than replaying the stopped song. At the boundary, wrap only with Repeat All and generate a fresh session if Shuffle is enabled. With no next entry and Repeat All off (including Repeat One), remain stopped. Never fabricate a duplicate to provide continuation.

#### Playback failures and seeking

- If automatic playback cannot open a track or decoding fails, show a brief message and advance to the next playable entry. Attempt each remaining entry at most once during recovery; stop when none can play even with repeat enabled. Do not generate repeated shuffle sessions or loop Repeat One to retry an exhausted failing set. Drive disconnection retains its separate pause/preserve-state rule.
- Explicit selection of a known-unavailable track leaves playback and the queue unchanged and shows “Track unavailable.” If the selected track appeared available but fails when opened, use the bounded recovery behavior above.
- Retain drag-preview/release-to-seek, preserve playing/paused state, and clamp ±10-second actions to track bounds. Seeking backward never resets shuffle consumption or changes the queue. Existing cancellation and non-seekable-track rules remain applicable.
- Seeking to the exact end while playing invokes normal completion, including repeat, explicit next requests, and Stop after. Seeking there while paused remains paused at the end; pressing Play then applies completion rules. This is distinct from Play after already-processed natural queue completion, which restarts the retained final track.

### Persistence, restoration and interruptions — functionality interview complete, implementation pending

Agreed on 2026-11-09. These rules govern the real playback service and supersede generic restoration wording where more specific. Existing demo SharedPreferences snapshots are not validation of production durability or audio-interruption behavior.

#### Restoration and playback intent

- On a fresh app start, restore the selected track, saved position, exact edited queue/occurrence identities, shuffle order and consumption progress, repeat mode, explicit Play next requests, and Stop after state, paused by default. The 2026-11-11 Settings interview adds a separate opt-in autoplay attempt for qualifying previously active sessions; onboarding completion and restoration alone never request playback.
- Returning to a still-running background service reconnects to that same session without pausing, restarting, or rebuilding its queue. Playback may continue while another app is visible or the display is off. Activity recreation must not apply fresh-process paused restoration to a live session.
- If the restored selected track's drive is absent or access permission is lost, retain selection and saved position and show it as unavailable. Reconnection/access recovery does not resume playback. Explicit Play while it remains unavailable shows a message and preserves the session rather than automatically choosing another song; Next can move to another entry under normal transport rules.
- Preserve continuation state explicitly: an unconsumed Stop after marker stays attached to its occurrence; an already-triggered stop stays consumed. Play after restoration advances after a scheduled stop, including the agreed Repeat All boundary behavior, whereas Play after natural Repeat Off completion restarts the retained final track. Also preserve paused seek-to-end state so Play applies completion rules exactly once rather than conflating it with an already-processed completion.
- Swiping Clocarmus out of recent apps does not act as Stop; allow active service playback to continue. Force-stop or process termination can end playback; a later fresh launch restores paused by default and applies only the explicitly eligible autoplay policy. Do not promise service survival against Android/OEM process management.
- Open app after boot and startup autoplay are separate opt-in settings, both off by default, as specified in the Settings interview. When automatic opening is unavailable, manual launch restores the same session and applies the same eligibility policy.

#### Checkpoints, consistency, and recovery

- Target a position checkpoint every five seconds while playing, plus promptly on pause, completed seeks, track changes, and queue/mode changes. “Immediately” means schedule persistence at the transition, without waiting for the next periodic checkpoint or blocking the UI/audio thread on storage.
- Save large queue/order data only on structural changes. Save small cursor/position/mode/continuation updates independently while linking them to the correct queue version; a checkpoint must never combine a new cursor with an incompatible old queue. Include session completion, marker consumption, and consumption progress in consistent transition persistence.
- Preserve the actual generated shuffle order and per-collection freshness history under the Genuine Shuffle specification; do not reconstruct the queue from a small seed on restart. Background preparation alone is not a committed active session.
- Use atomic/versioned persistence and retain a valid recovery point. For a damaged or incomplete playback snapshot, restore the newest valid, internally consistent saved session, paused. If none is valid, show an empty paused player with an in-app explanation; preserve library, playlists, and settings. Never guess a replacement queue or start playback as recovery.
- On write failure (including full storage), keep playback usable and preserve the last valid saved session. Show an in-app warning that recent progress may not survive restart, and retry in the background without repeated disruptive warnings. Implementation default: serialize/coalesce writes with bounded retry backoff so an older pending save cannot overwrite newer committed state.
- Five seconds is a checkpoint target, not a guaranteed maximum loss window. Abrupt power loss, scheduling delays, failed writes, or fallback to an older valid snapshot can lose more recent state. Validate actual persistence behavior; do not claim sudden-power-loss durability from emulator-only tests.

#### Audio interruptions and output changes

- Navigation announcements temporarily duck music while playback continues underneath, then restore normal playback volume. If the user pauses during ducking, remain paused after the announcement; restoring volume must not resume playback.
- Calls and another audio app taking focus pause playback and require explicit Play afterward, including after transient focus loss. Focus restoration is not permission to auto-resume. Navigation ducking is the specified exception to pausing for an interruption.
- Explicit Play during a call or while audio focus cannot be obtained leaves playback paused and gives a brief explanation. Do not retain a delayed automatic-resume request; the user must press Play again after the interruption.
- When Android reports active-output disconnection, pause to prevent unexpected switching to speakers. Reconnecting the output does not resume playback.
- On a reliable head-unit sleep signal, checkpoint pre-sleep playback intent and pause; waking stays paused by default, with only the Settings interview’s opt-in previously-playing exception. Display-off and ordinary backgrounding alone are not evidence of head-unit sleep. Unsupported/OEM-specific signals require documented device validation.
- Notification and supported hardware/media controls use the same authoritative queue and playback rules as the UI. Explicit Play through those controls can resume, subject to focus and track availability. Restoration itself never synthesizes a Play command.
- Implementation requirement: handle overlapping interruptions and delayed callbacks centrally. Clearing ducking or one interruption must not undo a user pause, sleep, output loss, or denied-focus result. Apply ducking as temporary attenuation without overwriting the user's media-volume setting; user volume changes during an announcement must not be reverted by a stale saved volume value.

### Genuine Shuffle — functionality interview complete, implementation pending

Initially agreed on 2026-11-06; cross-feature conflict review completed on 2026-11-11 against Library, Playback/queue, Persistence, Playlists, and Settings interviews. Randomness and performance are co-priorities. The reconciled rules below govern shuffle interactions; the implemented demo does not yet meet these requirements. Optional Launcher mode is now specified separately and does not change shuffle scope/order or make Home navigation a new shuffle session.

#### Randomness contract and scope

- Shuffle the active collection: All Tracks, artist, album, playlist, genre, folder, or search results. Never silently expand a scoped queue to the entire library. Preserve existing explicit Play all/Play album actions that disable shuffle.
- A first unconstrained session is uniform over all permutations of its eligible track snapshot. Later sessions are uniform over permutations satisfying the freshness rules below. The user explicitly chose conditional uniformity, accepting dependence between sessions: forbidden similar orders are not reachable immediately after a particular previous order.
- Every permitted ordering must be reachable even for 100k+ tracks. No artist balancing, popularity weighting, random-key sorting, modulo-biased index selection, or fixed small seed that limits the reachable permutation space. Do not describe a finite-state generator as proving unrestricted permutation reachability merely because it is cryptographically secure.
- Generate each eligible track once per automatic collection pass, except explicitly requested duplicate occurrences in manually assembled queues. Distinct physical copies retain their distinct library IDs under the existing identity policy. Manual replay/navigation overrides below do not reset session consumption.
- Selecting a song from a browser starts a new session/queue with that song first. Its chosen position overrides opening freshness rules; sample the remaining order uniformly subject to applicable freshness checks. Selecting an occurrence inside Queue preserves the session and actual queue order.

#### Session lifecycle and deliberate overrides

- Enabling Shuffle during playback preserves the current track, position, and playing/paused state, and shuffles the eligible upcoming remainder: unconsumed entries plus explicit scheduled/replay/duplicate occurrences. Previously consumed entries do not regain eligibility merely through a toggle. Explicit Play next requests retain priority; deliberate replay requests must not be discarded by consumption filtering.
- A track skipped with Next is consumed, even after only brief playback. Implementation default: forward jumps past entries consume those entries for the automatic pass. Track consumption separately from cursor and deliberate replay intent; navigation backward does not clear that history. Nevertheless, deliberate backward navigation replays the existing sequence: in A → B → C → D, Previous from C to B permits B → C → D on completion. This is an intentional replay exception, not a reset of consumption for future shuffle toggles.
- Previous, explicit selection of an already-played occurrence, Repeat One, manual duplicate additions, Play next, and manual queue ordering remain intentional exceptions to the automatic no-repeat/uniform-order guarantee. Dragging a played occurrence into the future deliberately requests replay when reached. Respect the edited order until another explicit ordering action (including switching Shuffle off) changes it; do not silently undo user changes to restore randomness. Repeat One does not start a new shuffle session.
- Disabling Shuffle keeps current playback uninterrupted, preserves explicit Play next requests first, then restores remaining eligible collection entries (including deliberate replays) to the collection snapshot's original order. Explicitly appended occurrences retain append order afterward. This explicit toggle supersedes ordinary drag ordering. Re-enabling Shuffle reshuffles only the eligible upcoming remainder, compares it against that remainder's immediately preceding order using size-aware freshness limits, and preserves pinned scheduling/replay exceptions. It neither resets consumption nor overwrites the original generated-session history used for future sessions. Current/fixed entries are excluded from the freely permuted comparison; do not apply a whole-session previous-opening rule to an already-playing current track.
- At completion, Repeat All starts a fresh shuffled session only when Shuffle is enabled. With Shuffle disabled, Repeat All repeats the edited queue order, including explicit duplicates and removals. With Shuffle enabled, an externally sourced collection uses its latest membership; temporary queue edits do not carry into that collection. A deleted playlist uses its retained collection snapshot as already agreed. A manually assembled queue without a source collection instead uses its manually maintained contents, including intentional duplicate occurrences, as its collection. Repeat Off stops; the existing one-shot Stop after rule still takes precedence.
- A queue built entirely through Add to queue has its own collection identity, membership, and original manual ordering, separate from generated playback order. Retain requested occurrence counts across shuffled Repeat All; never fall back to the full library. Persist this provenance with the session so restoration can distinguish a manual collection from temporary additions to an external collection.
- If the next collection snapshot has no eligible tracks because membership was emptied, excluded, or removed, stop at the boundary with a brief explanation. Do not reuse obsolete membership or substitute the library. The deleted-playlist snapshot exception remains distinct and still obeys library removals/availability; an empty manual collection also stops.
- For a search-created collection, retain its original query and scope and refresh matching membership at the next shuffled-session boundary. Editing the browser query alone does not retarget playback; selecting a new result starts a new collection/session. Scope/query identity is independent of incidental browser UI state.
- New library additions wait until the next session. Removed/unavailable tracks are skipped in the active snapshot, with finite traversal when none can play. Removing the drive containing the playing track still pauses under the existing rule. Implementation default: skipped unavailable entries are consumed for that pass; reconnection does not insert them back into its remainder.
- Restore the exact queue, generated order, consumption, cursor, position, shuffle/repeat state, scheduling, and stop marker after process restart/boot/sleep, paused by default. Resume the same session on Play or a qualifying opt-in autoplay attempt; restoration never creates a fresh shuffle session. Returning to an existing running service preserves its playback.

#### Freshness checks and small collections

- Compare with the most recent committed shuffle session for the same collection, using its originally generated order rather than subsequent manual edits. Switching collections does not erase this history. Preparation alone does not commit a session/history update.
- Collection identity follows its semantic scope (stable playlist/artist/album/genre identity, folder plus recursive/direct scope, or search scope plus normalized query). Membership changes do not alone create a different collection identity. Keep history on disk and load only the relevant record; do not retain every collection's full history in RAM.
- Require a different first track from the same collection's previous generated first track, and prevent an immediate repeat of the most recently audible track globally, even when that track belonged to another collection. Both apply when feasible; a user-selected opening overrides them. Implementation default for conflicts in tiny collections: avoiding an immediate audible repeat takes priority over changing the previous opening. One track necessarily repeats; zero tracks yields an empty stopped queue.
- Count same-position matches and shared directed adjacent pairs separately. A → B matches A → B, not B → A. Implementation default for changed membership: project both orders onto shared eligible track IDs before measuring positions and pairs; no shared IDs means no overlap checks. Exclude user-fixed positions/entries from similarity scoring and compare the freely permutable portion. These projected-pair semantics are deliberate and must be tested.
- Delegated threshold choice: for more than eight freely permutable tracks, allow at most three same-position matches and three shared directed adjacent pairs. Fixed counts keep permitted resemblance small even at 100k tracks. If the comparable overlap has eight or fewer tracks, treat that overlap with the small-collection rule below rather than imposing an infeasible cutoff.
- For at most eight comparable freely permutable tracks, enumerate their possible relative orders off the UI thread (at most 40,320). First retain feasible opening constraints, relaxing only as described above; choose the smallest achievable total of position matches plus directed-pair matches. Accept all orders attaining that total, without an arbitrary tie-break favoring a particular ordering. This defines achievable size-aware limits, including singleton/two-track cases. When other nonshared tracks exist, evaluate openings in the full candidate context and verify feasibility rather than excluding feasible full permutations based on projected openings.
- For manual collections containing duplicates, sample distinct queue occurrences uniformly, preserving their multiplicities, but score freshness by track identity. Swapping occurrence IDs for the same track must not improve the score. Compare directed-pair multiplicities, not just distinct pair membership, so repeated identical transitions count. Use duplicate-aware comparison/feasibility logic rather than a single previous-position/successor value per track.
- Relax opening/similarity limits only when the requested multiplicities or tiny collection make the ordinary limits infeasible, not because random attempts happened to fail. For an all-A manual queue, retain every requested occurrence and accept unavoidable audible repetition. Define any duplicate-aware relaxation deterministically from the comparison inputs before sampling; keep uniformity among accepted occurrence permutations. Efficient feasibility/relaxation for large duplicate-heavy queues remains an implementation proof/benchmark requirement, not a claim that exhaustive enumeration scales to them.
- Sample uniformly from the resulting admissible set: use exact uniform selection for a wholly tiny queue, or unbiased full-permutation rejection sampling for larger queues. Never choose the best of a fixed number of random candidates, repair candidates with swaps, or return an unchecked fallback, since these change the agreed distribution. Prove feasibility for supported constraint combinations before starting rejection; fixed/user-controlled slots and changing membership need explicit coverage.
- Bound each background work batch and check cancellation between batches; there is no hard bound on total rejection attempts. Prepare the next session ahead of completion. Current playback continues uninterrupted while preparing. If preparation is unfinished at the boundary, allow a brief wait instead of weakening constraints. UI controls remain responsive; pause, queue replacement, or scope/version changes invalidate stale work. Do not autoplay a prepared result after an intervening pause or stop.

#### Codebase findings and implementation direction

Inspection on 2026-11-06:

- Kotlin/Jetpack Compose, minimum API 23. `app/build.gradle.kts` and `gradle/libs.versions.toml` contain no Media3/ExoPlayer, Room, or other real playback/database dependency. The manifest declares no playback service. No engine or database has yet been selected or integrated.
- `MainPlayerScreen.kt` owns demo playback state and a one-second simulated completion clock. `TrackMenuPane.kt` and related browser fixtures supply hardcoded in-memory metadata. `PlaylistStorage.kt` and `DemoSessionStorage.kt` store JSON in SharedPreferences; these are demo persistence, not a music database.
- `TrackScheduling.kt` uses string track IDs, UUID occurrence objects, linear lookup/list copies, and `.shuffled()` with a pinned current track. Shuffle toggling occurs in the UI callback. The position-driven session effect serializes the entire queue repeatedly; `SharedPreferences.apply()` does not move preceding JSON construction off the calling thread. These paths are unsuitable as the production 100k-track design.

Planned approach, subject to compatibility verification and measured integration:

- A service-owned queue/session coordinator is authoritative; UI sends commands and observes lightweight state. Assess AndroidX Media3/ExoPlayer and a transactional local database such as Room/SQLite, while retaining minimum API 23 and verifying exact compatible versions before adoption. These are candidates, not installed components.
- Use a versioned collection snapshot of compact stable numeric track IDs, an original-order mapping, and primitive index arrays for generated/effective order. Preserve stable occurrence identity for duplicates and existing Queue actions. Avoid a metadata object, UUID string, or player MediaItem allocation for every track just to shuffle.
- Use Fisher–Yates with unbiased bounded random draws on a worker. Audit the entropy source against the all-permitted-orders requirement: consume fresh OS-backed random input throughout generation, buffered off-main, rather than deriving the entire permutation from one fixed-width seed. `SecureRandom` is an API candidate, not by itself a proof of the required entropy/reachability; validate the Android provider strategy before implementation. Persist the actual order, not a small replay seed.
- Target O(n) candidate generation/checking and O(n) compact storage. For unique-track collections, build previous-position and successor lookups once per snapshot/comparison, then check overlap in a linear pass with early rejection. For explicit duplicates use multiplicity-aware scoring/indexes, with separately validated feasibility and cost; the unique-track lookup is insufficient. Reuse buffers between attempts; use compact consumption flags, explicit replay intent, and indexed track/occurrence access instead of repeated full-list searches.
- Reuse the library repository's cached/versioned membership snapshot; obtain IDs in one bulk read when needed. No filesystem scan, metadata reload, database random sort, per-track query, or repeated membership query per random attempt. Refresh membership at session boundaries only when the repository version requires it; scanner work remains independent.
- Keep generation, similarity checks, database reads, and queue serialization off the UI/main thread. Before atomic activation, verify the session, effective collection membership/availability/order, scheduling, and most recently audible track against the preparation inputs. Membership changes require background regeneration; allow the agreed boundary wait rather than activate a stale queue. Metadata-only changes that leave membership, availability, and collection order unchanged do not require regeneration. Version the relevant inputs rather than invalidating on every unrelated database write. Save order/membership on structural changes and checkpoint small cursor/position state separately; never rewrite 100k entries every progress tick or place the full production queue in a saved-instance-state bundle.
- A 100k-element IntArray occupies about 0.4 MB of payload and LongArray about 0.8 MB; these are sizing examples, not a total-memory promise. Account for simultaneous current/candidate/history buffers and lookup tables. Virtualize Queue rows and fetch only needed metadata. Avoid submitting/materializing 100k player items on the UI thread; benchmark a bounded player window or suitable timeline integration while preserving exact queue navigation and media controls.
- Player operations must obey the selected engine's owning-thread contract. Off-main shuffle work does not authorize arbitrary cross-thread player calls. Media3 documents single-application-thread access: [ExoPlayer setup/threading](https://developer.android.com/media/media3/exoplayer/hello-world). Random-source reference: [Android SecureRandom](https://developer.android.com/reference/java/security/SecureRandom).

## 5. Proposed on-device architecture

These are implementation directions, not approved exact dependency versions:

- Compose screens and state holders render repository/player state and send user actions through explicit interfaces.
- A library repository owns local database access, source membership, metadata, search, playlists, and recent lists.
- A storage-access layer encapsulates Android-granted folder references, persisted access, traversal, and access recovery.
- A scanner performs cancellable incremental metadata/content processing and transactional reconciliation.
- A playback service owns the player and Android media session; UI controls connect to that session rather than create their own players.
- A playback-state store saves snapshots periodically and on meaningful transitions, and restores coherent state independently of screen creation, paused by default with separately evaluated opt-in autoplay eligibility.
- A settings store and clock presentation layer keep appearance/time updates independent of scanning and playback.
- Local diagnostics expose scanning/playback failures and manual text-report export. No automatic uploads; omit file paths from exports by default.

Before committing to implementations, verify current official documentation for media playback/session libraries, foreground service behavior, storage APIs, persistence libraries, minimum SDK compatibility, audio focus, and boot/background activity restrictions. Pin compatible versions in the version catalog. There is no server, external API, authentication, billing, analytics service, or network dependency in the product architecture.

## 6. Implementation phases

### Phase 1 — Compatibility and feasibility

- Confirm the existing build/toolchain and available AVD API level, dimensions, and density.
- Verify and pin dependencies compatible with API 23; document sources and any necessary configuration changes.
- Validate folder selection and persisted access, representative audio decoding/seeking, background service/session behavior, and paused restoration on minimum and modern Android AVDs.
- Define relocation matching for unique fingerprints and safe ambiguity handling for duplicate copies.
- Document boot-launch and actual sleep-detection limitations rather than presenting AVD behavior as head-unit compatibility.

### Phase 2 — Visual shell and onboarding

- Implement optional Launcher mode and references 051–052: Advanced setting and system Home workflow, actual default-status tracking/safe disable, app discovery and drawer, RHD, fading/inactivity integration, and Home-versus-boot launch handling. Keep normal app entry available and validate compatibility without making launcher mode the default.

- Implement design tokens, supplied branding, responsive fixed-split shell, clock/date formatting, background presets, and persistent controls.
- Integrate the completed Settings/onboarding specification with real sources and playback: persist unfinished setup steps, share folder/exclusion state, apply failure-aware settings writes, implement confirmed Remove all libraries, and add the independent off-by-default boot-opening preference. Retain the implemented welcome and settings screens.
- Implement fresh-start-only splash branding from reference 029, readiness-gated routing, and a 300 ms exit fade. Coordinate Android launch themes with the single-activity UI, preserving paused restoration and allowing scanning to continue after entry.
- Implemented reference 033: Queues/Track info buttons, an explicit fixed-target Track info request with its return destination, endpoint-aligned shared transport, and time labels above the existing seek bar. Queues now opens the implemented Queue screen described above.
- Implement main-player inactivity fading with shared transient opacity/timer state and non-consuming touch observation across both panes. Apply opacity only to the designated controls, including the optional drawer button; cancel pending countdowns/animations when inactive and interrupt fading immediately on touch.
- Implemented shared overflow-only marquees for music titles, row metadata, and artist/album context headings across the nine existing player/browser screens; retain this behavior when connecting real library data. See Long music titles above.
- Add the shared top-left Back long-press shortcut across screens and popups, preserving ordinary Back behavior, playback, existing restoration rules, and immediately applied edits.
- Validate readability on small/wide landscape emulators without changing the 50/50 split.

### Phase 3 — Local library

- Implement database/preferences and the completed Library and source management specification: selected sources and per-source exclusions, progressive recursive discovery, metadata/artwork caching, coordinated cancellable incremental refresh, availability reasons, permission recovery, confirmed-missing cleanup, and in-app scan summaries.
- Implement renamed/moved-file reconciliation and overlapping-source handling.
- Add browsing, search, sorting, playlists, favorites, recent lists, and immediate membership updates.
- Add the four-screen Artists flow with Artist-tag grouping, distinct album counts, artist-scoped tracks/albums and search, retained list state, hierarchical Back, and navigation to shared album options. Reuse the existing browser components with stable track identity rather than title-only selection when connecting real library data.

- Add the Albums list, options, and numbered/duration track browser using the renamed references and new normal/pressed Album Tracks assets. Extend library metadata with Album Artist, disc/track numbers, and duration; keep stable album identities and explicit artist-scope navigation.

- Implement the Playlists overview, named detail lists, creation/rename validation, confirmed deletion, track removal, and custom-track drag reordering. Use both supplied plus-button states only on the overview, and update bounded recent lists and favorites ordering as specified.

- Add genre membership metadata and the five-screen Genres flow with normalized genre grouping, counts, filtered track/artist/album views, retained navigation state, and the explicit whole-album exception. Reuse existing normal/pressed icons in the three-button Browse layout.

- Implemented Manage playlists as a player overlay using shared membership persistence and a reusable creation prompt. Imported the track-collections assets and added open/membership/empty icon states, fixed panel bounds, and automatic selected-track inclusion only for creation from this popup.

- Add the Folders browser with selected-source virtual root, directory listing, supplied row icons, scrolling parent navigation, natural name ordering, relative-path recursive search, restored directory state, and disconnected-source recovery. Keep source boundaries explicit.

- Add shared Track Options entry to eligible track lists, preserving Favorites/custom-playlist context menus. Reuse track collections with an explicit fixed target and return destination; keep deferred actions disabled and cancel automatic return throughout the popup stack.
- Implemented the read-only Track info child panel from reference 032 with wrapped values, fixed-target navigation, hidden search, and cached/unavailable states. `TrackTags` retains all ten raw fields separately from browser labels and supports snapshot serialization. Connecting these fields to scanned-file metadata storage remains part of the pending real-library integration.

### Phase 4 — Playback integration and lifecycle

- Implemented the demo Queue screen from reference 034 with shared controls, occurrence-aware selection and handle-only preview/drop reordering, context-dependent Add/Remove actions, exact-occurrence scheduling, and its entry-armed inactivity timer. Manual playback order, stop marker, and continuation after a consumed stop use the existing paused-restoration snapshot. Real playback-service integration remains pending.

- Implement Genuine Shuffle as specified above, replacing demo queue generation/persistence with the service-owned coordinator, compact cached snapshots, cancellable background sampling, and per-collection freshness history.
- Implement the completed Playback and queue lifecycle specification through the service-owned coordinator, then connect queue construction, service/session controls, seeking, repeat/shuffle, volume, and progress to the UI. Distinguish ordinary pause, natural queue completion, paused seek-to-end, consumed scheduled stop, and cleared selection; do not infer all continuation behavior from one playing boolean.
- Connect artist Play all to queue replacement, alphabetical track ordering, shuffle disablement, and immediate player return; preserve the existing delayed return for individual artist-track selection.
- Implement the completed Persistence, restoration and interruptions specification, including the later Settings opt-in autoplay exception and durable eligibility: consistent versioned snapshots, five-second position checkpoints plus transition saves, paused recovery/fallback, unavailable restored selections, service/UI lifecycle separation, audio ducking/focus/output handling, and shared media controls. Keep all persistence work off the UI/audio-critical path.
- Inactivity-return timer implemented for the browsing shell: arm after selection, reset on interaction, cancel on search typing or Settings/playlist editing entry. Complete service integration, screen-awake policy, best-effort boot entry, and distinguish sleep from ordinary background playback where platform signals allow.

- Connect Play album to whole-album queue replacement, shuffle disablement, disc/track ordering, and immediate player return; integrate filtered individual selection with the existing delayed return.

- Connect playlist selection to stable queue snapshots and the existing inactivity return; update Recently played on actual playback starts without reshuffling the current queue. Cancel automatic return throughout playlist editing.

- Connect genre and genre-scoped artist Play all to filtered alphabetical queue replacement with shuffle disabled and immediate return; preserve shuffle and the inactivity return for individual selections. Whole-album entry uses normal album playback rules.

- Implemented Manage playlists binding to stable selected-track identity in the demo shell: close popup and nested naming prompt on track change, preserve normal transport use, suspend fading while editing, and validate the target before committing changes. Connect automatic advancement when the real playback service is added.

- Connect Folders file selection to direct-directory queue snapshots and recursive-search selection to matching-result snapshots, preserving shuffle and the existing inactivity return.

- Replace shared slider tap/absolute-coordinate gestures with start-target validation and relative horizontal dragging. Separate seek preview/commit/cancel from continuous volume updates, retaining accessibility actions, hardware controls, and inactivity observation.

- Implement occurrence-aware Play next and Stop after this song scheduling, preserving the remaining shuffle order, paused state, and single stop marker. Move existing upcoming occurrences instead of duplicating them; add success toasts and return to the source list.

### Phase 5 — Diagnostics and delivery

- Add bounded local event capture, diagnostic screen, redacted manual report export, and actionable storage/playback errors.
- Run acceptance checks, document unresolved device-specific behavior, and package an installable APK.
- Document reproducible build/install steps and signing-key handling for subsequent updates. Do not commit signing secrets.

## 7. Test and acceptance criteria

### Long-press Back acceptance

- From browsers, nested options, settings, and popup flows that expose the top-left Back button, hold it to reach the main player directly and dismiss the entire open popup stack.
- A short tap follows the existing hierarchy; a recognized long-press navigates once at the normal threshold and release does not fire a second navigation action. Android system Back is unchanged.
- Verify both playing and paused sessions retain track, position, queue, shuffle/repeat, and playback state.
- Verify unfinished text prompts are cancelled without submission, while previously applied membership changes remain saved.
- Reopen each browsing section and verify its existing location, selection, query, and scroll restoration rules still apply.
- Verify the browsing-return timer is cancelled and player controls return fully visible under the existing fading rules.

### Automated behavior checks

- Collection/search/folder queue creation and specified ordering, including the Folders browser distinction between direct-directory playback and recursive search results.
- Playlist uniqueness, ordering, rename/delete, favorites, and distinct recent-list bounds.
- Metadata fallbacks and search across songs/artists/albums.
- Source overlap, disconnect/reconnect, permission loss, interrupted scan, source removal, and rename/move reconciliation with duplicate ambiguity.
- Shuffle/repeat restoration, previous-track threshold, seeking bounds, empty queue, and finite skipping of unplayable tracks.
- Snapshot recovery and paused startup; distinguish UI reconnection from a fresh player session.
- Browsing-return timer starts only after selection, resets on interaction, cancels in editing/search/settings, and cannot navigate away after cancellation.
- Main-player fading: full opacity before 10 seconds, animation begins at 10 seconds, and 20% opacity is reached after another 500 ms in both playing and paused states.
- Touch on either pane immediately restores opacity without suppressing the same touch's action; holds, drags, and multiple fingers prevent fading until the final release.
- Verify all fading-group controls, including the optional drawer button and inactive Shuffle/Repeat appearance, fade while all unlisted elements retain normal opacity.
- Hardware events, clock ticks, playback progress, and track changes do not reset the fading timer; dialog dismissal, navigation back, foreground return, and recreation start fresh without stale countdowns or animations.
- Verify fading and browsing-return timers remain independent and static reference previews remain fully opaque.

### Settings, onboarding and head-unit behavior acceptance

- Test each onboarding step, Back hierarchy, confirmed skipping, empty-folder confirmation, permission denial, folder-picker cancellation, and successful completion. Interrupt/relaunch each step: preserve committed sources/exclusions, resume the first unfinished step, and repeat only unfinished driving acknowledgement. Persist completion only after successful save.
- Verify the three-second onboarding animation is independent of real scan completion and the splash has no artificial delay. Completion/skip never starts audio, even when autoplay was previously enabled. Selected sources/exclusions appear consistently in onboarding, Manage folders, and the repository; broad permission alone imports nothing.
- Remove all libraries: confirm before changing state, keep files/empty custom playlists/appearance and onboarding completion, remove source and music references, stop/clear the queue, and reject stale scan/preparation results. Verify no deleted memberships or playback return on restart.
- Verify immediate persisted settings, failure rollback/error, unchanged playback/screen/scroll, hidden status-bar default and full-screen onboarding, both fading/RHD defaults, all backgrounds/clock/divider choices, 12/24-hour time and fixed DD-MM-YYYY. Background tab changes alone do not alter the applied selection; reopening selects its current tab.
- Exercise autoplay off/on across manual launch, boot, and detected wake with previously playing, user-paused, naturally completed, consumed-stop, empty, unavailable, interrupted, and inconsistent-restoration states. Only enabled, previously-active, uninterrupted eligible sessions get one attempt at the saved track/position. Restored shuffle queues do not regenerate.
- Verify toggle changes and setup completion never start playback; live-service reconnection does not restart it. Simulate overlapping boot/launch/wake events, focus denial, and delayed storage/focus recovery: no duplicate attempt, skipped selection, or latent retry. Calls, focus/output/drive loss clear eligibility across later restarts/wakes; sleep's own pause preserves only legitimate pre-sleep intent.
- Test Open app after boot independently from autoplay with all four switch combinations. Defaults are both off. Validate unsupported boot opening and manual-launch fallback without claiming Android/OEM restrictions are bypassed.
- Verify screen-awake request while visible, including paused and Settings, and its release in background. Display-off is not a head-unit sleep signal. Test sleep/boot/output behavior on representative head units; emulator results alone do not establish platform guarantees.

### Launcher mode and app drawer acceptance

- Fresh installation and missing settings default Launcher mode off; no enabled optional Home entry. Verify Advanced placement, exact description, opt-in capability enablement, conventional chooser, cancellation, selecting another launcher, and accurate default-status/Choose Home app presentation.
- Disable while non-default immediately; while default require a verified alternative selection. Cancellation, chooser errors, or no usable alternative retain Home capability. Changing default externally updates status without prompting to reclaim it. Preserve ordinary app entry and Android Settings access.
- Verify Home from browsing, Settings, nested popups, and an open drawer shows the main player/clock with unchanged live playback/queue/position and no autoplay. Repeat with a fresh process: ordinary Home entry still does not auto-resume. Test genuine boot/wake separately against existing autoplay eligibility without duplicate starts.
- Verify default-Home status disables the boot-opening control but preserves its value, explains independent system behavior, and restores the control when another launcher is selected. Enabled-but-not-default mode leaves the control usable. Do not claim the boot toggle suppresses Android's chosen Home app.
- Compare references 051–052 in ordinary and RHD layouts. Verify availability on every clock-bearing screen, absence when mode is off or in splash/onboarding, actual app icons/names, alphabetical ordering, no self-entry, dedicated Settings shortcut, nine-entry pages, vertical swipes/indicator taps, and current-session page retention/clamping.
- Test package installation/removal/disable and foreground refresh, launch failure preserving drawer/playback, successful launches closing it, and external audio-focus behavior. Ordinary app return shows the clock after a successful launch; unrelated backgrounding preserves an open drawer with a fresh timer.
- Verify the drawer button follows the player fading group and its disable-fading setting, including touch restoration without consuming the opening tap. Browsing/Settings retain existing fade suspension. An open drawer suspends main-player fading; closing starts a fresh interval.
- Verify 15-second drawer timeout, activity in either pane/any screen resetting it, held fingers and drags deferring it, and no reset from automatic playback/clock updates. Timeout/Back restore only the clock; Home closes all navigation to the player. Test independent browsing-return rules, background suspension, close/reopen races, and disabling mode with pending timer state.
- Validate supported API-level chooser/role behavior, package visibility, process recreation and Home intent delivery, plus actual head-unit boot, hardware Home, and wake/OEM behavior. Android restrictions are not bypassed and launcher eligibility is not evidence of persistent process survival.

### Library and source management acceptance

- Import only selected recursive scopes. Verify per-source exclusions, separately selected excluded subfolders, overlapping coverage, one entry per location, and separate entries for identical copies at distinct locations. Short audio and absent Android music classification do not exclude supported files.
- Exercise startup, reconnect, detected-change bursts, per-source and all-source manual refresh, repeated requests, queued sources, and cancellation. Imports become usable progressively, existing playback stays responsive, and refresh never autoplays. Changes during an active pass are not lost.
- Test disconnection, revoked permission, read errors, confirmed missing files, and incomplete traversal as distinct states. None silently deletes records or memberships. Cleanup preview includes only confirmed-missing eligible files and rechecks eligibility after reconnect/return or permission changes.
- Confirm source removal/exclusion and missing-record cleanup never delete files. Preserve records covered by another source. Removing the playing track through confirmed source removal/exclusion stops and clears selection without advancing; temporary drive loss instead pauses and preserves restoration state.
- Verify confident moves/renames and tag edits preserve references/history; ambiguous identical-copy moves do not transfer memberships by guesswork. A moved-and-edited file may be treated as a new import. Reconnection restores retained memberships; re-import after explicit removal does not resurrect deleted memberships.
- Test tag/artwork fallbacks, raw-tag separation, unreadable existing files, corrupt new files, and mixed successful/failed imports. A bad file does not stop the scan; failure details and cancelled/incomplete summaries remain accurate and in-app.
- Metadata refresh must not restart playback or reorder the active queue. New imports wait for a new queue/session snapshot. Versioned scan commits must not reintroduce records after source removal/exclusion or overwrite newer scope decisions.
- Measure large-library scan/query/caching behavior alongside Genuine Shuffle benchmarks; verify responsive browsing/playback and no main-thread traversal, hashing, metadata reads, or bulk persistence. Validate provider-dependent change detection, permission recovery, and USB/SD behavior on physical hardware; emulator checks alone do not establish those guarantees.

### Playback and queue lifecycle acceptance

- Verify browser selection replaces the exact displayed collection/search scope without confirmation, including restarting the same song; Queue selection retains the queue and targets exact duplicates. Known-unavailable selection changes neither playback nor queue. Play on an empty queue prompts rather than inventing a queue.
- With Shuffle off, verify selection partway through a collection preserves the full ordered snapshot and only wraps automatically with Repeat All. Check Previous at positions below, exactly at, and above three seconds; Next overrides Repeat One. Both preserve paused state, including asynchronous shuffle preparation at a boundary.
- Exercise first/last-entry boundaries with every shuffle/repeat combination. Forward shuffle wrapping generates a fresh session; backward wrapping retains existing order. Natural Repeat Off completion retains the final entry and Play restarts it.
- Verify current-entry drag preserves audio/position and subsequent playback follows the new position. Current removal advances only to a later playable entry without wrap, preserving paused state; no successor clears selection. Empty-queue addition selects paused; duplicate additions append distinct occurrences.
- Test latest-first Play next, exact Queue targeting, and pending-request priority over Repeat One, followed by repetition of the last requested track. Repeat All without shuffle retains queue edits; with shuffle it snapshots latest collection membership and discards temporary queue edits for the new session.
- Verify one-shot stop priority over repeat, marker replacement, preservation on pause/seek/reorder/scheduling, and clearing on removal, unplayable skip, manual Next away from the marked current track, explicit different-entry selection, or queue replacement. After a consumed stop, Play advances/wraps only as specified and never replays solely because Repeat One is active.
- Inject open/decode failures across multiple entries and all repeat modes; recovery terminates after at most one attempt per remaining entry and cannot keep regenerating shuffle sessions. Separately verify active-drive removal pauses and explicit source removal stops/clears under the library rules.
- Verify seek previews, cancellation, bounds, ±10 seconds, and unchanged shuffle consumption. Seek-to-end while playing processes completion once; paused seek-to-end defers it until Play. Distinguish natural-completion replay from scheduled-stop continuation and avoid double advancement from simultaneous engine completion and seek callbacks.
- Run meaningful coordinator tests and real-engine/device checks; demo timer tests alone cannot validate audio continuity, decoding errors, seeking, or owning-thread behavior. Also apply the Persistence, restoration and interruptions acceptance checks below.

### Persistence, restoration and interruptions acceptance

- Restore queue edits, exact occurrence IDs, current position, shuffle consumption/order/history, repeat, scheduling, and stop/continuation state after a fresh process start; remain paused by default and test the separately specified opt-in autoplay cases. Cover boot/manual launch, natural completion, consumed scheduled stop, and paused seek-to-end separately. Restoration must not record a new Recently played event.
- Distinguish fresh process creation from UI recreation/return to an existing service. Backgrounding, display-off, and recent-task dismissal must not deliberately stop live playback. Force-stop/process loss followed by launch restores paused by default, with only eligible opt-in resumption; do not claim service survival.
- Restore with absent storage or lost permission: retain selection/position and show unavailable state; Play reports the problem without skipping, Next works normally, and access restoration never starts music.
- Verify five-second playing checkpoints and transition-triggered saves, with no full queue rewrite on position ticks. Inject interrupted writes, stale write completion, mismatched versions, corrupt snapshots, and absent valid recovery points. Recover only internally consistent data, preserving unrelated library/settings data; never invent a queue.
- Simulate full storage and write failures: playback remains usable, the last valid session survives, one actionable in-app warning is presented without repeated disruption, and bounded background retries eventually save current state when storage recovers. Measure 100k-track snapshot/checkpoint behavior off-main; disclose limits of power-loss testing.
- Test navigation ducking and volume restoration, pause during ducking, user volume changes during ducking, calls, competing audio focus, denied Play, overlapping interruptions, and late callbacks. No event clearing an interruption may auto-resume paused playback or revive a denied delayed Play request.
- Disconnect/reconnect the active audio output: pause, clear autoplay eligibility, and remain paused. For supported sleep/wake, verify paused default and the explicit opt-in previously-active exception. Display-off alone preserves playback. Test explicit UI/notification/hardware Play against the same availability/focus checks and queue transitions.
- Validate real audio, background service, notification/media controls, USB/SD loss, audio routes, and sleep/boot behavior on minimum/modern Android and representative head units. Treat provider/OEM-dependent behavior as unvalidated until physically exercised; demo snapshots do not establish production compliance.

### Genuine Shuffle acceptance

- Exhaustively validate tiny-library admissible sets, feasibility, opening relaxation, pinned selections, and directed-pair scoring; test membership changes, projected comparisons, and empty/singleton/two-track cases. Establish uniformity by algorithm reasoning and exhaustive bounded-choice checks where feasible; frequency tests are supplementary, not proof.
- Audit unbiased bounded draws and entropy reachability for large permutations. Verify no truncating seed, modulo bias, random-key collisions, repair bias, or fixed-attempt fallback. Verify each generated queue contains precisely its eligible snapshot once before intentional overrides.
- Exercise normal completion, Next and forward jumps, Previous, Repeat One/All/Off, stop markers, Play next, manual duplicates/reordering/removal, shuffle off/on, browser replacement versus Queue selection, and paused restoration. Consumption history never resets merely because the cursor moved backward or shuffle was toggled. Deliberate backward replay follows the existing sequence and moving a consumed occurrence into the future requests replay; explicit replay eligibility survives toggles without generally re-enabling consumed tracks.
- Verify freshness against the previous original generated order for the same collection across collection switches and process restarts. Manual changes and uncommitted prefetch do not overwrite history. Confirm next-session opening uses the actual last played track at activation; invalidate/regenerate a candidate if that constraint changed.
- Test changes during generation: cancellation, source loss, membership version changes, queue replacement, pause/stop, and restart. Never publish stale results or start audio after the user's pause. Force repeated candidate rejection and confirm responsiveness and permitted boundary waiting without weakening checks.
- Benchmark 100k+ tracks for generation/check latency, rejection counts, peak heap/allocation/GC, queue rendering, cursor advancement, persistence, restore, and player queue publication. Instrument main-thread stalls/frame timing and ANRs; confirm no O(n) shuffle/serialization or blocking database/filesystem work on the UI thread. Verify query counts remain independent of sampling retries and routine track changes do not scan the whole collection.
- Validate on API 23 AVD and representative low/medium-powered physical head units; report measured results and limitations. The original 100-track fixture cannot establish 100k-track performance, and no numerical latency/heap guarantee has yet been agreed.

### Genuine Shuffle cross-feature conflict review acceptance

- In A → B → C → D, Previous from C to B allows subsequent B → C → D while consumption remains recorded. Drag played A after C and verify A plays when reached. Toggle Shuffle off/on and verify scheduled requests/deliberate replays remain eligible without restoring every consumed track.
- Shuffle-off preserves Play next priority, restores eligible collection entries to original order, and places manually appended occurrences afterward in append order. It explicitly replaces ordinary drag order. Re-enabling compares the new freely shuffled remainder with its immediate predecessor, leaving committed original-session history intact.
- Start a collection whose previous opening was A immediately after audible B from another collection: enforce both exclusions where feasible, with the agreed tiny-library/user-selection exceptions. Changing the last audible track during prefetch invalidates an incompatible opening before activation.
- Build a queue solely with Add to queue, including A/A/B and all-A cases. Shuffled Repeat All retains requested occurrence counts and never expands to the library. Track-level freshness must not count exchanging identical-track occurrence IDs as improvement. Verify occurrence-uniform sampling, pair multiplicities, deterministic feasibility-based relaxation, and termination on empty eligible membership.
- Edit search text without selecting a result: playing collection remains tied to the original query/scope. The next shuffled session refreshes that scope. Emptying an existing collection stops at the boundary; deleted-playlist fallback remains separate and does not resurrect removed library tracks.
- Change collection membership/availability/order during preparation and verify regeneration before activation, with responsive cancellation and permitted boundary waiting. Metadata-only updates with unchanged effective inputs must not restart preparation. Do not repeat library scans/queries for random retries.
- Restore manual-source provenance, duplicate counts, explicit replay intent, consumption, and generated history consistently. Qualifying opt-in autoplay resumes that same session, and preparation/restoration alone never commits a new session. Optional Launcher mode must preserve this session state; pressing Home cannot reshuffle or trigger autoplay.

### Long-title acceptance

- Use the documented long artist, album, and track fixture across all nine screens; confirm overflowing titles, row metadata, and context headings scroll automatically, including selected rows with playback indicators.
- Confirm the 1.5-second initial/repeat pauses, continuous looping, and restart from the beginning when text changes. Verify short text remains stationary with its existing alignment.
- Confirm original font size, single-line layout, selection colors, and clipping within each label; ensure scrolling never draws into playback indicators or the clock and does not interfere with row taps or list scrolling.

### Artists-flow acceptance

- Artist-tag grouping differs correctly from Album Artist; missing tags use Unknown artist. Artist rows sort alphabetically and show singular/plural distinct album counts, including Unknown album and disconnected sources.
- Duplicate album copies across sources count once per artist; identical album titles belonging to different artists remain scoped to their artist. Duplicate track titles remain distinct selectable tracks when real library data is connected.
- Artist detail, Tracks, and Albums headings reflect the chosen artist. Tracks have Artist - Album subtitles; album rows alone have year subtitles, including Unknown year.
- Search filters only the appropriate field and artist scope. Empty/no-match results never show tracks or albums from another artist.
- Play all replaces an existing queue, disables shuffle, starts in alphabetical title order using normal unplayable-track handling, and returns immediately. Individual track selection queues displayed results and uses the existing 15-second inactivity return instead.
- Artist-album taps retain selection and open shared album options for the whole album, including compilation tracks by other performers; navigation does not initiate playback.
- Both Back mechanisms follow the specified hierarchy and restore queries, scroll positions, and last-opened artist selection without leaking state between artists.
- Compare all four panes against their reference PNGs while verifying the clock, volume, and transport controls remain available.

### Albums-flow acceptance

- Same album title with different effective Album Artists remains separate; compilation performers share an album when Album Artist matches. Missing Album Artist falls back to track Artist, then Unknown artist.
- Track counts include distinct physical copies and disconnected entries; unknown year and singular/plural subtitles display correctly.
- Album lists are alphabetical; album tracks sort numerically by disc/track (including 2 versus 10), with missing track numbers after numbered tracks within each disc. Rows show duration and the selected-track indicator.
- Play album replaces the existing queue, disables shuffle, and returns immediately; individual selection queues displayed results and retains the inactivity return. Empty/unplayable collections never start unrelated music.
- Artist shortcut uses Album Artist scope without changing the main Artists list; test a Various Artists compilation and an equal name present in both scopes.
- Album navigation from Artists opens the whole compilation. Back retraces each entry path, restoring query, scroll, and selection without scope leakage.
- Interim list/options search stays within the agreed album/title scope and preserves numeric ordering.
- Compare all three panes against the renamed references and confirm both Album Tracks icon states use the supplied assets and colors.

### Playlists-flow acceptance

- Overview shows the three built-ins in fixed order, divider, alphabetical custom playlists, and the empty-custom-list message when appropriate; all rows scroll together. Both plus-button states match supplied SVGs, and no detail screen shows a plus button.
- Create/rename trim names and reject blank, case-insensitive duplicate, and reserved names. Cancel leaves data unchanged. Create stays on the overview; rename preserves membership and stable identity.
- Only custom playlists offer Rename/Remove. Remove requires confirmation and leaves audio files and the active queue intact, including when the deleted playlist supplied that queue.
- Favorites sorts by most recent membership; removing a favorite does not delete its track. Custom-track removal affects only that playlist, new tracks append, duplicate membership is rejected, and drag reordering persists after Done.
- Import/play 51 distinct tracks to verify each recent list holds 50. Replay an older track to verify it moves to the top without duplication. Failed starts and paused restoration do not update Recently played; unchanged reconnects do not update Recently added.
- Selecting a song snapshots displayed order, preserves shuffle, and arms the 15-second return. Subsequent recent-list updates, playlist edits, or deletion do not mutate that queue; editing cancels pending return.
- Verify distinct empty messages, track subtitles/indicators, Back hierarchy and restored scroll positions, and unchanged clock/transport availability. Recently played uses the shared built-in layout; populated custom playlists use `015` and empty custom playlists use `016`.

### Playlists, favorites and recent lists functionality acceptance

- Verify unavailable retained tracks support membership edits without enabling playback. Duplicate adds remain unique, custom additions append, and Favorites remove/re-add moves the track to the top without changing the active queue.
- Inject transaction/write failure for creation, rename, membership, reorder, and deletion: retain committed state, show an error, and never display false success. Verify stable identities and atomic creation-plus-membership from the player popup.
- During playback, edit and delete the source playlist: current queue remains unchanged. With Shuffle + Repeat All, use current membership for existing playlists and the retained collection snapshot for a deleted playlist. Verify this fallback survives session restoration, respects library removals/availability, excludes temporary queue edits, and does not attach to a newly created same-name playlist.
- Verify actual starts, explicit replay, Repeat One iteration, ordinary resume, seek, failed start, and paused restoration independently against Recently played updates. Deduplicate repeated engine callbacks for one start; keep 50 distinct entries. Confirm initial-import timestamps survive rescans, reconnects, and identity-preserving moves.
- Confirm both recent lists offer no clearing, per-entry removal, or reorder actions. Library-record deletion removes associated entries; temporary disconnection does not. Apply existing built-in rename/delete restrictions and playlist naming validation.

### Genres-flow acceptance

- Case/outer-whitespace variants group together; blank tags use Unknown genre. Multi-genre tracks appear once per applicable genre, without guessing delimiters in a single genre name.
- Genre track counts retain disconnected entries and separate physical copies. Artist rows use track Artist and count only associated album titles containing that artist's matching tracks.
- All four list types sort alphabetically by their displayed name/title. Search stays within the relevant field and genre scope, and empty matches never expose unrelated entries.
- A 12-track album with 3 matching tracks qualifies, displays 12 tracks, and opens all 12. Nonmatching tracks do not gain genre membership merely through that album.
- Opening an artist retains genre filtering for Play all, Tracks, and qualifying Albums. Opening any whole album leaves that filter; its Artist shortcut is unfiltered Album Artist scope. Back restores the previous genre/artist context and list state.
- Genre Play all replaces the queue, disables shuffle, and returns immediately. Individual track selection preserves shuffle, snapshots displayed results, and uses the 15-second return. Empty/unplayable collections never start unrelated tracks.
- Compare the five panes with references 018–022; verify the equally spaced three-button Browse layout and existing pressed/unpressed assets, unchanged clock/transport access, retained selection, scrolling, and long-title behavior.

### Manage playlists popup acceptance

- Match references 023–026, including fixed panel size over the seek bar, empty-custom message/divider, shared scrolling, Favorites-specific checkbox color, top buttons, and both pressed backgrounds. Hidden controls cannot receive popup touches.
- Open for playing and paused selected tracks; disable with no selection. Verify existing memberships, single-toggle row/checkbox behavior, immediate persistence, unique membership, and unchanged audio files/active queue.
- Verify closed icon states for no membership, Favorites only, custom membership only, and removal of the last membership. Open/toggled state always wins while editing.
- Creation uses the same validation as the overview but adds the selected track and returns with its new playlist checked; cancellation creates nothing. Overview creation still creates an empty playlist.
- Automatic advancement and manual track changes close popup and nested creation prompt, discarding uncommitted drafts. Pause/resume, seeking to restart the same track, and volume changes do not close it or change the editing target.
- Close, toggle, and Back dismiss correctly; nested Back first cancels naming. Clock/blank touches leave it open, and Menu closes it before navigation.
- Keep popup/button fully opaque beyond the normal fade interval; dismissal restarts fading. Verify no inactivity-driven popup closure and no interference with the browsing-return timer.

### Folders-flow acceptance

- Virtual root contains only selected sources, with no subtitle or Previous directory row. Browsing stays within source grants; parent from a source root returns to virtual root.
- Directory contents show folders and supported audio filenames/extensions only, with the supplied icons. Empty folders remain navigable and display the agreed empty message when appropriate.
- Previous directory scrolls with rows and precedes naturally sorted folder/audio groups. Verify numeric names, duplicate filenames, and long labels.
- Normal selection queues only the current directory's audio, excluding descendant files; recursive search selection queues all matching audio results, never folder results. Both preserve shuffle and use the existing inactivity return.
- Search includes nested folder names/audio filenames, shows distinguishing relative paths, and respects selected sources. Back clears search first; opening a folder result exits search and parent navigation follows its actual parent.
- Both Back controls restore directory scroll state and return to Menu at virtual root. Folder navigation alone does not start playback or arm automatic return.
- Disconnected sources remain at root with a reconnect message. Removal while browsing returns to root and applies the existing active-playback pause rule without deleting library references.
- Compare root/subdirectory layouts to references 027 and 028; confirm persistent clock/transport controls and metadata on the player versus filenames in the browser.

### Splash/startup acceptance

- Compare the full-screen splash with reference 029 on small and wide landscape displays: warm background, proportionate logo, Inter Bold wordmark, no clock or player controls.
- Fresh manual and boot-triggered starts show branding without a forced minimum duration; readiness triggers one 300 ms exit fade and a single destination transition.
- First-time users route to onboarding, completion/skipping itself routes to a paused player; later launches follow the opted-in startup policy, and setup completion survives relaunch and recreation.
- Return from background, wake of an existing session, recomposition, and same-session activity recreation do not replay app-controlled branding. Active background playback is unchanged; fresh restored sessions stay paused unless the explicit opt-in eligibility policy permits one attempt.
- Slow scans, missing USB/SD storage, and artwork work do not hold the splash. Recoverable failures reach an actionable destination state.
- Validate API 23 and Android 12+ system-launch handoff for blank flashes, duplicate holds, aspect-ratio distortion, and logo masking. Distinguish the OS-provided launch surface from the custom splash in results.
- Preserve existing debug screen fixtures through splash bypass and add a dedicated splash fixture; verify production launch uses normal readiness routing.

### Drag-only slider acceptance

- Taps anywhere, stationary holds, below-threshold jitter, and vertical-only movement issue neither seeks nor volume changes. A drag beginning outside the thumb/endpoint target never activates adjustment by crossing it later.
- Start off-center within the generous target and confirm no initial coordinate jump. Valid horizontal movement changes the preview/value smoothly and clamps at both range endpoints.
- Seek drag updates visual progress/time only; release commits exactly one seek, and cancellation commits none. Track changes cancel stale previews. Playing/paused state remains unchanged and invalid/nonseekable tracks cannot scrub.
- Volume retains its thumb-free appearance, supports easy endpoint grabbing including 0%/100%, updates continuously only after drag recognition, and retains its last applied value on cancellation. Verify this on player and browsing screens.
- Additional fingers cannot retarget the gesture. Losing the initiating pointer cancels safely; touch activity still participates in normal inactivity handling.
- Accessibility progress adjustments, hardware volume keys, and +/-10-second controls remain available. Existing popup coverage still prevents touches reaching a hidden seek bar.

### Track Options acceptance

- Verify eligible track rows across browsers and search, including Folders audio rows. Long-press never starts playback; non-track rows keep their previous actions. Favorites/custom playlists retain their existing menus with Track options before removal.
- Match normal/pressed references 030–031, hide the underlying track list, and block hidden-row input while retaining the clock/volume/transport controls. Target metadata remains fixed as playback advances.
- Verify popup nesting, one-level Close/Back, ignored blank taps, restored source-list state, and cancellation of automatic return. Scheduling success returns directly to the source list with a confirmation toast.
- Membership edits and creation affect only the fixed target and persist without altering playback. Closing collections returns to Track Options. Playback advancement leaves this nested flow intact while the main-player collections entry still closes on track change.
- Play next moves the earliest upcoming occurrence or inserts when absent; repeated A then B requests yield B then A. Verify shuffle stays enabled and untouched queue order is preserved, including repeated track identities and targeting the current track.
- Paused scheduling stays paused; no-current-track scheduling creates a paused first occurrence. Neither operation restarts the current song or changes its position.
- Stop after this song attaches one marker to the scheduled occurrence, replaces only the previous marker, survives other Play next requests, stops at marked completion despite repeat, and retains remaining queue content. Verify paused restoration of scheduling state.
- Once Queue is implemented, Add to queue is enabled outside Queue and disabled within it; Remove from queue is enabled only within Queue. Until implementation both remain disabled. Track info opens its specified panel.

### Main player redesign acceptance

- Compare reference 033 for the two new buttons, endpoint-aligned Previous/Next, centered Play/Pause, and time labels above the seek bar. Verify consistent transport positions across screens and overlays without changing top controls or the clock split.
- Verify supplied Queues/shared info icons, Queues opening its screen once implemented (disabled until then), and disabled Track info with no selected track.
- Open Track info directly while playing and paused; preserve playback and queue, retain the captured target after Next, and return home via Close or Back. Verify the existing Track Options entry still returns to Track Options.
- Verify panel coverage blocks underlying new-button input, all designated controls, including the optional drawer button, share fading/restoration, and disabled controls remain disabled after touch restores opacity.
- Verify seek/time labels move only where already present, and transport actions plus drag-only seek/volume behavior are unchanged.

### Track info acceptance

- Match reference 032: fixed left/right split, fixed Close and heading, scrolling tag values, visible clock/volume/transport, hidden search icon, and no interaction with hidden track rows.
- Verify all ten fields appear in the specified order. Missing and blank tags show “Empty” without library fallbacks; a tagged `3/12` supplies both numbers, while a missing album total is never inferred from library size.
- Verify separate artists retain order/spelling when comma-joined, names containing `/` or `&` remain intact, full release dates display their year, and Comment/Description remain separate.
- Verify long multiline values remain fully readable through wrapping and scrolling.
- Open info for a track other than the playing track and advance playback: the inspected track remains fixed, with no unintended playback or queue changes.
- Verify Close/normal Back/system Back return to Track Options, long-press Back returns home, blank taps do nothing, and no automatic return interrupts reading or is re-armed on close.
- Disconnect a source: cached metadata remains readable with its saved-tags notice; without cached metadata, show an unavailable message instead of falsely empty tags.

### Queue screen acceptance

- Match reference 034 with shared controls, QUEUE title/icon, handles, subtitles, current highlight, and empty state. Show the full effective queue, including duplicates and earlier entries, and open at the current occurrence.
- Tap a different occurrence to play from its start without queue replacement; tap current to resume paused playback or leave active playback uninterrupted. Preserve shuffle/repeat.
- Drag handles reorder any occurrence without interrupting audio or resetting position; row long-press opens Track Options. Drop commits immediately, cancellation does not commit, and shuffled playback follows the displayed edited order.
- Advance playback during a drag and verify identity remains stable. Replace the queue/remove the target and verify safe cancellation without reverting unrelated changes.
- Add outside Queue appends a distinct occurrence, preserves playback/shuffle, selects paused when empty, and confirms by toast. Verify Add is disabled in Queue and Remove is disabled elsewhere.
- Remove one exact duplicate without confirmation or file/library changes; verify current-entry removal while playing and paused, skip failures, and no wrapping when no next playable entry remains.
- Verify exact-occurrence Play next/Stop actions, disabled Play next on current, current Stop without duplication, marker retention on drag, clearing on removal, repeat override at completion, and explicit Play continuing after the marked entry.
- Verify the 15-second timer starts on entry, resets on interaction, cannot interrupt dragging/popups, and restarts after returning to Queue. Check scroll restoration after actions and normal Back to player.
- Restart after queue edits and verify occurrence order and marker persist with playback restored paused by default; separately test qualifying opt-in autoplay.

### AVD acceptance

- Install APK and operate without network or Google Play Services on API 23 and a modern Android image.
- Exercise 100 mixed-format tracks, missing tags/artwork, corrupt files, nested folders, duplicate copies, and non-Latin metadata.
- Confirm scanning remains responsive and does not interrupt playback; record scan/startup timings without claiming a hardware performance guarantee.
- Validate background playback, activity recreation, process termination/relaunch, persisted position/queue/modes, and no unsolicited playback on boot.
- Confirm persistent clock and transport controls, readable fixed split, long-title handling, full-screen landscape, and configurable formats/backgrounds.
- Verify diagnostics remain local and exported reports omit paths by default.

### Later physical-head-unit validation

Actual USB/SD detach/reinsert, real sleep/ignition cycles, steering-wheel/media/volume integration, audio routing and call interruptions, boot behavior, OEM process management, and low-powered-device performance require hardware testing. Do not mark these as validated from AVD simulations alone.

## 8. Assumptions, exclusions, and open risks

### Explicit assumptions/defaults

- Track Options implementation defaults: use “Save to playlist” as shown in reference 030 for the add/remove action; toast wording may follow the examples above. If a marked occurrence is removed, skipped as unplayable, or its queue is replaced, clear its stale stop marker rather than attaching it to a different occurrence. The Queue screen specification above defines explicit Play after a consumed stop and exact-occurrence queue management.

- Albums implementation defaults to validate with real metadata: missing/invalid disc number is disc 1; missing track number displays `--`; unknown duration displays `--:--`; ties use title then stable track ID. For conflicting album years, display the most frequent known year (earliest on a tie). These are defaults, not separately confirmed interview decisions.
- Artist-list album counts retain the prior distinct-title rule; artist album rows use the new title + Album Artist identity to avoid merging unrelated albums. Revisit the count rule if same-title albums with different Album Artists make this distinction confusing.

- Clock defaults to 12-hour with a 24-hour switch; date is fixed DD-MM-YYYY. Additional date formats are deferred.
- Exact dark preset color values and missing-order tie-breakers will be fixed during design implementation.
- Crossfade, equalizer, and guaranteed gapless playback are deferred.
- No deadline and no numeric startup/scan-time guarantee have been agreed.
- Initial test library is 100 tracks; there is no artificial import limit. Genuine Shuffle now explicitly targets 100k+ tracks with responsive UI and low overhead; this is a requirement awaiting benchmarks, not an achieved performance claim.

### Excluded from v1

Streaming, cloud sync, accounts, monetization, online metadata/artwork, portrait layout, metadata editing, background images, and playlist/favorites import/export.

### Risks to resolve or document

- Exact library/toolchain compatibility and Android-version restrictions are not yet verified against current documentation.
- USB providers, persisted access, boot UI launch, steering-wheel controls, and sleep signals vary by device; some require device-specific configuration.
- Byte-identical copies and simultaneous moves can make original file identity impossible to infer unambiguously. Never silently transfer the wrong playlist/favorite references.
- Content reads can be expensive; tag edits change byte hashes and are not yet promised to preserve identity across simultaneous moves.
- Sudden power loss can lose recent unsaved state. The agreed position checkpoint target is five seconds plus transition saves, not a guaranteed recovery bound; storage failures or fallback snapshots can increase loss. Validate persistence on representative hardware.
- Physical screen readability, fonts/assets, codec variants, and performance need empirical validation.
- AVD-only delivery must clearly disclose the remaining physical-device validation gap.
