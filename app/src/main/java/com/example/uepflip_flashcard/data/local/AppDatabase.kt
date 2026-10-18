package com.example.uepflip_flashcard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.uepflip_flashcard.data.local.dao.CardDao
import com.example.uepflip_flashcard.data.local.dao.DeckDao
import com.example.uepflip_flashcard.data.local.dao.DocumentDao
import com.example.uepflip_flashcard.data.local.dao.ReviewDao
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.DocumentEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        DocumentEntity::class,
        ReviewEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun documentDao(): DocumentDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "uepflip_database"

        /**
         * Adds the `documents` table plus the `cards.createdAt` / `cards.updatedAt` / `cards.rating`
         * columns. Existing decks, cards and review history are preserved; the new columns default to
         * 0, which for `rating` and the timestamps is the correct "unknown/never" value.
         *
         * Column definitions must match the entities exactly — Room validates the resulting schema
         * when the database is opened.
         *
         * The column additions are guarded with an existence check: builds made during development
         * could already have created version 1 with these columns, and a bare `ALTER TABLE ... ADD
         * COLUMN` would then fail with "duplicate column name".
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("cards", "createdAt")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("cards", "updatedAt")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("cards", "rating")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `rating` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                        "NOT NULL, `deckId` INTEGER NOT NULL, `uriString` TEXT NOT NULL, " +
                        "`extractedText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_documents_deckId` ON `documents` (`deckId`)"
                )
            }
        }

        /**
         * Adds the nullable `cards.hint` column (a pre-generated study nudge).
         *
         * The column is nullable with no default, so every existing row reads back `NULL` — the
         * intended "no hint generated yet" state. No table is rebuilt and no row is written or
         * deleted, so existing decks, cards, scheduling state and review history are untouched.
         * Room validates the result on open: it must be a nullable `TEXT` column named `hint`.
         *
         * Guarded like [MIGRATION_1_2]: a development build may already have created the column, and
         * a bare `ALTER TABLE ... ADD COLUMN` would fail with "duplicate column name".
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("cards", "hint")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `hint` TEXT")
                }
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
            query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex == -1) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
