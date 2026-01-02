package com.qwe7002.telegram_rc.database.yellowpage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneNumberDao {
    @Query("SELECT * FROM phone_numbers WHERE organization_id = :organizationId")
    fun getPhoneNumbersForOrganization(organizationId: Long): Flow<List<PhoneNumber>>

    @Insert
    suspend fun insertPhoneNumber(phoneNumber: PhoneNumber): Long

    @Insert
    suspend fun insertPhoneNumbers(phoneNumbers: List<PhoneNumber>)

    @Query("DELETE FROM phone_numbers WHERE organization_id = :organizationId")
    suspend fun deletePhoneNumbersForOrganization(organizationId: Long)

    @Query("SELECT * FROM phone_numbers WHERE phone_number = :phoneNumber")
    suspend fun getPhoneNumbersByPhoneNumber(phoneNumber: String): List<PhoneNumber>

    @Query("SELECT COUNT(*) FROM phone_numbers")
    suspend fun getCount(): Int
}
