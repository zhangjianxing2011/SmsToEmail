package com.example.smsforwarder;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles pushing email/SMS payload to an HTTP/HTTPS REST endpoint asynchronously.
 */
public class ApiSender {
    private static final String TAG = "ApiSender";
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public interface ApiCallback {
        void onSuccess(String response);
        void onFailure(Exception e);
    }

    /**
     * Sends an emailData JSON payload to the specified API URL with the provided authKey header.
     *
     * @param apiUrl    Target API endpoint (e.g. https://gmail.asiamessenger.com/api/emails)
     * @param authKey   Authentication token for the "authKey" HTTP header
     * @param emailData JSON object representing the message payload
     * @param callback  Callback to receive success or error notifications
     */
    public static void send(final String apiUrl, final String authKey, final JSONObject emailData, final ApiCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(apiUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    // Headers
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                    if (authKey != null && !authKey.trim().isEmpty()) {
                        conn.setRequestProperty("authKey", authKey.trim());
                    }

                    // Write JSON body
                    byte[] outputBytes = emailData.toString().getBytes(StandardCharsets.UTF_8);
                    conn.setRequestProperty("Content-Length", String.valueOf(outputBytes.length));

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(outputBytes);
                        os.flush();
                    }

                    int responseCode = conn.getResponseCode();
                    InputStream is = (responseCode >= 200 && responseCode < 300)
                            ? conn.getInputStream()
                            : conn.getErrorStream();

                    StringBuilder responseSb = new StringBuilder();
                    if (is != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseSb.append(line);
                            }
                        }
                    }

                    String responseText = responseSb.toString();
                    if (responseCode >= 200 && responseCode < 300) {
                        if (callback != null) {
                            callback.onSuccess(responseText);
                        }
                    } else {
                        String errMsg = "HTTP " + responseCode + (responseText.isEmpty() ? "" : ": " + responseText);
                        throw new Exception(errMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to push to API", e);
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
