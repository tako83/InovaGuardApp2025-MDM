package com.inova.guard.mdm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.inova.guard.mdm.service.MdmService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device rebooted. Starting MdmService...");
            Intent serviceIntent = new Intent(context, MdmService.class);
            context.startService(serviceIntent);
        }
    }
}
