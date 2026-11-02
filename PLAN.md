# Clocarmus — v1 plan

Status: main player, menu, All Tracks, Artists, and the three-screen Albums demo flow are implemented on `headunit_a6`. Browsers use demo metadata with search, retained list state, selection, interactive fast scrolling, and the 15-second inactivity return after track selection. Artist Play all and Play album update the demo queue, disable shuffle, and return immediately. Shared album options support entry from Artists and an explicitly scoped Album Artist shortcut. Automatic marquees for overflowing music titles and metadata are implemented across the player and all eight track, artist, and album browsing panes. Real library data, audio playback, onboarding, other browsing screens, and settings remain planned.

## 1. Product and constraints

Build a personal-use, entirely offline music player for aftermarket Android car head units using Kotlin and Jetpack Compose. Deliver an installable APK, support Android 6.0 (API 23) and above, and require neither accounts, internet, cloud services, nor Google Play Services.

The first release is validated on an Android Virtual Device (AVD) with 100 tracks. Physical head-unit validation follows. Do not impose an artificial library import limit or claim large-library performance based on this initial test set. Prioritize reliable background playback, responsive discovery, and low/medium-powered devices. No deadline has been specified.

### Existing project baseline

The current project is a minimal Compose shell using package/application ID `com.example.clocarmus`. Preserve this identity. Repository configuration currently declares minimum SDK 23, compile/target SDK 35, Java compatibility 17, AGP 9.2.1, Kotlin Compose plugin 2.2.10, Compose BOM 2024.09.03, and Activity Compose 1.8.2. These are recorded configuration values, not a verified compatibility endorsement. Verify exact dependency versions and current official documentation before implementation; do not silently replace the user's stack or raise the minimum SDK.

## 2. Agreed interface and journeys

### Design

Use `design/01-main-player.png` as the primary reference, the remaining screen mockups for flows, and `design/design-system-ref.png` for styling. Final name: **Clocarmus**. Use the supplied logo assets and Inter typography; confirm font asset availability and licensing before bundling.

- Full-screen, landscape-only, fixed 50/50 left/right division across supported landscape sizes.
- Left side: album artwork, title, artist, album, year directly below album, playback controls, progress, volume, shuffle, repeat, and +/-10-second controls. Browsing/settings replace left-side content.
- Right side: persistent day, digital clock, and date during normal app use, including browsing/settings. Splash and onboarding follow their separate full-screen mockups.
- Retain previous, play/pause, and next controls across browsing/settings, as shown.
- Keep the screen awake while the app UI is visible, even when paused; do not hold the display awake from the background.
- Preset backgrounds only: black, dark gray, dark blue, dark burgundy. Preserve readable white controls/text. No image backgrounds.
- Configurable time and date formats. Default assumption: mockup-style 12-hour time and DD-MM-YYYY date.
- English-only interface; preserve music metadata in its original language.

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

### Main-player inactivity fading

- Only Menu, Add to playlist, Shuffle, Repeat, Skip forward 10 seconds, and Skip backward 10 seconds fade. All other elements retain their normal appearance.
- While the main player is active, 10 seconds without touch starts a 500 ms animation from 100% to 20% opacity, regardless of whether music is playing or paused.
- Apply opacity to each control's existing appearance, including inactive Shuffle/Repeat styling; preserve active/inactive distinctions, hit areas, and accessibility semantics.
- Touch anywhere on the player, including the clock pane, immediately restores all six controls to full opacity. The same touch still performs its normal button or slider action; do not consume it merely to restore visibility.
- Keep controls fully visible while any finger remains down. Start a fresh 15-second countdown when the last finger lifts, including after holds, drags, or multi-touch gestures.
- Hardware/media buttons, physical volume keys, clock updates, playback progress, and track changes do not reset the countdown.
- Suspend and reset fading while another screen or dialog is active or the app is backgrounded. Returning to the active player, including dismissing a dialog, restores full opacity and starts a fresh interval.
- Keep this timer independent of the browsing-to-player return timer below. Its state is transient and is not persisted across screen recreation.
- Keep static reference-preview mode fully opaque for deterministic mockup comparisons.

### First use and navigation

1. Show branding and welcome flow, explain storage access, and let users select music folders.
2. Allow setup to be skipped into an empty player/clock with an Add music folder action.
3. Remember selected folders and expose source management and manual rescan in Settings.
4. Browse All Tracks, Artists, Albums, Genres, Folders, and Playlists. Search songs, albums, and artists.
5. Selecting a song starts its collection in displayed order; search queues all matching songs. Folder playback includes descendants.
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

### Sorting and metadata

- All Tracks and song search results: alphabetical by title.
- Album lists: alphabetical by title. Tracks within albums: disc/track number, with unnumbered tracks after numbered tracks within each disc, alphabetically by title.
- Folders: filename order, including a deterministic ordering across descendant folders.
- Playlists: user-controlled order.
- Missing title: filename. Missing artist/album: Unknown artist/Unknown album. Missing year: Unknown year; always retain the year row directly below Album on the main player. Missing artwork: local placeholder.
- No online metadata/artwork lookups or metadata editor.

