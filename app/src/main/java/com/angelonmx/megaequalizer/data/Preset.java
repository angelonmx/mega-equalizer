package com.angelonmx.megaequalizer.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * A ten-band preset. Gains are expressed in decibels and stored for the
 * frequencies in {@link #FREQUENCIES}.
 */
public final class Preset {

    public static final int BAND_COUNT = 10;
    public static final float MIN_GAIN = -12f;
    public static final float MAX_GAIN = 12f;
    public static final float[] FREQUENCIES = {
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    };

    public String id;
    public String name;
    public float[] gains;
    public float preamp;
    public long updatedAt;

    public Preset() {
        this(null, "", new float[BAND_COUNT], 0f);
    }

    public Preset(String id, String name, float[] gains, float preamp) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.gains = new float[BAND_COUNT];
        if (gains != null) {
            System.arraycopy(gains, 0, this.gains, 0, Math.min(gains.length, BAND_COUNT));
        }
        this.preamp = clamp(preamp);
        this.updatedAt = System.currentTimeMillis();
    }

    public Preset copy() {
        Preset copy = new Preset(id, name, gains, preamp);
        copy.updatedAt = updatedAt;
        return copy;
    }

    public static float clamp(float value) {
        return Math.max(MIN_GAIN, Math.min(MAX_GAIN, value));
    }

    /**
     * Returns a linearly interpolated gain for an arbitrary frequency. This is
     * used for devices that only expose a fixed five-band system equalizer.
     */
    public float gainAtFrequency(double frequency) {
        if (frequency <= FREQUENCIES[0]) {
            return gains[0];
        }
        if (frequency >= FREQUENCIES[FREQUENCIES.length - 1]) {
            return gains[FREQUENCIES.length - 1];
        }
        double target = Math.log10(frequency);
        for (int i = 1; i < FREQUENCIES.length; i++) {
            double previous = Math.log10(FREQUENCIES[i - 1]);
            double current = Math.log10(FREQUENCIES[i]);
            if (target <= current) {
                float ratio = (float) ((target - previous) / (current - previous));
                return gains[i - 1] + (gains[i] - gains[i - 1]) * ratio;
            }
        }
        return gains[FREQUENCIES.length - 1];
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("preamp", preamp);
        object.put("updatedAt", updatedAt);
        JSONArray gainArray = new JSONArray();
        for (float gain : gains) {
            gainArray.put(gain);
        }
        object.put("gains", gainArray);
        return object;
    }

    public static Preset fromJson(JSONObject object) throws JSONException {
        float[] gains = new float[BAND_COUNT];
        JSONArray gainArray = object.optJSONArray("gains");
        if (gainArray != null) {
            for (int i = 0; i < Math.min(gainArray.length(), BAND_COUNT); i++) {
                gains[i] = clamp((float) gainArray.optDouble(i, 0d));
            }
        }
        Preset preset = new Preset(
                object.optString("id", null),
                object.optString("name", ""),
                gains,
                (float) object.optDouble("preamp", 0d)
        );
        preset.updatedAt = object.optLong("updatedAt", System.currentTimeMillis());
        return preset;
    }

    public boolean isFlat() {
        for (float gain : gains) {
            if (Math.abs(gain) > 0.01f) {
                return false;
            }
        }
        return Math.abs(preamp) <= 0.01f;
    }

    @Override
    public String toString() {
        return "Preset{" +
                "name='" + name + '\'' +
                ", preamp=" + preamp +
                ", gains=" + Arrays.toString(gains) +
                '}';
    }
}
