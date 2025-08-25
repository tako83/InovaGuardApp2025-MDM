package com.inova.guard.mdm.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.inova.guard.mdm.MainActivity;
import com.inova.guard.mdm.R;
import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MdmService extends Service {

    private static final String TAG = "MdmService";
    public static boolean isRunning = false;
    private static final String CHANNEL_ID = "InovaGuardMDM_Channel";
    private static final int NOTIFICATION_ID = 123;

    private Handler handler;
    private Runnable connectivityRunnable;
    private long lastConnectedTime;
    private SharedPreferences sharedPreferences;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponentName;

    private BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                if (isConnected) {
                    Log.d(TAG, "Conexión a Internet detectada. Reiniciando contador.");
                    lastConnectedTime = System.currentTimeMillis();
                    reportDeviceStatus(true);
                } else {
                    Log.d(TAG, "Sin conexión a Internet.");
                    reportDeviceStatus(false);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MdmService onCreate");
        isRunning = true;
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponentName = new ComponentName(this, DeviceAdminReceiver.class);

        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("InovaGuard MDM Activo")
                .setContentText("Monitoreando el estado del dispositivo y pagos.")
                .setSmallIcon(R.drawable.inova_guard_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        if (isDeviceOwner()) {
            Log.d(TAG, "MDM Service is Device Owner. Applying restrictions.");
            devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_FACTORY_RESET);
        }

        lastConnectedTime = System.currentTimeMillis();

        handler = new Handler();
        connectivityRunnable = new Runnable() {
            @Override
            public void run() {
                checkConnectivityAndLockStatus();
                handler.postDelayed(this, Constants.CONNECTION_CHECK_INTERVAL);
            }
        };

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MdmService onStartCommand");

        // Lógica crucial: Si no hay un número de serie, salimos del servicio.
        String serialNumber = sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, null);
        if (serialNumber == null || serialNumber.isEmpty() || "unknown".equals(serialNumber)) {
            Log.e(TAG, "No se encontró el serial del dispositivo. El servicio no se ejecutará.");
            stopSelf(); // Detenemos el servicio para evitar un bucle de errores
            return START_NOT_STICKY;
        }

        handler.post(connectivityRunnable);
        reportDeviceStatus(true);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "InovaGuard MDM Channel";
            String description = "Canal para las notificaciones del servicio de monitoreo de InovaGuard MDM.";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void checkConnectivityAndLockStatus() {
        long timeWithoutConnection = System.currentTimeMillis() - lastConnectedTime;
        long minutesWithoutConnection = TimeUnit.MILLISECONDS.toMinutes(timeWithoutConnection);

        Log.d(TAG, "Minutos sin conexión: " + minutesWithoutConnection);

        boolean isLockedPref = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);

        // La lógica de bloqueo por falta de conexión se mantiene sin cambios
        if (minutesWithoutConnection >= Constants.LOCK_THRESHOLD_MINUTES && !isLockedPref) {
            Log.d(TAG, "Umbral de desconexión alcanzado. Bloqueando dispositivo.");
            lockDevice();
        } else if (isLockedPref) {
            Log.d(TAG, "Dispositivo localmente marcado como bloqueado. Reportando estado al servidor.");
            reportDeviceStatus(true);
        } else {
            Log.d(TAG, "Dispositivo en línea y no bloqueado. Reportando estado.");
            reportDeviceStatus(true);
        }
    }

    private void lockDevice() {
        String serialNumber = sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, "unknown");
        ApiUtils.lockDevice(this, serialNumber, new ApiUtils.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    String message = jsonResponse.getString("message");
                    String unlockCode = jsonResponse.optString("unlock_code", "");
                    String contactPhone = jsonResponse.optString("contact_phone", "+58 412 1234567");

                    Log.d(TAG, "Dispositivo bloqueado exitosamente por API. Código: " + unlockCode);
                    sharedPreferences.edit()
                            .putBoolean(Constants.PREF_IS_LOCKED, true)
                            .putString(Constants.PREF_LAST_UNLOCK_CODE, unlockCode)
                            .putString(Constants.PREF_CONTACT_PHONE, contactPhone)
                            .apply();

                    if (devicePolicyManager.isAdminActive(adminComponentName)) {
                        devicePolicyManager.lockNow();
                    }

                    Intent lockIntent = new Intent(MdmService.this, MainActivity.class);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(lockIntent);

                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing lock response: " + e.getMessage());
                    sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();
                    Intent lockIntent = new Intent(MdmService.this, MainActivity.class);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(lockIntent);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Error al llamar a la API de bloqueo: " + errorMessage);
                sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();

                if (devicePolicyManager.isAdminActive(adminComponentName)) {
                    devicePolicyManager.lockNow();
                }

                Intent lockIntent = new Intent(MdmService.this, MainActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(lockIntent);
            }
        });
    }

    private void reportDeviceStatus(boolean isOnline) {
        String serialNumber = sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, "unknown");
        ApiUtils.checkDeviceStatus(this, serialNumber, isOnline, new ApiUtils.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    // CAMBIO CLAVE: Leer el valor booleano 'is_locked' del JSON
                    boolean isLockedByAdmin = jsonResponse.getBoolean("is_locked");

                    // No todos los endpoints de status devuelven estos valores, por lo que los hacemos opcionales
                    String unlockCode = jsonResponse.optString("unlock_code", "");
                    String message = jsonResponse.optString("message", "");
                    String contactPhone = jsonResponse.optString("contact_phone", "+58 412 1234567");
                    String companyLogoUrl = jsonResponse.optString("company_logo_url", "");
                    String nextPaymentDate = jsonResponse.optString("next_payment_date", "N/A");
                    String paymentReminderMessage = jsonResponse.optString("payment_reminder_message", "");
                    String paymentDueDate = jsonResponse.optString("payment_due_date", "N/A");
                    String amountDue = jsonResponse.optString("amount_due", "0.00");
                    String amountPaid = jsonResponse.optString("amount_paid", "0.00");
                    String deviceBrandInfo = jsonResponse.optString("device_brand_info", "N/A");
                    String deviceModelInfo = jsonResponse.optString("device_model_info", "N/A");
                    String paymentInstructions = jsonResponse.optString("payment_instructions", "Contacte a la administración para más detalles.");

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(Constants.PREF_LAST_UNLOCK_CODE, unlockCode);
                    editor.putString(Constants.PREF_CONTACT_PHONE, contactPhone);
                    editor.putString(Constants.PREF_NEXT_PAYMENT_DATE, nextPaymentDate);
                    editor.putString(Constants.PREF_AMOUNT_DUE, amountDue);
                    editor.putString(Constants.PREF_AMOUNT_PAID, amountPaid);
                    editor.putString(Constants.PREF_PAYMENT_INSTRUCTIONS, paymentInstructions);
                    editor.putString(Constants.PREF_DEVICE_BRAND, deviceBrandInfo);
                    editor.putString(Constants.PREF_DEVICE_MODEL, deviceModelInfo);
                    editor.apply();

                    // Lógica para bloquear/desbloquear basada en el estado del servidor
                    if (isLockedByAdmin && !sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
                        Log.d(TAG, "Servidor indica bloqueado, forzando bloqueo local.");
                        editor.putBoolean(Constants.PREF_IS_LOCKED, true).apply();
                        if (devicePolicyManager.isAdminActive(adminComponentName)) {
                            devicePolicyManager.lockNow();
                        }
                        Intent lockIntent = new Intent(MdmService.this, MainActivity.class);
                        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(lockIntent);

                    } else if (!isLockedByAdmin && sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
                        Log.d(TAG, "Servidor indica desbloqueado, forzando desbloqueo local.");
                        editor.putBoolean(Constants.PREF_IS_LOCKED, false).apply();
                        // Este es el intent que lanza la MainActivity y desencadena el showScreen(false)
                        Intent unlockIntent = new Intent(MdmService.this, MainActivity.class);
                        unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(unlockIntent);
                    }
                    Log.d(TAG, "Estado de conectividad reportado y info actualizada: " + response);

                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing status response: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Fallo al reportar el estado de conectividad: " + errorMessage);
                // Si falla la conexión, la lógica de desconexión manejará el bloqueo
                // No es necesario modificar el lastConnectedTime aquí, ya que el ConnectivityReceiver lo hará.
            }
        });
    }

    private boolean isDeviceOwner() {
        return devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MdmService onDestroy");
        isRunning = false;
        handler.removeCallbacks(connectivityRunnable);
        unregisterReceiver(connectivityReceiver);
        stopForeground(true);
    }
}