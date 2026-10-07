package com.example.uepflip_flashcard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.uepflip_flashcard.data.local.dao.CardDao
import com.example.uepflip_flashcard.data.local.dao.DeckDao
import com.example.uepflip_flashcard.data.local.dao.ReviewDao
import com.example.uepflip_flashcard.data.local.entities.CardEntity
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.example.uepflip_flashcard.data.local.entities.ReviewEntity

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        ReviewEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uepflip_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
