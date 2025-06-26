package com.qwe7002.telegram_rc.static_class

import android.content.Context
import com.qwe7002.telegram_rc.R
import com.qwe7002.telegram_rc.ReSendJob
import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Resend {
    @JvmStatic
    fun addResendLoop(context: Context, msg: String) {
        var message = msg
        if (message.isEmpty()) {
            return
        }
        val mmkv = MMKV.mmkvWithID(Const.RESEND_MMKV_ID)
        val resendList = mmkv.decodeStringSet("resend_list", mutableSetOf())
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time), Locale.UK)
        message += "\n"+context.getString(R.string.time) + simpleDateFormat.format(Date(System.currentTimeMillis()))
        checkNotNull(resendList)
        resendList.add(message)
        mmkv.encode("resend_list", resendList)
        ReSendJob.startJob(context)
    }
}
