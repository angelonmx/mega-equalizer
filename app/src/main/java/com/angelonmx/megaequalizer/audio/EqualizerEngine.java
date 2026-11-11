package com.angelonmx.megaequalizer.audio;

import android.content.Context;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.DeviceCategory;
import com.angelonmx.megaequalizer.data.Preset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the Android audio effects. It first tries the modern 10-band
 * DynamicsProcessing effect on the global output mix, then falls back to the
 * device's built-in Equalizer. If the device does not allow global effects at
 * all, effects can still be attached to individual audio sessions opened by
 * compatible media players.
 *
 * <p>Global effects use session 0. Android applies them to the complete output
 * mix, so media, notifications, accessibility speech (TalkBack) and every other
 * stream share the same equalization when the device supports global effects.
 * The accessibility service does not capture audio; it only keeps this engine
 * alive and detects the foreground app for optional per-app profiles.</p>
 */
public final class EqualizerEngine {

    private static final String TAG = "EqualizerEngine";
    private static final int PRIORITY = 0;

    private final Context context;
    private final Map<Integer, SessionEffect> sessions = new HashMap<>();

    private DynamicsProcessing globalDynamics;
    private Equalizer globalEqualizer;
    private int equalizerBandCount;
    private DeviceCategory globalCategory;
    private DeviceCategory failedCategory;
    private String engineLabel = "";
    private String lastError = "";
    private Preset currentPreset;
    private AudioRoute currentRoute;
    private volatile Runnable needsReapplyListener;
    private volatile boolean controlLost;
    private volatile boolean globalAllowed = true;
    private long lastGlobalAttemptAt;

    public EqualizerEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Called when Android loses control of the global effect. */
    public void setOnNeedsReapplyListener(Runnable listener) {
        needsReapplyListener = listener;
    }

    public synchronized void applyPreset(Preset preset, AudioRoute route) {
        currentPreset = preset;
        currentRoute = route;
        if (preset == null) {
            disableGlobal();
            releaseAllSessions();
            engineLabel = "";
            lastError = "";
            return;
        }

        if (!globalAllowed) {
            // Session-only coverage: the user explicitly asked for per-session
            // effects even if the global mix is available on this device.
            releaseGlobal();
            engineLabel = context.getString(com.angelonmx.megaequalizer.R.string.engine_session_only);
            lastError = "";
            return;
        }

        DeviceCategory category = route == null ? DeviceCategory.OTHER : route.category;
        boolean globalExists = globalDynamics != null || globalEqualizer != null;
        if (globalExists && globalCategory != category) {
            releaseGlobal();
            globalExists = false;
        }

        if (!globalExists) {
            if (failedCategory == category) {
                engineLabel = context.getString(com.angelonmx.megaequalizer.R.string.engine_session_only);
                lastError = context.getString(com.angelonmx.megaequalizer.R.string.effect_not_supported);
                return;
            }
            createGlobalEffect(category);
        }

        if (globalDynamics != null || globalEqualizer != null) {
            if (!updateGlobalEffect(preset)) {
                releaseGlobal();
                createGlobalEffect(category);
                if ((globalDynamics != null || globalEqualizer != null) && !updateGlobalEffect(preset)) {
                    releaseGlobal();
                    failedCategory = category;
                    engineLabel = context.getString(com.angelonmx.megaequalizer.R.string.engine_session_only);
                    lastError = context.getString(com.angelonmx.megaequalizer.R.string.effect_not_supported);
                }
            }
        }
    }

    /**
     * Periodic health check used by the foreground service. It reapplies the
     * current preset, reattaches the global effect after Android steals or
     * loses it, and retries a previously blocked output every two minutes.
     *
     * @return true when the engine state changed and the UI should refresh.
     */
    public synchronized boolean ensureApplied() {
        if (currentPreset == null || !globalAllowed) {
            return false;
        }
        boolean changed = false;
        if (controlLost) {
            releaseGlobal();
            failedCategory = null;
            controlLost = false;
            changed = true;
        }
        if (!isGlobalActive()) {
            long now = System.currentTimeMillis();
            if (failedCategory != null && now - lastGlobalAttemptAt < 120000L) {
                return changed;
            }
            failedCategory = null;
            applyPreset(currentPreset, currentRoute);
            return true;
        }
        if (!updateGlobalEffect(currentPreset)) {
            releaseGlobal();
            failedCategory = null;
            createGlobalEffect(currentRoute == null ? DeviceCategory.OTHER : currentRoute.category);
            changed = true;
        }
        return changed;
    }

    public synchronized boolean isGlobalActive() {
        return globalDynamics != null || globalEqualizer != null;
    }

    public synchronized String getEngineLabel() {
        return engineLabel == null ? "" : engineLabel;
    }

    public synchronized String getLastError() {
        return lastError == null ? "" : lastError;
    }

    public synchronized Preset getCurrentPreset() {
        return currentPreset;
    }

    public synchronized void disableAll() {
        disableGlobal();
        for (SessionEffect effect : sessions.values()) {
            effect.disable();
        }
        engineLabel = "";
        lastError = "";
    }

