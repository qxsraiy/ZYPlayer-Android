package com.zyplayer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zyplayer.app.data.model.Favorite
import com.zyplayer.app.data.model.Source
import com.zyplayer.app.data.model.Video

@Database(
    entities = [Source::class, Video::class, Favorite::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun videoDao(): VideoDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        var INSTANCE: AppDatabase? = null
            private set

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zyplayer_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}