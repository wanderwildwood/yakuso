package com.wanderwildwood.yakuso.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    /** Newest day first, and the latest written first within a day. */
    @Query("SELECT * FROM journal ORDER BY day DESC, updatedAt DESC, id DESC")
    fun all(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal ORDER BY day DESC, updatedAt DESC, id DESC")
    suspend fun list(): List<JournalEntry>

    @Query("SELECT * FROM journal WHERE id = :id")
    suspend fun find(id: Long): JournalEntry?

    @Query("SELECT uuid FROM journal")
    suspend fun uuids(): List<String>

    /** A row whose uuid is already here is left as it is; -1 says so. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: JournalEntry): Long

    @Update
    suspend fun update(entry: JournalEntry)

    @Query("DELETE FROM journal WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CupboardDao {
    /** A to Z by name, ignoring case. */
    @Query("SELECT * FROM cupboard ORDER BY name COLLATE NOCASE, id")
    fun all(): Flow<List<CupboardItem>>

    @Query("SELECT * FROM cupboard ORDER BY name COLLATE NOCASE, id")
    suspend fun list(): List<CupboardItem>

    @Query("SELECT * FROM cupboard WHERE id = :id")
    suspend fun find(id: Long): CupboardItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CupboardItem): Long

    @Update
    suspend fun update(item: CupboardItem)

    @Query("DELETE FROM cupboard WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PresetDao {
    /** In the order they were made. */
    @Query("SELECT * FROM presets ORDER BY id")
    fun all(): Flow<List<OwnPreset>>

    @Query("SELECT * FROM presets ORDER BY id")
    suspend fun list(): List<OwnPreset>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(preset: OwnPreset): Long

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: Long)
}
