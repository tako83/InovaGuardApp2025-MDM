package com.inova.guard.mdm;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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

import com.bumptech.glide.Glide;
import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.service.MdmService;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

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
    private Runnable checkStatusRunnable;
    private boolean isTelevision;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Agregamos el nuevo Callback para el botón de "atrás"
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
                    Toast.makeText(MainActivity.this, "El dispositivo está bloqueado. Contacte a la administración.", Toast.LENGTH_SHORT).show();
                } else {
                    this.remove();
                    MainActivity.super.onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, null);

        if (deviceId == null || deviceId.isEmpty()) {
            Log.d(TAG, "Device not enrolled. Redirecting to EnrollmentActivity.");
            Intent enrollmentIntent = new Intent(this, EnrollmentActivity.class);
            startActivity(enrollmentIntent);
            finish();
            return;
        }

        isTelevision = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        Log.d(TAG, "isTelevision: " + isTelevision);

        // A partir de aquí, la lógica de UI se gestiona en un solo método
        setInitialLayout();
        initializeViews();

        if (!MdmService.isRunning) {
            Intent serviceIntent = new Intent(this, MdmService.class);
            startService(serviceIntent);
        }

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponentName = new ComponentName(this, DeviceAdminReceiver.class);

        if (unlockButton != null) {
            unlockButton.setOnClickListener(v -> attemptUnlock());
        }

        if (contactAdminButton != null) {
            contactAdminButton.setOnClickListener(v -> {
                String phoneNumber = contactPhoneMainTextView.getText().toString().replace("Teléfono: ", "");
                if (!phoneNumber.isEmpty() && !phoneNumber.equals("N/A")) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + phoneNumber));
                    startActivity(dialIntent);
                } else {
                    Toast.makeText(this, "Número de contacto no disponible.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (contactPhoneTextView != null) {
            contactPhoneTextView.setOnClickListener(v -> {
                String phoneNumber = contactPhoneTextView.getText().toString().replace("Teléfono: ", "");
                if (!phoneNumber.isEmpty()) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + phoneNumber));
                    startActivity(dialIntent);
                }
            });
        }

        handler = new Handler();
        checkStatusRunnable = () -> {
            refreshScreenState();
            handler.postDelayed(checkStatusRunnable, Constants.CONNECTION_CHECK_INTERVAL);
        };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshScreenState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshScreenState();
        handler.post(checkStatusRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(checkStatusRunnable);
    }

    private void setInitialLayout() {
        if (isTelevision) {
            setContentView(R.layout.main_layout_tv);
        } else {
            setContentView(R.layout.activity_main);
        }
    }

    private void initializeViews() {
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
    }

    private void refreshScreenState() {
        boolean isLocked = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);
        boolean showPaymentReminder = getIntent().getBooleanExtra(Constants.EXTRA_SHOW_PAYMENT_REMINDER, false);

        if (isLocked) {
            showLockedScreen();
        } else if (showPaymentReminder) {
            showPaymentReminderScreen();
        } else {
            // El dispositivo no está bloqueado y no hay recordatorio de pago activo.
            // Esto significa que la app fue lanzada por el usuario.
            // Podrías mostrar una pantalla "normal" o, como en tu código original, cerrar la actividad
            // para que regrese a la pantalla de inicio.
            if (!isTelevision) {
                // Aquí podrías agregar una pantalla de bienvenida o simplemente terminar
                // la actividad para no molestar al usuario si no hay nada que mostrar.
                // Como tu código original la terminaba, mantendré esa lógica.
                // Pero lo haré de forma más segura.
                if (mainLayout != null) {
                    mainLayout.setVisibility(View.GONE);
                }
            }
            // En caso de TV, siempre mostramos la pantalla principal si no hay bloqueo.
            showPaymentReminderScreen();
        }
    }

    private void showLockedScreen() {
        if (isTelevision) {
            setContentView(R.layout.locked_layout_tv);
            initializeViews();
            if (unlockButton != null) {
                unlockButton.setOnClickListener(v -> attemptUnlock());
            }
            if (contactPhoneTextView != null) {
                contactPhoneTextView.setOnClickListener(v -> {
                    String phoneNumber = contactPhoneTextView.getText().toString().replace("Teléfono: ", "");
                    if (!phoneNumber.isEmpty()) {
                        Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                        dialIntent.setData(Uri.parse("tel:" + phoneNumber));
                        startActivity(dialIntent);
                    }
                });
            }
        } else {
            if (lockedLayout != null) lockedLayout.setVisibility(View.VISIBLE);
            if (mainLayout != null) mainLayout.setVisibility(View.GONE);
            if (logoImageView != null) Glide.with(this).load(R.drawable.inova_guard_logo).into(logoImageView);
        }
        updateLockedInfo();
    }

    private void showPaymentReminderScreen() {
        if (isTelevision) {
            setContentView(R.layout.main_layout_tv);
            initializeViews();
        } else {
            if (lockedLayout != null) lockedLayout.setVisibility(View.GONE);
            if (mainLayout != null) mainLayout.setVisibility(View.VISIBLE);
        }
        updatePaymentInfo();
    }

    private void updateLockedInfo() {
        String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "+58 412 1234567");
        if (contactPhoneTextView != null) {
            contactPhoneTextView.setText("Teléfono: " + contactNumber);
        }
        if (incorrectCodeTextView != null) {
            incorrectCodeTextView.setVisibility(View.GONE);
        }
        if (unlockCodeEditText != null) {
            unlockCodeEditText.setText("");
        }
    }

    private void updatePaymentInfo() {
        String nextPaymentDate = sharedPreferences.getString(Constants.PREF_NEXT_PAYMENT_DATE, "N/A");
        String amountDue = sharedPreferences.getString(Constants.PREF_AMOUNT_DUE, "N/A");
        String amountPaid = sharedPreferences.getString(Constants.PREF_AMOUNT_PAID, "N/A");
        String deviceBrand = sharedPreferences.getString(Constants.PREF_DEVICE_BRAND, "N/A");
        String deviceModel = sharedPreferences.getString(Constants.PREF_DEVICE_MODEL, "N/A");
        String paymentInstructions = sharedPreferences.getString(Constants.PREF_PAYMENT_INSTRUCTIONS, "N/A");
        String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "N/A");

        if (nextPaymentDateTextView != null) nextPaymentDateTextView.setText(nextPaymentDate);
        if (amountDueTextView != null) amountDueTextView.setText(amountDue);
        if (amountPaidTextView != null) amountPaidTextView.setText(amountPaid);
        if (deviceInfoTextView != null) deviceInfoTextView.setText(deviceBrand + " " + deviceModel);
        if (paymentInstructionsTextView != null) paymentInstructionsTextView.setText(paymentInstructions);
        if (contactPhoneMainTextView != null) contactPhoneMainTextView.setText("Teléfono: " + contactNumber);
    }


    private void attemptUnlock() {
        String enteredCode = unlockCodeEditText.getText().toString().trim();
        if (enteredCode.isEmpty()) {
            if (incorrectCodeTextView != null) {
                incorrectCodeTextView.setText("Por favor, introduce el código.");
                incorrectCodeTextView.setVisibility(View.VISIBLE);
            }
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
                            refreshScreenState();
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            if (incorrectCodeTextView != null) {
                                incorrectCodeTextView.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            if (incorrectCodeTextView != null) {
                                incorrectCodeTextView.setText(message);
                                incorrectCodeTextView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing unlock response: " + e.getMessage());
                    runOnUiThread(() -> {
                        if (incorrectCodeTextView != null) {
                            incorrectCodeTextView.setText("Error en la respuesta del servidor.");
                            incorrectCodeTextView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Unlock API call failed: " + errorMessage);
                runOnUiThread(() -> {
                    if (incorrectCodeTextView != null) {
                        incorrectCodeTextView.setText("Error de conexión al servidor: " + errorMessage);
                        incorrectCodeTextView.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    public void lockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();
            refreshScreenState();
            Toast.makeText(this, "Dispositivo bloqueado por falta de pago.", Toast.LENGTH_LONG).show();
            if (isDeviceOwner()) {
                startLockTask();
            }
        });
    }

    public void unlockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
            refreshScreenState();
            Toast.makeText(this, "Dispositivo desbloqueado.", Toast.LENGTH_LONG).show();
            if (isDeviceOwner()) {
                stopLockTask();
            }
        });
    }

    private boolean isDeviceOwner() {
        return devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName());
    }
}