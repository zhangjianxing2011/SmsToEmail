package com.example.smsforwarder;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Handles sending emails asynchronously using JavaMail.
 */
public class EmailSender {
    static {
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");
        } catch (Exception ignored) {}
    }

    // Thread pool to send emails asynchronously without blocking the UI thread
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public interface EmailCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    /**
     * Sends an email using SMTP.
     */
    public static void send(final String host, final int port, final boolean useSsl,
                            final String username, final String password,
                            final String recipient, final String subject, final String body,
                            final EmailCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Properties props = new Properties();
                    props.put("mail.smtp.host", host);
                    props.put("mail.smtp.port", String.valueOf(port));
                    props.put("mail.smtp.auth", "true");

                    if (useSsl) {
                        props.put("mail.smtp.ssl.enable", "true");
                        props.put("mail.smtp.socketFactory.port", String.valueOf(port));
                        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                        props.put("mail.smtp.socketFactory.fallback", "false");
                    } else {
                        props.put("mail.smtp.ssl.enable", "false");
                        props.put("mail.smtp.starttls.enable", "true");
                        props.put("mail.smtp.starttls.required", "true");
                    }

                    // Set timeout values to avoid long hangs in case of poor network
                    props.put("mail.smtp.connectiontimeout", "10000");
                    props.put("mail.smtp.timeout", "10000");
                    props.put("mail.smtp.writetimeout", "10000");

                    Session session = Session.getInstance(props, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(username));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
                    message.setSubject(subject);
                    message.setText(body);

                    Transport.send(message);

                    if (callback != null) {
                        callback.onSuccess();
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                }
            }
        });
    }
}
