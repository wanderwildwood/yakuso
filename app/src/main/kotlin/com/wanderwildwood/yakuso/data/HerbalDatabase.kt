package com.wanderwildwood.yakuso.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The journal, the cupboard and the person's own timer settings. The herb book is not in here:
 * it is read from the APK.
 */
@Database(
    entities = [JournalEntry::class, CupboardItem::class, OwnPreset::class],
    version = HerbalDatabase.VERSION,
    exportSchema = true,
)
abstract class HerbalDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao
    abstract fun cupboard(): CupboardDao
    abstract fun presets(): PresetDao

    companion object {
        const val NAME = "herbal.db"
        const val VERSION = 1

        /**
         * One migration for every step from 1 to [VERSION], each tested in MigrationTest against
         * the schema exported for the version it starts from (app/schemas). None yet: version 1
         * is the first.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        @Volatile
        private var instance: HerbalDatabase? = null

        fun get(context: Context): HerbalDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Saves and deletes run here, not in a screen's scope: a screen that closes right after
         * "Done" or "Delete" would cancel its own scope and could drop the write.
         */
        private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun write(context: Context, block: suspend HerbalDatabase.() -> Unit): Job {
            val db = get(context)
            return writes.launch { db.block() }
        }

        /** Tests only: closes and forgets the open database. */
        @VisibleForTesting
        internal fun closeForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }

        private fun build(context: Context): HerbalDatabase =
            Room.databaseBuilder(context, HerbalDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration, ever: a missing migration must fail loudly
                // in a build, not quietly wipe somebody's journal.
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
