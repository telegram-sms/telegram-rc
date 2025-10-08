package com.qwe7002.telegram_rc.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM organizations")
    fun getAllOrganizations(): Flow<List<Organization>>

    @Query("SELECT * FROM organizations WHERE id = :id")
    suspend fun getOrganizationById(id: Long): Organization?

    @Insert
    suspend fun insertOrganization(organization: Organization): Long

    @Query("DELETE FROM organizations")
    suspend fun deleteAllOrganizations()

    @Transaction
    @Query("SELECT * FROM organizations")
    fun getOrganizationsWithPhoneNumbers(): Flow<List<OrganizationWithPhoneNumbers>>
}