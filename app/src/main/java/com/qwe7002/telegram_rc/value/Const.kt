package com.qwe7002.telegram_rc.value

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
const val SYSTEM_CONFIG_VERSION: Int = 1

@JvmField
val JSON_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
const val RESULT_CONFIG_JSON: Int = 1
