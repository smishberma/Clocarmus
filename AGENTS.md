# Working on Clocarmus

## Project map

- One Android application module, `:app`: an offline, landscape music player and clock built with Kotlin and Jetpack Compose.
- Sources live under `app/src/main/java/com/example/clocarmus/`. `ClocarmusApplication` owns the process library and playback session; `MainActivity` hosts Compose and startup navigation.
- `library/` implements SAF discovery, metadata extraction, versioned snapshots and SQLite persistence. `playback/` implements transport, service/session integration and checkpoints; `playback/shuffle/` implements collection resolution and background ordering.
- `ui/player/` contains screens, shared controls and several pure collection/queue models. `ui/startup/` handles onboarding, source configuration and reset recovery; `launcher/` handles optional Home/boot behavior.
- `core/` contains contracts and independently tested models. These interfaces are not the current runtime wiring. Runtime playback uses platform `MediaPlayer`/`MediaSession`, and library storage uses `SQLiteOpenHelper`, despite declared Media3 and Room dependencies.
- `design/` contains references and source assets; `scripts/` contains import and visual comparison tools. Generated Android resources are checked in.

## Developing, fixing and refactoring

- Read the relevant implementation and tests before changing behavior. Consult `PLAN.md` for agreed requirements and the focused documents below for contracts and remaining acceptance work. README, PLAN and source comments include historical stages; verify current behavior against code.
- Follow local formatting and official Kotlin style: four-space indentation, PascalCase types/composables, camelCase functions/properties and snake_case Android resources. Preserve the existing use of `internal`, immutable data classes and top-level helpers.
- Extend existing owners and shared controls. Keep pure logic independently testable; preserve browser queries, scroll state, navigation return targets and lifecycle cleanup during UI refactors.
- Keep database/provider access, hashing, artwork decoding and persistence off the UI thread. Preserve playback's owning-thread access, cancellation and generation guards when changing asynchronous work.
- Keep deterministic debug reference fixtures separate from real library/playback behavior. Real playback progress comes from the decoder.

## Compatibility and data contracts

- Preserve API 23 support, compile/target SDK 35 and Java source/target 17. Application ID is `com.smishberma.clocarmus`; Kotlin/resource namespace is `com.example.clocarmus`.
- Manage dependency versions in `gradle/libs.versions.toml`. Check resolved AAR requirements and the merged manifest after dependency changes; retain offline operation without `INTERNET` or `ACCESS_NETWORK_STATE` permissions.
- Traverse selected SAF trees with read grants. Document IDs are opaque. Preserve stable track identities and separate physical copies; disconnection, permission loss and incomplete scans are not deletion evidence. Source removal and library reset must leave music files untouched.
- Preserve exact queue occurrence identities, collection/search scope, scheduling and shuffle consumption/replay state. Returning to the UI must retain the live session; process restoration and source reconnection must not implicitly start playback.
- Persisted preference keys, serialized library payloads and versioned playback checkpoints are compatibility boundaries. Provide explicit migration/backward decoding for format changes; preserve transactions, recovery generations and reset invalidation rather than silently rebuilding saved data.
- Preserve the landscape 50/50 layout, proportional reference-grid scaling, bundled fonts and supplied assets. Use the shared transition and control implementations.

## Building and testing

Use the checked-in Gradle wrapper, an Android SDK with Platform 35 and a JDK compatible with the checked-in Gradle/AGP versions. Java source/target 17 does not itself specify the Gradle runtime JDK. Configure the SDK through `ANDROID_HOME` or local `local.properties`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug :app:verifyDebugCompatibility
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Connected tests require an emulator/device.
- Place each new release APK in a new directory under `/home/ismail/Projects/vibe-coded-projects/Clocarmus/build/releases/`, named for its version (for example, `build/releases/1.0.4/`). Put the signed APK and its SHA-256 checksum there; use a unique suffix if that version's directory already exists so previous releases are preserved.
- Add focused regression tests for behavioral fixes. JVM tests in `app/src/test/` use JUnit 4 and coroutine test utilities. Android-dependent tests in `app/src/androidTest/` use platform `InstrumentationTestCase` and `android.test.InstrumentationTestRunner`; follow their isolated database/preferences/files setup.
- For storage, decoder, service or compatibility changes, exercise API 23 and an appropriate newer Android runtime. Run relevant checks, report failures/skips and distinguish emulator observations from physical head-unit validation.
- For visual changes, build first, start `headunit_a6`, then run `python3 scripts/compare-main-player.py --screen player --install` (choose the affected screen). Requires `adb` and Pillow. Comparisons are visual aids, not pixel acceptance thresholds. Relaunch without preview extras afterward.
- Regenerate assets with the existing `scripts/import-*.py` tools; see `design/ASSETS.md` for prerequisites and provenance. Normal builds need no Inkscape. Keep local configuration, signing secrets, music fixtures and ignored build artifacts out of commits.

## Focused references

- [Compatibility, persistence and runtime limits](docs/compatibility-and-contracts.md)
- [Genuine Shuffle contracts, measurements and acceptance limits](docs/genuine-shuffle.md)
- [Navigation transitions](docs/ui-transitions.md)
- [Asset import workflow and licenses](design/ASSETS.md)

Update relevant documentation when behavior or contracts change; keep this file brief and link to details.
