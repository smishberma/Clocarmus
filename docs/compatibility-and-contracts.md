# Compatibility and library contracts

Implementation started 2026-11-12. This records the current local-library implementation; it is not a declaration that every Phase 1 feasibility check or Phase 3 requirement is complete.

## Stack

Application identity remains `com.example.clocarmus`, minimum SDK 23, compile/target SDK 35, Java source/target 17, AGP 9.2.1, Kotlin Compose plugin 2.2.10, Compose BOM 2024.09.03, Activity Compose 1.8.2. No playback or database dependency was added. SQLite, document traversal, metadata extraction and image decoding use Android platform APIs. The existing coroutine dependency supplies IO scheduling and cancellation.

Instrumentation uses the platform JUnit 3 runner and optional platform test compile libraries; the test APK explicitly declares `android.test.runner`. Tests inject the document boundary but use real SQLite in an isolated cache database. A separate test reads a real generated short PCM WAV through `MediaMetadataRetriever`.

## Library ownership

`ClocarmusApplication.library` owns a process-scoped `LocalLibrary`. Its state flow publishes immutable, versioned snapshots. Database access, recursive provider queries, hashing, metadata extraction and artwork decoding run on IO. Startup readiness does not wait for scanning. Normal browsers consume repository snapshots; reference previews keep deterministic fixtures.

`local-library.db` schema 1 stores unique physical locations, stable app IDs, cached records, directory listings and playlist state. Record/membership changes share a SQLite transaction. Payloads currently use Java serialization inside the private database: model changes require an explicit schema migration and backwards-compatible decoding. Do not silently rebuild this database when changing its payload classes. Demo playlist names migrate once; sample track memberships and queues are not treated as imported music.

Only selected SAF trees are traversed. Location identity is provider authority plus the opaque document ID; tree-qualified URIs remain the access handles. This deduplicates overlapping grants from the same provider. Providers exposing the same physical file through unrelated authorities/IDs cannot be confidently deduplicated by this rule. Content equality alone never merges physical copies.

The scanner caches size/last-modified/fingerprint evidence. Providers with absent attributes are reread. Same-size edits that preserve a provider's reported timestamp can escape this cache; manual rescan currently follows the same incremental policy. Provider change notifications, storage broadcasts and foreground returns request refresh. Requests are coalesced, with changes arriving during a pass retained for a follow-up. Notifications are provider-dependent, so manual source/all-source refresh remains available.

## Absence, identity and reset

Completed imports survive cancellation. Only a successfully completed traversal of every known covering source can confirm absence. Incomplete directory results, permission loss and disconnection are not deletion evidence. Known unreadable audio keeps cached tags and its identity. Cleanup previews only confirmed-missing entries and rechecks provider absence at confirmation. Source removal/exclusions delete records and memberships only when no retained indexed source covers them; files are never modified.

Full-file SHA-256 provides relocation evidence. A move inherits an old identity only if the old location is confirmed missing and exactly one old record and one newly discovered location share the fingerprint. Ambiguous copies import separately. Potential relocations wait until reconciliation; other first imports publish progressively. Moving and editing together can prevent matching.

Source configuration changes invalidate outstanding scanner writes. The existing pending-reset journal also resets the database, increments the scan generation, cancels in-flight work and clears directories/memberships before startup restoration. Temporary unavailability pauses shell transport without requesting resume. Confirmed removal of the selected record clears the shell selection.

## Current limits and remaining implementation

This is the first library implementation, not the end of Phase 3. Initial service-backed decoding, audio focus, and actual playback-start history are now connected as described below. The demo timer is restricted to reference fixtures.

Platform metadata currently provides title, artist, album, album artist, release year, disc/track number, duration, genre, composer and embedded artwork. Track info keeps raw fields separate from display fallbacks. Comment, Description, Copyright, Publisher and an independently tagged album total are not fully parsed; unsupported fields say “Not read”, rather than claiming the file tag is empty. Broader format-specific/multiple-artist tag extraction remains work. Container creation timestamps are never substituted for release years. Album totals are never inferred from container stream count or library membership.

The initial implementation materializes library records and browser collections in memory, publishes after each import, and retains full playlist payloads. Paging, bounded batches, query/search indexes, artwork garbage collection and large-library benchmarks remain necessary before claiming scale. Filesystem/provider calls are cooperatively cancellable between operations; a provider blocked inside a synchronous call may delay cancellation. A source replacement and overlapping-grant configuration change before the alternate source has ever been scanned still needs broader reconciliation validation.

