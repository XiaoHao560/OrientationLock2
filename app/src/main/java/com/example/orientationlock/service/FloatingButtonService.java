package com.example.orientationlock.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.example.orientationlock.BuildConfig;
import com.example.orientationlock.R;
import com.example.orientationlock.preference.PreferenceManager;
import com.example.orientationlock.utils.SimpleLog;
import com.example.orientationlock.utils.ViewUtils;

public class FloatingButtonService extends Service {

    private static final String TAG = FloatingButtonService.class.getSimpleName();

    public static final String ACTION_SHOW = BuildConfig.APPLICATION_ID + ".action.FLOATING_SHOW";
    public static final String ACTION_HIDE = BuildConfig.APPLICATION_ID + ".action.FLOATING_HIDE";
    public static final String ACTION_ENTER_EDIT_MODE = BuildConfig.APPLICATION_ID + ".action.ENTER_EDIT";
    public static final String ACTION_EXIT_EDIT_MODE = BuildConfig.APPLICATION_ID + ".action.EXIT_EDIT";
    public static final String ACTION_SAVE_POSITION = BuildConfig.APPLICATION_ID + ".action.SAVE_POSITION";

    private WindowManager windowManager;
    private ImageView floatingButton;
    private WindowManager.LayoutParams buttonParams;
    private PreferenceManager preferenceManager;

    private OrientationEventListener orientationListener;
    // 记录最近一次传感器检测到的方向角度（0°/90°/180°/270°）
    private int lastSensorRotation = -1;

    private boolean isEditMode = false;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    // 屏幕尺寸缓存
    private int screenWidth, screenHeight;
    private int buttonSize;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        preferenceManager = PreferenceManager.getInstance(this);

        buttonSize = ViewUtils.dp(this, 56);

