package com.angelonmx.megaequalizer.coverage;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.angelonmx.megaequalizer.audio.AudioRouteManager;
import com.angelonmx.megaequalizer.audio.EqualizerEngine;
import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps supplemental per-session effects in sync with the sessions that are
 * actually playing. The global session-0 effect remains the primary path; the
 * controller only adds effects where the global mix is known to miss audio
 * (voice calls, accessibility/TalkBack, notifications, alarms) or where the
 * user chose the session-only fallback.
 *
 * <p>Supported backends: Shizuku (preferred) and Root. ADB is not used.</p>
 */
public final class CoverageController {

    private static final String TAG = "CoverageController";
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final long RETRY_AFTER_FAILURE_MS = 15000L;
    private static final long RETRY_AFTER_ROOT_FAILURE_MS = 60000L;
    private static final long STATUS_HEARTBEAT_MS = 15000L;

    private final Context context;
    private final PresetRepository repository;
    private final EqualizerEngine engine;
    private final RootSessionDiscovery rootDiscovery = new RootSessionDiscovery();
    private final Set<Integer> appliedSessions = new HashSet<>();
    private final Object wakeLock = new Object();

    private volatile boolean running;
    private volatile boolean refreshRequested;
    private Thread thread;
    private long nextConnectAttemptAt;
    private String lastStatus = "";
    private String lastDetail = "";
    private String lastSummary = "";
    private long lastStatusAt;

