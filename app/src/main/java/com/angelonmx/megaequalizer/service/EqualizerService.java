package com.angelonmx.megaequalizer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.MainActivity;
import com.angelonmx.megaequalizer.R;
import com.angelonmx.megaequalizer.audio.AudioRouteManager;
import com.angelonmx.megaequalizer.audio.EqualizerEngine;
import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Foreground service that keeps the audio effects and device profiles alive. */
public class EqualizerService extends Service {

    private static final String TAG = "EqualizerService";
    public static final String ACTION_START = "com.angelonmx.megaequalizer.action.START";
    public static final String ACTION_STOP = "com.angelonmx.megaequalizer.action.STOP";
    public static final String ACTION_DISABLE = "com.angelonmx.megaequalizer.action.DISABLE";
    public static final String ACTION_REFRESH = "com.angelonmx.megaequalizer.action.REFRESH";
    public static final String ACTION_EFFECT_SESSION = "com.angelonmx.megaequalizer.action.EFFECT_SESSION";
    public static final String ACTION_STATE_CHANGED = "com.angelonmx.megaequalizer.action.STATE_CHANGED";
    public static final String EXTRA_SESSION_OPEN = "com.angelonmx.megaequalizer.extra.SESSION_OPEN";

    private static final String CHANNEL_ID = "mega_equalizer_status";
    private static final int NOTIFICATION_ID = 1001;

    private static volatile EqualizerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> sessionPackages = new HashMap<>();
    private final Runnable evaluateRunnable = this::evaluateAndApply;

