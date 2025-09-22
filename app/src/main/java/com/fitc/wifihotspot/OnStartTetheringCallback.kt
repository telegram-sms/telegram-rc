package com.fitc.wifihotspot

abstract class OnStartTetheringCallback {
    /**
     * Called when tethering has been successfully started.
     */
    abstract fun onTetheringStarted()

    /**
     * Called when starting tethering failed.
     */
    abstract fun onTetheringFailed()
}