package com.qwe7002.telegram_rc.database

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.database.yellowpage.AppDatabase
import com.qwe7002.telegram_rc.database.yellowpage.Organization
import com.qwe7002.telegram_rc.database.yellowpage.PhoneNumber
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.value.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request

object YellowPage {
    /**
     * Format phone number by removing all non-digit characters
     * Examples:
     * "+86 400-990-7930" -> "4009907930"
     * "+1 (650) 253-0000" -> "16502530000"
     * "0755-2878-0808" -> "075528780808"
     */
    private fun formatPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9]"), "")
    }

    @JvmStatic
    fun checkPhoneNumberInDatabaseBlocking(context: Context, phoneNumber: String): String? {
        return try {
            runBlocking {
                val db = AppDatabase.getDatabase(context)
                val formattedNumber = formatPhoneNumber(phoneNumber)
                val phoneNumbers = db.phoneNumberDao().getPhoneNumbersByPhoneNumber(formattedNumber)
                if (phoneNumbers.isNotEmpty()) {
                    val organization =
                        db.organizationDao().getOrganizationById(phoneNumbers[0].organizationId)
                    organization?.organization
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking phone number in database", e)
            null
        }
    }

    /**
     * Data class for parsing JSON entries
     */
    data class YellowPageEntry(
        val organization: String,
        val category: String
    )

    /**
     * Sync YellowPage database from a URL
     * @param context Android context
     * @param url The URL to download JSON from
     * @param statusCallback Callback for status updates
     * @return Number of entries added
     */
    suspend fun syncFromUrl(
        context: Context,
        url: String,
        statusCallback: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val client = Network.getOkhttpObj()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        statusCallback("Downloading...")
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val jsonString = response.body.string()

        statusCallback("Parsing...")
        val type = object : TypeToken<Map<String, YellowPageEntry>>() {}.type
        val data: Map<String, YellowPageEntry> = Gson().fromJson(jsonString, type)

        statusCallback("Saving to database...")
        val db = AppDatabase.getDatabase(context)
        val organizationDao = db.organizationDao()
        val phoneNumberDao = db.phoneNumberDao()

        // Clear existing data
        organizationDao.deleteAllOrganizations()

        // Group phone numbers by organization
        val orgToPhones = mutableMapOf<String, MutableList<String>>()
        for ((phoneNumber, entry) in data) {
            val orgKey = "${entry.organization}|${entry.category}"
            orgToPhones.getOrPut(orgKey) { mutableListOf() }.add(phoneNumber)
        }

        var totalEntries = 0
        // Insert organizations and their phone numbers
        for ((orgKey, phoneNumbers) in orgToPhones) {
            val parts = orgKey.split("|")
            val orgName = parts[0]
            val category = if (parts.size > 1) parts[1] else ""

            val orgId = organizationDao.insertOrganization(
                Organization(
                    organization = orgName,
                    url = category
                )
            )

            phoneNumberDao.insertPhoneNumbers(phoneNumbers.map {
                PhoneNumber(
                    organizationId = orgId,
                    phoneNumber = it
                )
            })
            totalEntries += phoneNumbers.size
        }

        Log.d(TAG, "YellowPage sync completed: $totalEntries entries")
        totalEntries
    }

    /**
     * Get the count of phone numbers in the database
     */
    suspend fun getPhoneNumberCount(context: Context): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        db.phoneNumberDao().getCount()
    }

    /**
     * Clear all data from the YellowPage database
     */
    suspend fun clearDatabase(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        db.organizationDao().deleteAllOrganizations()
        Log.d(TAG, "YellowPage database cleared")
    }
}
