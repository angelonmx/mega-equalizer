package com.angelonmx.megaequalizer.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.BuildConfig;
import com.angelonmx.megaequalizer.R;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.util.NativeAbi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks GitHub Releases whenever the UI opens and guides the user through install. */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    /** Error returned by {@link #check} when another check is already running. */
    public static final String ERROR_CHECKING = "checking";
    public static final String VERSION_URL = "https://github.com/"
            + BuildConfig.UPDATE_OWNER + "/" + BuildConfig.UPDATE_REPO
            + "/releases/latest/download/version.json";

    public static final String PENDING_PREFS = "mega_update_pending";
    public static final String KEY_DOWNLOAD_ID = "download_id";
    public static final String KEY_FILE = "file";
    public static final String KEY_VERSION = "version";
    public static final String KEY_VERSION_CODE = "version_code";
    public static final String KEY_SHA = "sha256";
    public static final String KEY_NOTES = "notes";
    public static final String KEY_READY_FILE = "ready_file";
    public static final String KEY_READY_VERSION = "ready_version";
    public static final String KEY_READY_VERSION_CODE = "ready_version_code";
    public static final String KEY_READY_SHA = "ready_sha";
    public static final String KEY_READY_PROMPTED = "ready_prompted";

    public static final String EXTRA_INSTALL_FILE = "install_file";
    public static final String EXTRA_INSTALL_VERSION = "install_version";
    public static final String ACTION_UPDATE_READY =
            "com.angelonmx.megaequalizer.action.UPDATE_READY";

    public interface UpdateCallback {
        /**
         * @param update non-null when a newer version exists; null and no error
         *               means the installed app is up to date.
         */
        void onResult(@Nullable UpdateInfo update, @Nullable String error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean RECONCILED = new AtomicBoolean(false);
    private static volatile int lastAutoPromptedVersionCode;
    private static WeakReference<Activity> dialogActivity = new WeakReference<>(null);

    private UpdateChecker() {
    }

    /**
     * Checks for updates every time a visible screen opens. This is deliberately
     * independent from the background service lifecycle: the service can stay
     * alive for days without being restarted, so its start/stop events are not
     * a reliable moment to look for releases.
     */
    public static void maybeCheckOnLaunch(Activity activity) {
        maybeCheckOnLaunch(activity, null);
    }

    /**
     * Same as {@link #maybeCheckOnLaunch(Activity)} but reports the result to
     * the caller, which is useful for the updates section in Settings.
     */
    public static void maybeCheckOnLaunch(
            Activity activity,
            @Nullable UpdateCallback callback
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        PresetRepository repository = PresetRepository.get(activity);
        if (!repository.isAutoUpdateEnabled()) {
            return;
        }
        if (hasReadyUpdate(activity)) {
            promptReadyUpdateIfNeeded(activity);
            if (callback != null) {
                callback.onResult(null, null);
            }
            return;
        }
        MAIN.postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            check(activity, (update, error) -> {
                if (callback != null) {
                    callback.onResult(update, error);
                }
                if (update == null || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                Activity current = dialogActivity.get();
                if (current != null && !current.isFinishing() && !current.isDestroyed()) {
                    return;
                }
                if (update.versionCode == lastAutoPromptedVersionCode) {
                    return;
                }
                lastAutoPromptedVersionCode = update.versionCode;
                showUpdateDialog(activity, update);
            });
        }, 900L);
    }

    /**
     * Handles the case where DownloadManager finished while the app process was
     * dead and the manifest receiver broadcast was missed. It is safe to call
     * multiple times; the receiver processing is idempotent.
     */
    public static void reconcilePendingDownload(Activity activity) {
        if (!RECONCILED.compareAndSet(false, true)) {
            return;
        }
        SharedPreferences preferences = pending(activity);
        long downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L);
        if (downloadId < 0) {
            return;
        }
        Context appContext = activity.getApplicationContext();
        EXECUTOR.execute(() -> {
            int result = UpdateDownloadReceiver.processCompletedDownload(appContext, downloadId);
            if (result == UpdateDownloadReceiver.RESULT_READY) {
                MAIN.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        promptReadyUpdateIfNeeded(activity);
                    }
                });
            } else if (result == UpdateDownloadReceiver.RESULT_FAILED) {
                MAIN.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        Toast.makeText(
                                activity,
                                R.string.update_download_failed,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            }
        });
    }

    public static void check(Context context, UpdateCallback callback) {
        if (!CHECKING.compareAndSet(false, true)) {
            MAIN.post(() -> callback.onResult(null, ERROR_CHECKING));
            return;
        }
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            UpdateInfo info = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(VERSION_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "MegaEqualizer/" + BuildConfig.VERSION_NAME);
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    StringBuilder body = new StringBuilder();
                    try (InputStream stream = connection.getInputStream();
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (body.length() < 256 * 1024) {
                                body.append(line).append('\n');
                            }
                        }
                    }
                    JSONObject json = new JSONObject(body.toString());
                    UpdateInfo remote = UpdateInfo.fromJson(json);
                    if (remote.versionCode > BuildConfig.VERSION_CODE) {
                        info = remote;
                    }
                } else {
                    error = "HTTP " + code;
                }
            } catch (Exception exception) {
                Log.w(TAG, "No se pudo comprobar la actualización", exception);
                error = exception.getClass().getSimpleName();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                PresetRepository.get(appContext).setLastUpdateCheck(System.currentTimeMillis());
                CHECKING.set(false);
            }
            UpdateInfo result = info;
            String failure = error;
            MAIN.post(() -> callback.onResult(result, failure));
        });
    }

    public static void showUpdateDialog(Activity activity, UpdateInfo update) {
        String deviceAbis = Build.SUPPORTED_ABIS.length == 0
                ? activity.getString(R.string.unknown)
                : TextUtils.join(", ", Build.SUPPORTED_ABIS);
        String installedAbi = NativeAbi.get();
        UpdateInfo.Asset asset = selectAsset(update);
        String downloadAbi = asset == null
                ? activity.getString(R.string.not_available)
                : selectedAbi(update) + " (" + asset.name + ")";

        StringBuilder message = new StringBuilder(
                activity.getString(
                        R.string.update_available_message,
                        update.versionName,
                        update.versionCode
                )
        );
        message.append("\n\n")
                .append(activity.getString(R.string.update_architecture_device_format, deviceAbis))
                .append("\n")
                .append(activity.getString(R.string.update_architecture_installed_format, installedAbi))
                .append("\n")
                .append(activity.getString(R.string.update_architecture_download_format, downloadAbi));
        if (!TextUtils.isEmpty(update.notes)) {
            message.append("\n\n")
                    .append(activity.getString(
                            R.string.update_notes_heading_format,
                            update.versionName
                    ))
                    .append("\n")
                    .append(plainNotes(update.notes));
        }
        if (asset == null) {
            message.append("\n\n").append(activity.getString(R.string.update_asset_missing));
        } else {
            message.append("\n\n").append(activity.getString(R.string.update_install_dialog_note));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message);
        if (asset != null) {
            builder.setPositiveButton(R.string.download_and_install, (dialog, which) ->
                            download(activity, update))
                    .setNegativeButton(R.string.later, null);
        } else {
            builder.setPositiveButton(R.string.close, null);
        }
        builder.setOnDismissListener(dialog -> {
            Activity current = dialogActivity.get();
            if (current == activity) {
                dialogActivity = new WeakReference<>(null);
            }
        });
        dialogActivity = new WeakReference<>(activity);
        builder.show();
    }

    public static void download(Activity activity, UpdateInfo update) {
        UpdateInfo.Asset asset = selectAsset(update);
        if (asset == null || TextUtils.isEmpty(asset.url)) {
            Toast.makeText(activity, R.string.update_asset_missing, Toast.LENGTH_LONG).show();
            return;
        }
        String fileName = sanitizeFileName(asset.name);
        if (TextUtils.isEmpty(fileName)) {
            fileName = "MegaEqualizer-" + sanitizeFileName(update.versionName) + ".apk";
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            fileName = fileName + ".apk";
        }
        try {
            DownloadManager manager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            File target = downloadedFile(activity, fileName);
            if (target.isFile()) {
                // DownloadManager cannot replace an existing destination file.
                target.delete();
            }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(asset.url));
            request.setTitle(activity.getString(
                    R.string.update_download_notification_title,
                    update.versionName
            ));
            request.setDescription(activity.getString(R.string.update_download_notification_text));
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            // The foreground download service publishes its own accessible
            // progress notification, so DownloadManager does not duplicate it.
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            request.setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );
            long downloadId;
            try {
                downloadId = manager.enqueue(request);
            } catch (SecurityException hiddenError) {
                // VISIBILITY_HIDDEN requiere un permiso especial en algunas
                // versiones de Android; se vuelve a intentar con notificación visible.
                Log.w(TAG, "Descarga oculta bloqueada, se usa visibilidad visible", hiddenError);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                downloadId = manager.enqueue(request);
            }
            if (downloadId < 0) {
                Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            SharedPreferences preferences = pending(activity);
            preferences.edit()
                    .putLong(KEY_DOWNLOAD_ID, downloadId)
                    .putString(KEY_FILE, fileName)
                    .putString(KEY_VERSION, update.versionName)
                    .putInt(KEY_VERSION_CODE, update.versionCode)
                    .putString(KEY_SHA, asset.sha256 == null ? "" : asset.sha256)
                    .putString(KEY_NOTES, plainNotes(update.notes))
                    .remove(KEY_READY_FILE)
                    .remove(KEY_READY_VERSION)
                    .remove(KEY_READY_VERSION_CODE)
                    .remove(KEY_READY_SHA)
                    .remove(KEY_READY_PROMPTED)
                    .commit();
            Intent progress = new Intent(activity, UpdateDownloadService.class)
                    .setAction(UpdateDownloadService.ACTION_START)
                    .putExtra(UpdateDownloadService.EXTRA_DOWNLOAD_ID, downloadId)
                    .putExtra(UpdateDownloadService.EXTRA_VERSION, update.versionName);
            try {
                ContextCompat.startForegroundService(activity, progress);
            } catch (RuntimeException serviceError) {
                Log.w(TAG, "No se pudo iniciar el progreso accesible de la descarga", serviceError);
            }
            Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Log.e(TAG, "No se pudo iniciar la descarga", error);
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Returns the architecture key that will actually be downloaded. */
    public static String selectedAbi(UpdateInfo update) {
        String abi = NativeAbi.get();
        String key;
        if (abi.contains("arm64")) {
            key = "arm64-v8a";
        } else if (abi.contains("armv7") || abi.contains("armeabi")) {
            key = "armeabi-v7a";
        } else {
            key = "universal";
        }
        if (update.assets.containsKey(key)) {
            return key;
        }
        if (update.assets.containsKey("universal")) {
            return "universal";
        }
        for (String candidate : update.assets.keySet()) {
            return candidate;
        }
        return key;
    }

    public static UpdateInfo.Asset selectAsset(UpdateInfo update) {
        UpdateInfo.Asset asset = update.assets.get(selectedAbi(update));
        if (asset == null && !update.assets.isEmpty()) {
            asset = update.assets.values().iterator().next();
        }
        return asset;
    }

    public static SharedPreferences pending(Context context) {
        return context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE);
    }

    public static File downloadedFile(Context context, String fileName) {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) {
            directory = new File(context.getFilesDir(), "Download");
        }
        return new File(directory, fileName);
    }

    public static boolean hasReadyUpdate(Context context) {
        SharedPreferences preferences = pending(context);
        String file = preferences.getString(KEY_READY_FILE, "");
        return !TextUtils.isEmpty(file) && downloadedFile(context, file).isFile();
    }

    public static void promptReadyUpdateIfNeeded(Activity activity) {
        SharedPreferences preferences = pending(activity);
        String file = preferences.getString(KEY_READY_FILE, "");
        String version = preferences.getString(KEY_READY_VERSION, "");
        if (TextUtils.isEmpty(file)) {
            return;
        }
        int current = BuildConfig.VERSION_CODE;
        int readyCode = preferences.getInt(KEY_READY_VERSION_CODE, 0);
        boolean alreadyInstalled = readyCode > 0 && readyCode <= current;
        int lastRun = PresetRepository.get(activity).getLastRunVersionCode();
        boolean justUpdated = lastRun != 0 && lastRun != current;
        // A legacy ready file (without a version code) is stale when the app has
        // just been updated. Never offer to install an older or the same APK.
        if (alreadyInstalled
                || (readyCode == 0
                && (version.equals(BuildConfig.VERSION_NAME) || justUpdated))) {
            clearReadyUpdate(activity);
            return;
        }
        if (!downloadedFile(activity, file).isFile()) {
            clearReadyUpdate(activity);
            return;
        }
        if (preferences.getBoolean(KEY_READY_PROMPTED, false)) {
            return;
        }
        preferences.edit().putBoolean(KEY_READY_PROMPTED, true).apply();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_download_complete_title)
                .setMessage(activity.getString(
                        R.string.update_download_complete_text,
                        version
                ))
                .setPositiveButton(R.string.update_install_ready, (dialog, which) ->
                        startInstaller(activity, file, version))
                .setNegativeButton(R.string.later, null)
                .show();
    }

    /** Removes a stale ready-to-install state and its downloaded APK. */
    public static void clearReadyUpdate(Context context) {
        SharedPreferences preferences = pending(context);
        String file = preferences.getString(KEY_READY_FILE, "");
        if (!TextUtils.isEmpty(file)) {
            downloadedFile(context, file).delete();
        }
        preferences.edit()
                .remove(KEY_READY_FILE)
                .remove(KEY_READY_VERSION)
                .remove(KEY_READY_VERSION_CODE)
                .remove(KEY_READY_SHA)
                .remove(KEY_READY_PROMPTED)
                .commit();
    }

    public static void promptReadyUpdate(Activity activity) {
        SharedPreferences preferences = pending(activity);
        String file = preferences.getString(KEY_READY_FILE, "");
        String version = preferences.getString(KEY_READY_VERSION, "");
        if (TextUtils.isEmpty(file) || !downloadedFile(activity, file).isFile()) {
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show();
            return;
        }
        startInstaller(activity, file, version);
    }

    public static void startInstaller(Activity activity, String fileName, String version) {
        Intent intent = new Intent(activity, InstallActivity.class)
                .putExtra(EXTRA_INSTALL_FILE, fileName)
                .putExtra(EXTRA_INSTALL_VERSION, version);
        activity.startActivity(intent);
    }

    /**
     * Shows the release notes of the version that was just installed. This is
     * strictly a first-launch event: the pending notes are cleared afterwards
     * and normal launches never show them again.
     */
    public static void showWhatsNewIfNeeded(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        PresetRepository repository = PresetRepository.get(activity);
        int current = BuildConfig.VERSION_CODE;
        int lastRun = repository.getLastRunVersionCode();
        int pendingCode = repository.getPendingWhatsNewCode();
        String pendingName = repository.getPendingWhatsNewName();
        String pendingNotes = repository.getPendingWhatsNewNotes();

        if (lastRun == 0) {
            // The "last run" marker did not exist before 1.2. Distinguish a
            // real update from a fresh installation with the package manager.
            String notesToShow = null;
            String versionName = BuildConfig.VERSION_NAME;
            if (pendingCode == current && !TextUtils.isEmpty(pendingNotes)) {
                notesToShow = pendingNotes;
                if (!TextUtils.isEmpty(pendingName)) {
                    versionName = pendingName;
                }
            } else if (wasUpdated(activity)) {
                notesToShow = readBundledWhatsNew(activity);
            }
            repository.clearPendingWhatsNew();
            repository.setLastRunVersionCode(current);
            if (!TextUtils.isEmpty(notesToShow)) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(R.string.whats_new_title, versionName))
                        .setMessage(notesToShow)
                        .setPositiveButton(R.string.whats_new_close, null)
                        .show();
            }
            return;
        }
        if (lastRun == current) {
            if (pendingCode == current) {
                repository.clearPendingWhatsNew();
            }
            return;
        }
        if (lastRun > current) {
            // Reinstalling an older version must not pop newer notes.
            repository.clearPendingWhatsNew();
            repository.setLastRunVersionCode(current);
            return;
        }

        String notesToShow = null;
        String versionName = BuildConfig.VERSION_NAME;
        if (pendingCode == current && !TextUtils.isEmpty(pendingNotes)) {
            notesToShow = pendingNotes;
            if (!TextUtils.isEmpty(pendingName)) {
                versionName = pendingName;
            }
        } else {
            notesToShow = readBundledWhatsNew(activity);
        }
        if (!TextUtils.isEmpty(notesToShow)) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.whats_new_title, versionName))
                    .setMessage(notesToShow)
                    .setPositiveButton(R.string.whats_new_close, null)
                    .show();
        }
        repository.clearPendingWhatsNew();
        repository.setLastRunVersionCode(current);
    }

    /** True when the package was installed over a previous version. */
    private static boolean wasUpdated(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime - info.firstInstallTime > 10_000L;
        } catch (Exception error) {
            Log.w(TAG, "No se pudo consultar la fecha de instalación", error);
            return false;
        }
    }

    /** Release notes bundled with the APK, used when no download metadata exists. */
    private static String readBundledWhatsNew(Context context) {
        try (InputStream stream = context.getAssets().open("whats_new.txt");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
            return result.toString().trim();
        } catch (Exception error) {
            Log.w(TAG, "No se pudieron leer las novedades incluidas", error);
            return "";
        }
    }

    public static void broadcastReady(Context context) {
        try {
            Intent ready = new Intent(ACTION_UPDATE_READY);
            ready.setPackage(context.getPackageName());
            context.sendBroadcast(ready);
        } catch (RuntimeException error) {
            Log.w(TAG, "No se pudo avisar que la actualización está lista", error);
        }
    }

    /** Converts the release-notes markdown into readable plain text. */
    public static String plainNotes(String notes) {
        if (TextUtils.isEmpty(notes)) {
            return "";
        }
        String normalized = notes.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder();
        for (String rawLine : normalized.split("\n", -1)) {
            String line = rawLine.trim();
            if (line.matches("^\\|?[\\s:|-]+\\|?$") && line.contains("-")) {
                continue;
            }
            if (line.matches("^[-*_]{3,}$")) {
                continue;
            }
            if (line.startsWith("|") && line.endsWith("|") && line.length() > 2) {
                String[] cells = line.substring(1, line.length() - 1).split("\\|");
                StringBuilder row = new StringBuilder();
                for (String cell : cells) {
                    String value = cell.trim();
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (row.length() > 0) {
                        row.append(" · ");
                    }
                    row.append(value);
                }
                line = row.toString();
            }
            line = line.replaceFirst("^#{1,6}\\s*", "");
            line = line.replace("**", "").replace("__", "").replace("`", "");
            line = line.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1 ($2)");
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = "• " + line.substring(2);
            }
            if (line.isEmpty()) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                result.append('\n');
            } else {
                result.append(line).append('\n');
            }
        }
        return result.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    public static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        String clean = name.replaceAll("[^A-Za-z0-9._-]", "_");
        clean = clean.replace("..", "_");
        return clean.trim();
    }

    public static String downloadUrlFor(String name) {
        try {
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            return "https://github.com/" + BuildConfig.UPDATE_OWNER + "/" + BuildConfig.UPDATE_REPO
                    + "/releases/latest/download/" + encoded;
        } catch (Exception error) {
            return "";
        }
    }
}
