package com.example.orientationlock;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.orientationlock.preference.PreferenceManager;
import com.example.orientationlock.service.OrientationLockService;
import com.example.orientationlock.utils.*;

import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_RESTORE_DEFAULT = BuildConfig.APPLICATION_ID + ".action.RESTORE_DEFAULT";
    private static final String CHANNEL_ID_QUICK_RECOVERY = BuildConfig.APPLICATION_ID + ".channel.quick_recovery";
    private static final int NOTIFICATION_ID_QUICK_RECOVERY = 2;

    private PreferenceManager preferenceManager;
    private int currentOrientation;
    private final SparseIntArray orientationMap = new SparseIntArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initView();

        preferenceManager = PreferenceManager.getInstance(this);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.id.tv_orientation_default);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, R.id.tv_orientation_full_sensor);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.id.tv_orientation_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, R.id.tv_orientation_reverse_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, R.id.tv_orientation_sensor_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.id.tv_orientation_portrait);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, R.id.tv_orientation_reverse_portrait);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, R.id.tv_orientation_sensor_portrait);

        int orientation = PermissionUtils.isDrawOverlaysPermissionGranted(this)
                ? preferenceManager.getOrientation() : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        setOrientation(orientation);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ACTION_RESTORE_DEFAULT.equals(intent.getAction())) {
            setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void setOrientation(int orientation) {
        SimpleLog.d(TAG, "select orientation: " + orientation);
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
                PermissionUtils.requestDrawOverlaysPermission(this);
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        preferenceManager.setOrientation(orientation);
        int lastViewId = orientationMap.get(currentOrientation, View.NO_ID);
        if (lastViewId != View.NO_ID) {
            findViewById(lastViewId).setBackgroundResource(R.drawable.bg_button);
        }
        int viewId = orientationMap.get(orientation, View.NO_ID);
        if (viewId != View.NO_ID) {
            findViewById(viewId).setBackgroundResource(R.drawable.bg_selected);
        }
        Intent intent = new Intent(this, OrientationLockService.class);
        intent.setAction(OrientationLockService.ACTION_SET_ORIENTATION);
        intent.putExtra(OrientationLockService.KEY_ORIENTATION, orientation);
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            stopService(intent);
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        currentOrientation = orientation;

        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            cancelQuickRecoveryNotification();
        } else {
            sendQuickRecoveryNotification();
        }
    }

    private void initView() {
        LinearLayout rootView = findViewById(R.id.ll_root);
        List<View> childViews = ViewUtils.getAllChildViews(rootView);
        Drawable icon;
        int iconSize = ViewUtils.dp(this, 32);
        int iconColor = Build.VERSION.SDK_INT >= 21
                ? ContextUtils.getColorFromAttr(this, android.R.attr.colorAccent)
                : ContextUtils.getColor(this, R.color.icon_tint);
        for (View view : childViews) {
            if (view instanceof TextView) {
                Drawable[] drawables = ((TextView) view).getCompoundDrawables();
                icon = drawables[1];
                if (icon == null) {
                    continue;
                }
                icon.setBounds(0, 0, iconSize, iconSize);
                ViewUtils.setDrawableColorFilter(icon, iconColor);
                ((TextView) view).setCompoundDrawables(drawables[0], icon, drawables[2], drawables[3]);
                if (view.getId() != View.NO_ID) {
                    view.setOnClickListener(this);
                }
            }
        }
        findViewById(R.id.iv_about).setOnClickListener(v -> {
            Uri uri = Uri.parse("https://github.com/tuyafeng/OrientationLock");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ignore) {
            }
        });
        findViewById(R.id.iv_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        int index = orientationMap.indexOfValue(id);
        if (index < 0) {
            return;
        }
        int orientation = orientationMap.keyAt(index);
        setOrientation(orientation);
    }

    private void sendQuickRecoveryNotification() {
        if (!preferenceManager.isQuickNotificationRecovery()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        createNotificationChannel();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_RESTORE_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlags);

        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(getString(R.string.quick_recovery_notification_title))
                .setContentText(getString(R.string.quick_recovery_notification_text))
                .setSmallIcon(R.drawable.ic_stat_orientation)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID_QUICK_RECOVERY);
        }

        manager.notify(NOTIFICATION_ID_QUICK_RECOVERY, builder.build());
    }

    private void cancelQuickRecoveryNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID_QUICK_RECOVERY);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager.getNotificationChannel(CHANNEL_ID_QUICK_RECOVERY) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_QUICK_RECOVERY,
                    getString(R.string.channel_quick_recovery_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channel_quick_recovery_desc));
            manager.createNotificationChannel(channel);
        }
    }
}
