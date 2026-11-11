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
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

/**
 * Keeps supplemental per-session effects in sync with the sessions that are
 * actually playing. The global session-0 effect remains the primary path; the
 * controller only adds effects where the global mix is known to miss audio
 * (voice calls) or where the user chose the session-only fallback.
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

    private AdbBridge adbBridge;
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
        disconnectAdb();
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
        if (PresetRepository.COVERAGE_BACKEND_ADB.equals(backend)) {
            return new DiscoveryResult(readAdb(), "ADB");
        }
        return discoverAutomatically();
    }

    /**
     * Automatic mode prefers Shizuku because it does not need ports or a
     * pairing code. If Shizuku is unavailable or fails, a previously paired
     * ADB connection is used as a fallback.
     */
    @NonNull
    private DiscoveryResult discoverAutomatically() throws Exception {
        if (ShizukuBridge.isAvailable() && ShizukuBridge.hasPermission()) {
            try {
                return new DiscoveryResult(ShizukuBridge.readDumpsysAudio(), "Shizuku");
            } catch (Throwable error) {
                if (repository.getAdbConnectPort() > 0) {
                    Log.w(TAG, "Shizuku falló; se intenta la conexión ADB", error);
                    return new DiscoveryResult(readAdb(), "ADB");
                }
                throw error;
            }
        }
        if (repository.getAdbConnectPort() > 0) {
            return new DiscoveryResult(readAdb(), "ADB");
        }
        throw new ConfigurationMissingException();
    }

    @NonNull
    private String readAdb() throws Exception {
        int port = repository.getAdbConnectPort();
        if (port <= 0) {
            throw new ConfigurationMissingException();
        }
        if (adbBridge == null) {
            adbBridge = new AdbBridge(AdbIdentity.get(context));
        }
        long now = System.currentTimeMillis();
        if (now < nextConnectAttemptAt) {
            throw new RetryLaterException();
        }
        try {
            if (!adbBridge.isConnected()) {
                adbBridge.connect(port);
                repository.setAdbPaired(true);
            }
            nextConnectAttemptAt = 0L;
            return adbBridge.readDumpsysAudio();
        } catch (Throwable error) {
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
            // Force a clean connection and shell loop on the next attempt.
            adbBridge.disconnect();
            throw error;
        }
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
            if (sessionOnly || !globalActive || session.isCallUsage()) {
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
        } else if (error instanceof AdbAuthenticationFailedException) {
            repository.setAdbPaired(false);
            disconnectAdb();
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
            publishStatus("Cobertura total activa · Autorización pendiente", detail, "");
        } else if (error instanceof AdbPairingRequiredException
                || error instanceof IllegalStateException && isPairingMessage(error)) {
            repository.setAdbPaired(false);
            disconnectAdb();
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
            publishStatus("Cobertura total activa · Emparejamiento necesario", detail, "");
        } else if (PresetRepository.COVERAGE_BACKEND_ROOT.equals(repository.getCoverageBackend())) {
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_ROOT_FAILURE_MS;
            publishStatus("Cobertura total activa · Root no disponible", detail, "");
        } else {
            disconnectAdb();
            nextConnectAttemptAt = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
            publishStatus("Cobertura total activa · ADB sin conexión", detail, "");
        }
    }

    private boolean isPairingMessage(Throwable error) {
        String message = error.getMessage();
        return message != null && message.toLowerCase().contains("pair");
    }

    @NonNull
    private String describeError(Throwable error) {
        if (error instanceof ConfigurationMissingException) {
            return "Abre Cobertura total y concede el permiso de Shizuku o empareja la depuración inalámbrica.";
        }
        if (error instanceof ShizukuBridge.UnavailableException) {
            return "Abre Shizuku e inícialo con ADB o root; Mega Equalizer se conectará solo.";
        }
        if (error instanceof ShizukuBridge.PermissionException) {
            return "En Cobertura total, pulsa Solicitar permiso y acepta el aviso de Shizuku.";
        }
        if (error instanceof AdbAuthenticationFailedException) {
            return "El equipo rechazó la identidad. Vuelve a emparejar con un código nuevo.";
        }
        if (error instanceof AdbPairingRequiredException) {
            return "El equipo exige emparejar esta app antes de aceptar la conexión.";
        }
        if (error instanceof ConnectException) {
            return "No se pudo abrir el puerto. Activa la depuración inalámbrica y comprueba el puerto.";
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
        for (AudioSessionInfo session : sessions) {
            if (session.isCallUsage()) {
                calls++;
            }
        }
        String detail = sessions.size() + (sessions.size() == 1
                ? " sesión activa"
                : " sesiones activas")
                + " · " + engine.getSessionCount() + " con ecualizador";
        if (calls > 0) {
            detail += " · " + calls + (calls == 1 ? " llamada" : " llamadas");
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

    private void disconnectAdb() {
        if (adbBridge != null) {
            adbBridge.disconnect();
        }
    }

    // ------------------------------------------------------------ pairing API

    public interface ResultCallback {
        void onResult(boolean success, @NonNull String message);
    }

    /**
     * Pairs the persistent identity, verifies the connection and stores the
     * ports. The callback runs on a background thread.
     */
    public static void pairAndVerify(
            @NonNull Context context,
            int pairingPort,
            @NonNull String pairingCode,
            int connectPort,
            @NonNull ResultCallback callback
    ) {
        runInBackground(() -> {
            AdbBridge bridge = null;
            try {
                AdbIdentity identity = AdbIdentity.get(context);
                bridge = new AdbBridge(identity);
                boolean paired = bridge.pair(pairingPort, pairingCode);
                if (!paired) {
                    throw new IOException("El código de emparejamiento no fue aceptado");
                }
                PresetRepository repository = PresetRepository.get(context);
                repository.setAdbPairingPort(pairingPort);
                repository.setAdbConnectPort(connectPort);
                repository.setAdbPaired(true);
                bridge.connect(connectPort);
                String dump = bridge.readDumpsysAudio();
                if (!dump.contains("AudioPlaybackConfiguration")) {
                    throw new IOException("ADB respondió, pero no devolvió sesiones de audio");
                }
                callback.onResult(true, "Emparejado y conectado al puerto " + connectPort);
            } catch (Throwable error) {
                callback.onResult(false, pairingError(error));
            } finally {
                if (bridge != null) {
                    bridge.disconnect();
                }
            }
        });
    }

    /** Tests an already paired identity against a connection port. */
    public static void testConnection(
            @NonNull Context context,
            int connectPort,
            @NonNull ResultCallback callback
    ) {
        runInBackground(() -> {
            AdbBridge bridge = null;
            try {
                AdbIdentity identity = AdbIdentity.get(context);
                bridge = new AdbBridge(identity);
                bridge.connect(connectPort);
                String dump = bridge.readDumpsysAudio();
                int sessions = AudioSessionDumpParser.parse(
                        dump,
                        context.getPackageManager(),
                        Process.myUid()
                ).size();
                PresetRepository repository = PresetRepository.get(context);
                repository.setAdbConnectPort(connectPort);
                repository.setAdbPaired(true);
                callback.onResult(
                        true,
                        "Conexión correcta · " + sessions
                                + (sessions == 1 ? " sesión activa" : " sesiones activas")
                );
            } catch (Throwable error) {
                callback.onResult(false, pairingError(error));
            } finally {
                if (bridge != null) {
                    bridge.disconnect();
                }
            }
        });
    }

    @NonNull
    private static String pairingError(Throwable error) {
        if (error instanceof AdbAuthenticationFailedException) {
            return "ADB rechazó la identidad. Empareja de nuevo con un código nuevo.";
        }
        if (error instanceof AdbPairingRequiredException) {
            return "Emparejamiento necesario: usa el puerto y el código de emparejamiento.";
        }
        if (error instanceof ConnectException) {
            return "No se pudo conectar. Comprueba el puerto y que la depuración inalámbrica siga activa.";
        }
        if (error.getMessage() != null && !error.getMessage().isEmpty()) {
            return error.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    private static void runInBackground(Runnable task) {
        Thread thread = new Thread(task, "coverage-pairing");
        thread.setDaemon(true);
        thread.start();
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
