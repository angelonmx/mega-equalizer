package com.angelonmx.megaequalizer.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Optional service used exclusively to learn the foreground package name for
 * per-app audio profiles. It does not retrieve window content and never reads
 * on-screen text.
 */
public class MegaAccessibilityService extends AccessibilityService {

    private static final Set<String> IGNORED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings"
    ));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingPackage;
    private String lastAppliedPackage;
    private final Runnable applyForeground = () -> {
        if (TextUtils.isEmpty(pendingPackage)) {
            return;
        }
        if (pendingPackage.equals(lastAppliedPackage)) {
            return;
        }
        lastAppliedPackage = pendingPackage;
        PresetRepository.get(this).setForegroundPackage(pendingPackage);
        EqualizerService.requestRefresh(this);
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        String packageValue = packageName.toString();
        if (packageValue.equals(getPackageName()) || IGNORED_PACKAGES.contains(packageValue)) {
            return;
        }
        pendingPackage = packageValue;
        handler.removeCallbacks(applyForeground);
        handler.postDelayed(applyForeground, 350L);
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt: this service only observes window changes.
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(applyForeground);
        super.onDestroy();
        EqualizerService.requestRefresh(this);
    }
}