    private PresetRepository repository;
    private EqualizerEngine engine;
    private AudioManager audioManager;
    private boolean foregroundStarted;
    private boolean destroyed;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] addedDevices) {
            scheduleEvaluate();
        }

        @Override
        public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] removedDevices) {
            scheduleEvaluate();
        }
    };

    private final BroadcastReceiver deviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scheduleEvaluate();
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener =
            (sharedPreferences, key) -> {
                if (key == null) {
                    return;
                }
                if (key.equals(PresetRepository.KEY_ENABLED)
                        || key.equals(PresetRepository.KEY_PRESETS)
                        || key.equals(PresetRepository.KEY_ASSIGNMENTS)
                        || key.equals(PresetRepository.KEY_APP_OVERRIDES)
                        || key.equals(PresetRepository.KEY_PER_APP_ENABLED)
                        || key.equals(PresetRepository.KEY_FOREGROUND_PACKAGE)) {
                    handler.removeCallbacks(evaluateRunnable);
                    handler.postDelayed(evaluateRunnable, 180L);
                }
            };

    public static EqualizerService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(
                    context,
                    new Intent(context, EqualizerService.class).setAction(ACTION_START)
            );
        } catch (RuntimeException error) {
            Log.w(TAG, "No se pudo iniciar el servicio en segundo plano", error);
        }
    }

    public static void requestRefresh(Context context) {
        EqualizerService service = instance;
        if (service != null) {
            service.handler.removeCallbacks(service.evaluateRunnable);
            service.handler.postDelayed(service.evaluateRunnable, 60L);
            return;
        }
        if (PresetRepository.get(context).isEnabled()) {
            start(context);
        }
    }

    public static void requestStop(Context context) {
        PresetRepository.get(context).setEnabled(false);
        EqualizerService service = instance;
        if (service != null) {
            try {
                context.startService(
                        new Intent(context, EqualizerService.class).setAction(ACTION_STOP)
                );
            } catch (RuntimeException error) {
                context.stopService(new Intent(context, EqualizerService.class));
            }
        } else {
            context.stopService(new Intent(context, EqualizerService.class));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        repository = PresetRepository.get(this);
        engine = new EqualizerEngine(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        }
        IntentFilter deviceFilter = new IntentFilter();
        deviceFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        deviceFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        deviceFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        try {
            ContextCompat.registerReceiver(
                    this,
                    deviceReceiver,
                    deviceFilter,
                    ContextCompat.RECEIVER_EXPORTED
            );
        } catch (RuntimeException error) {
            Log.w(TAG, "No se pudo registrar el receptor de dispositivos", error);
        }
        repository.getPreferences().registerOnSharedPreferenceChangeListener(preferencesListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_REFRESH : intent.getAction();
        if (ACTION_STOP.equals(action) || ACTION_DISABLE.equals(action)) {
            repository.setEnabled(false);
            shutdown();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            repository.setEnabled(true);
        }
        if (!repository.isEnabled()) {
            shutdown();
            return START_NOT_STICKY;
        }

        startForegroundSafely(buildNotification(getString(R.string.notification_text_initializing)));
        if (intent != null && ACTION_EFFECT_SESSION.equals(action)) {
            handleEffectSessionIntent(intent);
        }
        evaluateAndApply();
        return START_STICKY;    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (audioManager != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(deviceCallback);
            } catch (RuntimeException ignored) {
            }
        }
        try {
            unregisterReceiver(deviceReceiver);
        } catch (RuntimeException ignored) {
        }
        if (repository != null) {
            repository.getPreferences().unregisterOnSharedPreferenceChangeListener(preferencesListener);
            repository.updateServiceStatus(false, null, null, "", "");
        }
        if (engine != null) {
            engine.releaseAll();
        }
        sessionPackages.clear();
        instance = null;
        sendStateBroadcast();
        foregroundStarted = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void handleEffectSessionIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        if (sessionId <= 0) {
            return;
        }
        boolean open;
        if (intent.hasExtra(EXTRA_SESSION_OPEN)) {
            open = intent.getBooleanExtra(EXTRA_SESSION_OPEN, true);
        } else {
            open = AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction());
        }
        if (open) {
            String packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            sessionPackages.put(sessionId, packageName == null ? "" : packageName);
            if (engine.isGlobalActive()) {
                return;
            }
            AudioRoute route = AudioRouteManager.getCurrentRoute(this);
            Preset preset = repository.resolvePresetForPackage(packageName, route);
            engine.applyToSession(sessionId, preset, route);
        } else {
            sessionPackages.remove(sessionId);
            engine.releaseSession(sessionId);
        }
    }

    public void handleEffectSession(int sessionId, String packageName, boolean open) {
        if (sessionId <= 0) {
            return;
        }
        if (!open) {
            sessionPackages.remove(sessionId);
            engine.releaseSession(sessionId);
            return;
        }
        sessionPackages.put(sessionId, packageName == null ? "" : packageName);
        if (engine.isGlobalActive()) {
            return;
        }
        AudioRoute route = AudioRouteManager.getCurrentRoute(this);
        Preset preset = repository.resolvePresetForPackage(packageName, route);
        engine.applyToSession(sessionId, preset, route);
    }

    private void evaluateAndApply() {
        if (destroyed || !repository.isEnabled()) {
            if (!repository.isEnabled()) {
                shutdown();
            }
            return;
        }
        AudioRoute route = AudioRouteManager.getCurrentRoute(this);
        boolean accessibilityEnabled = AccessibilityUtils.isServiceEnabled(this);
        boolean perApp = repository.isPerAppEnabled() && accessibilityEnabled;
        String foregroundPackage = perApp ? repository.getForegroundPackage() : "";
        Preset preset = repository.resolvePreset(route, foregroundPackage, perApp);

        engine.applyPreset(preset, route);
        String engineLabel = preset == null ? "" : engine.getEngineLabel();
        String error = preset == null ? "" : engine.getLastError();

        updateSessionEffects(route);

        repository.updateServiceStatus(true, preset, route, engineLabel, error);
        Notification notification = buildNotification(null);
        if (foregroundStarted) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
            } catch (RuntimeException notificationError) {
                Log.w(TAG, "No se pudo actualizar la notificación del servicio", notificationError);
            }
        } else {
            startForegroundSafely(notification);
        }
        sendStateBroadcast();
    }

    private void updateSessionEffects(AudioRoute route) {
        if (engine.isGlobalActive()) {
            engine.releaseAllSessions();
            return;
        }
        if (sessionPackages.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, String> entry : new HashMap<>(sessionPackages).entrySet()) {
            Preset appPreset = repository.resolvePresetForPackage(entry.getValue(), route);
            engine.applyToSession(entry.getKey(), appPreset, route);
        }
    }

    private void scheduleEvaluate() {
        if (destroyed) {
            return;
        }
        handler.removeCallbacks(evaluateRunnable);
        handler.postDelayed(evaluateRunnable, 450L);
    }

    private void shutdown() {
        if (engine != null) {
            engine.releaseAll();
        }
        sessionPackages.clear();
        if (repository != null) {
            repository.updateServiceStatus(false, null, null, "", "");
        }
        sendStateBroadcast();
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        stopSelf();
    }

    private void sendStateBroadcast() {
        try {
            Intent update = new Intent(ACTION_STATE_CHANGED);
            update.setPackage(getPackageName());
            sendBroadcast(update);
        } catch (RuntimeException ignored) {
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_service_description));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String overrideText) {
        String text = overrideText;
        if (text == null) {
            String presetName = repository.getActivePresetName();
            String deviceName = repository.getActiveDeviceName();
            if (presetName == null || presetName.isEmpty()) {
                if (deviceName == null || deviceName.isEmpty()) {
                    deviceName = AudioRouteManager.getCurrentRoute(this).displayName();
                }
                text = getString(R.string.notification_text_no_preset_format, deviceName);
            } else {
                text = getString(R.string.notification_text_format, presetName, deviceName);
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent disableIntent = new Intent(this, EqualizerService.class).setAction(ACTION_DISABLE);
        PendingIntent disablePending = PendingIntent.getService(
                this,
                2,
                disableIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_equalizer_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(
                        R.drawable.ic_equalizer_notification,
                        getString(R.string.notification_action_disable),
                        disablePending
                )
                .build();
    }

    private void startForegroundSafely(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudo iniciar el servicio en primer plano", error);
        }
    }
}
