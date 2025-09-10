package com.inova.guard.mdm.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;
import com.inova.guard.mdm.MainActivity;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {

    private static final String TAG = "DeviceAdminReceiver";

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

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(context, DeviceAdminReceiver.class);

        if (dpm == null) {
            Log.e(TAG, "DevicePolicyManager is null.");
            return;
        }

        // --- Políticas de seguridad ---
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER);
        dpm.setUninstallBlocked(adminComponent, context.getPackageName(), true);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS);
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER);
        // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES); // Esta política sigue comentada

        Log.d(TAG, "Provisioning complete and device policies applied.");
    }
}