        initScreenSize();
        createFloatingButton();
        initOrientationListener();
    }

    private void initOrientationListener() {
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation != ORIENTATION_UNKNOWN) {
                    lastSensorRotation = orientation;
                }
            }
        };
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        } else {
            SimpleLog.d(TAG, "Device cannot detect orientation");
        }
    }

    private void initScreenSize() {
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
    }

    private void createFloatingButton() {
        floatingButton = new ImageView(this);
        floatingButton.setImageResource(R.drawable.ic_rotate_full_sensor);
        floatingButton.setBackgroundResource(R.drawable.bg_floating_button);
        floatingButton.setPadding(16, 16, 16, 16);
        floatingButton.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // 默认位置：屏幕右侧中间
        int defaultX = screenWidth - buttonSize - 32;
        int defaultY = screenHeight / 2 - buttonSize / 2;

        int savedX = preferenceManager.getFloatingButtonX(defaultX);
        int savedY = preferenceManager.getFloatingButtonY(defaultY);

        buttonParams = new WindowManager.LayoutParams(
                buttonSize,
                buttonSize,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        buttonParams.gravity = Gravity.START | Gravity.TOP;
        buttonParams.x = savedX;
        buttonParams.y = savedY;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isEditMode) {
                    return handleEditModeTouch(event);
                } else {
                    // 普通模式下点击事件由 OnClickListener 处理
                    return false;
                }
            }
        });

        floatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isEditMode) {
                    rotateToSensorOrientation();
                }
            }
        });

        try {
            windowManager.addView(floatingButton, buttonParams);
        } catch (Exception e) {
            SimpleLog.d(TAG, "Button already added or error: " + e.getMessage());
        }
    }

    private boolean handleEditModeTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = buttonParams.x;
                initialY = buttonParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int deltaX = (int) (event.getRawX() - initialTouchX);
                int deltaY = (int) (event.getRawY() - initialTouchY);
                buttonParams.x = initialX + deltaX;
                buttonParams.y = initialY + deltaY;
                // 边界限制
                buttonParams.x = Math.max(0, Math.min(buttonParams.x, screenWidth - buttonSize));
                buttonParams.y = Math.max(0, Math.min(buttonParams.y, screenHeight - buttonSize));
                windowManager.updateViewLayout(floatingButton, buttonParams);
                return true;
        }
        return false;
    }

    // 使用 OrientationEventListener 获取传感器方向，点击时根据物理传感器角度锁定对应方向
    private void rotateToSensorOrientation() {
        int targetOrientation;

        if (lastSensorRotation == -1) {
            // 尚未获取到传感器方向，使用 FULL_SENSOR 作为 fallback
            targetOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        } else {
            // OrientationEventListener 返回的角度定义：
            //   0°   : 设备自然方向（正常竖屏，顶部朝上）
            //   90°  : 设备逆时针转90°（左侧朝上）
            //   180° : 设备倒置（顶部朝下）
            //   270° : 设备顺时针转90°（右侧朝上）
            //
            // 映射到 ActivityInfo 方向常量：
            //   0°   -> PORTRAIT              正常竖屏
            //   90°  -> REVERSE_LANDSCAPE     左侧朝上（逆时针90°）
            //   180° -> REVERSE_PORTRAIT      倒置
            //   270° -> LANDSCAPE             右侧朝上（顺时针90°）
            // 使用 ±45° 容差区间判断

            if (lastSensorRotation >= 315 || lastSensorRotation < 45) {
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            } else if (lastSensorRotation >= 45 && lastSensorRotation < 135) {
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            } else if (lastSensorRotation >= 135 && lastSensorRotation < 225) {
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            } else { // 225° ~ 315°
                targetOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
        }

        SimpleLog.d(TAG, "sensor rotation: " + lastSensorRotation + " -> target: " + targetOrientation);

        // 发送广播通知主服务设置方向
        Intent intent = new Intent(this, OrientationLockService.class);
        intent.setAction(OrientationLockService.ACTION_SET_ORIENTATION);
        intent.putExtra(OrientationLockService.KEY_ORIENTATION, targetOrientation);
        startService(intent);

        // 保存到偏好设置
        preferenceManager.setOrientation(targetOrientation);
    }

    private void enterEditMode() {
        isEditMode = true;
        floatingButton.setAlpha(0.7f);
        floatingButton.setBackgroundResource(R.drawable.bg_floating_button_edit);
        
        if (floatingButton != null && floatingButton.getParent() != null) {
            windowManager.removeView(floatingButton);
            windowManager.addView(floatingButton, buttonParams);
        }
    }

    private void exitEditMode(boolean save) {
        isEditMode = false;
        floatingButton.setAlpha(1.0f);
        floatingButton.setBackgroundResource(R.drawable.bg_floating_button);
        if (save) {
            preferenceManager.setFloatingButtonPosition(buttonParams.x, buttonParams.y);
        } else {
            // 恢复保存的位置
            int defaultX = screenWidth - buttonSize - 32;
            int defaultY = screenHeight / 2 - buttonSize / 2;
            buttonParams.x = preferenceManager.getFloatingButtonX(defaultX);
            buttonParams.y = preferenceManager.getFloatingButtonY(defaultY);
            windowManager.updateViewLayout(floatingButton, buttonParams);
        }
    }

    private void hideButton() {
        if (floatingButton != null && floatingButton.getParent() != null) {
            windowManager.removeView(floatingButton);
        }
    }

    private void showButton() {
        if (floatingButton != null && floatingButton.getParent() == null) {
            try {
                windowManager.addView(floatingButton, buttonParams);
            } catch (Exception e) {
                SimpleLog.d(TAG, "Show button error: " + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        
        String action = intent.getAction();
        if (ACTION_SHOW.equals(action)) {
            showButton();
        } else if (ACTION_HIDE.equals(action)) {
            hideButton();
        } else if (ACTION_ENTER_EDIT_MODE.equals(action)) {
            enterEditMode();
        } else if (ACTION_EXIT_EDIT_MODE.equals(action)) {
            boolean save = intent.getBooleanExtra("save", false);
            exitEditMode(save);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (orientationListener != null) {
            orientationListener.disable();
        }
        hideButton();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
