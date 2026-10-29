# Clocarmus

Minimal Android app in Kotlin and Jetpack Compose with a blank Material 3 screen, light/dark themes, dynamic colors, and a Compose preview.

- Package: `com.example.clocarmus`
- Minimum Android: 6.0 (API 23)
- Compile/target SDK: 35

The app name is `Clocarmus` and the application ID and namespace are `com.example.clocarmus`.

## Run

Open this directory in Android Studio, let Gradle sync, select an emulator or device, and click Run.

For command-line builds, use JDK 17 or newer (Gradle 9.4.1 supports up to JDK 26), install Android SDK Platform 35 and Build Tools 36.0.0, and set `ANDROID_HOME` or configure `sdk.dir` in the ignored `local.properties` file.

```sh
./gradlew assembleDebug
./gradlew installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Start adding UI inside `App()` in `app/src/main/java/com/example/clocarmus/MainActivity.kt`. Theme defaults are in the adjacent `ui/theme/` directory.

Generated `.gradle/`, `.kotlin/`, and `build/` directories are ignored and can be deleted; Gradle recreates them. Keep the Gradle wrapper, build scripts, manifest, and launcher resources. `local.properties` holds the local Android SDK path and should stay untracked.
