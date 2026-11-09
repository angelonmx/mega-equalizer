package com.angelonmx.megaequalizer;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.angelonmx.megaequalizer.data.PresetRepository;

public class MegaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        applySavedTheme(this);
    }

    /** Applies the saved system/light/dark theme to the whole process. */
    public static void applySavedTheme(android.content.Context context) {
        String mode = PresetRepository.get(context).getThemeMode();
        int nightMode;
        if (PresetRepository.THEME_LIGHT.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (PresetRepository.THEME_DARK.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }
}
