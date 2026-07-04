package com.example.smsforwarder;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages retry attempts for failed forwardings with Fibonacci intervals:
 * 10s, 20s, 30s, 50s, 80s.
 * If another failure arrives, the retry count is reset to 0 and all
 * failed tasks in the queue are scheduled to retry together starting from 10s.
 */
public class RetryManager {
    private static final String TAG = "RetryManager";
    private static RetryManager instance;

    private final Context context;
    private final ConfigManager configManager;
    private final Handler handler;

    private final List<RetryTask> failedTasks = new ArrayList<>();
    private int retryCount = 0;
    private Runnable retryRunnable;
    private boolean isTimerScheduled = false;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private RetryManager(Context context) {
        this.context = context.getApplicationContext();
        this.configManager = new ConfigManager(this.context);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public static synchronized RetryManager getInstance(Context context) {
        if (instance == null) {
            instance = new RetryManager(context);
        }
        return instance;
    }

    /**
     * Queues a failed task to be retried.
     */
    public synchronized void addTask(RetryTask task) {
        failedTasks.add(task);
        configManager.addLog("任务已加入重试队列: " + task.getDescription() + "，当前队列任务数: " + failedTasks.size());
        
        // Reset retry count to 0 since a new failure has arrived, restarting the 6-retry cycle
        retryCount = 0;
        
        // Reschedule the first retry attempt (5s delay)
        rescheduleTimer(5000);
        
        // Keep CPU and Wi-Fi active
        acquireLocks();
    }

    private synchronized void rescheduleTimer(long delayMs) {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
        }
        
        retryRunnable = new Runnable() {
            @Override
            public void run() {
                runBatch();
            }
        };
        
        handler.postDelayed(retryRunnable, delayMs);
        isTimerScheduled = true;
    }

    /**
     * Executes the current batch of failed tasks.
     */
    private synchronized void runBatch() {
        isTimerScheduled = false;
        if (failedTasks.isEmpty()) {
            checkReleaseLocks();
            return;
        }

        retryCount++; // Increment retry attempt count (1 to 6)
        configManager.addLog("开始第 " + retryCount + " 次重试，当前队列中有 " + failedTasks.size() + " 个任务");

        final List<RetryTask> tasksToRun = new ArrayList<>(failedTasks);
        failedTasks.clear();

        final int totalTasks = tasksToRun.size();
        final AtomicInteger completedTasks = new AtomicInteger(0);
        final List<RetryTask> currentBatchFailures = Collections.synchronizedList(new ArrayList<RetryTask>());

        for (final RetryTask task : tasksToRun) {
            task.execute(new RetryTask.RetryCallback() {
                @Override
                public void onSuccess() {
                    configManager.addLog("重试成功: " + task.getDescription());
                    checkBatchFinished(completedTasks, totalTasks, currentBatchFailures);
                }

                @Override
                public void onFailure(Exception e) {
                    configManager.addLog("重试失败: " + task.getDescription() + "，原因: " + e.getMessage());
                    currentBatchFailures.add(task);
                    checkBatchFinished(completedTasks, totalTasks, currentBatchFailures);
                }
            });
        }
    }

    private synchronized void checkBatchFinished(AtomicInteger completedTasks, int totalTasks, List<RetryTask> currentBatchFailures) {
        if (completedTasks.incrementAndGet() == totalTasks) {
            // All tasks in the current batch have finished
            if (!currentBatchFailures.isEmpty()) {
                if (retryCount < 6) {
                    // Re-enqueue failures
                    failedTasks.addAll(currentBatchFailures);
                    
                    // Fetch Fibonacci interval for next attempt
                    long delayMs = getFibonacciDelay(retryCount);
                    configManager.addLog("本轮重试结束，仍有失败任务。已安排下一次重试 (将在 " + (delayMs / 1000) + " 秒后)");
                    rescheduleTimer(delayMs);
                } else {
                    // Maximum retry limit reached
                    for (RetryTask task : currentBatchFailures) {
                        configManager.addLog("重试已达上限，发送失败: " + task.getDescription());
                    }
                    checkReleaseLocks();
                }
            } else {
                // All tasks succeeded
                configManager.addLog("所有重试任务已成功发送！");
                checkReleaseLocks();
            }
        }
    }

    private long getFibonacciDelay(int count) {
        switch (count) {
            case 0:
                return 5000;   // 5s
            case 1:
                return 5000;   // 5s
            case 2:
                return 10000;  // 10s
            case 3:
                return 15000;  // 15s
            case 4:
                return 25000;  // 25s
            case 5:
                return 40000;  // 40s
            default:
                return 5000;
        }
    }

    private synchronized void checkReleaseLocks() {
        if (failedTasks.isEmpty() && !isTimerScheduled) {
            releaseLocks();
        }
    }

    private synchronized void acquireLocks() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSForwarder:RetryManagerWakeLock");
                wakeLock.acquire(300000); // 5 minutes max timeout
            }
        } else if (!wakeLock.isHeld()) {
            wakeLock.acquire(300000);
        }

        if (wifiLock == null) {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SMSForwarder:RetryManagerWifiLock");
                } else {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "SMSForwarder:RetryManagerWifiLock");
                }
                wifiLock.acquire();
            }
        } else if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
    }

    private synchronized void releaseLocks() {
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
