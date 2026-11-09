package com.angelonmx.megaequalizer.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.angelonmx.megaequalizer.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Local, offline storage for presets, assignments, app profiles and settings.
 * All public mutating methods are synchronized because the foreground service,
 * the accessibility service and the activities share this process.
 */
public final class PresetRepository {

    private static final String TAG = "PresetRepository";
    private static final String PREFS = "mega_equalizer";

    public static final String KEY_PRESETS = "presets_json";
    public static final String KEY_ASSIGNMENTS = "assignments_json";
    public static final String KEY_APP_OVERRIDES = "app_overrides_json";
    public static final String KEY_BT_NAMES = "bt_names_json";
    public static final String KEY_ENABLED = "eq_enabled";
    public static final String KEY_AUTO_UPDATE = "auto_update";
    public static final String KEY_PER_APP_ENABLED = "per_app_enabled";
    public static final String KEY_THEME = "theme_mode";
    public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    public static final String KEY_FOREGROUND_PACKAGE = "foreground_package";
    public static final String KEY_STATUS_RUNNING = "status_running";
    public static final String KEY_STATUS_PRESET_ID = "status_preset_id";
    public static final String KEY_STATUS_PRESET_NAME = "status_preset_name";
    public static final String KEY_STATUS_DEVICE = "status_device";
    public static final String KEY_STATUS_ENGINE = "status_engine";
    public static final String KEY_STATUS_ERROR = "status_error";

    public static final String APP_NONE = "__none__";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static volatile PresetRepository instance;

    private final Context context;
    private final SharedPreferences preferences;

