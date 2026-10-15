package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.Toast;

/** Mở từ notification nhắc tái xác thực. Xác thực thành công (vân tay hoặc
 *  PIN/mẫu hình khoá máy) -> gia hạn thêm đúng 7 ngày kể từ bây giờ.
 *  KHÔNG có cách nào gia hạn mà không qua màn này — đúng nguyên tắc "chỉ người
 *  đang cầm máy mới xác nhận được", không thể làm từ xa. */
public class RemoteReauthActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        String mac = getIntent().getStringExtra("mac");
        String name = getIntent().getStringExtra("name");
        if (mac == null) { finish(); return; }

        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(this, "Thiết bị không hỗ trợ xác thực sinh trắc", Toast.LENGTH_LONG).show();
            finish(); return;
        }

        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
            .setTitle("Xác thực lại — " + name)
            .setDescription("Xác nhận để tiếp tục cho phép thiết bị này gửi lệnh trong 7 ngày tới")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL) // vân tay HOẶC PIN/mẫu hình khoá máy
            .build();

        prompt.authenticate(new CancellationSignal(), getMainExecutor(),
            new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
                    RemoteDeviceStore.Device d = RemoteDeviceStore.find(prefs, mac);
                    if (d != null) {
                        RemoteDeviceStore.upsertTrusted(prefs, d.mac, d.name, d.pubKeyB64); // gia hạn thêm 7 ngày
                        Toast.makeText(RemoteReauthActivity.this, "Đã gia hạn tin cậy 7 ngày cho " + name, Toast.LENGTH_SHORT).show();
                    }
                    finish();
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    Toast.makeText(RemoteReauthActivity.this, "Xác thực thất bại — thiết bị sẽ hết hạn đúng lịch", Toast.LENGTH_LONG).show();
                    finish();
                }
                @Override public void onAuthenticationFailed() {
                    // không finish ngay, để người dùng thử lại vân tay
                }
            });
    }
}
