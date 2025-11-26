package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


class ISub {

    /**
     * Get ISub service interface with Shizuku privileges
     */
    @SuppressLint("PrivateApi")
    private fun getISubService(): Any? {
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")

            // Get the ISub service binder via Shizuku
            val binder = SystemServiceHelper.getSystemService("isub")
            if (binder == null) {
                Log.e(this::class.java.simpleName, "Failed to get ISub service binder")
                return null
            }

            // Wrap the binder with ShizukuBinderWrapper
            val wrappedBinder = ShizukuBinderWrapper(binder)

            // Use the ISub.Stub.asInterface pattern to get the service interface
            val iSubStubClass = Class.forName("com.android.internal.telephony.ISub\$Stub")
            val asInterfaceMethod =
                iSubStubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))

            val iSubService = asInterfaceMethod.invoke(null, wrappedBinder)
            Log.d(this::class.java.simpleName, "Successfully obtained ISub service")
            iSubService
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Failed to get ISub service: ${e.message}", e)
            null
        }
    }

    fun setDefaultDataSubIdWithShizuku(subId: Int): Boolean {
        return try {
            val iSubService = getISubService()
            if (iSubService == null) {
                Log.e(this::class.java.simpleName, "Failed to get ISub service")
                return false
            }

            // Use reflection to call setDefaultDataSubId method
            val setDefaultDataSubIdMethod = iSubService.javaClass.getMethod(
                "setDefaultDataSubId",
                Int::class.javaPrimitiveType
            )

            setDefaultDataSubIdMethod.invoke(iSubService, subId)
            Log.i(this::class.java.simpleName, "Successfully set default data subId to: $subId")
            true
        } catch (e: Exception) {
            Log.e(
                this::class.java.simpleName,
                "Exception during setDefaultDataSubId: ${e.message}",
                e
            )
            false
        }
    }

}
