package com.example.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.telephony.SmsMessage;
import android.util.Log;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Listens for incoming SMS broadcasts and forwards them via email if enabled.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    // Deduplication cache to prevent duplicate email sends from double receiver triggers
    private static final Map<String, Long> processedMessagesCache = new HashMap<>();
    private static final long CACHE_EXPIRATION_TIME = 10000; // 10 seconds window

    @SuppressWarnings("deprecation")
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !ACTION_SMS_RECEIVED.equals(intent.getAction())) {
            return;
        }

        final ConfigManager configManager = new ConfigManager(context);

        // Acquire WakeLock to keep CPU awake during screen-off/sleep mode
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm != null ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSForwarder:SmsReceiverWakeLock") : null;
        if (wakeLock != null) {
            wakeLock.acquire(30000); // 30 seconds max timeout
        }

        // Acquire WifiLock to keep Wi-Fi active during screen-off/sleep mode
        android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final android.net.wifi.WifiManager.WifiLock wifiLock;
        if (wm != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SMSForwarder:SmsReceiverWifiLock");
            } else {
                wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "SMSForwarder:SmsReceiverWifiLock");
            }
            try {
                wifiLock.acquire();
            } catch (Exception e) {
                Log.e(TAG, "Error acquiring WifiLock", e);
            }
        } else {
            wifiLock = null;
        }

        final AtomicInteger pendingTasks = new AtomicInteger(0);

        // Helper method to safely release WakeLock and WifiLock
        final Runnable checkReleaseWakeLock = new Runnable() {
            @Override
            public void run() {
                if (pendingTasks.decrementAndGet() <= 0) {
                    if (wakeLock != null && wakeLock.isHeld()) {
                        try {
                            wakeLock.release();
                        } catch (Exception e) {
                            Log.e(TAG, "Error releasing WakeLock", e);
                        }
                    }
                    if (wifiLock != null && wifiLock.isHeld()) {
                        try {
                            wifiLock.release();
                        } catch (Exception e) {
                            Log.e(TAG, "Error releasing WifiLock", e);
                        }
                    }
                }
            }
        };

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
            return;
        }

        try {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format");

            if (pdus == null || pdus.length == 0) {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                if (wifiLock != null && wifiLock.isHeld()) {
                    wifiLock.release();
                }
                return;
            }

            // Group parts of SMS messages by sender to handle multi-part messages
            Map<String, StringBuilder> messageBodies = new HashMap<>();
            long smsTimestamp = System.currentTimeMillis();

            for (Object pdu : pdus) {
                SmsMessage smsMessage;
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);

                if (smsMessage == null) continue;

                String sender = smsMessage.getDisplayOriginatingAddress();
                String body = smsMessage.getDisplayMessageBody();
                smsTimestamp = smsMessage.getTimestampMillis();

                if (sender != null && body != null) {
                    if (!messageBodies.containsKey(sender)) {
                        messageBodies.put(sender, new StringBuilder());
                    }
                    messageBodies.get(sender).append(body);
                }
            }

            // Forward each merged message
            for (Map.Entry<String, StringBuilder> entry : messageBodies.entrySet()) {
                final String sender = entry.getKey();
                final String fullBody = entry.getValue().toString();

                // Deduplicate check (checks sender + body within a 10-second window)
                String messageKey = sender + "|" + fullBody;
                long currentTime = System.currentTimeMillis();
                synchronized (processedMessagesCache) {
                    // Clean up expired cache items to prevent memory leaks
                    Iterator<Map.Entry<String, Long>> cacheIterator = processedMessagesCache.entrySet().iterator();
                    while (cacheIterator.hasNext()) {
                        if (currentTime - cacheIterator.next().getValue() > CACHE_EXPIRATION_TIME) {
                            cacheIterator.remove();
                        }
                    }

                    // Ignore duplicates within the window
                    if (processedMessagesCache.containsKey(messageKey)) {
                        long lastProcessed = processedMessagesCache.get(messageKey);
                        if (currentTime - lastProcessed < CACHE_EXPIRATION_TIME) {
                            Log.d(TAG, "Duplicate message detected within 10s window. Skipping email send.");
                            continue;
                        }
                    }
                    processedMessagesCache.put(messageKey, currentTime);
                }
                final String formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date(smsTimestamp));

                // Check if forwarding service is enabled
                if (!configManager.isEnabled()) {
                    // Log receipt but skip forwarding
                    configManager.addLog(context.getString(R.string.log_sms_received_raw_disabled, sender, fullBody));
                    continue;
                }

                // Log raw SMS receipt
                configManager.addLog(context.getString(R.string.log_sms_received_raw, sender, fullBody));

                // Check active delivery targets
                boolean willSendEmail = false;
                final String host = configManager.getSmtpHost();
                final int port = configManager.getSmtpPort();
                final boolean useSsl = configManager.isUseSsl();
                final String username = configManager.getSenderEmail();
                final String password = configManager.getSenderPassword();
                final String recipient = configManager.getRecipientEmail();

                if (!host.isEmpty() && !username.isEmpty() && !password.isEmpty() && !recipient.isEmpty()) {
                    willSendEmail = true;
                }

                boolean willSendTg = false;
                final String tgToken = configManager.getTgToken();
                final String tgChatId = configManager.getTgChatId();
                if (configManager.isTgEnabled() && !tgToken.isEmpty() && !tgChatId.isEmpty()) {
                    willSendTg = true;
                }

                boolean willSendApi = false;
                final String apiUrl = configManager.getApiUrl();
                final String apiAuthKey = configManager.getApiAuthKey();
                if (configManager.isApiEnabled() && !apiUrl.isEmpty()) {
                    willSendApi = true;
                }

                if (willSendEmail) {
                    pendingTasks.incrementAndGet();
                    final String emailSubject = context.getString(R.string.email_subject, sender);
                    final String emailBody = context.getString(R.string.email_body, sender, formattedTime, fullBody);

                    configManager.addLog(context.getString(R.string.log_sms_received, sender));

                    EmailSender.send(host, port, useSsl, username, password, recipient, emailSubject, emailBody, new EmailSender.EmailCallback() {
                        @Override
                        public void onSuccess() {
                            configManager.addLog(context.getString(R.string.log_sms_success, sender));
                            checkReleaseWakeLock.run();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            configManager.addLog(context.getString(R.string.log_sms_failed, sender, e.getMessage()) + " (已加入重试队列)");
                            Log.e(TAG, "Error sending email, queuing for retry", e);
                            EmailRetryTask task = new EmailRetryTask(context, host, port, useSsl, username, password, recipient, emailSubject, emailBody, sender);
                            RetryManager.getInstance(context).addTask(task);
                            checkReleaseWakeLock.run();
                        }
                    });
                }

                if (willSendTg) {
                    pendingTasks.incrementAndGet();
                    configManager.addLog(context.getString(R.string.log_sms_received_tg, sender));

                    final String tgMessage = "【SMS Forwarder】\nFrom: " + sender + "\nTime: " + formattedTime + "\nContent:\n" + fullBody;

                    TelegramSender.send(tgToken, tgChatId, tgMessage, new TelegramSender.TelegramCallback() {
                        @Override
                        public void onSuccess() {
                            configManager.addLog(context.getString(R.string.log_sms_success_tg, sender));
                            checkReleaseWakeLock.run();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            configManager.addLog(context.getString(R.string.log_sms_failed_tg, sender, e.getMessage()) + " (已加入重试队列)");
                            Log.e(TAG, "Error sending to Telegram, queuing for retry", e);
                            TelegramRetryTask task = new TelegramRetryTask(context, tgToken, tgChatId, tgMessage, sender);
                            RetryManager.getInstance(context).addTask(task);
                            checkReleaseWakeLock.run();
                        }
                    });
                } else if (configManager.isTgEnabled()) {
                    configManager.addLog(context.getString(R.string.log_sms_incomplete_tg, sender));
                }

                if (willSendApi) {
                    pendingTasks.incrementAndGet();
                    configManager.addLog(context.getString(R.string.log_sms_received_api, sender));

                    // Build ISO 8601 date (e.g. 2026-10-30T09:31:35.000Z)
                    SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String isoDate = isoFormat.format(new Date(smsTimestamp));

                    // Configure Subject
                    String subjectTemplate = configManager.getApiSubject();
                    String finalSubject;
                    if (subjectTemplate == null || subjectTemplate.trim().isEmpty()) {
                        finalSubject = context.getString(R.string.email_subject, sender);
                    } else if (subjectTemplate.contains("%s")) {
                        try {
                            finalSubject = String.format(subjectTemplate, sender);
                        } catch (Exception e) {
                            finalSubject = subjectTemplate;
                        }
                    } else {
                        finalSubject = subjectTemplate;
                    }

                    // Sender Email & From
                    String customSenderEmail = configManager.getApiSenderEmail().trim();
                    String finalSenderEmail = customSenderEmail.isEmpty() ? sender : customSenderEmail;

                    String customFrom = configManager.getApiFrom().trim();
                    String finalFrom;
                    if (!customFrom.isEmpty()) {
                        finalFrom = customFrom;
                    } else {
                        finalFrom = "SMSForwarder <" + finalSenderEmail + ">";
                    }

                    // To
                    String customTo = configManager.getApiTo().trim();
                    String finalTo;
                    if (!customTo.isEmpty()) {
                        finalTo = customTo;
                    } else {
                        String smtpRecipient = configManager.getRecipientEmail().trim();
                        finalTo = smtpRecipient.isEmpty() ? "admin" : smtpRecipient;
                    }

                    // HTML Body
                    String htmlBody = "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333;\">" +
                            "<h3 style=\"margin-top:0; color:#0d6efd;\">收到新短信通知</h3>" +
                            "<table style=\"border-collapse: collapse; width: 100%; margin-bottom: 12px;\">" +
                            "<tr><td style=\"padding: 4px 8px; font-weight: bold; width: 80px;\">发信人:</td><td style=\"padding: 4px 8px;\">" + escapeHtml(sender) + "</td></tr>" +
                            "<tr><td style=\"padding: 4px 8px; font-weight: bold;\">时&nbsp;&nbsp;间:</td><td style=\"padding: 4px 8px;\">" + escapeHtml(formattedTime) + "</td></tr>" +
                            "</table>" +
                            "<div style=\"background: #f8f9fa; border-left: 4px solid #0d6efd; padding: 12px 16px; border-radius: 4px; font-size: 14px; white-space: pre-wrap; word-break: break-word;\">" +
                            escapeHtml(fullBody) +
                            "</div>" +
                            "</div>";

                    try {
                        JSONObject emailData = new JSONObject();
                        emailData.put("id", UUID.randomUUID().toString());
                        emailData.put("subject", finalSubject);
                        emailData.put("from", finalFrom);
                        emailData.put("senderEmail", finalSenderEmail);
                        emailData.put("to", finalTo);
                        emailData.put("date", isoDate);
                        emailData.put("body", htmlBody);
                        emailData.put("plainBody", fullBody);

                        ApiSender.send(apiUrl, apiAuthKey, emailData, new ApiSender.ApiCallback() {
                            @Override
                            public void onSuccess(String response) {
                                configManager.addLog(context.getString(R.string.log_sms_success_api, sender));
                                checkReleaseWakeLock.run();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                configManager.addLog(context.getString(R.string.log_sms_failed_api, sender, e.getMessage()));
                                Log.e(TAG, "Error pushing to API", e);
                                checkReleaseWakeLock.run();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error building API JSON payload", e);
                        configManager.addLog(context.getString(R.string.log_sms_failed_api, sender, e.getMessage()));
                        checkReleaseWakeLock.run();
                    }
                } else if (configManager.isApiEnabled()) {
                    configManager.addLog(context.getString(R.string.log_sms_incomplete_api, sender));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS broadcast", e);
            configManager.addLog(context.getString(R.string.log_sms_error, e.getMessage()));
        }

        // If no tasks were spawned, release locks immediately
        if (pendingTasks.get() == 0) {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br/>");
    }
}
