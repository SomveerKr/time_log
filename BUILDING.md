# Building Time Log

This is the T01 visual shell, not the finished time tracker. Entry is disabled
until T03 connects validated local persistence. Reminder delivery and backups
are implemented in later tasks. `PROGRESS.md` records the current gate.

## Toolchain

Use JDK 17 and Android SDK platform 37 with build tools 37.0.0. Set `JAVA_HOME`
and `ANDROID_HOME` for the current shell, or configure your IDE with these tools.
Store machine-specific SDK paths in the ignored `local.properties` file.
The wrapper downloads Gradle 9.6.0 and checks its pinned SHA-256 digest.

The receiving environment's tools and caches are in the ignored `.toolchain`
folder. They are not part of the source repository. No global environment or
Git settings are needed. Keep build caches in this folder when using these tools.

Pinned app versions: AGP 9.4.0 with built-in Kotlin 2.2.10 and matching Compose
compiler, Compose BOM 2026.09.00, and Activity Compose 1.13.0. The app compiles
and targets Android 17 / API 37 and supports API
26 or newer. Package ID: `com.timelog.app`. Room is introduced in T02.

Compatibility references: [Android Gradle Plugin 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk),
[Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
and [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## Checks

From the project root in a shell configured with the toolchain:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

The app configuration and dependencies are resolved during this build. Lint
reports are under `app/build/reports`. The test APK compilation does not run UI
tests. With a connected, authorized USB-debugging Android phone or emulator:

```powershell
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:installDebug
adb shell am start -n com.timelog.app/.MainActivity
```

The navigation test checks empty states, the disabled T01 entry action,
Settings/back navigation, and recreation retaining the selected destination.
Manually inspect Today, Insights, and Settings in both system themes. Record
actual checks and any screenshots in progress; do not infer a visual pass from
compilation or the UI test.

The instrumentation runner removes its app installation after tests. Use a
dedicated emulator with synthetic data for subsequent test runs so phone
history is preserved. Select that emulator in the current process with
`$env:ANDROID_SERIAL = adb -e get-serialno` before running instrumentation,
then remove that temporary variable with `Remove-Item Env:ANDROID_SERIAL`.
Reinstall the debug APK before manual visual checks.

The debug app APK is `app/build/outputs/apk/debug/app-debug.apk`. Build outputs,
local tools, device credentials, signing files, and personal backups must stay
outside commits. There is no production signing or publishing setup.
