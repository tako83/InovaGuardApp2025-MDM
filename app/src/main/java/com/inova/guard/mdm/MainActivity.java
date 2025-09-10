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

import com.bumptech.glide.Glide;
import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.service.MdmService;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
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
    public static MainActivity instance;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, null);

        if (deviceId == null || deviceId.isEmpty()) {
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
        });

        handler = new Handler();
        checkConnectionRunnable = () -> {
            checkDeviceStatus();
            handler.postDelayed(checkConnectionRunnable, Constants.CONNECTION_CHECK_INTERVAL);
        };

        if (isDeviceOwner()) {
            devicePolicyManager.setLockTaskPackages(adminComponentName, new String[]{getPackageName()});
        }

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
                    Toast.makeText(MainActivity.this, "El dispositivo está bloqueado. Contacte a la administración.", Toast.LENGTH_SHORT).show();
                } else {
                    this.setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    this.setEnabled(true);
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
                            Manifest.permission.ACCESS_COARSE_LOCATION,false);
                    if (fineLocationGranted != null && fineLocationGranted) {
                        Toast.makeText(this, "Permiso de ubicación concedido.", Toast.LENGTH_SHORT).show();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        Toast.makeText(this, "Permiso de ubicación aproximada concedido.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Permisos de ubicación denegados.", Toast.LENGTH_SHORT).show();
                    }
                });

        requestLocationPermissions();
        updatePaymentInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    private void requestLocationPermissions() {
        boolean fineLocationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineLocationPermission || !coarseLocationPermission) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
            Glide.with(this).load(R.drawable.inova_guard_logo).into(logoImageView);
            if (isDeviceOwner()) {
                startLockTask();
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

    public void showReminderMessage(String title, String message) {
        runOnUiThread(() -> {
            showScreen(false);
            updatePaymentInfo(message);
            Toast.makeText(this, "¡Recordatorio de pago recibido!", Toast.LENGTH_LONG).show();
        });
    }

    private void updatePaymentInfo(String reminderMessage) {
        String nextPaymentDate = sharedPreferences.getString(Constants.PREF_NEXT_PAYMENT_DATE, "N/A");
        String amountDue = sharedPreferences.getString(Constants.PREF_AMOUNT_DUE, "N/A");
        String amountPaid = sharedPreferences.getString(Constants.PREF_AMOUNT_PAID, "N/A");
        String deviceBrand = sharedPreferences.getString(Constants.PREF_DEVICE_BRAND, "Marca");
        String deviceModel = sharedPreferences.getString(Constants.PREF_DEVICE_MODEL, "Modelo");
        String paymentInstructions = sharedPreferences.getString(Constants.PREF_PAYMENT_INSTRUCTIONS, "Contacte a la administración para más detalles.");
        String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "N/A");
        if (reminderMessage != null && !reminderMessage.isEmpty()) {
            paymentInstructions = reminderMessage;
        }
        nextPaymentDateTextView.setText(nextPaymentDate);
        amountDueTextView.setText(amountDue);
        amountPaidTextView.setText(amountPaid);
        deviceInfoTextView.setText(deviceBrand + " " + deviceModel);
        paymentInstructionsTextView.setText(paymentInstructions);
        contactPhoneMainTextView.setText("Teléfono: " + contactNumber);
    }

    private void updatePaymentInfo() {
        updatePaymentInfo(null);
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
                        sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
                        runOnUiThread(() -> {
                            unlockDevice();
                            incorrectCodeTextView.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        runOnUiThread(() -> {
                            incorrectCodeTextView.setText(message);
                            incorrectCodeTextView.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> {
                        incorrectCodeTextView.setText("Error en la respuesta del servidor.");
                        incorrectCodeTextView.setVisibility(View.VISIBLE);
                    });
                }
            }
            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    incorrectCodeTextView.setText("Error de conexión al servidor: " + errorMessage);
                    incorrectCodeTextView.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    public void lockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();
            showScreen(true);
            Toast.makeText(this, "Dispositivo bloqueado por falta de pago.", Toast.LENGTH_LONG).show();
        });
    }

    public void unlockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
            showScreen(false);
            Toast.makeText(this, "Dispositivo desbloqueado.", Toast.LENGTH_LONG).show();
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
                    Intent mainActivityIntent = new Intent(context, MainActivity.class);
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(mainActivityIntent);
                }
            }
        }
    }
}