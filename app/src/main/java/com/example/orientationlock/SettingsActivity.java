package com.example.orientationlock;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import com.example.orientationlock.preference.PreferenceManager;

public class SettingsActivity extends Activity {

    private PreferenceManager preferenceManager;
    private Switch switchQuickRecovery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        preferenceManager = PreferenceManager.getInstance(this);
        switchQuickRecovery = findViewById(R.id.switch_quick_recovery);

        switchQuickRecovery.setChecked(preferenceManager.isQuickNotificationRecovery());
        switchQuickRecovery.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setQuickNotificationRecovery(isChecked);
            if (isChecked && android.os.Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                }
            }
        });
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
