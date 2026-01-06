package com.qwe7002.telegram_rc.data_structure

import com.google.gson.annotations.SerializedName

data class OutputMetadata(
    @SerializedName("elements")
    val elements: List<OutputMetadataElement>
)

data class OutputMetadataElement(
    @SerializedName("versionCode")
    val versionCode: Int,
    @SerializedName("versionName")
    val versionName: String
)

