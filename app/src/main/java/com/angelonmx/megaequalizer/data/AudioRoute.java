package com.angelonmx.megaequalizer.data;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.text.TextUtils;

import com.angelonmx.megaequalizer.R;

import java.util.Locale;

/**
 * A concrete audio output route. Every physical output gets its own stable
 * target key, so a preset can follow a headset, a USB DAC or a Bluetooth
 * speaker across disconnections and reconnections. The category key is kept
 * as the fallback for outputs that cannot be identified individually.
 */
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
     * Key used in the assignment map. Bluetooth keeps its historical keys so
     * assignments created by earlier versions keep working. Every other output
     * uses a type plus a stable identity derived from its address or name.
     * If no identity is available the category key is returned.
     */
    public String targetKey() {
        if (hasBluetoothAddress()) {
            return "bt:" + address.toUpperCase(Locale.ROOT);
        }
        if (isBluetooth() && !"Audio".equals(name)) {
            return "bt:name:" + name;
        }
        String identity = deviceIdentity();
        if (!TextUtils.isEmpty(identity)) {
            return "dev:" + type + ":" + identity;
        }
        return category.targetKey();
    }

    public String categoryKey() {
        return category.targetKey();
    }

    /** True when this route can be stored as an individual output. */
    public boolean hasSpecificKey() {
        return isSpecificTarget(targetKey());
    }

    public static boolean isSpecificTarget(String targetKey) {
        return !TextUtils.isEmpty(targetKey)
                && (targetKey.startsWith("bt:") || targetKey.startsWith("dev:"));
    }

    /**
     * Output types that are internal routing nodes or telephony paths, not
     * assignable physical outputs. They must never appear as devices.
     */
    public static boolean isVirtualOutputType(int type) {
        return type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX
                || type == AudioDeviceInfo.TYPE_FM_TUNER
                || type == AudioDeviceInfo.TYPE_TELEPHONY;
    }

    public String displayName() {
        return name;
    }

    /**
     * Display name that includes the exact output type. This keeps built-in
     * speaker and earpiece (which usually share the same product name)
     * distinguishable for sighted users and TalkBack.
     */
    public String displayName(Context context) {
        String typeLabel = typeLabel(context);
        if (TextUtils.isEmpty(typeLabel)
                || name.toLowerCase(Locale.ROOT)
                .contains(typeLabel.toLowerCase(Locale.ROOT))) {
            return name;
        }
        return context.getString(R.string.device_name_with_type_format, name, typeLabel);
    }

    public String typeLabel(Context context) {
        int labelRes = typeLabelRes();
        if (labelRes == 0) {
            labelRes = category.labelRes;
        }
        return context.getString(labelRes);
    }

    private int typeLabelRes() {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return R.string.output_type_speaker;
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return R.string.output_type_earpiece;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return R.string.output_type_wired_headset;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return R.string.output_type_wired_headphones;
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return R.string.output_type_line_analog;
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return R.string.output_type_line_digital;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return R.string.output_type_bluetooth;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return R.string.output_type_bluetooth_sco;
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return R.string.output_type_ble;
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return R.string.output_type_ble_speaker;
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return R.string.output_type_ble;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return R.string.output_type_usb;
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return R.string.output_type_usb_headset;
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return R.string.output_type_usb_accessory;
            case AudioDeviceInfo.TYPE_HDMI:
                return R.string.output_type_hdmi;
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return R.string.output_type_hdmi_arc;
            case AudioDeviceInfo.TYPE_HDMI_EARC:
                return R.string.output_type_hdmi_earc;
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return R.string.output_type_aux;
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return R.string.output_type_hearing_aid;
            case AudioDeviceInfo.TYPE_DOCK:
            case AudioDeviceInfo.TYPE_DOCK_ANALOG:
                return R.string.output_type_dock;
            case AudioDeviceInfo.TYPE_IP:
                return R.string.output_type_ip;
            case AudioDeviceInfo.TYPE_BUS:
                return R.string.output_type_bus;
            case AudioDeviceInfo.TYPE_FM:
                return R.string.output_type_fm;
            default:
                return R.string.output_type_other;
        }
    }

    private String deviceIdentity() {
        String source = address;
        if (TextUtils.isEmpty(source)) {
            source = name;
        }
        if (TextUtils.isEmpty(source) || "Audio".equals(source.trim())) {
            return "";
        }
        String normalized = source.trim()
                .toLowerCase(Locale.ROOT)
                .replace(':', '_')
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("\\s+", "_");
        return normalized.isEmpty() ? "" : normalized;
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
