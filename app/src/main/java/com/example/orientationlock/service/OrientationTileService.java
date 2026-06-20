package com.example.orientationlock.service;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import com.example.orientationlock.MainActivity;
import com.example.orientationlock.R;
import com.example.orientationlock.preference.PreferenceManager;
import com.example.orientationlock.utils.PermissionUtils;

public class OrientationTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        PreferenceManager pm = PreferenceManager.getInstance(this);
        int currentOrientation = pm.getOrientation();
        int targetOrientation = pm.getTileTargetOrientation();

        // 如果当前方向就是我们设置的目标方向，显示为开启状态
        if (currentOrientation == targetOrientation) {
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        // 检查悬浮窗权限
        if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(intent);
            return;
        }

        PreferenceManager pm = PreferenceManager.getInstance(this);
        int currentOrientation = pm.getOrientation();
        int targetOrientation = pm.getTileTargetOrientation();

        // 切换逻辑：如果已经是目标方向，则取消锁定（恢复系统默认）; 否则设定为目标方向
        int newOrientation = (currentOrientation == targetOrientation) 
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED 
                : targetOrientation;

        pm.setOrientation(newOrientation);

        // 启动服务应用方向
        Intent serviceIntent = new Intent(this, OrientationLockService.class);
        serviceIntent.setAction(OrientationLockService.ACTION_SET_ORIENTATION);
        serviceIntent.putExtra(OrientationLockService.KEY_ORIENTATION, newOrientation);

        if (newOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            stopService(serviceIntent);
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

        // 更新磁贴UI状态
        updateTileState();
    }
}