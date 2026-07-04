package com.example.smsforwarder;

import android.content.Context;

/**
 * Encapsulates a failed Telegram message send that can be retried.
 */
public class TelegramRetryTask implements RetryTask {
    private final Context context;
    private final String token;
    private final String chatId;
    private final String text;
    private final String sender;

    public TelegramRetryTask(Context context, String token, String chatId, String text, String sender) {
        this.context = context.getApplicationContext();
        this.token = token;
        this.chatId = chatId;
        this.text = text;
        this.sender = sender;
    }

    @Override
    public void execute(final RetryCallback callback) {
        TelegramSender.send(token, chatId, text, new TelegramSender.TelegramCallback() {
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
        return "Telegram 消息 (来自短信 " + sender + ")";
    }
}
