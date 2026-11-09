package com.angelonmx.megaequalizer.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.angelonmx.megaequalizer.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/** Header plus installed-app list for the per-app profile screen. */
public class AppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final class AppEntry {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        public AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    public interface Listener {
        void onAccessibilitySettings();

        void onPerAppChanged(boolean enabled);

        void onSearchChanged(String query);

        void onAppClicked(AppEntry entry);

        String behaviorFor(String packageName);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_APP = 1;

    private final Context context;
    private final Listener listener;
    private final List<AppEntry> apps = new ArrayList<>();
    private boolean accessibilityEnabled;
    private boolean perAppEnabled;
    private boolean showEmpty;
    private boolean bindingSearch;
    private String query = "";
    private int emptyTextRes = R.string.apps_empty;

    public AppsAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setApps(List<AppEntry> values) {
        apps.clear();
        if (values != null) {
            apps.addAll(values);
        }
        notifyDataSetChanged();
    }

    public void setState(boolean accessibilityEnabled, boolean perAppEnabled) {
        this.accessibilityEnabled = accessibilityEnabled;
        this.perAppEnabled = perAppEnabled;
        notifyItemChanged(0);
    }

    public void setEmptyState(boolean showEmpty, int textRes) {
        this.showEmpty = showEmpty;
        this.emptyTextRes = textRes;
        notifyItemChanged(0);
    }

    public void setQuery(String value) {
        query = value == null ? "" : value;
    }

    public int appCount() {
        return apps.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.view_apps_header, parent, false);
            return new HeaderHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind();
        } else {
            AppHolder appHolder = (AppHolder) holder;
            AppEntry entry = apps.get(position - 1);
            appHolder.icon.setImageDrawable(entry.icon);
            appHolder.label.setText(entry.label);
            String behavior = listener.behaviorFor(entry.packageName);
            appHolder.behavior.setText(behavior);
            appHolder.itemView.setContentDescription(entry.label + ". " + behavior);
            appHolder.itemView.setOnClickListener(view -> listener.onAppClicked(entry));
        }
    }

    @Override
    public int getItemCount() {
        return 1 + apps.size();
    }

    private final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView accessibilityStatus;
        final MaterialButton accessibilityButton;
        final MaterialSwitch perAppSwitch;
        final TextView perAppDisabled;
        final TextInputEditText search;
        final TextView noApps;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            accessibilityStatus = itemView.findViewById(R.id.textAccessibilityStatus);
            accessibilityButton = itemView.findViewById(R.id.buttonAccessibility);
            perAppSwitch = itemView.findViewById(R.id.switchPerApp);
            perAppDisabled = itemView.findViewById(R.id.textPerAppDisabled);
            search = itemView.findViewById(R.id.editSearch);
            noApps = itemView.findViewById(R.id.textNoApps);

            accessibilityButton.setOnClickListener(view ->
                    listener.onAccessibilitySettings());
            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!bindingSearch) {
                        listener.onSearchChanged(s == null ? "" : s.toString());
                    }
                }
            });
        }

        void bind() {
            accessibilityStatus.setText(accessibilityEnabled
                    ? R.string.accessibility_active
                    : R.string.accessibility_inactive);
            perAppSwitch.setOnCheckedChangeListener(null);
            perAppSwitch.setChecked(accessibilityEnabled && perAppEnabled);
            perAppSwitch.setEnabled(accessibilityEnabled);
            perAppSwitch.setOnCheckedChangeListener((button, checked) ->
                    listener.onPerAppChanged(checked));
            perAppDisabled.setVisibility(accessibilityEnabled ? View.GONE : View.VISIBLE);
            noApps.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
            noApps.setText(emptyTextRes);
            bindingSearch = true;
            if (!TextUtils.equals(search.getText(), query)) {
                search.setText(query);
                search.setSelection(search.getText() == null ? 0 : search.getText().length());
            }
            bindingSearch = false;
        }
    }

    static final class AppHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView behavior;

        AppHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imageAppIcon);
            label = itemView.findViewById(R.id.textAppLabel);
            behavior = itemView.findViewById(R.id.textAppBehavior);
        }
    }
}
