package com.inova.guard.mdm.service;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.inova.guard.mdm.MainActivity;
import com.inova.guard.mdm.utils.Constants;

import java.io.IOException;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import androidx.annotation.NonNull;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    private static final String BACKEND_URL = "https://tako83.pythonanywhere.com/api/update-fcm-token/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String command = data.get("action");

            if ("lock".equals(command)) {
                SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(Constants.PREF_IS_LOCKED, true);
                editor.putString(Constants.PREF_UNLOCK_CODE, data.get("unlock_code"));
                editor.apply();

                Intent lockIntent = new Intent(this, MainActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(lockIntent);

            } else if ("unlock".equals(command)) {
                if (MainActivity.instance != null) {
                    new Handler(Looper.getMainLooper()).post(() -> MainActivity.instance.unlockDevice());
                }

            } else if ("reminder".equals(command) || "payment_reminder".equals(command)) {
                String reminderTitle = data.get("title");
                String reminderMessage = data.get("message");
                String nextPaymentDate = data.get("next_payment_date");
                String amountDue = data.get("amount_due");
                String amountPaid = data.get("amount_paid");
                String paymentInstructions = data.get("payment_instructions");
                String contactPhone = data.get("contact_phone");

                if (reminderMessage != null && !reminderMessage.isEmpty()) {
                    SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
                    editor.putString(Constants.PREF_REMINDER_TITLE, reminderTitle);
                    editor.putString(Constants.PREF_REMINDER_MESSAGE, reminderMessage);
                    editor.putString(Constants.PREF_NEXT_PAYMENT_DATE, nextPaymentDate);
                    editor.putString(Constants.PREF_AMOUNT_DUE, amountDue);
                    editor.putString(Constants.PREF_AMOUNT_PAID, amountPaid);
                    editor.putString(Constants.PREF_PAYMENT_INSTRUCTIONS, paymentInstructions);
                    editor.putString(Constants.PREF_CONTACT_PHONE, contactPhone);
                    editor.apply();

                    Intent reminderIntent = new Intent(this, ReminderOverlayService.class);
                    reminderIntent.putExtra("REMINDER_MESSAGE", reminderMessage);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(reminderIntent);
                    } else {
                        startService(reminderIntent);
                    }
                }
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        sendRegistrationTokenToServer(this, token);
    }

    public static void sendRegistrationTokenToServer(Context context, String token) {
        String serialNumber = getSerialNumber(context);
        if (serialNumber.equals("unknown") || serialNumber.isEmpty()) {
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {}
        });
    }

    private static String getSerialNumber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_SERIAL_NUMBER, "unknown");
    }
}