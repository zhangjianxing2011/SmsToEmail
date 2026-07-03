package com.example.smsforwarder;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView tvStatus;
    private SwitchCompat switchService;
    private Button btnRequestPermission;
    private TextView tvPermissionHint;

    // Collapsible SMTP Card views
    private LinearLayout layoutSmtpHeader;
    private LinearLayout layoutSmtpContent;
    private ImageView ivSmtpArrow;

    private AutoCompleteTextView etSmtpHost;
    private EditText etSmtpPort;
    private EditText etSenderEmail;
    private EditText etSenderPassword;
    private SwitchCompat switchSsl;

    // Dynamic Recipient Email views
    private LinearLayout containerRecipients;
    private Button btnAddRecipient;
    private final List<View> recipientRowViews = new ArrayList<>();

    // Collapsible TG Card views
    private LinearLayout layoutTgHeader;
    private LinearLayout layoutTgContent;
    private ImageView ivTgArrow;

    // Telegram Bot Settings views
    private EditText etTgToken;
    private EditText etTgChatId;
    private SwitchCompat switchTgEnabled;
    private Button btnTgSave;
    private Button btnTgTest;

    private Button btnSave;
    private Button btnTest;
    private Button btnBatteryOptimization;

    private TextView tvLogs;
    private Button btnClearLogs;

    private ConfigManager configManager;
    private final Handler logUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable logUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Optimize system status bar icons readability (light/dark adaptive icons)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_NO) {
                    // Light theme: status bar background is light, use dark icons
                    controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // Dark theme: status bar background is dark, use light icons
                    controller.setSystemBarsAppearance(
                            0,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        }

        configManager = new ConfigManager(this);

        initViews();
        loadSettings();
        setupListeners();
        setupSmtpAutocomplete();
        updateServiceStatusUI();
        checkBatteryOptimizations();

        // Check if permissions are already granted to hide the request button
        if (hasAllPermissions()) {
            btnRequestPermission.setVisibility(View.GONE);
            tvPermissionHint.setVisibility(View.GONE);
        }

        // Auto-refresh logs while activity is visible
        logUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateLogsUI();
                updateServiceStatusUI();
                logUpdateHandler.postDelayed(this, 2000); // refresh every 2 seconds
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        logUpdateHandler.post(logUpdateRunnable);
        checkBatteryOptimizations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        logUpdateHandler.removeCallbacks(logUpdateRunnable);
    }

    private void initViews() {
        TextView appTitle = findViewById(R.id.app_title);
        if (appTitle != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                appTitle.setText(getString(R.string.app_name) + " v" + versionName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        tvStatus = findViewById(R.id.tv_status);
        switchService = findViewById(R.id.switch_service);
        btnRequestPermission = findViewById(R.id.btn_request_permission);
        tvPermissionHint = findViewById(R.id.tv_permission_hint);

        layoutSmtpHeader = findViewById(R.id.layout_smtp_header);
        layoutSmtpContent = findViewById(R.id.layout_smtp_content);
        ivSmtpArrow = findViewById(R.id.iv_smtp_arrow);

        etSmtpHost = findViewById(R.id.et_smtp_host);
        etSmtpPort = findViewById(R.id.et_smtp_port);
        etSenderEmail = findViewById(R.id.et_sender_email);
        etSenderPassword = findViewById(R.id.et_sender_password);
        switchSsl = findViewById(R.id.switch_ssl);

        containerRecipients = findViewById(R.id.container_recipients);
        btnAddRecipient = findViewById(R.id.btn_add_recipient);

        layoutTgHeader = findViewById(R.id.layout_tg_header);
        layoutTgContent = findViewById(R.id.layout_tg_content);
        ivTgArrow = findViewById(R.id.iv_tg_arrow);

        etTgToken = findViewById(R.id.et_tg_token);
        etTgChatId = findViewById(R.id.et_tg_chat_id);
        switchTgEnabled = findViewById(R.id.switch_tg_enabled);
        btnTgSave = findViewById(R.id.btn_tg_save);
        btnTgTest = findViewById(R.id.btn_tg_test);

        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test);
        btnBatteryOptimization = findViewById(R.id.btn_battery_optimization);

        tvLogs = findViewById(R.id.tv_logs);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
    }

    private void setupSmtpAutocomplete() {
        String[] smtpServers = new String[]{
                "smtp.qq.com",
                "smtp.gmail.com",
                "smtp.163.com",
                "smtp.office365.com"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.smtp_dropdown_item, smtpServers);
        etSmtpHost.setAdapter(adapter);

        // Click to show suggestions list immediately
        etSmtpHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etSmtpHost.showDropDown();
            }
        });

        // Auto-configure Port & SSL toggles when a popular host is selected
        etSmtpHost.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                if ("smtp.gmail.com".equals(selected)) {
                    etSmtpPort.setText("465");
                    switchSsl.setChecked(true);
                } else if ("smtp.office365.com".equals(selected)) {
                    etSmtpPort.setText("587");
                    switchSsl.setChecked(false);
                } else {
                    // Default for QQ, 163, Sina, Aliyun, 139, 189, etc. is port 465 with SSL/TLS
                    etSmtpPort.setText("465");
                    switchSsl.setChecked(true);
                }
            }
        });
    }

    private void loadSettings() {
        etSmtpHost.setText(configManager.getSmtpHost());
        etSmtpPort.setText(String.valueOf(configManager.getSmtpPort()));
        etSenderEmail.setText(configManager.getSenderEmail());
        etSenderPassword.setText(configManager.getSenderPassword());
        switchSsl.setChecked(configManager.isUseSsl());
        switchService.setChecked(configManager.isEnabled());

        // Load recipients dynamically
        containerRecipients.removeAllViews();
        recipientRowViews.clear();
        String recipientsStr = configManager.getRecipientEmail();
        if (recipientsStr.isEmpty()) {
            addRecipientRow("");
        } else {
            String[] arr = recipientsStr.split(",");
            for (String email : arr) {
                addRecipientRow(email.trim());
            }
        }

        // Load Telegram Bot settings
        etTgToken.setText(configManager.getTgToken());
        etTgChatId.setText(configManager.getTgChatId());
        switchTgEnabled.setChecked(configManager.isTgEnabled());
    }

    private void addRecipientRow(String email) {
        final View row = getLayoutInflater().inflate(R.layout.recipient_email_row, containerRecipients, false);
        EditText etEmail = row.findViewById(R.id.et_recipient_email_item);
        etEmail.setText(email);

        ImageButton btnRemove = row.findViewById(R.id.btn_remove_recipient);
        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recipientRowViews.size() > 1) {
                    containerRecipients.removeView(row);
                    recipientRowViews.remove(row);
                } else {
                    Toast.makeText(MainActivity.this, "At least one recipient is required", Toast.LENGTH_SHORT).show();
                }
            }
        });

        containerRecipients.addView(row);
        recipientRowViews.add(row);
    }

    private void setupListeners() {
        // Collapsible SMTP content toggle
        layoutSmtpHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutSmtpContent.getVisibility() == View.VISIBLE) {
                    layoutSmtpContent.setVisibility(View.GONE);
                    ivSmtpArrow.setImageResource(R.drawable.ic_chevron_down);
                } else {
                    layoutSmtpContent.setVisibility(View.VISIBLE);
                    ivSmtpArrow.setImageResource(R.drawable.ic_chevron_up);
                }
            }
        });

        // Collapsible Telegram content toggle
        layoutTgHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutTgContent.getVisibility() == View.VISIBLE) {
                    layoutTgContent.setVisibility(View.GONE);
                    ivTgArrow.setImageResource(R.drawable.ic_chevron_down);
                } else {
                    layoutTgContent.setVisibility(View.VISIBLE);
                    ivTgArrow.setImageResource(R.drawable.ic_chevron_up);
                }
            }
        });

        // Add dynamic recipient email field
        btnAddRecipient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addRecipientRow("");
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saveSettings()) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestEmail();
            }
        });

        btnRequestPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestRequiredPermissions();
            }
        });

        btnClearLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                configManager.clearLogs();
                updateLogsUI();
                Toast.makeText(MainActivity.this, getString(R.string.toast_logs_cleared), Toast.LENGTH_SHORT).show();
            }
        });

        switchService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isChecked = switchService.isChecked();
                if (isChecked) {
                    // Turn on service
                    if (!hasAllPermissions()) {
                        switchService.setChecked(false);
                        Toast.makeText(MainActivity.this, getString(R.string.toast_grant_first), Toast.LENGTH_LONG).show();
                        requestRequiredPermissions();
                        return;
                    }

                    if (!validateInputs()) {
                        switchService.setChecked(false);
                        Toast.makeText(MainActivity.this, getString(R.string.toast_configure_first), Toast.LENGTH_LONG).show();
                        return;
                    }

                    saveSettings();
                    configManager.setEnabled(true);
                    startForwardingService();
                    configManager.addLog(getString(R.string.log_service_start));
                } else {
                    // Turn off service
                    configManager.setEnabled(false);
                    stopForwardingService();
                    configManager.addLog(getString(R.string.log_service_stop));
                }
                updateServiceStatusUI();
            }
        });

        // Telegram Bot Save/Test listeners
        btnTgSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTelegramSettings();
                Toast.makeText(MainActivity.this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
            }
        });

        btnTgTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestTelegram();
            }
        });

        btnBatteryOptimization.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestIgnoreBatteryOptimizations();
            }
        });
    }

    private boolean saveSettings() {
        if (!validateInputs()) {
            Toast.makeText(this, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show();
            return false;
        }

        configManager.setSmtpHost(etSmtpHost.getText().toString().trim());
        try {
            int port = Integer.parseInt(etSmtpPort.getText().toString().trim());
            configManager.setSmtpPort(port);
        } catch (NumberFormatException e) {
            configManager.setSmtpPort(465);
        }
        configManager.setSenderEmail(etSenderEmail.getText().toString().trim());
        // Clean all spaces in the app password (e.g. Gmail generated 16-character password with spaces)
        String rawPassword = etSenderPassword.getText().toString().trim();
        String cleanedPassword = rawPassword.replace(" ", "");
        configManager.setSenderPassword(cleanedPassword);
        
        // Gather and save recipient emails
        StringBuilder sb = new StringBuilder();
        for (View row : recipientRowViews) {
            EditText etEmail = row.findViewById(R.id.et_recipient_email_item);
            String email = etEmail.getText().toString().trim();
            if (!email.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(email);
            }
        }
        configManager.setRecipientEmail(sb.toString());
        
        configManager.setUseSsl(switchSsl.isChecked());
        return true;
    }

    private boolean validateInputs() {
        if (etSmtpHost.getText().toString().trim().isEmpty() ||
            etSmtpPort.getText().toString().trim().isEmpty() ||
            etSenderEmail.getText().toString().trim().isEmpty() ||
            etSenderPassword.getText().toString().trim().isEmpty()) {
            return false;
        }

        // Validate that there is at least one valid email recipient
        boolean hasValidRecipient = false;
        for (View row : recipientRowViews) {
            EditText etEmail = row.findViewById(R.id.et_recipient_email_item);
            String email = etEmail.getText().toString().trim();
            if (!email.isEmpty() && email.contains("@")) {
                hasValidRecipient = true;
            } else if (!email.isEmpty()) {
                // Return false if they entered something invalid
                return false;
            }
        }
        return hasValidRecipient;
    }

    private void saveTelegramSettings() {
        configManager.setTgToken(etTgToken.getText().toString().trim());
        configManager.setTgChatId(etTgChatId.getText().toString().trim());
        configManager.setTgEnabled(switchTgEnabled.isChecked());
    }

    private void sendTestTelegram() {
        final String token = etTgToken.getText().toString().trim();
        final String chatId = etTgChatId.getText().toString().trim();
        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Please configure Token and Chat ID first", Toast.LENGTH_SHORT).show();
            return;
        }

        btnTgTest.setEnabled(false);
        btnTgTest.setText(getString(R.string.btn_test_tg_sending));
        configManager.addLog(getString(R.string.log_tg_test_trigger, chatId));

        TelegramSender.send(token, chatId, "This is a test message from SMS Forwarder app!", new TelegramSender.TelegramCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnTgTest.setEnabled(true);
                        btnTgTest.setText(getString(R.string.btn_test_tg));
                        Toast.makeText(MainActivity.this, getString(R.string.toast_tg_test_success), Toast.LENGTH_SHORT).show();
                        configManager.addLog(getString(R.string.log_tg_test_success));
                    }
                });
            }

            @Override
            public void onFailure(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnTgTest.setEnabled(true);
                        btnTgTest.setText(getString(R.string.btn_test_tg));
                        Toast.makeText(MainActivity.this, getString(R.string.toast_tg_test_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                        configManager.addLog(getString(R.string.log_tg_test_failed, e.getMessage()));
                    }
                });
            }
        });
    }

    private void sendTestEmail() {
        if (!saveSettings()) {
            return;
        }

        btnTest.setEnabled(false);
        btnTest.setText(getString(R.string.btn_test_sending));
        configManager.addLog(getString(R.string.log_test_trigger, configManager.getRecipientEmail()));

        String host = configManager.getSmtpHost();
        int port = configManager.getSmtpPort();
        boolean useSsl = configManager.isUseSsl();
        String username = configManager.getSenderEmail();
        String password = configManager.getSenderPassword();
        String recipient = configManager.getRecipientEmail();

        String subject = "[SMS Forwarder] Test Connection";
        String body = "This is a test email sent from the SMS Forwarder Android application.\n\n" +
                "Your SMTP configurations are correct, and network access is working successfully!\n\n" +
                "Timestamp: " + new java.util.Date().toString();

        EmailSender.send(host, port, useSsl, username, password, recipient, subject, body, new EmailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnTest.setEnabled(true);
                        btnTest.setText(getString(R.string.btn_test));
                        configManager.addLog(getString(R.string.log_test_success));
                        Toast.makeText(MainActivity.this, getString(R.string.toast_test_success), Toast.LENGTH_SHORT).show();
                        updateLogsUI();
                    }
                });
            }

            @Override
            public void onFailure(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnTest.setEnabled(true);
                        btnTest.setText(getString(R.string.btn_test));
                        configManager.addLog(getString(R.string.log_test_failed, e.getMessage()));
                        Toast.makeText(MainActivity.this, getString(R.string.toast_test_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                        updateLogsUI();
                    }
                });
            }
        });
    }

    private void startForwardingService() {
        Intent serviceIntent = new Intent(this, SmsForwardingService.class);
        try {
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_service_start_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopForwardingService() {
        Intent serviceIntent = new Intent(this, SmsForwardingService.class);
        stopService(serviceIntent);
    }

    private void updateServiceStatusUI() {
        boolean isServiceRunning = isServiceRunning(SmsForwardingService.class);
        if (isServiceRunning) {
            tvStatus.setText(getString(R.string.status_running));
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            switchService.setChecked(true);
        } else {
            // If the user enabled it but the service isn't running (e.g. killed by OS), restart it!
            if (configManager.isEnabled()) {
                startForwardingService();
                isServiceRunning = isServiceRunning(SmsForwardingService.class);
            }

            if (isServiceRunning) {
                tvStatus.setText(getString(R.string.status_running));
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                switchService.setChecked(true);
            } else {
                tvStatus.setText(getString(R.string.status_stopped));
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                switchService.setChecked(false);
            }
        }
    }

    private void updateLogsUI() {
        List<String> logs = configManager.getLogs();
        if (logs.isEmpty()) {
            tvLogs.setText(getString(R.string.logs_empty));
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append("\n\n");
        }
        tvLogs.setText(sb.toString());
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        if (serviceClass.equals(SmsForwardingService.class)) {
            return SmsForwardingService.isRunning;
        }
        return false;
    }

    // Permission check & requests
    private boolean hasAllPermissions() {
        boolean smsReceive = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean smsRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        
        boolean notification = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notification = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        
        return smsReceive && smsRead && notification;
    }

    private void requestRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECEIVE_SMS);
        permissions.add(Manifest.permission.READ_SMS);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, getString(R.string.toast_permissions_granted), Toast.LENGTH_SHORT).show();
                btnRequestPermission.setVisibility(View.GONE);
                tvPermissionHint.setVisibility(View.GONE);
            } else {
                Toast.makeText(this, getString(R.string.toast_permissions_denied), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                btnBatteryOptimization.setVisibility(View.GONE);
            } else {
                btnBatteryOptimization.setVisibility(View.VISIBLE);
            }
        } else {
            btnBatteryOptimization.setVisibility(View.GONE);
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(this, "Could not open battery settings.", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_battery_ignored), Toast.LENGTH_SHORT).show();
        }
    }
}
