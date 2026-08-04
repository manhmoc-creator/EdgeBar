
package com.manhmoc.edgebar;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * [TỐI ƯU DUNG LƯỢNG] Bỏ androidx.biometric (kéo theo fragment/core/lifecycle
 * không cần thiết, làm APK phình ~1.2MB khi minifyEnabled=false). Dùng thẳng
 * KeyguardManager.createConfirmDeviceCredentialIntent() — API hệ thống có sẵn
 * từ API 21, không tốn thêm 1 byte thư viện nào, và màn hình xác thực hệ
 * thống vẫn tự ưu tiên vân tay/khuôn mặt nếu máy hỗ trợ trước khi hỏi PIN.
 */
public class LockOverlayActivity extends Activity {
    private static final int REQ_CONFIRM = 9911;
    private String targetPkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetPkg = getIntent().getStringExtra("lock_pkg");
        if (targetPkg == null) { finish(); return; }

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent i = km.createConfirmDeviceCredentialIntent("Mở khoá", null);
        if (i == null) {
            // Máy chưa đặt khoá màn hình (PIN/hình/vân tay) -> không có gì để xác thực
            finish();
            return;
        }
        startActivityForResult(i, REQ_CONFIRM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CONFIRM) {
            if (resultCode == RESULT_OK) {
                EdgeBarService.markPackageUnlocked(targetPkg);
            } else {
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
            }
            finish();
        }
    }
}