    private PresetRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static PresetRepository get(Context context) {
        if (instance == null) {
            synchronized (PresetRepository.class) {
                if (instance == null) {
                    instance = new PresetRepository(context);
                }
            }
        }
        return instance;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    // ---------------------------------------------------------------- presets

    public synchronized List<Preset> getPresets() {
        List<Preset> presets = new ArrayList<>();
        String raw = preferences.getString(KEY_PRESETS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                try {
                    Preset preset = Preset.fromJson(object);
                    if (!TextUtils.isEmpty(preset.id) && !TextUtils.isEmpty(preset.name)) {
                        presets.add(preset);
                    }
                } catch (JSONException error) {
                    Log.w(TAG, "Preset ignorado por datos inválidos", error);
                }
            }
        } catch (JSONException error) {
            Log.e(TAG, "No se pudo leer la lista de presets", error);
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(presets, (left, right) -> collator.compare(left.name, right.name));
        return presets;
    }

    public synchronized Preset getPreset(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        for (Preset preset : getPresets()) {
            if (id.equals(preset.id)) {
                return preset;
            }
        }
        return null;
    }

    public synchronized void savePreset(Preset preset) {
        if (preset == null) {
            return;
        }
        if (TextUtils.isEmpty(preset.id)) {
            preset.id = UUID.randomUUID().toString();
        }
        preset.name = preset.name == null ? "" : preset.name.trim();
        preset.updatedAt = System.currentTimeMillis();
        List<Preset> presets = getPresets();
        boolean replaced = false;
        for (int i = 0; i < presets.size(); i++) {
            if (preset.id.equals(presets.get(i).id)) {
                presets.set(i, preset);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            presets.add(preset);
        }
        writePresets(presets);
    }

    public synchronized Preset duplicatePreset(Preset source) {
        if (source == null) {
            return null;
        }
        Preset copy = source.copy();
        copy.id = UUID.randomUUID().toString();
        copy.name = uniqueName(context.getString(R.string.preset_duplicated_format, source.name));
        copy.updatedAt = System.currentTimeMillis();
        List<Preset> presets = getPresets();
        presets.add(copy);
        writePresets(presets);
        return copy;
    }

    public synchronized void deletePreset(String id) {
        if (TextUtils.isEmpty(id)) {
            return;
        }
        List<Preset> presets = getPresets();
        presets.removeIf(preset -> id.equals(preset.id));
        writePresets(presets);

        JSONObject assignments = readObject(KEY_ASSIGNMENTS);
        Iterator<String> assignmentKeys = assignments.keys();
        List<String> removeAssignments = new ArrayList<>();
        while (assignmentKeys.hasNext()) {
            String key = assignmentKeys.next();
            if (id.equals(assignments.optString(key))) {
                removeAssignments.add(key);
            }
        }
        for (String key : removeAssignments) {
            assignments.remove(key);
        }
        writeObject(KEY_ASSIGNMENTS, assignments);

        JSONObject overrides = readObject(KEY_APP_OVERRIDES);
        Iterator<String> overrideKeys = overrides.keys();
        List<String> removeOverrides = new ArrayList<>();
        while (overrideKeys.hasNext()) {
            String key = overrideKeys.next();
            if (id.equals(overrides.optString(key))) {
                removeOverrides.add(key);
            }
        }
        for (String key : removeOverrides) {
            overrides.remove(key);
        }
        writeObject(KEY_APP_OVERRIDES, overrides);
    }

    public synchronized boolean isNameTaken(String name, String excludedId) {
        String normalized = name == null ? "" : name.trim();
        for (Preset preset : getPresets()) {
            if (!preset.id.equals(excludedId) && preset.name.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public synchronized String uniqueName(String base) {
        String candidate = base;
        int index = 2;
        while (isNameTaken(candidate, null)) {
            candidate = context.getString(R.string.preset_name_numbered_format, base, index);
            index++;
        }
        return candidate;
    }

    private void writePresets(List<Preset> presets) {
        JSONArray array = new JSONArray();
        for (Preset preset : presets) {
            try {
                array.put(preset.toJson());
            } catch (JSONException error) {
                Log.e(TAG, "No se pudo serializar un preset", error);
            }
        }
        preferences.edit().putString(KEY_PRESETS, array.toString()).apply();
    }

    // ------------------------------------------------------------- assignments

    public synchronized void assign(String targetKey, String presetId) {
        if (TextUtils.isEmpty(targetKey)) {
            return;
        }
        JSONObject assignments = readObject(KEY_ASSIGNMENTS);
        try {
            if (TextUtils.isEmpty(presetId)) {
                assignments.remove(targetKey);
            } else {
                assignments.put(targetKey, presetId);
            }
            writeObject(KEY_ASSIGNMENTS, assignments);
        } catch (JSONException error) {
            Log.e(TAG, "No se pudo guardar la asignación", error);
        }
    }

    public synchronized String getAssignedPresetId(String targetKey) {
        if (TextUtils.isEmpty(targetKey)) {
            return null;
        }
        JSONObject assignments = readObject(KEY_ASSIGNMENTS);
        String id = assignments.optString(targetKey, null);
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        if (getPreset(id) == null) {
            assignments.remove(targetKey);
            writeObject(KEY_ASSIGNMENTS, assignments);
            return null;
        }
        return id;
    }

    public synchronized Set<String> getTargetsForPreset(String presetId) {
        Set<String> result = new HashSet<>();
        if (TextUtils.isEmpty(presetId)) {
            return result;
        }
        JSONObject assignments = readObject(KEY_ASSIGNMENTS);
        Iterator<String> keys = assignments.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (presetId.equals(assignments.optString(key))) {
                result.add(key);
            }
        }
        return result;
    }

    public synchronized Map<String, String> findConflicts(Collection<String> targetKeys, String presetId) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        JSONObject assignments = readObject(KEY_ASSIGNMENTS);
        for (String target : targetKeys) {
            String owner = assignments.optString(target, null);
            if (!TextUtils.isEmpty(owner) && !owner.equals(presetId) && getPreset(owner) != null) {
                conflicts.put(target, owner);
            }
        }
        return conflicts;
    }

    public synchronized void replaceAssignmentsForPreset(String presetId, Collection<String> targets) {
        if (TextUtils.isEmpty(presetId)) {
            return;
        }
        Set<String> desired = new HashSet<>(targets);
        JSONObject assignments = readObject(KEY_ASSIGNMENTS);
        Iterator<String> keys = assignments.keys();
        List<String> remove = new ArrayList<>();
        while (keys.hasNext()) {
            String key = keys.next();
            if (presetId.equals(assignments.optString(key)) && !desired.contains(key)) {
                remove.add(key);
            }
        }
        for (String key : remove) {
            assignments.remove(key);
        }
        for (String key : desired) {
            try {
                assignments.put(key, presetId);
            } catch (JSONException error) {
                Log.e(TAG, "No se pudo asignar " + key, error);
            }
        }
        writeObject(KEY_ASSIGNMENTS, assignments);
    }

    public synchronized String getTargetLabel(String targetKey) {
        if (TextUtils.isEmpty(targetKey)) {
            return "";
        }
        if (targetKey.startsWith("type:")) {
            DeviceCategory category = DeviceCategory.fromKey(targetKey.substring("type:".length()));
            return context.getString(category.labelRes);
        }
        if (targetKey.startsWith("bt:name:")) {
            return targetKey.substring("bt:name:".length());
        }
        if (targetKey.startsWith("bt:")) {
            String address = targetKey.substring("bt:".length());
            String name = readObject(KEY_BT_NAMES).optString(address, null);
            return TextUtils.isEmpty(name) ? context.getString(R.string.device_bluetooth) : name;
        }
        return targetKey;
    }

    public synchronized void rememberBluetoothDevice(String targetKey, String name) {
        if (TextUtils.isEmpty(targetKey) || !targetKey.startsWith("bt:") || TextUtils.isEmpty(name)) {
            return;
        }
        JSONObject names = readObject(KEY_BT_NAMES);
        try {
            names.put(targetKey.substring("bt:".length()), name);
            writeObject(KEY_BT_NAMES, names);
        } catch (JSONException error) {
            Log.e(TAG, "No se pudo guardar el nombre Bluetooth", error);
        }
    }

    // ---------------------------------------------------------- app overrides

    public synchronized void setAppOverride(String packageName, String value) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        JSONObject overrides = readObject(KEY_APP_OVERRIDES);
        try {
            if (TextUtils.isEmpty(value)) {
                overrides.remove(packageName);
            } else {
                overrides.put(packageName, value);
            }
            writeObject(KEY_APP_OVERRIDES, overrides);
        } catch (JSONException error) {
            Log.e(TAG, "No se pudo guardar el perfil de la aplicación", error);
        }
    }

    public synchronized String getAppOverride(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        String value = readObject(KEY_APP_OVERRIDES).optString(packageName, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    // --------------------------------------------------------------- resolution

    /**
     * Resolves the preset for a route and (optionally) the foreground app.
     * Priority: explicit app profile, specific Bluetooth device, category,
     * any-device fallback. A null result means the service must not process.
     */
    public synchronized Preset resolvePreset(
            AudioRoute route,
            String foregroundPackage,
            boolean perAppEnabled
    ) {
        if (perAppEnabled && !TextUtils.isEmpty(foregroundPackage)) {
            String override = readObject(KEY_APP_OVERRIDES).optString(foregroundPackage, null);
            if (APP_NONE.equals(override)) {
                return null;
            }
            if (!TextUtils.isEmpty(override)) {
                Preset appPreset = getPreset(override);
                if (appPreset != null) {
                    return appPreset;
                }
            }
        }
        if (route == null) {
            return null;
        }
        if (route.isBluetooth()) {
            String specificKey = route.targetKey();
            if (!specificKey.equals(route.categoryKey())) {
                String specificId = getAssignedPresetId(specificKey);
                if (!TextUtils.isEmpty(specificId)) {
                    Preset specific = getPreset(specificId);
                    if (specific != null) {
                        return specific;
                    }
                }
            }
            String bluetoothId = getAssignedPresetId(route.categoryKey());
            if (!TextUtils.isEmpty(bluetoothId)) {
                Preset bluetooth = getPreset(bluetoothId);
                if (bluetooth != null) {
                    return bluetooth;
                }
            }
        } else {
            String categoryId = getAssignedPresetId(route.categoryKey());
            if (!TextUtils.isEmpty(categoryId)) {
                Preset category = getPreset(categoryId);
                if (category != null) {
                    return category;
                }
            }
        }
        String allId = getAssignedPresetId(DeviceCategory.ALL.targetKey());
        if (!TextUtils.isEmpty(allId)) {
            return getPreset(allId);
        }
        return null;
    }

    public synchronized Preset resolvePresetForPackage(String packageName, AudioRoute route) {
        if (isPerAppEnabled() && !TextUtils.isEmpty(packageName)) {
            String override = getAppOverride(packageName);
            if (APP_NONE.equals(override)) {
                return null;
            }
            if (!TextUtils.isEmpty(override)) {
                Preset preset = getPreset(override);
                if (preset != null) {
                    return preset;
                }
            }
        }
        return resolvePreset(route, null, false);
    }

    // ---------------------------------------------------------------- settings

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean isAutoUpdateEnabled() {
        return preferences.getBoolean(KEY_AUTO_UPDATE, true);
    }

    public void setAutoUpdateEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply();
    }

    public boolean isPerAppEnabled() {
        return preferences.getBoolean(KEY_PER_APP_ENABLED, false);
    }

    public void setPerAppEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PER_APP_ENABLED, enabled).apply();
    }

    public String getThemeMode() {
        return preferences.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setThemeMode(String mode) {
        preferences.edit().putString(KEY_THEME, mode).apply();
    }

    public long getLastUpdateCheck() {
        return preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L);
    }

    public void setLastUpdateCheck(long timestamp) {
        preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, timestamp).apply();
    }

