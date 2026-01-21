package com.qwe7002.telegram_rc.MMKV

import com.tencent.mmkv.MMKV

object DataPlanManager {
    const val DATA_PLAN_TYPE_DAILY = 0
    const val DATA_PLAN_TYPE_MONTHLY = 1
    
    private const val KEY_DATA_PLAN_TYPE = "data_plan_type"
    private const val KEY_BILLING_CYCLE_START = "billing_cycle_start"
    
    private lateinit var dataPlanMMKV: MMKV
    
    fun initialize() {
        dataPlanMMKV = MMKV.mmkvWithID(DATA_PLAN_MMKV_ID)
    }
    
    fun setDataPlanType(type: Int) {
        dataPlanMMKV.putInt(KEY_DATA_PLAN_TYPE, type)
    }
    
    fun getDataPlanType(): Int {
        return dataPlanMMKV.getInt(KEY_DATA_PLAN_TYPE, DATA_PLAN_TYPE_DAILY)
    }
    
    fun setBillingCycleStart(day: Int) {
        dataPlanMMKV.putInt(KEY_BILLING_CYCLE_START, day)
    }
    
    fun getBillingCycleStart(): Int {
        return dataPlanMMKV.getInt(KEY_BILLING_CYCLE_START, 1)
    }
}
