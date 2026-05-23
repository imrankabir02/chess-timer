package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TimePreset::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timePresetDao(): TimePresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chess_clock_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.beginTransaction()
            try {
                // Populate some classic standard tournament presets using raw SQL inserts
                // isCustom = 0 (false)
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Bullet 1+0', 60000, 'NONE', 0, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Bullet 2+1', 120000, 'INCREMENT', 1, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Blitz 3+0', 180000, 'NONE', 0, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Blitz 3+2', 180000, 'INCREMENT', 2, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Blitz 5+0', 300000, 'NONE', 0, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Blitz 5+3', 300000, 'INCREMENT', 3, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Rapid 10+0', 600000, 'NONE', 0, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Rapid 15+10', 900000, 'INCREMENT', 10, 0, 0)")
                db.execSQL("INSERT INTO time_presets (name, initialTimeMs, timingStyle, incrementSeconds, delaySeconds, isCustom) VALUES ('Classical 30+0', 1800000, 'NONE', 0, 0, 0)")
                db.setTransactionSuccessful()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                db.endTransaction()
            }
        }
    }
}
