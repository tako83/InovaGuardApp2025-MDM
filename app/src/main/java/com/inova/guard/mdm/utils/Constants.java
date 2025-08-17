package com.inova.guard.mdm.utils;

public class Constants {
    // Asegúrate de que esta IP y puerto sean accesibles desde tu dispositivo/emulador Android.
    // Si estás usando un emulador, 10.0.2.2 suele mapear a localhost de tu máquina.
    // Si usas un dispositivo físico, debe ser la IP de tu máquina en la red local.
    public static final String BASE_URL = "http://10.0.2.2:8000";

    public static final String PREFS_NAME = "InovaGuardPrefs";
    public static final String PREF_IS_LOCKED = "is_locked";
    public static final String PREF_LAST_UNLOCK_CODE = "last_unlock_code"; // El último código de desbloqueo recibido
    public static final String PREF_CONTACT_PHONE = "contact_phone";
    public static final String PREF_DEVICE_ID = "device_id";
    public static final String PREF_SERIAL_NUMBER = "serial_number";
    public static final String PREF_DEVICE_BRAND = "device_brand";
    public static final String PREF_DEVICE_MODEL = "device_model";
    public static final String PREF_DEVICE_IMEI = "device_imei";
    public static final String PREF_NEXT_PAYMENT_DATE = "next_payment_date";
    public static final String PREF_AMOUNT_DUE = "amount_due";
    public static final String PREF_AMOUNT_PAID = "amount_paid";
    public static final String PREF_PAYMENT_INSTRUCTIONS = "payment_instructions";
    public static final String PREF_COMPANY_LOGO_URL = "company_logo_url"; // Para la URL del logo de la empresa

    // Nuevas constantes para la información de la tienda/administración
    public static final String PREF_STORE_LOGO_URL = "store_logo_url";
    public static final String PREF_STORE_CONTACT_PHONE = "store_contact_phone";


    public static final long LOCK_THRESHOLD_MINUTES = 5; // Cambiado a 5 minutos para pruebas
    public static final long CONNECTION_CHECK_INTERVAL = 10 * 1000; // 10 segundos
}
