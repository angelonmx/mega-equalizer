package com.angelonmx.megaequalizer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Small helpers for verifying downloaded release files. */
public final class HashUtils {

    private HashUtils() {
    }

    public static String sha256(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try (InputStream stream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] value = digest.digest();
            StringBuilder builder = new StringBuilder(value.length * 2);
            for (byte item : value) {
                builder.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception error) {
            return "";
        }
    }

    public static boolean matches(File file, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            return true;
        }
        String actual = sha256(file);
        return !actual.isEmpty() && actual.equalsIgnoreCase(expectedSha256.trim());
    }
}
