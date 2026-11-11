package com.angelonmx.megaequalizer.audio;

import android.media.AudioAttributes;

/**
 * Helper executed inside the Shizuku shell process (uid 2000, system API
 * access) to obtain the {@code AudioProductStrategy} id for
 * {@code AudioAttributes#USAGE_ASSISTANCE_ACCESSIBILITY}.
 *
 * <p>Run with:</p>
 * <pre>
 * app_process /system/bin --nice-name=audio -cp &lt;apk&gt; \
 *     com.angelonmx.megaequalizer.audio.AudioStrategyHelper
 * </pre>
 *
 * <p>Prints the strategy id to stdout, or {@code -1} if it cannot be
 * determined.</p>
 */
public final class AudioStrategyHelper {

    private AudioStrategyHelper() {
    }

    /**
     * Reflects over {@code android.media.audiopolicy.AudioProductStrategy} to
     * find the strategy that supports accessibility usage attributes.
     *
     * @return the accessibility strategy id, or {@code -1} on failure
     */
    public static int getAccessibilityStrategyId() {
        try {
            Class<?> strategyClass = Class.forName(
                    "android.media.audiopolicy.AudioProductStrategy");
            java.lang.reflect.Method getStrategies =
                    strategyClass.getMethod("getAudioProductStrategies");
            Object strategies = getStrategies.invoke(null);
            if (!(strategies instanceof java.util.List)) {
                return -1;
            }
            java.util.List<?> list = (java.util.List<?>) strategies;

            Class<?> attrClass = Class.forName("android.media.AudioAttributes");
            Class<?> builderClass = Class.forName(
                    "android.media.AudioAttributes$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            builder.getClass().getMethod("setUsage", int.class)
                    .invoke(builder, AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY);
            Object attrs = builder.getClass().getMethod("build").invoke(builder);

            java.lang.reflect.Method supports =
                    strategyClass.getMethod("supportsAudioAttributes", attrClass);
            java.lang.reflect.Method getId =
                    strategyClass.getMethod("getId");

            for (Object strategy : list) {
                Boolean supported = (Boolean) supports.invoke(strategy, attrs);
                if (Boolean.TRUE.equals(supported)) {
                    return (Integer) getId.invoke(strategy);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static void main(String[] args) {
        System.out.println(getAccessibilityStrategyId());
    }
}
