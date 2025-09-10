package com.inova.guard.mdm.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.inova.guard.mdm.MainActivity;
import com.inova.guard.mdm.utils.Constants;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.widget.Toast; // Importa esta clase para mostrar el mensaje de pago.

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    // Reemplaza esta URL con la URL de tu servidor
    private static final String BACKEND_URL = Constants.BASE_URL + "/api/update-fcm-token/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Mensaje FCM recibido. De: " + remoteMessage.getFrom());

        // Manejar el payload de datos del mensaje
        if (remoteMessage.getData().size() > 0) {
            String command = remoteMessage.getData().get("action");
            String unlockCode = remoteMessage.getData().get("unlock_code");
            String message = remoteMessage.getData().get("message");

            switch (command) {
                case "lock":
                    Log.d(TAG, "Comando de bloqueo recibido. Activando pantalla de bloqueo.");
                    // Guarda el estado de bloqueo y el código de desbloqueo.
                    SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
                    editor.putBoolean(Constants.PREF_IS_LOCKED, true);
                    editor.putString(Constants.PREF_UNLOCK_CODE, unlockCode);
                    editor.apply();

                    // Inicia la actividad principal para mostrar la pantalla de bloqueo.
                    Intent lockIntent = new Intent(this, MainActivity.class);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(lockIntent);
                    break;
                case "payment_reminder":
                    // AÑADIDO: Maneja el recordatorio de pago.
                    Log.d(TAG, "Comando de recordatorio de pago recibido.");
                    if (message != null) {
                        // Muestra el mensaje de pago en un Toast.
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    }
                    break;
                default:
                    Log.d(TAG, "Comando desconocido recibido: " + command);
                    break;
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Token de registro FCM actualizado: " + token);
        sendRegistrationTokenToServer(this, token);
    }

    public static void sendRegistrationTokenToServer(Context context, String token) {
        String serialNumber = getSerialNumber(context);
        if (serialNumber.equals("unknown") || serialNumber.isEmpty()) {
            Log.e(TAG, "No se pudo obtener el número de serie del dispositivo.");
            return;
        }

        OkHttpClient client = new OkHttpClient();
        String json = "{\"serial_number\": \"" + serialNumber + "\", \"fcm_token\": \"" + token + "\"}";
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Fallo al enviar el token al servidor: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Token enviado al servidor correctamente.");
                } else {
                    Log.e(TAG, "Error al enviar el token: " + response.code() + " " + response.message());
                }
            }
        });
    }

    private static String getSerialNumber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String serialNumber = prefs.getString(Constants.PREF_SERIAL_NUMBER, "unknown");

        if ("unknown".equals(serialNumber) || serialNumber.isEmpty()) {
            Log.e(TAG, "El número de serie no se encontró en las preferencias compartidas.");
        }
        return serialNumber;
    }
}
