package com.manhmoc.edgebar;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;

public class LockOverlayActivity extends Activity {
    private static final int REQ_CONFIRM = 9911;
    private String targetPkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetPkg = getIntent().getStringExtra("lock_pkg");
        if (targetPkg == null) { finish(); return; }

        if (Build.VERSION.SDK_INT >= 30) {
            // [FIX FULL MÀN HÌNH] Ép chỉ hỏi PIN/Pattern/Password, bỏ qua vân tay
            // hoàn toàn — đây là màn hình full-screen thật của hệ thống.
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Mở khoá " + targetPkg)
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
            prompt.authenticate(new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        EdgeBarService.markPackageUnlocked(targetPkg);
                        finish();
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        goHome();
                        finish();
                    }
                });
        } else {
            // Máy dưới Android 11: không có API ép bỏ vân tay, đành dùng cách cũ
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            Intent i = km.createConfirmDeviceCredentialIntent("Mở khoá", null);
            if (i == null) { finish(); return; }
            startActivityForResult(i, REQ_CONFIRM);
        }
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CONFIRM) {
            if (resultCode == RESULT_OK) EdgeBarService.markPackageUnlocked(targetPkg);
            else goHome();
            finish();
        }
    }
}
