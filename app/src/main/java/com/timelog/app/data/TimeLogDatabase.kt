package com.timelog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, TimeEntry::class, AppSettings::class, DeviceReminderState::class], version = 3, exportSchema = true)
abstract class TimeLogDatabase : RoomDatabase() {
    internal abstract fun dao(): TimeLogDao

    companion object {
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS device_reminder_state (id INTEGER NOT NULL, generation INTEGER NOT NULL, zoneId TEXT NOT NULL, lastHandledEventKey TEXT, snoozeAtMillis INTEGER, snoozeBlockStartMillis INTEGER, snoozeBlockEndMillis INTEGER, PRIMARY KEY(id))")
                db.execSQL("INSERT INTO device_reminder_state (id, generation, zoneId) VALUES (1, 0, '')")
            }
        }
        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE device_reminder_state ADD COLUMN activeEventKey TEXT")
            }
        }

        fun open(context: Context, name: String = "time-log.db"): TimeLogDatabase =
            Room.databaseBuilder(context.applicationContext, TimeLogDatabase::class.java, name)
                .addMigrations(Migration1To2, Migration2To3)
                .build()
    }
}