    public String getForegroundPackage() {
        return preferences.getString(KEY_FOREGROUND_PACKAGE, "");
    }

    public void setForegroundPackage(String packageName) {
        String current = getForegroundPackage();
        String next = packageName == null ? "" : packageName;
        if (!current.equals(next)) {
            preferences.edit().putString(KEY_FOREGROUND_PACKAGE, next).apply();
        }
    }

    // ------------------------------------------------------------------ status

    public void updateServiceStatus(
            boolean running,
            Preset preset,
            AudioRoute route,
            String engineLabel,
            String error
    ) {
        preferences.edit()
                .putBoolean(KEY_STATUS_RUNNING, running)
                .putString(KEY_STATUS_PRESET_ID, preset == null ? "" : preset.id)
                .putString(KEY_STATUS_PRESET_NAME, preset == null ? "" : preset.name)
                .putString(KEY_STATUS_DEVICE, route == null ? "" : route.displayName())
                .putString(KEY_STATUS_ENGINE, engineLabel == null ? "" : engineLabel)
                .putString(KEY_STATUS_ERROR, error == null ? "" : error)
                .apply();
    }

    public void clearServiceStatus() {
        updateServiceStatus(false, null, null, "", "");
    }

    public boolean isServiceRunning() {
        return preferences.getBoolean(KEY_STATUS_RUNNING, false);
    }

    public String getActivePresetId() {
        return preferences.getString(KEY_STATUS_PRESET_ID, "");
    }

    public String getActivePresetName() {
        return preferences.getString(KEY_STATUS_PRESET_NAME, "");
    }

    public String getActiveDeviceName() {
        return preferences.getString(KEY_STATUS_DEVICE, "");
    }

    public String getActiveEngineLabel() {
        return preferences.getString(KEY_STATUS_ENGINE, "");
    }

    public String getActiveError() {
        return preferences.getString(KEY_STATUS_ERROR, "");
    }

    // ------------------------------------------------------------------- reset

    public synchronized void resetAll() {
        preferences.edit().clear().commit();
    }

    private JSONObject readObject(String key) {
        String raw = preferences.getString(key, "{}");
        try {
            return new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
        } catch (JSONException error) {
            Log.e(TAG, "JSON inválido en " + key, error);
            return new JSONObject();
        }
    }

    private void writeObject(String key, JSONObject object) {
        preferences.edit().putString(key, object.toString()).apply();
    }
}
