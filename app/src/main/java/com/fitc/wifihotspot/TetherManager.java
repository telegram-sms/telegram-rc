package com.fitc.wifihotspot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.util.Log;

import com.android.dx.stock.ProxyBuilder;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Created by jonro on 19/03/2018.
 */

public class TetherManager {
    private static final String TAG = TetherManager.class.getSimpleName();

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;

    public TetherManager(Context context) {
        mContext = context;
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
    }
    /**
     * Checks where tethering is on.
     * This is determined by the getTetheredIfaces() method,
     * that will return an empty array if not devices are tethered
     *
     * @return true if a tethered device is found, false if not found
     */
    public boolean isTetherActive() {
        try {
            @SuppressLint("DiscouragedPrivateApi") Method method = mConnectivityManager.getClass().getDeclaredMethod("getTetheredIfaces");
            String[] res = (String[]) method.invoke(mConnectivityManager);
            Log.d(TAG, "getTetheredIfaces invoked");
            Log.d(TAG, Arrays.toString(res));
            assert res != null;
            if (res.length > 0) {
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getTetheredIfaces");
            e.printStackTrace();
        }
        return false;
    }

    public static class TetherMode {
        public static final int TETHERING_WIFI = 0;
        public static final int TETHERING_USB = 1;
        public static final int TETHERING_BLUETOOTH = 2;
        public static final int TETHERING_NCM = 4;
        public static final int TETHERING_ETHERNET = 5;
        public static final int TETHERING_WIGIG = 6;
    }

    /**
     * This enables tethering using the ssid/password defined in Settings App>Hotspot & tethering
     * Does not require app to have system/privileged access
     * Credit: Vishal Sharma - https://stackoverflow.com/a/52219887
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean startTethering(int mode, final OnStartTetheringCallback callback) {

        // On Pie if we try to start tethering while it is already on, it will
        // be disabled. This is needed when startTethering() is called programmatically.
        if (isTetherActive()) {
            Log.d(TAG, "Tether already active, returning");
            return false;
        }

        File outputDir = mContext.getCodeCacheDir();
        Object proxy;
        try {
            //noinspection unchecked
            proxy = ProxyBuilder.forClass(OnStartTetheringCallbackClass())
                    .dexCache(outputDir).handler((proxy1, method, args) -> {
                        switch (method.getName()) {
                            case "onTetheringStarted":
                                if (callback != null) {
                                    callback.onTetheringStarted();
                                }
                                break;
                            case "onTetheringFailed":
                                if (callback != null) {
                                    callback.onTetheringFailed();
                                }
                                break;
                            default:
                                ProxyBuilder.callSuper(proxy1, method, args);
                        }
                        //noinspection SuspiciousInvocationHandlerImplementation
                        return null;
                    }).build();
        } catch (Exception e) {
            Log.e(TAG, "Error in enableTethering ProxyBuilder");
            e.printStackTrace();
            return false;
        }

        Method method;
        try {
            method = mConnectivityManager.getClass().getDeclaredMethod("startTethering", int.class, boolean.class, OnStartTetheringCallbackClass(), Handler.class);
            method.invoke(mConnectivityManager, mode, false, proxy, null);
            Log.d(TAG, "startTethering invoked");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in enableTethering");
            e.printStackTrace();
        }
        return false;
    }

    public void stopTethering(int mode) {
        try {
            Method method = mConnectivityManager.getClass().getDeclaredMethod("stopTethering", int.class);
            method.invoke(mConnectivityManager, mode);
            Log.d(TAG, "stopTethering invoked");
        } catch (Exception e) {
            Log.e(TAG, "stopTethering error: " + e);
            e.printStackTrace();
        }
    }

    @SuppressLint("PrivateApi")
    @SuppressWarnings("rawtypes")
    private Class OnStartTetheringCallbackClass() {
        try {
            return Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "OnStartTetheringCallbackClass error: " + e);
            e.printStackTrace();
        }
        return null;
    }
}
