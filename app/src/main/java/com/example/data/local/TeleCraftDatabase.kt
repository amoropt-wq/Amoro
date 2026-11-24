package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ApiLogEntity::class,
        AutoReplyRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TeleCraftDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun apiLogDao(): ApiLogDao
    abstract fun autoReplyRuleDao(): AutoReplyRuleDao

    companion object {
        @Volatile
        private var INSTANCE: TeleCraftDatabase? = null

        fun getInstance(context: Context): TeleCraftDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TeleCraftDatabase::class.java,
                    "telecraft_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
