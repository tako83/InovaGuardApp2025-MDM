package com.inova.guard.mdm;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

// Nuevas importaciones para los permisos de ubicación
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import android.os.Build;
import android.annotation.SuppressLint;

import com.bumptech.glide.Glide;
import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.service.MdmService;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    public static final String ACTION_COMMAND_RECEIVED = "com.inova.guard.mdm.ACTION_COMMAND_RECEIVED";

    private View lockedLayout;
    private ImageView logoImageView;
    private TextView lockedMessageTextView;
    private EditText unlockCodeEditText;
    private Button unlockButton;
    private TextView incorrectCodeTextView;
    private TextView contactPhoneTextView;
    private View mainLayout;
    private TextView nextPaymentDateTextView;
    private TextView amountDueTextView;
    private TextView amountPaidTextView;
    private TextView deviceInfoTextView;
    private TextView paymentInstructionsTextView;
    private Button contactAdminButton;
    private TextView contactPhoneMainTextView;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponentName;
    private SharedPreferences sharedPreferences;
    private Handler handler;
    private Runnable checkConnectionRunnable;
    private ScreenReceiver screenReceiver;
    private CommandReceiver commandReceiver;

    // Declaración del lanzador de permisos
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // AÑADIDO: Lógica para manejar el aprovisionamiento
        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null &&
                intent.getAction().equals(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)) {

            Log.d(TAG, "Recibiendo el intent de aprovisionamiento en la actividad principal.");

            sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean provisioningCompleted = sharedPreferences.getBoolean(Constants.PREF_PROVISIONING_COMPLETE, false);

            if (!provisioningCompleted) {
                Bundle provisioningExtras = intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
                if (provisioningExtras != null) {
                    String clientName = provisioningExtras.getString("client_name");
                    String clientEmail = provisioningExtras.getString("client_email");
                    String serialFromQR = provisioningExtras.getString("serial_number");

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(Constants.PREF_SERIAL_NUMBER, serialFromQR);
                    editor.putString(Constants.PREF_CLIENT_NAME, clientName);
                    editor.putString(Constants.PREF_CLIENT_EMAIL, clientEmail);
                    editor.putBoolean(Constants.PREF_PROVISIONING_COMPLETE, true);
                    editor.apply();

                    Log.d(TAG, "Datos de aprovisionamiento guardados en SharedPreferences desde MainActivity.");

                    try {
                        JSONObject enrollmentData = new JSONObject();
                        enrollmentData.put("client_name", clientName);
                        enrollmentData.put("client_email", clientEmail);
                        enrollmentData.put("serial_number", serialFromQR);
                        enrollmentData.put("device_brand_info", Build.BRAND);
                        enrollmentData.put("device_model_info", Build.MODEL);
                        enrollmentData.put("device_type", "smartphone");

                        ApiUtils.enrollDevice(this, enrollmentData, new ApiUtils.ApiCallback() {
                            @Override
                            public void onSuccess(String response) {
                                Log.d(TAG, "Dispositivo registrado con éxito: " + response);
                            }
                            @Override
                            public void onFailure(String errorMessage) {
                                Log.e(TAG, "Fallo al registrar el dispositivo: " + errorMessage);
                            }
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "Error al crear el objeto JSON para el enrolamiento: " + e.getMessage());
                    }

                } else {
                    Log.e(TAG, "El bundle de aprovisionamiento es nulo. Esto es un error del sistema.");
                }
            } else {
                Log.d(TAG, "Proceso de aprovisionamiento ya completado. No se tomará ninguna acción.");
            }
        }

        // El resto del código que ya tenías se mantiene intacto
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, null);

        if (deviceId == null || deviceId.isEmpty()) {
            Log.d(TAG, "Device not enrolled. Redirecting to EnrollmentActivity.");
            Intent enrollmentIntent = new Intent(this, EnrollmentActivity.class);
            startActivity(enrollmentIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        lockedLayout = findViewById(R.id.locked_layout);
        logoImageView = findViewById(R.id.logo_image_view);
        lockedMessageTextView = findViewById(R.id.locked_message_text_view);
        unlockCodeEditText = findViewById(R.id.unlock_code_edit_text);
        unlockButton = findViewById(R.id.unlock_button);
        incorrectCodeTextView = findViewById(R.id.incorrect_code_text_view);
        contactPhoneTextView = findViewById(R.id.contact_phone_text_view);

        mainLayout = findViewById(R.id.main_layout);
        nextPaymentDateTextView = findViewById(R.id.next_payment_date_text_view);
        amountDueTextView = findViewById(R.id.amount_due_text_view);
        amountPaidTextView = findViewById(R.id.amount_paid_text_view);
        deviceInfoTextView = findViewById(R.id.device_info_text_view);
        paymentInstructionsTextView = findViewById(R.id.payment_instructions_text_view);
        contactAdminButton = findViewById(R.id.contact_admin_button);
        contactPhoneMainTextView = findViewById(R.id.contact_phone_main_text_view);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponentName = new ComponentName(this, DeviceAdminReceiver.class);

        if (!MdmService.isRunning) {
            Intent serviceIntent = new Intent(this, MdmService.class);
            startService(serviceIntent);
        }

        unlockButton.setOnClickListener(v -> attemptUnlock());

        contactAdminButton.setOnClickListener(v -> {
            String phoneNumber = contactPhoneMainTextView.getText().toString().replace("Teléfono: ", "");
            if (!phoneNumber.isEmpty() && !phoneNumber.equals("N/A")) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(android.net.Uri.parse("tel:" + phoneNumber));
                startActivity(dialIntent);
            } else {
                Toast.makeText(this, "Número de contacto no disponible.", Toast.LENGTH_SHORT).show();
            }
        });

        contactPhoneTextView.setOnClickListener(v -> {
            String phoneNumber = contactPhoneTextView.getText().toString().replace("Teléfono: ", "");
            if (!phoneNumber.isEmpty()) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(android.net.Uri.parse("tel:" + phoneNumber));
                startActivity(dialIntent);
            }
            // AÑADIDO: Manejo del Intent en onCreate
        });

        handler = new Handler();
        checkConnectionRunnable = new Runnable() {
            @Override
            public void run() {
                checkDeviceStatus();
                handler.postDelayed(this, Constants.CONNECTION_CHECK_INTERVAL);
            }
        };

        if (devicePolicyManager != null && adminComponentName != null && devicePolicyManager.isAdminActive(adminComponentName)) {
            Log.d(TAG, "Device Admin is active. Attempting to restrict settings.");
        } else {
            Log.w(TAG, "Device Admin is not active. App may be easily uninstalled.");
        }

        if (isDeviceOwner()) {
            Log.d(TAG, "Running in Device Owner mode. App can be set as Kiosk.");
            devicePolicyManager.setLockTaskPackages(adminComponentName, new String[]{getPackageName()});
        }

        // Se reemplaza `onBackPressed` con el nuevo `OnBackPressedDispatcher`
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled */) {
            @Override
            public void handleOnBackPressed() {
                if (sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
                    // Si el dispositivo está bloqueado, se consume el evento y se muestra un Toast.
                    Toast.makeText(MainActivity.this, "El dispositivo está bloqueado. Contacte a la administración.", Toast.LENGTH_SHORT).show();
                } else {
                    // Si no está bloqueado, se desactiva el callback y se permite el comportamiento por defecto.
                    this.setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    this.setEnabled(true);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        // Inicializa el lanzador de permisos
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.getOrDefault(
                            Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(
                            Manifest.permission.ACCESS_COARSE_LOCATION,false);
                    if (fineLocationGranted != null && fineLocationGranted) {
                        // Permiso de ubicación precisa concedido
                        Toast.makeText(this, "Permiso de ubicación concedido.", Toast.LENGTH_SHORT).show();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        // Solo permiso de ubicación aproximada concedido
                        Toast.makeText(this, "Permiso de ubicación aproximada concedido.", Toast.LENGTH_SHORT).show();
                    } else {
                        // Permisos denegados, puedes mostrar un mensaje al usuario
                        Toast.makeText(this, "Permisos de ubicación denegados.", Toast.LENGTH_SHORT).show();
                    }
                });

        // Llama al método para solicitar los permisos
        requestLocationPermissions();

        // AÑADIDO: Manejo del Intent en onCreate para procesar el recordatorio de pago
        checkIntentForPaymentReminder(getIntent());
    }

    // AÑADIDO: Manejo del Intent cuando la actividad ya está en memoria
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkIntentForPaymentReminder(intent);
    }

    // AÑADIDO: Método para centralizar la lógica de procesamiento del Intent
    private void checkIntentForPaymentReminder(Intent intent) {
        // AÑADIDO: Log para verificar si el Intent tiene la bandera
        if (intent != null) {
            boolean hasExtra = intent.hasExtra(Constants.EXTRA_SHOW_PAYMENT_REMINDER);
            Log.d(TAG, "checkIntentForPaymentReminder: Intent recibido, tiene el extra: " + hasExtra);
        }

        if (intent != null && intent.hasExtra(Constants.EXTRA_SHOW_PAYMENT_REMINDER)) {
            boolean showPaymentReminder = intent.getBooleanExtra(Constants.EXTRA_SHOW_PAYMENT_REMINDER, false);
            if (showPaymentReminder) {
                Log.d(TAG, "Intent con recordatorio de pago recibido y procesado.");
                sharedPreferences.edit()
                        .putBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, true)
                        .apply();
                checkDeviceStatus();
            }
        }
    }

    private void requestLocationPermissions() {
        boolean fineLocationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean coarseLocationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fineLocationPermission || !coarseLocationPermission) {
            // Los permisos no han sido concedidos, los solicitamos
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            // Los permisos ya están concedidos
            Log.d(TAG, "Permisos de ubicación ya están concedidos.");
        }
    }

    // Se suprime el aviso de Lint "UnspecifiedRegisterReceiverFlag"
    // Es necesario para la compatibilidad con versiones de Android anteriores a la API 26
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        checkDeviceStatus();
        handler.post(checkConnectionRunnable);

        screenReceiver = new ScreenReceiver();
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, screenFilter);

        commandReceiver = new CommandReceiver();
        IntentFilter commandFilter = new IntentFilter(ACTION_COMMAND_RECEIVED);

        // Esta es la solución que se adapta a tu minSdk de 24 y evita el error.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(commandReceiver, commandFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, commandFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(checkConnectionRunnable);
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
        if (commandReceiver != null) {
            unregisterReceiver(commandReceiver);
        }
    }

    private void showScreen(boolean isLocked) {
        boolean showPaymentReminder = sharedPreferences.getBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, false);
        // AÑADIDO: Log para verificar los valores justo antes de mostrar la pantalla
        Log.d(TAG, "showScreen: isLocked=" + isLocked + ", showPaymentReminder=" + showPaymentReminder);


        if (isLocked) {
            lockedLayout.setVisibility(View.VISIBLE);
            mainLayout.setVisibility(View.GONE);
            Glide.with(this).load(R.drawable.inova_guard_logo).into(logoImageView);
            if (isDeviceOwner()) {
                startLockTask();
            }
        } else if (showPaymentReminder) {
            lockedLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
            if (isDeviceOwner()) {
                stopLockTask();
            }
        } else {
            lockedLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
            if (isDeviceOwner()) {
                stopLockTask();
            }
        }
    }

    private void checkDeviceStatus() {
        boolean isDeviceLocked = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);
        showScreen(isDeviceLocked);
        if (isDeviceLocked) {
            String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "+58 412 1234567");
            contactPhoneTextView.setText("Teléfono: " + contactNumber);
            incorrectCodeTextView.setVisibility(View.GONE);
            unlockCodeEditText.setText("");
        } else {
            updatePaymentInfo();
        }
    }

    private void updatePaymentInfo() {
        String nextPaymentDate = sharedPreferences.getString(Constants.PREF_NEXT_PAYMENT_DATE, "31/12/2025");
        String amountDue = sharedPreferences.getString(Constants.PREF_AMOUNT_DUE, "$0.00");
        String amountPaid = sharedPreferences.getString(Constants.PREF_AMOUNT_PAID, "$0.00");
        String deviceBrand = sharedPreferences.getString(Constants.PREF_DEVICE_BRAND, "Marca");
        String deviceModel = sharedPreferences.getString(Constants.PREF_DEVICE_MODEL, "Modelo");
        String paymentInstructions = sharedPreferences.getString(Constants.PREF_PAYMENT_INSTRUCTIONS, "Contacte a la administración para más detalles.");
        String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "N/A");

        nextPaymentDateTextView.setText(nextPaymentDate);
        amountDueTextView.setText(amountDue);
        amountPaidTextView.setText(amountPaid);
        deviceInfoTextView.setText(deviceBrand + " " + deviceModel);
        paymentInstructionsTextView.setText(paymentInstructions);
        contactPhoneMainTextView.setText("Teléfono: " + contactNumber);
    }

    private void attemptUnlock() {
        String enteredCode = unlockCodeEditText.getText().toString().trim();
        if (enteredCode.isEmpty()) {
            incorrectCodeTextView.setText("Por favor, introduce el código.");
            incorrectCodeTextView.setVisibility(View.VISIBLE);
            return;
        }

        String serialNumber = sharedPreferences.getString(Constants.PREF_SERIAL_NUMBER, "unknown");

        ApiUtils.verifyUnlockCode(this, serialNumber, enteredCode, new ApiUtils.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    boolean success = jsonResponse.getBoolean("success");
                    String message = jsonResponse.getString("message");

                    if (success) {
                        sharedPreferences.edit()
                                .putBoolean(Constants.PREF_IS_LOCKED, false)
                                .putBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, false)
                                .apply();
                        runOnUiThread(() -> {
                            unlockDevice();
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            incorrectCodeTextView.setVisibility(View.GONE);
                        });
                    } else {
                        runOnUiThread(() -> {
                            incorrectCodeTextView.setText(message);
                            incorrectCodeTextView.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing unlock response: " + e.getMessage());
                    runOnUiThread(() -> {
                        incorrectCodeTextView.setText("Error en la respuesta del servidor.");
                        incorrectCodeTextView.setVisibility(View.VISIBLE);
                    });
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Unlock API call failed: " + errorMessage);
                runOnUiThread(() -> {
                    incorrectCodeTextView.setText("Error de conexión al servidor: " + errorMessage);
                    incorrectCodeTextView.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    public void lockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREF_IS_LOCKED, true)
                    .putBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, false)
                    .apply();
            showScreen(true);
            Toast.makeText(this, "Dispositivo bloqueado por falta de pago.", Toast.LENGTH_LONG).show();
        });
    }

    public void unlockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREF_IS_LOCKED, false)
                    .putBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, false)
                    .apply();
            showScreen(false);
            Toast.makeText(this, "Dispositivo desbloqueado.", Toast.LENGTH_LONG).show();
        });
    }

    public void showPaymentReminder() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, true).apply();
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
            checkDeviceStatus();
            Toast.makeText(this, "Recordatorio de pago activado.", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean isDeviceOwner() {
        return devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName());
    }

    public static class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_ON) || Objects.equals(intent.getAction(), Intent.ACTION_USER_PRESENT)) {
                SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
                boolean isLocked = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);
                boolean isPaymentReminder = sharedPreferences.getBoolean(Constants.PREF_SHOW_PAYMENT_REMINDER, false);
                if (isLocked || isPaymentReminder) {
                    Log.d(TAG, "Screen on event received. Re-enforcing state.");
                    Intent mainActivityIntent = new Intent(context, MainActivity.class);
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(mainActivityIntent);
                }
            }
        }
    }

    private class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_COMMAND_RECEIVED.equals(intent.getAction())) {
                String command = intent.getStringExtra("command");
                if (command != null) {
                    Log.d(TAG, "Command received via broadcast: " + command);
                    switch (command) {
                        case "lock":
                            lockDevice();
                            break;
                        case "unlock":
                            unlockDevice();
                            break;
                        case "show_payment_reminder":
                            showPaymentReminder();
                            break;
                    }
                }
            }
        }
    }
}
