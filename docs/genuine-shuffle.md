# Genuine Shuffle

Implementation started 2026-11-12. Normal playback now uses collection-aware shuffle,
independent consumption/replay state, disk history, background ordering, and linked
structural/cursor persistence. Reference previews retain their demo ordering.
The functionality contract remains in PLAN.md. General mixed-duplicate scalability
and physical-device acceptance remain open; see the limits below.

## Collections and session state

Browser selection captures a semantic collection descriptor and its displayed
snapshot. Descriptors cover All Tracks, track/album artist (including genre scope),
album identity, genre, playlist ID, folder, and search scope/query. Query casing
normalizes collection identity; changing browser UI state does not retarget playback.
Collection identity is independent of membership and generated order.

At shuffled Repeat All, the controller resolves current membership from the cached
library snapshot on a worker. No filesystem scan or per-candidate database query is
performed. New tracks wait for that boundary. Temporary edits to an external queue
do not enter its next pass. An emptied existing playlist stops; a deleted playlist
uses the last activated collection snapshot, filtered against current library
membership/availability. Empty next collections produce an explained empty stopped
queue. No scope expands to unrelated music.

Manual collections have a persistent identity, maintained contents, original order,
and duplicate occurrence counts. Original entries can include temporarily
unavailable members absent from the active pass. This retains their requested
multiplicity for a later session while preventing reconnection from inserting them
into the current pass. Legacy queues without provenance retain their own snapshot;
restoration does not infer that they represent the whole library. Inline legacy
checkpoints infer consumption only for entries before the saved cursor; history
that older builds never recorded cannot be reconstructed.

`TrackQueue` records original occurrences, consumed occurrence IDs, and deliberate
replay IDs independently of the cursor. Forward skips/jumps and unavailable traversal
consume entries. Backward navigation retains consumption and marks the existing
sequence for deliberate replay. Moving played entries into the future, explicit
scheduling, duplicate additions, and Repeat One remain intentional exceptions.
Shuffle toggles preserve the current occurrence, position, and playback intent.
Consumed non-replay tail entries move into history. Scheduled requests and deliberate
replays stay ahead of the freely shuffled remainder. Switching shuffle off restores
eligible entries to original order, with scheduled requests first and additions in
append order. Both ordering directions run on workers.

## Preparation and activation

`ShufflePreparation` caches one immutable request/result. Its key includes exact
queue state, work kind, source occurrences, availability, comparison history and
last audible identity. Queue edits, mode changes, pause/interruption, source changes
and new audible starts invalidate work. A generation guard prevents even an
uncooperative cancelled task from publishing. Only the controller's main thread
activates a result after rebuilding and matching its input key.

A selected opening starts audio while its remainder prepares. If that track finishes
first, automatic advancement waits for the generated order. Repeat All also prepares
ahead and waits at the pass boundary when necessary. These waits survive restoration
paused; explicit Play resumes their continuation. Paused Next stays paused. Repeat
One and one-shot Stop after take precedence, including changes during a wait.
Preparation alone does not commit order/history. Playback controls never activate
an old result after an intervening pause or queue replacement.

Source membership is resolved off-main for repository snapshots. Metadata-only
updates that leave membership, order and availability unchanged reuse preparation.
New-pass activation waits for outstanding membership validation. Remainder ordering
uses its captured queue, so membership changes intended for the next pass do not
restart current-pass preparation. Queue rendering uses
LazyColumn, indexed metadata lookup and a constant-space drag preview; only visible
rows are composed. A queue snapshot allocates one occurrence-ID namespace rather
than requesting a UUID separately for every entry. Library snapshots cache their
track and identity indexes; transport availability checks do not scan all metadata.

## Sampling and freshness