    public synchronized void releaseAll() {
        releaseGlobal();
        releaseAllSessions();
        currentPreset = null;
        currentRoute = null;
        engineLabel = "";
        lastError = "";
        failedCategory = null;
        globalAllowed = true;
    }

    public synchronized void applyToSession(int sessionId, Preset preset, AudioRoute route) {
        if (sessionId <= 0) {
            return;
        }
        currentRoute = route;
        if (preset == null) {
            releaseSession(sessionId);
            return;
        }
        SessionEffect effect = sessions.get(sessionId);
        if (effect == null) {
            effect = new SessionEffect();
            if (!effect.create(sessionId, preset)) {
                return;
            }
            sessions.put(sessionId, effect);
        } else if (!effect.update(preset)) {
            effect.release();
            sessions.remove(sessionId);
            SessionEffect replacement = new SessionEffect();
            if (replacement.create(sessionId, preset)) {
                sessions.put(sessionId, replacement);
            }
            return;
        }
        if (TextUtils.isEmpty(engineLabel)) {
            engineLabel = context.getString(com.angelonmx.megaequalizer.R.string.engine_session_only);
        }
    }

    public synchronized void releaseSession(int sessionId) {
        SessionEffect effect = sessions.remove(sessionId);
        if (effect != null) {
            effect.release();
        }
    }

    public synchronized void releaseAllSessions() {
        for (SessionEffect effect : sessions.values()) {
            effect.release();
        }
        sessions.clear();
    }

    /**
     * Enables or disables the global session-0 effect. The session-only
     * fallback releases session 0 so Android does not apply two equalizers to
     * the same output.
     */
    public synchronized void setGlobalAllowed(boolean allowed) {
        if (globalAllowed == allowed) {
            return;
        }
        globalAllowed = allowed;
        if (!allowed) {
            releaseGlobal();
            if (currentPreset != null) {
                engineLabel = context.getString(
                        com.angelonmx.megaequalizer.R.string.engine_session_only
                );
            }
        } else {
            failedCategory = null;
            controlLost = false;
        }
    }

    public synchronized boolean isGlobalAllowed() {
        return globalAllowed;
    }

    public synchronized int getSessionCount() {
        return sessions.size();
    }

    public synchronized boolean hasSession(int sessionId) {
        return sessions.containsKey(sessionId);
    }

