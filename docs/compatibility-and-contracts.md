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

This is the first library implementation, not the end of Phase 3. Audio service/decoding playback, audio focus and actual playback-start history remain the next playback stage. Normal-mode progress is no longer advanced by the demo timer, and shell Play does not add real Recently played entries.

Platform metadata currently provides title, artist, album, album artist, release year, disc/track number, duration, genre, composer and embedded artwork. Track info keeps raw fields separate from display fallbacks. Comment, Description, Copyright, Publisher and an independently tagged album total are not fully parsed; unsupported fields say “Not read”, rather than claiming the file tag is empty. Full release dates and format-specific/multiple-artist tag extraction remain work. Container creation timestamps are never substituted for release years. Album totals are never inferred from container stream count or library membership.

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
