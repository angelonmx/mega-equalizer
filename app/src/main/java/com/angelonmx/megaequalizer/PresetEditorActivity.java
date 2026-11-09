package com.angelonmx.megaequalizer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.angelonmx.megaequalizer.audio.AudioRouteManager;
import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.DeviceCategory;
import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.ui.BandControlView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates and edits a ten-band preset, including its device assignments. */
public class PresetEditorActivity extends BaseActivity {

    public static final String EXTRA_PRESET_ID = "preset_id";

    private static final int[] BAND_VISIBLE_LABELS = {
            R.string.band_short_31,
            R.string.band_short_62,
            R.string.band_short_125,
            R.string.band_short_250,
            R.string.band_short_500,
            R.string.band_short_1k,
            R.string.band_short_2k,
            R.string.band_short_4k,
            R.string.band_short_8k,
            R.string.band_short_16k
    };

    private static final int[] BAND_ACCESSIBLE_LABELS = {
            R.string.frequency_31,
            R.string.frequency_62,
            R.string.frequency_125,
            R.string.frequency_250,
            R.string.frequency_500,
            R.string.frequency_1k,
            R.string.frequency_2k,
            R.string.frequency_4k,
            R.string.frequency_8k,
            R.string.frequency_16k
    };

    private static final float[][] TEMPLATE_GAINS = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {8, 7, 5, 2, 0, -1, -1, -1, 0, 0},
            {0, 0, 0, 0, 0, 1, 3, 5, 6, 5},
            {-4, -3, -1, 2, 4, 4, 3, 1, 0, -2},
            {-2, -1, 1, 3, 4, 4, 2, 0, -1, -2}
    };

    private PresetRepository repository;
    private Preset preset;
    private String presetId;
    private boolean isNew;
    private boolean loading = true;
    private boolean dirty;
    private boolean saved;

    private TextInputLayout nameLayout;
    private TextInputEditText editName;
    private BandControlView preampControl;
    private final List<BandControlView> bandControls = new ArrayList<>();
    private final Set<String> selectedBluetoothKeys = new LinkedHashSet<>();

    private MaterialCheckBox checkAll;
    private MaterialCheckBox checkSpeaker;
    private MaterialCheckBox checkWired;
    private MaterialCheckBox checkBluetooth;
    private MaterialCheckBox checkUsb;
    private MaterialCheckBox checkAux;
    private MaterialCheckBox checkHdmi;
    private MaterialCheckBox checkOther;
    private TextView textBluetoothSummary;
    private MaterialButton buttonChooseBluetooth;

    private final ActivityResultLauncher<String> bluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    showBluetoothPicker();
                } else {
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
        setContentView(R.layout.activity_preset_editor);
        repository = PresetRepository.get(this);

        MaterialToolbar toolbar = setupBackToolbar();
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        presetId = getIntent().getStringExtra(EXTRA_PRESET_ID);
        Preset existing = repository.getPreset(presetId);
        isNew = existing == null;
        preset = isNew ? new Preset() : existing.copy();
        if (isNew) {
            presetId = null;
        }
        toolbar.setTitle(isNew ? R.string.new_preset_title : R.string.edit_preset_title);

        nameLayout = findViewById(R.id.nameLayout);
        editName = findViewById(R.id.editName);
        editName.setText(preset.name);

        checkAll = findViewById(R.id.checkAllDevices);
        checkSpeaker = findViewById(R.id.checkSpeaker);
        checkWired = findViewById(R.id.checkWired);
        checkBluetooth = findViewById(R.id.checkBluetooth);
        checkUsb = findViewById(R.id.checkUsb);
        checkAux = findViewById(R.id.checkAux);
        checkHdmi = findViewById(R.id.checkHdmi);
        checkOther = findViewById(R.id.checkOther);
        textBluetoothSummary = findViewById(R.id.textBluetoothSummary);
        buttonChooseBluetooth = findViewById(R.id.buttonChooseBluetooth);

        buildControls();
        loadAssignments();
        updateBluetoothSummary();

        editName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!loading) {
                    dirty = true;
                    nameLayout.setError(null);
                }
            }
        });

        View.OnClickListener dirtyClick = view -> {
            if (!loading) {
                dirty = true;
            }
        };
        checkAll.setOnClickListener(dirtyClick);
        checkSpeaker.setOnClickListener(dirtyClick);
        checkWired.setOnClickListener(dirtyClick);
        checkBluetooth.setOnClickListener(dirtyClick);
        checkUsb.setOnClickListener(dirtyClick);
        checkAux.setOnClickListener(dirtyClick);
        checkHdmi.setOnClickListener(dirtyClick);
        checkOther.setOnClickListener(dirtyClick);

        buttonChooseBluetooth.setOnClickListener(view -> chooseBluetooth());
        findViewById(R.id.buttonTemplate).setOnClickListener(view -> showTemplates());
        findViewById(R.id.buttonSave).setOnClickListener(view -> savePreset());

        MaterialButton deleteButton = findViewById(R.id.buttonDelete);
        deleteButton.setVisibility(isNew ? View.GONE : View.VISIBLE);
        deleteButton.setOnClickListener(view -> confirmDelete());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        loading = false;
        dirty = false;
    }

    private void buildControls() {
        LinearLayout preampContainer = findViewById(R.id.preampContainer);
        preampControl = new BandControlView(
                this,
                getString(R.string.preamp_short),
                getString(R.string.preamp_control_description),
                preset.preamp
        );
        preampControl.setOnGainChangedListener((gain, fromUser) -> {
            if (!loading) {
                dirty = true;
            }
        });
        preampContainer.addView(preampControl);

        LinearLayout bandsContainer = findViewById(R.id.bandsContainer);
        for (int band = 0; band < Preset.BAND_COUNT; band++) {
            BandControlView control = new BandControlView(
                    this,
                    getString(BAND_VISIBLE_LABELS[band]),
                    getString(BAND_ACCESSIBLE_LABELS[band]),
                    preset.gains[band]
            );
            final int index = band;
            control.setOnGainChangedListener((gain, fromUser) -> {
                if (!loading) {
                    dirty = true;
                }
            });
            bandControls.add(control);
            bandsContainer.addView(control);
        }
    }

    private void loadAssignments() {
        Set<String> targets = repository.getTargetsForPreset(presetId);
        for (String target : targets) {
            if (target.equals(DeviceCategory.ALL.targetKey())) {
                checkAll.setChecked(true);
            } else if (target.equals(DeviceCategory.SPEAKER.targetKey())) {
                checkSpeaker.setChecked(true);
            } else if (target.equals(DeviceCategory.WIRED.targetKey())) {
                checkWired.setChecked(true);
            } else if (target.equals(DeviceCategory.BLUETOOTH.targetKey())) {
                checkBluetooth.setChecked(true);
            } else if (target.equals(DeviceCategory.USB.targetKey())) {
                checkUsb.setChecked(true);
            } else if (target.equals(DeviceCategory.AUX.targetKey())) {
                checkAux.setChecked(true);
            } else if (target.equals(DeviceCategory.HDMI.targetKey())) {
                checkHdmi.setChecked(true);
            } else if (target.equals(DeviceCategory.OTHER.targetKey())) {
                checkOther.setChecked(true);
            } else if (target.startsWith("bt:") || target.startsWith("type:")) {
                selectedBluetoothKeys.add(target);
            }
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            savePreset();
            return true;
        }
        return false;
    }

    private void showTemplates() {
        String[] labels = {
                getString(R.string.template_flat),
                getString(R.string.template_bass),
                getString(R.string.template_treble),
                getString(R.string.template_voice),
                getString(R.string.template_podcast)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.template_title)
                .setItems(labels, (dialog, which) -> applyTemplate(which))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyTemplate(int which) {
        if (which < 0 || which >= TEMPLATE_GAINS.length) {
            return;
        }
        float[] gains = TEMPLATE_GAINS[which];
        for (int band = 0; band < bandControls.size() && band < gains.length; band++) {
            bandControls.get(band).setGain(gains[band]);
        }
        dirty = true;
        announce(getString(R.string.template_applied_format, templateName(which)));
    }

    private String templateName(int which) {
        switch (which) {
            case 0:
                return getString(R.string.template_flat);
            case 1:
                return getString(R.string.template_bass);
            case 2:
                return getString(R.string.template_treble);
            case 3:
                return getString(R.string.template_voice);
            case 4:
            default:
                return getString(R.string.template_podcast);
        }
    }

    private void chooseBluetooth() {
        if (!AudioRouteManager.hasBluetoothPermission(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.bluetooth_picker_title)
                    .setMessage(R.string.bluetooth_permission_needed)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.grant_bluetooth_permission, (dialog, which) ->
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT))
                    .show();
            return;
        }
        showBluetoothPicker();
    }

    private void showBluetoothPicker() {
        List<AudioRoute> devices = AudioRouteManager.listBluetoothDevices(this);
        final List<String> keys = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (AudioRoute route : devices) {
            String key = route.targetKey();
            if (TextUtils.isEmpty(key) || keys.contains(key)) {
                continue;
            }
            keys.add(key);
            labels.add(AudioRouteManager.describeBluetoothRoute(this, route, route.connected));
        }
        for (String selected : selectedBluetoothKeys) {
            if (!keys.contains(selected)) {
                keys.add(selected);
                labels.add(repository.getTargetLabel(selected));
            }
        }
        if (keys.isEmpty()) {
            Toast.makeText(this, R.string.no_bluetooth_devices, Toast.LENGTH_LONG).show();
            return;
        }

        final boolean[] checked = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            checked[i] = selectedBluetoothKeys.contains(keys.get(i));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bluetooth_picker_title)
                .setMessage(R.string.bluetooth_picker_description)
                .setMultiChoiceItems(
                        labels.toArray(new CharSequence[0]),
                        checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked
                )
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    selectedBluetoothKeys.clear();
                    for (int i = 0; i < keys.size(); i++) {
                        if (checked[i]) {
                            selectedBluetoothKeys.add(keys.get(i));
                        }
                    }
                    for (AudioRoute route : devices) {
                        if (selectedBluetoothKeys.contains(route.targetKey())) {
                            repository.rememberBluetoothDevice(route.targetKey(), route.name);
                        }
                    }
                    dirty = true;
                    updateBluetoothSummary();
                    announce(getString(
                            R.string.bluetooth_specific_summary,
                            selectedBluetoothKeys.size()
                    ));
                })
                .show();
    }

    private void updateBluetoothSummary() {
        String summary;
        if (selectedBluetoothKeys.isEmpty()) {
            summary = getString(R.string.bluetooth_none_summary);
        } else {
            summary = getString(
                    R.string.bluetooth_specific_summary,
                    selectedBluetoothKeys.size()
            );
        }
        textBluetoothSummary.setText(summary);
        buttonChooseBluetooth.setContentDescription(getString(
                R.string.choose_bluetooth_devices
        ) + ". " + summary);
    }

    private void savePreset() {
        String name = editName.getText() == null
                ? ""
                : editName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            nameLayout.setError(getString(R.string.preset_name_required));
            editName.requestFocus();
            announce(getString(R.string.preset_name_required));
            return;
        }
        nameLayout.setError(null);
        if (repository.isNameTaken(name, presetId)) {
            nameLayout.setError(getString(R.string.preset_name_duplicate));
            editName.requestFocus();
            announce(getString(R.string.preset_name_duplicate));
            return;
        }

        if (isNew) {
            preset.id = UUID.randomUUID().toString();
        }
        preset.name = name;
        preset.preamp = preampControl.getGain();
        for (int band = 0; band < bandControls.size(); band++) {
            preset.gains[band] = bandControls.get(band).getGain();
        }

        Set<String> targets = collectTargets();
        Map<String, String> conflicts = repository.findConflicts(targets, preset.id);
        if (!conflicts.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String target : conflicts.keySet()) {
                names.add(repository.getTargetLabel(target));
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.assignment_conflict_title)
                    .setMessage(getString(
                            R.string.assignment_conflict_message,
                            TextUtils.join(", ", names)
                    ))
                    .setNegativeButton(R.string.keep_editing, null)
                    .setPositiveButton(R.string.assignment_conflict_continue, (dialog, which) ->
                            commitSave(targets))
                    .show();
            return;
        }
        commitSave(targets);
    }

    private Set<String> collectTargets() {
        Set<String> targets = new LinkedHashSet<>();
        if (checkAll.isChecked()) {
            targets.add(DeviceCategory.ALL.targetKey());
        }
        if (checkSpeaker.isChecked()) {
            targets.add(DeviceCategory.SPEAKER.targetKey());
        }
        if (checkWired.isChecked()) {
            targets.add(DeviceCategory.WIRED.targetKey());
        }
        if (checkBluetooth.isChecked()) {
            targets.add(DeviceCategory.BLUETOOTH.targetKey());
        }
        if (checkUsb.isChecked()) {
            targets.add(DeviceCategory.USB.targetKey());
        }
        if (checkAux.isChecked()) {
            targets.add(DeviceCategory.AUX.targetKey());
        }
        if (checkHdmi.isChecked()) {
            targets.add(DeviceCategory.HDMI.targetKey());
        }
        if (checkOther.isChecked()) {
            targets.add(DeviceCategory.OTHER.targetKey());
        }
        targets.addAll(selectedBluetoothKeys);
        return targets;
    }

    private void commitSave(Set<String> targets) {
        repository.savePreset(preset);
        repository.replaceAssignmentsForPreset(preset.id, targets);
        isNew = false;
        presetId = preset.id;
        saved = true;
        dirty = false;
        EqualizerService.requestRefresh(this);
        Toast.makeText(this, R.string.preset_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        if (isNew || TextUtils.isEmpty(presetId)) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_preset_title)
                .setMessage(getString(R.string.delete_preset_message, preset.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.deletePreset(presetId);
                    saved = true;
                    dirty = false;
                    EqualizerService.requestRefresh(this);
                    Toast.makeText(this, R.string.preset_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private void handleBack() {
        if (saved || !dirty) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.preset_discard_title)
                .setMessage(R.string.preset_discard_message)
                .setNegativeButton(R.string.keep_editing, null)
                .setPositiveButton(R.string.discard, (dialog, which) -> {
                    saved = true;
                    finish();
                })
                .show();
    }

    @SuppressWarnings("unused")
    private String normalizedName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.getDefault());
    }
}
