package com.inova.guard.mdm;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import android.provider.Settings; // Importación necesaria para Settings.ACTION_USAGE_ACCESS_SETTINGS

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EnrollmentActivity extends AppCompatActivity {

    private static final String TAG = "EnrollmentActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private static final int REQUEST_CODE_USAGE_ACCESS = 2; // Nuevo código para acceso de uso

    private EditText serialNumberEditText;
    private EditText deviceBrandEditText;
    private EditText deviceModelEditText;
    private EditText imeiEditText;
    private Button enrollButton;
    private Button activateAdminButton;
    private Button grantUsageAccessButton;
    private Button startServiceButton;
    private Button verifyStatusButton;
    private TextView statusTextView;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponentName;
    private SharedPreferences sharedPreferences;

    // Indicadores de estado de cada paso
    private boolean isEnrolled = false;
    private boolean isAdminActive = false;
    private boolean isServiceRunning = false;
    private boolean hasPhoneStatePermission = false;
    private boolean hasUsageAccess = false; // Nuevo indicador

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrollment);

        serialNumberEditText = findViewById(R.id.serial_number_edit_text);
        deviceBrandEditText = findViewById(R.id.device_brand_edit_text);
        deviceModelEditText = findViewById(R.id.device_model_edit_text);
        imeiEditText = findViewById(R.id.imei_edit_text);
        enrollButton = findViewById(R.id.enroll_button);
        activateAdminButton = findViewById(R.id.activate_admin_button);
        grantUsageAccessButton = findViewById(R.id.grant_usage_access_button);
        startServiceButton = findViewById(R.id.start_service_button);
        verifyStatusButton = findViewById(R.id.verify_status_button);
        statusTextView = findViewById(R.id.status_text_view);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponentName = new ComponentName(this, DeviceAdminReceiver.class);
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);

        serialNumberEditText.setText(sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, ""));
        deviceBrandEditText.setText(Build.BRAND);
        deviceModelEditText.setText(Build.MODEL);

        enrollButton.setOnClickListener(v -> enrollDevice());

        activateAdminButton.setOnClickListener(v -> activateDeviceAdmin());

        grantUsageAccessButton.setOnClickListener(v -> grantUsageAccess());

        startServiceButton.setOnClickListener(v -> startMdmService());

        verifyStatusButton.setOnClickListener(v -> checkPermissionsAndStatus());

        // Al inicio, actualizamos los estados y verificamos si podemos redirigir
        updateSetupStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cuando la actividad vuelve al frente (después de conceder permisos, por ejemplo)
        updateSetupStatus();
    }

    private void updateSetupStatus() {
        isEnrolled = sharedPreferences.contains(Constants.PREF_DEVICE_ID);
        isAdminActive = devicePolicyManager.isAdminActive(adminComponentName);
        isServiceRunning = com.inova.guard.mdm.service.MdmService.isRunning;
        hasPhoneStatePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        // Verificar si tiene acceso de uso. Solo disponible en Lollipop (API 21) y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hasUsageAccess = isUsageAccessGranted(this);
        } else {
            hasUsageAccess = true; // Considerar concedido para versiones antiguas donde no aplica
        }

        checkPermissionsAndStatus(); // Actualiza el TextView de estado visualmente
        checkAllSetupCompleteAndRedirect(); // Intenta redirigir si todo está listo
    }

    private boolean isUsageAccessGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        }
        return false;
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                // Si la app es de sistema o Device Owner y necesita IMEI de forma privilegiada:
                // Manifest.permission.READ_PRIVILEGED_PHONE_STATE
        };

        boolean hasPermissions = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                hasPermissions = false;
                break;
            }
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            getDeviceIMEI();
            updateSetupStatus(); // Revisa el estado después de obtener IMEI
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permisos concedidos.", Toast.LENGTH_SHORT).show();
                getDeviceIMEI();
            } else {
                Toast.makeText(this, "Permisos denegados. Algunas funcionalidades pueden no estar disponibles.", Toast.LENGTH_LONG).show();
            }
            updateSetupStatus(); // Revisa el estado después de los permisos
        }
    }

    private void getDeviceIMEI() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        imeiEditText.setText(telephonyManager.getImei());
                    } else {
                        imeiEditText.setText(telephonyManager.getDeviceId());
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException when getting IMEI: " + e.getMessage());
                    imeiEditText.setText("No se pudo obtener IMEI (permiso denegado o API > 28).");
                }
            }
        } else {
            imeiEditText.setText("Permiso READ_PHONE_STATE no concedido.");
        }
    }

    private void enrollDevice() {
        String serialNumber = serialNumberEditText.getText().toString().trim();
        String brand = deviceBrandEditText.getText().toString().trim();
        String model = deviceModelEditText.getText().toString().trim();
        String imei = imeiEditText.getText().toString().trim();

        if (serialNumber.isEmpty() || brand.isEmpty() || model.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos requeridos (excepto IMEI si es opcional).", Toast.LENGTH_LONG).show();
            return;
        }

        String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, null);
        if (deviceId == null || deviceId.isEmpty()) { // Si ya hay un ID, no generarlo de nuevo.
            deviceId = UUID.randomUUID().toString();
            sharedPreferences.edit().putString(Constants.PREF_DEVICE_ID, deviceId).apply();
        }

        sharedPreferences.edit()
                .putString(Constants.PREF_SERIAL_NUMBER, serialNumber)
                .putString(Constants.PREF_DEVICE_BRAND, brand)
                .putString(Constants.PREF_DEVICE_MODEL, model)
                .putString(Constants.PREF_DEVICE_IMEI, imei)
                .apply();

        ApiUtils.enrollDevice(this, deviceId, serialNumber, brand, model, imei, new ApiUtils.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        String status = jsonResponse.getString("status");
                        String message = jsonResponse.getString("message");

                        Toast.makeText(EnrollmentActivity.this, message, Toast.LENGTH_SHORT).show();
                        statusTextView.setText("Estado: " + message);
                        if ("success".equals(status) || "ok".equals(status)) {
                            Log.d(TAG, "Dispositivo enrolado exitosamente.");
                            isEnrolled = true; // Actualiza el indicador de estado
                            updateSetupStatus(); // Intenta redirigir después del enrolamiento
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing enrollment response: " + e.getMessage());
                        Toast.makeText(EnrollmentActivity.this, "Error en la respuesta del servidor.", Toast.LENGTH_SHORT).show();
                        statusTextView.setText("Estado: Error en la respuesta del servidor.");
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(EnrollmentActivity.this, "Error de enrolamiento: " + errorMessage, Toast.LENGTH_LONG).show();
                    statusTextView.setText("Estado: Error de conexión: " + errorMessage);
                });
            }
        });
    }

    private void activateDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "InovaGuard necesita permisos de administrador para proteger el dispositivo y asegurar los pagos.");
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
    }

    private void grantUsageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(intent, REQUEST_CODE_USAGE_ACCESS); // Usar startActivityForResult
        } else {
            Toast.makeText(this, "Acceso de uso no disponible en esta versión de Android.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startMdmService() {
        Intent serviceIntent = new Intent(this, com.inova.guard.mdm.service.MdmService.class);
        startService(serviceIntent);
        Toast.makeText(this, "Servicio InovaGuard iniciado.", Toast.LENGTH_SHORT).show();
        isServiceRunning = true; // Actualiza el indicador de estado
        updateSetupStatus(); // Intenta redirigir después de iniciar el servicio
    }

    private void checkPermissionsAndStatus() {
        StringBuilder status = new StringBuilder();

        // Actualiza el estado visual de los botones y el texto
        enrollButton.setEnabled(!isEnrolled);
        activateAdminButton.setEnabled(!isAdminActive);
        grantUsageAccessButton.setEnabled(!hasUsageAccess);
        startServiceButton.setEnabled(!isServiceRunning);

        if (isEnrolled) {
            status.append("✔️ Dispositivo Enrolado (ID: ").append(sharedPreferences.getString(Constants.PREF_DEVICE_ID, "N/A")).append(")\n");
        } else {
            status.append("❌ Dispositivo NO Enrolado.\n");
        }

        if (isAdminActive) {
            status.append("✔️ Administrador de dispositivo activo.\n");
        } else {
            status.append("❌ Administrador de dispositivo INACTIVO.\n");
        }

        if (hasPhoneStatePermission) {
            status.append("✔️ Permiso de estado del teléfono concedido.\n");
        } else {
            status.append("❌ Permiso de estado del teléfono DENNEGADO.\n");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (hasUsageAccess) {
                status.append("✔️ Acceso de uso concedido.\n");
            } else {
                status.append("❌ Acceso de uso NO concedido.\n");
            }
        } else {
            status.append("ℹ️ Acceso de uso no aplicable (API < 21).\n");
        }

        if (isServiceRunning) {
            status.append("✔️ Servicio MDM en ejecución.\n");
        } else {
            status.append("❌ Servicio MDM NO en ejecución.\n");
        }

        statusTextView.setText("Estado de Configuración:\n" + status.toString());
    }

    // Nuevo método para verificar si todos los pasos están completos y redirigir
    private void checkAllSetupCompleteAndRedirect() {
        if (isEnrolled && isAdminActive && isServiceRunning && hasPhoneStatePermission && hasUsageAccess) {
            Log.d(TAG, "Todos los pasos de configuración completados. Redirigiendo a MainActivity.");
            Intent mainIntent = new Intent(EnrollmentActivity.this, MainActivity.class);
            startActivity(mainIntent);
            finish(); // Cierra EnrollmentActivity para que el usuario no pueda volver con el botón de atrás
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Administrador de dispositivo activado.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Activación de administrador de dispositivo cancelada.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_USAGE_ACCESS) {
            // No hay resultCode directo para Settings.ACTION_USAGE_ACCESS_SETTINGS,
            // así que simplemente volvemos a verificar el estado
            Log.d(TAG, "Volviendo de la configuración de acceso de uso.");
        }
        updateSetupStatus(); // Siempre actualiza el estado después de un resultado de actividad
    }
}
