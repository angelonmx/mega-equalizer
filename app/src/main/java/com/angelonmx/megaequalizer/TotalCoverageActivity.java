package com.angelonmx.megaequalizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Settings screen for total coverage: per-session effects discovered through
 * the device's own wireless debugging daemon or through root.
 */
public class TotalCoverageActivity extends BaseActivity {

    private PresetRepository repository;
    private MaterialSwitch switchTotalCoverage;
    private RadioGroup groupMode;
    private RadioGroup groupBackend;
    private View cardAdb;
    private View cardRoot;
    private TextInputEditText editConnectPort;
    private TextInputEditText editPairingPort;
    private TextInputEditText editPairingCode;
    private MaterialButton buttonPair;
    private MaterialButton buttonTestConnection;
    private MaterialButton buttonTestRoot;
    private TextView textPairingStatus;
    private TextView textRootStatus;
    private TextView textCoverageStatus;
    private TextView textCoverageDetail;
    private TextView textCoverageSessions;
    private boolean binding;
    private boolean stateReceiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isFinishing() && !isDestroyed()) {
                refreshStatus();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_total_coverage);
        repository = PresetRepository.get(this);
        setupBackToolbar();

        switchTotalCoverage = findViewById(R.id.switchTotalCoverage);
        groupMode = findViewById(R.id.groupMode);
        groupBackend = findViewById(R.id.groupBackend);
        cardAdb = findViewById(R.id.cardAdb);
        cardRoot = findViewById(R.id.cardRoot);
        editConnectPort = findViewById(R.id.editConnectPort);
        editPairingPort = findViewById(R.id.editPairingPort);
        editPairingCode = findViewById(R.id.editPairingCode);
        buttonPair = findViewById(R.id.buttonPair);
        buttonTestConnection = findViewById(R.id.buttonTestConnection);
        buttonTestRoot = findViewById(R.id.buttonTestRoot);
        textPairingStatus = findViewById(R.id.textPairingStatus);
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
                    repository.setCoverageBackend(PresetRepository.COVERAGE_BACKEND_ADB);
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
            } else {
                repository.setCoverageMode(PresetRepository.COVERAGE_MODE_AUTO);
                requestCoverageRefresh();
            }
        });

        groupBackend.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) {
                return;
            }
            repository.setCoverageBackend(checkedId == R.id.radioRoot
                    ? PresetRepository.COVERAGE_BACKEND_ROOT
                    : PresetRepository.COVERAGE_BACKEND_ADB);
            updateBackendCards();
            requestCoverageRefresh();
        });

        buttonPair.setOnClickListener(view -> pairAndConnect());
        buttonTestConnection.setOnClickListener(view -> testConnection());
        buttonTestRoot.setOnClickListener(view -> testRoot());
        findViewById(R.id.buttonRefreshCoverage).setOnClickListener(view -> {
            requestCoverageRefresh();
            textPairingStatus.setText(R.string.coverage_test_in_progress);
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
        super.onStop();
    }

    private void bindValues() {
        binding = true;
        switchTotalCoverage.setChecked(repository.isTotalCoverageEnabled());
        groupMode.check(repository.isSessionOnlyCoverage()
                ? R.id.radioSessionOnly
                : R.id.radioAuto);
        groupBackend.check(PresetRepository.COVERAGE_BACKEND_ROOT.equals(
                repository.getCoverageBackend())
                ? R.id.radioRoot
                : R.id.radioAdb);
        int connectPort = repository.getAdbConnectPort();
        int pairingPort = repository.getAdbPairingPort();
        if (editConnectPort.getText() == null || editConnectPort.getText().length() == 0) {
            editConnectPort.setText(connectPort > 0 ? String.valueOf(connectPort) : "");
        }
        if (editPairingPort.getText() == null || editPairingPort.getText().length() == 0) {
            editPairingPort.setText(pairingPort > 0 ? String.valueOf(pairingPort) : "");
        }
        binding = false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            textPairingStatus.setText(R.string.coverage_adb_android_requirement);
        } else if (repository.isAdbPaired() && repository.getAdbConnectPort() > 0) {
            textPairingStatus.setText(getString(
                    R.string.coverage_port_saved_format,
                    repository.getAdbConnectPort()
            ));
        }
    }

    private void updateBackendCards() {
        boolean root = PresetRepository.COVERAGE_BACKEND_ROOT.equals(
                repository.getCoverageBackend());
        cardAdb.setVisibility(root ? View.GONE : View.VISIBLE);
        cardRoot.setVisibility(root ? View.VISIBLE : View.GONE);
        buttonPair.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
    }

    private void startCoverage() {
        if (PresetRepository.COVERAGE_BACKEND_ADB.equals(repository.getCoverageBackend())
                && repository.getAdbConnectPort() <= 0) {
            textCoverageStatus.setText(R.string.coverage_config_missing_title);
            textCoverageDetail.setText(R.string.coverage_config_missing_message);
            textCoverageSessions.setText("");
        }
        requestCoverageRefresh();
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

    private void pairAndConnect() {
        Integer pairingPort = parsePort(editPairingPort);
        Integer connectPort = parsePort(editConnectPort);
        if (pairingPort == null || connectPort == null) {
            Toast.makeText(this, R.string.coverage_port_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        String code = textOf(editPairingCode);
        if (code.length() != 6) {
            Toast.makeText(this, R.string.coverage_pairing_code_invalid, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, R.string.coverage_adb_android_requirement, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        setPairingBusy(true, getString(R.string.coverage_pairing_in_progress));
        CoverageController.pairAndVerify(
                getApplicationContext(),
                pairingPort,
                code,
                connectPort,
                (success, message) -> runOnUiThread(() -> {
                    setPairingBusy(false, message);
                    if (success) {
                        repository.setTotalCoverageEnabled(true);
                        bindValues();
                        startCoverage();
                        announce(message);
                    }
                    refreshStatus();
                })
        );
    }

    private void testConnection() {
        Integer connectPort = parsePort(editConnectPort);
        if (connectPort == null) {
            Toast.makeText(this, R.string.coverage_port_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        setPairingBusy(true, getString(R.string.coverage_test_in_progress));
        CoverageController.testConnection(
                getApplicationContext(),
                connectPort,
                (success, message) -> runOnUiThread(() -> {
                    setPairingBusy(false, message);
                    if (success) {
                        repository.setTotalCoverageEnabled(true);
                        bindValues();
                        startCoverage();
                        announce(message);
                    }
                    refreshStatus();
                })
        );
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

    private void setPairingBusy(boolean busy, String message) {
        buttonPair.setEnabled(!busy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        buttonTestConnection.setEnabled(!busy);
        editConnectPort.setEnabled(!busy);
        editPairingPort.setEnabled(!busy);
        editPairingCode.setEnabled(!busy);
        textPairingStatus.setText(message);
        announce(message);
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

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private static Integer parsePort(TextInputEditText editText) {
        String text = textOf(editText);
        if (text.isEmpty()) {
            return null;
        }
        try {
            int port = Integer.parseInt(text);
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
