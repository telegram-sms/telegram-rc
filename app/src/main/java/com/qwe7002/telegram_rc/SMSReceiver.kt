package com.qwe7002.telegram_rc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.sumimakito.codeauxlib.CodeauxLibPortable
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.request_message
import com.qwe7002.telegram_rc.root_kit.Networks.setData
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.qwe7002.telegram_rc.static_class.Resend.addResendLoop
import com.qwe7002.telegram_rc.static_class.USSD.sendUssd
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.other
import com.qwe7002.telegram_rc.static_class.service
import com.qwe7002.telegram_rc.static_class.sms
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

@Suppress("DEPRECATION")
class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        val TAG = "sms_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.")
            return
        }
        val botToken = sharedPreferences.getString("bot_token", "")
        val chatId = sharedPreferences.getString("chat_id", "")
        val requestUri = network.getUrl(botToken, "sendMessage")

        var intentSlot = extras.getInt("slot", -1)
        val subId = extras.getInt("subscription", -1)
        if (other.getActiveCard(context) >= 2 && intentSlot == -1) {
            val manager = SubscriptionManager.from(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val info = manager.getActiveSubscriptionInfo(subId)
                intentSlot = info.simSlotIndex
            }
        }
        val slot = intentSlot
        val dualSim = other.getDualSimCardDisplay(
            context,
            intentSlot,
            sharedPreferences.getBoolean("display_dual_sim_display_name", false)
        )

        val pdus = (extras["pdus"] as Array<*>?)!!
        val messages = arrayOfNulls<SmsMessage>(
            pdus.size
        )
        for (i in pdus.indices) {
            val format = extras.getString("format")
            Log.d(TAG, "format: $format")
            assert(format != null)
            messages[i] = SmsMessage.createFromPdu(pdus[i] as ByteArray, format)
        }
        if (messages.isEmpty()) {
            writeLog(context, "Message length is equal to 0.")
            return
        }

        val messageBodyBuilder = StringBuilder()
        for (item in messages) {
            messageBodyBuilder.append(item!!.messageBody)
        }

        val messageBody = messageBodyBuilder.toString()
        val messageAddress = messages[0]!!.originatingAddress!!
        val trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", null)
        var isTrustedPhone = false
        if (!trustedPhoneNumber.isNullOrEmpty()) {
            isTrustedPhone = messageAddress.contains(trustedPhoneNumber)
        }
        Log.d(TAG, "onReceive: $isTrustedPhone")
        val requestBody = request_message()
        requestBody.chat_id = chatId
        requestBody.message_thread_id = sharedPreferences.getString("message_thread_id", "")
        var messageBodyHtml = messageBody
        var flashSmsString = ""
        if (messages[0]!!.messageClass == SmsMessage.MessageClass.CLASS_0) {
            flashSmsString = "\nType: Class 0"
        }
        val messageHead = """
            [$dualSim${context.getString(R.string.receive_sms_head)}]$flashSmsString
            ${context.getString(R.string.from)}$messageAddress
            ${context.getString(R.string.content)}
            """.trimIndent()
        var rawRequestBodyText: String = messageHead + messageBody
        var isVerificationCode = false
        if (sharedPreferences.getBoolean("verification_code", false) && !isTrustedPhone) {
            if (messageBody.length <= 140) {
                val verification = CodeauxLibPortable.find(context,messageBody)
                if (verification != null) {
                    requestBody.parse_mode = "html"
                    messageBodyHtml = messageBody
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("&", "&amp;")
                        .replace(verification, "<code>$verification</code>")
                    isVerificationCode = true
                }
            } else {
                writeLog(context, "SMS exceeds 140 characters, no verification code is recognized.")
            }
        }
        requestBody.text = messageHead + messageBodyHtml
        val dataEnable = network.getDataEnable(context)
        if (isTrustedPhone) {
            val messageCommand = messageBody.lowercase(Locale.getDefault()).replace("_", "")
            val commandList =
                messageCommand.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (commandList.isNotEmpty()) {
                val messageList =
                    messageBody.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (commandList[0]) {
                    "/restartservice" -> {
                        Thread {
                            service.stopAllService(context)
                            service.startService(
                                context,
                                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                                sharedPreferences.getBoolean("chat_command", false)
                            )
                        }.start()
                        rawRequestBodyText = """
                        ${context.getString(R.string.system_message_head)}
                        ${context.getString(R.string.restart_service)}
                        """.trimIndent()
                        requestBody.text = rawRequestBodyText
                    }

                    "/switchdata" -> {
                        if (!dataEnable) {
                            openData(context)
                        }
                        rawRequestBodyText = """
                            ${context.getString(R.string.system_message_head)}
                            ${context.getString(R.string.switch_data)}
                            """.trimIndent()
                        requestBody.text = rawRequestBodyText
                    }

                    "/sendussd" -> if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (commandList.size == 2) {
                            sendUssd(context, messageList[1], subId)
                            return
                        }
                    }

                    "/sendsms", "/sendsms1", "/sendsms2" -> {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.SEND_SMS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i(TAG, "No SMS permission.")
                            return
                        }
                        val msgSendTo = other.getSendPhoneNumber(commandList[1])
                        if (other.isPhoneNumber(msgSendTo) && messageList.size > 2) {
                            val msgSendContent = StringBuilder()
                            var i = 2
                            while (i < messageList.size) {
                                if (i != 2) {
                                    msgSendContent.append("\n")
                                }
                                msgSendContent.append(messageList[i])
                                ++i
                            }
                            var sendSlot = intentSlot
                            if (other.getActiveCard(context) > 1) {
                                sendSlot = when (commandList[0].trim { it <= ' ' }) {
                                    "/sendsms1" -> 0
                                    "/sendsms2" -> 1
                                    else -> sendSlot
                                }
                            }
                            Thread {
                                sms.sendSMS(
                                    context,
                                    msgSendTo,
                                    msgSendContent.toString(),
                                    sendSlot,
                                    other.getSubId(context, sendSlot)
                                )
                            }
                                .start()
                            return
                        }
                    }
                }
            }
        }
        Log.d(TAG, "onReceive: $isVerificationCode")
        if (!isVerificationCode && !isTrustedPhone) {
            Log.d(TAG, "onReceive: ")
            val blackListArray =
                Paper.book("system_config").read("block_keyword_list", ArrayList<String>())!!
            for (blockListItem in blackListArray) {
                if (blockListItem.isEmpty()) {
                    continue
                }
                if (messageBody.contains(blockListItem)) {
                    val simpleDateFormat =
                        SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
                    val writeMessage = """
                        ${requestBody.text}
                        ${context.getString(R.string.time)}${simpleDateFormat.format(Date(System.currentTimeMillis()))}
                        """.trimIndent()
                    Paper.init(context)
                    val spamSmsList = Paper.book().read("spam_sms_list", ArrayList<String>())!!
                    if (spamSmsList.size >= 5) {
                        spamSmsList.removeAt(0)
                    }
                    spamSmsList.add(writeMessage)
                    Paper.book().write("spam_sms_list", spamSmsList)
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list")
                    return
                }
            }
        }


        val body: RequestBody = RequestBody.create(CONST.JSON,Gson().toJson(requestBody))
        val okhttpClient = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send SMS forward failed:"
        val finalRawRequestBodyText = rawRequestBodyText
        val finalIsFlash = (messages[0]!!.messageClass == SmsMessage.MessageClass.CLASS_0)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, e.toString())
                writeLog(context, errorHead + e.message)
                sms.sendFallbackSMS(context, finalRawRequestBodyText, subId)
                addResendLoop(requestBody.text)
                commandHandle(sharedPreferences, messageBody, dataEnable)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    writeLog(context, errorHead + response.code + " " + result)
                    if (!finalIsFlash) {
                        sms.sendFallbackSMS(context, finalRawRequestBodyText, subId)
                    }
                    addResendLoop(requestBody.text)
                } else {
                    if (!other.isPhoneNumber(messageAddress)) {
                        writeLog(context, "[$messageAddress] Not a regular phone number.")
                        return
                    }
                    other.addMessageList(other.getMessageId(result), messageAddress, slot)
                }
                commandHandle(sharedPreferences, messageBody, dataEnable)
            }
        })
    }

    private fun commandHandle(
        sharedPreferences: SharedPreferences,
        messageBody: String,
        dataEnable: Boolean
    ) {
        if (sharedPreferences.getBoolean("root", false)) {
            if (messageBody.lowercase(Locale.getDefault()).replace("_", "") == "/data") {
                if (dataEnable) {
                    setData(false)
                }
            }
        }
    }

    private fun openData(context: Context?) {
        setData(true)
        var loopCount = 0
        while (!network.checkNetworkStatus(context!!)) {
            if (loopCount >= 100) {
                break
            }
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Log.d("openData", e.toString())
            }
            ++loopCount
        }
    }

}


