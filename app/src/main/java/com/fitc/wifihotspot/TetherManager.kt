@file:Suppress("unused")

package com.fitc.wifihotspot

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.util.Log
import com.android.dx.stock.ProxyBuilder
import com.qwe7002.telegram_rc.static_class.LogManage
import java.io.File

/**
 * Created by jonro on 19/03/2018.
 **/
class TetherManager(private val context: Context) {
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Checks where tethering is on.
     * This is determined by the getTetheredIfaces() method,
     * that will return an empty array if not devices are tethered
     *
     * @return true if a tethered device is found, false if not found
     */
    fun isTetherActive(): Boolean {
        return try {
            @SuppressLint("DiscouragedPrivateApi") val method =
                connectivityManager.javaClass.getDeclaredMethod("getTetheredIfaces")
            val res = method.invoke(connectivityManager) as Array<*>
            res.isNotEmpty()
        } catch (e: NoSuchMethodException) {
            Log.e(this::class.java.simpleName, "getTetheredIfaces method not found: $e")
            LogManage.writeLog(context, "getTetheredIfaces method not found: $e")
            false
        } catch (e: IllegalAccessException) {
            Log.e(this::class.java.simpleName, "getTetheredIfaces access denied: $e")
            LogManage.writeLog(context, "getTetheredIfaces access denied: $e")
            false
        } catch (e: java.lang.reflect.InvocationTargetException) {
            Log.e(this::class.java.simpleName, "getTetheredIfaces invocation failed: $e")
            LogManage.writeLog(context, "getTetheredIfaces invocation failed: $e")
            false
        } catch (e: ClassCastException) {
            Log.e(this::class.java.simpleName, "getTetheredIfaces invalid return type: $e")
            LogManage.writeLog(context, "getTetheredIfaces invalid return type: $e")
            false
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Error in getTetheredIfaces: $e")
            LogManage.writeLog(context, "Error in getTetheredIfaces: $e")
            false
        }
    }

    object TetherMode {
        const val TETHERING_WIFI = 0
        const val TETHERING_USB = 1
        const val TETHERING_BLUETOOTH = 2
        const val TETHERING_NCM = 4
        const val TETHERING_ETHERNET = 5
        const val TETHERING_WIGIG = 6
        // VPNHotspot
        const val TETHERING_VPN = 7
    }

    /**
     * This enables tethering using the ssid/password defined in Settings App>Hotspot & tethering
     * Does not require app to have system/privileged access
     * Credit: Vishal Sharma - https://stackoverflow.com/a/52219887
     */
    @Suppress("unused")
    fun startTethering(mode: Int, callback: OnStartTetheringCallback?): Boolean {
        val outputDir: File = context.codeCacheDir
        val proxy: Any
        try {
            val callbackClass = onStartTetheringCallbackClass()
            if (callbackClass == null) {
                LogManage.writeLog(context, "onStartTetheringCallbackClass returned null")
                return false
            }

            proxy = ProxyBuilder.forClass(callbackClass)
                .dexCache(outputDir).handler { proxy1, method, args ->
                    when (method.name) {
                        "onTetheringStarted" -> callback?.onTetheringStarted()
                        "onTetheringFailed" -> callback?.onTetheringFailed()
                        else -> ProxyBuilder.callSuper(proxy1, method, args)
                    }
                    //noinspection SuspiciousInvocationHandlerImplementation
                    null
                }.build()
        } catch (e: java.io.IOException) {
            Log.e(this::class.java.simpleName, "ProxyBuilder IO error: $e")
            LogManage.writeLog(context, "ProxyBuilder IO error: $e")
            return false
        } catch (e: java.lang.reflect.InvocationTargetException) {
            Log.e(this::class.java.simpleName, "ProxyBuilder invocation failed: $e")
            LogManage.writeLog(context, "ProxyBuilder invocation failed: $e")
            return false
        } catch (e: IllegalAccessException) {
            Log.e(this::class.java.simpleName, "ProxyBuilder access denied: $e")
            LogManage.writeLog(context, "ProxyBuilder access denied: $e")
            return false
        } catch (e: InstantiationException) {
            Log.e(this::class.java.simpleName, "ProxyBuilder instantiation failed: $e")
            LogManage.writeLog(context, "ProxyBuilder instantiation failed: $e")
            return false
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Error in enableTethering ProxyBuilder: $e")
            LogManage.writeLog(context, "Error in enableTethering ProxyBuilder: $e")
            return false
        }

        return try {
            val callbackClass = onStartTetheringCallbackClass()
            if (callbackClass == null) {
                Log.e(this::class.java.simpleName, "onStartTetheringCallbackClass returned null")
                LogManage.writeLog(context, "onStartTetheringCallbackClass returned null")
                return false
            }

            val method = connectivityManager.javaClass.getDeclaredMethod(
                "startTethering",
                Int::class.java,
                Boolean::class.java,
                callbackClass,
                Handler::class.java
            )
            method.invoke(connectivityManager, mode, false, proxy, null as Handler?)
            Log.d(this::class.java.simpleName, "startTethering invoked")
            true
        } catch (e: NoSuchMethodException) {
            Log.e(this::class.java.simpleName, "startTethering method not found: $e")
            LogManage.writeLog(context, "startTethering method not found: $e")
            false
        } catch (e: IllegalAccessException) {
            Log.e(this::class.java.simpleName, "startTethering access denied: $e")
            LogManage.writeLog(context, "startTethering access denied: $e")
            false
        } catch (e: java.lang.reflect.InvocationTargetException) {
            Log.e(this::class.java.simpleName, "startTethering invocation failed: $e")
            LogManage.writeLog(context, "startTethering invocation failed: $e")
            false
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Error in enableTethering: $e")
            LogManage.writeLog(context, "Error in enableTethering: $e")
            false
        }
    }

    fun stopTethering(mode: Int) {
        try {
            val method =
                connectivityManager.javaClass.getDeclaredMethod("stopTethering", Int::class.java)
            method.invoke(connectivityManager, mode)
            Log.d(this::class.java.simpleName, "stopTethering invoked")
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "stopTethering error: $e")
            LogManage.writeLog(context, "stopTethering error: $e")
        }
    }

    @SuppressLint("PrivateApi")
    private fun onStartTetheringCallbackClass(): Class<*>? {
        return try {
            Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
        } catch (e: ClassNotFoundException) {
            Log.e(this::class.java.simpleName, "OnStartTetheringCallbackClass not found: $e")
            LogManage.writeLog(context, "OnStartTetheringCallbackClass not found: $e")
            null
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "OnStartTetheringCallbackClass error: $e")
            LogManage.writeLog(context, "OnStartTetheringCallbackClass error: $e")
            null
        }
    }
}
