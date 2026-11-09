package com.angelonmx.megaequalizer.util;

public final class NativeAbi {

    private static String cached;

    private NativeAbi() {
    }

    /**
     * Returns the ABI of the installed native library. If the library is not
     * present (for example, an experimental x86_64 debug build), it falls back
     * to the first ABI reported by the platform.
     */
    public static synchronized String get() {
        if (cached != null) {
            return cached;
        }
        try {
            cached = nativeAbi();
        } catch (UnsatisfiedLinkError error) {
            String[] supported = android.os.Build.SUPPORTED_ABIS;
            cached = supported.length > 0 ? supported[0] : "universal";
        }
        return cached;
    }

    private static native String nativeAbi();
}
