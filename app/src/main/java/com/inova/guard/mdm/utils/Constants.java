package com.inova.guard.mdm.utils;

public class Constants {
    // URL de tu servidor (la que me proporcionaste)
    public static final String BASE_URL = "http://10.0.2.2:8000";

    // SharedPreferences
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

    // Intervalos y umbrales (en milisegundos y minutos)
    public static final long CONNECTION_CHECK_INTERVAL = 15 * 60 * 1000; // 15 minutos
    public static final long LOCK_THRESHOLD_MINUTES = 48 * 60; // 48 horas sin conexión
}