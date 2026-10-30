# Clocarmus — v1 plan

Status: main player, menu, and All Tracks visual shells implemented on `headunit_a6`. All Tracks uses 28 demo titles with search, selection, interactive fast scrolling, and the 15-second inactivity return after selection. Real library data, playback, onboarding, other browsing screens, and settings remain planned.

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

### Sorting and metadata

- All Tracks and song search results: alphabetical by title.
- Albums: disc/track number; define a deterministic fallback for missing track numbers during implementation.
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
- Validate readability on small/wide landscape emulators without changing the 50/50 split.

### Phase 3 — Local library

- Implement database/preferences, selected sources, recursive discovery, metadata/artwork caching, incremental refresh, and unavailable states.
- Implement renamed/moved-file reconciliation and overlapping-source handling.
- Add browsing, search, sorting, playlists, favorites, recent lists, and immediate membership updates.

### Phase 4 — Playback integration and lifecycle

- Connect queue construction, service/session controls, seeking, repeat/shuffle, volume, and progress to the UI.
- Add snapshots and paused restoration, source-loss/error handling, audio interruption behavior, and supported media buttons.
- Inactivity-return timer implemented for the browsing shell: arm after selection, reset on interaction, cancel on search typing or Settings/playlist editing entry. Complete service integration, screen-awake policy, best-effort boot entry, and distinguish sleep from ordinary background playback where platform signals allow.

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
