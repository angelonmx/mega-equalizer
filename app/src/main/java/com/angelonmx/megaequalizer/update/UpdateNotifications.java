package com.angelonmx.megaequalizer.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.angelonmx.megaequalizer.R;

/** Builds the update notifications used by the download receiver. */
public final class UpdateNotifications {

    private static final String TAG = "UpdateNotifications";
    public static final String CHANNEL_ID = "mega_equalizer_updates";

    private static final int NOTIFICATION_READY = 2001;
    private static final int NOTIFICATION_ERROR = 2002;

    private UpdateNotifications() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.update_channel_description));
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    public static void showReady(Context context, String fileName, String version) {
        ensureChannel(context);
        Intent installIntent = new Intent(context, InstallActivity.class)
                .putExtra(UpdateChecker.EXTRA_INSTALL_FILE, fileName)
                .putExtra(UpdateChecker.EXTRA_INSTALL_VERSION, version)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent installPending = PendingIntent.getActivity(
                context,
                0,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_equalizer_notification)
                .setContentTitle(context.getString(R.string.update_install_notification_title))
                .setContentText(context.getString(
                        R.string.update_install_notification_text,
                        version
                ))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(
                        R.string.update_install_notification_text,
                        version
                )))
                .setContentIntent(installPending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                        R.drawable.ic_equalizer_notification,
                        context.getString(R.string.update_install_notification_action),
                        installPending
                )
                .build();
        notify(context, NOTIFICATION_READY, notification);
    }

    public static void showVerificationFailed(Context context) {
        ensureChannel(context);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_equalizer_notification)
                .setContentTitle(context.getString(R.string.update_verification_failed_title))
                .setContentText(context.getString(R.string.update_download_verification_failed))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.update_download_verification_failed)
                ))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notify(context, NOTIFICATION_ERROR, notification);
    }

    public static void showDownloadFailed(Context context) {
        ensureChannel(context);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_equalizer_notification)
                .setContentTitle(context.getString(R.string.update_download_failed_title))
                .setContentText(context.getString(R.string.update_download_failed))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.update_download_failed)
                ))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        notify(context, NOTIFICATION_ERROR, notification);
    }

    private static void notify(Context context, int id, Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException error) {
            Log.w(TAG, "No se pudo mostrar la notificación de actualización", error);
        } catch (RuntimeException error) {
            Log.w(TAG, "Error al mostrar la notificación de actualización", error);
        }
    }
}
