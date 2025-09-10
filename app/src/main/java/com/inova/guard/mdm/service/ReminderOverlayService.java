package com.inova.guard.mdm.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.inova.guard.mdm.R;

public class ReminderOverlayService extends Service {

    private static final String TAG = "ReminderOverlayService";
    private WindowManager windowManager;
    private View overlayView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Servicio de recordatorio iniciado.");

        if (overlayView != null) {
            Log.d(TAG, "La vista de superposición ya está mostrada. Deteniendo el servicio anterior.");
            windowManager.removeView(overlayView);
        }

        // Infla el layout completo de activity_main.xml
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rootView = inflater.inflate(R.layout.activity_main, null);

        // Oculta los layouts que no necesitas
        LinearLayout lockedLayout = rootView.findViewById(R.id.locked_layout);
        if (lockedLayout != null) {
            lockedLayout.setVisibility(View.GONE);
        }
        LinearLayout adminPanel = rootView.findViewById(R.id.admin_panel);
        if (adminPanel != null) {
            adminPanel.setVisibility(View.GONE);
        }

        // El layout que queremos mostrar como superposición es el main_layout
        overlayView = rootView.findViewById(R.id.main_layout);
        if (overlayView == null) {
            Log.e(TAG, "No se encontró main_layout en el XML.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Ahora, configura los datos en las vistas de main_layout
        String reminderTitle = intent.getStringExtra("REMINDER_TITLE");
        String reminderMessage = intent.getStringExtra("REMINDER_MESSAGE");
        String nextPaymentDate = intent.getStringExtra("NEXT_PAYMENT_DATE");
        String amountDue = intent.getStringExtra("AMOUNT_DUE");
        String amountPaid = intent.getStringExtra("AMOUNT_PAID");
        String paymentInstructions = intent.getStringExtra("PAYMENT_INSTRUCTIONS");
        String contactPhone = intent.getStringExtra("CONTACT_PHONE");

        TextView nextPaymentDateTextView = overlayView.findViewById(R.id.next_payment_date_text_view);
        TextView amountDueTextView = overlayView.findViewById(R.id.amount_due_text_view);
        TextView amountPaidTextView = overlayView.findViewById(R.id.amount_paid_text_view);
        TextView paymentInstructionsTextView = overlayView.findViewById(R.id.payment_instructions_text_view);
        TextView contactPhoneMainTextView = overlayView.findViewById(R.id.contact_phone_main_text_view);
        Button contactAdminButton = overlayView.findViewById(R.id.contact_admin_button);

        // Aplica los datos a las vistas
        if (nextPaymentDateTextView != null) nextPaymentDateTextView.setText(nextPaymentDate);
        if (amountDueTextView != null) amountDueTextView.setText(amountDue);
        if (amountPaidTextView != null) amountPaidTextView.setText(amountPaid);
        if (paymentInstructionsTextView != null) paymentInstructionsTextView.setText(paymentInstructions);
        if (contactPhoneMainTextView != null) contactPhoneMainTextView.setText("Teléfono: " + contactPhone);

        if (contactAdminButton != null) {
            contactAdminButton.setOnClickListener(v -> {
                Log.d(TAG, "Botón de Contactar Administración presionado.");
                if (contactPhone != null && !contactPhone.isEmpty()) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(android.net.Uri.parse("tel:" + contactPhone));
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(dialIntent);
                }
                stopSelf();
            });
        }

        // Define los parámetros de la ventana
        int overlayType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(overlayView, params);
            Log.d(TAG, "Vista de superposición de recordatorio añadida correctamente.");
        } catch (Exception e) {
            Log.e(TAG, "Error al añadir la vista de superposición: " + e.getMessage());
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Servicio de recordatorio destruido.");
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }
}