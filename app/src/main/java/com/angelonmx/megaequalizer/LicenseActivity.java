package com.angelonmx.megaequalizer;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shows the complete GNU GPL v3 text bundled in the APK assets. */
public class LicenseActivity extends BaseActivity {

    private static final String LICENSE_ASSET = "gpl-3.0.txt";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        setupBackToolbar();

        TextView textLicense = findViewById(R.id.textLicense);
        textLicense.setText(readLicense());
    }

    private String readLicense() {
        try (InputStream input = getAssets().open(LICENSE_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return getString(R.string.license_text);
        }
    }
}
