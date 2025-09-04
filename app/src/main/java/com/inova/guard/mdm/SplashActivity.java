package com.inova.guard.mdm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.inova.guard.mdm.utils.Constants;

/**
 * SplashActivity es la actividad de carga que se muestra al inicio de la aplicación.
 * Esta actividad se encarga de mostrar la animación del logo por un breve período de tiempo
 * y luego navegar a la actividad principal (MainActivity).
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 3000; // 3 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // Carga el layout de la animación

        new Handler().postDelayed(() -> {
            // Verificar el estado de enrolamiento
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean isEnrolled = prefs.getBoolean("is_enrolled", false);

            Intent nextActivityIntent;
            if (isEnrolled) {
                // Si ya está enrolado, ir a la actividad principal (MainActivity)
                nextActivityIntent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                // Si no está enrolado, ir a la actividad de enrolamiento (EnrollmentActivity)
                nextActivityIntent = new Intent(SplashActivity.this, EnrollmentActivity.class);
            }

            SplashActivity.this.startActivity(nextActivityIntent);
            SplashActivity.this.finish(); // Cierra esta actividad para que el usuario no pueda volver atrás
        }, SPLASH_DISPLAY_LENGTH);
    }
}
