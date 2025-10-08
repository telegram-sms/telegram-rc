package com.qwe7002.telegram_rc.Room.YellowPage

import androidx.room.Embedded
import androidx.room.Relation

data class OrganizationWithPhoneNumbers(
    @Embedded
    val organization: Organization,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "organization_id"
    )
    val phoneNumbers: List<PhoneNumber>
)
