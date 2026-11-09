package com.angelonmx.megaequalizer;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;
import com.angelonmx.megaequalizer.ui.AppsAdapter;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional per-app profiles based on the foreground package. */
public class AppsActivity extends BaseActivity implements AppsAdapter.Listener {

    private PresetRepository repository;
    private AppsAdapter adapter;
    private final List<AppsAdapter.AppEntry> allApps = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String query = "";
    private boolean loading = true;
    private boolean destroyed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        repository = PresetRepository.get(this);
        setupBackToolbar();

        RecyclerView list = findViewById(R.id.listApps);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppsAdapter(this, this);
        list.setAdapter(adapter);
        adapter.setApps(new ArrayList<>());
        adapter.setEmptyState(true, R.string.apps_loading);
        refreshAccessibilityState();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessibilityState();
        if (repository.isEnabled()) {
            EqualizerService.requestRefresh(this);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refreshAccessibilityState() {
        boolean accessibility = AccessibilityUtils.isServiceEnabled(this);
        adapter.setState(accessibility, repository.isPerAppEnabled());
    }

    private void loadApps() {
        loading = true;
        executor.execute(() -> {
            PackageManager manager = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved;
            try {
                resolved = manager.queryIntentActivities(main, 0);
            } catch (RuntimeException error) {
                resolved = new ArrayList<>();
            }
            Map<String, AppsAdapter.AppEntry> byPackage = new HashMap<>();
            for (ResolveInfo info : resolved) {
                if (info == null || info.activityInfo == null
                        || info.activityInfo.applicationInfo == null) {
                    continue;
                }
                String packageName = info.activityInfo.applicationInfo.packageName;
                if (TextUtils.isEmpty(packageName)
                        || packageName.equals(getPackageName())
                        || byPackage.containsKey(packageName)) {
                    continue;
                }
                String label;
                try {
                    label = info.loadLabel(manager).toString();
                } catch (RuntimeException error) {
                    label = packageName;
                }
                if (TextUtils.isEmpty(label)) {
                    label = packageName;
                }
                android.graphics.drawable.Drawable icon;
                try {
                    icon = info.loadIcon(manager);
                } catch (RuntimeException error) {
                    icon = null;
                }
                byPackage.put(packageName, new AppsAdapter.AppEntry(
                        packageName,
                        label,
                        icon
                ));
            }
            List<AppsAdapter.AppEntry> loaded = new ArrayList<>(byPackage.values());
            Collator collator = Collator.getInstance(Locale.getDefault());
            loaded.sort((left, right) -> collator.compare(left.label, right.label));
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                allApps.clear();
                allApps.addAll(loaded);
                loading = false;
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AppsAdapter.AppEntry> filtered = new ArrayList<>();
        if (normalized.isEmpty()) {
            filtered.addAll(allApps);
        } else {
            for (AppsAdapter.AppEntry entry : allApps) {
                if (entry.label.toLowerCase(Locale.ROOT).contains(normalized)
                        || entry.packageName.toLowerCase(Locale.ROOT).contains(normalized)) {
                    filtered.add(entry);
                }
            }
        }
        adapter.setApps(filtered);
        if (loading) {
            adapter.setEmptyState(true, R.string.apps_loading);
        } else if (filtered.isEmpty()) {
            adapter.setEmptyState(
                    true,
                    normalized.isEmpty() ? R.string.apps_empty : R.string.apps_search_no_results
            );
        } else {
            adapter.setEmptyState(false, R.string.apps_empty);
        }
    }

    @Override
    public void onAccessibilitySettings() {
        AccessibilityUtils.openAccessibilitySettings(this);
    }

    @Override
    public void onPerAppChanged(boolean enabled) {
        repository.setPerAppEnabled(enabled);
        EqualizerService.requestRefresh(this);
        adapter.setState(AccessibilityUtils.isServiceEnabled(this), enabled);
    }

    @Override
    public void onSearchChanged(String value) {
        query = value == null ? "" : value;
        adapter.setQuery(query);
        applyFilter();
    }

    @Override
    public void onAppClicked(AppsAdapter.AppEntry entry) {
        List<Preset> presets = repository.getPresets();
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.app_use_device_preset));
        labels.add(getString(R.string.app_no_effect));
        for (Preset preset : presets) {
            labels.add(getString(R.string.app_behavior_preset, preset.name));
        }

        String current = repository.getAppOverride(entry.packageName);
        int checked = 0;
        if (PresetRepository.APP_NONE.equals(current)) {
            checked = 1;
        } else if (!TextUtils.isEmpty(current)) {
            for (int i = 0; i < presets.size(); i++) {
                if (presets.get(i).id.equals(current)) {
                    checked = i + 2;
                    break;
                }
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.app_behavior_title, entry.label))
                .setSingleChoiceItems(
                        labels.toArray(new CharSequence[0]),
                        checked,
                        (DialogInterface dialog, int which) -> {
                            dialog.dismiss();
                            if (which == 0) {
                                repository.setAppOverride(entry.packageName, null);
                            } else if (which == 1) {
                                repository.setAppOverride(
                                        entry.packageName,
                                        PresetRepository.APP_NONE
                                );
                            } else {
                                repository.setAppOverride(
                                        entry.packageName,
                                        presets.get(which - 2).id
                                );
                            }
                            if (AccessibilityUtils.isServiceEnabled(this)
                                    && !repository.isPerAppEnabled()) {
                                repository.setPerAppEnabled(true);
                                adapter.setState(true, true);
                            }
                            EqualizerService.requestRefresh(this);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(
                                    this,
                                    getString(R.string.app_behavior_updated_format, entry.label),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public String behaviorFor(String packageName) {
        String value = repository.getAppOverride(packageName);
        if (PresetRepository.APP_NONE.equals(value)) {
            return getString(R.string.app_behavior_none);
        }
        if (!TextUtils.isEmpty(value)) {
            Preset preset = repository.getPreset(value);
            if (preset != null) {
                return getString(R.string.app_behavior_preset, preset.name);
            }
        }
        return getString(R.string.app_behavior_device);
    }
}
