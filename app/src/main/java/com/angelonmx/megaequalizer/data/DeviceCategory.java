package com.angelonmx.megaequalizer.data;

import android.media.AudioDeviceInfo;

import com.angelonmx.megaequalizer.R;

import java.util.Locale;

/**
 * Logical output categories used for assignments. Individual Bluetooth
 * devices are handled separately with their own target key.
 */
public enum DeviceCategory {
    SPEAKER("speaker", R.string.device_speaker),
    WIRED("wired", R.string.device_wired),
    BLUETOOTH("bluetooth", R.string.device_bluetooth),
    USB("usb", R.string.device_usb),
    AUX("aux", R.string.device_aux),
    HDMI("hdmi", R.string.device_hdmi),
    OTHER("other", R.string.device_other),
    ALL("all", R.string.device_all);

    public final String key;
    public final int labelRes;

    DeviceCategory(String key, int labelRes) {
        this.key = key;
        this.labelRes = labelRes;
    }

    public String targetKey() {
        return "type:" + key;
    }

    public static DeviceCategory fromKey(String key) {
        for (DeviceCategory category : values()) {
            if (category.key.equals(key)) {
                return category;
            }
        }
        return OTHER;
    }

    public static DeviceCategory fromAudioDeviceType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return SPEAKER;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return WIRED;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return BLUETOOTH;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return USB;
            case AudioDeviceInfo.TYPE_AUX_LINE:
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return AUX;
            case AudioDeviceInfo.TYPE_HDMI:
            case AudioDeviceInfo.TYPE_HDMI_ARC:
            case AudioDeviceInfo.TYPE_HDMI_EARC:
                return HDMI;
            default:
                return OTHER;
        }
    }

    public static int priority(int type) {
        switch (fromAudioDeviceType(type)) {
            case WIRED:
                return 0;
            case USB:
                return 1;
            case BLUETOOTH:
                return 2;
            case AUX:
                return 3;
            case HDMI:
                return 4;
            case SPEAKER:
                return 5;
            case OTHER:
                return 6;
            case ALL:
            default:
                return 7;
        }
    }

    public String keyForStorage() {
        return key.toLowerCase(Locale.ROOT);
    }
}
