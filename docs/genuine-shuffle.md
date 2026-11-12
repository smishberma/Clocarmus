# Genuine Shuffle implementation

Started 2026-11-12. `playback/shuffle/ShuffleSampler.kt` now runs through the
process-owned PlaybackController. Normal browser selection and the shuffle button
use background preparation; reference previews retain their demo ordering. This
implements the playback integration slice, not the entire PLAN.md contract.

## Implemented sampling policy

Inputs are eligible snapshot track identities, the previous committed generated
order for that collection, the globally last audible identity, and an optional
user-selected first occurrence. The result contains occurrence indices; the engine
does not load metadata, allocate UUIDs, query storage or commit history. It copies
inputs and runs on Dispatchers.Default. An injected bounded-draw interface keeps
the mathematical sampling policy separate from the production entropy provider.
The runtime adapter maps existing string track identities to snapshot-local numeric
identities without merging physical tracks or occurrence IDs.

For up to eight total occurrences, enumerate all occurrence permutations, retain
feasible opening rules, minimize position-plus-directed-pair matches, and use
reservoir selection over every minimizing permutation. Duplicates remain separate
occurrences and scoring uses track identities and pair multiplicities. A selected
first occurrence is pinned and removed from scoring; one matching occurrence is
also removed from the previous comparison. Audible-repeat avoidance wins opening
conflicts. One-track repetition and empty queues are defined.

Larger unique-track snapshots use Fisher–Yates and full-permutation rejection,
with no attempt cap, repairs, best-of sampling or unchecked fallback. Project both
orders onto shared identities before scoring. Overlaps of at most eight are
exhaustively evaluated to determine the attainable minimum. This feasibility
calculation allows a nonshared entry to open the full permutation; it does not
incorrectly impose opening restrictions on the projected subsequence alone.
Larger overlaps have separate limits of three position and three directed-pair
matches. Candidate arrays are reused and scoring uses precomputed position and
successor maps. Enumeration, generation and rejection check coroutine cancellation
at intervals of 256 work steps. Bulk snapshot copying/index construction still
has linear non-suspending sections; end-to-end preparation latency is not validated.

## Feasibility and uniformity argument

Assume independent uniform bounded draws. Fisher–Yates gives every occurrence
permutation equal probability; rejecting all disallowed candidates preserves
uniformity on the permitted set. Reservoir sampling gives every enumerated
minimum-cost permutation equal probability. These are algorithmic statements
under the random-source assumption, not proof about Android entropy providers.

For unique-track overlap k > 8, a uniform projected permutation has expected
position matches 1 and expected shared directed-pair matches (k-1)/k < 1.
Markov's inequality bounds the probability of exceeding either separate limit
of three by at most 1/4 for positions and less than 1/4 for pairs. Without a fixed
opening, at most two identities are forbidden first: their combined probability
is at most 2/n <= 2/9. The union bound is therefore strictly less than 1, proving
that at least one permitted order exists. A fixed opening removes that opening
failure event, and scoring considers only the freely permutable remainder.
For tiny overlaps, enumeration considers precisely the feasible relative orders;
any accepted projected order can be interleaved with the nonshared entries.
No-overlap comparisons have cost zero. Large duplicate-heavy inputs are rejected
before random draws: this proof does not cover their multiplicities.

## Playback preparation and activation

`ShufflePreparation` owns one cancellable request and cached result. Its immutable
key includes the exact queue (occurrence order, cursor, schedule and marker), work
kind, available snapshot identities, previous comparison order and last audible
identity. Identical inputs reuse work. Invalidating cancels the job, drops cached
results and advances a generation guard. Even an uncooperative old task cannot
publish after cancellation. Results become active only on the controller's main
thread after rebuilding and comparing the current input key.

