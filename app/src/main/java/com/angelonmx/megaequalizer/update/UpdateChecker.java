package com.angelonmx.megaequalizer.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks GitHub Releases once on launch and guides the user through install. */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    public static final String VERSION_URL = "https://github.com/"
            + BuildConfig.UPDATE_OWNER + "/" + BuildConfig.UPDATE_REPO
            + "/releases/latest/download/version.json";

    public static final String PENDING_PREFS = "mega_update_pending";
    public static final String KEY_DOWNLOAD_ID = "download_id";
    public static final String KEY_FILE = "file";
    public static final String KEY_VERSION = "version";
    public static final String KEY_VERSION_CODE = "version_code";
    public static final String KEY_SHA = "sha256";
    public static final String KEY_READY_FILE = "ready_file";
    public static final String KEY_READY_VERSION = "ready_version";
    public static final String KEY_READY_SHA = "ready_sha";
    public static final String KEY_READY_PROMPTED = "ready_prompted";

    public static final String EXTRA_INSTALL_FILE = "install_file";
    public static final String EXTRA_INSTALL_VERSION = "install_version";

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
    private static volatile boolean checkedOnLaunch;

    private UpdateChecker() {
    }

    public static void maybeCheckOnLaunch(Activity activity) {
        if (checkedOnLaunch) {
            return;
        }
        checkedOnLaunch = true;
        PresetRepository repository = PresetRepository.get(activity);
        if (!repository.isAutoUpdateEnabled()) {
            return;
        }
        MAIN.postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            check(activity, (update, error) -> {
                if (update != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    showUpdateDialog(activity, update);
                }
            });
        }, 1400L);
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
            MAIN.post(() -> callback.onResult(null, context.getString(R.string.updates_checking)));
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
        StringBuilder message = new StringBuilder(
                activity.getString(R.string.update_available_message, update.versionName)
        );
        if (!TextUtils.isEmpty(update.notes)) {
            message.append("\n\n")
                    .append(activity.getString(R.string.update_notes_heading))
                    .append("\n")
                    .append(update.notes.trim());
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setPositiveButton(R.string.download_and_install, (dialog, which) ->
                        download(activity, update))
                .setNegativeButton(R.string.later, null)
                .show();
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
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );
            long downloadId = manager.enqueue(request);
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
                    .remove(KEY_READY_FILE)
                    .remove(KEY_READY_VERSION)
                    .remove(KEY_READY_SHA)
                    .remove(KEY_READY_PROMPTED)
                    .apply();
            Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Log.e(TAG, "No se pudo iniciar la descarga", error);
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static UpdateInfo.Asset selectAsset(UpdateInfo update) {
        String abi = NativeAbi.get();
        String key;
        if (abi.contains("arm64")) {
            key = "arm64-v8a";
        } else if (abi.contains("armv7") || abi.contains("armeabi")) {
            key = "armeabi-v7a";
        } else {
            key = "universal";
        }
        UpdateInfo.Asset asset = update.assets.get(key);
        if (asset == null && !"universal".equals(key)) {
            asset = update.assets.get("universal");
        }
        if (asset == null) {
            asset = update.assets.values().stream().findFirst().orElse(null);
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
        if (TextUtils.isEmpty(file) || !downloadedFile(activity, file).isFile()) {
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
