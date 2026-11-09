package com.angelonmx.megaequalizer.audio;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.angelonmx.megaequalizer.R;
import com.angelonmx.megaequalizer.data.AudioRoute;
import com.angelonmx.megaequalizer.data.DeviceCategory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Detects connected outputs and exposes Bluetooth devices for assignments. */
public final class AudioRouteManager {

    private AudioRouteManager() {
    }

    public static boolean hasBluetoothPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static AudioRoute getCurrentRoute(Context context) {
        List<AudioRoute> routes = listConnectedOutputs(context);
        AudioRoute best = null;
        int bestScore = Integer.MAX_VALUE;
        for (AudioRoute route : routes) {
            int score = DeviceCategory.priority(route.type) * 100 + typeScore(route.type);
            if (score < bestScore) {
                bestScore = score;
                best = route;
            }
        }
        return best == null ? AudioRoute.fallbackSpeaker() : best;
    }

    public static List<AudioRoute> listConnectedOutputs(Context context) {
        List<AudioRoute> result = new ArrayList<>();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            try {
                for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (device == null) {
                        continue;
                    }
                    DeviceCategory category = DeviceCategory.fromAudioDeviceType(device.getType());
                    if (device.getType() == AudioDeviceInfo.TYPE_REMOTE_SUBMIX
                            || device.getType() == AudioDeviceInfo.TYPE_FM_TUNER) {
                        continue;
                    }
                    String name = readProductName(device);
                    String address = readAddress(device);
                    if (category == DeviceCategory.BLUETOOTH && TextUtils.isEmpty(name)) {
                        name = bluetoothName(address);
                    }
                    if (TextUtils.isEmpty(name)) {
                        name = context.getString(category.labelRes);
                    }
                    result.add(new AudioRoute(device.getType(), category, name, address, true));
                }
            } catch (RuntimeException error) {
                // Some vendor implementations throw when querying audio devices.
            }
        }
        if (result.isEmpty()) {
            result.add(AudioRoute.fallbackSpeaker());
        }
        return result;
    }

    /**
     * Returns connected and bonded Bluetooth devices. Connected devices are
     * listed first. Needs BLUETOOTH_CONNECT to read bonded devices on Android
     * 12 and newer; connected outputs can still be read without it.
     */
    public static List<AudioRoute> listBluetoothDevices(Context context) {
        Map<String, AudioRoute> byKey = new LinkedHashMap<>();
        for (AudioRoute route : listConnectedOutputs(context)) {
            if (route.category == DeviceCategory.BLUETOOTH) {
                byKey.put(route.targetKey(), route);
            }
        }

        if (hasBluetoothPermission(context)) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    if (adapter.isEnabled()) {
                        Set<String> knownAddresses = new HashSet<>();
                        for (AudioRoute route : byKey.values()) {
                            if (route.address != null) {
                                knownAddresses.add(route.address.toUpperCase(Locale.ROOT));
                            }
                        }
                        for (BluetoothDevice device : adapter.getBondedDevices()) {
                            String address = device.getAddress();
                            if (address == null || knownAddresses.contains(address.toUpperCase(Locale.ROOT))) {
                                continue;
                            }
                            String name;
                            try {
                                name = device.getName();
                            } catch (SecurityException error) {
                                name = null;
                            }
                            if (TextUtils.isEmpty(name)) {
                                name = context.getString(R.string.device_bluetooth);
                            }
                            AudioRoute route = new AudioRoute(
                                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                                    DeviceCategory.BLUETOOTH,
                                    name,
                                    address,
                                    false
                            );
                            byKey.put(route.targetKey(), route);
                        }
                    }
                }
            } catch (SecurityException ignored) {
                // Permission can be revoked between the check and the call.
            } catch (RuntimeException ignored) {
                // Bluetooth may be unavailable on this device.
            }
        }

        List<AudioRoute> result = new ArrayList<>(byKey.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(result, (left, right) -> {
            if (left.connected != right.connected) {
                return left.connected ? -1 : 1;
            }
            return collator.compare(left.name, right.name);
        });
        return result;
    }

    public static String describeBluetoothRoute(Context context, AudioRoute route, boolean connected) {
        return context.getString(
                connected ? R.string.bluetooth_connected_format : R.string.bluetooth_bonded_format,
                route.name
        );
    }

    private static String readProductName(AudioDeviceInfo device) {
        try {
            CharSequence productName = device.getProductName();
            return productName == null ? "" : productName.toString().trim();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static String readAddress(AudioDeviceInfo device) {
        try {
            return device.getAddress();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String bluetoothName(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                BluetoothDevice device = adapter.getRemoteDevice(address);
                String name = device.getName();
                return name == null ? "" : name;
            }
        } catch (SecurityException ignored) {
            // Reading the name requires BLUETOOTH_CONNECT on Android 12+.
        } catch (RuntimeException ignored) {
            // Invalid or unavailable address.
        }
        return "";
    }

    private static int typeScore(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return 0;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return 1;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return 2;
            default:
                return 3;
        }
    }
}
