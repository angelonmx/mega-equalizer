package com.angelonmx.megaequalizer.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.angelonmx.megaequalizer.R;
import com.angelonmx.megaequalizer.data.Preset;
import com.angelonmx.megaequalizer.data.PresetRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Accessible list of presets with edit and overflow actions. */
public class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.Holder> {

    public interface Listener {
        void onEdit(Preset preset);

        void onMore(Preset preset, View anchor);
    }

    private final Context context;
    private final PresetRepository repository;
    private final Listener listener;
    private final List<Preset> presets = new ArrayList<>();

    public PresetAdapter(Context context, Listener listener) {
        this.context = context;
        this.repository = PresetRepository.get(context);
        this.listener = listener;
    }

    public void setPresets(List<Preset> values) {
        presets.clear();
        if (values != null) {
            presets.addAll(values);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_preset, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Preset preset = presets.get(position);
        holder.name.setText(preset.name);
        String preamp = gainText(context, preset.preamp);
        holder.summary.setText(context.getString(
                R.string.preset_summary_format,
                preamp,
                Preset.BAND_COUNT
        ));
        List<String> labels = new ArrayList<>();
        for (String target : repository.getTargetsForPreset(preset.id)) {
            labels.add(repository.getTargetLabel(target));
        }
        if (labels.isEmpty()) {
            holder.devices.setText(R.string.preset_unassigned);
        } else {
            holder.devices.setText(context.getString(
                    R.string.preset_devices_format,
                    TextUtils.join(", ", labels)
            ));
        }
        holder.edit.setContentDescription(context.getString(
                R.string.edit_preset_description_format,
                preset.name
        ));
        holder.more.setContentDescription(context.getString(
                R.string.more_options,
                preset.name
        ));
        holder.card.setContentDescription(preset.name + ". "
                + holder.summary.getText() + ". " + holder.devices.getText());
        holder.card.setOnClickListener(view -> listener.onEdit(preset));
        holder.edit.setOnClickListener(view -> listener.onEdit(preset));
        holder.more.setOnClickListener(view -> listener.onMore(preset, view));
    }

    @Override
    public int getItemCount() {
        return presets.size();
    }

    public static String gainText(Context context, float gain) {
        int rounded = Math.round(gain);
        if (rounded == 0) {
            return context.getString(R.string.gain_value_zero);
        }
        if (rounded > 0) {
            return context.getString(R.string.gain_value_positive, rounded);
        }
        return context.getString(R.string.gain_value_negative, Math.abs(rounded));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView name;
        final TextView summary;
        final TextView devices;
        final MaterialButton edit;
        final MaterialButton more;

        Holder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            name = itemView.findViewById(R.id.textPresetName);
            summary = itemView.findViewById(R.id.textPresetSummary);
            devices = itemView.findViewById(R.id.textPresetDevices);
            edit = itemView.findViewById(R.id.buttonEdit);
            more = itemView.findViewById(R.id.buttonMore);
        }
    }
}
