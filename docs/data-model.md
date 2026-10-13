# Local data contract

Room 2.8.5, processed by KSP 2.3.12, stores the data in app-private SQLite.
The initial exported schema is version 1 under `app/schemas`. The database
factory registers explicit migrations and has no destructive fallback.
Device reminder state is introduced in T04 through a versioned migration.

- Categories have stable UUID text IDs, display spelling, a unique comparison key, a color token from 0 through 7, and an archive flag. The key is Unicode NFKC, surrounding trim, then lowercase with `Locale.ROOT`. Archived names remain reserved. Rename/archive preserve entry references and color.
- Entries have canonical UUID text IDs, a category foreign key with deletion restricted, UTC epoch-millisecond bounds, optional note, and created/updated metadata. Bounds are minute-aligned and half-open. Duration is 1 through 1,440 actual minutes; future end times are rejected against the injected clock's current minute.
- Name and note limits count Unicode code points: 1 through 40 for trimmed display names and at most 500 for notes. Notes are preserved without trimming.
- Settings use singleton ID 1. Defaults are an unconfirmed 09:00-22:00 window, 60-minute interval, and reminders off. Permission grants and derived report values are not stored here.

All application writes go through `TimeLogStore`. Overlap queries and writes
share the same Room transaction. Editing excludes only the original ID and
preserves creation metadata. A repeated new save with the same ID and content
returns the existing record; an ID reused for different content is rejected.
Splits require positive consecutive pieces covering the selected range, retain
the original identity in the first edited piece, and commit or roll back together.
The DAO uses bound SQL parameters; records and settings are not exposed through
an exported provider. Database operations use Room's asynchronous APIs.

References: [Room releases](https://developer.android.com/jetpack/androidx/releases/room),
[Room storage](https://developer.android.com/training/data-storage/room),
[KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12).
