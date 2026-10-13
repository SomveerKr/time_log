# Saved implementation progress

Last updated: 2026-10-13, Asia/Kolkata

## Current checkpoint

- Project state: T01 complete; Android 17-targeted shell verified on Android 14 phone and Android 17 emulator.
- Current task: none.
- Next task: T01.
- Exact next action: verify the T01 commit, then start T02 and inspect stable Room/KSP compatibility plus SPEC sections 5 and 7.
- Current blocker: none; phone is connected and authorized.
- Last verified implementation commit: none yet; `chore(T01): establish Android project and visual shell` pending commit verification.
- Uncommitted app changes: completed T01 source, task documentation, and verification evidence ready for the task commit.
- Ponytail: available in this Codex skill catalog; installed instructions read from the discovered user skill path and applied at full level.

## Task ledger

| Task | Status | Evidence or remaining work |
| --- | --- | --- |
| T01 Foundation and visual shell | done | Build/lint, two-version UI test, phone light/dark review, and scoped security audit passed. See `docs/verification/t01/README.md`. |
| T02 Local records and categories | not_started | |
| T03 Fast entry and history | not_started | |
| T04 Reminder rules and settings | not_started | |
| T05 Android notification delivery | not_started | |
| T06 Daily and weekly insights | not_started | |
| T07 Backup export | not_started | |
| T08 Safe backup restore | not_started | |
| T09 Design and accessibility review | not_started | |
| T10 Device validation and delivery | not_started | |

Allowed statuses: `not_started`, `in_progress`, `blocked`, `done`. At most one task may be `in_progress` or `blocked`.

## Active task details

- Objective: T01 complete; commit and verify before starting T02.
- Completed steps: verified root and handoff; loaded Ponytail full; initialized codex branch and authorized local Git author; installed verified tools locally; created and verified single-module Compose shell with no fake records or reminder delivery.
- Files touched: `PROGRESS.md`, `.gitignore`, `.gitattributes`, root/app Gradle files, and `app/src/main` plus `app/src/androidTest`.
- Checks run and results: root/Git inspection passed; official compatibility checked; vendor SHA-256 checks passed for all three tool archives; wrapper generation and `gradlew.bat --version` passed (Gradle 8.11.1 / Microsoft JDK 17.0.20.1); SDK 35, build tools 35.0.0 and platform-tools installed; XML parsing/string references and six ignore-rule probes passed; adb device list empty. App build/lint/device tests not yet run.
- Checks still required: T01 commit verification. Subsequent app behavior and release checks remain in their assigned tasks.
- Known issues: default sandbox command setup fails; scoped elevated commands work. Use per-command safe.directory for the sandbox identity. PowerShell scripts are disabled, so supply toolchain environment only to each process; no policy changes. Log time is disabled until T03 connects persistence.
- Latest check: final security-cleaned debug build/lint/two-device test passed in 45 s. Phone: 1 passed, 9.120 s; emulator: 1 passed, 6.627 s; zero failures/errors/skips. Lint: 0 errors, 1 advisory newer-Gradle warning. Final merged manifest has only the launcher and DUMP-protected profile receiver exported, with no network/broad storage permissions. Final 16-KB APK alignment and diff checks passed. Six phone screenshots visually reviewed; original theme restored. Evidence saved under `docs/verification/t01`.
- Next concrete action: commit T01 using its specified subject, verify SHA/content and working tree, then save T02 start checkpoint before app edits.

## Decisions and environment

- Product name: Time Log, a working name.
- Scope: the version 1 requirements in `SPEC.md`.
- Selected T01 versions after user steering: AGP 9.4.0, Gradle 9.6.0, built-in Kotlin/Compose compiler 2.2.10 (verified in official AGP Maven metadata), Compose BOM 2026.09.00, Activity Compose 1.13.0, compile/target Android 17 / API 37, build tools 37.0.0, minimum SDK 26, Microsoft JDK 17.0.20.1 and Java bytecode 17. SDK 37.0 is the stable major platform package; no preview SDK is selected. Room is reserved for T02. Upgraded build checks remain pending.
- Package ID and name: `com.timelog.app`, Time Log.
- Project root and branch: `E:\Time Log`, `codex/t01-foundation` (unborn; no commits yet).
- Git identity: supplied by user and configured locally; global configuration unchanged.
- Phone: Samsung SM-A042F, Android 14 / API 34; USB debugging authorized and app/test launch verified. Local emulator: API 37.0 Google APIs x86_64 16-KB image, test results pending.
- Instruction updates: latest Android support added to SPEC platform row/T01 scope; user reiterated prompt task commits after tests and security audit, now explicit in AGENTS.md and the task cycle.

## Checkpoint history

- 2026-10-13: Specification and implementation instructions prepared. No app tests or implementation commits performed.
- 2026-10-13: T01 checks/security audit passed; `chore(T01): establish Android project and visual shell` pending commit verification. Android 14 phone and Android 17 emulator UI tests passed; six light/dark screenshots reviewed.

Keep the active checkpoint concise. Add one short history line per completed task with its unique commit subject and test outcome. Resolve commit hashes from Git as described in `IMPLEMENTATION.md`; never invent a hash.
