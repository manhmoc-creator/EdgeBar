
package com.manhmoc.edgebar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

public class LockOverlayActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String targetPkg = getIntent().getStringExtra("lock_pkg");
        if (targetPkg == null) { finish(); return; }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Mở khoá")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build();

        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    EdgeBarService.markPackageUnlocked(targetPkg);
                    finish();
                }
                @Override public void onAuthenticationFailed() {
                    Toast.makeText(LockOverlayActivity.this, "Xác thực thất bại", Toast.LENGTH_SHORT).show();
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    // Người dùng huỷ hoặc lỗi -> đá về Home, không cho vào app đang khoá
                    Intent home = new Intent(Intent.ACTION_MAIN);
                    home.addCategory(Intent.CATEGORY_HOME);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(home);
                    finish();
                }
            });
        prompt.authenticate(promptInfo);
    }
}
