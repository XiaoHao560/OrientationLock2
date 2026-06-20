package com.example.orientationlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.orientationlock.preference.PreferenceManager;
import com.example.orientationlock.service.FloatingButtonService;
import com.example.orientationlock.utils.PermissionUtils;
import com.example.orientationlock.utils.ViewUtils;

public class SettingsActivity extends Activity {

    private PreferenceManager preferenceManager;
    private Switch switchQuickRecovery;
    private Switch switchFloatingButton;
    private TextView tvTileSettingsDesc;
    private TextView tvFloatingButtonPosition;
    private LinearLayout llFloatingButtonPosition;

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
        switchFloatingButton = findViewById(R.id.switch_floating_button);
        tvTileSettingsDesc = findViewById(R.id.tv_tile_settings_desc);
        tvFloatingButtonPosition = findViewById(R.id.tv_floating_button_position);
        llFloatingButtonPosition = findViewById(R.id.ll_floating_button_position);

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

        // 悬浮按钮开关
        switchFloatingButton.setChecked(preferenceManager.isFloatingButtonEnabled());
        updateFloatingButtonPositionEnabled(preferenceManager.isFloatingButtonEnabled());
        switchFloatingButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
                PermissionUtils.requestDrawOverlaysPermission(this);
                switchFloatingButton.setChecked(false);
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                return;
            }
            preferenceManager.setFloatingButtonEnabled(isChecked);
            updateFloatingButtonPositionEnabled(isChecked);
            updateFloatingButtonVisibility(isChecked);
        });

        // 悬浮按钮位置设置
        llFloatingButtonPosition.setOnClickListener(v -> {
            if (!preferenceManager.isFloatingButtonEnabled()) {
                return;
            }
            if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
                PermissionUtils.requestDrawOverlaysPermission(this);
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                return;
            }
            showPositionEditDialog();
        });

        // 初始化磁贴设置描述文本
        updateTileDesc(preferenceManager.getTileTargetOrientation());

        // 设置点击事件，弹出单选对话框
        findViewById(R.id.ll_tile_settings).setOnClickListener(v -> showTileOrientationDialog());
    }

    // 同步位置选项可用性状态
    private void updateFloatingButtonPositionEnabled(boolean enabled) {
        llFloatingButtonPosition.setEnabled(enabled);
        llFloatingButtonPosition.setClickable(enabled);
        llFloatingButtonPosition.setFocusable(enabled);
        llFloatingButtonPosition.setAlpha(enabled ? 1.0f : 0.4f);
    }

    // 启动或停止悬浮按钮服务
    private void updateFloatingButtonVisibility(boolean enabled) {
        Intent intent = new Intent(this, FloatingButtonService.class);
        if (enabled) {
            intent.setAction(FloatingButtonService.ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } else {
            intent.setAction(FloatingButtonService.ACTION_HIDE);
            startService(intent);
        }
    }

    private void showPositionEditDialog() {
        // 确保悬浮按钮服务已启动
        Intent ensureIntent = new Intent(this, FloatingButtonService.class);
        ensureIntent.setAction(FloatingButtonService.ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(ensureIntent);
        } else {
            startService(ensureIntent);
        }

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 根布局
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        rootLayout.setBackgroundResource(R.drawable.bg_edit_panel);
        rootLayout.setPadding(
                ViewUtils.dp(this, 24),
                ViewUtils.dp(this, 20),
                ViewUtils.dp(this, 24),
                ViewUtils.dp(this, 20)
        );

        // 提示文字
        TextView hintText = new TextView(this);
        hintText.setText(R.string.floating_button_edit_hint);
        hintText.setTextColor(0xFFFFFFFF);
        hintText.setTextSize(16);
        hintText.setGravity(Gravity.CENTER);
        hintText.setLineSpacing(ViewUtils.dp(this, 4), 1.2f);

        // 按钮容器
        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, ViewUtils.dp(this, 16), 0, 0);
        buttonContainer.setLayoutParams(containerParams);

        // 取消按钮
        Button cancelButton = new Button(this);
        cancelButton.setText(R.string.cancel);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewUtils.dp(this, 100),
                ViewUtils.dp(this, 40));
        btnParams.setMargins(ViewUtils.dp(this, 8), 0, ViewUtils.dp(this, 8), 0);
        cancelButton.setLayoutParams(btnParams);

        // 保存按钮
        Button saveButton = new Button(this);
        saveButton.setText(R.string.save);
        saveButton.setLayoutParams(btnParams);

        buttonContainer.addView(cancelButton);
        buttonContainer.addView(saveButton);

        rootLayout.addView(hintText);
        rootLayout.addView(buttonContainer);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        // 先让服务进入编辑模式
        Intent editIntent = new Intent(this, FloatingButtonService.class);
        editIntent.setAction(FloatingButtonService.ACTION_ENTER_EDIT_MODE);
        startService(editIntent);

        // 显示提示面板
        windowManager.addView(rootLayout, params);

        // 统一的关闭处理
        View.OnClickListener dismissListener = v -> {
            boolean save = (v == saveButton);
            Intent exitIntent = new Intent(this, FloatingButtonService.class);
            exitIntent.setAction(FloatingButtonService.ACTION_EXIT_EDIT_MODE);
            exitIntent.putExtra("save", save);
            startService(exitIntent);
            windowManager.removeView(rootLayout);
            if (save) {
                Toast.makeText(this, R.string.position_saved, Toast.LENGTH_SHORT).show();
            }
        };

        saveButton.setOnClickListener(dismissListener);
        cancelButton.setOnClickListener(dismissListener);
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

    @Override
    protected void onResume() {
        super.onResume();
        // 检查权限状态，如果用户去设置里关闭了权限，同步开关状态
        if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            if (preferenceManager.isFloatingButtonEnabled()) {
                preferenceManager.setFloatingButtonEnabled(false);
                switchFloatingButton.setChecked(false);
                updateFloatingButtonPositionEnabled(false);
            }
        }
    }
}
