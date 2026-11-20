package com.wanderwildwood.yakuso.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * What a person keeps in the app: what they brewed, what is in the cupboard, and their own timer
 * settings. Each row carries a uuid made when it is first written and never changed, so reading a
 * backup back in can tell a row already here from a new one (BackupJson only ever adds).
 */

/** One cup in the journal. */
@Entity(tableName = "journal", indices = [Index(value = ["uuid"], unique = true)])
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /** The day it was brewed, as days since 1970-01-01 (LocalDate.toEpochDay). */
    val day: Long,
    /** The herb's id in the book, or null for something the book does not hold. */
    val herbId: String?,
    /** What was brewed, as the person sees it: the book's name, or their own words. */
    val name: String,
    /** How long it steeped, in seconds; null when not said. */
    val steepSeconds: Int?,
    val note: String,
    /** 1 to 5, or null for none. */
    val rating: Int?,
    val updatedAt: Long,
)

/** One herb or tea on hand. */
@Entity(tableName = "cupboard", indices = [Index(value = ["uuid"], unique = true)])
data class CupboardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val herbId: String?,
    val name: String,
    /** The day it was bought, as days since 1970-01-01; null when not said. */
    val boughtDay: Long?,
    /** About to run out. */
    val runningLow: Boolean,
    val updatedAt: Long,
)

/** A brew timer setting of the person's own, beside the four the app comes with. */
@Entity(tableName = "presets", indices = [Index(value = ["uuid"], unique = true)])
data class OwnPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val seconds: Int,
    /** Both or neither: stored as written, never one worked out from the other. */
    val tempF: Int?,
    val tempC: Int?,
    val updatedAt: Long,
)
