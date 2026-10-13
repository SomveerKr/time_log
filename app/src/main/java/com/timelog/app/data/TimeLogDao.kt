package com.timelog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TimeLogDao {
    @Query("SELECT * FROM categories ORDER BY normalizedName")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY normalizedName")
    suspend fun categories(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: String): Category?

    @Query("SELECT * FROM categories WHERE normalizedName = :key")
    suspend fun categoryWithName(key: String): Category?

    @Insert
    suspend fun insertCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Query("SELECT * FROM entries WHERE endMillis > :start AND startMillis < :end ORDER BY startMillis DESC")
    fun observeEntries(start: Long, end: Long): Flow<List<TimeEntry>>

    @Query("SELECT * FROM entries ORDER BY startMillis DESC")
    suspend fun entries(): List<TimeEntry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: String): TimeEntry?

    @Query("SELECT * FROM entries WHERE endMillis > :start AND startMillis < :end AND (:excludedId IS NULL OR id != :excludedId) LIMIT 1")
    suspend fun conflict(start: Long, end: Long, excludedId: String?): TimeEntry?

    @Insert
    suspend fun insertEntry(entry: TimeEntry)

    @Update
    suspend fun updateEntry(entry: TimeEntry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): AppSettings?

    @Upsert
    suspend fun saveSettings(settings: AppSettings)

    @Query("SELECT * FROM device_reminder_state WHERE id = 1")
    suspend fun deviceState(): DeviceReminderState?

    @Upsert
    suspend fun saveDeviceState(state: DeviceReminderState)
}
