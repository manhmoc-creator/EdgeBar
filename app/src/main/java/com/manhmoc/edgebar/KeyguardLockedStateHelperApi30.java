     package com.manhmoc.edgebar;

import android.app.KeyguardManager;
import android.content.Context;

/**
 * Tách riêng khỏi EdgeBarService để tránh ClassNotFoundException trên API < 30.
 * Nếu để type KeyguardManager.KeyguardLockedStateListener nằm trực tiếp trong
 * bytecode của EdgeBarService, ART verifier sẽ cố resolve type này ngay khi
 * verify method của EdgeBarService (bất kể có check SDK_INT hay không), khiến
 * cả EdgeBarService không load được trên máy < Android 11. Class riêng này
 * chỉ bị load khi thực sự được gọi tới (và ta chỉ gọi trên API >= 30).
 */
public class KeyguardLockedStateHelperApi30 {
    private KeyguardManager.KeyguardLockedStateListener listener;

    public void register(Context ctx, KeyguardManager km, Runnable onUnlocked) {
        listener = locked -> {
            if (!locked) onUnlocked.run();
        };
        try {
            km.addKeyguardLockedStateListener(ctx.getMainExecutor(), listener);
        } catch (Exception ignored) {}
    }

    public void unregister(KeyguardManager km) {
        if (listener == null) return;
        try { km.removeKeyguardLockedStateListener(listener); } catch (Exception ignored) {}
        listener = null;
    }
}
