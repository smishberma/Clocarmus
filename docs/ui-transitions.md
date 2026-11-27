# Navigation transitions

Player navigation uses a 300 ms crossfade by default. The outgoing view fades
out while the incoming view fades in. Initial display is immediate, and playback
continues during navigation.

The shared `PlayerPaneCrossfade` covers Menu and its library browsers (including
their child pages), playlist membership, track panels, Settings pages and
Appearance palettes, and the Launcher-mode app drawer. Back, Close, automatic
player returns, and long-press Back use the same transition as forward navigation.
The volume slider and transport controls remain steady. The clock remains steady
except when switching to or from the app drawer on its half of the display.

Navigation snapshots retain the outgoing destination until its fade finishes.
Outgoing views have their touch input and accessibility semantics suppressed and
use an inactive lifecycle owner so their Back handlers cannot intercept the
incoming destination. Browser saveable state and the source behind track panels
remain available, preserving searches and scroll positions.

Shared library browsers render rows with `LazyColumn`, keyed by the existing row
identities. Each destination saves its first visible item and pixel offset; opening
track panels or returning through Menu retains that position. A changed search
resets its results to the top. The supplied scrollbar uses rounded row heights and
gaps, including the shorter folder rows, so dragging can jump across a large list
without composing intermediate rows. All Tracks sorts and filters on Default when
the library or query changes, retaining the query with its displayed results for
playback scope. Transport and navigation recompositions reuse those results.

Browser validation on 2026-11-27: debug build, compatibility verification, lint
(0 errors), Android test APK assembly and 235 JVM tests pass; the existing mixed
duplicate scalability benchmark remains skipped. Three browser instrumentation
tests pass on both API 23 (`headunit_a6`) and API 29 (`headunit_a10`): a generated
10,000-track list accesses fewer than 300 track entries on opening and fewer than
1,000 through an end jump, retains selection/long-press actions and scroll position,
and preserves search through Menu return. These entry-access bounds detect eager
row construction; they are not frame-time measurements. Visual captures and test
logs are under `build/visual-comparison/browser-performance/`. Physical head-unit
timing remains unmeasured.

Long-press Back still clears unfinished local work before saving browser state.
Settings retains its outgoing Appearance page visually while saving its root page
for the next entry.

For visual checks, debug launches can use `reference_preview=true` for stable
sample tracks and a fixed clock. After checking animations, stop that activity and
launch without preview extras to restore the user's normal library view.
