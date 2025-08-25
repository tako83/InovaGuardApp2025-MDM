package com.inova.guard.mdm.utils;

public class Constants {
    public static final String BASE_URL = "http://10.0.2.2:8000";
    public static final String PREFS_NAME = "InovaGuardMdmPrefs";
    public static final String PREF_IS_ENROLLED = "isEnrolled";
    public static final String PREF_DEVICE_ID = "deviceId";
    public static final String PREF_SERIAL_NUMBER = "serialNumber";
    public static final String PREF_IS_LOCKED = "isLocked";
    public static final String PREF_CONTACT_PHONE = "contactPhone";
    public static final String PREF_LAST_UNLOCK_CODE = "lastUnlockCode";
    public static final String PREF_NEXT_PAYMENT_DATE = "nextPaymentDate";
    public static final String PREF_AMOUNT_DUE = "amountDue";
    public static final String PREF_AMOUNT_PAID = "amountPaid";
    public static final String PREF_DEVICE_BRAND = "deviceBrand";
    public static final String PREF_DEVICE_MODEL = "deviceModel";
    public static final String PREF_PAYMENT_INSTRUCTIONS = "paymentInstructions";
    public static final String PREF_MESSAGE = "message";
    public static final String PREF_COMPANY_LOGO_URL = "companyLogoUrl";

    public static final long CONNECTION_CHECK_INTERVAL = 15 * 60 * 1000;
    public static final long LOCK_THRESHOLD_MINUTES = 60 * 24 * 7;

    public static final int PAYMENT_REMINDER_THRESHOLD_DAYS = 5;
    public static final String EXTRA_SHOW_PAYMENT_REMINDER = "showPaymentReminder";

}