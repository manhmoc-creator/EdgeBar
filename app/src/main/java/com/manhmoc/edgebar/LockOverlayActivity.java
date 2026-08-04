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
            // Chỉ những app được đánh dấu 🔓 trong LockList mới cho vân tay ngay;
            // app không đánh dấu -> ép PIN/Pattern/Password, che kín cả màn hình
            // (đúng nhu cầu che app tin nhắn của bạn).
            boolean allowFinger = getIntent().getBooleanExtra("allow_fingerprint", false);
            int authFlags = allowFinger
                ? (BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                : BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Unlock 🎭")
                .setAllowedAuthenticators(authFlags)
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
