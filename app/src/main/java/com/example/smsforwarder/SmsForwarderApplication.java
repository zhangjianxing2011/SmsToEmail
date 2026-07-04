package com.example.smsforwarder;

import android.app.Application;
import android.util.Log;

/**
 * Custom Application class to perform early configuration of network properties.
 */
public class SmsForwarderApplication extends Application {
    private static final String TAG = "SmsForwarderApp";

    static {
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");
            Log.d(TAG, "Successfully configured IPv4 stack preference in static initializer");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system properties in static initializer", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");
            Log.d(TAG, "Successfully configured IPv4 stack preference in onCreate");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system properties in onCreate", e);
        }
    }
}
