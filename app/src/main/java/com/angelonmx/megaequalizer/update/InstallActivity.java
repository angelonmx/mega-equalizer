package com.angelonmx.megaequalizer.update;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.angelonmx.megaequalizer.R;
import com.angelonmx.megaequalizer.util.AccessibilityUtils;
import com.angelonmx.megaequalizer.util.HashUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

/**
 * Invisible trampoline used by the update notification and by the in-app
 * prompt. It verifies the APK again, asks for the unknown-sources permission
 * when needed, and hands the file to the system package installer.
 */
public class InstallActivity extends AppCompatActivity {

    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String STATE_WAITING_PERMISSION = "waiting_permission";

    private String fileName;
    private String version;
    private boolean waitingForPermission;
    private boolean verifying;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fileName = UpdateChecker.sanitizeFileName(
                getIntent().getStringExtra(UpdateChecker.EXTRA_INSTALL_FILE)
        );
        version = getIntent().getStringExtra(UpdateChecker.EXTRA_INSTALL_VERSION);
        if (TextUtils.isEmpty(version)) {
            version = UpdateChecker.pending(this).getString(UpdateChecker.KEY_READY_VERSION, "");
        }
        if (savedInstanceState != null) {
            waitingForPermission = savedInstanceState.getBoolean(STATE_WAITING_PERMISSION, false);
        }
        if (TextUtils.isEmpty(fileName)) {
            fail(R.string.update_install_failed);
            return;
        }
        verifyAndContinue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPermission && !verifying) {
            waitingForPermission = false;
            if (AccessibilityUtils.canRequestPackageInstalls(this)) {
                continueInstall();
            } else {
                Toast.makeText(
                        this,
                        R.string.update_install_permission_still_missing,
                        Toast.LENGTH_LONG
                ).show();
                finish();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_WAITING_PERMISSION, waitingForPermission);
    }

    private void verifyAndContinue() {
        File file = UpdateChecker.downloadedFile(this, fileName);
        if (!file.isFile()) {
            fail(R.string.update_install_failed);
            return;
        }
        String expectedSha = UpdateChecker.pending(this)
                .getString(UpdateChecker.KEY_READY_SHA, "");
        if (TextUtils.isEmpty(expectedSha)) {
            verifyPackage(file);
            return;
        }
        verifying = true;
        Toast.makeText(this, R.string.update_verifying, Toast.LENGTH_SHORT).show();
        Thread thread = new Thread(() -> {
            boolean valid = HashUtils.matches(file, expectedSha);
            runOnUiThread(() -> {
                verifying = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!valid) {
                    file.delete();
                    UpdateChecker.pending(this).edit()
                            .remove(UpdateChecker.KEY_READY_FILE)
                            .remove(UpdateChecker.KEY_READY_VERSION)
                            .remove(UpdateChecker.KEY_READY_SHA)
                            .remove(UpdateChecker.KEY_READY_PROMPTED)
                            .apply();
                    UpdateNotifications.showVerificationFailed(this);
                    fail(R.string.update_download_verification_failed);
                } else {
                    verifyPackage(file);
                }
            });
        }, "update-verify");
        thread.start();
    }

    private void verifyPackage(File file) {
        try {
            PackageInfo archive = getPackageManager()
                    .getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (archive == null || !getPackageName().equals(archive.packageName)) {
                fail(R.string.update_install_package_mismatch);
                return;
            }
        } catch (RuntimeException error) {
            fail(R.string.update_install_package_mismatch);
            return;
        }
        continueInstall();
    }

    private void continueInstall() {
        if (!AccessibilityUtils.canRequestPackageInstalls(this)) {
            showPermissionDialog();
            return;
        }
        File file = UpdateChecker.downloadedFile(this, fileName);
        if (!file.isFile()) {
            fail(R.string.update_install_failed);
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, APK_MIME);
            intent.setClipData(ClipData.newRawUri("apk", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) {
                fail(R.string.update_install_open_failed);
                return;
            }
            startActivity(intent);
            finish();
        } catch (ActivityNotFoundException error) {
            fail(R.string.update_install_open_failed);
        } catch (SecurityException error) {
            fail(R.string.update_install_open_failed);
        } catch (RuntimeException error) {
            fail(R.string.update_install_failed);
        }
    }

    private void showPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.open_install_settings, (dialog, which) -> {
                    waitingForPermission = true;
                    AccessibilityUtils.openInstallPermissionSettings(this);
                })
                .setNegativeButton(R.string.update_install_cancel, (dialog, which) -> finish())
                .show();
    }

    private void fail(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        finish();
    }
}
