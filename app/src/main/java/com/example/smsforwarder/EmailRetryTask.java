package com.example.smsforwarder;

import android.content.Context;

/**
 * Encapsulates a failed email send that can be retried.
 */
public class EmailRetryTask implements RetryTask {
    private final Context context;
    private final String host;
    private final int port;
    private final boolean useSsl;
    private final String username;
    private final String password;
    private final String recipient;
    private final String subject;
    private final String body;
    private final String sender;

    public EmailRetryTask(Context context, String host, int port, boolean useSsl,
                          String username, String password, String recipient,
                          String subject, String body, String sender) {
        this.context = context.getApplicationContext();
        this.host = host;
        this.port = port;
        this.useSsl = useSsl;
        this.username = username;
        this.password = password;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.sender = sender;
    }

    @Override
    public void execute(final RetryCallback callback) {
        EmailSender.send(host, port, useSsl, username, password, recipient, subject, body, new EmailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    @Override
    public String getDescription() {
        return "邮件给 " + recipient + " (来自短信 " + sender + ")";
    }
}
