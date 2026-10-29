# Main player assets

The main player uses the supplied SVGs in [assets/](assets/), converted into Android vector resources under `app/src/main/res/drawable/player_*.xml`. Original SVGs remain unchanged. Icons use `ContentScale.Fit` and square display boxes; artwork uses the square `no-album-art.svg` placeholder. All placement and icon dimensions share one uniform scale within each screen half. Text uses Inter directly, with no geometric scaling.

Menu normal/pressed, play/pause, shuffle on/off, and repeat off/one/all each use their provided asset. Menu and repeat-one SVG text is outlined during import so the vectors retain its original lettering. The volume slider background is derived from `volume-slider.svg`; its changing fill and seek-bar track/thumb are rendered by Compose. The volume icon is imported separately. No additional main-screen assets are required.

## Regenerating Android vectors

The generated XML resources are checked in, so normal Android builds do not require Inkscape or SVG conversion. After changing a source SVG, install Inkscape, make the bundled Inter font available to Inkscape, and use a JDK plus the project's resolved Gradle build-tool dependencies:

```sh
python3 scripts/import-player-assets.py
./gradlew assembleDebug
```

The import script outlines text with Inkscape and calls the Android build tools' `Svg2Vector` converter. It preserves the SVG viewports, paths, colors, and gradient. It does not rasterize the assets. Run a normal Gradle build first if the build-tool dependencies have not been downloaded yet.

Inter 4.1 regular and bold are bundled as static TTF fonts for Android 6 compatibility. Source: https://github.com/rsms/inter/releases/tag/v4.1. Inter is distributed under the SIL Open Font License; see [the included license](licenses/Inter-LICENSE.txt).
