package com.example.orientationlock.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

public class PreferenceManager {

    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_QUICK_NOTIFICATION_RECOVERY = "quick_notification_recovery";
    private static final String KEY_TILE_TARGET_ORIENTATION = "tile_target_orientation";

    private static PreferenceManager sManager;
    private static SharedPreferences sPreferences;

    private static final String PREFERENCES = "settings";

    public static PreferenceManager getInstance(Context context) {
        if (sManager == null) {
            sManager = new PreferenceManager(context);
        }
        return sManager;
    }

    private PreferenceManager(Context context) {
        sPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private void putInt(String name, int value) {
        if (name == null) {
            return;
        }
        sPreferences.edit().putInt(name, value).apply();
    }

    private void putBoolean(String name, boolean value) {
        if (name == null) {
            return;
        }
        sPreferences.edit().putBoolean(name, value).apply();
    }

    public void setOrientation(int orientation) {
        putInt(KEY_ORIENTATION, orientation);
    }

    public int getOrientation() {
        return sPreferences.getInt(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public void setQuickNotificationRecovery(boolean enabled) {
        putBoolean(KEY_QUICK_NOTIFICATION_RECOVERY, enabled);
    }

    public boolean isQuickNotificationRecovery() {
        return sPreferences.getBoolean(KEY_QUICK_NOTIFICATION_RECOVERY, false);
    }

    public void setTileTargetOrientation(int orientation) {
        putInt(KEY_TILE_TARGET_ORIENTATION, orientation);
    }

    public int getTileTargetOrientation() {
        return sPreferences.getInt(KEY_TILE_TARGET_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
}
