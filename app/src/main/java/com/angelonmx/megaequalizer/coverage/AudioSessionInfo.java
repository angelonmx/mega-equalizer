package com.angelonmx.megaequalizer.coverage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One active audio playback session discovered through ADB or root. */
public final class AudioSessionInfo {

    public final int sessionId;
    public final int uid;
    public final int pid;
    public final String state;
    public final String usage;
    public final String contentType;
    public final String packageName;

    public AudioSessionInfo(
            int sessionId,
            int uid,
            int pid,
            @NonNull String state,
            @NonNull String usage,
            @NonNull String contentType,
            @Nullable String packageName
    ) {
        this.sessionId = sessionId;
        this.uid = uid;
        this.pid = pid;
        this.state = state;
        this.usage = usage;
        this.contentType = contentType;
        this.packageName = packageName == null ? "" : packageName;
    }

    public boolean isActive() {
        return "started".equals(state);
    }

    /**
     * Voice and call usages that the global output effect usually misses on
     * devices with a dedicated voice processing path. These sessions get a
     * supplemental per-session effect even when the global effect is active.
     */
    public boolean isCallUsage() {
        return "USAGE_VOICE_COMMUNICATION".equals(usage)
                || "USAGE_VOICE_COMMUNICATION_SIGNALLING".equals(usage)
                || "USAGE_CALL_ASSISTANT".equals(usage)
                || "USAGE_NOTIFICATION_COMMUNICATION_REQUEST".equals(usage);
    }

    /** Human readable stream name used by the accessibility summaries. */
    @NonNull
    public String usageLabel() {
        if (usage == null) {
            return "Desconocido";
        }
        switch (usage) {
            case "USAGE_MEDIA":
                return "Multimedia";
            case "USAGE_GAME":
                return "Juego";
            case "USAGE_VOICE_COMMUNICATION":
                return "Llamada";
            case "USAGE_VOICE_COMMUNICATION_SIGNALLING":
                return "Señal de llamada";
            case "USAGE_CALL_ASSISTANT":
                return "Asistente de llamada";
            case "USAGE_NOTIFICATION":
                return "Notificación";
            case "USAGE_NOTIFICATION_RINGTONE":
                return "Timbre";
            case "USAGE_NOTIFICATION_COMMUNICATION_REQUEST":
                return "Llamada entrante";
            case "USAGE_NOTIFICATION_COMMUNICATION_INSTANT":
                return "Mensaje";
            case "USAGE_NOTIFICATION_COMMUNICATION_DELAYED":
                return "Mensaje";
            case "USAGE_NOTIFICATION_EVENT":
                return "Evento";
            case "USAGE_ALARM":
                return "Alarma";
            case "USAGE_ASSISTANCE_ACCESSIBILITY":
                return "Accesibilidad";
            case "USAGE_ASSISTANCE_NAVIGATION_GUIDANCE":
                return "Navegación";
            case "USAGE_ASSISTANCE_SONIFICATION":
                return "Sonido del sistema";
            case "USAGE_ASSISTANT":
                return "Asistente";
            default:
                return usage.startsWith("USAGE_")
                        ? usage.substring("USAGE_".length()).replace('_', ' ').toLowerCase()
                        : usage;
        }
    }

    @NonNull
    public String describe() {
        String app = packageName.isEmpty() ? "uid " + uid : packageName;
        return sessionId + " · " + usageLabel() + " · " + app;
    }
}