    private void createGlobalEffect(DeviceCategory category) {
        releaseGlobal();
        lastError = "";
        lastGlobalAttemptAt = System.currentTimeMillis();
        controlLost = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                DynamicsProcessing processor = createDynamics(0, currentPreset);
                if (processor != null) {
                    globalDynamics = processor;
                    attachControlListener(processor);
                    globalCategory = category;
                    failedCategory = null;
                    engineLabel = context.getString(com.angelonmx.megaequalizer.R.string.engine_dynamics);
                    lastError = "";
                    return;
                }
            } catch (Throwable error) {
                Log.w(TAG, "DynamicsProcessing global no disponible", error);
            }
        }
        try {
            Equalizer equalizer = createEqualizer(0, currentPreset);
            if (equalizer != null) {
                globalEqualizer = equalizer;
                attachControlListener(equalizer);
                globalCategory = category;
                failedCategory = null;
                engineLabel = context.getString(
                        com.angelonmx.megaequalizer.R.string.engine_equalizer_format,
                        equalizerBandCount
                );
                lastError = "";
                return;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Equalizer global no disponible", error);
        }
        try {
            globalEqualizer = new Equalizer(PRIORITY, 0);
            attachControlListener(globalEqualizer);
            globalEqualizer.setEnabled(true);
            equalizerBandCount = globalEqualizer.getNumberOfBands();
            globalCategory = category;
            failedCategory = null;
            engineLabel = context.getString(
                    com.angelonmx.megaequalizer.R.string.engine_equalizer_format,
                    equalizerBandCount
            );
            lastError = "";
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo crear ningún efecto global", error);
            releaseGlobal();
            failedCategory = category;
            engineLabel = context.getString(com.angelonmx.megaequalizer.R.string.engine_session_only);
            lastError = context.getString(com.angelonmx.megaequalizer.R.string.effect_not_supported);
        }
    }

    private void attachControlListener(AudioEffect effect) {
        try {
            effect.setControlStatusListener((audioEffect, controlGranted) -> {
                if (controlGranted) {
                    controlLost = false;
                    return;
                }
                Log.w(TAG, "Android perdió el control del efecto global");
                controlLost = true;
                Runnable listener = needsReapplyListener;
                if (listener != null) {
                    listener.run();
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo vigilar el estado del efecto global", error);
        }
    }

    private boolean updateGlobalEffect(Preset preset) {
        if (preset == null) {
            return false;
        }
        if (globalDynamics != null) {
            try {
                for (int i = 0; i < Preset.BAND_COUNT; i++) {
                    DynamicsProcessing.EqBand band = new DynamicsProcessing.EqBand(
                            true,
                            Preset.FREQUENCIES[i],
                            preset.gains[i]
                    );
                    globalDynamics.setPostEqBandAllChannelsTo(i, band);
                }
                globalDynamics.setInputGainAllChannelsTo(preset.preamp);
                globalDynamics.setEnabled(true);
                lastError = "";
                return true;
            } catch (Throwable error) {
                Log.w(TAG, "No se pudo actualizar DynamicsProcessing", error);
                return false;
            }
        }
        if (globalEqualizer != null) {
            return applyToEqualizer(globalEqualizer, equalizerBandCount, preset);
        }
        return false;
    }

    private DynamicsProcessing createDynamics(int sessionId, Preset preset) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || preset == null) {
            return null;
        }
        DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false,
                0,
                false,
                0,
                true,
                Preset.BAND_COUNT,
                true
        );
        DynamicsProcessing.Eq eq = new DynamicsProcessing.Eq(true, true, Preset.BAND_COUNT);
        for (int i = 0; i < Preset.BAND_COUNT; i++) {
            eq.setBand(i, new DynamicsProcessing.EqBand(true, Preset.FREQUENCIES[i], preset.gains[i]));
        }
        builder.setPostEqAllChannelsTo(eq);
        builder.setInputGainAllChannelsTo(preset.preamp);
        builder.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                true,
                true,
                0,
                0.001f,
                0.06f,
                20f,
                -3f,
                0f
        ));
        DynamicsProcessing processor = new DynamicsProcessing(PRIORITY, sessionId, builder.build());
        processor.setEnabled(true);
        return processor;
    }

    private Equalizer createEqualizer(int sessionId, Preset preset) {
        if (preset == null) {
            return null;
        }
        Equalizer equalizer = new Equalizer(PRIORITY, sessionId);
        equalizer.setEnabled(true);
        int bands = equalizer.getNumberOfBands();
        if (!applyToEqualizer(equalizer, bands, preset)) {
            equalizer.release();
            return null;
        }
        if (sessionId == 0) {
            equalizerBandCount = bands;
        }
        return equalizer;
    }

    private boolean applyToEqualizer(Equalizer equalizer, int bandCount, Preset preset) {
        try {
            short[] range = equalizer.getBandLevelRange();
            short min = range != null && range.length >= 2 ? range[0] : -1500;
            short max = range != null && range.length >= 2 ? range[1] : 1500;
            for (short band = 0; band < bandCount; band++) {
                int centerHz = equalizer.getCenterFreq(band) / 1000;
                float gain = preset.gainAtFrequency(centerHz) + preset.preamp;
                short millibels = (short) Math.round(gain * 100f);
                millibels = (short) Math.max(min, Math.min(max, millibels));
                equalizer.setBandLevel(band, millibels);
            }
            equalizer.setEnabled(true);
            lastError = "";
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "No se pudo aplicar el ecualizador de respaldo", error);
            return false;
        }
    }

    private void disableGlobal() {
        try {
            if (globalDynamics != null) {
                globalDynamics.setEnabled(false);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (globalEqualizer != null) {
                globalEqualizer.setEnabled(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseGlobal() {
        if (globalDynamics != null) {
            try {
                globalDynamics.release();
            } catch (Throwable ignored) {
            }
            globalDynamics = null;
        }
        if (globalEqualizer != null) {
            try {
                globalEqualizer.release();
            } catch (Throwable ignored) {
            }
            globalEqualizer = null;
        }
        globalCategory = null;
        equalizerBandCount = 0;
        controlLost = false;
    }

    private final class SessionEffect {
        private DynamicsProcessing dynamics;
        private Equalizer equalizer;
        private int bandCount;

        boolean create(int sessionId, Preset preset) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    dynamics = createDynamics(sessionId, preset);
                    if (dynamics != null) {
                        return true;
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "DynamicsProcessing de sesión no disponible", error);
                }
            }
            try {
                equalizer = createEqualizer(sessionId, preset);
                if (equalizer != null) {
                    bandCount = equalizer.getNumberOfBands();
                    return true;
                }
            } catch (Throwable error) {
                Log.w(TAG, "Equalizer de sesión no disponible", error);
            }
            return false;
        }

        boolean update(Preset preset) {
            if (dynamics != null) {
                try {
                    for (int i = 0; i < Preset.BAND_COUNT; i++) {
                        dynamics.setPostEqBandAllChannelsTo(
                                i,
                                new DynamicsProcessing.EqBand(true, Preset.FREQUENCIES[i], preset.gains[i])
                        );
                    }
                    dynamics.setInputGainAllChannelsTo(preset.preamp);
                    dynamics.setEnabled(true);
                    return true;
                } catch (Throwable error) {
                    return false;
                }
            }
            if (equalizer != null) {
                return applyToEqualizer(equalizer, bandCount, preset);
            }
            return false;
        }

        void disable() {
            try {
                if (dynamics != null) {
                    dynamics.setEnabled(false);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (equalizer != null) {
                    equalizer.setEnabled(false);
                }
            } catch (Throwable ignored) {
            }
        }

        void release() {
            if (dynamics != null) {
                try {
                    dynamics.release();
                } catch (Throwable ignored) {
                }
                dynamics = null;
            }
            if (equalizer != null) {
                try {
                    equalizer.release();
                } catch (Throwable ignored) {
                }
                equalizer = null;
            }
        }
    }
}
