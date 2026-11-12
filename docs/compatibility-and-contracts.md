# Compatibility checks and core contracts

Implementation started 2026-11-12. This stage establishes dependency/build compatibility and
production-facing contracts. The existing screens still run the demo; a scanner, Room database,
Media3 service, UI adapters, and production shuffle engine are subsequent integrations.

## Dependency decisions

| Component | Pinned version / decision | Evidence |
| --- | --- | --- |
| AGP / Gradle | Retain 9.2.1 / 9.4.1 | [AGP 9.2 requirements](https://developer.android.com/build/releases/agp-9-2-0-release-notes) specify Gradle 9.4.1 and minimum JDK 17. |
| Kotlin / Compose compiler | Retain 2.2.10 / 2.2.10 | AGP's built-in Kotlin baseline is 2.2.10; do not add the legacy Android Kotlin plugin. |
| Compose BOM / Activity | Retain 2024.09.03 / 1.8.2 | Validate the resolved graph with AAR metadata, manifest merging, compilation and lint. |
| Media3 ExoPlayer / Session | 1.9.4 | [Official release notes](https://developer.android.com/jetpack/androidx/releases/media3) list this stable release; downloaded common AAR declares minimum API 23 and minimum compile SDK 35. This is a compatible selection, not a claim it is the newest release. |
| Room runtime | 2.8.5 | [Official Room releases](https://developer.android.com/jetpack/androidx/releases/room) list 2.8.5; runtime-android AAR declares minimum API 23, minimum compile SDK 34 and minimum AGP 8.1.1. |
| Coroutines Android / test | 1.8.1 | Align explicit runtime and test versions with Room's resolved core dependency instead of retaining a lower 1.7.3 declaration. |

Room's KSP compiler, schema-export setup and migration tests belong with the first database
implementation. No database annotations or generated database are introduced in this stage.
Media3's network-observation permission is removed during manifest merging; playback sources
will be granted local documents. The compatibility task rejects Internet/network permissions.
No service is registered until it has an actual lifecycle implementation.

Minimum SDK remains 23, compile/target SDK remain 35, and Java source/target remain 17.
Local execution uses OpenJDK 25.0.4.1; this does not change the application's Java target.

## Contract boundaries

The new `core/library` and `core/playback` packages have no dependency on Compose, activities,
Android URI objects, demo records or Java serialization. Suspending operations perform IO on
workers; interfaces do not imply permission to access ExoPlayer from arbitrary threads.

- Library IDs are positive numeric values; provider document identity is authority plus opaque
  document ID, independent of the tree URI granting access. Exclusions are source-specific.
  Never compare URI/document-ID prefixes to decide ancestry. Storage adapters must resolve
  ancestry through provider operations and deduplicate covered document identities. Providers
  exposing the same physical file under unrelated identities remain a reconciliation limitation.
- Raw metadata preserves absent tags separately from presentation fallbacks, and cached absence
  separately from unavailable metadata. Availability distinguishes disconnected media, revoked
  permission, confirmed missing files and read failures.
- Collection keys identify semantic scope and captured search query; revisions separate
  membership, availability and order from metadata updates. Compact defensive-copy snapshots
  preserve original order. Only manual collections admit duplicate track identities.
- Effective queues use independent numeric occurrence IDs, so two copies of the same track in
  a queue can be targeted exactly. Queue provenance retains the original collection snapshot,
  including manual multiplicity and the deleted-playlist fallback. No metadata object or player
  item is allocated per queue entry by these contracts.
- Cursor checkpoints link to one queue version. Unknown occurrence IDs, dangling stop markers,
  or incompatible versions cannot form a restored session. Large queue/progress changes have
  an atomic commit boundary; periodic position saves use a separate small-checkpoint API.
- Continuation distinguishes ordinary pause, natural queue end, consumed scheduled stop and
  paused seek-to-end. Restoration exposes paused playback while retaining prior playback
  evidence for the separate opt-in startup policy; a live service reattachment skips restoration.
- Shuffle preparation tokens include queue/scope revisions, user intent and the last audible
  track. Activation must compare the token again on the owner thread. Generated history,
  consumption and deliberate replay are persisted separately from position checkpoints.
  These contracts do not implement or prove the required shuffle distribution/performance.

`matchUniqueRelocations` accepts complete reconciliation evidence, using full-file SHA-256 and
byte count. Only a single confirmed-missing old copy and a single new location can match.
Multiple known/discovered copies, disconnected or permission-blocked records, different hashes,
same-location records, and destinations already owned by another record do not transfer identity.
Fingerprint collection and same-location tag updates remain scanner work. Simultaneous moves
and tag edits may fail to match safely. A fingerprint alone is not proof that two physical copies
are the same file.

`TraversalEvidence.canConfirmAbsence` permits missing-file reconciliation only after successful
traversal of the relevant accessible scope. Cancellation/failure retains completed imports but
cannot establish absence in unvisited scopes. The future scanner must enforce this evidence
when committing database changes, coalesce scans and retain dirty-after-visit work.

## Platform constraints for integration

- Use [MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
  to own playback independently of UI, with `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `foregroundServiceType="mediaPlayback"` when the
  service is implemented. UI connects to the same session. Test notification/media controls,
  recent-task dismissal and service lifetime on both minimum and current Android.
- ExoPlayer operations must obey its [single application-thread contract](https://developer.android.com/media/media3/exoplayer/hello-world).
  Queue generation, hashing and database serialization remain worker tasks.
- Use read-only [persisted SAF tree grants](https://developer.android.com/training/data-storage/shared/documents-files).
  Android 11 restricts selectable roots and Android/data/obb access. Persisted grants can still
  become invalid after moving/deleting documents; permission recovery does not imply Play.
- With target 35, [audio focus](https://developer.android.com/media/optimize/audio-focus) requires
  a top app or foreground service. Navigation ducks temporarily; calls/focus takeover pause
  until explicit Play. Do not rely on automatic focus-gain resumption or delayed Play requests.
- Android 15 [prohibits BOOT_COMPLETED from starting a mediaPlayback foreground service](https://developer.android.com/about/versions/15/behavior-changes-15#media-playback-fgs-boot-completed).
  The existing receiver attempts opted-in UI opening only. Background activity restrictions and
  OEM behavior make opening best effort; becoming the selected Home app is a separate workflow.
- Display-off and activity backgrounding are not reliable head-unit sleep signals. Real
  ignition/sleep, steering-wheel integration, media detach/reinsert and OEM process behavior
  require physical hardware checks.

## Reproducible checks and remaining gates

```sh
./gradlew :app:verifyDebugCompatibility :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

`verifyDebugCompatibility` runs Android's AAR requirement check and inspects the resolved merged
manifest for minimum/target SDK, application identity and network permissions. Manifest merging
also rejects dependencies whose minimum SDK exceeds 23. It checks compile SDK and Java targets.
Unit tests cover safe relocation, incomplete traversal, collection multiplicity, snapshot buffer
ownership, exact occurrence identity, coherent restoration, consumed stops and repeat order.

Available running AVD: `emulator-5554`, API 23, 1280×720, density 160. Installed image inventory
also includes API 29 and 31; neither establishes API 35 foreground-service/focus compliance.
Validation on 2026-11-12: compatibility task, debug assembly, all 135 unit tests (23 new core
tests), and lint passed. Lint reports zero errors and 41 warnings, none in the new core files.
The APK installed over the existing app and MainActivity launched successfully on API 23;
the activity resumed and the checked AndroidRuntime log contained no crash. This is a launch
smoke check, not a codec/service/storage validation.

Build compatibility is distinct from device behavior. Pending integration acceptance remains:

- API 23 and Android 15+ SAF grant persistence/recovery and scoped traversal.
- MP3, AAC/M4A, Opus, FLAC, Vorbis/OGG and WAV decoding/seeking with representative fixtures.
- Real background playback, interruptions, bounded failed-track recovery and coherent durable
  restoration under process death/storage failure.
- Exact uniform shuffle feasibility, duplicate-aware scoring, Android entropy-provider strategy,
  cancellation and 100k+ track benchmarks. No large-library performance claim is established.
