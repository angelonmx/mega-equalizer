package com.angelonmx.megaequalizer.coverage;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Optional Shizuku backend. It runs commands through Shizuku with the
 * permission the user grants; Mega Equalizer still creates every audio
 * effect itself. No root access is required when Shizuku is running.
 *
 * <p>Besides {@code dumpsys audio}, it can execute arbitrary shell commands
 * through Shizuku (e.g. reflection over {@code AudioProductStrategy} to get
 * the accessibility strategy id).</p>
 */
public final class ShizukuBridge {

    public static final String PACKAGE_NAME = "moe.shizuku.privileged.api";
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;
    private static final int TIMEOUT_SECONDS = 30;

    private ShizukuBridge() {
    }

    /** True when the Shizuku app is installed, even if its service is stopped. */
    public static boolean isInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    /** True when the Shizuku service is running and its binder is reachable. */
    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable error) {
            return false;
        }
    }

    /** True when Mega Equalizer is allowed to use the Shizuku API. */
    public static boolean hasPermission() {
        if (!isAvailable()) {
            return false;
        }
        try {
            if (Shizuku.isPreV11()) {
                return true;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable error) {
            return false;
        }
    }

    public static boolean shouldShowRequestPermissionRationale() {
        try {
            return isAvailable() && !Shizuku.isPreV11()
                    && Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable error) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        Shizuku.requestPermission(requestCode);
    }

    /**
     * Runs {@code dumpsys audio} through Shizuku and returns its output.
     *
     * @throws UnavailableException when Shizuku is not running
     * @throws PermissionException  when the user has not authorized the app
     */
    @NonNull
    public static String readDumpsysAudio() throws Exception {
        return runShellCommand(new String[]{"/system/bin/sh", "-c", "dumpsys audio 2>&1"}, true);
    }

    /**
     * Runs an arbitrary shell command through Shizuku and returns its stdout.
     *
     * @param command command and arguments, e.g. {@code {"/system/bin/sh", "-c", "..."}}
     * @throws UnavailableException when Shizuku is not running
     * @throws PermissionException  when the user has not authorized the app
     */
    @NonNull
    public static String runShellCommand(@NonNull String[] command) throws Exception {
        return runShellCommand(command, false);
    }

    @NonNull
    private static String runShellCommand(@NonNull String[] command, boolean requireAudio) throws Exception {
        if (!isAvailable()) {
            throw new UnavailableException("Shizuku no está en ejecución");
        }
        if (!hasPermission()) {
            throw new PermissionException("Falta el permiso de Shizuku");
        }

        IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
        if (service == null) {
            throw new UnavailableException("Shizuku no expuso su servicio");
        }

        IRemoteProcess process;
        try {
            process = service.newProcess(command, null, null);
        } catch (SecurityException error) {
            throw new PermissionException("Shizuku rechazó el permiso de la app");
        }
        if (process == null) {
            throw new IOException("Shizuku no pudo iniciar el comando");
        }

        ParcelFileDescriptor stdout = null;
        ParcelFileDescriptor stderr = null;
        try {
            stdout = process.getInputStream();
            stderr = process.getErrorStream();
            if (stdout == null) {
                throw new IOException("Shizuku no devolvió la salida del comando");
            }
            ProcessReader reader = new ProcessReader(stdout);
            Thread readerThread = new Thread(reader, "shizuku-shell-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitForTimeout(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS.toString()
            );
            if (!finished) {
                try {
                    process.destroy();
                } catch (RuntimeException ignored) {
                }
                throw new IOException("Shizuku no respondió a tiempo");
            }
            readerThread.join(2000L);
            if (readerThread.isAlive()) {
                throw new IOException("Shizuku no terminó de entregar la salida");
            }
            if (reader.failure != null) {
                throw new IOException("No se pudo leer la salida de Shizuku", reader.failure);
            }
            String output = reader.output == null ? "" : reader.output;
            if (requireAudio && !output.contains("AudioPlaybackConfiguration")) {
                String detail = output.trim();
                if (detail.length() > 160) {
                    detail = detail.substring(0, 160);
                }
                throw new IOException("Shizuku respondió, pero no devolvió sesiones de audio"
                        + (detail.isEmpty() ? "" : ": " + detail));
            }
            return output;
        } finally {
            closeQuietly(stdout);
            closeQuietly(stderr);
            try {
                process.destroy();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Tries to obtain the {@code AudioProductStrategy} id for
     * {@code USAGE_ASSISTANCE_ACCESSIBILITY} by running reflection inside the
     * Shizuku shell process (uid 2000 / system API access).
     *
     * @return the strategy id, or {@code null} if it could not be determined
     * @throws UnavailableException when Shizuku is not running
     * @throws PermissionException  when the user has not authorized the app
     */
    @Nullable
    public static Integer getAccessibilityStrategyId(@NonNull Context context) throws Exception {
        if (!isAvailable() || !hasPermission()) {
            return null;
        }

        String apkPath;
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), 0);
            apkPath = info.sourceDir;
        } catch (PackageManager.NameNotFoundException error) {
            return null;
        }

        String[] command = {
                "/system/bin/app_process",
                "/system/bin",
                "--nice-name=audio",
                "-cp", apkPath,
                "com.angelonmx.megaequalizer.audio.AudioStrategyHelper"
        };

        String output = runShellCommand(command, false);
        if (output == null || output.isEmpty()) {
            return null;
        }
        output = output.trim();
        try {
            int id = Integer.parseInt(output);
            return id >= 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }

    private static final class ProcessReader implements Runnable {
        private final ParcelFileDescriptor descriptor;
        private volatile String output;
        private volatile Throwable failure;

        private ProcessReader(ParcelFileDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public void run() {
            try (InputStream input = new FileInputStream(descriptor.getFileDescriptor());
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream(32768)) {
                byte[] buffer = new byte[16384];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_OUTPUT_BYTES) {
                        throw new IOException("La salida de Shizuku es demasiado grande");
                    }
                    bytes.write(buffer, 0, read);
                }
                output = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            } catch (Throwable error) {
                failure = error;
            }
        }
    }

    /** Shizuku is installed but its service is not running. */
    public static final class UnavailableException extends IOException {
        public UnavailableException(String message) {
            super(message);
        }
    }

    /** Shizuku is running but the app has not been authorized. */
    public static final class PermissionException extends IOException {
        public PermissionException(String message) {
            super(message);
        }
    }
}
