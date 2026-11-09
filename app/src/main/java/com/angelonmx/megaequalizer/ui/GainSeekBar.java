package com.angelonmx.megaequalizer.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.angelonmx.megaequalizer.R;

import java.util.Locale;

/**
 * SeekBar with decibel semantics and explicit accessibility actions. Screen
 * readers hear the actual gain instead of an internal 0-24 slider position.
 */
public class GainSeekBar extends AppCompatSeekBar {

    public interface OnGainChangedListener {
        void onGainChanged(float gain, boolean fromUser);
    }

    private static final int ACTION_GAIN_UP = 0x01020001;
    private static final int ACTION_GAIN_DOWN = 0x01020002;

    private final float minDb = -12f;
    private final float maxDb = 12f;
    private final float stepDb = 1f;
    private String label = "";
    private boolean internalChange;
    @Nullable
    private OnGainChangedListener gainListener;

    public GainSeekBar(Context context) {
        super(context);
        initialize();
    }

    public GainSeekBar(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public GainSeekBar(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setMax(Math.round((maxDb - minDb) / stepDb));
        setProgress(progressForGain(0f));
        setSplitTrack(false);
        super.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateAccessibilityDescription();
                if (!internalChange && gainListener != null) {
                    gainListener.onGainChanged(getGain(), fromUser);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        updateAccessibilityDescription();
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        updateAccessibilityDescription();
    }

    public void setOnGainChangedListener(@Nullable OnGainChangedListener listener) {
        gainListener = listener;
    }

    public float getGain() {
        return minDb + getProgress() * stepDb;
    }

    public void setGain(float gain) {
        setGain(gain, true);
    }

    public void setGain(float gain, boolean notify) {
        float clamped = Math.max(minDb, Math.min(maxDb, gain));
        internalChange = true;
        setProgress(progressForGain(clamped));
        internalChange = false;
        updateAccessibilityDescription();
        if (notify && gainListener != null) {
            gainListener.onGainChanged(getGain(), false);
        }
    }

    public void changeGainBy(float deltaDb) {
        float next = getGain() + deltaDb;
        if (next > maxDb) {
            next = maxDb;
        }
        if (next < minDb) {
            next = minDb;
        }
        setGain(next, true);
        announceForAccessibility(getAccessibilityDescription());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                minDb,
                maxDb,
                getGain()
        ));
        info.setContentDescription(getAccessibilityDescription());
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_GAIN_UP,
                getContext().getString(R.string.gain_action_up)
        ));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                ACTION_GAIN_DOWN,
                getContext().getString(R.string.gain_action_down)
        ));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == ACTION_GAIN_UP || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            changeGainBy(stepDb);
            return true;
        }
        if (action == ACTION_GAIN_DOWN || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            changeGainBy(-stepDb);
            return true;
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
                && arguments != null) {
            float value = arguments.getFloat(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    getGain()
            );
            setGain(value);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private int progressForGain(float gain) {
        return Math.round((gain - minDb) / stepDb);
    }

    private void updateAccessibilityDescription() {
        setContentDescription(getAccessibilityDescription());
    }

    private String getAccessibilityDescription() {
        String gainText;
        int rounded = Math.round(getGain());
        if (rounded == 0) {
            gainText = getContext().getString(R.string.gain_accessible_zero);
        } else if (rounded > 0) {
            gainText = getContext().getString(R.string.gain_accessible_positive, rounded);
        } else {
            gainText = getContext().getString(R.string.gain_accessible_negative, Math.abs(rounded));
        }
        String fullLabel = getContext().getString(R.string.gain_slider_description_format, label);
        return getContext().getString(R.string.gain_slider_full_description, fullLabel, gainText);
    }

    @SuppressWarnings("unused")
    private static String normalizeNumber(float value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }
}