    public CoverageController(
            @NonNull Context context,
            @NonNull PresetRepository repository,
            @NonNull EqualizerEngine engine
    ) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.engine = engine;
    }

    public void start() {
        if (running) {
            requestRefresh();
            return;
        }
        running = true;
        thread = new Thread(this::loop, "coverage-poller");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        requestRefresh();
        if (thread != null) {
            try {
                thread.join(1500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        releaseAppliedSessions();
    }

    public void requestRefresh() {
        refreshRequested = true;
        synchronized (wakeLock) {
            wakeLock.notifyAll();
        }
    }

    private void loop() {
        while (running) {
            try {
                pollOnce();
            } catch (Throwable error) {
                Log.w(TAG, "La consulta de cobertura falló", error);
                publishStatus(
                        "Cobertura total activa · Error temporal",
                        error.getMessage() == null ? "" : error.getMessage(),
                        ""
                );
            }
            synchronized (wakeLock) {
                if (!running) {
                    break;
                }
                long wait = refreshRequested ? 0L : POLL_INTERVAL_MS;
                refreshRequested = false;
                try {
                    wakeLock.wait(wait);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void pollOnce() {
        if (!running) {
            return;
        }
        if (!repository.isEnabled() || !repository.isTotalCoverageEnabled()) {
            releaseAppliedSessions();
            if (!lastStatus.isEmpty()) {
                publishStatus("Cobertura total desactivada", "", "");
            }
            return;
        }
        engine.setGlobalAllowed(!repository.isSessionOnlyCoverage());

        DiscoveryResult discovery;
        try {
            discovery = discoverSessions();
        } catch (Throwable error) {
            handleDiscoveryError(error);
            return;
        }

        List<AudioSessionInfo> sessions = AudioSessionDumpParser.parse(
                discovery.dump,
                context.getPackageManager(),
                Process.myUid()
        );
        applySessions(sessions);
        publishStatus(
                "Cobertura total activa · " + discovery.backendLabel,
                buildDetail(sessions),
                buildSummary(sessions)
        );
    }

    @NonNull
    private DiscoveryResult discoverSessions() throws Exception {
        String backend = repository.getCoverageBackend();
        if (PresetRepository.COVERAGE_BACKEND_ROOT.equals(backend)) {
            return new DiscoveryResult(rootDiscovery.readDumpsysAudio(), "root");
        }
        if (PresetRepository.COVERAGE_BACKEND_SHIZUKU.equals(backend)) {
            return new DiscoveryResult(ShizukuBridge.readDumpsysAudio(), "Shizuku");
        }
        return discoverAutomatically();
    }

    /**
     * Automatic mode prefers Shizuku because it does not need ports or a
     * pairing code. If Shizuku is unavailable or fails, root is offered as a
     * fallback.
     */
    @NonNull
    private DiscoveryResult discoverAutomatically() throws Exception {
        if (ShizukuBridge.isAvailable() && ShizukuBridge.hasPermission()) {
            try {
                return new DiscoveryResult(ShizukuBridge.readDumpsysAudio(), "Shizuku");
            } catch (Throwable error) {
                Log.w(TAG, "Shizuku falló", error);
                throw error;
            }
        }
        throw new ConfigurationMissingException();
    }

    private void applySessions(@NonNull List<AudioSessionInfo> sessions) {
        boolean sessionOnly = repository.isSessionOnlyCoverage();
        boolean globalActive = engine.isGlobalActive();
        AudioRoute route = AudioRouteManager.getCurrentRoute(context);

        List<AudioSessionInfo> desired = new ArrayList<>();
        for (AudioSessionInfo session : sessions) {
            if (!session.isActive()) {
                continue;
            }
            if (sessionOnly || !globalActive) {
                desired.add(session);
            } else if (session.isCallUsage()
                    || session.isAccessibilityUsage()
                    || session.isNotificationUsage()
                    || session.isAlarmUsage()) {
                // Sesiones que el efecto global suele dejar fuera: llamadas,
                // lector de pantalla (TalkBack/accesibilidad), notificaciones y alarmas.
                desired.add(session);
            }
        }

        Set<Integer> desiredIds = new HashSet<>();
        for (AudioSessionInfo session : desired) {
            Preset preset = repository.resolvePresetForPackage(session.packageName, route);
            if (preset == null) {
                continue;
            }
            engine.applyToSession(session.sessionId, preset, route);
            desiredIds.add(session.sessionId);
        }

        for (Integer sessionId : new ArrayList<>(appliedSessions)) {
            if (!desiredIds.contains(sessionId)) {
                engine.releaseSession(sessionId);
            }
        }
        appliedSessions.clear();
        appliedSessions.addAll(desiredIds);
    }

    private void releaseAppliedSessions() {
        if (appliedSessions.isEmpty()) {
            return;
        }
        for (Integer sessionId : new ArrayList<>(appliedSessions)) {
            engine.releaseSession(sessionId);
        }
        appliedSessions.clear();
    }

    private void handleDiscoveryError(@NonNull Throwable error) {
        String detail = describeError(error);
        if (error instanceof ConfigurationMissingException) {
            publishStatus("Cobertura total activa · Falta configurar el acceso", detail, "");
        } else if (error instanceof ShizukuBridge.UnavailableException) {
            publishStatus("Cobertura total activa · Shizuku no disponible", detail, "");
        } else if (error instanceof ShizukuBridge.PermissionException) {
            publishStatus("Cobertura total activa · Permiso de Shizuku pendiente", detail, "");
        } else if (error instanceof RetryLaterException) {
            // Keep the previous status; the next attempt is already scheduled.
        } else if (PresetRepository.COVERAGE_BACKEND_ROOT.equals(repository.getCoverageBackend())) {
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_ROOT_FAILURE_MS;
            publishStatus("Cobertura total activa · Root no disponible", detail, "");
        } else {
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
            publishStatus("Cobertura total activa · Error", detail, "");
        }
    }

    @NonNull
    private String describeError(Throwable error) {
        if (error instanceof ConfigurationMissingException) {
            return "Abre Cobertura total y concede el permiso de Shizuku o usa root.";
        }
        if (error instanceof ShizukuBridge.UnavailableException) {
            return "Abre Shizuku e inícialo; Mega Equalizer se conectará solo.";
        }
        if (error instanceof ShizukuBridge.PermissionException) {
            return "En Cobertura total, pulsa Solicitar permiso y acepta el aviso de Shizuku.";
        }
        if (error instanceof IOException && error.getMessage() != null) {
            return error.getMessage();
        }
        if (error.getMessage() != null) {
            return error.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    @NonNull
    private String buildDetail(List<AudioSessionInfo> sessions) {
        if (sessions.isEmpty()) {
            return "Sin sesiones activas; se reintenta automáticamente.";
        }
        int calls = 0;
        int accessibility = 0;
        int notifications = 0;
        int alarms = 0;
        for (AudioSessionInfo session : sessions) {
            if (session.isCallUsage()) calls++;
            if (session.isAccessibilityUsage()) accessibility++;
            if (session.isNotificationUsage()) notifications++;
            if (session.isAlarmUsage()) alarms++;
        }
        String detail = sessions.size() + (sessions.size() == 1
                ? " sesión activa"
                : " sesiones activas")
                + " · " + engine.getSessionCount() + " con ecualizador";
        if (calls > 0) {
            detail += " · " + calls + (calls == 1 ? " llamada" : " llamadas");
        }
        if (accessibility > 0) {
            detail += " · " + accessibility + (accessibility == 1 ? " accesibilidad" : " accesibilidad");
        }
        if (notifications > 0) {
            detail += " · " + notifications + (notifications == 1 ? " notificación" : " notificaciones");
        }
        if (alarms > 0) {
            detail += " · " + alarms + (alarms == 1 ? " alarma" : " alarmas");
        }
        return detail;
    }

    @NonNull
    private String buildSummary(List<AudioSessionInfo> sessions) {
        if (sessions.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        int shown = 0;
        for (AudioSessionInfo session : sessions) {
            if (shown == 6) {
                summary.append("\n…");
                break;
            }
            if (shown > 0) {
                summary.append('\n');
            }
            summary.append(session.describe());
            shown++;
        }
        return summary.toString();
    }

    private void publishStatus(String status, String detail, String summary) {
        String safeStatus = status == null ? "" : status;
        String safeDetail = detail == null ? "" : detail;
        String safeSummary = summary == null ? "" : summary;
        long now = System.currentTimeMillis();
        boolean changed = !safeStatus.equals(lastStatus)
                || !safeDetail.equals(lastDetail)
                || !safeSummary.equals(lastSummary);
        if (!changed && now - lastStatusAt < STATUS_HEARTBEAT_MS) {
            return;
        }
        lastStatus = safeStatus;
        lastDetail = safeDetail;
        lastSummary = safeSummary;
        lastStatusAt = now;
        repository.updateCoverageStatus(safeStatus, safeDetail, safeSummary);
        Intent update = new Intent(EqualizerService.ACTION_STATE_CHANGED);
        update.setPackage(context.getPackageName());
        context.sendBroadcast(update);
    }

    // ------------------------------------------------------------ pairing API

    public interface ResultCallback {
        void onResult(boolean success, @NonNull String message);
    }

    private static final class DiscoveryResult {
        private final String dump;
        private final String backendLabel;

        private DiscoveryResult(String dump, String backendLabel) {
            this.dump = dump;
            this.backendLabel = backendLabel;
        }
    }

    private static final class ConfigurationMissingException extends Exception {
    }

    private static final class RetryLaterException extends Exception {
    }
}
