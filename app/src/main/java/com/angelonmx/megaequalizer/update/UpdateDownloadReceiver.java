package com.angelonmx.megaequalizer.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.util.HashUtils;

import java.io.File;

/**
 * Handles DownloadManager completion, verifies the APK hash and package, and
 * publishes the "ready to install" notification. The same processing is also
 * used when the app starts and finds a completed download whose broadcast was
 * missed while the process was not running.
 */
public class UpdateDownloadReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateDownloadReceiver";

    public static final int RESULT_PENDING = 0;
    public static final int RESULT_READY = 1;
    public static final int RESULT_FAILED = 2;

    private static final Object LOCK = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            return;
        }
        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (downloadId < 0) {
            return;
        }
        try {
            processCompletedDownload(context.getApplicationContext(), downloadId);
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudo procesar la descarga completada", error);
        }
    }

    /**
     * Verifies a completed download. This method is intentionally idempotent:
     * both the broadcast and the on-launch reconciliation can call it.
     */
    public static int processCompletedDownload(Context context, long downloadId) {
        synchronized (LOCK) {
            SharedPreferences preferences = UpdateChecker.pending(context);
            long expectedId = preferences.getLong(UpdateChecker.KEY_DOWNLOAD_ID, -1L);
            if (expectedId < 0 || expectedId != downloadId) {
                return RESULT_PENDING;
            }

            DownloadManager manager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                fail(context, preferences, false);
                return RESULT_FAILED;
            }

            DownloadManager.Query query = new DownloadManager.Query()
                    .setFilterById(downloadId);
            try (Cursor cursor = manager.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return RESULT_PENDING;
                }
                int status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                );
                if (status == DownloadManager.STATUS_FAILED) {
                    fail(context, preferences, false);
                    return RESULT_FAILED;
                }
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    return RESULT_PENDING;
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "No se pudo consultar la descarga", error);
                return RESULT_PENDING;
            }

            String fileName = preferences.getString(UpdateChecker.KEY_FILE, "");
            String version = preferences.getString(UpdateChecker.KEY_VERSION, "");
            String sha256 = preferences.getString(UpdateChecker.KEY_SHA, "");
            String notes = preferences.getString(UpdateChecker.KEY_NOTES, "");
            int expectedVersionCode = preferences.getInt(UpdateChecker.KEY_VERSION_CODE, 0);
            if (TextUtils.isEmpty(fileName)) {
                fail(context, preferences, false);
                return RESULT_FAILED;
            }

            File file = UpdateChecker.downloadedFile(context, fileName);
            if (!file.isFile()) {
                Log.w(TAG, "El archivo descargado no existe: " + file);
                fail(context, preferences, false);
                return RESULT_FAILED;
            }

            if (!TextUtils.isEmpty(sha256) && !HashUtils.matches(file, sha256)) {
                Log.w(TAG, "La firma SHA-256 del APK descargado no coincide");
                file.delete();
                fail(context, preferences, true);
                return RESULT_FAILED;
            }

            try {
                PackageInfo archive = context.getPackageManager()
                        .getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (archive == null
                        || !context.getPackageName().equals(archive.packageName)) {
                    Log.w(TAG, "El APK descargado no pertenece a Mega Equalizer");
                    file.delete();
                    fail(context, preferences, true);
                    return RESULT_FAILED;
                }
                if (expectedVersionCode > 0 && archive.versionCode != expectedVersionCode) {
                    Log.w(TAG, "La versión del APK no coincide con version.json");
                    file.delete();
                    fail(context, preferences, true);
                    return RESULT_FAILED;
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "No se pudo inspeccionar el APK descargado", error);
                file.delete();
                fail(context, preferences, true);
                return RESULT_FAILED;
            }

            PresetRepository.get(context).setPendingWhatsNew(
                    expectedVersionCode,
                    version,
                    notes
            );
            preferences.edit()
                    .remove(UpdateChecker.KEY_DOWNLOAD_ID)
                    .remove(UpdateChecker.KEY_NOTES)
                    .putString(UpdateChecker.KEY_READY_FILE, fileName)
                    .putString(UpdateChecker.KEY_READY_VERSION, version)
                    .putInt(UpdateChecker.KEY_READY_VERSION_CODE, expectedVersionCode)
                    .putString(UpdateChecker.KEY_READY_SHA, sha256)
                    .putBoolean(UpdateChecker.KEY_READY_PROMPTED, false)
                    .commit();
            UpdateNotifications.showReady(context, fileName, version);
            UpdateChecker.broadcastReady(context);
            return RESULT_READY;
        }
    }

    private static void fail(Context context, SharedPreferences preferences, boolean verification) {
        preferences.edit()
                .remove(UpdateChecker.KEY_DOWNLOAD_ID)
                .remove(UpdateChecker.KEY_FILE)
                .remove(UpdateChecker.KEY_VERSION)
                .remove(UpdateChecker.KEY_SHA)
                .remove(UpdateChecker.KEY_VERSION_CODE)
                .remove(UpdateChecker.KEY_NOTES)
                .apply();
        if (verification) {
            UpdateNotifications.showVerificationFailed(context);
        } else {
            UpdateNotifications.showDownloadFailed(context);
        }
    }
}
