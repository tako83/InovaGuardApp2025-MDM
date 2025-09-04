package com.inova.guard.mdm.service;

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

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    // Reemplaza esta URL con la URL de tu servidor
    private static final String BACKEND_URL = "http://tu-servidor.com/api/update-fcm-token/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Mensaje FCM recibido. De: " + remoteMessage.getFrom());

        // Manejar el payload de datos del mensaje
        if (remoteMessage.getData().size() > 0) {
            String command = remoteMessage.getData().get("action");
            String unlockCode = remoteMessage.getData().get("unlock_code");

            if ("lock".equals(command)) {
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
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Token de registro FCM actualizado: " + token);
        // Envía el nuevo token a tu backend para que el dispositivo pueda ser contactado.
        sendRegistrationTokenToServer(token);
    }

    private void sendRegistrationTokenToServer(String token) {
        String serialNumber = getSerialNumber();
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

    private String getSerialNumber() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Build.getSerial();
            } else {
                return Build.SERIAL;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error de seguridad al obtener el número de serie", e);
            return "unknown";
        }
    }
}
