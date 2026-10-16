# Time Log Android requirements and design specification

Version 1.0 | 13 October 2026 | Status: ready for incremental implementation

## 1. Purpose and priorities

Build a personal Android app that helps the user remember and record how they spent their time. The previous Google Form approach failed because opening and filling it was easy to forget. The app must combine scheduled reminders with a very short entry flow.

Priority order: reminder and entry usability, correct local persistence, safe backup and restore, useful daily and weekly charts, visual refinement throughout. All are required for version 1; the order controls implementation.

Success means a returning user can tap a notification, select a category, and save a normal entry in about 10 seconds, with no keyboard required. Treat this as a usability target to measure on the user's phone, not a guaranteed timing claim.

## 2. Scope and default decisions

| Area | Version 1 decision |
| --- | --- |
| Platform | Native Android, phone first; minimum Android 8 / API 26. Compile and target the latest stable Android SDK verified during implementation, regardless of the connected phone's older OS. Confirm the user's phone supports the minimum before delivery. |
| Entry | Retrospective time blocks, one category per block, optional note. Edit, split, and delete available. |
| Reminders | Every 60 or 120 minutes, daily, within a selected same-day tracking window. Default 09:00 to 22:00, every 60 minutes; user confirms during setup. |
| Categories | Small starter set plus user-created categories; rename and archive. |
| Persistence | App-private local database; all core features work without internet. |
| Backup | User-initiated export and restore of a versioned JSON file. |
| Insights | Daily category bars, weekly totals by day, weekly category bars, and text values. |
| Design | Quiet, spacious, minimal, readable, light and dark themes following system settings. |
| Week | Monday through Sunday. |

No login, server, cloud sync, ads, analytics, automatic activity tracking, AI classification, social features, goals, gamification, calendar integration, or Google Form migration. No overnight reminder window, weekday schedule builder, widget, CSV export, automated backup scheduler, or custom chart library in this version. These are scope boundaries, not missing implementation tasks.

## 3. Screens and navigation

Use two bottom destinations: **Today** and **Insights**. A settings icon in the top app bar opens Settings. Category management and backup live in Settings. Opening a reminder goes directly to the entry sheet over Today.

### First use

Show one compact setup screen: tracking start, tracking end, interval, and a preview such as "First check-in 10:00, last check-in 22:00". Primary action: "Enable reminders". Secondary text action: "Set up later".

Explain notification permission only when the user enables reminders. Denial leaves the app usable and shows a quiet settings status. Do not request storage, contacts, location, microphone, or unrelated permissions.

### Today

- Header: Today and the date; tapping the date permits reviewing or filling an earlier day.
- Small status line: next reminder, reminders off, or notifications blocked.
- One main action: "Log time".
- A compact summary: logged time for the displayed day.
- For today only, show elapsed unlogged time inside the selected tracking window and a "Fill a gap" action. Future hours are never counted as missing.
- Chronological entry list: time range, category, duration; show a note only when present.
- Tapping an entry opens editing. Empty state: "Your day starts here. Add your first time block."

Historical days show recorded totals and entries. Do not claim historical completeness using today's reminder settings. Users can add historical records with the date/time picker.

### Entry sheet

The compact sheet contains the time range, category choices, optional collapsed note, and one full-width "Save" button. Existing categories should be selectable without a keyboard. Include "+ New category" beside the choices. Do not preselect a category that could cause accidental wrong classification.

Default from a reminder: the exact block associated with that reminder. Default from "Log time": the most recent unlogged portion of the preceding reminder interval, capped at the current time. If that interval is fully logged, ask the user to choose a range; never silently overlap a record or turn several missed hours into one huge block.

Time fields are editable. Date and time controls must respect the phone's 12/24-hour preference. A selected historical date defaults to an unlogged interval on that day, not to today's timestamps.

"Split this block" adds another category and duration within the same range. The pieces are consecutive, positive, and must add up exactly to the range duration. Save all pieces in one database transaction. Keep this secondary action out of the ordinary one-category flow.

After save, show a brief confirmation and update Today immediately. Preserve an unsaved draft across rotation and ordinary backgrounding using saved UI state. Prevent double taps while a save is running. Editing keeps the same record identity. Ask before deletion; an Undo action may be added only if it remains simple and reliable.

### Settings

Group only the essential items: reminder switch/window/interval, reminder permission status, categories, export backup, restore backup, last successful backup time, and a short local-data explanation. Include "Send test reminder" so the user can check delivery without waiting an hour. Follow system theme; a separate theme picker is unnecessary.

## 4. Minimal visual design

The desired appearance is calm and finished, with strong typography, generous spacing, and one accent. Use native Material 3 controls with a restrained theme. Avoid dashboards packed with cards, decorative gradients, heavy shadows, colorful backgrounds, tiny labels, and illustration-heavy onboarding.

