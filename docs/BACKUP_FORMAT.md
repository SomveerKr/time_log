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

Restore implementation and its supported validation are delivered in T08. The specification requires a full atomic replacement after confirmation, with restored reminders switched off.
