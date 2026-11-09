package com.angelonmx.megaequalizer.util;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.accessibility.MegaAccessibilityService;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Small helpers for permission status and system settings screens. */
public final class AccessibilityUtils {

    private AccessibilityUtils() {
    }

    public static boolean isServiceEnabled(Context context) {
        try {
            AccessibilityManager manager =
                    (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager != null) {
                List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                );
                for (AccessibilityServiceInfo info : services) {
                    if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                        if (context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                                && info.getResolveInfo().serviceInfo.name != null
                                && info.getResolveInfo().serviceInfo.name.contains(
                                MegaAccessibilityService.class.getSimpleName())) {
                            return true;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }

        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        ComponentName component = new ComponentName(context, MegaAccessibilityService.class);
        String shortComponent = context.getPackageName() + "/." + MegaAccessibilityService.class.getSimpleName();
        String fullComponent = component.flattenToString();
        String fullShort = context.getPackageName() + "/" + MegaAccessibilityService.class.getName();
        String[] entries = enabled.split(":");
        for (String entry : entries) {
            if (entry.equalsIgnoreCase(fullComponent)
                    || entry.equalsIgnoreCase(fullShort)
                    || entry.equalsIgnoreCase(shortComponent)) {
                return true;
            }
        }
        return false;
    }

    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean areNotificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                    && NotificationManagerCompat.from(context).areNotificationsEnabled();
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static void openNotificationSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException error) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + context.getPackageName()));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
    }

    public static void openBatterySettings(Context context) {
        try {
            Intent request = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName())
            );
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(request);
        } catch (RuntimeException error) {
            Intent settings = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
        }
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static String formatDateTime(Context context, long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return format.format(new Date(timestamp));
    }

    public static void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean canRequestPackageInstalls(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public static void openInstallPermissionSettings(Activity activity) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(intent);
        } catch (RuntimeException error) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
