package com.angelonmx.megaequalizer.data;

import android.media.AudioDeviceInfo;

import java.util.Locale;

/** A concrete audio output route, including optional Bluetooth information. */
public final class AudioRoute {

    public final int type;
    public final DeviceCategory category;
    public final String name;
    public final String address;
    public final boolean connected;

    public AudioRoute(int type, DeviceCategory category, String name, String address, boolean connected) {
        this.type = type;
        this.category = category == null ? DeviceCategory.OTHER : category;
        this.name = name == null || name.trim().isEmpty() ? "Audio" : name.trim();
        this.address = address == null || address.trim().isEmpty() ? null : address.trim();
        this.connected = connected;
    }

    public static AudioRoute fallbackSpeaker() {
        return new AudioRoute(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                DeviceCategory.SPEAKER,
                "Altavoz del teléfono",
                null,
                true
        );
    }

    public boolean isBluetooth() {
        return category == DeviceCategory.BLUETOOTH;
    }

    public boolean hasBluetoothAddress() {
        return isBluetooth() && address != null;
    }

    /**
     * Key used in the assignment map. Specific Bluetooth devices win over the
     * generic Bluetooth category. Devices without a readable address fall back
     * to their advertised name so they can still be assigned.
     */
    public String targetKey() {
        if (hasBluetoothAddress()) {
            return "bt:" + address.toUpperCase(Locale.ROOT);
        }
        if (isBluetooth() && !"Audio".equals(name)) {
            return "bt:name:" + name;
        }
        return category.targetKey();
    }

    public String categoryKey() {
        return category.targetKey();
    }

    public String displayName() {
        return name;
    }

    @Override
    public String toString() {
        return "AudioRoute{" +
                "category=" + category +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", connected=" + connected +
                '}';
    }
}