Suggested tokens are implementation defaults, not a requirement to build a separate design framework:

| Token | Light | Dark |
| --- | --- | --- |
| Background | `#F7F8F5` | `#111714` |
| Surface | `#FFFFFF` | `#1B241F` |
| Primary text | `#17231D` | `#E8EFEA` |
| Secondary text | `#56645B` | `#AFBEB4` |
| Accent | `#286747` | `#91D6AE` |
| On accent | `#FFFFFF` | `#102B1D` |
| Divider | `#DDE5DE` | `#34463A` |
| Error | `#B3261E` | `#FFB4AB` |

- Use the system sans-serif font. Start with 28 sp screen titles, 20 sp section titles, 16 sp body text, and 14 sp secondary text. Let font scaling work.
- Use a 4 dp spacing grid, usually 20 dp screen padding, 16 dp between sections, and 8 dp between related elements. Keep repeated geometry consistent.
- Use roughly 16 dp corner radii and restrained borders. Primary actions are visually distinct and easy to reach.
- All interactive targets are at least 48 dp. Support TalkBack, 200% font scaling, dark mode, keyboard visibility, and system navigation insets.
- Verify normal text contrast at least 4.5:1 and meaningful graphical controls at least 3:1. Tokens are starting values; measure actual combinations.
- Categories use short text labels with a small stable color marker. Never communicate category, error, or selection through color alone.
- Charts use direct labels and values; no 3D effects, dense legends, unlabeled arcs, or animation needed to understand them. Horizontal bars are preferred to pie charts.

Conceptual layout, not fixed pixel positioning:

```text
Today                         Settings
Tuesday, 13 October
Next check-in at 15:00

[ Log time                         ]

3 h 30 m logged
1 h unlogged in today's window   Fill

14:00 - 15:00  Work             1 h
13:30 - 14:00  Personal care   30 m
...

Today                         Insights
```

On a small screen, content scrolls rather than shrinking. The entry sheet must keep Save reachable above the keyboard and expose validation beside the relevant field.

## 5. Entries and categories

### Entry validation

Store UTC instants; use half-open intervals `[start, end)` so adjacent records do not overlap. Inputs are minute-precision. Require an end after start, a duration from 1 to 1,440 actual minutes, no future end beyond the current minute, an existing category, and a note no longer than 500 characters. Overnight activity records are allowed even though overnight reminder schedules are out of scope.

Reject overlap against existing entries, excluding the entry being edited. Offer the conflicting record and a way to adjust the new range. Never silently replace, truncate, or merge records. Enforce the overlap check and write in the same serialized database transaction, including split saves. Repeated Save taps must not create two records.

An app restart, process death, or force-stop after a confirmed save must not lose the record. Database failures leave the draft intact and show a retryable message.

### Categories

Starter categories: Work, Learning, Health, Family and friends, Chores and travel, Leisure, Unplanned browsing, and Sleep and personal care.

Create a category inline using a name only; assign its stable color from a small palette. Trim surrounding whitespace; require 1 to 40 characters; reject duplicates after case-insensitive normalized comparison, including archived categories. Define the comparison key as Unicode NFKC normalization, surrounding-whitespace trim, then lowercase with `Locale.ROOT`. Retain the user's display spelling. If an archived match exists, offer to reactivate it.

A newly saved category is immediately selected and remains available on future visits. Rename by stable category ID, so existing records and reports use the new name. Archive instead of deleting a category referenced by records. Archived categories remain in reports and existing entries but are absent from choices for new entries. Editing an existing record may retain its archived category.

## 6. Reminder behavior

### Schedule contract

The selected window describes time to track. First reminder = start + interval; subsequent reminders stay anchored to that start time. Always include a final reminder at the selected end, even for a shorter final block. If the window is shorter than the interval, send just the end reminder. Do not also send a reminder at the start.

Examples:

| Window and interval | Reminder times | Blocks |
| --- | --- | --- |
| 09:00-12:00, 60 minutes | 10:00, 11:00, 12:00 | Three one-hour blocks. |
| 09:00-12:30, 120 minutes | 11:00, 12:30 | 09:00-11:00, then 11:00-12:30. |
| 09:00-09:30, 60 minutes | 09:30 | One 30-minute block. |

Reject equal start/end or end before start. Save schedule changes immediately; cancel obsolete reminders and calculate the next strictly future boundary. No burst of past reminders when enabling, reopening, changing settings, or restoring a backup.

Use local wall-clock schedule times in the phone's current timezone. For a daylight-saving gap, move a nonexistent local boundary to the next valid instant; for a repeated local time, choose its first occurrence and deduplicate coincident instants. Records still use real elapsed time. Test with an injected clock and timezone.

### Notification and busy-user flow

