# Progressive implementation and session recovery guide

Build the app in ten ordered tasks. Each task must produce a working increment, pass its required checks, update `PROGRESS.md`, and be committed before the next task starts. Do not implement the whole app in one batch and test afterward.

## 1. The task cycle

1. **Recover context.** Follow `AGENTS.md`. Inspect the working tree and recent Git history. Read the active task and its specification sections.
2. **Start the task.** Set its ledger status to `in_progress`. Write the objective, initial files to inspect, and exact next action in `PROGRESS.md` before editing app code.
3. **Implement the scope.** Use Ponytail full. Keep the change cohesive. Do not add features reserved for later tasks.
4. **Check the result.** Run the task's meaningful tests, build checks, and UI checks. Fix failures caused by the change. Record commands, outcome, and environment honestly.
5. **Review the diff and audit security.** Inspect changed behavior, data handling, generated files, dependencies, and accidental unrelated edits. Check permissions, exported components, input/data-loss boundaries, private storage, and accidental keys or personal files as relevant to the task. Record findings and fix them before committing. Review visible UI when a task changes it. Do not defer a verified task commit for optional checks belonging to later tasks.
6. **Save the completion checkpoint.** Set the task to `done` only after its required checks pass. Record test evidence, remaining release checks, the unique commit subject, and the next task. Set the current task to none. Do not start the next task yet.
7. **Commit.** Stage only this task's changes and progress. Make the local commit with the specified subject, verify it exists, and inspect the remaining working tree. Only then may the next task begin.

If a task's required check cannot run, mark it `blocked`, state what is missing, and leave its completion gate open. Do not substitute "looks correct" for a test or advance to dependent work. Tests explicitly assigned to T10 are release checks, not hidden prerequisites for earlier tasks.

## 2. Progress and Git without conflicting state

`PROGRESS.md` is the compact working checkpoint; Git is the committed history. Save progress after a meaningful substep, a test run, a decision, or a blocker, and before ending a session. Do not rely on chat memory, an unsaved plan, or a conversation summary.

During a task, record the latest previously verified commit SHA. In the task's completion checkpoint, record its unique commit subject with "commit pending verification". The new commit will include this checkpoint; it cannot contain its own SHA. After the commit, verify its subject and content. At the start of the next task, record that SHA in the new progress update. For the final task, resolve the SHA from Git in the delivery response; a separate progress-only commit is optional, not required.

On a new session:

- If progress says `in_progress`, inspect its files and diff, then resume the exact next action.
- If it says `done` with a pending commit, find the matching commit and inspect its files. If absent, review and commit the completed task before moving on.
- If a commit already finished the task but progress is stale, reconcile the checkpoint using the commit and relevant test evidence. Do not reimplement it.
- If the working tree contains unfinished work after an interruption, preserve it. Identify which changes belong to the active task; never run a blanket reset or clean.
- If the recorded blocker is gone, return the task to `in_progress` and continue. If still blocked, report the missing prerequisite and the recovery action.
- On unexpected test failure after a completed task, repair the failing task in a focused fix commit and update progress before proceeding.

Initialize Git only in the intended dedicated project root when no suitable repository exists. Do not accidentally make the user's broader documents folder the app repository. Use a `codex/` branch when creating a branch. Do not change signing settings, global Git identity, remote configuration, or publish the repository just to satisfy a checkpoint.

## 3. Task order and completion gates

### T01 Foundation and visual shell

**Scope:** Inspect the environment, locate JDK/Android SDK/emulator support, create a reproducible single-module Kotlin/Compose project with a Gradle wrapper, and record pinned compatible versions using the latest verified stable compile/target Android SDK while retaining minimum API 26. Establish package ID and working app name. Add the theme tokens, Today/Insights navigation, Settings destination, and coherent empty states from SPEC sections 3-4. Add no fake saved records or live reminder behavior.

