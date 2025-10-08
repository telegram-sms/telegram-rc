package com.qwe7002.telegram_rc

import com.qwe7002.telegram_rc.Room.YellowPage.checkPhoneNumberInDatabaseBlocking
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.sumimakito.codeauxlib.CodeauxLibPortable
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.shizuku_kit.Networks.setData
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.qwe7002.telegram_rc.static_class.Resend.addResendLoop
import com.qwe7002.telegram_rc.static_class.USSD.sendUssd
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.qwe7002.telegram_rc.static_class.SMS
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.Objects

@Suppress("DEPRECATION")
class SMSReceiver : BroadcastReceiver() {
    private lateinit var preferences: MMKV

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val logTag = "sms_receiver"
        val extras = intent.extras!!
        preferences = MMKV.defaultMMKV()
        if (!preferences.contains("initialized")) {
            Log.i(logTag, "Uninitialized, SMS receiver is deactivated.")
            return
        }
        val botToken = preferences.getString("bot_token", "").toString()
        val chatId = preferences.getString("chat_id", "").toString()
        val requestUri = Network.getUrl(botToken, "sendMessage")

        var intentSlot = extras.getInt("slot", -1)
        val subId = extras.getInt("subscription", -1)
        if (Other.getActiveCard(context) >= 2 && intentSlot == -1) {
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
        val dualSim = Other.getDualSimCardDisplay(
            context,
            intentSlot,
            preferences.getBoolean("display_dual_sim_display_name", false)
        )

        val pdus = (extras["pdus"] as Array<*>?)!!
        val messages = arrayOfNulls<SmsMessage>(
            pdus.size
        )
        for (i in pdus.indices) {
            val format = extras.getString("format")
            Log.d(logTag, "format: $format")
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
        // Check if the phone number exists in our database
        val organizationInfo =
            checkPhoneNumberInDatabaseBlocking(context.applicationContext, messageAddress)
        var messageAddressString = messageAddress

        if (organizationInfo != null) {
            messageAddressString = messageAddressString + " [${organizationInfo}]"
        }
        val trustedPhoneNumber = preferences.getString("trusted_phone_number", "")!!
        var isTrustedPhone = false
        if (trustedPhoneNumber.isNotEmpty()) {
            isTrustedPhone = messageAddress.contains(trustedPhoneNumber)
        }
        Log.d(logTag, "onReceive: $isTrustedPhone")
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.messageThreadId = preferences.getString("message_thread_id", "")
        var messageBodyHtml = messageBody
        val messageHead = "[" + dualSim + context.getString(R.string.receive_sms_head) + "]\n" +
                context.getString(R.string.from) + messageAddressString + "\n" +
                context.getString(R.string.content)

        var rawRequestBodyText: String = messageHead + messageBody
        var isVerificationCode = false
        if (preferences.getBoolean("verification_code", false) && !isTrustedPhone) {
            val verification = CodeauxLibPortable.find(context, messageBody)
            if (verification != null) {
                requestBody.parseMode = "html"
                messageBodyHtml = messageBody
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("&", "&amp;")
                    .replace(verification, "<code>$verification</code>")
                isVerificationCode = true
            }
        }
        requestBody.text = messageHead + messageBodyHtml
        val dataEnable = Network.getDataEnable(context)
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
                            ServiceManage.stopAllService(context)
                            ServiceManage.startService(
                                context,
                                preferences.getBoolean("battery_monitoring_switch", false),
                                preferences.getBoolean("chat_command", false)
                            )
                        }.start()
                        rawRequestBodyText =
                            context.getString(R.string.system_message_head) + "\n" +
                                    context.getString(R.string.restart_service)
                        requestBody.text = rawRequestBodyText
                    }

                    "/switchdata" -> {
                        if (!dataEnable) {
                            openData(context)
                        }
                        rawRequestBodyText =
                            context.getString(R.string.system_message_head) + "\n" +
                                    context.getString(R.string.switch_data)
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
                            Log.i(logTag, "No SMS permission.")
                            return
                        }
                        val msgSendTo = Other.getSendPhoneNumber(commandList[1])
                        if (Other.isPhoneNumber(msgSendTo) && messageList.size > 2) {
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
                            if (Other.getActiveCard(context) > 1) {
                                sendSlot = when (commandList[0].trim { it <= ' ' }) {
                                    "/sendsms1" -> 0
                                    "/sendsms2" -> 1
                                    else -> sendSlot
                                }
                            }
                            Thread {
                                SMS.sendSMS(
                                    context,
                                    msgSendTo,
                                    msgSendContent.toString(),
                                    sendSlot,
                                    Other.getSubId(context, sendSlot)
                                )
                            }
                                .start()
                            return
                        }
                    }
                }
            }
        }
        Log.d(logTag, "onReceive: $isVerificationCode")
        var silentSend = false
        if (!isVerificationCode && !isTrustedPhone) {
            val blackListArray =
                preferences.getStringSet("block_keyword_list", setOf())?.toMutableList()
                    ?: mutableListOf()
            for (blockListItem in blackListArray) {
                if (blockListItem.isEmpty()) {
                    continue
                }
                if (messageBody.contains(blockListItem)) {
                    Log.i(logTag, "Detected message contains blacklist keywords, Silent send.")
                    silentSend = true
                }
            }
        }

        requestBody.disableNotification = silentSend
        val body: RequestBody = RequestBody.create(Const.JSON, Gson().toJson(requestBody))
        val okhttpClient = Network.getOkhttpObj()
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send SMS forward failed:"
        val finalRawRequestBodyText = rawRequestBodyText
        if (!silentSend) {
            CcSendJob.startJob(
                context,
                context.getString(R.string.receive_sms_head),
                finalRawRequestBodyText
            )
        }
        val finalIsFlash = (messages[0]!!.messageClass == SmsMessage.MessageClass.CLASS_0)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(logTag, e.toString())
                writeLog(context, errorHead + e.message)
                SMS.sendFallbackSMS(context, finalRawRequestBodyText, subId)
                addResendLoop(context, requestBody.text)
                commandHandle(messageBody, dataEnable)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    writeLog(context, errorHead + response.code + " " + result)
                    if (!finalIsFlash) {
                        SMS.sendFallbackSMS(context, finalRawRequestBodyText, subId)
                    }
                    addResendLoop(context, requestBody.text)
                } else {
                    if (!Other.isPhoneNumber(messageAddress)) {
                        writeLog(context, "[$messageAddress] Not a regular phone number.")
                        return
                    }
                    Other.addMessageList(Other.getMessageId(result), messageAddress, slot)
                }
                commandHandle(messageBody, dataEnable)
            }
        })
    }


    private fun commandHandle(
        messageBody: String,
        dataEnable: Boolean
    ) {
        if (messageBody.lowercase(Locale.getDefault()).replace("_", "") == "/data") {
            if (dataEnable) {
                setData(false)
            }
        }

    }

    private fun openData(context: Context) {
        setData(true)
        var loopCount = 0
        while (!Network.checkNetworkStatus(context)) {
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
