      package com.manhmoc.edgebar;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class LockEbService extends Service {
    private static final String[] BARS = {"b_c", "r", "l", "r_u", "r_c", "r_d", "t_c", "t_r", "t_l", "l_u", "l_c", "l_d"};
    private static final int[] GRAV = {
        Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
        Gravity.TOP|Gravity.RIGHT, Gravity.CENTER_VERTICAL|Gravity.RIGHT, Gravity.BOTTOM|Gravity.RIGHT,
        Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT,
        Gravity.TOP|Gravity.LEFT, Gravity.CENTER_VERTICAL|Gravity.LEFT, Gravity.BOTTOM|Gravity.LEFT
    };
    private static final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private static final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    private WindowManager wm;
    private KeyguardManager km;
    private SharedPreferences prefs;
    private CameraManager cm;
    private String camId;
    private boolean torchOn = false;
    private Vibrator vibrator;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final View[] bars = new View[12];
    private final View[] corners = new View[4];
    private final java.util.Set<String> hiddenKeys = new java.util.HashSet<>(); // "r", "corner_br"...
    private PanelEngine panelEngine;
    private AssistiveBubbleEngine bubbleEngine;
    private boolean rxOn = false;

    // ---------- View ----------
    private static class BarView extends View {
        BarView(Context c, int alpha, float radius) {
            super(c);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(radius);
            gd.setColor(Color.argb(alpha, 96, 125, 139));
            setBackground(gd);
        }
    }

    /** Bản sao đúng thuật toán CornerView của EdgeBarService (stroke + trăng lưỡi liềm). */
    private static class CornerView extends View {
        private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int type; private final String ck; private final SharedPreferences prefs;
        CornerView(Context c, SharedPreferences prefs, int type, String ck, int thick, int moonAlpha, int strokeAlpha) {
            super(c);
            this.type = type; this.ck = ck; this.prefs = prefs;
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(Color.argb(moonAlpha, 96, 125, 139));
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeCap(Paint.Cap.ROUND);
            pStroke.setStrokeJoin(Paint.Join.ROUND);
            pStroke.setStrokeWidth(thick);
            pStroke.setColor(Color.argb(strokeAlpha, 255, 255, 255));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float tw = getWidth(), th = getHeight(), pad = pStroke.getStrokeWidth() / 2;
            int shapeMode = prefs.getInt(ck+"shape", 0);
            float sRad = prefs.getInt(ck+"rad", 80) / 1000f, mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
            float sw = prefs.getInt(ck+"w", 100), sh = prefs.getInt(ck+"h", 100);
            float mw = prefs.getInt(ck+"moon_w", 100), mh = prefs.getInt(ck+"moon_h", 100);
            Path moonPath = new Path(), strokePath = new Path();
            float sRootX, sRootY, sTipX, sTipY, sCtrlX, sCtrlY, mRootX, mRootY, mTipX, mTipY, mCtrlX, mCtrlY;
            if (type == 0) { // BR
                sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
            } else if (type == 1) { // BL
                sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
            } else if (type == 2) { // TR
                sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
            } else { // TL
                sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=0; mTipX=mw; mTipY=mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
            }
            if (shapeMode == 1) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sTipX, sRootY); }
            else if (shapeMode == 2) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sRootX, sTipY); }
            else { strokePath.moveTo(sRootX, sTipY); strokePath.quadTo(sCtrlX, sCtrlY, sTipX, sRootY); }
            if (type == 0 || type == 1) { moonPath.moveTo(mRootX, mTipY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mTipX, mRootY); moonPath.quadTo(mCtrlX, mCtrlY, mRootX, mTipY); }
            else { moonPath.moveTo(mTipX, mRootY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mRootX, mTipY); moonPath.quadTo(mCtrlX, mCtrlY, mTipX, mRootY); }
            moonPath.close();
            canvas.drawPath(strokePath, pStroke);
            canvas.save();
            canvas.translate(prefs.getInt(ck+"moon_x", 1250) - 1250, prefs.getInt(ck+"moon_y", 1250) - 1250);
            canvas.drawPath(moonPath, pFill);
            canvas.restore();
        }
    }

    // ---------- Vòng đời ----------
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { camId = cm.getCameraIdList()[0]; } catch (Exception ignored) {}

        startForegroundQuiet(); // BẮT BUỘC gọi trước mọi lệnh stopSelf()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        createViews();
        applyVisibility();

        try {
            panelEngine = new PanelEngine(this, wm, prefs, false, true);
            panelEngine.rebuildAll();
        } catch (Exception ignored) {}
        if (prefs.getBoolean("bubble_en", false) || prefs.getBoolean("bubble_circle_en", false)) {
            try { bubbleEngine = new AssistiveBubbleEngine(this, wm, prefs, false, true); bubbleEngine.rebuild(); }
            catch (Exception ignored) {}
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction("com.manhmoc.edgebar.STOP_LOCK_EB");
        f.addAction("com.manhmoc.edgebar.LOCKEB_FG");
        f.addAction("com.manhmoc.edgebar.IPC_ACTION");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(rx, f);
        rxOn = true;
    }

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if ("com.manhmoc.edgebar.STOP_LOCK_EB".equals(a)) { stopSelf(); return; }
            if ("com.manhmoc.edgebar.IPC_ACTION".equals(a)) { handleIpc(i); return; }
            applyVisibility(); // LOCKEB_FG / SCREEN_ON / USER_PRESENT
            if (Intent.ACTION_USER_PRESENT.equals(a)) {
                // dự phòng: watchdog đã kết thúc mà LockEb còn sót -> tự tắt
                h.postDelayed(() -> { if (!prefs.getBoolean("blacklist_lock_active", false)) stopSelf(); }, 500);
            }
        }
    };

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

    // ---------- Tạo view (chỉ cái đang bật) ----------
    private WindowManager.LayoutParams lp(int w, int hh, int gravity, int x, int y, int pri) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        flags |= (pri == 1) ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            : (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, hh,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        p.gravity = gravity; p.x = x; p.y = y;
        return p;
    }

    private void createViews() {
        for (int i = 0; i < 12; i++) {
            String k = "lock_" + BARS[i];
            if (!prefs.getBoolean(k + "_en", false)) continue;
            int visMode = prefs.getInt(k + "_vis_mode", 0);
            int alpha = visMode == 0 ? prefs.getInt(k + "_alpha", 50) : 0;
            BarView v = new BarView(this, alpha, prefs.getInt("lock_bar_radius", 24));
            WindowManager.LayoutParams p = lp(prefs.getInt(k + "_w", 300), prefs.getInt(k + "_h", 60), GRAV[i],
                prefs.getInt(k + "_x", 0), prefs.getInt(k + "_y", 0), prefs.getInt(k + "_pri_mode", 0));
            try { wm.addView(v, p); bars[i] = v; } catch (Exception ignored) { continue; }
            v.setOnTouchListener(new Gesture(k));
        }
        for (int i = 0; i < 4; i++) {
            String ck = "lock_corner_" + CORNERS[i] + "_";
            if (!prefs.getBoolean("lock_corner_" + CORNERS[i] + "_en", false)) continue;
            int visMode = prefs.getInt(ck + "vis_mode", 0);
            int moonA = visMode == 0 ? prefs.getInt("lock_corner_moon_alpha", 100) : 0;
            int strokeA = visMode == 0 ? prefs.getInt("lock_corner_stroke_alpha", 200) : 0;
            CornerView v = new CornerView(this, prefs, i, ck, prefs.getInt("lock_corner_thick", 8), moonA, strokeA);
            int wp = prefs.getInt(ck + "w", 100), hp = prefs.getInt(ck + "h", 100);
            int mw = prefs.getInt(ck + "moon_w", 100), mh = prefs.getInt(ck + "moon_h", 100);
            int mx = Math.abs(prefs.getInt(ck + "moon_x", 1250) - 1250), my = Math.abs(prefs.getInt(ck + "moon_y", 1250) - 1250);
            WindowManager.LayoutParams p = lp(Math.max(10, Math.max(wp, mw) + mx), Math.max(10, Math.max(hp, mh) + my),
                C_GRAV[i], prefs.getInt(ck + "x", 0), prefs.getInt(ck + "y", 0), prefs.getInt(ck + "pri_mode", 0));
            try { wm.addView(v, p); corners[i] = v; } catch (Exception ignored) { continue; }
            v.setOnTouchListener(new Gesture("lock_corner_" + CORNERS[i]));
        }
    }

    /** Chế độ "Chỉ màn khoá gốc": ẩn khi máy đã mở khoá hoặc app Blacklist đang ở foreground. */
    /** Có app Blacklist đang ở foreground không (cờ do Watchdog cập nhật). */
    private boolean blFg() { return prefs.getBoolean("blacklist_lock_fg", false); }

    private void applyVisibility() {
        boolean locked = km != null && km.isKeyguardLocked();
        boolean fg = blFg();
        // Đã mở khoá và không có app Blacklist ở foreground = đang ở Home/app thường
        // -> LockEb nhường hẳn cho Homacc/Homeb, ẩn TẤT CẢ (kể cả bar "Luôn xuyên suốt")
        boolean show = locked || fg;
        boolean secure = !locked || fg;  // only-base: ẩn khi mở khoá hoặc app Blacklist che màn
        for (int i = 0; i < 12; i++) {
            if (bars[i] == null) continue;
            boolean gate = show && (prefs.getInt("lock_" + BARS[i] + "_lockmode", 1) == 1 || !secure);
            bars[i].setVisibility(gate && !hiddenKeys.contains(BARS[i]) ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < 4; i++) {
            if (corners[i] == null) continue;
            boolean gate = show && (prefs.getInt("lock_corner_" + CORNERS[i] + "_lockmode", 1) == 1 || !secure);
            corners[i].setVisibility(gate && !hiddenKeys.contains("corner_" + CORNERS[i]) ? View.VISIBLE : View.GONE);
        }
    }

    // ---------- Cử chỉ: tap / dtap / long / 4 hướng ----------
    private class Gesture implements View.OnTouchListener {
        final String keyBase; float sx, sy; boolean longFired, multi;
        long lastTapMs = 0; Runnable pendingTap;
        final Runnable longCheck;
        static final int SLOP = 60, DTAP_MS = 280;
        Gesture(String keyBase) {
            this.keyBase = keyBase;
            longCheck = () -> { longFired = true; handleAction(keyBase + "_long"); };
        }
        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX(); sy = e.getRawY(); longFired = false; multi = false;
                    h.postDelayed(longCheck, prefs.getInt("hold_dur", 600));
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    multi = true; h.removeCallbacks(longCheck); return true;
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(longCheck); return true;
                case MotionEvent.ACTION_UP: {
                    h.removeCallbacks(longCheck);
                    if (longFired || multi) return true;
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy, adx = Math.abs(dx), ady = Math.abs(dy);
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
                        handleAction(keyBase + "_" + (adx > ady ? (dx > 0 ? "right" : "left") : (dy > 0 ? "down" : "up")));
                    }
                    return true;
                }
            }
            return true;
        }
    }

    private void handleAction(String key) {
        String action = prefs.getString(key, "NONE");
        if (action.equals("NONE") || !prefs.getBoolean(key + "_on", true)) return;
        if (prefs.getBoolean(key + "_vib", true)) doVibrate(prefs.getInt("vib_dur", 30));
        if (prefs.getBoolean(key + "_snd", false)) TouchSoundHelper.play(this, prefs);
        for (String a : action.split(",")) {
            String at = a.trim();
            if (at.equals("LAUNCH_APP")) launchPkg(prefs.getString(key + "_launch_pkg", ""));
            else if (at.equals("RUN_SHORTCUT")) runShortcut(prefs.getString(key + "_shortcut_id", ""));
            else exec(at);
        }
    }

    private void handleIpc(Intent i) {
        String act = i.getStringExtra("act");
        if (act == null) return;
        if (act.equals("LAUNCH_APP")) launchPkg(i.getStringExtra("launch_pkg"));
        else if (act.equals("RUN_SHORTCUT")) runShortcut(i.getStringExtra("shortcut_id"));
        else exec(act);
    }

    private void doVibrate(int ms) {
        if (ms <= 0 || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(ms);
        } catch (Exception ignored) {}
    }

    private void launchPkg(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
        } catch (Exception ignored) {}
    }

    private void runShortcut(String id) {
        if (id == null || id.isEmpty()) return;
        try {
            String uri = prefs.getString("shortcut_" + id + "_intent_uri", "");
            if (uri.isEmpty()) return;
            Intent it = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception ignored) {}
    }

    private void fireIntentById(String id) {
        try {
            String act = prefs.getString("intent_" + id + "_act", "");
            String pkg = prefs.getString("intent_" + id + "_pkg", "");
            Intent i;
            if (act.isEmpty() && !pkg.isEmpty()) {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) return;
            } else if (act.isEmpty()) return;
            else {
                i = new Intent(act);
                if (!pkg.isEmpty()) i.setPackage(pkg);
                String cls = prefs.getString("intent_" + id + "_cls", "");
                if (!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new ComponentName(pkg, cls));
                String data = prefs.getString("intent_" + id + "_data", "");
                if (!data.isEmpty()) i.setData(android.net.Uri.parse(data));
                String cat = prefs.getString("intent_" + id + "_cat", "");
                if (!cat.isEmpty()) i.addCategory(cat);
                String flg = prefs.getString("intent_" + id + "_flags", "");
                if (!flg.isEmpty()) i.addFlags(Integer.parseInt(flg));
            }
            if (prefs.getBoolean("intent_" + id + "_br", false) && !act.isEmpty()) sendBroadcast(i);
            else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Exception ignored) {}
    }

    // ---------- Action không cần Trợ năng (giống Homeb) ----------
    private void exec(String a) {
        if (a == null || a.isEmpty() || a.equals("NONE")) return;
        try {
            switch (a) {
                case "HOME": {
                    Intent home = new Intent(Intent.ACTION_MAIN);
                    home.addCategory(Intent.CATEGORY_HOME);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(home); break;
                }
                case "FLASH": if (camId != null) { torchOn = !torchOn; cm.setTorchMode(camId, torchOn); } break;
                case "CAMERA": {
                    Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                    c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break;
                }
                case "VOLUME":
                    ((AudioManager) getSystemService(AUDIO_SERVICE)).adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    break;
                case "SCREEN_OFF": {
                    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm.isAdminActive(new ComponentName(this, HomebDeviceAdminReceiver.class))) dpm.lockNow();
                    break;
                }
                case "SCREEN_ON": {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    if (pm != null && !pm.isInteractive()) {
                        android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "EdgeBar:LockEbOn");
                        wl.acquire(2000);
                    }
                    break;
                }
                case "TOGGLE_RECORD": case "PAUSE_RECORD": {
                    if (a.equals("PAUSE_RECORD") && !VoiceRecorderService.isRunning) break;
                    Intent i = new Intent(this, VoiceRecorderService.class);
                    i.setAction(a.equals("TOGGLE_RECORD") ? VoiceRecorderService.ACTION_TOGGLE : VoiceRecorderService.ACTION_PAUSE_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    break;
                }
                case "PLAY_MY_PLAYLIST": {
                    Intent i = new Intent(this, MyPlaylistService.class);
                    i.setAction(MyPlaylistService.ACTION_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    break;
                }
                case "SCAN_QR": {
                    Intent qr = new Intent(this, QrScanActivity.class);
                    qr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    startActivity(qr); break;
                }
                case "AUTO_ROTATE_TOGGLE":
                    if (Settings.System.canWrite(this)) {
                        int cur = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, cur == 1 ? 0 : 1);
                    }
                    break;
                case "TOGGLE_WORK_PROFILE": {
                    android.os.UserManager um = (android.os.UserManager) getSystemService(Context.USER_SERVICE);
                    android.os.UserHandle work = null;
                    for (android.os.UserHandle uh : um.getUserProfiles())
                        if (!uh.equals(android.os.Process.myUserHandle())) { work = uh; break; }
                    if (work != null) um.requestQuietModeEnabled(!um.isQuietModeEnabled(work), work);
                    break;
                }
                case "HIDE_SOME_OVERLAY": {
                    String t = prefs.getString("lock_bar_hide_targets", "") + "," + prefs.getString("lock_corner_hide_targets", "");
                    for (String s : t.split(",")) { String x = s.trim(); if (!x.isEmpty()) hiddenKeys.add(x); }
                    applyVisibility(); break;
                }
                case "SHOW_ALL_OVERLAY": hiddenKeys.clear(); applyVisibility(); break;
                default:
                    if (a.startsWith("PANEL_")) { if (panelEngine != null) panelEngine.togglePanel(a.substring(6)); }
                    else if (a.startsWith("INTENT_")) fireIntentById(a.substring(7));
                    else if (a.startsWith("MACRO_")) {
                        Intent m = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                        m.putExtra("services", prefs.getString("macro_" + a.substring(6) + "_svcs", ""));
                        sendBroadcast(m);
                    } else if (a.startsWith("RUN_SHORTCUT_")) runShortcut(a.substring(13));
                    break; // TRIGGER_*, BACK, RECENTS... cần Trợ năng -> bỏ qua
            }
        } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (rxOn) { try { unregisterReceiver(rx); } catch (Exception ignored) {} rxOn = false; }
        if (panelEngine != null) { try { panelEngine.destroy(); } catch (Exception ignored) {} panelEngine = null; }
        if (bubbleEngine != null) { try { bubbleEngine.destroy(); } catch (Exception ignored) {} bubbleEngine = null; }
        for (int i = 0; i < 12; i++) if (bars[i] != null) { try { wm.removeView(bars[i]); } catch (Exception ignored) {} bars[i] = null; }
        for (int i = 0; i < 4; i++) if (corners[i] != null) { try { wm.removeView(corners[i]); } catch (Exception ignored) {} corners[i] = null; }
        super.onDestroy();
    }
}
