package com.angelonmx.megaequalizer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.audio.AudioRouteManager;
import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.update.UpdateChecker;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Main status screen and entry point for every section. */
public class MainActivity extends BaseActivity {

    private PresetRepository repository;
    private MaterialSwitch switchEnabled;
    private TextView textStatus;
    private TextView textActivePreset;
    private TextView textActiveDevice;
    private TextView textNoPreset;
    private TextView textEngine;
    private MaterialCardView cardAccessibility;
    private boolean bindingSwitch;
    private boolean receiverRegistered;
    private boolean readyReceiverRegistered;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus();
        }
    };

    private final BroadcastReceiver readyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isFinishing() && !isDestroyed()) {
                UpdateChecker.promptReadyUpdateIfNeeded(MainActivity.this);
            }
        }
    };

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(
                            MainActivity.this,
                            R.string.notifications_denied_service,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = PresetRepository.get(this);

        switchEnabled = findViewById(R.id.switchEnabled);
        textStatus = findViewById(R.id.textStatus);
        textActivePreset = findViewById(R.id.textActivePreset);
        textActiveDevice = findViewById(R.id.textActiveDevice);
        textNoPreset = findViewById(R.id.textNoPreset);
        textEngine = findViewById(R.id.textEngine);
        cardAccessibility = findViewById(R.id.cardAccessibility);

        switchEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSwitch) {
                return;
            }
            if (checked) {
                enableEqualizer();
            } else {
                disableEqualizer();
            }
        });

        findViewById(R.id.buttonAssign).setOnClickListener(view ->
                startActivity(new Intent(this, DevicesActivity.class)));
        findViewById(R.id.buttonAccessibility).setOnClickListener(view ->
                AccessibilityUtils.openAccessibilitySettings(this));

        MaterialCardView cardPresets = findViewById(R.id.cardPresets);
        MaterialCardView cardDevices = findViewById(R.id.cardDevices);
        MaterialCardView cardApps = findViewById(R.id.cardApps);
        MaterialCardView cardSettings = findViewById(R.id.cardSettings);

        cardPresets.setContentDescription(getString(R.string.nav_presets_description));
        cardDevices.setContentDescription(getString(R.string.nav_devices_description));
        cardApps.setContentDescription(getString(R.string.nav_apps_description));
        cardSettings.setContentDescription(getString(R.string.nav_settings_description));

        cardPresets.setOnClickListener(view ->
                startActivity(new Intent(this, PresetListActivity.class)));
        cardDevices.setOnClickListener(view ->
                startActivity(new Intent(this, DevicesActivity.class)));
        cardApps.setOnClickListener(view ->
                startActivity(new Intent(this, AppsActivity.class)));
        cardSettings.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));

        UpdateChecker.reconcilePendingDownload(this);
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                UpdateChecker.promptReadyUpdateIfNeeded(this);
            }
        }, 700L);
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                UpdateChecker.showWhatsNewIfNeeded(this);
            }
        }, 1400L);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Se comprueba en cada apertura de la interfaz, no solo al crear la
        // actividad, porque el servicio puede seguir vivo entre aperturas.
        UpdateChecker.maybeCheckOnLaunch(this);
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    stateReceiver,
                    new IntentFilter(EqualizerService.ACTION_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            receiverRegistered = true;
        }
        if (!readyReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    readyReceiver,
                    new IntentFilter(UpdateChecker.ACTION_UPDATE_READY),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            readyReceiverRegistered = true;
        }
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository.isEnabled()) {
            EqualizerService.start(this);
        }
        UpdateChecker.promptReadyUpdateIfNeeded(this);
        updateStatus();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (RuntimeException ignored) {
            }
            receiverRegistered = false;
        }
        if (readyReceiverRegistered) {
            try {
                unregisterReceiver(readyReceiver);
            } catch (RuntimeException ignored) {
            }
            readyReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void enableEqualizer() {
        repository.setEnabled(true);
        EqualizerService.start(this);
        requestNotificationPermissionIfNeeded();
        updateStatus();
    }

    private void disableEqualizer() {
        EqualizerService.requestStop(this);
        updateStatus();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void updateStatus() {
        if (repository == null || isFinishing() || isDestroyed()) {
            return;
        }
        boolean enabled = repository.isEnabled();
        bindingSwitch = true;
        switchEnabled.setChecked(enabled);
        bindingSwitch = false;

        AudioRoute route = AudioRouteManager.getCurrentRoute(this);
        boolean accessibility = AccessibilityUtils.isServiceEnabled(this);
        boolean perApp = repository.isPerAppEnabled() && accessibility;
        String foreground = perApp ? repository.getForegroundPackage() : "";
        Preset preset = repository.isEnabled()
                ? repository.resolvePreset(route, foreground, perApp)
                : null;

        if (!enabled) {
            setTextIfChanged(textStatus, getString(R.string.eq_status_off));
            setTextIfChanged(textActivePreset, getString(R.string.active_preset_none));
        } else if (preset != null) {
            setTextIfChanged(textStatus, getString(R.string.eq_status_on));
            setTextIfChanged(
                    textActivePreset,
                    getString(R.string.active_preset_format, preset.name)
            );
        } else {
            setTextIfChanged(textStatus, getString(R.string.eq_status_no_preset));
            setTextIfChanged(textActivePreset, getString(R.string.active_preset_none));
        }
        setTextIfChanged(
                textActiveDevice,
                getString(R.string.current_device_format, route.displayName(this))
        );

        boolean showNoPreset = enabled && preset == null;
        textNoPreset.setVisibility(showNoPreset ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonAssign).setVisibility(showNoPreset ? View.VISIBLE : View.GONE);

        String engine = repository.getActiveEngineLabel();
        String error = repository.getActiveError();
        if (!enabled) {
            setTextIfChanged(textEngine, "");
        } else if (!TextUtils.isEmpty(error)) {
            setTextIfChanged(textEngine, error);
        } else if (!TextUtils.isEmpty(engine) && preset != null) {
            setTextIfChanged(textEngine, getString(R.string.engine_format, engine));
        } else {
            setTextIfChanged(textEngine, getString(R.string.notification_text_initializing));
        }

        cardAccessibility.setVisibility(accessibility ? View.GONE : View.VISIBLE);
    }

    private void setTextIfChanged(TextView view, String text) {
        if (!TextUtils.equals(view.getText(), text)) {
            view.setText(text);
        }
    }
}
