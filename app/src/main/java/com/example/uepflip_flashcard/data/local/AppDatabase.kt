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
import com.example.uepflip_flashcard.data.local.dao.UserDao
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.DocumentEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity
import com.example.uepflip_flashcard.data.local.entities.UserEntity

@Database(
    entities = [
        UserEntity::class,
        DeckEntity::class,
        CardEntity::class,
        DocumentEntity::class,
        ReviewEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
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
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
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
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("cards", "hint")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `hint` TEXT")
                }
            }
        }

        /**
         * Adds [UserEntity] table and sync fields with FK from decks to users.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (`uid` TEXT NOT NULL, " +
                        "`email` TEXT, `displayName` TEXT, `photoUrl` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, `lastSyncedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`syncState` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`uid`))"
                )

                if (!db.hasColumn("decks", "userId")) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `decks_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "`userId` TEXT, " +
                            "`remoteId` TEXT, " +
                            "`syncState` INTEGER NOT NULL DEFAULT 0, " +
                            "`lastSyncedAt` INTEGER NOT NULL DEFAULT 0, " +
                            "FOREIGN KEY(`userId`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "INSERT INTO `decks_new` (`id`, `title`, `description`, `createdAt`, `updatedAt`, `userId`, `remoteId`, `syncState`, `lastSyncedAt`) " +
                            "SELECT `id`, `title`, `description`, `createdAt`, `updatedAt`, NULL, NULL, 0, 0 FROM `decks`"
                    )
                    db.execSQL("DROP TABLE `decks`")
                    db.execSQL("ALTER TABLE `decks_new` RENAME TO `decks`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_decks_userId` ON `decks` (`userId`)")
                }

                if (!db.hasColumn("cards", "userId")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `userId` TEXT")
                }
                if (!db.hasColumn("cards", "remoteId")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `remoteId` TEXT")
                }
                if (!db.hasColumn("cards", "syncState")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `syncState` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("cards", "lastSyncedAt")) {
                    db.execSQL("ALTER TABLE `cards` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
                }

                if (!db.hasColumn("documents", "userId")) {
                    db.execSQL("ALTER TABLE `documents` ADD COLUMN `userId` TEXT")
                }
                if (!db.hasColumn("documents", "remoteId")) {
                    db.execSQL("ALTER TABLE `documents` ADD COLUMN `remoteId` TEXT")
                }
                if (!db.hasColumn("documents", "syncState")) {
                    db.execSQL("ALTER TABLE `documents` ADD COLUMN `syncState` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("documents", "lastSyncedAt")) {
                    db.execSQL("ALTER TABLE `documents` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
                }

                if (!db.hasColumn("reviews", "userId")) {
                    db.execSQL("ALTER TABLE `reviews` ADD COLUMN `userId` TEXT")
                }
                if (!db.hasColumn("reviews", "remoteId")) {
                    db.execSQL("ALTER TABLE `reviews` ADD COLUMN `remoteId` TEXT")
                }
                if (!db.hasColumn("reviews", "syncState")) {
                    db.execSQL("ALTER TABLE `reviews` ADD COLUMN `syncState` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("reviews", "lastSyncedAt")) {
                    db.execSQL("ALTER TABLE `reviews` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
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

        /** MIGRATION_4_5: Add FK on documents.deckId referencing decks(id) with ON DELETE CASCADE.
         * Since Room cannot do FK additions with ALTER TABLE, we do a table-rebuild migration:
         * 1. Create `documents_new` with the FK
         * 2. Copy data from `documents` to `documents_new`
         * 3. Drop old `documents`
         * 4. Rename `documents_new` to `documents`
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `documents_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                        "NOT NULL, `deckId` INTEGER NOT NULL, `uriString` TEXT NOT NULL, " +
                        "`extractedText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `documents_new` (`id`, `deckId`, `uriString`, `extractedText`, `createdAt`) " +
                        "SELECT `id`, `deckId`, `uriString`, `extractedText`, `createdAt` FROM `documents`"
                )
                db.execSQL("DROP TABLE `documents`")
                db.execSQL("ALTER TABLE `documents_new` RENAME TO `documents`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
