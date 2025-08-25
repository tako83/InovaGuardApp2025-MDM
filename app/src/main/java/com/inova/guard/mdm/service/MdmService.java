package com.inova.guard.mdm.service;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
    private FusedLocationProviderClient fusedLocationClient;

    private BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                if (isConnected) {
                    Log.d(TAG, "Conexión a Internet detectada. Reiniciando contador y reportando estado.");
                    lastConnectedTime = System.currentTimeMillis();
                    // Reportar inmediatamente para obtener el estado de bloqueo del backend
                    requestLocationAndReportStatus();
                } else {
                    Log.d(TAG, "Sin conexión a Internet.");
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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("InovaGuard MDM Activo")
                .setContentText("Monitoreando el estado del dispositivo y pagos.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        if (isDeviceOwner()) {
            Log.d(TAG, "MDM Service is Device Owner. Applying restrictions.");
            devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_FACTORY_RESET);
        }

        lastConnectedTime = System.currentTimeMillis();
        handler = new Handler();
        connectivityRunnable = this::checkLockStatusAndReport;

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MdmService onStartCommand");
        String serialNumber = sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, null);
        if (serialNumber == null || serialNumber.isEmpty() || "unknown".equals(serialNumber)) {
            Log.e(TAG, "No se encontró el serial del dispositivo. El servicio no se ejecutará.");
            stopSelf();
            return START_NOT_STICKY;
        }

        handler.postDelayed(connectivityRunnable, Constants.CONNECTION_CHECK_INTERVAL);
        requestLocationAndReportStatus();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "InovaGuard MDM Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Canal para las notificaciones del servicio de monitoreo de InovaGuard MDM.");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void checkLockStatusAndReport() {
        long timeWithoutConnection = System.currentTimeMillis() - lastConnectedTime;
        long minutesWithoutConnection = TimeUnit.MILLISECONDS.toMinutes(timeWithoutConnection);

        Log.d(TAG, "Minutos sin conexión: " + minutesWithoutConnection);

        boolean isLockedPref = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);

        if (minutesWithoutConnection >= Constants.LOCK_THRESHOLD_MINUTES && !isLockedPref) {
            Log.d(TAG, "Umbral de desconexión alcanzado. Bloqueando dispositivo localmente.");
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();
            startMainActivityWithLockScreen();
        }

        // Siempre reporta el estado al backend para sincronizar
        requestLocationAndReportStatus();
        handler.postDelayed(connectivityRunnable, Constants.CONNECTION_CHECK_INTERVAL);
    }

    private void requestLocationAndReportStatus() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                double latitude = 0.0;
                double longitude = 0.0;
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                }
                reportDeviceStatus(latitude, longitude);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error al obtener la ubicación: " + e.getMessage());
                reportDeviceStatus(0.0, 0.0);
            });
        } else {
            Log.w(TAG, "Permisos de ubicación no concedidos. No se enviará la ubicación.");
            reportDeviceStatus(0.0, 0.0);
        }
    }

    private void reportDeviceStatus(double latitude, double longitude) {
        String serialNumber = sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, "unknown");
        boolean isOnline = isNetworkConnected();

        ApiUtils.checkDeviceStatus(this, serialNumber, isOnline, new ApiUtils.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    boolean isLockedByAdmin = jsonResponse.optBoolean("is_locked", false);
                    String nextPaymentDate = jsonResponse.optString("next_payment_date", "N/A");
                    String amountDue = jsonResponse.optString("amount_due", "N/A");
                    String amountPaid = jsonResponse.optString("amount_paid", "N/A");
                    String message = jsonResponse.optString("message", "N/A");
                    String companyLogoUrl = jsonResponse.optString("company_logo_url", null);

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(Constants.PREF_IS_LOCKED, isLockedByAdmin);
                    editor.putString(Constants.PREF_NEXT_PAYMENT_DATE, nextPaymentDate);
                    editor.putString(Constants.PREF_AMOUNT_DUE, amountDue);
                    editor.putString(Constants.PREF_AMOUNT_PAID, amountPaid);
                    editor.putString(Constants.PREF_MESSAGE, message);
                    editor.putString(Constants.PREF_COMPANY_LOGO_URL, companyLogoUrl);
                    editor.apply();

                    if (isLockedByAdmin) {
                        startMainActivityWithLockScreen();
                    } else {
                        // Aquí podrías mostrar el recordatorio de pago si no está bloqueado
                        // Por ejemplo, lanzando MainActivity con un extra específico
                        Intent mainIntent = new Intent(MdmService.this, MainActivity.class);
                        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(mainIntent);
                    }
                    Log.d(TAG, "Estado de conectividad reportado y info actualizada: " + response);

                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing status response: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Fallo al reportar el estado de conectividad: " + errorMessage);
                // No se hace nada aquí para evitar bucles de bloqueo si el servidor falla.
                // La lógica de bloqueo por desconexión lo manejará si el umbral se excede.
            }
        });
    }

    private void startMainActivityWithLockScreen() {
        Intent lockIntent = new Intent(this, MainActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(lockIntent);
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
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