package com.example.orientationlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.orientationlock.preference.PreferenceManager;

public class SettingsActivity extends Activity {

    private PreferenceManager preferenceManager;
    private Switch switchQuickRecovery;
    private TextView tvTileSettingsDesc;

    // 映射所有的方向及其对应的字符串，用于对话框展示
    private final int[] orientationValues = {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    };

    private final int[] orientationNames = {
            R.string.orientation_full_sensor,
            R.string.orientation_landscape,
            R.string.orientation_reverse_landscape,
            R.string.orientation_sensor_landscape,
            R.string.orientation_portrait,
            R.string.orientation_reverse_portrait,
            R.string.orientation_sensor_portrait
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        preferenceManager = PreferenceManager.getInstance(this);
        switchQuickRecovery = findViewById(R.id.switch_quick_recovery);
        tvTileSettingsDesc = findViewById(R.id.tv_tile_settings_desc);

        // 恢复快速恢复开关状态
        switchQuickRecovery.setChecked(preferenceManager.isQuickNotificationRecovery());
        switchQuickRecovery.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setQuickNotificationRecovery(isChecked);
            if (isChecked && android.os.Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                }
            }
        });

        // 初始化磁贴设置描述文本
        updateTileDesc(preferenceManager.getTileTargetOrientation());

        // 设置点击事件，弹出单选对话框
        findViewById(R.id.ll_tile_settings).setOnClickListener(v -> showTileOrientationDialog());
    }

    private void showTileOrientationDialog() {
        int currentOrientation = preferenceManager.getTileTargetOrientation();
        int checkedItem = 1; // 默认给 Landscape

        String[] items = new String[orientationNames.length];
        for (int i = 0; i < orientationNames.length; i++) {
            items[i] = getString(orientationNames[i]);
            if (orientationValues[i] == currentOrientation) {
                checkedItem = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.tile_settings_title)
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    int selectedOrientation = orientationValues[which];
                    preferenceManager.setTileTargetOrientation(selectedOrientation);
                    updateTileDesc(selectedOrientation);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateTileDesc(int orientation) {
        for (int i = 0; i < orientationValues.length; i++) {
            if (orientationValues[i] == orientation) {
                tvTileSettingsDesc.setText(orientationNames[i]);
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                switchQuickRecovery.setChecked(false);
                preferenceManager.setQuickNotificationRecovery(false);
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }
}