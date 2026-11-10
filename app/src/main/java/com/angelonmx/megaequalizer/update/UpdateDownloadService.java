package com.angelonmx.megaequalizer.update;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.angelonmx.megaequalizer.MainActivity;
import com.angelonmx.megaequalizer.R;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;

/**
 * Foreground service that follows a DownloadManager download, publishes an
 * accessible progress notification and speaks the percentage through TalkBack.
 * When the APK is verified it stops itself and asks the UI to show the install
 * dialog.
 */
public class UpdateDownloadService extends Service {

    private static final String TAG = "UpdateDownloadService";
    public static final String ACTION_START =
            "com.angelonmx.megaequalizer.action.DOWNLOAD_PROGRESS_START";
    public static final String ACTION_CANCEL =
            "com.angelonmx.megaequalizer.action.DOWNLOAD_PROGRESS_CANCEL";
    public static final String EXTRA_DOWNLOAD_ID = "download_id";
    public static final String EXTRA_VERSION = "version";

    private static final String CHANNEL_ID = "mega_equalizer_download_progress";
    private static final int NOTIFICATION_ID = 2003;
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long ANNOUNCE_INTERVAL_MS = 20000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DownloadManager downloadManager;
    private long downloadId = -1L;
    private String version = "";
    private int lastPercent = 0;
    private long lastAnnounceAt;
    private boolean finished;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (finished) {
                return;
            }
            queryDownload();
            if (!finished) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelDownload();
            return START_NOT_STICKY;
        }
        long requestedId = intent == null
                ? -1L
                : intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L);
        String requestedVersion = intent == null
                ? ""
                : intent.getStringExtra(EXTRA_VERSION);
        SharedPreferences preferences = UpdateChecker.pending(this);
        if (requestedId < 0) {
            requestedId = preferences.getLong(UpdateChecker.KEY_DOWNLOAD_ID, -1L);
        }
        if (TextUtils.isEmpty(requestedVersion)) {
            requestedVersion = preferences.getString(UpdateChecker.KEY_VERSION, "");
        }
        if (requestedId < 0 || downloadManager == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        downloadId = requestedId;
        version = TextUtils.isEmpty(requestedVersion)
                ? getString(R.string.app_name)
                : requestedVersion;
        finished = false;
        lastPercent = 0;
        lastAnnounceAt = SystemClock.elapsedRealtime();

        startForegroundSafely(buildProgressNotification(0, 0, 0, true));
        AccessibilityUtils.announceToScreenReader(
                this,
                getString(R.string.update_download_started_announcement, version)
        );
        handler.removeCallbacks(pollRunnable);
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        finished = true;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void queryDownload() {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                finishWithoutCursor();
                return;
            }
            int status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            );
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                verifyCompletedDownload();
                return;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                AccessibilityUtils.announceToScreenReader(
                        this,
                        getString(R.string.update_download_failed)
                );
                finishWithFailure();
                return;
            }
            long downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            );
            long total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            );
            if (downloaded < 0) {
                downloaded = 0;
            }
            if (total < 0) {
                total = 0;
            }
            int percent = total > 0
                    ? (int) Math.min(100L, Math.max(0L, downloaded * 100L / total))
                    : -1;
            updateNotification(percent, downloaded, total);
            announceProgress(percent, downloaded, total);
        } catch (RuntimeException error) {
            Log.w(TAG, "No se pudo consultar el progreso de la descarga", error);
        }
    }

    private void updateNotification(int percent, long downloaded, long total) {
        Notification notification = buildProgressNotification(
                Math.max(0, percent),
                downloaded,
                total,
                percent < 0
        );
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException error) {
            Log.w(TAG, "No se pudo actualizar la notificación de progreso", error);
        }
    }

    private void announceProgress(int percent, long downloaded, long total) {
        long now = SystemClock.elapsedRealtime();
        boolean threshold = percent >= 0 && percent >= lastPercent + 10;
        boolean elapsed = now - lastAnnounceAt >= ANNOUNCE_INTERVAL_MS;
        if (!threshold && !elapsed) {
            return;
        }
        String text;
        if (percent >= 0 && total > 0) {
            text = getString(
                    R.string.update_download_progress_announcement,
                    version,
                    percent,
                    Formatter.formatShortFileSize(this, downloaded),
                    Formatter.formatShortFileSize(this, total)
            );
        } else {
            text = getString(
                    R.string.update_download_progress_announcement_nototal,
                    version,
                    Formatter.formatShortFileSize(this, downloaded)
            );
        }
        AccessibilityUtils.announceToScreenReader(this, text);
        if (percent >= 0) {
            lastPercent = percent;
        }
        lastAnnounceAt = now;
    }

    private void verifyCompletedDownload() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacks(pollRunnable);
        Notification verifying = buildVerifyingNotification();
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, verifying);
        } catch (RuntimeException ignored) {
        }
        new Thread(() -> {
            int result = UpdateDownloadReceiver.processCompletedDownload(
                    getApplicationContext(),
                    downloadId
            );
            handler.post(() -> {
                boolean ready = result == UpdateDownloadReceiver.RESULT_READY
                        || UpdateChecker.hasReadyUpdate(getApplicationContext());
                if (ready) {
                    AccessibilityUtils.announceToScreenReader(
                            getApplicationContext(),
                            getString(R.string.update_download_complete_announcement, version)
                    );
                    UpdateChecker.broadcastReady(getApplicationContext());
                } else {
                    AccessibilityUtils.announceToScreenReader(
                            getApplicationContext(),
                            getString(R.string.update_download_failed)
                    );
                }
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        }, "update-download-verify").start();
    }

    private void finishWithoutCursor() {
        finished = true;
        handler.removeCallbacks(pollRunnable);
        if (UpdateChecker.hasReadyUpdate(this)) {
            AccessibilityUtils.announceToScreenReader(
                    this,
                    getString(R.string.update_download_complete_announcement, version)
            );
            UpdateChecker.broadcastReady(this);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void finishWithFailure() {
        finished = true;
        handler.removeCallbacks(pollRunnable);
        new Thread(() -> {
            UpdateDownloadReceiver.processCompletedDownload(getApplicationContext(), downloadId);
            handler.post(() -> {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        }, "update-download-failure").start();
    }

    private void cancelDownload() {
        finished = true;
        handler.removeCallbacks(pollRunnable);
        SharedPreferences preferences = UpdateChecker.pending(this);
        long id = downloadId >= 0
                ? downloadId
                : preferences.getLong(UpdateChecker.KEY_DOWNLOAD_ID, -1L);
        if (id >= 0 && downloadManager != null) {
            try {
                downloadManager.remove(id);
            } catch (RuntimeException error) {
                Log.w(TAG, "No se pudo cancelar la descarga", error);
            }
        }
        preferences.edit()
                .remove(UpdateChecker.KEY_DOWNLOAD_ID)
                .remove(UpdateChecker.KEY_NOTES)
                .commit();
        AccessibilityUtils.announceToScreenReader(
                this,
                getString(R.string.update_download_cancelled)
        );
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification buildProgressNotification(
            int percent,
            long downloaded,
            long total,
            boolean indeterminate
    ) {
        String text;
        if (indeterminate || total <= 0) {
            text = downloaded > 0
                    ? getString(
                            R.string.update_download_progress_indeterminate,
                            Formatter.formatShortFileSize(this, downloaded)
                    )
                    : getString(R.string.update_download_progress_waiting);
        } else {
            text = getString(
                    R.string.update_download_progress_percent,
                    percent,
                    Formatter.formatShortFileSize(this, downloaded),
                    Formatter.formatShortFileSize(this, total)
            );
        }

        return buildNotification(text, percent, indeterminate);
    }

    private Notification buildVerifyingNotification() {
        return buildNotification(getString(R.string.update_verifying), 0, true);
    }

    private Notification buildNotification(String text, int percent, boolean indeterminate) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                21,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Intent cancelIntent = new Intent(this, UpdateDownloadService.class)
                .setAction(ACTION_CANCEL);
        PendingIntent cancelPending = PendingIntent.getService(
                this,
                22,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_equalizer_notification)
                .setContentTitle(getString(R.string.update_download_progress_title, version))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(100, Math.max(0, percent), indeterminate)
                .addAction(
                        R.drawable.ic_equalizer_notification,
                        getString(R.string.update_download_cancel_action),
                        cancelPending
                )
                .build();
    }

    private void startForegroundSafely(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudo mostrar el progreso de la descarga", error);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.update_download_progress_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(
                R.string.update_download_progress_channel_description
        ));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }
}
