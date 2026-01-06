package com.qwe7002.telegram_rc.shizuku_kit

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.Executor

object TetheringManagerShizuku {

    /**
     * Get TetheringManager instance with Shizuku privileges
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun getTetheringManager(context: Context): Any? {
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")
            
            // Get the system TetheringManager service via Shizuku
            val binder = SystemServiceHelper.getSystemService(Context.TETHERING_SERVICE)
            if (binder == null) {
                Log.e(ShizukuKit.TAG, "Failed to get TetheringManager service binder")
                return null
            }

            // Wrap the binder with ShizukuBinderWrapper
            val wrappedBinder = ShizukuBinderWrapper(binder)
            
            // Create a minimal Context wrapper that only provides what TetheringManager needs
            // This avoids package name checks
            val minimalContext = object : ContextWrapper(context) {
                override fun getPackageName(): String {
                    return "com.android.shell"  // Use shell package name to match UID 2000
                }
                
                override fun getOpPackageName(): String {
                    return "com.android.shell"
                }
                
                override fun getAttributionTag(): String? {
                    return null
                }
                
                override fun getApplicationInfo(): android.content.pm.ApplicationInfo {
                    return super.getApplicationInfo().apply {
                        packageName = "com.android.shell"
                        uid = 2000  // Shell UID
                    }
                }
            }
            
            val tetheringManagerClass = Class.forName("android.net.TetheringManager")
            
            // Try to find and use the constructor with Supplier parameter
            try {
                val supplierClass = Class.forName("java.util.function.Supplier")
                val constructor = tetheringManagerClass.getDeclaredConstructor(
                    Context::class.java,
                    supplierClass
                )
                constructor.isAccessible = true
                
                // Create a Supplier that returns the wrapped binder
                val supplier = java.lang.reflect.Proxy.newProxyInstance(
                    supplierClass.classLoader,
                    arrayOf(supplierClass)
                ) { _, method, _ -> 
                    when (method.name) {
                        "get" -> wrappedBinder
                        else -> null
                    }
                }
                
                Log.d(ShizukuKit.TAG, "Creating TetheringManager with Supplier constructor")
                return constructor.newInstance(minimalContext, supplier)
            } catch (e: NoSuchMethodException) {
                Log.d(ShizukuKit.TAG, "Supplier constructor not found: ${e.message}",e)
            }
            
            // Try the older constructor with direct IBinder
            try {
                val constructor = tetheringManagerClass.getDeclaredConstructor(
                    Context::class.java,
                    Class.forName("android.os.IBinder")
                )
                constructor.isAccessible = true
                Log.d(ShizukuKit.TAG, "Creating TetheringManager with IBinder constructor")
                return constructor.newInstance(minimalContext, wrappedBinder)
            } catch (e: NoSuchMethodException) {
                Log.d(ShizukuKit.TAG, "IBinder constructor not found: ${e.message}",e)
            }
            
            Log.e(ShizukuKit.TAG, "No suitable constructor found for TetheringManager")
            null
        } catch (e: Exception) {
            Log.e(ShizukuKit.TAG, "Failed to get TetheringManager: ${e.message}", e)
            null
        }
    }

    /**
     * Start tethering using TetheringManager.
     * @param context Application context
     * @param mode Tethering mode (e.g., TetheringManager.TETHERING_WIFI for hotspot)
     * @return true if successful, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun startTethering(context: Context, mode: Int): Boolean {
        return try {
            val tetheringManager = getTetheringManager(context)
            if (tetheringManager == null) {
                Log.e(ShizukuKit.TAG, "Failed to get TetheringManager")
                return false
            }

            // Create a simple executor for callbacks
            val executor = Executor { runnable -> runnable.run() }

            // Use reflection to call startTethering method
            val startTetheringMethod = tetheringManager.javaClass.getMethod(
                "startTethering",
                Int::class.javaPrimitiveType,
                Executor::class.java,
                Class.forName("android.net.TetheringManager\$StartTetheringCallback")
            )

            // Create callback using reflection
            val callbackClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
            val callback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onTetheringStarted" -> {
                        Log.d(ShizukuKit.TAG, "Tethering started successfully for mode: $mode")
                    }
                    "onTetheringFailed" -> {
                        val error = args?.getOrNull(0) as? Int ?: -1
                        Log.e(ShizukuKit.TAG, "Tethering failed for mode: $mode with error: $error")
                    }
                }
                null
            }

            startTetheringMethod.invoke(tetheringManager, mode, executor, callback)

            Log.i(ShizukuKit.TAG, "Initiated tethering start for mode: $mode")
            true
        } catch (e: Exception) {
            Log.e(ShizukuKit.TAG, "Exception during tethering start: ${e.message}", e)
            false
        }
    }

    /**
     * Stop tethering using TetheringManager.
     * @param context Application context
     * @param mode Tethering mode (e.g., TetheringManager.TETHERING_WIFI for hotspot)
     * @return true if successful, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun stopTethering(context: Context, mode: Int): Boolean {
        return try {
            val tetheringManager = getTetheringManager(context)
            if (tetheringManager == null) {
                Log.e(ShizukuKit.TAG, "Failed to get TetheringManager")
                return false
            }

            // Use reflection to call stopTethering method
            val stopTetheringMethod = tetheringManager.javaClass.getMethod(
                "stopTethering",
                Int::class.javaPrimitiveType
            )
            stopTetheringMethod.invoke(tetheringManager, mode)
            Log.i(ShizukuKit.TAG, "Initiated tethering stop for mode: $mode")
            true
        } catch (e: Exception) {
            Log.e(ShizukuKit.TAG, "Exception during tethering stop: ${e.message}", e)
            false
        }
    }
}
