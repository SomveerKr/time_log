# Time Log JSON backup, version 1

UTF-8 JSON written by Android's native JsonWriter. The format version is independent of the Room schema version. Root fields:

| Field | Type | Meaning |
| --- | --- | --- |
| format | string | Exactly `time-log-backup` |
| version | integer | `1` |
| exportedAtMillis | integer | UTC Unix epoch milliseconds when the consistent snapshot was requested |
| categories | array | All categories, including archived ones |
| entries | array | All saved time entries |
| settings | object | Portable preferences |

Each category contains string `id`, `name`, `normalizedName`; integer `colorToken`; boolean `archived`. IDs are UUIDs. The normalized name is Unicode NFKC, trimmed, then lowercase with Locale.ROOT. The color token is 0 through 7.

Each entry contains string `id`, `categoryId`; integer `startMillis`, `endMillis`, `createdMillis`, `updatedMillis`; and `note` as a string or JSON null. All timestamps are UTC Unix epoch milliseconds. Intervals use an inclusive start and exclusive end; categories are referenced by ID.

Settings contain integer `id` (1), `trackingStartMinute`, `trackingEndMinute`, `intervalMinutes` (60 or 120); boolean `onboardingComplete`, `remindersEnabled`. Tracking minutes are local wall-clock minutes after midnight. The window must end after it starts on the same day.

Array order has no semantic meaning. Export reads all three portable tables in one Room transaction and releases that transaction before writing the document. No permission state, pending alarm, schedule generation, derived chart value, UI draft or last-export metadata is portable. The local last-success timestamp is recorded only after writing and closing the destination.

Files are unencrypted and contain activity history and notes. The chosen document provider can store files locally or in the cloud; Time Log does not upload or sync them. A canceled picker makes no change. A failed write can leave an incomplete destination; retry using a new file. Local history remains intact.

The system picker uses ACTION_CREATE_DOCUMENT with application/json and a suggested name `time-log-backup-YYYY-MM-DD-HHmm.json`. No broad storage permission is used. See [Android document access](https://developer.android.com/training/data-storage/shared/documents-files).

The manifest disables automatic backup; legacy backup rules and Android 12+ cloud/device-transfer rules exclude all credential-protected app data, including Room and export metadata. The app stores no device-protected data, has no cross-platform counterpart/transfer configuration, and uses no BackupAgent. Manufacturer transfer behavior remains outside the app's control; exclusions are not a promise about every OEM tool. See [Android backup rules](https://developer.android.com/identity/data/autobackup).

Restore accepts only version 1, with exactly the documented fields and their JSON types. Duplicate or unknown fields, malformed UTF-8, trailing content and non-integral numbers are rejected. Input is limited to 20 MiB read from the provider and 100,000 entries. Names must match the app's trimmed display-name and normalization rules; colors, UUIDs, references, settings, minute-aligned 1-minute to 24-hour intervals, non-future ends, notes up to 500 Unicode code points, and non-overlap are validated. Export/creation instants must be nonnegative and update time cannot precede creation.

Opening a backup only prepares an in-memory preview. It shows the backup date, category/entry counts and replacement warning, with Export current data, Cancel and Restore and replace actions. Rotation retains the preview; process death discards it safely, requiring selection and confirmation again. The selected file is read once, so later provider edits cannot change the confirmed snapshot.

Confirmation replaces categories, entries and portable settings in one Room transaction under the reminder controller lock. The local schedule generation increments, old device state is cleared, and restored reminders are off. Validation and transaction failures leave data and reminders unchanged. Platform alarm cancellation happens only after commit; a cancellation failure is reported as a completed restore needing reconciliation, not a rolled-back replacement. Reopening reconciles against the committed settings; stale deliveries are rejected. Permission grants and last-export metadata are never restored.
