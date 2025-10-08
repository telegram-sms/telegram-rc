package com.qwe7002.telegram_rc.database.YellowPage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Organization::class, PhoneNumber::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun organizationDao(): OrganizationDao
    abstract fun phoneNumberDao(): PhoneNumberDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "com.qwe7002.telegram_rc.Room.YellowPage"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
