package com.angelonmx.megaequalizer.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.service.EqualizerService;

/**
 * Receives the optional audio-effect session broadcasts sent by compatible
 * music players. When a global effect is available the session is ignored to
 * avoid applying the equalizer twice.
 */
public class AudioEffectSessionReceiver extends BroadcastReceiver {

    private static final String TAG = "AudioEffectSession";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        boolean open;
        if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            open = true;
        } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            open = false;
        } else {
            return;
        }
        int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        if (session <= 0) {
            return;
        }
        String packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        EqualizerService service = EqualizerService.getInstance();
        if (service != null) {
            service.handleEffectSession(session, packageName, open);
            return;
        }
        if (!PresetRepository.get(context).isEnabled()) {
            return;
        }
        try {
            Intent serviceIntent = new Intent(context, EqualizerService.class)
                    .setAction(EqualizerService.ACTION_EFFECT_SESSION)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(EqualizerService.EXTRA_SESSION_OPEN, open);
            ContextCompat.startForegroundService(context, serviceIntent);
        } catch (RuntimeException error) {
            Log.w(TAG, "No se pudo reenviar la sesión de audio al servicio", error);
        }
    }
}
