package com.inova.guard.mdm.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiUtils {
    private static final String TAG = "ApiUtils";
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface ApiCallback {
        void onSuccess(String response);
        void onFailure(String errorMessage);
    }

    private static String getBaseUrl() {
        return Constants.BASE_URL;
    }

    public static void enrollDevice(Context context, JSONObject deviceData, ApiCallback callback) {
        String url = getBaseUrl() + "/api/enroll/";
        RequestBody body = RequestBody.create(JSON, deviceData.toString());
        Request request = new Request.Builder().url(url).post(body).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().string());
                } else {
                    callback.onFailure("Error en el enrolamiento: " + response.code());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Fallo de red: " + e.getMessage());
            }
        });
    }

    public static void checkDeviceStatus(Context context, String serialNumber, boolean isOnline, ApiCallback callback) {
        String url = getBaseUrl() + "/api/status/" + serialNumber + "/";
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().string());
                } else {
                    callback.onFailure("Error al chequear el estado: " + response.code());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Fallo de red: " + e.getMessage());
            }
        });
    }

    public static void lockDevice(Context context, String serialNumber, ApiCallback callback) {
        String url = getBaseUrl() + "/api/lock_device_initiated_by_app/";
        JSONObject payload = new JSONObject();
        try {
            payload.put("serial_number", serialNumber);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder().url(url).post(body).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().string());
                } else {
                    callback.onFailure("Error al bloquear dispositivo: " + response.code());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Fallo de red: " + e.getMessage());
            }
        });
    }

    public static void verifyUnlockCode(Context context, String serialNumber, String code, ApiCallback callback) {
        String url = getBaseUrl() + "/api/verify_unlock_code/" + serialNumber + "/";
        JSONObject payload = new JSONObject();
        try {
            payload.put("unlock_key", code);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(JSON, payload.toString());
        Request request = new Request.Builder().url(url).post(body).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().string());
                } else {
                    callback.onFailure("Error al verificar código: " + response.code());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Fallo de red: " + e.getMessage());
            }
        });
    }
}