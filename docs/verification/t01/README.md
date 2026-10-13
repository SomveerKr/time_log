# T01 verification

Date: 2026-10-13, Asia/Kolkata. Scope: Android foundation and visual shell.

- Gradle configuration/dependency resolution: `gradlew.bat help` and debug build passed using the pinned wrapper and JDK 17.
- Final command: `gradlew.bat :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest`, successful in 45 s.
- Lint: 0 errors, 1 advisory warning about a newer Gradle. Gradle 9.6.0 is the documented compatible version for AGP 9.4.0.
- Physical phone: Samsung SM-A042F, Android 14 / API 34. Navigation/recreation test: 1 passed, 0 failures/errors/skips, 9.120 s.
- Emulator: Android 17 / API 37, Google APIs x86_64, 16-KB memory pages, existing WHPX acceleration. Same test: 1 passed, 0 failures/errors/skips, 6.627 s.
- Manual app launches succeeded on both environments. Final APK passed `zipalign -c -P 16 4`.
- Inspected the six phone screenshots below. Titles, empty states, settings values, green selection, scrolling layout, and system insets are coherent in both themes. Native back returned Settings to the previous Insights destination. Original phone theme was restored.

Screens: [Today light](today-light.png), [Today dark](today-dark.png),
[Insights light](insights-light.png), [Insights dark](insights-dark.png),
[Settings light](settings-light.png), [Settings dark](settings-dark.png).

## Scoped security audit

Reviewed source/diff, dependency pins, generated wrapper, merged app manifest,
backup exclusions, and ignored private artifacts. No internet or broad storage
permission. OS backup and transfer exclusions cover the app storage domains.
Unused preview/test-manifest dependencies were removed because they exported
unnecessary debug activities. The resulting exported components are the
launcher activity and AndroidX's profile receiver protected by the system DUMP
permission. Tools, SDK paths, build outputs, databases, backups, and keys are
excluded from the commit. Official tool archives passed vendor SHA-256 checks.

The app contains no saved records or live reminder behavior. Logging is disabled
until T03. Persistence/reminders/backups, API-26 runtime, large text, TalkBack,
keyboard, and long-running delivery checks remain in subsequent tasks.
