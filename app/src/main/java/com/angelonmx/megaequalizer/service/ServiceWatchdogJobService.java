package com.angelonmx.megaequalizer.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic JobScheduler entry point for {@link ServiceWatchdog}. */
public class ServiceWatchdogJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        ServiceWatchdog.ensureRunning(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
