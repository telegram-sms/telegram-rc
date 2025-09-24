// IPhoneSubInfo.aidl
package com.android.internal.telephony;

interface IPhoneSubInfo {
    // Retrieves the unique subscriber ID, e.g., IMSI for GSM phones.
    String getSubscriberId(String callingPackage);

    // Retrieves the unique device ID, e.g., IMEI for GSM phones.
    String getDeviceId(String callingPackage);

    // Retrieves the ICC serial number (ICCID).
    String getIccSerialNumber(String callingPackage);

    // Retrieves the unique subscriber ID, e.g., IMSI for GSM phones.
    String getSubscriberIdForSubscriber(int subId, String callingPackage,
            String callingFeatureId);
}
