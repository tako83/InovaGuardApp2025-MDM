package com.inova.guard.mdm.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;

import com.inova.guard.mdm.MainActivity;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {

    private static final String TAG = "DeviceAdminReceiver";

    // NOTA: onEnabled() no es llamado durante el aprovisionamiento con QR.
    // Su lógica se ha movido a onProfileProvisioningComplete().
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Administrador de Dispositivo InovaGuard activado.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Administrador de Dispositivo InovaGuard activado.");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = new ComponentName(context, DeviceAdminReceiver.class);
        if (devicePolicyManager != null && devicePolicyManager.isAdminActive(adminComponentName)) {
            devicePolicyManager.lockNow();
            Intent lockIntent = new Intent(context, MainActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            lockIntent.putExtra("reason_for_lock", "admin_disabled");
            context.startActivity(lockIntent);
        }
        String serialNumber = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Constants.PREF_SERIAL_NUMBER, "unknown");
        if (serialNumber != null && !"unknown".equals(serialNumber)) {
            ApiUtils.notifyAdminDisabled(context, serialNumber, new ApiUtils.ApiCallback() {
                @Override public void onSuccess(String response) { Log.d(TAG, "Notificación de desactivación enviada."); }
                @Override public void onFailure(String errorMessage) { Log.e(TAG, "Error al notificar sobre la desactivación: " + errorMessage); }
            });
        }
        return null;
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Administrador de Dispositivo InovaGuard desactivado.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Administrador de Dispositivo InovaGuard desactivado.");
        String serialNumber = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Constants.PREF_SERIAL_NUMBER, "unknown");
        if (serialNumber != null && !"unknown".equals(serialNumber)) {
            ApiUtils.notifyAdminDisabled(context, serialNumber, new ApiUtils.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Notificación de desactivación enviada al servidor.");
                }
                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "Error al notificar al servidor sobre la desactivación: " + errorMessage);
                }
            });
        }
    }

    @Override
    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
        super.onLockTaskModeEntering(context, intent, pkg);
        Log.d(TAG, "Entering lock task mode for package: " + pkg);
    }

    @Override
    public void onLockTaskModeExiting(Context context, Intent intent) {
        super.onLockTaskModeExiting(context, intent);
        Log.d(TAG, "Exiting lock task mode.");
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        Log.d(TAG, "Provisioning complete. Setting up device policies...");

        // FASE 1: OBTENER LOS DATOS DEL CLIENTE DEL QR Y GUARDARLOS EN SHARED PREFERENCES
        Bundle provisioningExtras = intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        if (provisioningExtras != null) {
            String clientName = provisioningExtras.getString("client_name");
            String clientEmail = provisioningExtras.getString("client_email");
            String serialFromQR = provisioningExtras.getString("serial_number");

            SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Constants.PREF_SERIAL_NUMBER, serialFromQR);
            editor.putString(Constants.PREF_CLIENT_NAME, clientName);
            editor.putString(Constants.PREF_CLIENT_EMAIL, clientEmail);
            editor.apply();

            Log.d(TAG, "Datos de aprovisionamiento guardados en SharedPreferences.");

            // AHORA QUE LOS DATOS ESTÁN DISPONIBLES, ENVIAMOS LA INFORMACIÓN AL SERVIDOR
            try {
                JSONObject enrollmentData = new JSONObject();
                enrollmentData.put("client_name", clientName);
                enrollmentData.put("client_email", clientEmail);
                enrollmentData.put("serial_number", serialFromQR);
                enrollmentData.put("device_brand_info", Build.BRAND);
                enrollmentData.put("device_model_info", Build.MODEL);
                enrollmentData.put("device_type", "smartphone");

                ApiUtils.enrollDevice(context, enrollmentData, new ApiUtils.ApiCallback() {
                    @Override
                    public void onSuccess(String response) {
                        Log.d(TAG, "Dispositivo registrado con éxito: " + response);
                        // Limpiar los datos de SharedPreferences después de un enrolamiento exitoso
                        prefs.edit().remove(Constants.PREF_SERIAL_NUMBER).apply();
                        prefs.edit().remove(Constants.PREF_CLIENT_NAME).apply();
                        prefs.edit().remove(Constants.PREF_CLIENT_EMAIL).apply();
                    }
                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Fallo al registrar el dispositivo: " + errorMessage);
                        // El dispositivo seguirá activo, pero la notificación al backend falló.
                        // El servicio en segundo plano podría intentar reenviarlo.
                    }
                });

            } catch (JSONException e) {
                Log.e(TAG, "Error al crear el objeto JSON para el enrolamiento: " + e.getMessage());
            }

        } else {
            Log.e(TAG, "No se encontraron datos de aprovisionamiento. Abortando.");
            return;
        }

        // FASE 2: APLICAR POLÍTICAS Y CONFIGURAR EL MODO KIOSCO
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(context, DeviceAdminReceiver.class);

        if (dpm == null) {
            Log.e(TAG, "DevicePolicyManager is null.");
            return;
        }

        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER);
        dpm.setUninstallBlocked(adminComponent, context.getPackageName(), true);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);

        String[] allowedPackages = {context.getPackageName()};
        dpm.setLockTaskPackages(adminComponent, allowedPackages);

        Intent lockIntent = new Intent(context, MainActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        lockIntent.putExtra("start_kiosk_mode", true);
        context.startActivity(lockIntent);

        Intent mdmServiceIntent = new Intent(context, com.inova.guard.mdm.service.MdmService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(mdmServiceIntent);
        } else {
            context.startService(mdmServiceIntent);
        }

        Intent firebaseServiceIntent = new Intent(context, com.inova.guard.mdm.service.MyFirebaseMessagingService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(firebaseServiceIntent);
        } else {
            context.startService(firebaseServiceIntent);
        }

        Log.d(TAG, "Provisioning complete and device policies applied.");
    }
}
