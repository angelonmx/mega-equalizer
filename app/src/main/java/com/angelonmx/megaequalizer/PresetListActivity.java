package com.angelonmx.megaequalizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.ui.PresetAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** List of saved presets. */
public class PresetListActivity extends BaseActivity implements PresetAdapter.Listener {

    private PresetRepository repository;
    private PresetAdapter adapter;
    private RecyclerView list;
    private View emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preset_list);
        repository = PresetRepository.get(this);

        MaterialToolbar toolbar = setupBackToolbar();
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        list = findViewById(R.id.listPresets);
        emptyState = findViewById(R.id.emptyState);
        adapter = new PresetAdapter(this, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.buttonCreateFirst).setOnClickListener(view -> createPreset());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<Preset> presets = repository.getPresets();
        adapter.setPresets(presets);
        boolean empty = presets.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            createPreset();
            return true;
        }
        return false;
    }

    private void createPreset() {
        startActivity(new Intent(this, PresetEditorActivity.class));
    }

    @Override
    public void onEdit(Preset preset) {
        Intent intent = new Intent(this, PresetEditorActivity.class)
                .putExtra(PresetEditorActivity.EXTRA_PRESET_ID, preset.id);
        startActivity(intent);
    }

    @Override
    public void onMore(Preset preset, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.duplicate);
        popup.getMenu().add(0, 2, 1, R.string.delete);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                Preset copy = repository.duplicatePreset(preset);
                if (copy != null) {
                    EqualizerService.requestRefresh(this);
                    reload();
                    announce(getString(R.string.preset_duplicated_format, preset.name));
                }
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDelete(preset);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(Preset preset) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_preset_title)
                .setMessage(getString(R.string.delete_preset_message, preset.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.deletePreset(preset.id);
                    EqualizerService.requestRefresh(this);
                    reload();
                    Toast.makeText(this, R.string.preset_deleted, Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