## 3. Library, playlists, and persistence

### Sources and discovery

Support music from internal storage and accessible USB/SD storage. Users select folders once; scan recursively, refresh when reconnection is detected, and provide manual rescan. Keep scanning off the UI thread and show progress without preventing playback.

Preserve disconnected entries as unavailable, including favorites and playlist membership. Reconnection never starts playback. Explicit source removal requires confirmation and removes affected library and playlist entries without deleting files. Tracks still covered by another selected source must remain available; overlapping folder selection must not import the same location twice.

Recognize renamed/moved files using background content inspection, preserving their app identity and references. Identical copies at distinct locations remain distinct entries. Never merge or arbitrarily reassign ambiguous duplicate copies: unique matching rules and ambiguous-case handling need validation in the discovery phase. Cache identity work rather than rereading every unchanged file on every launch. An incomplete/failed scan must not be interpreted as successful deletion of all missing files.

### Playlists and recent lists

- Create, rename, delete playlists; add/remove tracks and manually reorder entries.
- Disallow duplicate track membership within one playlist.
- Add-to-playlist checkboxes immediately update membership; no Save button. The top-right toggle returns to artwork.
- Favorites are a built-in collection.
- Recently added: 100 most recently imported tracks, newest first; reconnecting unchanged files does not reset import time.
- Recently played: 100 most recently played distinct tracks, newest first.
- No local playlist/favorites export/import in v1.

### Logical data model

Keep all data on the device:

- Selected source: stable ID, granted folder reference, display name, access/scan status.
- Track: stable app ID, source/location reference, availability, metadata, duration, artwork reference, file attributes/content identity, first-import timestamp.
- Playlist and ordered playlist membership, with unique track membership per playlist.
- Favorites and distinct-track recent playback timestamps.
- Playback snapshot: queue, selected track/index, position, shuffle order, repeat mode.
- Preferences: onboarding state, appearance, clock/date formats, source configuration.
- Bounded local diagnostic events.

A location identifies a physical copy; a content fingerprint is evidence for relocation, not a global uniqueness constraint. Use transactional persistence for playlist changes and identity reconciliation. Deleting a playlist never deletes audio files.

## 4. Playback and platform behavior

Validate MP3, M4A containing AAC, OPUS, FLAC, OGG Vorbis, and WAV using representative fixtures. Do not promise every codec profile or container variant merely because an extension is recognized.

- Playback runs independently of UI navigation and continues in the background.
- Support seek bar, +/-10 seconds, shuffle, repeat one, repeat all, and repeat off.
- Previous restarts the current track after three seconds of playback; otherwise selects the previous track.
- Volume slider controls Android media volume. Integrate standard media-session controls for supported steering-wheel/media buttons.
- Persist track, position, entire queue, shuffle order, and repeat mode. Restore paused after fresh app restart, boot, or head-unit sleep/wake.
- Returning to a still-running background session leaves playback unchanged. Do not equate ordinary UI backgrounding with head-unit sleep.
- Navigation prompts temporarily lower volume. Calls and another music app taking over pause playback; remain paused until explicit Play.
- Skip unavailable/unplayable queued entries with a brief message; stop when no playable entries remain. Avoid infinite skipping under repeat.
- Removing the drive containing the actively playing track pauses instead of automatically advancing. Preserve its restoration state.
- Boot UI launch is best-effort and never starts playback. Manual launch is acceptable on unsupported devices.

## 5. Proposed on-device architecture

These are implementation directions, not approved exact dependency versions:

- Compose screens and state holders render repository/player state and send user actions through explicit interfaces.
- A library repository owns local database access, source membership, metadata, search, playlists, and recent lists.
- A storage-access layer encapsulates Android-granted folder references, persisted access, traversal, and access recovery.
- A scanner performs cancellable incremental metadata/content processing and transactional reconciliation.
- A playback service owns the player and Android media session; UI controls connect to that session rather than create their own players.
- A playback-state store saves snapshots periodically and on meaningful transitions, and restores paused state independently of screen creation.
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

- Implement design tokens, supplied branding, responsive fixed-split shell, clock/date formatting, background presets, and persistent controls.
- Implement skippable onboarding, source management, empty/access-denied states, and browsing navigation.
- Implement main-player inactivity fading with shared transient opacity/timer state and non-consuming touch observation across both panes. Apply opacity only to the six designated controls; cancel pending countdowns/animations when inactive and interrupt fading immediately on touch.
- Implemented shared overflow-only marquees for music titles, row metadata, and artist/album context headings across the nine existing player/browser screens; retain this behavior when connecting real library data. See Long music titles above.
- Validate readability on small/wide landscape emulators without changing the 50/50 split.

### Phase 3 — Local library

