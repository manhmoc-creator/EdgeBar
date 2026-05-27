package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.DashPathEffect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private FlashView fV;
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private KeyguardManager km;
    private SharedPreferences prefs;
    private Vibrator vibrator;

    private String unlockedPackage = ""; // để nhớ app đã mở khóa

    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
        if (k != null) {
            updateVisibility();
            if (fV != null) fV.updateStyle();
        }
    };

    private BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if ("com.manhmoc.edgebar.TEST_ANIM".equals(i.getAction())) {
                playAnim();
            } else if ("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS".equals(i.getAction())) {
                unlockedPackage = i.getStringExtra("pkg");
            } else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                unlockedPackage = "";
                updateVisibility();
            } else {
                updateVisibility();
            }
        }
    };

    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if ("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) {
                exec(i.getStringExtra("act"));
            }
        }
    };

    private class FlashView extends View {
        // ... giữ nguyên code FlashView từ bản cũ (đã có trong project)
        // Để tiết kiệm, tôi sẽ không lặp lại toàn bộ nhưng bạn giữ nguyên file cũ
        // Nếu chưa có, hãy copy từ bản 19.12.3.4
    }

    private class CornerView extends View {
        // ... giữ nguyên
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction("com.manhmoc.edgebar.TEST_ANIM");
        filter.addAction("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
        registerReceiver(stateReceiver, filter);

        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"));

        String cid = "eb_19_acc";
        NotificationChannel c = new NotificationChannel(cid, "Edge Bar đang chạy nền", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
        startForeground(1, n);
        createFloatingBars();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String pName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            String cName = event.getClassName() != null ? event.getClassName().toString() : "";
            isKbd = pName.contains("inputmethod") || cName.contains("InputWindow") || cName.contains("keyboard") || cName.contains("Keyboard");
            String bl = prefs.getString("blacklist", "");
            isBl = !pName.isEmpty() && bl.contains(pName);

            // Xử lý locklist
            String locklist = prefs.getString("locklist", "");
            boolean isAppLocked = false;
            if (!pName.isEmpty() && !locklist.isEmpty()) {
                for (String pkg : locklist.split(",")) {
                    if (pkg.trim().equals(pName)) { isAppLocked = true; break; }
                }
            }
            if (isAppLocked) {
                if (!pName.equals(unlockedPackage)) {
                    Intent i = new Intent("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
                    i.putExtra("pkg", pName);
                    sendBroadcast(i);
                }
            } else if (!pName.isEmpty() && !pName.contains("systemui") && !isKbd) {
                unlockedPackage = "";
            }

            updateVisibility();
            Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE");
            i.putExtra("isKbd", isKbd);
            i.putExtra("isBl", isBl);
            sendBroadcast(i);
        }
    }

    private void exec(String a) {
        // Giữ nguyên từ bản cũ (có xử lý TOGGLE_MORSE, MACRO...)
        // ...
    }

    private void fireIntent(String idx) {
        // giữ nguyên
    }

    private void playAnim() {
        // giữ nguyên
    }

    private void handleAction(String key) {
        // giữ nguyên
    }

    private void doVibrate(int dur) {
        // giữ nguyên
    }

    private void createFloatingBars() {
        // giữ nguyên
    }

    private void updateVisibility() {
        // giữ nguyên (chỉ hiện lock bars)
    }

    private class SidebarTouchListener implements View.OnTouchListener {
        // giữ nguyên
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(stateReceiver); } catch (Exception e) {}
        try { unregisterReceiver(ipcReceiver); } catch (Exception e) {}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        // remove views...
    }
}
