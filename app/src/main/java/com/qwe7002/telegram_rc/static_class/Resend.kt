package com.qwe7002.telegram_rc.static_class

import io.paperdb.Paper

object Resend {
    @JvmStatic
    fun addResendLoop(message: String) {
        val resendList = Paper.book().read(
            "resend_list",
            ArrayList<String>()
        )!!
        resendList.add(message)
        Paper.book().write("resend_list", resendList)
    }
}
