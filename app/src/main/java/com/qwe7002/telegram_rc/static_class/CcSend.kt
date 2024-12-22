package com.qwe7002.telegram_rc.static_class

class CcSend {
    companion object {
        fun render(template: String, values: Map<String, String>): String {
            var result = template
            for ((key, value) in values) {
                result = result.replace("{{${key}}}", value)
            }
            return result
        }
        val options: ArrayList<String> = arrayListOf(
            "GET",
            "POST"
        )

        var JOBID_counter: Int = 100
    }

}
