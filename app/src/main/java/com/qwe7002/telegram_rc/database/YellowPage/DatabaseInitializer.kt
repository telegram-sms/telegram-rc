package com.qwe7002.telegram_rc.database.YellowPage

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object DatabaseInitializer {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Future migrations can be added here
        }
    }

    fun initializeDatabase(context: Context) {
        val db = AppDatabase.getDatabase(context)

        // Pre-populate with Huawei data as requested
        populateWithInitialData(db)
    }

    private fun populateWithInitialData(db: AppDatabase) {
        // This will run in a background thread
        CoroutineScope(Dispatchers.IO).launch {
            val organizationDao = db.organizationDao()
            val phoneNumberDao = db.phoneNumberDao()
            
            // Check if data already exists
            if (organizationDao.getOrganizationsWithPhoneNumbers().first().isEmpty()) {
                // Insert Huawei organization
                val huaweiOrg = Organization(
                    organization = "华为",
                    url = "https://www.huawei.com/cn/"
                )
                
                val orgId = organizationDao.insertOrganization(huaweiOrg)
                
                // Insert phone numbers
                val phoneNumbers = listOf(
                    "4008308300",
                    "8008308300",
                    "4008229999",
                    "0755-28780808",
                    "0769-23280808",
                    "0755-22731198",
                    "0769-22862200"
                ).map { PhoneNumber(organizationId = orgId, phoneNumber = it) }
                
                phoneNumberDao.insertPhoneNumbers(phoneNumbers)
            }
        }
    }
}