The sampler returns occurrence indices and never allocates metadata/player objects.
It consumes injected unbiased bounded draws. Android production draws buffered
unsigned 32-bit words directly from `/dev/urandom`, rejecting the incomplete modulo
range. Failed reads propagate without a provider fallback; cancellation closes the
stream. The [Android randomness guidance](https://developer.android.com/privacy-and-security/risks/weak-prng)
documents the platform's use of `/dev/urandom`. Reading it throughout generation
avoids an app-owned fixed replay seed. It does not prove unrestricted permutation
reachability from the kernel's finite-state generator: uniformity arguments assume
independent uniform source draws, and hardware/provider acceptance remains open.

The first unconstrained pass is uniform over occurrence permutations. Subsequent
passes avoid the previous collection opening and globally last audible track when
feasible; audible avoidance wins tiny conflicts. User-selected openings override
these constraints. A mid-session toggle compares only its freely permutable
remainder without imposing a whole-session opening constraint.

Both orders project onto shared track identities before counting position matches
and directed adjacent pairs. Duplicate transitions retain multiplicities. Fixed
entries are excluded. The previous projection and transition counts compile once,
including when a large history shrinks to a tiny collection.

For up to eight total occurrences, enumerate all occurrence permutations, determine
the minimum feasible sum of position and directed-pair matches, and use reservoir
selection over every minimum. Larger unique-track collections use Fisher–Yates and
full-permutation rejection. Shared overlaps of at most eight use exact feasible
projection minima, accounting for nonshared entries that can open the full order.
Larger overlaps have separate limits of three positions and three pairs.

For unique overlap k > 8, expected position matches are 1 and expected shared directed
pairs are (k-1)/k < 1. Markov bounds each probability of exceeding three by at most
1/4 and less than 1/4 respectively. At most two forbidden openings have probability
at most 2/n <= 2/9. The union bound is below 1, proving a feasible permutation exists.
A fixed opening removes that failure event. Rejection preserves uniformity over
all permitted permutations; there is no attempt cap, repair or unchecked fallback.

Large duplicate collections use deterministic feasibility before sampling:

- Identical-track queues have invariant similarity; shuffle their occurrences.
- A single minority occurrence permits O(n) enumeration of its identity positions.
  Determine ordinary feasibility, or the exact minimum when infeasible/tiny, draw
  one admissible position uniformly, then shuffle majority occurrences. Each
  identity ordering has the same (n-1)! occurrence multiplicity.
- Tiny shared projections use exhaustive feasible projection minima.
- Other mixed queues first seek deterministic witnesses of the ordinary limits.
  A witness is used only to prove feasibility. Sampling still rejects unbiased full
  permutations. When witnesses do not establish feasibility, exact multiset
  enumeration either proves ordinary feasibility or proves infeasibility and finds
  the minimum attainable total score. All minimum orders are then admissible.

Limits never relax because random attempts failed. Enumeration and scoring check
cancellation in bounded work batches. **The general mixed-duplicate verifier can be
combinatorial; it does not establish efficient arbitrary 100k duplicate-heavy
preparation.** Cancellation keeps controls usable but does not make that algorithm
fast. This remains an implementation/performance gap against the full contract.

## Durable history and checkpoints

History lives in checksum-validated per-collection files under `files/shuffle-history/`,
with an independent recovery record. Load only the relevant collection. Activated
generated orders have increasing per-collection revisions; stale writes cannot
replace newer records. Toggles and manual edits retain the original generated order.
The active checkpoint also retains that order/revision and can repair an interrupted
history write. Switching collections retains prior records on disk. Pending writes
coalesce per collection, retry with bounded backoff, and hold at most eight collection
records; an actual storage backlog prevents further new-history activations with an
explanation. Existing playback remains usable.

`files/playback-v1.json` retains the legacy discovery filename but now contains a
version-3 checksummed cursor envelope referencing an immutable SHA-256-addressed
structure. Version-1 inline and version-2 linked cursors remain readable. Structures
contain occurrences, original order, provenance, retained membership and generated
order. Cursor saves contain position, selection, modes, scheduling, markers,
continuations and compact consumption/replay bitmaps indexed by that exact structure.
Position saves do not serialize the full queue. Explicit scheduling data can still
grow with the number of outstanding requests.

Each structure has an independent recovery copy. Atomic cursor replacement retains
an additional validated cursor generation with its referenced structure. Loading
chooses the newest valid linked generation, paused, and explains recovery; if none
is valid it restores explained empty paused state. Unreferenced structures are
removed after successful commits. Reset invalidates pending writers and clears
both playback generations and collection history under the existing reset journal.
Files in the music library are never modified.

Store access is serialized explicitly; [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)
supplies replacement semantics. Flags use the platform
[BitSet byte-array API](https://developer.android.com/reference/java/util/BitSet).
These mechanisms and emulator interruption tests do not establish sudden-power-loss
durability on a physical head unit.

## Validation and remaining acceptance

Host tests cover source/query identity and refresh, deletion/empty distinctions,
consumption/replay, original-order restoration, duplicate-aware feasibility against
exhaustive oracles, cancellation, history shrinkage, bounded draws and virtual drag
preview equivalence. Playback instrumentation covers real decoding, gated preparation,
initial/pass boundary waits, pause/restoration, scope replacement, manual duplicate
counts, scheduled stops, Repeat One, disk history, checksum damage, linked-generation
recovery, injected write failure/retry and 100k component measurements.

`:app:testDebugUnitTest` passes all 165 host tests. Debug and test APK builds and
`:app:lintDebug` pass (0 errors, 48 warnings). All 35 playback instrumentation tests
pass on API 23 and API 31 using isolated emulator data images. All 12 library
instrumentation regressions also pass on API 31 after the snapshot-index changes. Installed app APKs
were verified byte-for-byte against the build. Structure-write counters directly verify that position updates preserve immutable
queue data. API 23 reference UI checks verify visible-row composition, current-row
drag ordering and fast scrolling to the final entry; model/decoder tests verify
selection and position preservation. Local logs, JSON measurements and UI dumps are under
`build/visual-comparison/genuine-shuffle/`.

One observation from each full synthetic 100k-identifier fixture run:

| Component | API 23 | API 31 |
| --- | ---: | ---: |
| Structural checkpoint | 513 ms | 740 ms |
| Two cursor checkpoints | 42 ms | 62 ms |
| Unique-track sampling | 255 ms | 376 ms |
| Identical-track occurrence sampling | 90 ms | 116 ms |
| Single-minority occurrence sampling | 121 ms | 118 ms |
| Cursor file, 50k consumed flags | 8,914 bytes | 8,914 bytes |

These are emulator component observations with synthetic short IDs and a closed
controller during persistence measurements; they are not controlled comparative
benchmarks. They isolate sampling and persistence, not a complete 100k-library UI,
scanner or memory acceptance run. Remaining work includes the general mixed-duplicate
performance gap, further reduction/measurement of owning-thread snapshot/list work,
API 33+ and physical-device entropy, removable-storage and power-loss testing. No reliable OEM sleep signal is assumed.

The full 100k-track lifecycle benchmark was removed from the agenda at the user’s
request on 2026-11-12 and is not a pending acceptance requirement. The completed
component measurements above remain historical observations, without a full-app
performance claim at that scale.

### Mixed-duplicate scalability run — 2026-11-12

An opt-in host measurement exercised the production `ShuffleSampler` with synthetic
IDs, using the same queue as previous history and the normal opening exclusion.
Four patterns (balanced two identities, grouped/interleaved three identities, and
two occurrences per identity with an odd final singleton) ran at 9, 12, 18, 24,
48, 96 and 384 entries with seeds 42 and 2026: 56 attempts total. Each attempt
had a cooperative one-second deadline. Completed results were checked for exact
occurrence preservation, unchanged inputs and the opening exclusion.

| Queue entries | Completed | Hit deadline before sampling |
| --- | ---: | ---: |
| 9 | 8/8 | 0/8 |
| 12 | 8/8 | 0/8 |
| 18 | 4/8 | 4/8 |
| 24 | 0/8 | 8/8 |
| 48 | 0/8 | 8/8 |
| 96 | 0/8 | 8/8 |
| 384 | 0/8 | 8/8 |

At 18 entries, grouped three-identity and lightly duplicated queues hit the deadline;
balanced two-identity and interleaved three-identity queues completed in about
62–136 ms. All 36 deadline cases had zero random draws, locating the bottleneck in
deterministic preparation/feasibility rather than random rejection sampling. The
largest observed deadline elapsed time was about 1.02 seconds, including cooperative
cancellation. These results confirm the mixed-duplicate scalability gap remains open;
they do not establish a maximum supported queue size or full performance acceptance.

Environment: Linux x86_64, Intel Core i5-8365U, 8 logical CPUs, OpenJDK 25.0.4.1.
These are local host observations with two seeds, no isolated warm-up or controlled
load, and no Android hardware/memory measurement. Seeded `java.util.Random` is only
used for repeatable test inputs; production entropy is unchanged. No 100k-track
benchmark was run.

[Raw measurements](measurements/mixed-duplicate-scalability-2026-11-12.csv) retain
per-attempt timing, draw counts and completed similarity scores. Reproduce with:

```bash
GENUINE_SHUFFLE_SCALABILITY_OUTPUT="$PWD/build/reports/genuine-shuffle/mixed-duplicate-scalability.csv" \
  ./gradlew :app:testDebugUnitTest --tests '*MixedDuplicateScalabilityTest' --rerun-tasks
```

Without that environment variable, the measurement test is skipped. Deadlines are
recorded results rather than JUnit failures; a successful harness run does not mean
scalability acceptance passed. Existing exhaustive-oracle tests validate the
sampler's feasibility/relaxation rules separately.

### Simulated removable-storage run — 2026-11-12

API 31 (`clocarmus_splash_api31`, isolated read-only emulator instance) passed 17
instrumentation checks: all 14 library tests, including two new recovery checks,
and three new playback storage-loss checks. Debug and instrumentation APK builds
passed. The injected `DocumentAccess` boundary throws an I/O failure to simulate a
disconnected drive or `SecurityException` to simulate lost access. SQLite, saved
checkpoints and MediaPlayer WAV decoding are real Android implementations.

The checks verify:

- Disconnection and access loss retain track identity, cached metadata, import time,
  Favorites and custom playlist membership across library recreation and recovery.
  Missing-file cleanup does not delete temporarily unavailable entries.
- Active shuffled playback pauses; queue occurrence IDs, consumption, shuffle mode,
  selected entry and position survive controller recreation while storage is absent.
- Play while unavailable reports the problem without opening audio. Reconnection or
  restored access stays paused; explicit Play resumes decoding and position progress.
- Next with every queued track disconnected terminates, consumes the unavailable tail
  and does not open unavailable audio. Reconnection still does not resume playback.

[Run metadata and passed test names](measurements/removable-storage-api31-2026-11-12.json)
record the APK hashes. Full runner output is saved under
`build/reports/genuine-shuffle/removable-storage-api31/instrumentation.txt`.
The measurement emulator was stopped after testing; the pre-existing API 23 emulator
was left running.

This is simulated provider-failure coverage. No physical USB/SD device was removed,
no Android persisted URI grant was actually revoked, and no storage broadcast or
OEM connection-detection path was validated. Physical storage acceptance remains open.
