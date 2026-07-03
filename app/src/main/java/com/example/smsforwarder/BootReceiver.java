package com.example.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;

/**
 * Listens for system boot notifications to automatically restart the SMS Forwarding Service.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "Received boot broadcast with action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            ConfigManager configManager = new ConfigManager(context);
            
            // Auto-start the service only if forwarding was previously enabled by the user
            if (configManager.isEnabled()) {
                Intent serviceIntent = new Intent(context, SmsForwardingService.class);
                try {
                    ContextCompat.startForegroundService(context, serviceIntent);
                    configManager.addLog(context.getString(R.string.log_boot_detected));
                    Log.d(TAG, "SMS Forwarding Service started successfully on boot");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to auto-start service on boot", e);
                    configManager.addLog(context.getString(R.string.log_boot_failed, e.getMessage()));
                }
            } else {
                Log.d(TAG, "SMS Forwarding is disabled. Skipping auto-start.");
            }
        }
    }
}
