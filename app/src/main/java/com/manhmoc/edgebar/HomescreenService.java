package com.manhmoc.edgebar;

import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Random;

public class HomescreenService extends Service {
    public static boolean isRunning = false;
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private RelativeLayout morseContainer;
    private TextView tvMorseStatus;
    private View scratchView; // hiệu ứng vỡ màn
    private View[] mBars = new View[8];
    private View[] mCorners = new View[4];
    private FlashView fV;
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private SharedPreferences prefs;
    private KeyguardManager km;
    private Vibrator vibrator;

    private boolean isMorseLockActive = false;
    private String currentMorseAttempt = "";
    private int morseFailCount = 0;
    private String lockedPkg = "";
    private Handler morseDotHandler = new Handler();

    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL};

    private final String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
    private final int[] M_GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.CENTER};

    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
        if (k != null) {
            updateVisibility();
            if (fV != null) fV.updateStyle();
        }
    };

    private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getAction().equals("com.manhmoc.edgebar.SYNC_STATE")) {
                isKbd = i.getBooleanExtra("isKbd", false);
                isBl = i.getBooleanExtra("isBl", false);
                updateVisibility();
            } else if (i.getAction().equals("com.manhmoc.edgebar.TEST_ANIM")) {
                playAnim();
            } else if (i.getAction().equals("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE")) {
                isMorseLockActive = true;
                lockedPkg = i.getStringExtra("pkg");
                morseFailCount = 0;
                currentMorseAttempt = "";
                tvMorseStatus.setText("");
                updateVisibility();
            } else if (i.getAction().equals("com.manhmoc.edgebar.TOGGLE_MORSE")) {
                boolean isM = prefs.getBoolean("morse_mode_en", false);
                prefs.edit().putBoolean("morse_mode_en", !isM).apply();
                updateVisibility();
            }
        }
    };

    // Ánh xạ động từ SharedPreferences
    private String mapComponentToNumber(String comp) {
        String key = "morse_map_" + comp.replace("morse_", "").replace("home_", "").replace("lock_", "").replace("corner_", "");
        return prefs.getString(key, "*");
    }

    // Lớp vẽ hiệu ứng sọc màn
    private class ScratchView extends View {
        private Paint paint = new Paint();
        private Random random = new Random();
        private int[] crackPoints;

        public ScratchView(Context context) {
            super(context);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(6);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            generateCracks();
        }

        private void generateCracks() {
            crackPoints = new int[20];
            for (int i = 0; i < 20; i += 2) {
                crackPoints[i] = random.nextInt(2000);
                crackPoints[i + 1] = random.nextInt(3000);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (crackPoints == null) generateCracks();
            paint.setAlpha(80);
            for (int i = 0; i < crackPoints.length - 2; i += 2) {
                canvas.drawLine(crackPoints[i], crackPoints[i + 1], crackPoints[i + 2], crackPoints[i + 3], paint);
            }
            // vẽ thêm các vết nứt chéo
            paint.setStrokeWidth(3);
            paint.setAlpha(120);
            for (int i = 0; i < 15; i++) {
                int x1 = random.nextInt(getWidth());
                int y1 = random.nextInt(getHeight());
                int x2 = x1 + random.nextInt(200) - 100;
                int y2 = y1 + random.nextInt(200) - 100;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
            invalidate(); // liên tục vẽ lại để tạo cảm giác động
        }
    }

    private class FlashView extends View {
        // ... giữ nguyên từ bản cũ
    }

    private class CornerView extends View {
        // ... giữ nguyên từ bản cũ
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        sendSyncState();
        return START_STICKY;
    }

    private void sendSyncState() {
        Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE");
        sendBroadcast(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction("com.manhmoc.edgebar.SYNC_STATE");
        filter.addAction("com.manhmoc.edgebar.TEST_ANIM");
        filter.addAction("com.manhmoc.edgebar.IPC_ACTION");
        filter.addAction("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
        filter.addAction("com.manhmoc.edgebar.TOGGLE_MORSE");
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(syncReceiver, filter);

        String cid = "eb_19_home";
        NotificationChannel c = new NotificationChannel(cid, "Edge Bar Màn Chính", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Edge Bar Màn Chính")
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setOngoing(true).build();
        startForeground(2, n);

        fV = new FlashView(this);
        fV.setAlpha(0f);
        fV.setVisibility(View.GONE);
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        try { wm.addView(fV, fp); } catch (Exception e) {}

        // Home layers
        for (int i = 0; i < 5; i++) {
            bars[i] = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch (Exception e) {}
            bars[i].setOnTouchListener(new SidebarTouchListener("home_" + BARS[i], null));
        }
        for (int i = 0; i < 4; i++) {
            corners[i] = new CornerView(this, i, "home_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(corners[i], p); } catch (Exception e) {}
            corners[i].setOnTouchListener(new SidebarTouchListener("home_corner_" + CORNERS[i], corners[i]));
        }

        // Morse container
        morseContainer = new RelativeLayout(this);
        morseContainer.setBackgroundColor(Color.BLACK);
        morseContainer.setVisibility(View.GONE);
        // Chỉ chặn touch khi đang khóa app, không chặn khi preview
        morseContainer.setOnTouchListener((v, e) -> {
            // Nếu là preview thì không chặn (cho phép chạm vào thanh bar)
            boolean isPreview = prefs.getBoolean("preview_morse", false);
            return !isPreview; // true = chặn, false = không chặn
        });

        tvMorseStatus = new TextView(this);
        tvMorseStatus.setTextColor(Color.WHITE);
        tvMorseStatus.setTextSize(30);
        tvMorseStatus.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(-1, -2);
        tLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        morseContainer.addView(tvMorseStatus, tLp);

        // Thêm hiệu ứng sọc màn
        scratchView = new ScratchView(this);
        morseContainer.addView(scratchView, new RelativeLayout.LayoutParams(-1, -1));

        WindowManager.LayoutParams bgP = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { wm.addView(morseContainer, bgP); } catch (Exception e) {}

        // Morse bars & corners
        for (int i = 0; i < 8; i++) {
            mBars[i] = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mBars[i], p); } catch (Exception e) {}
            mBars[i].setOnTouchListener(new SidebarTouchListener("morse_" + M_BARS[i], null));
        }
        for (int i = 0; i < 4; i++) {
            mCorners[i] = new CornerView(this, i, "morse_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mCorners[i], p); } catch (Exception e) {}
            mCorners[i].setOnTouchListener(new SidebarTouchListener("morse_corner_" + CORNERS[i], mCorners[i]));
        }

        updateVisibility();
        sendSyncState();
    }

    private void updateVisibility() {
        boolean isUnlocked = !km.isKeyguardLocked();
        boolean avoidKbd = prefs.getBoolean("avoid_kbd", true);
        boolean hideNormal = (avoidKbd && isKbd) || isBl;
        boolean isPreviewMorse = prefs.getBoolean("preview_morse", false);

        if (hideNormal && fV != null && !isMorseLockActive && !isPreviewMorse) fV.setVisibility(View.GONE);

        if (isMorseLockActive || isPreviewMorse) {
            morseContainer.setVisibility(View.VISIBLE);
            morseContainer.setAlpha(prefs.getInt("morse_bg_alpha", 180) / 255f);

            // Ẩn home layers
            for (int i = 0; i < 5; i++) if (bars[i] != null) bars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (corners[i] != null) corners[i].setVisibility(View.GONE);

            // Hiện morse bars
            for (int i = 0; i < 8; i++) {
                if (mBars[i] == null) continue;
                boolean en = prefs.getBoolean("morse_" + M_BARS[i] + "_en", false);
                mBars[i].setVisibility(en ? View.VISIBLE : View.GONE);
                if (en) {
                    int alpha = prefs.getInt("morse_" + M_BARS[i] + "_alpha", 50);
                    int w = prefs.getInt("morse_" + M_BARS[i] + "_w", 300);
                    int h = prefs.getInt("morse_" + M_BARS[i] + "_h", 60);
                    int x = prefs.getInt("morse_" + M_BARS[i] + "_x", 0);
                    int y = prefs.getInt("morse_" + M_BARS[i] + "_y", 0);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setColor(Color.argb(alpha, 96, 125, 139));
                    gd.setCornerRadius(24f);
                    mBars[i].setBackground(gd);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) mBars[i].getLayoutParams();
                    p.flags = baseFlags;
                    p.width = w;
                    p.height = h;
                    p.x = x;
                    p.y = y;
                    p.gravity = M_GRAV[i];
                    wm.updateViewLayout(mBars[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mBars[i].getVisibility() == View.VISIBLE) {
                        Rect rect = new Rect(0, 0, w, h);
                        mBars[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
                    }
                }
            }
            // Hiện morse corners
            for (int i = 0; i < 4; i++) {
                if (mCorners[i] == null) continue;
                boolean cornEn = prefs.getBoolean("morse_corner_" + CORNERS[i] + "_en", false);
                mCorners[i].setVisibility(cornEn ? View.VISIBLE : View.GONE);
                if (cornEn) {
                    String ck = "morse_corner_" + CORNERS[i] + "_";
                    int moonAlpha = prefs.getInt("morse_corner_moon_alpha", 100);
                    int strokeAlpha = prefs.getInt("morse_corner_stroke_alpha", 200);
                    int hideDelay = prefs.getInt("morse_corner_hide_dur", 2500);
                    int visMode = prefs.getInt(ck + "vis_mode", 0);
                    boolean isAuto = (visMode == 1);
                    boolean isInv = (visMode == 2);
                    ((CornerView) mCorners[i]).updateProps(prefs.getInt("morse_corner_thick", 8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) mCorners[i].getLayoutParams();
                    p.flags = baseFlags;
                    p.gravity = C_GRAV[i];
                    int wPref = prefs.getInt(ck + "w", 100);
                    int hPref = prefs.getInt(ck + "h", 100);
                    int mwPref = prefs.getInt(ck + "moon_w", 100);
                    int mhPref = prefs.getInt(ck + "moon_h", 100);
                    int mxOffset = Math.abs(prefs.getInt(ck + "moon_x", 1250) - 1250);
                    int myOffset = Math.abs(prefs.getInt(ck + "moon_y", 1250) - 1250);
                    p.width = Math.max(10, Math.max(wPref, mwPref) + mxOffset);
                    p.height = Math.max(10, Math.max(hPref, mhPref) + myOffset);
                    p.x = prefs.getInt(ck + "x", 0);
                    p.y = prefs.getInt(ck + "y", 0);
                    wm.updateViewLayout(mCorners[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mCorners[i].getVisibility() == View.VISIBLE) {
                        Rect rect = new Rect(0, 0, p.width, p.height);
                        mCorners[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
                    }
                }
            }
        } else {
            morseContainer.setVisibility(View.GONE);
            for (int i = 0; i < 8; i++) if (mBars[i] != null) mBars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (mCorners[i] != null) mCorners[i].setVisibility(View.GONE);

            // Home layers
            boolean isPreviewLock = prefs.getBoolean("preview_lock", false);
            for (int i = 0; i < 5; i++) {
                if (bars[i] == null) continue;
                boolean en = prefs.getBoolean("home_" + BARS[i] + "_en", false);
                bars[i].setVisibility((en && isUnlocked && !hideNormal) ? View.VISIBLE : View.GONE);
                if (en && isUnlocked) {
                    int alpha = isPreviewLock ? 0 : prefs.getInt("home_" + BARS[i] + "_alpha", 50);
                    int w = prefs.getInt("home_" + BARS[i] + "_w", 300);
                    int h = prefs.getInt("home_" + BARS[i] + "_h", 60);
                    int x = prefs.getInt("home_" + BARS[i] + "_x", 0);
                    int y = prefs.getInt("home_" + BARS[i] + "_y", 0);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setColor(Color.argb(alpha, 96, 125, 139));
                    gd.setCornerRadius(24f);
                    bars[i].setBackground(gd);
                    int priMode = prefs.getInt("home_" + BARS[i] + "_pri_mode", 0);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                    if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
                    p.flags = baseFlags;
                    p.width = w;
                    p.height = h;
                    p.x = x;
                    p.y = y;
                    p.gravity = GRAV[i];
                    wm.updateViewLayout(bars[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bars[i].getVisibility() == View.VISIBLE && priMode == 0) {
                        Rect rect = new Rect(0, 0, w, h);
                        bars[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                if (corners[i] == null) continue;
                boolean cornEn = prefs.getBoolean("home_corner_" + CORNERS[i] + "_en", false);
                corners[i].setVisibility((cornEn && isUnlocked && !hideNormal) ? View.VISIBLE : View.GONE);
                if (cornEn && isUnlocked) {
                    String ck = "home_corner_" + CORNERS[i] + "_";
                    int moonAlpha = isPreviewLock ? 0 : prefs.getInt("home_corner_moon_alpha", 100);
                    int strokeAlpha = isPreviewLock ? 0 : prefs.getInt("home_corner_stroke_alpha", 200);
                    int hideDelay = prefs.getInt("home_corner_hide_dur", 2500);
                    int visMode = prefs.getInt(ck + "vis_mode", 0);
                    boolean isAuto = (visMode == 1);
                    boolean isInv = (visMode == 2);
                    ((CornerView) corners[i]).updateProps(prefs.getInt("home_corner_thick", 8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                    int priMode = prefs.getInt(ck + "pri_mode", 0);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                    if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
                    p.flags = baseFlags;
                    p.gravity = C_GRAV[i];
                    int wPref = prefs.getInt(ck + "w", 100);
                    int hPref = prefs.getInt(ck + "h", 100);
                    int mwPref = prefs.getInt(ck + "moon_w", 100);
                    int mhPref = prefs.getInt(ck + "moon_h", 100);
                    int mxOffset = Math.abs(prefs.getInt(ck + "moon_x", 1250) - 1250);
                    int myOffset = Math.abs(prefs.getInt(ck + "moon_y", 1250) - 1250);
                    p.width = Math.max(10, Math.max(wPref, mwPref) + mxOffset);
                    p.height = Math.max(10, Math.max(hPref, mhPref) + myOffset);
                    p.x = prefs.getInt(ck + "x", 0);
                    p.y = prefs.getInt(ck + "y", 0);
                    wm.updateViewLayout(corners[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && corners[i].getVisibility() == View.VISIBLE && priMode == 0) {
                        Rect rect = new Rect(0, 0, p.width, p.height);
                        corners[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
                    }
                }
            }
        }
    }

    private void handleMorseTap(String comp, View v) {
        doVibrate(30);
        if (v != null && v instanceof CornerView) ((CornerView) v).triggerFlash();

        String mappedKey = mapComponentToNumber(comp);

        if (mappedKey.equals("X")) {
            currentMorseAttempt = "";
            tvMorseStatus.setText("");
        } else if (mappedKey.equals(">")) {
            String masterPass = prefs.getString("morse_master_pass", "");
            if (currentMorseAttempt.isEmpty() || masterPass.isEmpty()) return;
            // Loại bỏ dấu > nếu có trong masterPass (đề phòng)
            String cleanMaster = masterPass.replace(">", "");
            if (currentMorseAttempt.equals(cleanMaster)) {
                isMorseLockActive = false;
                morseFailCount = 0;
                Intent i = new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
                i.putExtra("pkg", lockedPkg);
                sendBroadcast(i);
                updateVisibility();
            } else {
                morseFailCount++;
                int failVib = prefs.getInt("morse_fail_vib", 500);
                doVibrate(failVib);
                if (morseFailCount == 1)
                    tvMorseStatus.setText(prefs.getString("morse_insult_1", "Who are u?"));
                else if (morseFailCount == 2)
                    tvMorseStatus.setText(prefs.getString("morse_insult_2", "What are u doing?"));
                else {
                    tvMorseStatus.setText(prefs.getString("morse_insult_3", "Get out!"));
                    Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                    kick.putExtra("act", "HOME");
                    sendBroadcast(kick);
                    isMorseLockActive = false;
                    new Handler().postDelayed(() -> updateVisibility(), 500);
                }
                currentMorseAttempt = "";
            }
        } else {
            // Nếu là số (0-9)
            currentMorseAttempt += mappedKey;
            tvMorseStatus.setText(currentMorseAttempt);
            morseDotHandler.removeCallbacksAndMessages(null);
            int dotDelay = prefs.getInt("morse_dot_delay", 500);
            morseDotHandler.postDelayed(() -> {
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < currentMorseAttempt.length(); i++) dots.append("• ");
                tvMorseStatus.setText(dots.toString());
            }, dotDelay);
        }
    }

    private void playAnim() {
        // giữ nguyên
    }

    private void handleAction(String key) {
        String action = prefs.getString(key, "NONE");
        boolean isOn = prefs.getBoolean(key + "_on", true);
        if (!action.equals("NONE") && isOn) {
            if (prefs.getBoolean(key + "_vib", true)) doVibrate(prefs.getInt("vib_dur", 30));
            if (prefs.getBoolean(key + "_anim", true)) playAnim();
            String[] acts = action.split(",");
            for (String a : acts) exec(a.trim());
        }
    }

    private void doVibrate(int dur) {
        if (dur <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(dur);
        } catch (Exception e) {}
    }

    private void exec(String a) {
        // giữ nguyên từ bản cũ (đã có xử lý TOGGLE_MORSE, v.v.)
        // ...
    }

    private class SidebarTouchListener implements View.OnTouchListener {
        // giữ nguyên, nhưng bổ sung gọi handleMorseTap khi đang ở chế độ morse
        // ...
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try { unregisterReceiver(syncReceiver); } catch (Exception e) {}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        // remove views...
    }
}
