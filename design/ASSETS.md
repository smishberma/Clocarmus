# Player and menu assets

The main player uses the supplied SVGs in [assets/main-player/](assets/main-player/) and [assets/shared/](assets/shared/), converted into Android vector resources under `app/src/main/res/drawable/player_*.xml`. Original SVGs remain unchanged. Icons use `ContentScale.Fit` and square display boxes; artwork uses the square `no-album-art.svg` placeholder. All placement and icon dimensions share one uniform scale within each screen half. Text uses Inter directly, with no geometric scaling.

Menu normal/pressed, play/pause, shuffle on/off, and repeat off/one/all each use their provided asset. Menu and repeat-one SVG text is outlined during import so the vectors retain its original lettering. The volume slider background is derived from `volume-slider.svg`; its changing fill and seek-bar track/thumb are rendered by Compose. The volume icon is imported separately. No additional main-screen assets are required.

## Regenerating Android vectors

The generated XML resources are checked in, so normal Android builds do not require Inkscape or SVG conversion. After changing a source SVG, install Inkscape, make the bundled Inter font available to Inkscape, and use a JDK plus the project's resolved Gradle build-tool dependencies:

```sh
python3 scripts/import-player-assets.py --group menu-page
./gradlew assembleDebug
```

The import script outlines text with Inkscape and calls the Android build tools' `Svg2Vector` converter. It preserves the SVG viewports, paths, colors, and gradient. All icons and the artwork placeholder remain vectors; no PNG export is used. Run a normal Gradle build first if the build-tool dependencies have not been downloaded yet.

Inter 4.1 regular and bold are bundled as static TTF fonts for Android 6 compatibility. Source: https://github.com/rsms/inter/releases/tag/v4.1. Inter is distributed under the SIL Open Font License; see [the included license](licenses/Inter-LICENSE.txt).

## Menu assets

The sixteen normal/pressed SVGs in [assets/menu-page/](assets/menu-page/) are imported as `menu_*.xml`. Back and Settings retain their supplied outlined lettering and square source canvases, with touch targets on the visible pills. Category icons retain their source proportions; category labels use bundled Inter Bold. No SVG source is modified.

```sh
python3 scripts/import-player-assets.py --group menu-page
```

The importer searches the reorganized asset folders recursively and supports selecting a single folder with `--group`. Stroke-scaling warnings are printed; unsupported features remain errors. The six category buttons, Back, and Settings switch to the supplied pressed SVG for the duration of a press, with the default ripple suppressed. Releasing or canceling a press restores the normal SVG. Category labels retain their normal color. Shared transport controls use the category icons' pink (`#bf1363`) while pressed because no transport pressed SVGs were supplied.

## Updated artwork placeholder

`design/assets/main-player/no-album-art.svg` is imported as `app/src/main/res/drawable/player_no_album_art.xml`. The current SVG has no blur filters; its paths, gradients, and outlined lettering stay vector-based in the existing square artwork frame. Importing it removes the previous PNG resource. The importer strips unused SVG script elements from its temporary copy and preserves the `xlink` namespace for linked gradients; the source SVG remains unchanged.

```sh
python3 scripts/import-player-assets.py --group main-player --asset no-album-art
```

The checked-in vector requires no runtime SVG library or conversion tools during normal builds. `vectorDrawables.useSupportLibrary = true` disables Android's generated PNG fallbacks, allowing Compose to load the vector and its gradients directly on API 23.

## Button canvases and touch bounds

Transparent SVG space is part of the image's layout bounds. It does not automatically exclude touches. The main-player Menu SVG has a 48×48 page, while the visible pill (including its stroke) occupies approximately x=0..48, y=12.979..34.0575. Its touch target now follows that pill's outer bounds and rounded corners, while the original square canvas and visual placement are preserved. Compose's automatic minimum touch-target expansion is disabled only for this button so it cannot extend beyond the border.

For future button assets in Inkscape, resize the page to the drawing or selected button, including the outer stroke, and export the page. Avoid invisible spacer objects. Use matching page/viewBox dimensions and matching outer button bounds for normal and pressed variants. Leave intended padding inside the button border; keep surrounding layout spacing in the app. Cropped canvases simplify sizing, but the app must still define the intended hit shape: an image's transparent pixels do not define its touch target. If this Menu asset's canvas changes, update its documented bounds in `MenuButton` accordingly.
