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
 * Optional service used to learn the foreground package name for per-app audio
 * profiles and to keep the global audio engine alive. It does not retrieve
 * window content and never reads on-screen text.
 *
 * <p>The global audio effect is attached to the output mix (session 0), so it
 * covers every audio stream, including the accessibility stream used by
 * TalkBack, whenever the device allows global effects.</p>
 */
public class MegaAccessibilityService extends AccessibilityService {

    private static final Set<String> IGNORED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings"
    ));
    private static final long WATCHDOG_INTERVAL_MS = 15000L;

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
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (PresetRepository.get(MegaAccessibilityService.this).isEnabled()) {
                if (EqualizerService.isRunning()) {
                    EqualizerService.requestRefresh(MegaAccessibilityService.this);
                } else {
                    EqualizerService.start(MegaAccessibilityService.this);
                }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Keep the system-wide engine alive and re-evaluate the current route
        // when the user enables the service. The engine processes the complete
        // output mix, including the accessibility stream, when supported.
        EqualizerService.requestRefresh(this);
        scheduleWatchdog();
    }

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
        // Nothing to interrupt: this service only observes window changes and
        // keeps the audio engine alive.
        EqualizerService.requestRefresh(this);
    }

    private void scheduleWatchdog() {
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(applyForeground);
        handler.removeCallbacks(watchdog);
        super.onDestroy();
        EqualizerService.requestRefresh(this);
    }
}
