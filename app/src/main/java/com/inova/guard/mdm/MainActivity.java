package com.inova.guard.mdm;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager; // Se añadió esta importación
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;
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
    private static final String ADMIN_MODE_CODE = "251983";
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
    private View adminPanel;
    private Button clearDeviceOwnerButton;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponentName;
    private SharedPreferences sharedPreferences;
    private Handler handler;
    private Runnable checkConnectionRunnable;
    private ScreenReceiver screenReceiver;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        adminPanel = findViewById(R.id.admin_panel);
        clearDeviceOwnerButton = findViewById(R.id.clear_device_owner_button);

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

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isDeviceOwner() && sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
                    Toast.makeText(MainActivity.this, "El dispositivo está bloqueado. Contacte a la administración.", Toast.LENGTH_SHORT).show();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.getOrDefault(
                            Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(
                            Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (Boolean.TRUE.equals(fineLocationGranted) && Boolean.TRUE.equals(coarseLocationGranted)) {
                        Toast.makeText(this, "Permisos de ubicación concedidos.", Toast.LENGTH_SHORT).show();
                    } else if (Boolean.TRUE.equals(coarseLocationGranted)) {
                        Toast.makeText(this, "Solo permiso de ubicación aproximada concedido.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Permisos de ubicación denegados.", Toast.LENGTH_SHORT).show();
                    }
                });

        requestLocationPermissions();
        getAndSendFCMToken();
        clearDeviceOwnerButton.setOnClickListener(v -> clearDeviceOwner());
    }

    private void requestLocationPermissions() {
        boolean fineLocationPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineLocationPermissionGranted || !coarseLocationPermissionGranted) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            Log.d(TAG, "Permisos de ubicación ya están concedidos.");
        }
    }

    private void enforceDevicePolicies() {
        if (isDeviceOwner()) {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, DeviceAdminReceiver.class);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET);
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Verificación de seguridad clave
        if (!isDeviceOwner()) {
            Log.e(TAG, "Device Owner status lost. Redirecting to Enrollment.");
            Intent enrollmentIntent = new Intent(this, EnrollmentActivity.class);
            startActivity(enrollmentIntent);
            finish();
            return;
        }

        // Método para re-aplicar políticas (código de un mensaje anterior)
        enforceDevicePolicies();

        checkDeviceStatus();
        handler.post(checkConnectionRunnable);
        screenReceiver = new ScreenReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(checkConnectionRunnable);
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
    }

    private void showScreen(boolean isLocked) {
        if (isLocked) {
            lockedLayout.setVisibility(View.VISIBLE);
            mainLayout.setVisibility(View.GONE);
            adminPanel.setVisibility(View.GONE);
            Glide.with(this).load(R.drawable.inova_guard_logo).into(logoImageView);
        } else {
            lockedLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
            adminPanel.setVisibility(View.GONE);
        }
    }

    private void getAndSendFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    sendTokenToServer(token);
                });
    }

    private void sendTokenToServer(String token) {
        String serverUrl = "https://tu-backend.com/api/register-device";
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("fcmToken", token);
            String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, "N/A");
            jsonBody.put("deviceId", deviceId);
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear el JSON para enviar el token", e);
            return;
        }
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(serverUrl)
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error al enviar el token al servidor", e);
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Token de FCM enviado exitosamente al servidor.");
                } else {
                    Log.e(TAG, "Error en la respuesta del servidor: " + response.code());
                }
                response.close();
            }
        });
    }

    private void checkDeviceStatus() {
        boolean isDeviceLocked = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);
        showScreen(isDeviceLocked);
        handleKioskMode(isDeviceLocked);
        if (isDeviceLocked) {
            String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "+58 412 1234567");
            contactPhoneTextView.setText("Teléfono: " + contactNumber);
            incorrectCodeTextView.setVisibility(View.GONE);
            unlockCodeEditText.setText("");
        } else {
            updatePaymentInfo();
        }
    }

    private void handleKioskMode(boolean isLocked) {
        if (isDeviceOwner()) {
            if (isLocked) {
                Log.d(TAG, "Activando modo Kiosk (Lock Task Mode)");
                startLockTask();
            } else {
                Log.d(TAG, "Desactivando modo Kiosk (Lock Task Mode)");
                stopLockTask();
            }
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
        if (enteredCode.equals(ADMIN_MODE_CODE) && isDeviceOwner()) {
            unlockCodeEditText.setText("");
            showAdminPanel();
            return;
        }
        if (enteredCode.isEmpty()) {
            incorrectCodeTextView.setText("Por favor, introduce el código.");
            incorrectCodeTextView.setVisibility(View.VISIBLE);
            return;
        }
        String storedUnlockCode = sharedPreferences.getString(Constants.PREF_UNLOCK_CODE, "");
        if (enteredCode.equals(storedUnlockCode)) {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
            runOnUiThread(() -> {
                unlockDevice();
                Toast.makeText(MainActivity.this, "Dispositivo desbloqueado correctamente.", Toast.LENGTH_SHORT).show();
                incorrectCodeTextView.setVisibility(View.GONE);
            });
        } else {
            runOnUiThread(() -> {
                incorrectCodeTextView.setText("Código de desbloqueo incorrecto.");
                incorrectCodeTextView.setVisibility(View.VISIBLE);
            });
        }
    }

    private void showAdminPanel() {
        lockedLayout.setVisibility(View.GONE);
        mainLayout.setVisibility(View.GONE);
        adminPanel.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Modo de administración activado.", Toast.LENGTH_SHORT).show();
    }

    private void clearDeviceOwner() {
        if (isDeviceOwner()) {
            devicePolicyManager.clearDeviceOwnerApp(getPackageName());
            Toast.makeText(this, "Dispositivo desvinculado del modo Device Owner. La aplicación se puede desinstalar.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        }
    }

    public void lockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();
            sharedPreferences.edit().putString(Constants.PREF_UNLOCK_CODE, "1234").apply();
            showScreen(true);
            Toast.makeText(this, "Dispositivo bloqueado por falta de pago.", Toast.LENGTH_LONG).show();
            if (isDeviceOwner()) {
                Log.d(TAG, "Iniciando modo de bloqueo de tarea.");
                startLockTask();
            }
        });
    }

    public void unlockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
            showScreen(false);
            Toast.makeText(this, "Dispositivo desbloqueado.", Toast.LENGTH_LONG).show();
            if (isDeviceOwner()) {
                Log.d(TAG, "Deteniendo modo de bloqueo de tarea.");
                stopLockTask();
            }
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
                if (isLocked) {
                    Log.d(TAG, "Screen on event received. Re-enforcing lock.");
                    Intent mainActivityIntent = new Intent(context, MainActivity.class);
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(mainActivityIntent);
                }
            }
        }
    }
}