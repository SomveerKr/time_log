# Time Log Android app

Time Log is an offline Android app for recording time quickly and remembering to check in. It includes editable time history, custom categories, daily/weekly charts, local reminders and versioned JSON backup/restore. Android 8.0 / API 26 or newer; compile/target API 37. No account, analytics or app network permission.

T01-T10 are complete with the documented physical-device checks, including the user-approved temporary 10-minute reminder test. T11 adds a signed, optimized release APK; see the [release audit](docs/verification/t11.md) for measurements and limits. See [progress](PROGRESS.md), [build/install instructions](BUILDING.md) and [verification evidence](docs/verification/).

## Download

Download the latest APK directly from GitHub:

**[Download Time Log APK](https://github.com/SomveerKr/time_log/raw/main/release/time%20log.apk)**

Or copy this URL into your browser:
```
https://github.com/SomveerKr/time_log/raw/main/release/time%20log.apk
```

1. Tap the link above on your Android phone (or transfer the downloaded file to your phone).
2. Open the downloaded `time log.apk` file.
3. If prompted, allow installation from unknown sources for your browser or file manager.
4. Tap **Install** and then **Open**.

> Requires Android 8.0 (API 26) or newer. No account, network or special permissions needed.

## Install and use

Share `app/build/outputs/apk/release/app-release.apk` (version 1.0.0) as a document or by USB. On an Android 8.0+ phone, open the file, allow installation from that source if prompted, and install. The release is signed with a private release key, optimized and not debuggable.

If the old debug app is installed, this release cannot update it because the signatures differ. Export a backup from the old app, verify the file is readable and keep it safely outside app storage before any manual uninstall. Then install the release, restore the backup and re-enable reminders. Uninstalling without a backup loses local data. Future releases signed with this same release key can update in place.

For a new installation or an update of the same release-signed app using ADB:

```powershell
adb -d install -r app/build/outputs/apk/release/app-release.apk
adb -d shell am start -n com.timelog.app/.MainActivity
```

Choose tracking hours and a 60/120-minute interval, or select Set up later. Today > Log time opens the recent unlogged interval: check its range, select a category, then Save. Add a note or split only when needed. Tap a saved entry to edit, split or delete with confirmation. Use the date arrows/picker for earlier days. Insights shows daily/weekly durations and category shares. Settings contains reminders, categories and backups.

## Backup and restore

Settings > Export backup uses the system file picker. Backups are unencrypted JSON containing all categories, entries and notes; choose a private location. A document provider can be cloud-backed even though Time Log itself performs no sync. Keep a copy outside app-private storage; uninstalling the app removes its local history.

Settings > Restore backup validates a selected file before changing data. Inspect the backup date and counts. Export current data first if needed, or Cancel. Restore and replace replaces all local history and categories; it does not merge. Restored reminders remain off until you explicitly enable them. Invalid or incomplete files and failed database replacements preserve existing data and reminders. Supported input: version 1, up to 20 MiB and 100,000 entries. See the [backup contract](docs/BACKUP_FORMAT.md).

## Reminder troubleshooting and limits

- Enable reminders in Settings and allow notifications. If the status says blocked, check the app's notification/channel settings. Send test reminder checks notification delivery without waiting for a scheduled hour.
- Allow precise reminders is optional. Without that access, Android may deliver alarms late; the app shows that timing may vary. Battery saving and manufacturer restrictions can also delay or prevent delivery.
- A reminder opens its remaining unlogged block. Fully logged blocks need no reminder. Snooze is 15 minutes and is unavailable when it would reach the next regular reminder or exceed the selected window.
- Delivery after the window-end instant is discarded. The user selected this strict cutoff, so an end-of-window alarm arriving even milliseconds late can be suppressed. No overdue burst is posted on reopening.
- Force-stop prevents Android from delivering reminders until you reopen Time Log. After reopening, future scheduling resumes. A normal app close is different from force-stop; Android/OEM restrictions still apply.
- Unlock the phone once after a reboot so its encrypted local data becomes available. Reopen Time Log if reminders do not resume.
- Timezone changes can move historical intervals between report dates. Records retain their actual UTC instants. Reminder windows must start and end on the same day.

Physical-phone offline entry/edit/delete, backup round trip, notification tap, force-stop/reopen recovery, two consecutive 10-minute reminders, quiet cutoff, snooze rejection and user-confirmed TalkBack speech/gestures passed. The normal APK and original settings were restored and all portable data compared equal afterward. With 10,000 isolated synthetic records, save took 37 ms and query/report calculation about 1.05 seconds. See [T10 evidence and limits](docs/verification/t10.md). Normal hourly/overnight reliability and accepted 15-minute snooze delivery were not observed on this phone. The signed release follows in T11; store publishing remains outside scope.

## Use these files in VS Code

1. Extract or copy this folder into the dedicated Android project directory. Keep `AGENTS.md` at the project root, beside the other four files. Do not put the files only in a chat attachment.
2. Open that directory as the VS Code workspace. Start Codex there so its project instructions apply.
3. Send the starting prompt below. Codex should work through one task at a time, save progress, test, and commit before starting the next task.
4. In later sessions, send the resume prompt. The files preserve context; they do not launch Codex or start background work when VS Code opens.

| File | Purpose |
| --- | --- |
| `SPEC.md` | Product behavior, minimalist visual design, data rules, architecture, and acceptance criteria. |
| `IMPLEMENTATION.md` | Ordered tasks, tests, commit rules, and interruption recovery. |
| `AGENTS.md` | Instructions Codex should load at the start of work in this project. |
| `PROGRESS.md` | Current task, completed work, test evidence, blockers, and exact next action. |

## Starting prompt

```text
Build the Android app described in SPEC.md. First read AGENTS.md,
PROGRESS.md, and IMPLEMENTATION.md. Use the installed Ponytail skill at
full level. Begin with the first unfinished task and follow the task order.
Implement one task, test it, save progress, and commit it before moving
to the next. Continue while the required checks are possible. Preserve
unrelated changes. Record any blocker and the exact resume action.
```

## Resume prompt

```text
Continue this project from PROGRESS.md. Follow AGENTS.md and Ponytail
full. Reconcile saved progress with the working tree and Git history,
then resume the unfinished task. Test and commit each task before the next.
```

## Ponytail verification

Ponytail was verified as installed and enabled at user scope on the originating Mac on 13 October 2026:

- Codex configuration: `~/.codex/config.toml`, section `[plugins."ponytail@ponytail"]`, with `enabled = true`.
- Installed skill read successfully: `~/.codex/plugins/cache/ponytail/ponytail/5.1.0/skills/ponytail/SKILL.md`.
- The skill is active in the originating Codex session.

The receiving Windows Codex environment also lists Ponytail as an installed skill; its current instructions were read and applied at full level. This is a user-level installation, not a copy inside this project. A different environment must verify its own installation. Do not claim a measured token saving: Ponytail keeps work focused and avoids unnecessary code and context.

Codex discovers project `AGENTS.md` instructions at session startup. Local plugin choices can be saved in the user configuration. See [Codex project instructions](https://learn.chatgpt.com/docs/agent-configuration/agents-md) and [plugin configuration](https://developers.openai.com/plugins/build/plugins).

## Scope of this delivery

The initial handoff contained documentation only. T01-T10 now provide the single-module Android app, tested local persistence, reminder delivery, charts, backup/restore, accessibility refinements and debug-build handoff. The old Google Form and Google Sheet are outside this app project; do not change or import them automatically.
