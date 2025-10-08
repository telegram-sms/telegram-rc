package com.qwe7002.telegram_rc.database.YellowPage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organizations")
data class Organization(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "organization")
    val organization: String,
    
    @ColumnInfo(name = "url")
    val url: String
)
