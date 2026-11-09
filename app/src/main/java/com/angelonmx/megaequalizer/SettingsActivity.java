package com.angelonmx.megaequalizer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.update.UpdateChecker;
import com.angelonmx.megaequalizer.update.UpdateInfo;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Permissions, updates, theme, service and legal information. */
public class SettingsActivity extends BaseActivity {

    private PresetRepository repository;
    private TextView textAccessibilityStatus;
    private TextView textNotificationStatus;
    private TextView textLastCheck;
    private TextView textUpdateStatus;
    private TextView textThemeSummary;
    private MaterialSwitch switchAutoUpdate;
    private MaterialButton buttonNotifications;
    private MaterialButton buttonInstallReady;
    private boolean binding;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                refresh();
                if (!granted) {
                    Toast.makeText(
                            this,
                            R.string.notifications_permission_denied,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        repository = PresetRepository.get(this);
        setupBackToolbar();

        textAccessibilityStatus = findViewById(R.id.textAccessibilityStatus);
        textNotificationStatus = findViewById(R.id.textNotificationStatus);
        textLastCheck = findViewById(R.id.textLastCheck);
        textUpdateStatus = findViewById(R.id.textUpdateStatus);
        textThemeSummary = findViewById(R.id.textThemeSummary);
        switchAutoUpdate = findViewById(R.id.switchAutoUpdate);
        buttonNotifications = findViewById(R.id.buttonNotifications);
        buttonInstallReady = findViewById(R.id.buttonInstallReady);

        switchAutoUpdate.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                repository.setAutoUpdateEnabled(checked);
            }
        });
        findViewById(R.id.buttonAccessibility).setOnClickListener(view ->
                AccessibilityUtils.openAccessibilitySettings(this));
        buttonNotifications.setOnClickListener(view -> handleNotificationAction());
        findViewById(R.id.buttonCheckUpdates).setOnClickListener(view -> checkUpdates());
        buttonInstallReady.setOnClickListener(view ->
                UpdateChecker.promptReadyUpdate(this));
        findViewById(R.id.buttonTheme).setOnClickListener(view -> showThemeDialog());
        findViewById(R.id.buttonBattery).setOnClickListener(view ->
                AccessibilityUtils.openBatterySettings(this));
        findViewById(R.id.buttonStopService).setOnClickListener(view -> stopService());
        findViewById(R.id.buttonReset).setOnClickListener(view -> confirmReset());
        findViewById(R.id.buttonRepository).setOnClickListener(view ->
                AccessibilityUtils.openUrl(this, BuildConfig.PROJECT_URL));
        findViewById(R.id.buttonPrivacy).setOnClickListener(view ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.about_privacy)
                        .setMessage(R.string.about_privacy_text)
                        .setPositiveButton(R.string.close, null)
                        .show());
        findViewById(R.id.buttonLicense).setOnClickListener(view ->
                startActivity(new Intent(this, LicenseActivity.class)));

        TextView textVersion = findViewById(R.id.textVersion);
        textVersion.setText(getString(
                R.string.about_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
        ));
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (repository == null) {
            return;
        }
        boolean accessibility = AccessibilityUtils.isServiceEnabled(this);
        textAccessibilityStatus.setText(accessibility
                ? R.string.accessibility_active
                : R.string.accessibility_inactive);

        boolean notifications = AccessibilityUtils.areNotificationsEnabled(this);
        textNotificationStatus.setText(notifications
                ? R.string.notifications_enabled
                : R.string.notifications_disabled);
        buttonNotifications.setText(notifications
                ? R.string.open_notification_settings
                : R.string.allow_notifications);

        binding = true;
        switchAutoUpdate.setChecked(repository.isAutoUpdateEnabled());
        binding = false;

        long lastCheck = repository.getLastUpdateCheck();
        textLastCheck.setText(lastCheck <= 0
                ? getString(R.string.last_update_check_never)
                : getString(
                        R.string.last_update_check_format,
                        AccessibilityUtils.formatDateTime(this, lastCheck)
                ));
        textThemeSummary.setText(themeLabel(repository.getThemeMode()));

        boolean ready = UpdateChecker.hasReadyUpdate(this);
        buttonInstallReady.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) {
            String version = UpdateChecker.pending(this)
                    .getString(UpdateChecker.KEY_READY_VERSION, "");
            if (!TextUtils.isEmpty(version)) {
                textUpdateStatus.setText(getString(
                        R.string.update_ready_summary_format,
                        version
                ));
            }
        }
    }

    private String themeLabel(String mode) {
        if (PresetRepository.THEME_LIGHT.equals(mode)) {
            return getString(R.string.theme_light);
        }
        if (PresetRepository.THEME_DARK.equals(mode)) {
            return getString(R.string.theme_dark);
        }
        return getString(R.string.theme_system);
    }

    private void handleNotificationAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        AccessibilityUtils.openNotificationSettings(this);
    }

    private void checkUpdates() {
        textUpdateStatus.setText(R.string.updates_checking);
        MaterialButton button = findViewById(R.id.buttonCheckUpdates);
        button.setEnabled(false);
        UpdateChecker.check(this, (update, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            button.setEnabled(true);
            if (update != null) {
                textUpdateStatus.setText(getString(
                        R.string.update_available_status_format,
                        update.versionName
                ));
                UpdateChecker.showUpdateDialog(this, update);
            } else if (error != null) {
                textUpdateStatus.setText(R.string.updates_error);
            } else {
                textUpdateStatus.setText(R.string.updates_up_to_date);
            }
            refreshLastCheck();
        });
    }

    private void refreshLastCheck() {
        long lastCheck = repository.getLastUpdateCheck();
        textLastCheck.setText(lastCheck <= 0
                ? getString(R.string.last_update_check_never)
                : getString(
                        R.string.last_update_check_format,
                        AccessibilityUtils.formatDateTime(this, lastCheck)
                ));
    }

    private void showThemeDialog() {
        String[] values = {
                PresetRepository.THEME_SYSTEM,
                PresetRepository.THEME_LIGHT,
                PresetRepository.THEME_DARK
        };
        String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        String current = repository.getThemeMode();
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_dialog_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    repository.setThemeMode(values[which]);
                    MegaApp.applySavedTheme(this);
                    textThemeSummary.setText(themeLabel(values[which]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void stopService() {
        EqualizerService.requestStop(this);
        EqualizerService.requestRefresh(this);
        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_all_title)
                .setMessage(R.string.reset_all_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    EqualizerService.requestStop(this);
                    repository.resetAll();
                    MegaApp.applySavedTheme(this);
                    Toast.makeText(this, R.string.all_reset, Toast.LENGTH_LONG).show();
                    Intent main = new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(main);
                    finish();
                })
                .show();
    }
}