**Required checks:** Gradle sync, debug build, lint, and app launch on an emulator or connected phone. Inspect the shell in light/dark themes and save concise evidence. Verify `.gitignore` excludes build outputs, `local.properties`, keystores, and personal files. Missing toolchain or runnable device is a blocker to this task's gate.

**Commit:** `chore(T01): establish Android project and visual shell`

### T02 Local records and categories

**Scope:** Implement Room entities/DAOs/settings, stable identifiers, starter categories, normalization, foreign keys, indexes, and transactional entry validation. Implement create/rename/archive category operations and save/edit/delete/split data operations. Add migration infrastructure and schema export. Keep tests synthetic. See SPEC sections 5 and 7.

**Required checks:** Database tests for persistence after reopening, duplicate categories, archived references, invalid dates/durations, adjacent versus overlapping intervals, split rollback, edit exclusion, foreign keys, and concurrent conflicting saves. Check schema export, build, and relevant lint. Test persistence using an Android-backed Room test environment rather than only mocked collections.

**Commit:** `feat(T02): persist time entries and custom categories`

### T03 Fast entry and history

**Scope:** Connect Today, date navigation, entry sheet, inline category creation, optional notes, edit/split/delete, draft state, and gap filling to local data. Implement elapsed Today coverage correctly. No historical completeness score. See SPEC sections 3, 5, and 8.

**Required checks:** UI tests for category-to-save flow, new category appearing next time, correct selected date/range, validation, double-save protection, edit, split, and deletion confirmation. Manually check keyboard/insets and rotation draft retention. Verify saved records survive process recreation. Exercise airplane-mode entry. Test gap calculation with existing, partial, outside-window, and future time.

**Commit:** `feat(T03): add quick time entry and editable history`

### T04 Reminder rules and settings

**Scope:** Implement pure next-boundary/block calculations and settings persistence for 60/120 minutes, selected same-day window, first/final partial intervals, local timezone behavior, schedule generations, snooze rules, and invalid settings. Add setup preview and reminder settings UI. Keep alarm-delivery wiring for T05. See SPEC section 6.

**Required checks:** Deterministic tests using injected time: before/at/after boundaries, end of day, short window, partial final block, invalid overnight/equal window, interval edits, timezone/DST changes, stale event, superseded snooze, and no past-notification burst. Verify settings survive reopening and preview matches computed schedule. Build and inspect settings UI.

**Commit:** `feat(T04): define reminder schedule and configurable hours`

### T05 Android notification delivery

**Scope:** Connect AlarmManager and receivers, notification channel and permission flow, exact-access check with fallback, notification deep link, snooze, reboot/timezone/time/update reconciliation, duplicate suppression, and test-reminder action. Show honest status when delivery is blocked or approximate. See SPEC section 6.

**Required checks:** Automated tests for event idempotency, stale events, partial/fully logged blocks, cancellation, and scheduling reconciliation. On emulator or phone verify permission grant/denial/revocation, notification tap with app closed, snooze, quiet hours, schedule change, reboot, timezone change, and a Doze exercise. Log OS version and observed versus intended behavior. Test inexact fallback; do not change release timing just to shorten a test. Physical overnight reliability remains T10.

**Commit:** `feat(T05): deliver local reminders and snooze actions`

### T06 Daily and weekly insights

**Scope:** Add date/week navigation, aggregation, daily category bars, seven daily weekly bars, weekly category totals, explicit durations/shares, and accessible data values. Use simple Compose primitives rather than a new chart dependency unless required and justified. See SPEC section 8.

**Required checks:** Assert the reference fixtures plus empty data, midnight, Monday boundary, DST, archive/rename effects, stable sorting, and equality of daily sums and weekly totals. Inspect labels in both themes with large text. Confirm changes to entries update charts. Use a 10,000-entry synthetic dataset to find obvious performance issues and record actual observations.

**Commit:** `feat(T06): add daily and weekly time charts`

### T07 Backup export

