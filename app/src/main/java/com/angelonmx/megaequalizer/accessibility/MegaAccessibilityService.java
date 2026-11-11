package com.angelonmx.megaequalizer.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Optional accessibility service used to learn the foreground package name for
 * per-app audio profiles, to keep the global audio engine alive, and to help
 * the user grant the Shizuku permission by clicking the grant dialog buttons
 * when they appear.
 *
 * <p>This service does not retrieve window content and never reads on-screen
 * text except package names and button labels needed to click grant buttons.</p>
 */
public class MegaAccessibilityService extends AccessibilityService {

    private static final Set<String> IGNORED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings"
    ));
    private static final long WATCHDOG_INTERVAL_MS = 15000L;
    private static final long CLICK_DELAY_MS = 800L;

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
        EqualizerService.requestRefresh(this);
        scheduleWatchdog();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        // Detect window changes for per-app profiles.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence packageName = event.getPackageName();
            if (!TextUtils.isEmpty(packageName)) {
                String packageValue = packageName.toString();
                if (!packageValue.equals(getPackageName())
                        && !IGNORED_PACKAGES.contains(packageValue)) {
                    pendingPackage = packageValue;
                    handler.removeCallbacks(applyForeground);
                    handler.postDelayed(applyForeground, 350L);
                }
            }
        }

        // Help the user grant the Shizuku permission by clicking grant buttons
        // when Shizuku's permission dialog is on screen.
        if (isShizukuPermissionDialog(event)) {
            handler.postDelayed(() -> clickGrantButtons(event), CLICK_DELAY_MS);
        }
    }

    private boolean isShizukuPermissionDialog(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        return pkg != null
                && (pkg.toString().equals("moe.shizuku.privileged.api")
                || pkg.toString().equals("moe.shizuku.privileged.api"))
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
    }

    private void clickGrantButtons(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        try {
            clickButtonContaining(root, "Permitir");
            clickButtonContaining(root, "Allow");
            clickButtonContaining(root, "Aceptar");
            clickButtonContaining(root, "OK");
            clickButtonContaining(root, "Conceder");
        } finally {
            root.recycle();
        }
    }

    private void clickButtonContaining(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return;
        }
        if (text != null && node.getText() != null
                && node.getText().toString().contains(text)) {
            if (node.isClickable() || node.isEnabled()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                clickButtonContaining(child, text);
            }
        }
    }

    @Override
    public void onInterrupt() {
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
