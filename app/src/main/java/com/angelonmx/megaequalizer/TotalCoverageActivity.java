package com.angelonmx.megaequalizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.coverage.AudioSessionDumpParser;
import com.angelonmx.megaequalizer.coverage.AudioSessionInfo;
import com.angelonmx.megaequalizer.coverage.CoverageController;
import com.angelonmx.megaequalizer.coverage.RootSessionDiscovery;
import com.angelonmx.megaequalizer.coverage.ShizukuBridge;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Settings screen for total coverage: per-session effects discovered through
 * the device's own wireless debugging daemon or through root.
 */
public class TotalCoverageActivity extends BaseActivity {

    private static final int SHIZUKU_PERMISSION_REQUEST = 3104;

    private PresetRepository repository;
    private MaterialSwitch switchTotalCoverage;
    private RadioGroup groupMode;
    private RadioGroup groupBackend;
    private View cardShizuku;
    private View cardRoot;
    private MaterialButton buttonTestRoot;
    private MaterialButton buttonRequestShizuku;
    private MaterialButton buttonOpenShizuku;
    private TextView textShizukuStatus;
    private TextView textRootStatus;
    private TextView textCoverageStatus;
    private TextView textCoverageDetail;
    private TextView textCoverageSessions;
    private boolean binding;
    private boolean stateReceiverRegistered;
    private boolean shizukuListenerRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isFinishing() && !isDestroyed()) {
                refreshStatus();
            }
        }
    };

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> runOnUiThread(() -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST
                        || isFinishing() || isDestroyed()) {
                    return;
                }
                updateShizukuStatus();
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_SHIZUKU);
                    repository.setTotalCoverageEnabled(true);
                    bindValues();
                    updateBackendCards();
                    requestCoverageRefresh();
                    Toast.makeText(
                            this,
                            R.string.coverage_shizuku_permission_granted,
                            Toast.LENGTH_LONG
                    ).show();
                    announce(getString(R.string.coverage_shizuku_permission_granted));
                } else {
                    Toast.makeText(
                            this,
                            R.string.coverage_shizuku_permission_denied,
                            Toast.LENGTH_LONG
                    ).show();
                    announce(getString(R.string.coverage_shizuku_permission_denied));
                }
                refreshStatus();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_total_coverage);
        repository = PresetRepository.get(this);
        setupBackToolbar();

        switchTotalCoverage = findViewById(R.id.switchTotalCoverage);
        groupMode = findViewById(R.id.groupMode);
        groupBackend = findViewById(R.id.groupBackend);
        cardShizuku = findViewById(R.id.cardShizuku);
        cardRoot = findViewById(R.id.cardRoot);
        buttonTestRoot = findViewById(R.id.buttonTestRoot);
        buttonRequestShizuku = findViewById(R.id.buttonRequestShizuku);
        buttonOpenShizuku = findViewById(R.id.buttonOpenShizuku);
        textShizukuStatus = findViewById(R.id.textShizukuStatus);
        textRootStatus = findViewById(R.id.textRootStatus);
        textCoverageStatus = findViewById(R.id.textCoverageStatus);
        textCoverageDetail = findViewById(R.id.textCoverageDetail);
        textCoverageSessions = findViewById(R.id.textCoverageSessions);

        switchTotalCoverage.setOnCheckedChangeListener((button, checked) -> {
            if (binding) {
                return;
            }
            if (checked) {
                if (PresetRepository.COVERAGE_BACKEND_NONE.equals(
                        repository.getCoverageBackend())) {
                    repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_AUTO);
                }
                repository.setTotalCoverageEnabled(true);
                updateBackendCards();
                startCoverage();
                announce(getString(R.string.coverage_enabled_announcement));
            } else {
                repository.setTotalCoverageEnabled(false);
                repository.clearCoverageStatus();
                requestCoverageRefresh();
                announce(getString(R.string.coverage_disabled_announcement));
            }
            refreshStatus();
        });

        groupMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) {
                return;
            }
            if (checkedId == R.id.radioSessionOnly) {
                confirmSessionOnlyMode();
            } else if (checkedId == R.id.radioSessionAll) {
                confirmSessionAllMode();
            } else {
                repository.setCoverageMode(PresetRepository.COVERAGE_MODE_AUTO);
                requestCoverageRefresh();
            }
        });

        groupBackend.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) {
                return;
            }
            if (checkedId == R.id.radioRoot) {
                repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_ROOT);
            } else if (checkedId == R.id.radioShizuku) {
                repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_SHIZUKU);
            } else {
                repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_AUTO);
            }
            updateBackendCards();
            requestCoverageRefresh();
        });

        buttonTestRoot.setOnClickListener(view -> testRoot());
        buttonRequestShizuku.setOnClickListener(view -> requestShizukuPermission());
        buttonOpenShizuku.setOnClickListener(view -> openShizuku());
        findViewById(R.id.buttonRefreshCoverage).setOnClickListener(view -> {
            requestCoverageRefresh();
            refreshStatus();
        });

        bindValues();
        updateBackendCards();
        refreshStatus();

        if (!repository.isTotalCoverageEnabled()) {
            repository.clearCoverageStatus();
        } else {
            startCoverage();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!stateReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    stateReceiver,
                    new IntentFilter(EqualizerService.ACTION_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            stateReceiverRegistered = true;
        }
        if (!shizukuListenerRegistered) {
            try {
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
                shizukuListenerRegistered = true;
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindValues();
        updateBackendCards();
        refreshStatus();
    }

    @Override
    protected void onStop() {
        if (stateReceiverRegistered) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (RuntimeException ignored) {
            }
            stateReceiverRegistered = false;
        }
        if (shizukuListenerRegistered) {
            try {
                Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
            } catch (Throwable ignored) {
            }
            shizukuListenerRegistered = false;
        }
        super.onStop();
    }

    private void bindValues() {
        binding = true;
        switchTotalCoverage.setChecked(repository.isTotalCoverageEnabled());
        String mode = repository.getCoverageMode();
        if (PresetRepository.COVERAGE_MODE_SESSION_ALL.equals(mode)) {
            groupMode.check(R.id.radioSessionAll);
        } else if (PresetRepository.COVERAGE_MODE_SESSION_ONLY.equals(mode)) {
            groupMode.check(R.id.radioSessionOnly);
        } else {
            groupMode.check(R.id.radioAuto);
        }
        groupBackend.check(backendRadioId(repository.getCoverageBackend()));
        binding = false;
    }

    private int backendRadioId(String backend) {
        if (PresetRepository.COVERAGE_BACKEND_ROOT.equals(backend)) {
            return R.id.radioRoot;
        }
        if (PresetRepository.COVERAGE_BACKEND_SHIZUKU.equals(backend)) {
            return R.id.radioShizuku;
        }
        return R.id.radioBackendAuto;
    }

    private void updateBackendCards() {
        String backend = repository.getCoverageBackend();
        boolean root = PresetRepository.COVERAGE_BACKEND_ROOT.equals(backend);
        boolean shizuku = PresetRepository.COVERAGE_BACKEND_SHIZUKU.equals(backend)
                || PresetRepository.COVERAGE_BACKEND_AUTO.equals(backend)
                || PresetRepository.COVERAGE_BACKEND_NONE.equals(backend);
        cardShizuku.setVisibility(shizuku ? View.VISIBLE : View.GONE);
        cardRoot.setVisibility(root ? View.VISIBLE : View.GONE);
        updateShizukuStatus();
    }

    private void startCoverage() {
        String backend = repository.getCoverageBackend();
        if (PresetRepository.COVERAGE_BACKEND_SHIZUKU.equals(backend)
                && !ShizukuBridge.isAvailable()) {
            textCoverageStatus.setText(R.string.coverage_status_shizuku_unavailable);
            textCoverageDetail.setText(R.string.coverage_shizuku_unavailable_detail);
            textCoverageSessions.setText("");
        } else if ((PresetRepository.COVERAGE_BACKEND_AUTO.equals(backend)
                || PresetRepository.COVERAGE_BACKEND_NONE.equals(backend))
                && !ShizukuBridge.isAvailable()) {
            textCoverageStatus.setText(R.string.coverage_status_shizuku_unavailable);
            textCoverageDetail.setText(R.string.coverage_shizuku_unavailable_detail);
            textCoverageSessions.setText("");
        }
        requestCoverageRefresh();
    }

    private void updateShizukuStatus() {
        boolean available = ShizukuBridge.isAvailable();
        boolean permitted = available && ShizukuBridge.hasPermission();
        int message;
        if (permitted) {
            message = R.string.coverage_shizuku_status_available;
        } else if (available) {
            message = R.string.coverage_shizuku_status_no_permission;
        } else {
            message = R.string.coverage_shizuku_status_unavailable;
        }
        textShizukuStatus.setText(message);
        buttonRequestShizuku.setVisibility(permitted ? View.GONE : View.VISIBLE);
        buttonRequestShizuku.setEnabled(available && !permitted);
    }

    private void requestShizukuPermission() {
        if (!ShizukuBridge.isAvailable()) {
            Toast.makeText(
                    this,
                    R.string.coverage_shizuku_unavailable,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        if (ShizukuBridge.hasPermission()) {
            repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_SHIZUKU);
            repository.setTotalCoverageEnabled(true);
            bindValues();
            updateBackendCards();
            requestCoverageRefresh();
            announce(getString(R.string.coverage_shizuku_permission_granted));
            return;
        }
        try {
            ShizukuBridge.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            textShizukuStatus.setText(R.string.coverage_shizuku_permission_requested);
            announce(getString(R.string.coverage_shizuku_permission_requested));
        } catch (Throwable error) {
            Toast.makeText(
                    this,
                    R.string.coverage_shizuku_unavailable,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void openShizuku() {
        Intent intent = getPackageManager()
                .getLaunchIntentForPackage(ShizukuBridge.PACKAGE_NAME);
        if (intent != null) {
            startActivity(intent);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.coverage_shizuku_heading)
                .setMessage(R.string.coverage_shizuku_not_installed)
                .setPositiveButton(R.string.coverage_shizuku_download, (dialog, which) ->
                        AccessibilityUtils.openUrl(this, "https://github.com/RikkaApps/Shizuku/releases/latest"))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void confirmSessionOnlyMode() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.coverage_mode_session_warning_title)
                .setMessage(R.string.coverage_mode_session_warning_message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    binding = true;
                    groupMode.check(R.id.radioAuto);
                    binding = false;
                })
                .setOnCancelListener(dialog -> {
                    binding = true;
                    groupMode.check(R.id.radioAuto);
                    binding = false;
                })
                .setPositiveButton(R.string.coverage_mode_session, (dialog, which) -> {
                    repository.setCoverageMode(PresetRepository.COVERAGE_MODE_SESSION_ONLY);
                    requestCoverageRefresh();
                })
                .show();
    }

    private void confirmSessionAllMode() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.coverage_mode_session_all_warning_title)
                .setMessage(R.string.coverage_mode_session_all_warning_message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    binding = true;
                    groupMode.check(R.id.radioAuto);
                    binding = false;
                })
                .setOnCancelListener(dialog -> {
                    binding = true;
                    groupMode.check(R.id.radioAuto);
                    binding = false;
                })
                .setPositiveButton(R.string.coverage_mode_session_all, (dialog, which) -> {
                    repository.setCoverageMode(PresetRepository.COVERAGE_MODE_SESSION_ALL);
                    requestCoverageRefresh();
                })
                .show();
    }

    private void testRoot() {
        buttonTestRoot.setEnabled(false);
        textRootStatus.setText(R.string.coverage_root_test_in_progress);
        new Thread(() -> {
            String message;
            try {
                String dump = new RootSessionDiscovery().readDumpsysAudio();
                List<AudioSessionInfo> sessions = AudioSessionDumpParser.parse(
                        dump,
                        getPackageManager(),
                        Process.myUid()
                );
                message = getString(R.string.coverage_root_ok, sessions.size());
            } catch (Throwable error) {
                String detail = error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage();
                message = getString(R.string.coverage_root_unavailable, detail);
            }
            String result = message;
            runOnUiThread(() -> {
                buttonTestRoot.setEnabled(true);
                textRootStatus.setText(result);
                announce(result);
            });
        }, "coverage-root-test").start();
    }

    private void refreshStatus() {
        boolean enabled = repository.isTotalCoverageEnabled();
        String status = repository.getCoverageStatus();
        String detail = repository.getCoverageDetail();
        String summary = repository.getCoverageSessionSummary();
        if (status == null || status.isEmpty()) {
            status = getString(enabled
                    ? R.string.coverage_status_waiting
                    : R.string.coverage_status_disabled);
        }
        textCoverageStatus.setText(status);
        textCoverageDetail.setText(detail == null ? "" : detail);
        textCoverageSessions.setText(summary == null ? "" : summary);
    }

    private void requestCoverageRefresh() {
        EqualizerService.requestRefresh(this);
    }
}
