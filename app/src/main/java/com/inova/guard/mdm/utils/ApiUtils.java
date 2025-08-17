package com.inova.guard.mdm.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiUtils {

    private static final String TAG = "ApiUtils";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    public interface ApiCallback {
        void onSuccess(String response);
        void onFailure(String errorMessage); // ¡Esta es la firma correcta!
    }

    public static void enrollDevice(Context context, String deviceId, String serialNumber, String brand, String model, String imei, ApiCallback callback) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("device_id", deviceId);
            jsonBody.put("serial_number", serialNumber);
            jsonBody.put("brand", brand);
            jsonBody.put("model", model);
            jsonBody.put("imei", imei);
            jsonBody.put("current_ip", getLocalIpAddress());

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(Constants.BASE_URL + "/api/enroll/")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error de red al enrolar: " + e.getMessage());
                    callback.onFailure("Error de red: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Enrolamiento exitoso: " + responseBody);
                        callback.onSuccess(responseBody);
                    } else {
                        Log.e(TAG, "Enrolamiento fallido (" + response.code() + "): " + responseBody);
                        callback.onFailure("Error en el servidor: " + responseBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error JSON al enrolar: " + e.getMessage());
            callback.onFailure("Error interno al crear JSON: " + e.getMessage());
        }
    }

    public static void verifyUnlockCode(Context context, String code, ApiCallback callback) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = sharedPreferences.getString(Constants.PREF_DEVICE_ID, "unknown");

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("device_id", deviceId);
            jsonBody.put("unlock_code", code);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(Constants.BASE_URL + "/api/unlock/")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error de red al verificar código: " + e.getMessage());
                    callback.onFailure("Error de red: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Verificación de código exitosa: " + responseBody);
                        callback.onSuccess(responseBody);
                    } else {
                        Log.e(TAG, "Verificación de código fallida (" + response.code() + "): " + responseBody);
                        callback.onFailure("Código incorrecto o error en el servidor: " + responseBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error JSON al verificar código: " + e.getMessage());
            callback.onFailure("Error interno al crear JSON: " + e.getMessage());
        }
    }

    public static void lockDevice(Context context, String serialNumber, ApiCallback callback) { // Cambiado a serialNumber
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("serial_number", serialNumber); // Usar serial_number
            jsonBody.put("lock_reason", "no_internet_connection");

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(Constants.BASE_URL + "/api/lock_device_initiated_by_app/")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error de red al notificar bloqueo: " + e.getMessage());
                    callback.onFailure("Error de red: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Notificación de bloqueo exitosa: " + responseBody);
                        callback.onSuccess(responseBody);
                    } else {
                        Log.e(TAG, "Notificación de bloqueo fallida (" + response.code() + "): " + responseBody);
                        callback.onFailure("Error en el servidor al notificar bloqueo: " + responseBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error JSON al notificar bloqueo: " + e.getMessage());
            callback.onFailure("Error interno al crear JSON: " + e.getMessage());
        }
    }

    // Renombrado de reportDeviceStatus a checkDeviceStatus para la API de estado
    public static void checkDeviceStatus(Context context, String serialNumber, boolean isOnline, ApiCallback callback) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("serial_number", serialNumber); // Usar serial_number para la API
            jsonBody.put("is_online", isOnline);
            jsonBody.put("last_known_ip", getLocalIpAddress());

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(Constants.BASE_URL + "/api/status/" + serialNumber + "/") // URL ajustada para serial_number
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error de red al verificar estado: " + e.getMessage());
                    callback.onFailure("Error de red: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Verificación de estado exitosa: " + responseBody);
                        callback.onSuccess(responseBody);
                    } else {
                        Log.e(TAG, "Verificación de estado fallida (" + response.code() + "): " + responseBody);
                        callback.onFailure("Error en el servidor al verificar estado: " + responseBody);
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error JSON al verificar estado: " + e.getMessage());
            callback.onFailure("Error interno al crear JSON: " + e.getMessage());
        }
    }

    private static String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface intf = interfaces.nextElement();
                java.util.Enumeration<java.net.InetAddress> addrs = intf.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error al obtener IP: " + ex.getMessage());
        }
        return "N/A";
    }
}
