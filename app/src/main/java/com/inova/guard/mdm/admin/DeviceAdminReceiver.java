package com.inova.guard.mdm.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
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
        // Bloquea la pantalla para evitar que el usuario desactive el administrador de dispositivo
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponentName = new ComponentName(context, DeviceAdminReceiver.class);

        // Verificamos si aún somos administradores antes de intentar bloquear
        if (devicePolicyManager != null && devicePolicyManager.isAdminActive(adminComponentName)) {
            devicePolicyManager.lockNow();

            // Inicia la actividad principal para forzar el modo de tarea bloqueada
            Intent lockIntent = new Intent(context, MainActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(lockIntent);
        }

        // Devuelve el mensaje de advertencia
        return "Advertencia: Deshabilitar InovaGuard puede resultar en el bloqueo del dispositivo si existen pagos pendientes.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Administrador de Dispositivo InovaGuard desactivado.", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Administrador de Dispositivo InovaGuard desactivado.");

        // Notificar al servidor que el Device Admin ha sido desactivado
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
}