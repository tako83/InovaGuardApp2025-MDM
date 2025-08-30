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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.service.MdmService;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class EnrollmentActivity extends AppCompatActivity {

    private static final String TAG = "EnrollmentActivity";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponentName;
    private SharedPreferences sharedPreferences;

    private Button btnActivateAdmin;
    private Button btnEnroll;

    private EditText etSerialNumber;
    private EditText etDeviceType;
    private EditText etDeviceBrandModel;
    private EditText etClientName;
    private EditText etClientEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrollment);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponentName = new ComponentName(this, DeviceAdminReceiver.class);
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);

        btnActivateAdmin = findViewById(R.id.btn_activate_admin);
        btnEnroll = findViewById(R.id.btn_enroll);
        etSerialNumber = findViewById(R.id.et_serial);
        etDeviceType = findViewById(R.id.et_device_type);
        etDeviceBrandModel = findViewById(R.id.et_device_brand_model);

        etClientName = findViewById(R.id.et_client_name);
        etClientEmail = findViewById(R.id.et_client_email);

        checkPermissionsAndAdminStatus();

        btnActivateAdmin.setOnClickListener(v -> activateDeviceAdmin());
        btnEnroll.setOnClickListener(v -> attemptEnrollment());
    }

    private void checkPermissionsAndAdminStatus() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_CODE);
        } else {
            updateButtonState();
        }
    }

    private void updateButtonState() {
        boolean isAdminActive = devicePolicyManager.isAdminActive(adminComponentName);
        btnActivateAdmin.setEnabled(!isAdminActive);
        btnEnroll.setEnabled(isAdminActive);
    }

    private void activateDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(adminComponentName)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "InovaGuard necesita ser un administrador del dispositivo para poder gestionar las funciones MDM, como el bloqueo de pantalla.");
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            updateButtonState();
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Administrador del dispositivo activado.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Activación del administrador del dispositivo cancelada.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateButtonState();
            } else {
                Toast.makeText(this, "Permiso de teléfono denegado. La app no puede enrolarse.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void attemptEnrollment() {
        if (!devicePolicyManager.isAdminActive(adminComponentName)) {
            Toast.makeText(this, "Por favor, activa el administrador del dispositivo primero.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] deviceIdentifier = {null};
        final String[] identifierType = {null};

        // Valores por defecto
        String imei = "unknown";
        String serialNumber = Build.SERIAL;

        // Lógica condicional para asignar el identificador principal
        if (devicePolicyManager.isDeviceOwnerApp(getPackageName())) {
            // Escenario 1: Device Owner (se usa IMEI)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de teléfono no concedido. No se puede enrolar con IMEI.", Toast.LENGTH_LONG).show();
                return;
            }
            imei = getImei();
            deviceIdentifier[0] = imei;
            identifierType[0] = "imei";
            Log.d(TAG, "Enrolamiento como Device Owner. Usando IMEI: " + deviceIdentifier[0]);
        } else {
            // Escenario 2: Device Admin (se usa serial)
            deviceIdentifier[0] = serialNumber;
            identifierType[0] = "serial_number";
            Log.d(TAG, "Enrolamiento como Device Admin. Usando número de serie: " + deviceIdentifier[0]);
        }

        // Si el identificador es 'unknown' o 'undefined', usar el serial del UI o un UUID de fallback
        if ("unknown".equals(deviceIdentifier[0]) || "undefined".equals(deviceIdentifier[0])) {
            String manualSerial = etSerialNumber.getText().toString().trim();
            if (!manualSerial.isEmpty()) {
                deviceIdentifier[0] = manualSerial;
                Log.d(TAG, "Identificador del dispositivo no disponible. Usando el serial del UI: " + deviceIdentifier[0]);
            } else {
                deviceIdentifier[0] = "EMULATOR_" + UUID.randomUUID().toString();
                Log.d(TAG, "Identificador no disponible y serial del UI vacío. Usando un UUID temporal: " + deviceIdentifier[0]);
            }
        }

        String typeText = etDeviceType.getText().toString().trim();
        String brandModelText = etDeviceBrandModel.getText().toString().trim();
        String clientName = etClientName.getText().toString().trim();
        String clientEmail = etClientEmail.getText().toString().trim();

        if (typeText.isEmpty() || brandModelText.isEmpty() || clientName.isEmpty() || clientEmail.isEmpty()) {
            Toast.makeText(this, "Todos los campos son obligatorios.", Toast.LENGTH_SHORT).show();
            return;
        }

        String brand = "";
        String modelName = "";
        String[] parts = brandModelText.split(" ", 2);
        if (parts.length >= 1) {
            brand = parts[0];
        }
        if (parts.length >= 2) {
            modelName = parts[1];
        }

        try {
            JSONObject deviceData = new JSONObject();
            // Siempre enviar ambos campos al servidor
            deviceData.put("imei", imei);
            deviceData.put("serial_number", serialNumber);

            // También enviar el identificador principal para retrocompatibilidad si el servidor lo necesita
            deviceData.put(identifierType[0], deviceIdentifier[0]);

            deviceData.put("device_type", typeText);
            deviceData.put("brand", brand);
            deviceData.put("model_name", modelName);
            deviceData.put("client_name", clientName);
            deviceData.put("client_email", clientEmail);
            deviceData.put("contact_phone", "");
            deviceData.put("company_logo_url", "");
            deviceData.put("unlock_code", "");
            deviceData.put("message", "");
            deviceData.put("device_brand_info", brand);
            deviceData.put("device_model_info", modelName);


            ApiUtils.enrollDevice(this, deviceData, new ApiUtils.ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        boolean success = jsonResponse.getBoolean("success");
                        String message = jsonResponse.getString("message");
                        runOnUiThread(() -> Toast.makeText(EnrollmentActivity.this, message, Toast.LENGTH_LONG).show());

                        if (success) {
                            String deviceId = jsonResponse.getString("device_id");
                            String contactPhone = jsonResponse.optString("contact_phone", "");

                            sharedPreferences.edit()
                                    .putBoolean(Constants.PREF_IS_ENROLLED, true)
                                    .putString(Constants.PREF_DEVICE_ID, deviceId)
                                    .putString(Constants.PREF_SERIAL_NUMBER, deviceIdentifier[0])
                                    .putString(Constants.PREF_CONTACT_PHONE, contactPhone)
                                    .apply();
                            Intent serviceIntent = new Intent(EnrollmentActivity.this, MdmService.class);
                            startService(serviceIntent);

                            Intent mainIntent = new Intent(EnrollmentActivity.this, MainActivity.class);
                            startActivity(mainIntent);
                            finish();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(EnrollmentActivity.this, "Error de datos del servidor: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "Enrollment API call failed: " + errorMessage);
                    runOnUiThread(() -> Toast.makeText(EnrollmentActivity.this, "Error de conexión: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error durante el enrolamiento: " + e.getMessage());
            Toast.makeText(this, "Error al obtener información del dispositivo.", Toast.LENGTH_LONG).show();
        }
    }

    private String getImei() {
        String imei = "unknown";
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    imei = telephonyManager.getImei();
                } else {
                    imei = telephonyManager.getDeviceId();
                }
            }
        }
        return imei != null ? imei : "unknown";
    }

    private boolean isTablet() {
        return (getResources().getConfiguration().screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE;
    }
}