Minimum-API automated tests and sample-file UI checks do not establish modern-Android, removable-drive or physical-head-unit compatibility. The planned 100-track mixed-format acceptance run is still required.

## Official API contracts consulted

- [DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract): tree-scoped document URIs, child queries and loading results.
- [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever): available metadata keys and embedded artwork. `NUM_TRACKS` describes container tracks, not album track totals.
- [SQLiteOpenHelper](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper): lazy database opening and versioned upgrade ownership.
- [Android local tests](https://developer.android.com/training/testing/local-tests): framework APIs in host unit tests are stubs; SQLite/provider behavior needs device tests or a controlled boundary.

## Validation on 2026-11-12

- `:app:assembleDebug`, all 121 host unit tests and `:app:lintDebug` pass; lint reports 0 errors and 44 warnings (including existing dependency/API recommendations and platform-versus-KTX style suggestions).
- Ten instrumentation tests pass on API 23 (`headunit_a6`): real SQLite persistence; unchanged-file metadata caching; unique rename identity and Favorites retention; interrupted scans; cancellation; dirty follow-up passes; overlapping sources; source-scoped exclusion; empty directory retention; cleanup rechecking a returned file; short untagged WAV extraction; release-year versus container-creation-date extraction; and reset invalidation with retained empty custom playlists.
- Gradle's offline connected-test task could not resolve an uncached UTP host plugin. The same built test APK was installed and run successfully with `adb shell am instrument -w com.example.clocarmus.test/android.test.InstrumentationTestRunner`.
- The real Android folder picker granted a nested test source containing FLAC, M4A and Opus copies from the local sample directory. All three imported with real titles, artists, albums and durations; artwork displayed on the player. Process restart retained the library and restored selection paused. A subsequent incremental scan reported three indexed tracks, zero additions/updates/missing records/failures. The scan dialog regression found in this pass was fixed and rechecked on the final APK.
- Local captures, XML dumps, database snapshot, instrumentation output and interaction helper are under `build/visual-comparison/local-library/` (ignored build artifacts). Emulator music fixtures were separate copies and have been removed. The original preferences were restored and checked byte for byte; temporary database fixtures were removed. Original repository music files are untouched.

The 100-track acceptance corpus, modern Android runtime checks, physical USB/SD permission recovery, exhaustive tag parsing and scalability work remain pending.

The synthetic `app/src/androidTest/assets/release-year.m4a` fixture is 100 ms of generated silence with a 1985 release tag and a 2026 container creation timestamp. It catches accidental use of `METADATA_KEY_DATE` as an album release date. It was generated with `ffmpeg -f lavfi -i anullsrc=r=8000:cl=mono -t 0.1 -c:a aac -metadata date=1985 -metadata creation_time=2026-11-12T00:00:00Z release-year.m4a`.


## Real playback and lifecycle — initial implementation

Implementation started 2026-11-12. This is the first real-audio slice of Phase 4; the stage remains in progress.

`ClocarmusApplication.playback` owns one process session. Normal UI transport/queue state delegates to that controller; activity navigation/recreation and returning to the UI do not restore or replace a live session. Reference previews do not initialize playback. Existing real-library demo-session snapshots migrate once when no new checkpoint exists; fixture queues are excluded.

The non-exported `PlaybackService` hosts a platform `MediaSession` and MediaStyle notification. Explicit playback promotes it to a `mediaPlayback` foreground service before requesting focus. It is not stopped by task removal. Pausing relinquishes foreground status and focus; the paused session can reconnect while its process survives. Process death is not service survival: `START_NOT_STICKY` avoids synthesizing a Play command, and a new controller restores paused. The service and UI use the same controller for transport. No dependency, SDK, application identity, or library-database schema change was needed.

MediaPlayer opens indexed SAF content through a descriptor, with provider opening on IO and asynchronous preparation. Progress comes from the decoder, not the demo clock. Seeks commit through the existing drag UI, keep paused/playing intent, and suppress stale progress while awaiting seek completion. Previous uses the three-second threshold; transport wraps only with Repeat All. Natural completion, a consumed scheduled stop, and paused seek-to-end retain distinct continuation behavior. Repeat and stop scheduling use exact occurrence IDs. Decoder failures traverse later available occurrences once without repeat-driven retry loops. A changed/unavailable active source pauses and never resumes on reconnection. Recently played updates after decoder start, not queue selection, restoration, ordinary pause/resume, or failed preparation.

Focus loss pauses and requires explicit Play. Navigation ducking attenuates the decoder, leaving Android's media-volume setting intact. Focus gain only clears attenuation. Focus callbacks carry a generation guard so late callbacks cannot resume a paused or denied request. `ACTION_AUDIO_BECOMING_NOISY` pauses the same session. Backgrounding and display-off do not imply sleep. The existing Activity window flag keeps the display awake only while its UI is visible.

`files/playback-v1.json` stores a versioned, validated queue/cursor snapshot through Android AtomicFile. Queue occurrences, current entry, position, repeat/shuffle, scheduled requests, stop marker, consumed-stop continuation and natural completion survive restoration. Checkpoints run on IO after transitions and on a five-second target while playing, through a serial conflated writer with capped retry backoff. Failed writes preserve the previous committed file and display one storage warning per controller lifetime. Interrupted replacements recover through AtomicFile's backup. A corrupt/unrecognized snapshot yields an explained empty paused player; it does not guess a queue. Remove all libraries also clears the new durable checkpoint under the existing reset journal.

### Remaining playback work

- Genuine Shuffle still uses the existing demo ordering algorithm. Collection provenance, consumption/history, uniform/fresh sampling, source refresh at shuffled Repeat All, and cancellable large-collection preparation remain unimplemented.
- Checkpoints currently rewrite the full queue. Separate version-linked structural/cursor saves, an additional validated recovery generation for damaged committed files, storage-failure injection, and 100k-entry performance measurements remain required. AtomicFile interruption tests do not establish sudden-power-loss durability.
- The saved autoplay setting does not yet request real startup playback. Durable previously-active eligibility, qualifying one-shot startup attempts and device-specific sleep/wake integration remain pending. Boot/Home navigation does not start audio.
- Broader call/output overlap, navigation ducking, denied focus, Bluetooth/USB/SD disconnection, display-off/task-dismissal survival, and notification/hardware-control behavior need modern-Android and physical-device acceptance. No OEM sleep signal is assumed.

### Playback validation

Debug/test APK builds, all 121 host unit tests, lint (0 errors, 45 warnings), and all 20 API 23 instrumentation tests pass (ten existing library tests plus ten playback tests). The playback suite uses an isolated library/database/session and generated eight-second PCM audio. It checks real decoder progress, pause/seek/resume, uninterrupted queue reorder, no extra Recently played entry on ordinary resume, scheduled-stop continuation, paused end-seek restoration, final completion/restart, Previous/repeat boundary behavior, bounded failure with Repeat One, competing focus with no gain-driven resume, corrupt-snapshot recovery, reset save-generation invalidation, and atomic interrupted-write recovery. The actual service also passed an API 23 startup smoke check: its MediaSession and three-action MediaStyle notification register paused. Full audible service/UI lifecycle acceptance is still pending.

Official contracts consulted:

- [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer): asynchronous preparation, decoder seeking/completion and resource ownership.
- [Audio focus](https://developer.android.com/media/optimize/audio-focus): API 26 focus requests and target-35 foreground-service eligibility.
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): mediaPlayback type/permissions and target-35 boot restrictions.
- [Notification permission exemptions](https://developer.android.com/develop/ui/compose/notifications/notification-permission): media-session notifications are exempt; the targeted lint suppression applies only to this MediaStyle notification.


## Release-year display update (2026-11-12)

Track years prefer a nonempty `year` value and fall back to `date`; full dates such as `2004-06-15` display as `2004`. Android exposes MP4/M4A release-date metadata through its YEAR key; its separate DATE key represents container creation and is not used as a release-year fallback for those containers. Raw release-date text remains available to Track info. The main player omits the year label when no year can be read, including the empty player; other browser fallback labels remain unchanged.

Metadata reader version 2 refreshes existing cached records during scanning while retaining track identities, import timestamps and playlist memberships. Interrupted or unavailable-source refreshes remain pending until a later scan. The serialized record/database schema is unchanged.

Validation: debug and instrumented-test APK builds, all 125 host tests, all 12 library instrumentation tests on API 23, and lint pass. Generated silent FLAC/M4A fixtures cover a full `date` value and a creation timestamp without release tags; the existing release-year fixture continues to verify release-date precedence over creation time.


## Track info file type (2026-11-12)

Track info displays File type above Artist(s), using the cached provider filename's extension in uppercase (for example FLAC, MP3, OPUS, WAV, OGG, and M4A (AAC)). It follows the fixed Track info target and remains available for disconnected sources. Existing imports need no rescan or database migration. Missing extensions use the panel's existing Empty value.
