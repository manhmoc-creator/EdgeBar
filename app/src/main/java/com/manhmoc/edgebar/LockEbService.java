      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings; // <--- THÊM DÒNG NÀY
import android.view.*;
import android.widget.Toast;

/**
 * "Homeb dành cho màn khoá" — chỉ sống trong lúc BlacklistLockWatchdogService tạm
 * thu hồi Trợ năng để 1 app Blacklist (Tammi/Vcall...) toàn quyền xử lý cuộc gọi ở
 * lockscreen. Vẽ lại đúng Bar/Corner "lock_..." bằng overlay THƯỜNG
 * (TYPE_APPLICATION_OVERLAY + FLAG_SHOW_WHEN_LOCKED) — KHÔNG cần AccessibilityService
 * nên hoàn toàn không đụng chạm/tranh giành gì với app Blacklist đang chạy.
 *
 * GIỚI HẠN CÓ CHỦ Ý (nhẹ code + không cần Trợ năng):
 *  - Không TRIGGER_*, không BACK/RECENTS/POWER_DIALOG/QUICK_SETTINGS (cần performGlobalAction).
 *  - Không Panel/Bubble (tránh 2 bộ chồng nhau khi Trợ năng quay lại — sẽ tự có lại ngay).
 *  - Cử chỉ chỉ còn Tap/DoubleTap/LongPress/4-hướng vuốt.
 * Tự tắt khi: BlacklistLockWatchdogService.stopService() lúc khôi phục, hoặc khi máy
 * mở khoá hẳn (ACTION_USER_PRESENT) — an toàn dự phòng nếu watchdog có lỗi logic.
 */
public class LockEbService extends Service {

    private WindowManager wm;
    private KeyguardManager km;
    private SharedPreferences prefs;
    private CameraManager cm;
    private String camId;
    private boolean torchOn = false;
    private Vibrator vibrator;

    private final View[] bars = new View[12];
    private final View[] corners = new View[4];
    private final String[] BARS = {"b_c", "r", "l", "r_u", "r_c", "r_d", "t_c", "t_r", "t_l", "l_u", "l_c", "l_d"};
    private final int[] GRAV = {
        Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
        Gravity.TOP|Gravity.RIGHT, Gravity.CENTER_VERTICAL|Gravity.RIGHT, Gravity.BOTTOM|Gravity.RIGHT,
        Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT,
        Gravity.TOP|Gravity.LEFT, Gravity.CENTER_VERTICAL|Gravity.LEFT, Gravity.BOTTOM|Gravity.LEFT
    };
    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    private BroadcastReceiver userPresentReceiver;

