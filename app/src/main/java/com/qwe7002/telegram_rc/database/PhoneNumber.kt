package com.qwe7002.telegram_rc.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organization_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("organization_id")]
)
data class PhoneNumber(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "organization_id", index = true)
    val organizationId: Long,
    
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String
)