**Scope:** Define and document the versioned JSON contract, consistent snapshot serialization, system create-document flow, cancellation/failure handling, and last-success time. Configure and inspect private storage and OS backup exclusions. Add the brief plaintext backup explanation. See SPEC sections 7 and 9.

**Required checks:** Snapshot consistency and serialization tests for all portable fields, unicode names, empty data, and archived categories. Verify exported file contents with synthetic data. Test canceled picker and failed/partial write without false success or local changes. Inspect merged manifest and backup XML. No broad storage or internet permission.

**Commit:** `feat(T07): export versioned local backups`

### T08 Safe backup restore

**Scope:** Parse and validate backups, preview counts, offer current export, require replace confirmation, replace transactionally, reset device reminder state, and keep restored reminders off. Add only supported format migration handling; reject unsupported versions clearly. See SPEC section 9.

**Required checks:** Round-trip full data equivalence with the documented enablement exception; corrupt/truncated/oversized input; unknown versions; duplicate names/IDs; missing category; invalid setting/time; overlapping records; canceled restore; failed transaction; repeated restore; restart after replacement. Verify failed restore preserves existing data and schedule. Test permission state on a fresh installation.

**Commit:** `feat(T08): restore backups with validation and rollback`

### T09 Design and accessibility review

**Scope:** Review the integrated app and refine spacing, typography, category controls, empty/error/loading states, chart labels, and navigation. Preserve the minimalist design. Resolve visual issues; do not add another product feature. See SPEC sections 3-4.

**Required checks:** Small phone width around 360 dp, light/dark mode, 200% font scaling, TalkBack, keyboard/insets, contrast measurement, touch targets, long category names, and landscape/recreation stability. Save representative screenshots of Today, entry, Insights, and Settings using synthetic data. Measure the ordinary reminder-to-save path and record observations. Re-run checks affected by refinements.

**Commit:** `refactor(T09): refine minimal UI and accessibility`

### T10 Device validation and delivery

**Scope:** Validate the finished flow on the user's Android phone or an explicitly documented representative physical Android phone. Produce a debug APK and build/install/use guide, backup instructions, reminder troubleshooting, and known limitations. Leave release signing and store publishing outside scope.

**Required checks:** Full unit suite, lint, debug build, relevant instrumented/UI tests; real-phone reminder checks with screen off, app closed normally, a restart, selected hours, snooze, and battery-saving state. Observe at least two consecutive real scheduled reminders and a quiet-hours boundary. Verify local entry and backup restore offline. Test force-stop recovery after reopening and describe the platform limitation honestly. Do not mark device validation complete without physical-device evidence.

**Commit:** `chore(T10): validate Android build and document delivery`

If phone access or user observations are unavailable, preserve the completed build and test results, mark T10 blocked, and state exactly which device checks remain. Do not fabricate a device pass or call the app fully validated.

## 4. Test evidence format

In the active progress checkpoint, record each meaningful check as:

```text
Check: <what behavior this verifies>
Command or procedure: <reproducible command or UI steps>
Environment: <JDK/SDK/emulator API or phone model and Android version>
Result: passed / failed / blocked / not run
Evidence: <short result, report path, or screenshot path>
```

Use normal Gradle wrapper tasks after the project is created, typically `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew assembleDebug`, and `./gradlew connectedDebugAndroidTest`; use `gradlew.bat` on Windows. Confirm available tasks in the actual project. Do not run unavailable commands blindly or repeat the entire suite after a docs-only change.

## 5. Completion response and next-session handoff

At a stopping point, state the task completed or blocked, the tests actually run, its verified commit hash if committed, and the exact next task/action. End with untested behavior and relevant risk. Keep the response short; the detailed state belongs in `PROGRESS.md`.

Reading `AGENTS.md` can restore project instructions in a new Codex session. It does not wake an idle extension or grant perpetual execution. The user starts or resumes a chat; the agent should then use the saved checkpoint without asking the user to reconstruct the project history.
