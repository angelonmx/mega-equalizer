package com.angelonmx.megaequalizer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.audio.AudioRouteManager;
import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.DeviceCategory;
import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Assigns presets to output categories and to specific Bluetooth devices. */
public class DevicesActivity extends BaseActivity {

    private PresetRepository repository;
    private LinearLayout containerOutputs;
    private LinearLayout containerCategories;
    private LinearLayout containerBluetooth;
    private TextView textCurrentOutput;
    private TextView textCurrentPreset;
    private TextView textBluetoothPermission;
    private TextView textNoBluetooth;
    private TextView textNoOutputs;
    private MaterialButton buttonBluetoothPermission;
    private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    private final ActivityResultLauncher<String> bluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                refresh();
                if (!granted) {
                    Toast.makeText(
                            this,
                            R.string.bluetooth_permission_denied,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);
        repository = PresetRepository.get(this);
        setupBackToolbar();

        containerOutputs = findViewById(R.id.containerOutputs);
        containerCategories = findViewById(R.id.containerCategories);
        containerBluetooth = findViewById(R.id.containerBluetooth);
        textCurrentOutput = findViewById(R.id.textCurrentOutput);
        textCurrentPreset = findViewById(R.id.textCurrentPreset);
        textBluetoothPermission = findViewById(R.id.textBluetoothPermission);
        textNoBluetooth = findViewById(R.id.textNoBluetooth);
        textNoOutputs = findViewById(R.id.textNoOutputs);
        buttonBluetoothPermission = findViewById(R.id.buttonBluetoothPermission);

        buttonBluetoothPermission.setOnClickListener(view ->
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    stateReceiver,
                    new IntentFilter(EqualizerService.ACTION_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            receiverRegistered = true;
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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
        super.onStop();
    }

    private void refresh() {
        if (repository == null) {
            return;
        }
        AudioRoute currentRoute = AudioRouteManager.getCurrentRoute(this);
        boolean accessibility = AccessibilityUtils.isServiceEnabled(this);
        boolean perApp = repository.isPerAppEnabled() && accessibility;
        String foreground = perApp ? repository.getForegroundPackage() : "";
        Preset currentPreset = repository.resolvePreset(currentRoute, foreground, perApp);

        textCurrentOutput.setText(currentRoute.displayName());
        if (currentPreset == null) {
            textCurrentPreset.setText(R.string.current_output_no_preset);
        } else {
            textCurrentPreset.setText(getString(
                    R.string.current_output_preset_format,
                    currentPreset.name
            ));
        }

        containerOutputs.removeAllViews();
        containerCategories.removeAllViews();
        containerBluetooth.removeAllViews();

        Set<String> added = new HashSet<>();
        int connectedCount = 0;
        for (AudioRoute route : AudioRouteManager.listConnectedOutputs(this)) {
            if (route.isBluetooth()) {
                continue;
            }
            String target = route.categoryKey();
            if (!added.add(target)) {
                continue;
            }
            addRow(containerOutputs, route.displayName(), target, false);
            connectedCount++;
        }
        textNoOutputs.setVisibility(connectedCount == 0 ? View.VISIBLE : View.GONE);

        for (DeviceCategory category : DeviceCategory.values()) {
            addRow(
                    containerCategories,
                    getString(category.labelRes),
                    category.targetKey(),
                    false
            );
        }

        boolean canReadBluetooth = AudioRouteManager.hasBluetoothPermission(this);
        boolean showPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canReadBluetooth;
        textBluetoothPermission.setVisibility(showPermission ? View.VISIBLE : View.GONE);
        buttonBluetoothPermission.setVisibility(showPermission ? View.VISIBLE : View.GONE);

        List<AudioRoute> bluetoothDevices = AudioRouteManager.listBluetoothDevices(this);
        int bluetoothCount = 0;
        Set<String> bluetoothKeys = new HashSet<>();
        for (AudioRoute route : bluetoothDevices) {
            String target = route.targetKey();
            if (!bluetoothKeys.add(target)) {
                continue;
            }
            String name = AudioRouteManager.describeBluetoothRoute(
                    this,
                    route,
                    route.connected
            );
            addRow(containerBluetooth, name, target, true);
            bluetoothCount++;
        }
        textNoBluetooth.setVisibility(bluetoothCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void addRow(LinearLayout container, String name, String target, boolean specificBluetooth) {
        View row = LayoutInflater.from(this).inflate(R.layout.view_output_row, container, false);
        TextView nameView = row.findViewById(R.id.textOutputName);
        TextView presetView = row.findViewById(R.id.textOutputPreset);
        MaterialButton choose = row.findViewById(R.id.buttonChooseOutputPreset);

        nameView.setText(name);
        String summary = assignmentSummary(target, specificBluetooth);
        presetView.setText(summary);
        choose.setContentDescription(getString(
                R.string.output_assignment_description,
                name,
                summary
        ));
        choose.setOnClickListener(view -> showPresetChooser(
                name,
                target,
                specificBluetooth
        ));
        container.addView(row);
    }

    private String assignmentSummary(String target, boolean specificBluetooth) {
        Preset direct = repository.getPreset(repository.getAssignedPresetId(target));
        if (direct != null) {
            return direct.name;
        }
        if (specificBluetooth) {
            Preset category = repository.getPreset(
                    repository.getAssignedPresetId(DeviceCategory.BLUETOOTH.targetKey())
            );
            if (category != null) {
                return getString(
                        R.string.uses_category_format,
                        getString(R.string.device_bluetooth),
                        category.name
                );
            }
        }
        if (!target.equals(DeviceCategory.ALL.targetKey())) {
            Preset fallback = repository.getPreset(
                    repository.getAssignedPresetId(DeviceCategory.ALL.targetKey())
            );
            if (fallback != null) {
                return getString(R.string.all_devices_assignment_format, fallback.name);
            }
        }
        if (specificBluetooth) {
            return getString(R.string.no_specific_preset_short);
        }
        return getString(R.string.no_preset_short);
    }

    private void showPresetChooser(
            String outputName,
            String target,
            boolean specificBluetooth
    ) {
        List<Preset> presets = repository.getPresets();
        if (presets.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.preset_for_format, outputName))
                    .setMessage(R.string.no_presets_available)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.create_preset, (dialog, which) ->
                            startActivity(new Intent(this, PresetListActivity.class)))
                    .show();
            return;
        }

        List<String> labels = new ArrayList<>();
        labels.add(getString(specificBluetooth
                ? R.string.no_specific_preset_option
                : R.string.no_preset_option));
        int checked = 0;
        String currentId = repository.getAssignedPresetId(target);
        for (int i = 0; i < presets.size(); i++) {
            Preset preset = presets.get(i);
            labels.add(preset.name);
            if (preset.id.equals(currentId)) {
                checked = i + 1;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.preset_for_format, outputName))
                .setSingleChoiceItems(
                        labels.toArray(new CharSequence[0]),
                        checked,
                        (DialogInterface dialog, int which) -> {
                            dialog.dismiss();
                            if (which == 0) {
                                repository.assign(target, null);
                            } else {
                                repository.assign(target, presets.get(which - 1).id);
                            }
                            EqualizerService.requestRefresh(this);
                            announce(getString(R.string.assignment_saved));
                            refresh();
                        }
                )
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