    // ===== BarView / CornerView tối giản — chỉ đủ vẽ khối màu bo góc, không auto-hide =====
    private class SimpleBar extends View {
        GradientDrawable gd = new GradientDrawable();
        SimpleBar(Context c) { super(c); gd.setCornerRadius(24f); setBackground(gd); }
        void update(int alpha, int visMode, float radius) {
            gd.setCornerRadius(radius);
            gd.setColor(visMode == 2 ? Color.argb(0,96,125,139) : Color.argb(alpha,96,125,139));
        }
    }
    private class SimpleCorner extends View {
        GradientDrawable gd = new GradientDrawable();
        SimpleCorner(Context c) { super(c); gd.setCornerRadius(80f); setBackground(gd); }
        void update(int alpha, int visMode) {
            gd.setColor(visMode == 2 ? Color.argb(0,96,125,139) : Color.argb(alpha,96,125,139));
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { camId = cm.getCameraIdList()[0]; } catch (Exception ignored) {}

        // An toàn: nếu máy không còn khoá nữa thì không có việc gì để làm
        if (km == null || !km.isKeyguardLocked()) { stopSelf(); return; }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        startForegroundQuiet();
        createBars();
        updateVisibility();

        userPresentReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_USER_PRESENT.equals(i.getAction())) stopSelf();
            }
        };
        registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }

    private void startForegroundQuiet() {
        String cid = "eb_lockeb";
        NotificationChannel c = new NotificationChannel(cid, "LockEb (Blacklist)", NotificationManager.IMPORTANCE_MIN);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
            .setContentTitle("LockEb").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(98, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(98, n);
    }

    private void createBars() {
        int wmType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        for (int i = 0; i < 12; i++) {
            SimpleBar v = new SimpleBar(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, wmType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            try { wm.addView(v, p); bars[i] = v; } catch (Exception ignored) { continue; }
            v.setOnTouchListener(new GestureListener("lock_" + BARS[i]));
        }
        for (int i = 0; i < 4; i++) {
            SimpleCorner v = new SimpleCorner(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, wmType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            try { wm.addView(v, p); corners[i] = v; } catch (Exception ignored) { continue; }
            v.setOnTouchListener(new GestureListener("lock_corner_" + CORNERS[i]));
        }
    }

    private void updateVisibility() {
        for (int i = 0; i < 12; i++) {
            if (bars[i] == null) continue;
            boolean en = prefs.getBoolean("lock_" + BARS[i] + "_en", false);
            bars[i].setVisibility(en ? View.VISIBLE : View.GONE);
            if (!en) continue;
            int alpha = prefs.getInt("lock_" + BARS[i] + "_alpha", 50);
            int w = prefs.getInt("lock_" + BARS[i] + "_w", 300);
            int h = prefs.getInt("lock_" + BARS[i] + "_h", 60);
            int x = prefs.getInt("lock_" + BARS[i] + "_x", 0);
            int y = prefs.getInt("lock_" + BARS[i] + "_y", 0);
            int visMode = prefs.getInt("lock_" + BARS[i] + "_vis_mode", 0);
            ((SimpleBar) bars[i]).update(alpha, visMode, prefs.getInt("lock_bar_radius", 24));
            int priMode = prefs.getInt("lock_" + BARS[i] + "_pri_mode", 0);
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            flags |= (priMode == 1) ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                : (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
            p.flags = flags; p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i];
            try { wm.updateViewLayout(bars[i], p); } catch (Exception ignored) {}
        }
        for (int i = 0; i < 4; i++) {
            if (corners[i] == null) continue;
            boolean en = prefs.getBoolean("lock_corner_" + CORNERS[i] + "_en", false);
            corners[i].setVisibility(en ? View.VISIBLE : View.GONE);
            if (!en) continue;
            String ck = "lock_corner_" + CORNERS[i] + "_";
            int alpha = prefs.getInt("lock_corner_moon_alpha", 100);
            int visMode = prefs.getInt(ck + "vis_mode", 0);
            ((SimpleCorner) corners[i]).update(alpha, visMode);
            int priMode = prefs.getInt(ck + "pri_mode", 0);
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            flags |= (priMode == 1) ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                : (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            int w = prefs.getInt(ck + "w", 100), h = prefs.getInt(ck + "h", 100);
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
            p.flags = flags; p.width = Math.max(40, w); p.height = Math.max(40, h);
            p.x = prefs.getInt(ck + "x", 0); p.y = prefs.getInt(ck + "y", 0); p.gravity = C_GRAV[i];
            try { wm.updateViewLayout(corners[i], p); } catch (Exception ignored) {}
        }
    }

    // ===== Cử chỉ tối giản: Tap / DoubleTap / LongPress / 4-hướng vuốt =====
    private class GestureListener implements View.OnTouchListener {
        final String keyBase; float sx, sy; long downMs; boolean longFired;
        long lastTapMs = 0; final Handler h = new Handler(Looper.getMainLooper());
        Runnable pendingTap; Runnable longCheck = () -> { longFired = true; handleAction(keyBase + "_long"); };
        static final int SLOP = 60, DTAP_MS = 280;

        GestureListener(String keyBase) { this.keyBase = keyBase; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX(); sy = e.getRawY(); downMs = System.currentTimeMillis(); longFired = false;
                    h.postDelayed(longCheck, prefs.getInt("hold_dur", 600));
                    return true;
                case MotionEvent.ACTION_MOVE: return true;
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(longCheck); return true;
                case MotionEvent.ACTION_UP:
                    h.removeCallbacks(longCheck);
                    if (longFired) return true;
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                    float adx = Math.abs(dx), ady = Math.abs(dy);
                    if (adx < SLOP && ady < SLOP) {
                        long now = System.currentTimeMillis();
                        boolean hasDtap = !prefs.getString(keyBase + "_dtap", "NONE").equals("NONE");
                        if (!hasDtap) { handleAction(keyBase + "_tap"); return true; }
                        if (pendingTap != null && now - lastTapMs < DTAP_MS) {
                            h.removeCallbacks(pendingTap); pendingTap = null;
                            handleAction(keyBase + "_dtap");
                        } else {
                            lastTapMs = now;
                            pendingTap = () -> { pendingTap = null; handleAction(keyBase + "_tap"); };
                            h.postDelayed(pendingTap, DTAP_MS + 20);
                        }
                    } else {
                        String dir = adx > ady ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up");
                        handleAction(keyBase + "_" + dir);
                    }
                    return true;
            }
            return false;
        }
    }

    private void handleAction(String key) {
        String action = prefs.getString(key, "NONE");
        if (action.equals("NONE") || !prefs.getBoolean(key + "_on", true)) return;
        if (prefs.getBoolean(key + "_vib", true)) doVibrate(prefs.getInt("vib_dur", 30));
        for (String a : action.split(",")) exec(a.trim());
    }

    private void doVibrate(int ms) {
        if (ms <= 0 || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(ms);
        } catch (Exception ignored) {}
    }

    // ===== Bộ Action KHÔNG cần Accessibility =====
    private void exec(String a) {
        if (a == null || a.equals("NONE") || a.isEmpty()) return;
        try {
            switch (a) {
                case "FLASH":
                    if (camId != null) { torchOn = !torchOn; cm.setTorchMode(camId, torchOn); }
                    break;
                case "CAMERA": {
                    Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                    c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c);
                    break;
                }
                case "VOLUME":
                    ((AudioManager) getSystemService(AUDIO_SERVICE))
                        .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    break;
                case "SCREEN_OFF": {
                    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName admin = new ComponentName(this, HomebDeviceAdminReceiver.class);
                    if (dpm.isAdminActive(admin)) { try { dpm.lockNow(); } catch (Exception ignored) {} }
                    break;
                }
                case "TOGGLE_RECORD": {
                    Intent i = new Intent(this, VoiceRecorderService.class);
                    i.setAction(VoiceRecorderService.ACTION_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    break;
                }
                case "PAUSE_RECORD": {
                    if (!VoiceRecorderService.isRunning) break;
                    Intent i = new Intent(this, VoiceRecorderService.class);
                    i.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    break;
                }
                case "PLAY_MY_PLAYLIST": {
                    Intent i = new Intent(this, MyPlaylistService.class);
                    i.setAction(MyPlaylistService.ACTION_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    break;
                }
                case "HIDE_SOME_OVERLAY": {
                    String targetsBar = prefs.getString("lock_bar_hide_targets", "");
                    String targetsCorner = prefs.getString("lock_corner_hide_targets", "");
                    for (String t : (targetsBar + "," + targetsCorner).split(",")) {
                        String tt = t.trim(); if (tt.isEmpty()) continue;
                        boolean isCorner = tt.equals("br")||tt.equals("bl")||tt.equals("tr")||tt.equals("tl");
                        if (isCorner) { int idx = java.util.Arrays.asList(CORNERS).indexOf(tt); if (idx>=0 && corners[idx]!=null) corners[idx].setVisibility(View.GONE); }
                        else { int idx = java.util.Arrays.asList(BARS).indexOf(tt); if (idx>=0 && bars[idx]!=null) bars[idx].setVisibility(View.GONE); }
                    }
                    break;
                }
                case "SHOW_ALL_OVERLAY": updateVisibility(); break;
                default:
                    if (a.equals("LAUNCH_APP")) { /* cần launch_pkg riêng theo key, bỏ qua ở path chung */ }
                    else if (a.startsWith("RUN_SHORTCUT_")) {
                        String scId = a.substring("RUN_SHORTCUT_".length());
                        String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                        if (!uri.isEmpty()) {
                            Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                            scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(scIntent);
                        }
                    } else if (a.startsWith("MACRO_")) {
                        Intent iM = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                        iM.putExtra("services", prefs.getString("macro_" + a.substring(6) + "_svcs", ""));
                        sendBroadcast(iM);
                    }
                    break;
            }
        } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    @Override public void onDestroy() {
        if (userPresentReceiver != null) { try { unregisterReceiver(userPresentReceiver); } catch (Exception ignored) {} }
        for (int i = 0; i < 12; i++) if (bars[i] != null) { try { wm.removeView(bars[i]); } catch (Exception ignored) {} }
        for (int i = 0; i < 4; i++) if (corners[i] != null) { try { wm.removeView(corners[i]); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
