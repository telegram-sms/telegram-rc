package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.android.internal.telephony.IPhoneSubInfo
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader

class IPhoneSubInfo {
    private fun getIPhoneSubInfo(): IPhoneSubInfo? {
        val binder: IBinder =
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("iphonesubinfo"))
        return IPhoneSubInfo.Stub.asInterface(binder)
    }

    fun getDefaultIMSIWithShizuku(): String {
        val subInfo = getIPhoneSubInfo() ?: return ""
        return subInfo.getSubscriberId("com.android.shell")
    }

    fun getIMSIWithShizuku(subid: Int): String {
        val subInfo = getIPhoneSubInfo() ?: return ""

        return subInfo.getSubscriberIdForSubscriber(subid, "com.android.shell", null)
    }


    /**
     * Get IPhoneSubInfo service interface with Shizuku privileges
     */
    @SuppressLint("PrivateApi")
    private fun getIPhoneSubInfoService(): Any? {
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")

            // Get the IPhoneSubInfo service binder via Shizuku
            val binder = SystemServiceHelper.getSystemService("iphonesubinfo")
            if (binder == null) {
                Log.e(this::class.java.simpleName, "Failed to get IPhoneSubInfo service binder")
                return null
            }

            // Wrap the binder with ShizukuBinderWrapper
            val wrappedBinder = ShizukuBinderWrapper(binder)

            // Use the IPhoneSubInfo.Stub.asInterface pattern to get the service interface
            val iPhoneSubInfoStubClass =
                Class.forName("com.android.internal.telephony.IPhoneSubInfo\$Stub")
            val asInterfaceMethod = iPhoneSubInfoStubClass.getMethod(
                "asInterface",
                Class.forName("android.os.IBinder")
            )

            val iPhoneSubInfoService = asInterfaceMethod.invoke(null, wrappedBinder)
            Log.d(this::class.java.simpleName, "Successfully obtained IPhoneSubInfo service")
            iPhoneSubInfoService
        } catch (e: Exception) {
            Log.e(
                this::class.java.simpleName,
                "Failed to get IPhoneSubInfo service: ${e.message}",
                e
            )
            null
        }
    }

    fun getDefaultIMSIFallbackWithShizuku(): String? {
        return try {
            val iPhoneSubInfoService = getIPhoneSubInfoService()
            if (iPhoneSubInfoService == null) {
                Log.e(this::class.java.simpleName, "Failed to get IPhoneSubInfo service")
                return null
            }

            // Use reflection to call getSubscriberId method
            val getSubscriberIdMethod = iPhoneSubInfoService.javaClass.getMethod(
                "getSubscriberId",
                String::class.java
            )

            val result =
                getSubscriberIdMethod.invoke(iPhoneSubInfoService, "com.android.shell") as? String

            Log.i(this::class.java.simpleName, "Successfully retrieved subscriber ID")
            result
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Exception during getSubscriberId: ${e.message}", e)
            null
        }
    }

    fun getIMSIFallbackWithShizuku(subId: Int): String? {
        return try {
            val iPhoneSubInfoService = getIPhoneSubInfoService()
            if (iPhoneSubInfoService == null) {
                Log.e(this::class.java.simpleName, "Failed to get IPhoneSubInfo service")
                return null
            }

            // Try with callingFeatureId parameter first (Android 11+)
            // Signature: getSubscriberIdForSubscriber(int subId, String callingPackage, String callingFeatureId)
            try {
                val getSubscriberIdForSubscriberMethod = iPhoneSubInfoService.javaClass.getMethod(
                    "getSubscriberIdForSubscriber",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java
                )

                val result = getSubscriberIdForSubscriberMethod.invoke(
                    iPhoneSubInfoService,
                    subId,
                    "com.android.shell",
                    null  // callingFeatureId
                ) as? String

                Log.i(
                    this::class.java.simpleName,
                    "Successfully retrieved subscriber ID for subId: $subId (with featureId)"
                )
                return result
            } catch (e: NoSuchMethodException) {
                Log.d(
                    this::class.java.simpleName,
                    "Method with featureId not found, trying without: ${e.message}"
                )
            }
            // Fallback to old signature (Android 10 and below)
            // Signature: getSubscriberIdForSubscriber(int subId, String callingPackage)
            val getSubscriberIdForSubscriberMethod = iPhoneSubInfoService.javaClass.getMethod(
                "getSubscriberIdForSubscriber",
                Int::class.javaPrimitiveType,
                String::class.java
            )

            val result = getSubscriberIdForSubscriberMethod.invoke(
                iPhoneSubInfoService,
                subId,
                "com.android.shell"
            ) as? String

            Log.i(
                this::class.java.simpleName,
                "Successfully retrieved subscriber ID for subId: $subId"
            )
            result
        } catch (e: Exception) {
            Log.e(
                this::class.java.simpleName,
                "Exception during getSubscriberIdForSubscriber: ${e.message}",
                e
            )
            null
        }
    }
}