Notification: "Time check-in" and "What did you do from 14:00 to 15:00?" Actions: "Log time" and "Snooze 15 min". Hide activity notes and category history from lock-screen notification text.

- Tapping Log time or the notification opens the corresponding entry sheet after any device unlock.
- Keep at most one visible time-check-in notification. A newer regular reminder replaces an older unhandled one; older gaps remain visible in Today.
- Snooze retains the original block and does not move the regular schedule. Do not snooze beyond the selected end; explain that the block can be filled in the app. A regular reminder due before a snooze supersedes that snooze.
- If a block is already fully logged, suppress or dismiss its notification. If partly logged, open its remaining gap(s); never overwrite the recorded part. Saving unrelated time must not dismiss the active block's reminder.
- On delayed alarm delivery, present at most the most recent completed block for the current window. Discard obsolete notification intents safely. Do not notify after the window ends; keep those gaps in Today. This includes delayed final reminders.
  The user confirmed a strict instant cutoff: even a final delivery milliseconds after the selected end is discarded. No final-minute grace period is allowed.
- Closing the app's screen must not be required for notifications to work. Reminders work offline. No full-screen takeover, alarm-clock UI, permanent foreground service, or repeated nag loop.

### Android delivery implementation

Use `AlarmManager` with a next-occurrence alarm, recalculating after delivery, schedule edits, reboot, clock/timezone change, and app update. Use explicit immutable pending intents and idempotent handling. Persist enough scheduling state to reject stale deliveries and cancel a pending snooze.

Offer optional precise reminders using `SCHEDULE_EXACT_ALARM`, checking access before scheduling. If unavailable or revoked, use an inexact fallback and show "Timing may vary". Do not use `USE_EXACT_ALARM` or promise exact delivery. Follow Android's current eligibility rules during implementation. See [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