Browser selection pins the selected occurrence first and begins audio while the
remaining order prepares. A toggle shuffles the upcoming remainder, with earlier
entries/current occurrence and explicit scheduling held fixed. Activation does
not reset position or release the decoder. Explicit Queue selection and Previous
cancel pending remainder ordering; queue edits and relevant availability changes
invalidate prepared results. Position ticks do not invalidate preparation.

With shuffled Repeat All, the controller prepares a fresh pass of available
active-snapshot occurrences ahead of completion. If unfinished at the boundary,
it releases the completed decoder and waits without replaying the old final
track. Pause, focus loss, queue replacement and mode changes prevent stale
activation. Paused Next can advance into a prepared pass while staying paused.
One-shot Stop after wins over an already prepared pass. Preparation errors retain
the queue: an unsuccessful toggle switches shuffle off with an explanation;
a failed next pass stops at the boundary and permits an explicit Play retry.
There is no unchecked ordering fallback.

Preparation does not commit generated-session history. Only activated orders
update the controller's current-process generated order. Ordinary edits/toggles
retain that comparison order for the next pass. The existing atomic checkpoint
persists the actual effective queue and an optional `shuffleBoundary` continuation
flag (backwards-compatible with older version-1 files). A fresh controller restores
paused and does not prepare or activate a new session until an explicit command.

The production adapter buffers reads directly from `/dev/urandom` throughout each
generation and closes the stream on completion/cancellation/failure. Unsigned
32-bit rejection draws remove modulo bias; failed reads propagate without a PRNG
fallback. This avoids deriving the complete order from an app-owned finite seed.
It does not establish unrestricted permutation reachability from the kernel's
finite-state random generator. Provider/device entropy acceptance remains open.
The [Android randomness guidance](https://developer.android.com/privacy-and-security/risks/weak-prng)
documents `/dev/urandom` as the platform default seed source; the integration
suite exercises direct access on API 23 only.

## Remaining implementation and current limits

- Capture semantic collection provenance and retained search scope/query; refresh
  membership at shuffled Repeat All, including deleted-playlist/manual exceptions.
  **Current Repeat All uses available occurrences in the active queue snapshot.**
  New source additions are not incorporated, and temporary edits currently carry
  into the next pass. It never substitutes unrelated library tracks.
- Track consumption and deliberate replay independently from cursor. The current
  remainder uses cursor position, so backward navigation followed by toggling does
  not yet satisfy the independent-consumption contract. Switching shuffle off
  retains the effective remainder; restoring original collection order is pending.
- Persist history by semantic collection, generated order and consumption with
  version-linked structural/cursor checkpoints. Current-process history is only
  for the active session; restoration uses the persisted effective order as the
  comparison fallback. Switching collections does not yet retain their histories.
- Define and prove deterministic feasibility/relaxation for duplicate-heavy queues
  larger than eight occurrences. The current sampler rejects them, and playback
  handles that failure without silently weakening sampling constraints.
- Finish entropy/provider acceptance, particularly modern Android and hardware.
- Benchmark the complete 100k lifecycle: memory, metadata access, startup,
  serialization, cancellation and Queue rendering. Snapshot construction and some
  controller list operations are still linear work on the owning thread. The host
  100k sampling test is a correctness check, not a device performance result.

Coroutine cancellation follows the official
[Kotlin ensureActive contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/ensure-active.html).

## Validation

Host tests cover the sampler, cancellation/stale-result guards, cached preparation,
failure handling, unsigned bounded draws, entropy EOF, pinned scheduling and
available-snapshot filtering. Playback instrumentation uses real decoding and gated
preparation to check uninterrupted browser starts, paused toggles, boundary waits,
pause/restoration, scope replacement, scheduled stops, failure/retry, paused Next
and explicit occurrence selection.

`:app:testDebugUnitTest` passes all 147 host tests (20 shuffle tests).
`:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug` pass.
The API 23 playback suite passes all 18 instrumentation tests. No modern-Android,
physical-device, full collection-lifecycle or 100k performance acceptance is claimed.
