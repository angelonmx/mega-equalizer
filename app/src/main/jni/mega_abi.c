#include <jni.h>

/*
 * Small native library that makes the per-architecture APKs meaningful and
 * lets the updater confirm the ABI of the running build. The actual audio
 * processing uses Android's built-in DynamicsProcessing / Equalizer effects.
 */
JNIEXPORT jstring JNICALL
Java_com_angelonmx_megaequalizer_util_NativeAbi_nativeAbi(JNIEnv *env, jclass clazz) {
    (void) clazz;
#if defined(__aarch64__)
    return (*env)->NewStringUTF(env, "arm64-v8a");
#elif defined(__arm__)
    return (*env)->NewStringUTF(env, "armeabi-v7a");
#elif defined(__x86_64__)
    return (*env)->NewStringUTF(env, "x86_64");
#elif defined(__i386__)
    return (*env)->NewStringUTF(env, "x86");
#else
    return (*env)->NewStringUTF(env, "unknown");
#endif
}
