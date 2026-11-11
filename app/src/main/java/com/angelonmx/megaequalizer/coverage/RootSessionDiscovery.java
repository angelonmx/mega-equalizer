package com.angelonmx.megaequalizer.coverage;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Optional root backend. It runs the same {@code dumpsys audio} command
 * through {@code su}, so no extra daemon or app is required on rooted devices.
 */
public final class RootSessionDiscovery {

    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;
    private static final int TIMEOUT_SECONDS = 15;

    @NonNull
    public String readDumpsysAudio() throws Exception {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", "dumpsys audio")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("root no respondió a tiempo");
            }
            String output;
            try (InputStream input = process.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream(32768)) {
                byte[] buffer = new byte[16384];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_OUTPUT_BYTES) {
                        throw new IOException("La salida de root es demasiado grande");
                    }
                    bytes.write(buffer, 0, read);
                }
                output = bytes.toString("UTF-8");
            }
            if (process.exitValue() != 0 && !output.contains("AudioPlaybackConfiguration")) {
                throw new IOException("root rechazó el comando");
            }
            if (!output.contains("AudioPlaybackConfiguration")) {
                throw new IOException("root no devolvió sesiones de audio");
            }
            return output;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
