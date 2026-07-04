package com.example.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Handles application configuration settings and log entries using SharedPreferences.
 */
public class ConfigManager {
    private static final String PREF_NAME = "sms_forwarder_prefs";
    private static final String KEY_SMTP_HOST = "smtp_host";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_SENDER_EMAIL = "sender_email";
    private static final String KEY_SENDER_PASSWORD = "sender_password";
    private static final String KEY_RECIPIENT_EMAIL = "recipient_email";
    private static final String KEY_USE_SSL = "use_ssl";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LOGS = "logs";
    private static final String KEY_TG_TOKEN = "tg_token";
    private static final String KEY_TG_CHAT_ID = "tg_chat_id";
    private static final String KEY_TG_ENABLED = "tg_enabled";
    private static final int MAX_LOGS = 1500;

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getSmtpHost() {
        return prefs.getString(KEY_SMTP_HOST, "");
    }

    public void setSmtpHost(String val) {
        prefs.edit().putString(KEY_SMTP_HOST, val).apply();
    }

    public int getSmtpPort() {
        return prefs.getInt(KEY_SMTP_PORT, 465);
    }

    public void setSmtpPort(int val) {
        prefs.edit().putInt(KEY_SMTP_PORT, val).apply();
    }

    public String getSenderEmail() {
        return prefs.getString(KEY_SENDER_EMAIL, "");
    }

    public void setSenderEmail(String val) {
        prefs.edit().putString(KEY_SENDER_EMAIL, val).apply();
    }

    public String getSenderPassword() {
        return prefs.getString(KEY_SENDER_PASSWORD, "");
    }

    public void setSenderPassword(String val) {
        prefs.edit().putString(KEY_SENDER_PASSWORD, val).apply();
    }

    public String getRecipientEmail() {
        return prefs.getString(KEY_RECIPIENT_EMAIL, "");
    }

    public void setRecipientEmail(String val) {
        prefs.edit().putString(KEY_RECIPIENT_EMAIL, val).apply();
    }

    public boolean isUseSsl() {
        return prefs.getBoolean(KEY_USE_SSL, true);
    }

    public void setUseSsl(boolean val) {
        prefs.edit().putBoolean(KEY_USE_SSL, val).apply();
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean val) {
        prefs.edit().putBoolean(KEY_ENABLED, val).apply();
    }

    /**
     * Retrieves the history of operations logs.
     */
    public synchronized List<String> getLogs() {
        String logStr = prefs.getString(KEY_LOGS, "");
        if (logStr.isEmpty()) {
            return new ArrayList<>();
        }
        String[] arr = logStr.split("###");
        List<String> list = new ArrayList<>();
        Collections.addAll(list, arr);
        return list;
    }

    /**
     * Adds an entry to the top of the logs history, keeping it under the maximum limit.
     */
    public synchronized void addLog(String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String logEntry = "[" + timestamp + "] " + message;

        List<String> list = getLogs();
        list.add(0, logEntry); // Add to the top of the list

        if (list.size() > MAX_LOGS) {
            list = list.subList(0, MAX_LOGS);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) {
                sb.append("###");
            }
        }
        prefs.edit().putString(KEY_LOGS, sb.toString()).apply();
    }

    /**
     * Clears all log entries.
     */
    public synchronized void clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply();
    }

    public String getTgToken() {
        return prefs.getString(KEY_TG_TOKEN, "");
    }

    public void setTgToken(String val) {
        prefs.edit().putString(KEY_TG_TOKEN, val).apply();
    }

    public String getTgChatId() {
        return prefs.getString(KEY_TG_CHAT_ID, "");
    }

    public void setTgChatId(String val) {
        prefs.edit().putString(KEY_TG_CHAT_ID, val).apply();
    }

    public boolean isTgEnabled() {
        return prefs.getBoolean(KEY_TG_ENABLED, false);
    }

    public void setTgEnabled(boolean val) {
        prefs.edit().putBoolean(KEY_TG_ENABLED, val).apply();
    }
}