Request `POST_NOTIFICATIONS` on Android 13+ when reminders are enabled. Recheck permission and notification-channel state on resume; show a settings link if blocked. Test grant, denial, and revocation. See [notification permission guidance](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Respect system notification settings and Do Not Disturb. Explain in reminder settings that force-stop and some phone battery restrictions can prevent delivery. Provide troubleshooting without automatically changing battery policy. Do not treat unit tests or emulator delivery as proof of reliability on the user's phone.

## 7. Local data and technical structure

Use Kotlin, Jetpack Compose with Material 3, standard Android lifecycle/ViewModel support, Kotlin coroutines/Flow, and Room. One app module is sufficient. Pin compatible stable dependency and toolchain versions during T01/T02 and record them in progress; do not use dynamic versions or assume these documents establish the newest release.

Room is the persistent source of truth. Store the small settings row there as well so backup restore can replace all durable application data transactionally. Additional DataStore, dependency injection frameworks, generic base repositories, multiple modules, and separate domain layers are unnecessary here. Use constructor injection where needed to make scheduling and aggregation testable. Room supports structured local persistence; see the [official Room guide](https://developer.android.com/training/data-storage/room).

Suggested storage contract:

| Entity | Required values |
| --- | --- |
| Category | Stable ID, display name, normalized unique name, color token, archived flag. |
| Entry | Stable ID, category ID, start/end epoch milliseconds, optional note, created/updated instants. |
| Settings | Singleton ID, onboarding complete, tracking start/end minute of day, interval, desired reminder-enabled state. |
| Device reminder state | Schedule generation, last handled event key, optional snooze time and target block. Separate from portable backup settings. |

Permission grants are system state, not database truth. Derived chart totals, gap totals, and "next reminder" are calculated, not separately persisted. Last successful backup metadata is local housekeeping, not user history.

Use foreign keys and appropriate indexes. Store Room schema exports and add explicit migrations when the schema changes. Never use destructive migration as a shortcut. Keep database and file operations off the main thread.

Database and settings live in app-private storage. No internet permission, telemetry, remote crash SDK, account, or background upload. Disable automatic OS cloud backup and exclude private data from platform transfer rules where supported; audit current target-SDK behavior. `allowBackup=false` alone does not cover every manufacturer's device transfer behavior. See [Android backup rules](https://developer.android.com/identity/data/autobackup).

## 8. Daily and weekly insights

Use the phone's current timezone consistently for report boundaries and visible times. Changing the timezone may move historical entries between calendar dates; the underlying instants do not change. Split a crossing-midnight entry at local date boundaries when calculating totals; do not duplicate it in storage.

Daily view: date navigation, total logged duration, category bars sorted by duration descending with an alphabetical tie break, and each category's duration and percentage of logged time. No-data days have an inviting empty state, not a misleading chart.

Weekly view: week navigation, Monday-Sunday range, seven daily bars including zero days, total logged duration, and category totals with the same ordering. Mark the current day subtly. Future days in the current week have no claimed missing time. Show a plain-language table or semantic list with the same values for accessibility.

Use actual durations for calculations and round only displayed values. Zero total means no percentages, not division by zero. Category shares use logged time as the denominator. Do not label unlogged time as wasted or infer productivity from a category.

Today coverage is a separate calculation: intersect the selected window with elapsed time today, subtract the union of logged intervals inside that intersection, and show remaining gaps. Time logged outside the window still appears in recorded totals. Do not subtract all-day totals from the window length.

Reference fixtures:

- Work 09:00-10:00 and Health 10:00-10:30 = 90 minutes total; category shares approximately 66.7% and 33.3%.
- Learning 23:30-00:30 = 30 minutes on each adjacent date.
- Window 09:00-12:00, current time 11:00, one logged hour inside the window = 60 minutes unlogged; the future hour is excluded.
- Weekly entries on two days remain two nonzero day bars and five zero day bars. Daily sums must equal the weekly total.

## 9. Backup and restore

### Export

"Export backup" opens Android's system file picker to create `time-log-backup-YYYY-MM-DD-HHmm.json`. Export a consistent database snapshot containing a format identifier, schema version, export instant, categories, entries, and portable settings. Use a standard JSON serializer. Android's Storage Access Framework supports user-selected files without broad storage access; see [file access guidance](https://developer.android.com/training/data-storage/shared/documents-files).

Backups are unencrypted JSON in version 1. Explain that they contain activity history and notes. The chosen document provider may be local or cloud-backed; the app itself performs no cloud sync. Mark export successful only after writing and closing the destination successfully. Cancel, no space, and write errors preserve all local data and do not update the last-success timestamp.

Exclude permission flags, device reminder state, derived values, and cached UI data. Keep backup format versioning separate from Room schema versioning. Document the concrete JSON fields when implementing T07.

### Restore

Restore is a full replacement, not a merge. Open a selected JSON file and validate before changing any records. Require a recognized format/version, valid field types, unique IDs and normalized category names, valid category references, valid colors/settings, valid intervals/notes, and no overlapping entries. Reject unsupported future versions. Bound parsing to 20 MiB and 100,000 entries; explain this limit on failure.

Show backup date and counts, plus "This replaces the data currently on this device". Offer Export current data and Cancel alongside an explicit Restore confirmation. After confirmation, replace categories, entries, and portable settings in one transaction; any failure rolls back all changes.

Stop obsolete reminders and clear device reminder state. Restored reminders remain off until the user explicitly re-enables them and grants any required system permission. If interrupted, startup reconciliation must agree with the committed database state. Loading a file, canceling, failed validation, or a failed transaction must not erase records or silently disable the user's existing schedule.

Round-trip acceptance: export, restore into a fresh test database, and compare every category, entry, and portable setting (except reminder enablement, intentionally off after restore). Also test repeated restore, malformed/truncated files, duplicate IDs, dangling categories, overlapping entries, unsupported versions, size limits, cancel, and simulated failure during replacement.

## 10. Acceptance and delivery

Test exception authorized on 15 October 2026: T10 may use a temporary accelerated debug build for the user's requested 10-minute reminder test. Record its actual interval and distinguish its evidence from the normal 60/120-minute schedule. Restore normal source, APK and original phone settings afterward. Keep the strict window-end cutoff and 15-minute snooze rules unchanged.

Version 1 is ready only when:

1. First-use setup, category selection, inline category creation, save/edit/split/delete, and history work offline.
2. Saved data survives app restart, process death, and reboot. Invalid data and overlaps are blocked without data loss.
3. Reminder windows, interval changes, snooze, stale events, quiet hours, and permission states follow section 6. Emulator tests and a real-phone reminder check are documented separately.
4. Daily/weekly calculations match the fixtures and handle midnight, week boundaries, empty data, and daylight-saving changes.
5. Backup round trip and failed restore tests preserve the required data guarantees.
6. Light/dark screens are visually reviewed at small phone width, 200% text size, with the keyboard, and with TalkBack. No clipped primary action or inaccessible chart data.
7. A debug APK, source repository, build/install instructions, test evidence, known limitations, and completed progress record are available. Label a debug APK as a test build; do not claim Play Store readiness or a signed production release.

Aim for immediate feedback on taps and sub-second ordinary record saves and weekly report updates on the test phone with 10,000 synthetic entries. Record actual device and results; do not invent measurements. No polling loop or continuously running service is needed.

Platform references were checked on 13 October 2026. Recheck affected official documentation during implementation when the SDK/API behavior or selected versions differ.

### Signed release follow-up (16 October 2026)

The user authorized a locally signed release APK after a security and memory/storage review. Optimize release code/resources, verify the actual release artifact and document measurements and limits. Retain normal 60/120-minute reminders. Keep signing material private and reusable for updates. This does not authorize publishing or deleting the existing debug installation/data.
