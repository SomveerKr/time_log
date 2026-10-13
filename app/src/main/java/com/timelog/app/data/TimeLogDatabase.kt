package com.timelog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(entities = [Category::class, TimeEntry::class, AppSettings::class], version = 1, exportSchema = true)
abstract class TimeLogDatabase : RoomDatabase() {
    internal abstract fun dao(): TimeLogDao

    companion object {
        // Add explicit migrations here when the exported schema version changes.
        private val migrations = emptyArray<Migration>()

        fun open(context: Context, name: String = "time-log.db"): TimeLogDatabase =
            Room.databaseBuilder(context.applicationContext, TimeLogDatabase::class.java, name)
                .addMigrations(*migrations)
                .build()
    }
}
