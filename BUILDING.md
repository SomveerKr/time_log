# Building Time Log

This debug test build includes local entry/history/categories, Today gaps,
reminders, daily/weekly charts and JSON export/restore. T01-T10 are complete;
`docs/verification/t10.md` records the physical checks and their limits.

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
26 or newer. Package ID: `com.timelog.app`. Room 2.8.5 with KSP 2.3.12 stores
local data; exported schemas 1, 2 and 3 are under `app/schemas`; explicit migrations
preserve existing history. Lifecycle ViewModel and
SavedState use 2.9.4, matching the existing Activity dependency graph.

Compatibility references: [Android Gradle Plugin 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk),
[Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
and [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## Checks

From the project root in a shell configured with the toolchain:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:testDebugUnitTest
```

The app configuration and dependencies are resolved during this build. Lint
reports are under `app/build/reports`. The test APK compilation does not run UI
tests. Run instrumentation only on a dedicated emulator with synthetic data:

```powershell
adb devices
$env:ANDROID_SERIAL = (adb -e get-serialno).Trim()
.\gradlew.bat :app:connectedDebugAndroidTest
Remove-Item Env:ANDROID_SERIAL
```

Install the debug app on an authorized USB-debugging phone without clearing data:

```powershell
adb -d install -r app/build/outputs/apk/debug/app-debug.apk
adb -d shell am start -n com.timelog.app/.MainActivity
```

Tests cover entry/category save, validation and retry, double taps, edit/split,
delete confirmation, draft recreation, navigation, actual SQLite transactions,
gap/day/chart calculations, timezone/DST reminder rules, delivery reconciliation,
backup serialization/validation/rollback, accessible layout, setup and migrations.
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

Do not uninstall or clear storage on a phone containing real history. If an
update fails because a different signing certificate was used, export a backup
before considering removal; replacing the existing installation can erase data.
Internet is needed to download the toolchain/dependencies on a fresh computer;
the built app itself works offline. On macOS/Linux use `./gradlew` with the same
tasks. See README for normal use, backup/restore and reminder limitations.
