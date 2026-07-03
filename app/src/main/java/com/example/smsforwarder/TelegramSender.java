package com.example.smsforwarder;

import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles sending messages to a Telegram channel or chat via Telegram Bot API.
 */
public class TelegramSender {
    private static final String TAG = "TelegramSender";
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public interface TelegramCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    /**
     * Sends a text message to a Telegram chat asynchronously.
     */
    public static void send(final String token, final String chatId, final String text, final TelegramCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    // URL encode arguments
                    String postData = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                            "&text=" + URLEncoder.encode(text, "UTF-8");

                    byte[] out = postData.getBytes(StandardCharsets.UTF_8);
                    
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(out);
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } else {
                        throw new Exception("HTTP Response Code: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send Telegram message", e);
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }
}
