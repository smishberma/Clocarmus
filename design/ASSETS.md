# Player and menu assets

## App logo and launcher icons

The supplied [logo-brand/logo-no-background.svg](assets/shared/logo-brand/logo-no-background.svg) is imported as `drawable/brand_logo.xml`, retaining its transparent background and gradients. Use it with Compose `painterResource` for in-app branding, including on API 23.

[logo-brand/logo.svg](assets/shared/logo-brand/logo.svg) supplies the circular launcher artwork; [logo-square.svg](assets/shared/logo-brand/logo-square.svg) supplies the square version. Android 8.0+ uses `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, with a vector foreground derived from the circular SVG and a separate `drawable/ic_launcher_background.xml` vector preserving its orange gradient. `ic_launcher_foreground.xml` combines the SVG-derived `ic_launcher_mark.xml` vector with a transparent 432px `drawable-xxxhdpi/ic_launcher_highlight.png` overlay for the source Gaussian blur, which Android vectors cannot render. The circular backdrop is extended into a full-bleed square for adaptive masks. The original circular canvas fits inside the central 66dp safe circle of the 108dp adaptive layer, preserving the mark under launcher masks. See [Android's adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Android 6.0–7.1 uses legacy PNGs exported directly from `logo-square.svg` for `ic_launcher` and `logo.svg` for `ic_launcher_round`: 48, 72, 96, 144, and 192px in `mipmap-mdpi` through `mipmap-xxxhdpi`. Both manifest icon entries have density-specific PNGs. There are no unqualified `mipmap-anydpi` overrides, so older devices select the PNGs.

Regenerate all branding resources with Inkscape, a JDK, and resolved Gradle dependencies:

```sh
python3 scripts/import-brand-assets.py
./gradlew assembleDebug
```

Source artwork stays unchanged. Generated resources are checked in; normal builds need no SVG conversion tools. The player importer skips `logo-brand` because branding uses this dedicated pipeline.

### Playback notification small icon

`logo-brand/logo-monochrome.svg` supplies `drawable/ic_notification_playback.xml`.
The brand importer unions its white shapes and subtracts the black tonearm detail
as a transparent cutout. This preserves the supplied silhouette without opaque
black paint in Android's alpha mask. No path simplification is applied. The mark
fits proportionally within 22dp, centered on a 24dp transparent vector canvas.
Its only fill is white; Android supplies the notification/status-bar tint.
It uses no gradients, background plate, or attributes requiring API 24.

```sh
python3 scripts/import-brand-assets.py --notification-only
./gradlew :app:assembleDebug
```

The full brand import also regenerates this icon. `PlaybackService.publish()`
uses it for every initial notification and playing/paused update; launcher
resources, artwork and transport action resources are separate and unchanged.

The main player uses the supplied SVGs in [assets/main-player/](assets/main-player/) and [assets/shared/](assets/shared/), converted into Android vector resources under `app/src/main/res/drawable/player_*.xml`. Original SVGs remain unchanged. Icons use `ContentScale.Fit` and square display boxes; artwork uses the square `no-album-art.svg` placeholder. All placement and icon dimensions share one uniform scale within each screen half. Text uses Inter directly, with no geometric scaling.

Menu normal/pressed, play/pause, shuffle on/off, and repeat off/one/all each use their provided asset. Menu and repeat-one SVG text is outlined during import so the vectors retain its original lettering. The volume slider background is derived from `volume-slider.svg`; its changing fill and seek-bar track/thumb are rendered by Compose. The volume icon is imported separately. No additional main-screen assets are required.

Inactive Shuffle and Repeat use the supplied fully opaque white glyphs with cancellation slashes. Their inactive state is distinguished by shape, so the shared inactivity fade applies the same opacity to active and inactive icons. The main-player Menu is drawn at 95% of its previous size, centered at the same position; its rounded touch bounds follow the resized visible pill.

## Regenerating Android vectors

The generated XML resources are checked in, so normal Android builds do not require Inkscape or SVG conversion. After changing a source SVG, install Inkscape, make the bundled Inter font available to Inkscape, and use a JDK plus the project's resolved Gradle build-tool dependencies:

```sh
python3 scripts/import-player-assets.py --group menu-page
./gradlew assembleDebug
```

The import script outlines text with Inkscape and calls the Android build tools' `Svg2Vector` converter. It preserves the SVG viewports, paths, colors, and gradient. All icons and the artwork placeholder remain vectors; no PNG export is used. Run a normal Gradle build first if the build-tool dependencies have not been downloaded yet.

Inter 4.1 regular and bold are bundled as static TTF fonts for Android 6 compatibility. Source: https://github.com/rsms/inter/releases/tag/v4.1. Inter is distributed under the SIL Open Font License; see [the included license](licenses/Inter-LICENSE.txt).

The complete Inter notice and license are also bundled in the APK's Settings → About → Open source licenses document. See [About and license maintenance](../docs/about-and-licenses.md) when updating fonts or dependencies.

## Menu assets

The sixteen normal/pressed SVGs in [assets/menu-page/](assets/menu-page/) are imported as `menu_*.xml`. Back and Settings retain their supplied outlined lettering and square source canvases, with touch targets on the visible pills. Category icons retain their source proportions; category labels use bundled Inter Bold. No SVG source is modified.

```sh
python3 scripts/import-player-assets.py --group menu-page
```

The importer searches the reorganized asset folders recursively and supports selecting a single folder with `--group`. Stroke-scaling warnings are printed; unsupported features remain errors. The six category buttons, Back, and Settings switch to the supplied pressed SVG for the duration of a press, with the default ripple suppressed. Releasing or canceling a press restores the normal SVG. Category labels retain their normal color. Shared transport controls use the supplied `play-pressed.svg`, `pause-pressed.svg`, `skip-back-pressed.svg`, and `skip-next-pressed.svg` while held on the player, menu, and All Tracks screens. Their supplied colors and proportions are preserved, with no tint or ripple added. Releasing or canceling a press restores the appropriate normal asset.

## Updated artwork placeholder

`design/assets/main-player/no-album-art.svg` is imported as `app/src/main/res/drawable/player_no_album_art.xml`. The current SVG has no blur filters; its paths, gradients, and outlined lettering stay vector-based in the existing square artwork frame. Importing it removes the previous PNG resource. The importer strips unused SVG script elements from its temporary copy and preserves the `xlink` namespace for linked gradients; the source SVG remains unchanged.

```sh
python3 scripts/import-player-assets.py --group main-player --asset no-album-art
```

The checked-in vector requires no runtime SVG library or conversion tools during normal builds. `vectorDrawables.useSupportLibrary = true` disables Android's generated PNG fallbacks, allowing Compose to load the vector and its gradients directly on API 23.

## Button canvases and touch bounds

Transparent SVG space is part of the image's layout bounds. It does not automatically exclude touches. The main-player Menu SVG has a 48×48 page, while the visible pill (including its stroke) occupies approximately x=0..48, y=12.979..34.0575. Its touch target now follows that pill's outer bounds and rounded corners, while the original square canvas and visual placement are preserved. Compose's automatic minimum touch-target expansion is disabled only for this button so it cannot extend beyond the border.

For future button assets in Inkscape, resize the page to the drawing or selected button, including the outer stroke, and export the page. Avoid invisible spacer objects. Use matching page/viewBox dimensions and matching outer button bounds for normal and pressed variants. Leave intended padding inside the button border; keep surrounding layout spacing in the app. Cropped canvases simplify sizing, but the app must still define the intended hit shape: an image's transparent pixels do not define its touch target. If this Menu asset's canvas changes, update its documented bounds in `MenuButton` accordingly.

## Track menu assets

The supplied `assets/track-menu/search.svg`, `scroll-pill.svg`, and `play-indicator.svg` are imported as `track_search.xml`, `track_scroll_pill.xml`, and `track_play_indicator.xml`. Search retains its square canvas; the pill retains its original 2.9479708:9.6478851 proportions. Both use `ContentScale.Fit` and the same uniform scale as the rest of the screen. The rail, selected-row background, and temporary letter bubble are drawn with Compose. Back, All Tracks, volume, and transport reuse the existing vectors.

```sh
python3 scripts/import-player-assets.py --group track-menu
```

The pill's pointer area is wider than its visible shape to make dragging easier. Grabbing it preserves the finger's offset; tapping the rail centers the pill at that point. The rail, list scroll offset, and accessibility progress share the same clamped scroll range.

Track rows show title and artist/album on two lines. The selected row uses a solid white background, black title/metadata, and the supplied black play indicator (or a black-tinted pause icon when paused). The scroll rail and letter bubble retain their dark gray background.

## Shared playback pressed assets

The four pressed playback SVGs in `assets/shared/` are imported as `player_play_pressed.xml`, `player_pause_pressed.xml`, `player_skip_back_pressed.xml`, and `player_skip_next_pressed.xml`. Play/Pause chooses the pressed asset for its current playback state; actions still occur on release. Both normal and pressed images use the same proportional display size and existing touch target.

To regenerate these individually, run `python3 scripts/import-player-assets.py --group shared --asset play-pressed` (and repeat for `pause-pressed`, `skip-back-pressed`, and `skip-next-pressed`).

## Artist options pressed assets

Play all uses `assets/shared/play-all.svg` and `play-all-pressed.svg`, imported as `player_play_all.xml` and `player_play_all_pressed.xml`, replacing the triangle previously drawn with Compose. Albums uses the shared `albums.svg` / `albums-pressed.svg` pair (`player_albums*.xml`). Tracks reuses `menu_all_tracks.xml` / `menu_all_tracks_pressed.xml`. Each button shows its supplied pressed SVG while held and restores its normal SVG on release or cancellation, retaining white labels and suppressing ripples. Back and the shared transport buttons retain their existing pressed assets.

Regenerate each new shared vector with `python3 scripts/import-player-assets.py --group shared --asset play-all`, repeating for `play-all-pressed`, `albums`, and `albums-pressed`.

## Playlist assets

The supplied `assets/playlists-screen/add-playlist.svg` and `add-playlist-pressed.svg` are used as `add_playlist.xml` and `add_playlist_pressed.xml`, preserving their white/magenta paths and square viewports. They appear only on the overview. Regenerate them with `python3 scripts/import-player-assets.py --group playlists-screen`.

The heading reuses `menu_playlists.xml`. The built-in heart and recent-history glyphs are local Android vectors (`playlist_favorites.xml`, `playlist_recent_added.xml`, `playlist_recent_played.xml`) drawn to match the updated mockups; no separate source SVGs were supplied for these glyphs. Detail rows reuse the supplied selected-track indicator and transport assets.

## Splash screen

Reference: `029-Splash-screen.png`. `scripts/import-splash-assets.py` exports `assets/splash-screen/splash-screen-background.svg` and `assets/shared/logo-brand/logo.svg` to `drawable-nodpi/splash_background.png` and `splash_logo.png`. Bitmap export preserves the SVG's elliptical gradient transforms. Compose fits the background and square logo uniformly inside one reference canvas; the wordmark uses bundled Inter Bold with measured spacing, without stretching. A separate padded `drawable-xxxhdpi/splash_system_icon.png` keeps the circular logo inside Android 12's mask-safe region. Inkscape is needed only for regeneration; all generated assets are checked in.


## Track Options

References `030`–`031` use the supplied `assets/long-press/info-icon.svg`, imported as `options_info_icon.xml`. Regenerate with `python3 scripts/import-player-assets.py --group long-press`. The popup reuses the collections Close button assets and shared Back/transport controls. Option pills use the reference's gray fill with white text, switching to white with black text while pressed; deferred actions use muted text and have no click action.


## App drawer redesign

References 03–05 under `screens/app-drawer/` use dedicated drawer layouts and the supplied `assets/app-drawer/search-apps.svg`, `refresh-apps.svg`, and `pin.svg`, imported as `drawer_search_apps.xml`, `drawer_refresh_apps.xml`, and `drawer_pin.xml`. Settings and Back retain their drawer SVG variants. App icons come from Android package discovery, rather than placeholder artwork. The options panel and search controls use drawer-specific geometry with bundled Inter. The app-options Close button uses the shared collections button and its supplied `track-collections/button-unpressed.svg` and `button-pressed.svg` resources, including pressed feedback.

Regenerate with `python3 scripts/import-player-assets.py --group app-drawer`. Enable Launcher mode in the emulator first, then capture with `python3 scripts/compare-main-player.py --screen app-drawer --install`. The comparison uses real installed apps; app icons/names naturally differ from the wireframe.

## About and developer assets

The supplied `assets/about-sreen/` directory (original spelling retained) contains
`clocarmus-about-graphic.png` and `developer-avatar.png`. These are copied unchanged
to `drawable-nodpi/about_banner.png` and `about_developer_avatar.png`, preserving
their proportions with `ContentScale.Fit`. The supplied social SVGs are imported
as `about_x.xml`, `about_linkedin.xml`, `about_github.xml` and `about_email.xml`:

```sh
python3 scripts/import-player-assets.py --group about-sreen
```

References are `screens/about-screen/redesigned-about-screen.png` and
`screens/about-screen/about-developer.png`. Back, volume, Settings heading, clock,
transport, action rows and panel buttons reuse the established implementations.


## Background settings redesign

The references under `screens/settings-screen/01-settings-redesigned-drop-down-collapsed.png`,
`02-settings-redesigned-drop-down-expanded.png` and `03-settings-customize-solid-colors.png`
replace the tabbed background selector. The supplied `drop-down.svg`, `customize-button.svg`
and `reset-all-button.svg` are imported as `settings_drop_down.xml`,
`settings_customize_button.xml` and `settings_reset_all_button.xml`, preserving outlined
Inter labels. Regenerate with `python3 scripts/import-player-assets.py --group settings-screen --asset STEM`
for each of those three stems. Existing radio and preset-gradient assets remain in use.
The Law preset appears directly below Modern and uses `assets/settings-screen/Law-gradient.svg`.
Regenerate it with `python3 scripts/import-player-assets.py --group settings-screen --asset Law-gradient`.
It exports to `drawable-nodpi/settings_law_gradient.png` at 1644 pixels wide to preserve
the SVG's transformed elliptical radial gradients, which Android vectors cannot represent.
Background preset lists scroll within the customization pane and show the shared
draggable scrollbar when their content overflows. Solid and gradient scroll positions
are remembered independently while navigating settings.
Capture the three references using `scripts/compare-main-player.py --screen settings-background`,
`--screen settings-background-expanded` and `--screen settings-background-solid`.

## Animated divider

`assets/animated-dividers/equalizer.json` is the supplied Lottie source, copied unchanged
to `app/src/main/res/raw/divider_equalizer.json`. Regenerate it with
`python3 scripts/import-player-assets.py --group animated-dividers` (no Inkscape needed).
The source is 312 × 40, 24 fps, 52 frames. Runtime applies a white color filter and
90-degree clockwise rotation without changing its timing or geometry. The reference is
`assets/animated-dividers/visualizer-animated-divider.png`. Use debug
`settings_preview=settings-divider-animated` to inspect the shared divider, then
long-press Back to inspect it on the player. The existing static-divider captures now
open the redesigned type selector; Customize opens the static preset rows.

Color line uses `assets/animated-dividers/Color-Line.json`, copied unchanged to
`app/src/main/res/raw/divider_color_line.json` by the same animated-dividers import
group (or `--asset Color-Line`). Its 400 × 4 canvas, original gradient colors, and
approximately 29.97 fps/60-frame timing are preserved. The shared renderer rotates
it 90 degrees clockwise at the pane boundary and uses its own 100:1 aspect ratio.
Its first frame has zero-width shapes, so it becomes visible as playback animates.
Animated customization remembers either preset independently of the selected type.
