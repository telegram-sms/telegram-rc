package com.qwe7002.telegram_rc.database

import android.content.Context
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.database.yellowpage.AppDatabase
import com.qwe7002.telegram_rc.database.yellowpage.Organization
import com.qwe7002.telegram_rc.database.yellowpage.PhoneNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object YellowPage {
    @JvmStatic
    fun checkPhoneNumberInDatabaseBlocking(context: Context, phoneNumber: String): String? {
        return try {
            runBlocking {
                val db = AppDatabase.getDatabase(context)
                val phoneNumbers = db.phoneNumberDao().getPhoneNumbersByPhoneNumber(phoneNumber)
                if (phoneNumbers.isNotEmpty()) {
                    val organization =
                        db.organizationDao().getOrganizationById(phoneNumbers[0].organizationId)
                    organization?.organization
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(Const.TAG, "Error checking phone number in database", e)
            null
        }
    }

    fun addPhoneNumberToDatabase(
        context: Context,
        phoneNumbers: List<String>,
        organization: String,
        url: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val organizationDao = db.organizationDao()
            val phoneNumberDao = db.phoneNumberDao()
            val orgId = organizationDao.insertOrganization(
                Organization(
                    organization = organization,
                    url = url
                )
            )
            phoneNumberDao.insertPhoneNumbers(phoneNumbers.map {
                PhoneNumber(
                    organizationId = orgId,
                    phoneNumber = it
                )
            })
        }
    }
}
