package com.fitc.wifihotspot;

public abstract class OnStartTetheringCallback {
        /**
         * Called when tethering has been successfully started.
         */
        public abstract void onTetheringStarted();

        /**
         * Called when starting tethering failed.
         */
        public abstract void onTetheringFailed();

}
