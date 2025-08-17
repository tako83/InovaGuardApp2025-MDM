package com.inova.guard.mdm;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.inova.guard.mdm.admin.DeviceAdminReceiver;
import com.inova.guard.mdm.service.MdmService;
import com.inova.guard.mdm.utils.ApiUtils;
import com.inova.guard.mdm.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

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

    // Vistas de la pantalla de bloqueo
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ***************************************************************
        // LÓGICA DE VERIFICACIÓN INICIAL PARA ENROLAMIENTO
        // Si el dispositivo no está enrolado, redirige a EnrollmentActivity
        // ***************************************************************
        sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, null);

        if (deviceId == null || deviceId.isEmpty()) {
            // Si no hay Device ID, el dispositivo no está enrolado.
            // Inicia la actividad de enrolamiento y finaliza esta actividad.
            Log.d(TAG, "Device not enrolled. Redirecting to EnrollmentActivity.");
            Intent enrollmentIntent = new Intent(this, EnrollmentActivity.class);
            startActivity(enrollmentIntent);
            finish(); // Cierra esta actividad para que el usuario no pueda volver a ella con el botón de atrás
            return; // Detiene la ejecución del resto de onCreate
        }
        // ***************************************************************


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
        // sharedPreferences ya fue inicializado arriba.


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

        // Configurar listener para el botón de contacto en la pantalla de bloqueo
        contactPhoneTextView.setOnClickListener(v -> {
            String phoneNumber = contactPhoneTextView.getText().toString().replace("Teléfono: ", "");
            if (!phoneNumber.isEmpty()) {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(android.net.Uri.parse("tel:" + phoneNumber));
                startActivity(dialIntent);
            }
        });


        // Cargar y mostrar el logo de la empresa (puedes usar un GIF para animación sutil)
        // Por ahora, usaremos una imagen estática, pero Glide te permitiría cargar GIFs.
        // Glide.with(this).load(R.drawable.inova_guard_logo).into(logoImageView);

        // Iniciar el chequeo periódico de conexión solo si la app está en un estado que lo requiere
        handler = new Handler();
        checkConnectionRunnable = new Runnable() {
            @Override
            public void run() {
                checkDeviceStatus(); // Este método ahora se encarga de determinar qué pantalla mostrar
                handler.postDelayed(this, Constants.CONNECTION_CHECK_INTERVAL); // Repetir cada X segundos
            }
        };
        handler.post(checkConnectionRunnable); // Iniciar el chequeo inmediatamente

        // Asegurarse de que el sistema no apague nuestra app fácilmente
        if (devicePolicyManager != null && adminComponentName != null && devicePolicyManager.isAdminActive(adminComponentName)) {
            // En modo Device Owner, puedes usar devicePolicyManager.setLockTaskPackages()
            // Para un Device Administrator, puedes intentar bloquear el acceso a Settings para evitar desinstalación
            // Sin embargo, esto es limitado y un usuario avanzado podría sortearlo.
            // Para robustez real, el modo Device Owner es clave.
            Log.d(TAG, "Device Admin is active. Attempting to restrict settings.");
            // Aquí podrías añadir restricciones si estás en modo Device Owner
        } else {
            Log.w(TAG, "Device Admin is not active. App may be easily uninstalled.");
            // Podrías dirigir al usuario a la pantalla de activación si es la primera vez
            // o si la deshabilitaron.
        }

        // Verifica si la app es la launcher por defecto para la experiencia kiosco
        if (isDeviceOwner()) {
            // Si eres Device Owner, puedes establecer la app como launcher de kiosco
            Log.d(TAG, "Running in Device Owner mode. App can be set as Kiosk.");
            devicePolicyManager.setLockTaskPackages(adminComponentName, new String[]{getPackageName()});
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkDeviceStatus(); // Verificar el estado cada vez que la actividad se reanuda
        handler.post(checkConnectionRunnable); // Asegurarse de que el chequeo de conexión esté activo
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(checkConnectionRunnable); // Detener el chequeo de conexión al pausar
    }

    // Método para cambiar entre las pantallas de bloqueo y principal
    private void showScreen(boolean isLocked) {
        if (isLocked) {
            lockedLayout.setVisibility(View.VISIBLE);
            mainLayout.setVisibility(View.GONE);
            // Asegurarse de que el logo se vea animado si es un GIF o aplicar animación programática
            Glide.with(this).load(R.drawable.inova_guard_logo).into(logoImageView); // Cargar logo estático o GIF
        } else {
            lockedLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
        }
    }

    // Método para verificar el estado del dispositivo (bloqueado/desbloqueado)
    // y la conexión a internet.
    private void checkDeviceStatus() {
        boolean isDeviceLocked = sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false);
        showScreen(isDeviceLocked);

        // Si está bloqueado, asegurar que se muestre el contacto actualizado
        if (isDeviceLocked) {
            String contactNumber = sharedPreferences.getString(Constants.PREF_CONTACT_PHONE, "+58 412 1234567");
            contactPhoneTextView.setText("Teléfono: " + contactNumber);
            incorrectCodeTextView.setVisibility(View.GONE); // Ocultar el mensaje de código incorrecto inicialmente
            unlockCodeEditText.setText(""); // Limpiar el campo de código
        } else {
            // Si no está bloqueado, actualizar la información de pago y contacto
            updatePaymentInfo();
        }
    }

    // Este método simulará la actualización de la información de pago desde el backend.
    // En una app real, esto se haría mediante una llamada a la API.
    private void updatePaymentInfo() {
        // Simular datos del backend. En un escenario real, harías una llamada HTTP.
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

    // Intento de desbloqueo por parte del usuario
    private void attemptUnlock() {
        String enteredCode = unlockCodeEditText.getText().toString().trim();
        if (enteredCode.isEmpty()) {
            incorrectCodeTextView.setText("Por favor, introduce el código.");
            incorrectCodeTextView.setVisibility(View.VISIBLE);
            return;
        }

        // Enviar el código al backend para verificación
        ApiUtils.verifyUnlockCode(this, enteredCode, new ApiUtils.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    boolean success = jsonResponse.getBoolean("success");
                    String message = jsonResponse.getString("message");

                    if (success) {
                        sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
                        runOnUiThread(() -> {
                            showScreen(false); // Desbloquear la pantalla
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

    // Método para simular una acción de bloqueo (llamado por el servicio MDM)
    public void lockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, true).apply();
            showScreen(true);
            Toast.makeText(this, "Dispositivo bloqueado por falta de pago.", Toast.LENGTH_LONG).show();
            // Opcional: Si estás en modo Device Owner, puedes usar
            // devicePolicyManager.setLockTaskPackages() para forzar el modo kiosco.
            if (isDeviceOwner()) {
                startLockTask(); // Entrar en modo kiosco
            }
        });
    }

    // Método para simular una acción de desbloqueo (llamado por el servicio MDM)
    public void unlockDevice() {
        runOnUiThread(() -> {
            sharedPreferences.edit().putBoolean(Constants.PREF_IS_LOCKED, false).apply();
            showScreen(false);
            Toast.makeText(this, "Dispositivo desbloqueado.", Toast.LENGTH_LONG).show();
            if (isDeviceOwner()) {
                stopLockTask(); // Salir del modo kiosco
            }
        });
    }

    // Verifica si la aplicación es un Device Owner
    private boolean isDeviceOwner() {
        return devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(getPackageName());
    }

    // Sobrescribir onBackPressed para evitar salir de la app fácilmente cuando está bloqueada
    @Override
    public void onBackPressed() {
        if (sharedPreferences.getBoolean(Constants.PREF_IS_LOCKED, false)) {
            // Si está bloqueado, no permitir salir con el botón de atrás
            Toast.makeText(this, "El dispositivo está bloqueado. Contacte a la administración.", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed(); // Comportamiento normal si no está bloqueado
        }
    }
}
