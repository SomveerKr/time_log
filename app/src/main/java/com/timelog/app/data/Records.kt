package com.timelog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

const val MINUTE_MILLIS = 60_000L
const val CATEGORY_COLOR_COUNT = 8

@Entity(tableName = "categories", indices = [Index(value = ["normalizedName"], unique = true)])
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val colorToken: Int,
    val archived: Boolean = false,
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("categoryId"), Index(value = ["startMillis", "endMillis"])],
)
data class TimeEntry(
    @PrimaryKey val id: String,
    val categoryId: String,
    val startMillis: Long,
    val endMillis: Long,
    val note: String?,
    val createdMillis: Long,
    val updatedMillis: Long,
)

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val onboardingComplete: Boolean = false,
    val trackingStartMinute: Int = 9 * 60,
    val trackingEndMinute: Int = 22 * 60,
    val intervalMinutes: Int = 60,
    val remindersEnabled: Boolean = false,
)

data class EntryInput(
    val categoryId: String,
    val startMillis: Long,
    val endMillis: Long,
    val note: String? = null,
    val id: String = UUID.randomUUID().toString(),
)

enum class ValidationIssue {
    INVALID_NAME, DUPLICATE_CATEGORY, CATEGORY_MISSING, CATEGORY_ARCHIVED,
    INVALID_ID, ENTRY_MISSING, DUPLICATE_ENTRY, INVALID_RANGE, FUTURE_END,
    NOTE_TOO_LONG, OVERLAP, INVALID_SPLIT, INVALID_SETTINGS,
}

class DataValidationException(
    val issue: ValidationIssue,
    val conflictingEntry: TimeEntry? = null,
    val matchingCategory: Category? = null,
) : IllegalArgumentException(issue.name)

fun normalizedCategoryName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)

internal val starterCategories: List<Category> = listOf(
    "Work", "Learning", "Health", "Family and friends", "Chores and travel",
    "Leisure", "Unplanned browsing", "Sleep and personal care",
).mapIndexed { index, name ->
    Category(
        id = UUID.nameUUIDFromBytes("time-log:$name".toByteArray(Charsets.UTF_8)).toString(),
        name = name,
        normalizedName = normalizedCategoryName(name),
        colorToken = index,
    )
}
