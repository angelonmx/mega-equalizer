package com.angelonmx.megaequalizer.service;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.angelonmx.megaequalizer.data.PresetRepository;

/**
 * Backstop that revives the equalizer service after reboots, app updates or
 * aggressive battery managers. The accessibility service and the foreground
 * service itself also keep the engine refreshed every few seconds.
 */
public final class ServiceWatchdog {

    private static final String TAG = "ServiceWatchdog";
    private static final int JOB_ID = 7301;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    private ServiceWatchdog() {
    }

    public static void schedule(Context context) {
        try {
            JobScheduler scheduler =
                    (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return;
            }
            JobInfo job = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(context, ServiceWatchdogJobService.class)
            )
                    .setPersisted(true)
                    .setPeriodic(PERIOD_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            scheduler.schedule(job);
        } catch (RuntimeException error) {
            Log.w(TAG, "No se pudo programar el vigilante del servicio", error);
        }
    }

    /** Starts or refreshes the service only when the user enabled it. */
    public static void ensureRunning(Context context) {
        if (!PresetRepository.get(context).isEnabled()) {
            return;
        }
        if (EqualizerService.isRunning()) {
            EqualizerService.requestRefresh(context);
        } else {
            EqualizerService.start(context);
        }
    }
}
