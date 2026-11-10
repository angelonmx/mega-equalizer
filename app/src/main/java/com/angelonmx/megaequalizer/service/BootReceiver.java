package com.angelonmx.megaequalizer.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.angelonmx.megaequalizer.data.PresetRepository;

/**
 * Restores the equalizer after a reboot or after the app is updated. If the
 * user disabled the equalizer, nothing is started.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        ServiceWatchdog.schedule(context);
        if (!PresetRepository.get(context).isEnabled()) {
            return;
        }
        EqualizerService.start(context);
    }
}
