package com.example.smsforwarder;

/**
 * Interface representing a task that can be retried by the RetryManager.
 */
public interface RetryTask {
    interface RetryCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    /**
     * Executes the task.
     * @param callback Callback run when execution completes.
     */
    void execute(RetryCallback callback);

    /**
     * Returns a user-friendly description of the task for logging.
     */
    String getDescription();
}
