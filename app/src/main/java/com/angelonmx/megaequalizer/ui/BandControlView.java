package com.angelonmx.megaequalizer.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.angelonmx.megaequalizer.R;
import com.google.android.material.button.MaterialButton;

/** One labeled gain row with buttons and an accessible seek bar. */
public class BandControlView extends LinearLayout {

    private final TextView labelView;
    private final TextView valueView;
    private final GainSeekBar seekBar;
    private final String accessibleLabel;

    public BandControlView(Context context, String label, String accessibleLabel, float initialGain) {
        super(context);
        this.accessibleLabel = accessibleLabel;
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_band_slider, this, true);

        labelView = findViewById(R.id.textBandLabel);
        valueView = findViewById(R.id.textBandValue);
        seekBar = findViewById(R.id.seekBand);
        MaterialButton decrease = findViewById(R.id.buttonDecrease);
        MaterialButton increase = findViewById(R.id.buttonIncrease);

        labelView.setText(label);
        seekBar.setLabel(accessibleLabel);
        seekBar.setGain(initialGain, false);
        updateValue();

        decrease.setContentDescription(
                context.getString(R.string.decrease_gain_format, accessibleLabel)
        );
        increase.setContentDescription(
                context.getString(R.string.increase_gain_format, accessibleLabel)
        );

        decrease.setOnClickListener(v -> seekBar.changeGainBy(-1f));
        increase.setOnClickListener(v -> seekBar.changeGainBy(1f));

        seekBar.setOnGainChangedListener((gain, fromUser) -> {
            updateValue();
            if (gainListener != null) {
                gainListener.onGainChanged(gain, fromUser);
            }
        });
    }

    public interface OnBandGainChangedListener {
        void onGainChanged(float gain, boolean fromUser);
    }

    private OnBandGainChangedListener gainListener;

    public void setOnGainChangedListener(OnBandGainChangedListener listener) {
        gainListener = listener;
    }

    public float getGain() {
        return seekBar.getGain();
    }

    public void setGain(float gain) {
        seekBar.setGain(gain, true);
        updateValue();
    }

    private void updateValue() {
        int rounded = Math.round(seekBar.getGain());
        String visible;
        String accessible;
        if (rounded == 0) {
            visible = getContext().getString(R.string.gain_value_zero);
            accessible = getContext().getString(R.string.gain_accessible_zero);
        } else if (rounded > 0) {
            visible = getContext().getString(R.string.gain_value_positive, rounded);
            accessible = getContext().getString(R.string.gain_accessible_positive, rounded);
        } else {
            visible = getContext().getString(R.string.gain_value_negative, Math.abs(rounded));
            accessible = getContext().getString(R.string.gain_accessible_negative, Math.abs(rounded));
        }
        valueView.setText(visible);
        valueView.setContentDescription(accessible);
        labelView.setContentDescription(
                getContext().getString(R.string.gain_slider_description_format, accessibleLabel)
        );
    }
}
