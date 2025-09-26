package com.android.internal.telephony;

interface ITelephony {
   /**
     * Set SIM card power state.
     * @param slotIndex SIM slot id
     * @param state  State of SIM (power down, power up, pass through)
     * @hide
     */
    void setSimPowerStateForSlot(int slotIndex, int state);

}
