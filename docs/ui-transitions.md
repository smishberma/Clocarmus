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

Long-press Back still clears unfinished local work before saving browser state.
Settings retains its outgoing Appearance page visually while saving its root page
for the next entry.

For visual checks, debug launches can use `reference_preview=true` for stable
sample tracks and a fixed clock. After checking animations, stop that activity and
launch without preview extras to restore the user's normal library view.