- Implement database/preferences, selected sources, recursive discovery, metadata/artwork caching, incremental refresh, and unavailable states.
- Implement renamed/moved-file reconciliation and overlapping-source handling.
- Add browsing, search, sorting, playlists, favorites, recent lists, and immediate membership updates.
- Add the four-screen Artists flow with Artist-tag grouping, distinct album counts, artist-scoped tracks/albums and search, retained list state, hierarchical Back, and navigation to shared album options. Reuse the existing browser components with stable track identity rather than title-only selection when connecting real library data.

- Add the Albums list, options, and numbered/duration track browser using the renamed references and new normal/pressed Album Tracks assets. Extend library metadata with Album Artist, disc/track numbers, and duration; keep stable album identities and explicit artist-scope navigation.

### Phase 4 — Playback integration and lifecycle

- Connect queue construction, service/session controls, seeking, repeat/shuffle, volume, and progress to the UI.
- Connect artist Play all to queue replacement, alphabetical track ordering, shuffle disablement, and immediate player return; preserve the existing delayed return for individual artist-track selection.
- Add snapshots and paused restoration, source-loss/error handling, audio interruption behavior, and supported media buttons.
- Inactivity-return timer implemented for the browsing shell: arm after selection, reset on interaction, cancel on search typing or Settings/playlist editing entry. Complete service integration, screen-awake policy, best-effort boot entry, and distinguish sleep from ordinary background playback where platform signals allow.

- Connect Play album to whole-album queue replacement, shuffle disablement, disc/track ordering, and immediate player return; integrate filtered individual selection with the existing delayed return.

### Phase 5 — Diagnostics and delivery

- Add bounded local event capture, diagnostic screen, redacted manual report export, and actionable storage/playback errors.
- Run acceptance checks, document unresolved device-specific behavior, and package an installable APK.
- Document reproducible build/install steps and signing-key handling for subsequent updates. Do not commit signing secrets.

## 7. Test and acceptance criteria

### Automated behavior checks

- Collection/search/folder queue creation and specified ordering, including recursive folders.
- Playlist uniqueness, ordering, rename/delete, favorites, and distinct recent-list bounds.
- Metadata fallbacks and search across songs/artists/albums.
- Source overlap, disconnect/reconnect, permission loss, interrupted scan, source removal, and rename/move reconciliation with duplicate ambiguity.
- Shuffle/repeat restoration, previous-track threshold, seeking bounds, empty queue, and finite skipping of unplayable tracks.
- Snapshot recovery and paused startup; distinguish UI reconnection from a fresh player session.
- Browsing-return timer starts only after selection, resets on interaction, cancels in editing/search/settings, and cannot navigate away after cancellation.
- Main-player fading: full opacity before 10 seconds, animation begins at 10 seconds, and 20% opacity is reached after another 500 ms in both playing and paused states.
- Touch on either pane immediately restores opacity without suppressing the same touch's action; holds, drags, and multiple fingers prevent fading until the final release.
- Verify all six controls fade, including inactive Shuffle/Repeat appearance, while all unlisted elements retain normal opacity.
- Hardware events, clock ticks, playback progress, and track changes do not reset the fading timer; dialog dismissal, navigation back, foreground return, and recreation start fresh without stale countdowns or animations.
- Verify fading and browsing-return timers remain independent and static reference previews remain fully opaque.

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

- Albums implementation defaults to validate with real metadata: missing/invalid disc number is disc 1; missing track number displays `--`; unknown duration displays `--:--`; ties use title then stable track ID. For conflicting album years, display the most frequent known year (earliest on a tie). These are defaults, not separately confirmed interview decisions.
- Artist-list album counts retain the prior distinct-title rule; artist album rows use the new title + Album Artist identity to avoid merging unrelated albums. Revisit the count rule if same-title albums with different Album Artists make this distinction confusing.

- Initial clock/date defaults: 12-hour time and DD-MM-YYYY; configurable options remain to be enumerated.
- Exact dark preset color values and missing-order tie-breakers will be fixed during design implementation.
- Crossfade, equalizer, and guaranteed gapless playback are deferred.
- No deadline and no numeric startup/scan-time guarantee have been agreed.
- Initial test library is 100 tracks; there is no artificial import limit or larger-library performance commitment.

### Excluded from v1

Streaming, cloud sync, accounts, monetization, online metadata/artwork, portrait layout, metadata editing, background images, and playlist/favorites import/export.

### Risks to resolve or document

- Exact library/toolchain compatibility and Android-version restrictions are not yet verified against current documentation.
- USB providers, persisted access, boot UI launch, steering-wheel controls, and sleep signals vary by device; some require device-specific configuration.
- Byte-identical copies and simultaneous moves can make original file identity impossible to infer unambiguously. Never silently transfer the wrong playlist/favorite references.
- Content reads can be expensive; tag edits change byte hashes and are not yet promised to preserve identity across simultaneous moves.
- Sudden power loss can lose the most recent unsaved position; select and document a checkpoint interval during implementation.
- Physical screen readability, fonts/assets, codec variants, and performance need empirical validation.
- AVD-only delivery must clearly disclose the remaining physical-device validation gap.
