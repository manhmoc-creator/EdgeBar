package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.FingerprintGestureController;
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.DashPathEffect;
import android.graphics.BlurMaskFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
// ĐẰNG TRƯỚC (Có thể là các dòng import cuối cùng)
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


public class EdgeBarService extends AccessibilityService {

    // === CHÈN CODE BIẾN TOÀN CỤC CỦA BẠN VÀO ĐÂY ===
    // Động cơ Twin-Engine Trợ năng
private android.view.View[] accHomeBars = new android.view.View[12];
private android.view.View[] accHomeCorners = new android.view.View[4];
private android.content.BroadcastReceiver accHomeReceiver;
private boolean isHomaccDrawn = false; // Guard chặn vẽ lại khi đã có view
// THÊM MỚI — cache trạng thái preview để tránh gọi drawAccessibleHome()/removeAccessibleHome()
// lặp lại mỗi lần updateVisibility() chạy (event này bắn khá thường xuyên).
// So sánh giá trị cache TRƯỚC khi đụng vào WindowManager — đây là nguyên tắc
// "chỉ IPC khi giá trị thực sự đổi" đã áp dụng xuyên suốt codebase (giống homaccDebounce).
private boolean lastPreviewHomaccState = false;
private FingerprintGestureController fpController;
private FingerprintGestureController.FingerprintGestureCallback fpCallback;
private boolean fpRegistered = false;
// [MỚI] AppLock — lưu mốc thời gian unlock gần nhất theo RAM, không ghi prefs
    public static void markPackageUnlocked(String pkg) { AppLockHelper.markUnlocked(pkg); }
    private void checkAppLock(String pkg) { AppLockHelper.check(this, prefs, pkg); }
    // ĐẰNG SAU (Các biến cũ của EdgeBarService)
// [FIX ĐỆ QUY] Cờ chặn: đang bắn touch ảo TRIGGER_* xuống màn hình
    private volatile boolean isDispatchingSyntheticGesture = false;
    private final Handler syntheticGuardHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable syntheticGuardResetRunnable;
    // [MỚI] Lưu toạ độ chạm thực tế để giả lập cử chỉ đích xác
    private float globalTouchStartX = -1f, globalTouchStartY = -1f, globalTouchEndX = -1f, globalTouchEndY = -1f;
    private WindowManager wm;
    private View[] bars = new View[12];
    private View[] corners = new View[4];
    private FlashView fV;
    private GestureRippleView rippleView;
    // [MỚI] Chỉ báo ghi âm (chấm đỏ + mm:ss)
    private LinearLayout recIndicatorView;
private TextView recIndicatorText;
private View recIndicatorDot;
private ValueAnimator recBlinkAnim;
private boolean recIndicatorTestMode = false;
private boolean recIndicatorTestPaused = false;

    private void ensureRippleView() {
    if (rippleView != null) return;
    GestureRippleView newView = new GestureRippleView(this);
    WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1,-1,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT);
    try {
        wm.addView(newView, p);
        rippleView = newView; // chỉ gán field khi addView() THÀNH CÔNG
    } catch (Exception ignored) {
        rippleView = null; // không để lại view "ma" chưa gắn vào WindowManager
    }
}

    private void removeRippleViewIfIdle() {
        if (rippleView == null) return;
        try { wm.removeView(rippleView); } catch (Exception ignored) {}
        rippleView = null;
    }
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private KeyguardManager km;
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private PanelEngine panelEngine;
    private final String[] BARS = {"b_c", "r", "l", "r_u", "r_c", "r_d", "t_c", "t_r", "t_l", "l_u", "l_c", "l_d"};
    private final int[] GRAV = {
        Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
        Gravity.TOP|Gravity.RIGHT, Gravity.CENTER_VERTICAL|Gravity.RIGHT, Gravity.BOTTOM|Gravity.RIGHT,
        Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT,
        Gravity.TOP|Gravity.LEFT, Gravity.CENTER_VERTICAL|Gravity.LEFT, Gravity.BOTTOM|Gravity.LEFT
    };
    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};
// [FIX LONG-PRESS] Cache chữ ký layout — chỉ gọi updateViewLayout() khi THẬT SỰ đổi.
// Gọi updateViewLayout() vô điều kiện là nguyên nhân khiến hệ thống tự hủy gesture
// (ACTION_CANCEL) đang diễn ra trên View đó, làm hỏng bộ đếm long-press.
private final java.util.Map<View, String> lastLayoutSig = new java.util.HashMap<>();
private void updateLayoutIfChanged(View v, WindowManager.LayoutParams p) {
    String sig = p.flags + "|" + p.width + "|" + p.height + "|" + p.x + "|" + p.y + "|" + p.gravity;
    if (sig.equals(lastLayoutSig.get(v))) return; // không đổi -> zero IPC, giữ gesture nguyên vẹn
    lastLayoutSig.put(v, sig);
    try { wm.updateViewLayout(v, p); } catch (Exception ignored) {}
}
private final java.util.Map<View, String> lastGestureSig = new java.util.HashMap<>();
private void applyAntiTapjacking(View v, int w, int h) {
    // [FIX VUỐT CẠNH KHÔNG BACK ĐƯỢC] Không loại trừ cử chỉ hệ thống nữa — việc
    // gọi setSystemGestureExclusionRects() ở đây vô tình chặn luôn cử chỉ vuốt
    // cạnh Back của Android tại vị trí Bar/Corner, càng vào sâu càng dính nhiều
    // Bar/Corner nên càng khó thoát ra. Trả rỗng để không loại trừ gì cả, nhường
    // đúng nghĩa cử chỉ Back cho hệ điều hành.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
    try {
        v.setSystemGestureExclusionRects(java.util.Collections.emptyList());
    } catch (Exception ignored) {}
}
private final Handler homaccDebounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable homaccDebounceRunnable = null;
private final Handler panelDebounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable panelDebounceRunnable = null;
private static final long PANEL_DEBOUNCE_MS = 120;
// [TỐI ƯU PIN/RAM] Throttle ghi prefs khi kéo Slider — leading-edge throttle +
// write bắt buộc lúc nhả tay. Giảm số lần apply() từ "mỗi pixel kéo" (40-100+ lần
// mỗi lần vuốt) xuống tối đa ~16 lần/giây, vẫn giữ cảm giác preview real-time.
private final Handler sliderPrefHandler = new Handler(android.os.Looper.getMainLooper());
private final java.util.Map<String, Long> sliderLastWriteMs = new java.util.HashMap<>();
private final java.util.Map<String, Runnable> sliderPendingRunnable = new java.util.HashMap<>();
private static final long SLIDER_WRITE_THROTTLE_MS = 60;
private final Handler debounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable debounceRunnable = null;
// [MỚI] Auto Icon Color — throttle chụp màn hình, chỉ chạy khi thực sự cần
private long lastIconColorSampleMs = 0;
private static final long ICON_COLOR_SAMPLE_THROTTLE_MS = 1200;
private static final int ICON_COLOR_LIGHT_THRESHOLD = 175; // >= ngưỡng này -> nền sáng -> icon đen

private boolean isAutoColorOff(String prefix, String barKey) {
    String off = prefs.getString(prefix + "bar_auto_icon_color_off", "");
    return ("," + off + ",").contains("," + barKey + ",");
}

private boolean barNeedsAutoColor(View[] arr, String prefix) {
    if (arr == null) return false;
    for (int i = 0; i < 12; i++) {
        View v = arr[i];
        if (v == null || v.getVisibility() != View.VISIBLE) continue;
        if (prefs.getString(prefix + BARS[i] + "_icons", "").isEmpty()) continue;
        if (isAutoColorOff(prefix, BARS[i])) continue;
        return true;
    }
    return false;
}

private void sampleAndApplyIconColors() {
    if (Build.VERSION.SDK_INT < 30) return; // takeScreenshot() cần Android 11+
    long now = System.currentTimeMillis();
    if (now - lastIconColorSampleMs < ICON_COLOR_SAMPLE_THROTTLE_MS) return;
    if (!barNeedsAutoColor(bars, "lock_") && !barNeedsAutoColor(accHomeBars, "homacc_")) return;
    lastIconColorSampleMs = now;
    try {
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
            new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    try {
                        Bitmap hw = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if (hw == null) { result.getHardwareBuffer().close(); return; }
                        Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                        result.getHardwareBuffer().close();
                        applyAutoColorsFromScreenshot(sw, bars, "lock_");
                        applyAutoColorsFromScreenshot(sw, accHomeBars, "homacc_");
                        sw.recycle();
                    } catch (Exception ignored) {}
                }
                @Override public void onFailure(int errorCode) {}
            });
    } catch (Exception ignored) {}
}

private void applyAutoColorsFromScreenshot(Bitmap screen, View[] arr, String prefix) {
    if (arr == null) return;
    int[] loc = new int[2];
    for (int i = 0; i < 12; i++) {
        View v = arr[i];
        if (!(v instanceof BarView) || v.getVisibility() != View.VISIBLE) continue;
        if (prefs.getString(prefix + BARS[i] + "_icons", "").isEmpty()) continue;
        if (isAutoColorOff(prefix, BARS[i])) { ((BarView) v).setIconTintColor(null); continue; }
        try {
            v.getLocationOnScreen(loc);
            int w = Math.max(1, v.getWidth()), h = Math.max(1, v.getHeight());
            int x = Math.max(0, Math.min(loc[0], screen.getWidth() - 1));
            int y = Math.max(0, Math.min(loc[1], screen.getHeight() - 1));
            int rw = Math.min(w, screen.getWidth() - x);
            int rh = Math.min(h, screen.getHeight() - y);
            if (rw <= 0 || rh <= 0) continue;
            long sum = 0; int count = 0;
            int stepX = Math.max(1, rw / 12), stepY = Math.max(1, rh / 12); // lấy mẫu thưa, đỡ CPU
            for (int py = y; py < y + rh; py += stepY) {
                for (int px = x; px < x + rw; px += stepX) {
                    int pixel = screen.getPixel(px, py);
                    int r = (pixel >> 16) & 0xFF, g = (pixel >> 8) & 0xFF, b = pixel & 0xFF;
                    sum += (r * 299 + g * 587 + b * 114) / 1000;
                    count++;
                }
            }
            if (count == 0) continue;
            int avg = (int) (sum / count);
            ((BarView) v).setIconTintColor(avg >= ICON_COLOR_LIGHT_THRESHOLD ? Color.BLACK : Color.WHITE);
        } catch (Exception ignored) {}
    }
}
private boolean lastAccHomeRunningState = false;
private long lastHomaccUpdateMs = 0;

// V19.12.3.6.8 THE ETERNAL EGO — throttle biến event
private long lastEventMs = 0;
private static final long EVENT_THROTTLE_MS = 200;
private long lastLockCheckMs = 0;
private static final long LOCK_CHECK_THROTTLE_MS = 150; // riêng cho LockList, không bị nuốt bởi throttle chung
private String lastEventPkg = "";
private boolean lastIsKbd_cache = false;
private int cachedKbdHeight = 0;
private static final int KBD_HEIGHT_CHANGE_THRESHOLD = 20;
private boolean lastIsBl_cache = false;
// V19.12.3.6.6 — Whitelist key của EdgeBar, chặn key lạ của Zalo/Messenger
private static final java.util.Set<String> EB_KEY_PREFIXES =
    new java.util.HashSet<>(java.util.Arrays.asList(
        "lock_","home_","morse_","homacc_","anim_","vib_","hold_",
        "blacklist","avoid_kbd","shortcut_","preview_",
        "lang_","ytdl_","intent_","tile_","macro_",
        // [FIX] Khóa thật của Panel là "pack_panel_<id>_..." — không phải "panel".
        // Thiếu tiền tố đúng khiến isOurKey() chặn TOÀN BỘ thay đổi live của Panel
        // (Preview Handle, Enable, slider...) ngay từ vòng lọc whitelist.
        "pack_panel_","lenap_",
        "i1_","i2_","i3_","i4_","i5_","i6_","i7_","i8_",
        "i9_","i10_","i11_","i12_","i13_","i14_","i15_"
    ));
private boolean isOurKey(String k) {
    if (k == null) return false;
    for (String prefix : EB_KEY_PREFIXES)
        if (k.startsWith(prefix) || k.equals(prefix)) return true;
    return false;
}

// V19.12.3.6.7 THE FINAL VERDICT
// Tăng debounce Homacc từ 300ms → 1000ms
// Lý do: updateHomaccLive() gọi wm.updateViewLayout() là IPC call đến system_server
// Mỗi IPC call = wakelock ngắn trên Adreno 540 → pin hao nhanh
// 1000ms debounce = user kéo slider thoải mái, chỉ apply sau khi dừng tay
private static final long HOMACC_DEBOUNCE_MS = 1000;
private static final long LOCK_DEBOUNCE_MS = 400;

private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
    // TẦNG 1: Whitelist tuyệt đối — bỏ qua mọi key không thuộc EdgeBar
    if (!isOurKey(k)) return;

    // TẦNG 2: anim_ → update ngay, user đang xem preview live
    if (k != null && k.startsWith("anim_")) {
        if (fV != null) fV.updateStyle();
        if (k.startsWith("anim_rec_")) liveUpdateRecIndicatorPosition();
        return;
    }
    // TẦNG 2.5: fingerprint_ → kiểm tra lại có cần đăng ký/huỷ đăng ký không
if (k != null && k.contains("fingerprint_")) {
    refreshFingerprintRegistration();
    return;
}
    // TẦNG 2.8: panel_ → debounce ngắn 120ms, update TẠI CHỖ, không removeView/addView
    // [FIX] Khớp đúng tiền tố khóa thật "pack_panel_" thay vì "panel"
if (k != null && k.startsWith("pack_panel_")) {
        if (panelEngine == null) return;
        final String key = k;
        if (panelDebounceRunnable != null) panelDebounceHandler.removeCallbacks(panelDebounceRunnable);
        panelDebounceRunnable = () -> panelEngine.onPrefChanged(key);
        panelDebounceHandler.postDelayed(panelDebounceRunnable, PANEL_DEBOUNCE_MS);
        return;
    }
if (k != null && k.startsWith("shortcut_") && k.endsWith("_icon_override")) {
        if (panelEngine == null) return;
        final String key = k;
        if (panelDebounceRunnable != null) panelDebounceHandler.removeCallbacks(panelDebounceRunnable);
        panelDebounceRunnable = () -> panelEngine.onPrefChanged(key);
        panelDebounceHandler.postDelayed(panelDebounceRunnable, PANEL_DEBOUNCE_MS);
        return;
    }
    // TẦNG 3: homacc_ → debounce DÀI 1000ms, chỉ gọi updateHomaccLive() sau khi dừng
    // Guard kép: isRunning + isHomaccDrawn tránh IPC vô nghĩa
    if (k != null && k.startsWith("homacc_")) {
    boolean previewOn = prefs.getBoolean("preview_homacc", false);
    if ((!AccessibleHomeService.isRunning && !previewOn) || !isHomaccDrawn) return;

    // [MỚI] Key chỉ ảnh hưởng icon -> cập nhật NGAY, không debounce
    boolean isIconOnlyKey = k.endsWith("_icon_size") || k.endsWith("_icon_alpha")
        || k.equals("homacc_jump_icon_size") || k.equals("homacc_jump_icon_alpha")
        || k.startsWith("homacc_gesture_icon_");
    if (isIconOnlyKey) { updateHomaccLive(); return; }

    if (homaccDebounceRunnable != null)
        homaccDebounceHandler.removeCallbacks(homaccDebounceRunnable);
    homaccDebounceRunnable = () -> {
        boolean stillPreview = prefs.getBoolean("preview_homacc", false);
        if ((!AccessibleHomeService.isRunning && !stillPreview) || !isHomaccDrawn) return;
        lastHomaccUpdateMs = System.currentTimeMillis();
        updateHomaccLive();
    };
    homaccDebounceHandler.postDelayed(homaccDebounceRunnable, HOMACC_DEBOUNCE_MS);
    return;
}
    // TẦNG 3.5: "_manual_hide" đã được set TRỰC TIẾP lên View ngay tại nơi ghi
    // (checkAndYieldOS/hideSomeOverlay/showAllOverlay) — không cần rebuild lại
    // toàn bộ 16 Bar/Corner + Panel qua updateVisibility() nữa. Bỏ debounce cho
    // riêng key này để tránh giật hình khi "Nhường OS" trùng lúc TRIGGER_* đang
    // chạy animation mở khoá.
    if (k != null && k.endsWith("_manual_hide")) return;

    // TẦNG 4: lock bars → debounce 400ms như cũ
    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    debounceRunnable = () -> updateVisibility();
    debounceHandler.postDelayed(debounceRunnable, LOCK_DEBOUNCE_MS);
};
private BroadcastReceiver stateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        if ("com.manhmoc.edgebar.TEST_ANIM".equals(act)) {
            playAnim();
        } else if (Intent.ACTION_SCREEN_OFF.equals(act)) {
            if (isHomaccDrawn) removeAccessibleHome();
            removeYtdlOverlay(); 
            removeRippleViewIfIdle(); 
            AppLockHelper.clearAll(); 
            if (fpRegistered && fpController != null && fpCallback != null) {
                try { fpController.unregisterFingerprintGestureCallback(fpCallback); } catch (Exception e) {}
                fpRegistered = false;
            }
            
            // [MỚI] Hồi sinh hoàn toàn: Hủy mọi cờ xuyên thấu/giả lập đang kẹt
            isDispatchingSyntheticGesture = false;
            setTransientUntouchable(false);

            // [THAY BẰNG CODE HỒI SINH LOCK Ở ĐÂY]
            SharedPreferences.Editor ed = prefs.edit();
            for (String b : BARS) ed.putBoolean("lock_" + b + "_manual_hide", false);
            for (String cn : CORNERS) ed.putBoolean("lock_corner_" + cn + "_manual_hide", false);
            ed.apply();

        } else if (Intent.ACTION_USER_PRESENT.equals(act)) {
            if (AccessibleHomeService.isRunning) drawAccessibleHome();
            refreshFingerprintRegistration(); 
            
            // [MỚI] Hồi sinh hoàn toàn: Hủy mọi cờ xuyên thấu/giả lập đang kẹt
            isDispatchingSyntheticGesture = false;
            setTransientUntouchable(false);
            
            // [THAY BẰNG CODE HỒI SINH HOMACC Ở ĐÂY]
            SharedPreferences.Editor ed = prefs.edit();
            for (String b : BARS) ed.putBoolean("homacc_" + b + "_manual_hide", false);
            for (String cn : CORNERS) ed.putBoolean("homacc_corner_" + cn + "_manual_hide", false);
            ed.apply();
            
            updateVisibility();
// CODE MỚI — thay bằng:
} else if ("com.manhmoc.edgebar.OPEN_PANEL_REQUEST".equals(act)) {
    String panelId = i.getStringExtra("panel_id");
    if (panelEngine != null && panelId != null) panelEngine.togglePanel(panelId);
} else if ("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED".equals(act)) {
    if (panelEngine != null) panelEngine.rebuildAll();
} else if ("com.manhmoc.edgebar.PANEL_TEST_TOGGLE".equals(act)) {
    String panelId = i.getStringExtra("panel_id");
    if (panelEngine != null && panelId != null) panelEngine.setForceTest(panelId, i.getBooleanExtra("on", false));
} else if ("com.manhmoc.edgebar.PAUSE_WM_OPS".equals(act)) {
    for (int j=0;j<12;j++) if (bars[j]!=null) bars[j].setVisibility(View.GONE);
    for (int j=0;j<4;j++) if (corners[j]!=null) corners[j].setVisibility(View.GONE);
    for (int j=0;j<12;j++) if (accHomeBars[j]!=null) accHomeBars[j].setVisibility(View.GONE);
    for (int j=0;j<4;j++) if (accHomeCorners[j]!=null) accHomeCorners[j].setVisibility(View.GONE);
} else if ("com.manhmoc.edgebar.RESUME_WM_OPS".equals(act)) {
    updateVisibility();
} else if (VoiceRecorderService.TICK_ACTION.equals(act)) {
    String state = i.getStringExtra("state");
    long sec = i.getLongExtra("elapsed_sec", 0);
    recIndicatorTestMode = false; // ghi âm thật luôn được ưu tiên hơn chế độ THỬ
    updateRecIndicator(state, sec);
} else if ("com.manhmoc.edgebar.TEST_REC_INDICATOR".equals(act)) {
    boolean on = i.getBooleanExtra("on", false);
    if (!VoiceRecorderService.isRunning) {
        recIndicatorTestMode = on;
        recIndicatorTestPaused = false;
        updateRecIndicator(on ? "RECORDING" : "STOPPED", 0);
    }
} else {
    updateVisibility();
       }
    }
};
    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent i) {
        if ("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) {
            String act = i.getStringExtra("act");
            if ("LAUNCH_APP".equals(act)) {
                String pkg = i.getStringExtra("launch_pkg");
                if (pkg != null && !pkg.isEmpty()) {
                    try {
                        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
                    } catch (Exception ignored) {}
                }
                return;
            }
            if ("RUN_SHORTCUT".equals(act)) {
                String scId = i.getStringExtra("shortcut_id");
                if (scId != null && !scId.isEmpty()) {
                    try {
                        String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                        if (!uri.isEmpty()) {
                            Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                            scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(scIntent);
                        }
                    } catch (Exception ignored) {}
                }
                return;
            }
            // [FIX] PanelEngine gửi thẳng "RUN_SHORTCUT_<id>" (không kèm extra shortcut_id
            // riêng) — trước đây rơi thẳng vào exec() và bị bỏ qua vì không khớp case nào.
            if (act != null && act.startsWith("RUN_SHORTCUT_")) {
                String scId = act.substring("RUN_SHORTCUT_".length());
                try {
                    String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                    if (!uri.isEmpty()) {
                        Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                        scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(scIntent);
                    }
                } catch (Exception ignored) {}
                return;
            }
            exec(act);
        }
    }
};
    private static final int BAR_ICON_CACHE_LIMIT = 40;
private static final java.util.LinkedHashMap<String, android.graphics.Bitmap> barIconCache =
    new java.util.LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true) {
        protected boolean removeEldestEntry(java.util.Map.Entry<String, android.graphics.Bitmap> e) {
            return size() > BAR_ICON_CACHE_LIMIT;
        }
    };
private android.graphics.Bitmap resolveBarIconBitmap(String ref, int size) {
    String key = ref + "_" + size;
    synchronized (barIconCache) {
        android.graphics.Bitmap cached = barIconCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
    }
    android.graphics.drawable.Drawable d = null;
    try {
        if (ref.startsWith("app:")) {
            d = getPackageManager().getApplicationIcon(ref.substring(4));
        } else if (ref.startsWith("poolc:")) {
            int idx = Integer.parseInt(ref.substring(6));
            int[] pool = PanelEngine.getCustomIconPool(this);
            if (idx >= 0 && idx < pool.length) d = getDrawable(pool[idx]);
        } else if (ref.startsWith("pool:")) {
            int idx = Integer.parseInt(ref.substring(5));
            if (idx >= 0 && idx < PanelEngine.SYSTEM_ICON_POOL.length) d = getDrawable(PanelEngine.SYSTEM_ICON_POOL[idx]);
        }
    } catch (Exception ignored) {}
    if (d == null) return null;
    d = d.mutate();
    d.setTint(Color.WHITE);
    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    d.setBounds(0, 0, size, size);
    d.draw(c);
    synchronized (barIconCache) { barIconCache.put(key, bmp); }
    return bmp;
}
private java.util.List<android.graphics.Bitmap> resolveBarIcons(String csv, int size) {
    java.util.List<android.graphics.Bitmap> list = new java.util.ArrayList<>();
    if (csv == null || csv.isEmpty()) return list;
    for (String ref : csv.split(",")) {
        if (ref.trim().isEmpty()) continue;
        android.graphics.Bitmap b = resolveBarIconBitmap(ref.trim(), size);
        if (b != null) list.add(b);
    }
    return list;
}
        private class FlashView extends View {
    private Paint pCore = new Paint(), pGlowMid = new Paint(), pGlowOuter = new Paint();
    float radius = 40f; String cTheme = "WHITE";
    int aStyle = 0; private float phaseFraction = 0f;
    private int effW = 0, effH = 0;

    public FlashView(Context c) {
        super(c);
        for (Paint p : new Paint[]{pCore, pGlowMid, pGlowOuter}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setAntiAlias(true);
        }
        setLayerType(LAYER_TYPE_SOFTWARE, null); // bắt buộc để BlurMaskFilter hoạt động
        updateStyle();
    }

    public void updateStyle() {
        int baseAlpha = prefs.getInt("anim_alpha", 255);
        int thick = Math.max(4, prefs.getInt("anim_thick", 12));
        radius = prefs.getInt("anim_rad", 40);
        cTheme = prefs.getString("anim_color", "WHITE");
        aStyle = prefs.getInt("anim_style", 0);
        effW = prefs.getInt("anim_w", 0);
        effH = prefs.getInt("anim_h", 0);

        // Lõi: mảnh, sáng vừa phải, blur nhẹ để không "sắc lẻm"
        pCore.setStrokeWidth(thick * 0.35f);
        pCore.setAlpha((int)(baseAlpha * 0.9f));
        pCore.setMaskFilter(new BlurMaskFilter(thick * 0.5f, BlurMaskFilter.Blur.NORMAL));

        // Glow giữa: rộng hơn, mờ vừa
        pGlowMid.setStrokeWidth(thick * 1.4f);
        pGlowMid.setAlpha((int)(baseAlpha * 0.4f));
        pGlowMid.setMaskFilter(new BlurMaskFilter(thick * 1.3f, BlurMaskFilter.Blur.NORMAL));

        // Glow ngoài: rất rộng, rất mờ -> tạo halo lan tỏa như video
        pGlowOuter.setStrokeWidth(thick * 3f);
        pGlowOuter.setAlpha((int)(baseAlpha * 0.2f));
        pGlowOuter.setMaskFilter(new BlurMaskFilter(thick * 2.8f, BlurMaskFilter.Blur.NORMAL));

        if (getWidth() > 0) applyGradient(getWidth(), getHeight());
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h);
    }

    private void applyGradient(int w, int h) {
        int[] cArr; switch(cTheme) {
            case "NEON": cArr=new int[]{Color.parseColor("#FF0055"), Color.parseColor("#7000FF"), Color.parseColor("#00E5FF"), Color.parseColor("#FF0055")}; break;
            case "CYBERPUNK": cArr=new int[]{Color.parseColor("#F500FF"), Color.parseColor("#00E5FF"), Color.parseColor("#FFDF00"), Color.parseColor("#F500FF")}; break;
            case "LAVA": cArr=new int[]{Color.parseColor("#FF0000"), Color.parseColor("#FF5A00"), Color.parseColor("#FF9A00"), Color.parseColor("#FF0000")}; break;
            case "OCEAN": cArr=new int[]{Color.parseColor("#005BEA"), Color.parseColor("#00C6FB"), Color.parseColor("#005BEA")}; break;
            case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000"), Color.parseColor("#00FF00")}; break;
            case "SUNSET": cArr=new int[]{Color.parseColor("#FF512F"), Color.parseColor("#DD2476"), Color.parseColor("#FF512F")}; break;
            case "GOOGLE": cArr=new int[]{Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853"), Color.parseColor("#4285F4")}; break;
            case "AURORA": cArr=new int[]{Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"), Color.parseColor("#00E5FF"), Color.parseColor("#8E2DE2")}; break;
            case "ABYSS": cArr=new int[]{Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364"), Color.parseColor("#0F2027")}; break;
            case "COSMIC": cArr=new int[]{Color.parseColor("#FF00CC"), Color.parseColor("#333399"), Color.parseColor("#FF00CC")}; break;
            case "FOREST": cArr=new int[]{Color.parseColor("#11998E"), Color.parseColor("#38EF7D"), Color.parseColor("#11998E")}; break;
            case "FLAME": cArr=new int[]{Color.parseColor("#F12711"), Color.parseColor("#F5AF19"), Color.parseColor("#F12711")}; break;
            case "MIDNIGHT": cArr=new int[]{Color.parseColor("#1A2980"), Color.parseColor("#26D0CE"), Color.parseColor("#1A2980")}; break;
            case "TROPICAL": cArr=new int[]{Color.parseColor("#43C6AC"), Color.parseColor("#F8FFAE"), Color.parseColor("#43C6AC")}; break;
            case "CANDY": cArr=new int[]{Color.parseColor("#FF9A9E"), Color.parseColor("#FECFEF"), Color.parseColor("#FF9A9E")}; break;
            default: cArr=new int[]{Color.WHITE, Color.parseColor("#E0E0E0"), Color.WHITE}; break;
        }
        Shader shader = new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.MIRROR);
        pCore.setShader(shader);
        pGlowMid.setShader(shader);
        pGlowOuter.setShader(shader);
    }

    public void setPhase(float fraction) { this.phaseFraction = fraction; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        float drawW = getWidth(); float drawH = getHeight();
        if (drawW <= 0 || drawH <= 0) return;
        float off = pGlowOuter.getStrokeWidth() / 2;
        float insetW = effW != 0 ? Math.min((float)effW, drawW/2f - 1f) : 0f;
        float insetH = effH != 0 ? Math.min((float)effH, drawH/2f - 1f) : 0f;
        float left = off + insetW; float top = off + insetH;
        float right = drawW - off - insetW; float bottom = drawH - off - insetH;

        if (aStyle > 0) {
            float perim = 2 * ((right - left) + (bottom - top));
            float currentPhase = -perim * phaseFraction;
            float[] intervals = aStyle == 1 ? new float[]{perim/4f, 3*perim/4f}
                : aStyle == 2 ? new float[]{perim/8f, 3*perim/8f}
                : new float[]{perim/12f, 3*perim/12f};
            DashPathEffect dash = new DashPathEffect(intervals, currentPhase);
            pCore.setPathEffect(dash); pGlowMid.setPathEffect(dash); pGlowOuter.setPathEffect(dash);
        } else {
            pCore.setPathEffect(null); pGlowMid.setPathEffect(null); pGlowOuter.setPathEffect(null);
        }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, pGlowOuter);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, pGlowMid);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, pCore);
    }
}
    // ===== GESTURE RIPPLE VIEW (chấm sóng chạm + icon NHẢY LÊN xoay 1 vòng rồi RƠI XUỐNG) =====
    private class GestureRippleView extends View {
        private float touchX = -1, touchY = -1;
        private float rippleRadius = 0f, rippleAlpha = 0f;
        private Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator popAnim;

        private Bitmap jumpIconBmp = null;
        private float jumpX = -1, jumpY = -1;
        private float jumpOffsetX = 0f;
        private float jumpOffsetY = 0f;
        private float jumpRotation = 0f;
        private float jumpAlpha = 0f;
        private ValueAnimator jumpUpAnim, fallAnim;
        private final Handler jumpHandler = new Handler(android.os.Looper.getMainLooper());

        public GestureRippleView(Context c) { super(c); setLayerType(LAYER_TYPE_HARDWARE, null); }

        public void showAt(float x, float y) {
            touchX = x; touchY = y;
            rippleRadius = 10f; rippleAlpha = 1f;
            setVisibility(View.VISIBLE); invalidate();
        }
        public void moveTo(float x, float y) { touchX = x; touchY = y; invalidate(); }

        public void popRipple() {
            if (popAnim != null) popAnim.cancel();
            popAnim = ValueAnimator.ofFloat(1f, 0f);
            popAnim.setDuration(320);
            popAnim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                rippleRadius = 60f + (1f - v) * 70f;
                rippleAlpha = v;
                invalidate();
            });
            popAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (jumpIconBmp == null) {
                        rippleAlpha = 0f; rippleRadius = 0f; touchX = -1; touchY = -1;
                        setVisibility(View.GONE);
                        removeRippleViewIfIdle(); // Giải phóng View vĩnh viễn khỏi WM chống Tapjacking
                    }
                }
            });
            popAnim.start();
        }

        public void jumpIcon(float x, float y, String gestureKey, int color, float dxDir, float dyDir) {
            jumpHandler.removeCallbacksAndMessages(null);
            if (jumpUpAnim != null) jumpUpAnim.cancel();
            if (fallAnim != null) fallAnim.cancel();
            int jSize = prefs.getInt("homacc_jump_icon_size", 90);
            jumpIconBmp = resolveGestureIconBitmap(gestureKey, jSize);
            if (jumpIconBmp == null) {
                // [FIX] Không có icon gán cho gesture này -> dọn sạch ripple còn sót,
                // không để lại đốm nhỏ do rippleAlpha/rippleRadius chưa reset.
                rippleAlpha = 0f; rippleRadius = 0f; touchX = -1; touchY = -1;
                setVisibility(View.GONE);
                invalidate();
                return;
            }
            jumpX = x; jumpY = y;
            jumpOffsetX = 0f; jumpOffsetY = 0f; jumpRotation = 0f; jumpAlpha = 1f;
            setVisibility(View.VISIBLE);
            invalidate();
            int jumpTotalDur = Math.max(60, prefs.getInt("homacc_jump_anim_dur", 1000));
            jumpUpAnim = ValueAnimator.ofFloat(0f, 1f);
            jumpUpAnim.setDuration((long) (jumpTotalDur * 0.45f));
            jumpUpAnim.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
            jumpUpAnim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                int dist = prefs.getInt("homacc_jump_dist", 160); // [MỚI] khoảng cách nhảy toàn cục
                jumpOffsetY = dyDir * dist * v;
                jumpOffsetX = dxDir * dist * v;
                jumpRotation = 360f * v;
                invalidate();
            });
            jumpUpAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    jumpHandler.postDelayed(() -> startFall(), prefs.getInt("homacc_jump_hold_ms", 2000));
                }
            });
            jumpUpAnim.start();
        }
        private void startFall() {
            int fallDur = Math.max(60, prefs.getInt("homacc_jump_anim_dur", 1000));
            fallAnim = ValueAnimator.ofFloat(0f, 1f);
            fallAnim.setDuration((long) (fallDur * 0.55f));
            fallAnim.setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f));
            float startOffset = jumpOffsetY;
            float startOffsetX = jumpOffsetX;
            fallAnim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                jumpOffsetY = startOffset + (140f - startOffset) * v;
                jumpOffsetX = startOffsetX * (1f - v);
                jumpAlpha = 1f - v;
                invalidate();
            });
            fallAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    jumpIconBmp = null;
                    if (rippleAlpha <= 0f) {
                        setVisibility(View.GONE);
                        removeRippleViewIfIdle(); // Giải phóng View vĩnh viễn khỏi WM chống Tapjacking
                    }
                    invalidate();
                }
            });
            fallAnim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (touchX >= 0 && rippleAlpha > 0f) {
                ripplePaint.setColor(Color.argb((int) (rippleAlpha * 160), 255, 255, 255));
                canvas.drawCircle(touchX, touchY, rippleRadius, ripplePaint);
            }
            if (jumpIconBmp != null) {
                canvas.save();
                float cx = jumpX + jumpOffsetX, cy = jumpY + jumpOffsetY;
                canvas.rotate(jumpRotation, cx, cy);
                int jAlpha = prefs.getInt("homacc_jump_icon_alpha", 255);
iconPaint.setAlpha((int) (jumpAlpha * jAlpha));
                canvas.drawBitmap(jumpIconBmp, cx - jumpIconBmp.getWidth()/2f, cy - jumpIconBmp.getHeight()/2f, iconPaint);
                canvas.restore();
            }
        }
    }
    private final java.util.Map<String, Bitmap> gestureIconCache = new java.util.HashMap<>();
    private Bitmap resolveGestureIconBitmap(String gestureKey, int size) {
        String ref = prefs.getString("homacc_gesture_icon_" + gestureKey, "");
        String cacheKey = gestureKey + "_" + ref + "_" + size; // [FIX] gồm cả size lẫn ref -> đổi slider/icon là cache tự invalidate
        if (gestureIconCache.containsKey(cacheKey)) return gestureIconCache.get(cacheKey);
        Drawable d = null;
        try {
            if (ref.startsWith("app:")) d = getPackageManager().getApplicationIcon(ref.substring(4));
            else if (ref.startsWith("poolc:")) { int[] p2 = PanelEngine.getCustomIconPool(this); int idx = Integer.parseInt(ref.substring(6)); if (idx>=0 && idx<p2.length) d = getDrawable(p2[idx]); }
            else if (ref.startsWith("pool:")) { int idx = Integer.parseInt(ref.substring(5)); if (idx>=0 && idx<PanelEngine.SYSTEM_ICON_POOL.length) d = getDrawable(PanelEngine.SYSTEM_ICON_POOL[idx]); }
        } catch (Exception ignored) {}
        if (d == null) { gestureIconCache.put(cacheKey, null); return null; }
    // [FIX CRASH] size lấy từ slider "Jump Icon Size" có thể bị kéo về 0/âm —
    // Bitmap.createBitmap(0,0,...) ném IllegalArgumentException không bắt được,
    // đây chính là nguyên nhân crash tap/dtap/long khi có gán icon (swipe không
    // crash vì thường chưa được gán icon nên hàm return null ở dòng trên, không
    // bao giờ chạy tới đây). Ép tối thiểu 8px + bọc try/catch để không bao giờ
    // crash dù prefs có giá trị bất thường.
    int safeSize = Math.max(8, size);
    try {
        Bitmap bmp = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d = d.mutate(); d.setTint(Color.WHITE); d.setBounds(0,0,safeSize,safeSize); d.draw(c);
        gestureIconCache.put(cacheKey, bmp);
        return bmp;
    } catch (Exception e) {
        gestureIconCache.put(cacheKey, null);
        return null;
    }
    }
    private class BarView extends View {
    private int baseAlpha, hideDelay;
    private boolean isAutoHiding = false, isInv = false;
    private Handler autoHideHandler = new Handler();
    private GradientDrawable gd = new GradientDrawable();
    private java.util.List<android.graphics.Bitmap> icons = new java.util.ArrayList<>();
    private Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public BarView(Context c) { super(c); gd.setCornerRadius(24f); setBackground(gd); }
    private int userIconAlpha = 255;
    public void setIcons(java.util.List<android.graphics.Bitmap> newIcons, int alpha) {
        this.icons = newIcons != null ? newIcons : new java.util.ArrayList<>();
        userIconAlpha = alpha;
        invalidate();
    }
    // [MỚI] Tô lại icon (vốn là bitmap trắng đơn sắc) sang đen/trắng theo nền —
    // null = giữ nguyên màu gốc (trắng). Zero-alloc: chỉ đổi ColorFilter, không tạo Bitmap mới.
    private Integer iconTintColor = null;
    public void setIconTintColor(Integer color) {
        if (iconTintColor == null ? color == null : iconTintColor.equals(color)) return;
        iconTintColor = color;
        iconPaint.setColorFilter(color == null ? null :
            new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        invalidate();
    }
    private int iconAlphaFactor = 255; // 0 = ẩn hoàn toàn icon, 255 = hiện đầy đủ

    public void updateProps(int alpha, boolean autoHide, int delay, boolean inv, float radius) {
        this.baseAlpha = alpha; this.isAutoHiding = autoHide; this.hideDelay = delay; this.isInv = inv;
        autoHideHandler.removeCallbacksAndMessages(null);
        gd.setCornerRadius(radius); // [MỚI] Độ bo tròn tuỳ chỉnh từ slider "bar_radius"
        if (inv) { gd.setColor(Color.argb(0, 96, 125, 139)); iconAlphaFactor = 0; }
        else if (!autoHide) { gd.setColor(Color.argb(alpha, 96, 125, 139)); iconAlphaFactor = 255; }
        else { gd.setColor(Color.argb(0, 96, 125, 139)); iconAlphaFactor = 255; }
        invalidate();
    }
    public void triggerFlash() {
        if (!isAutoHiding || isInv) return;
        autoHideHandler.removeCallbacksAndMessages(null);
        gd.setColor(Color.argb(Math.min(255, baseAlpha + 50), 96, 125, 139));
        invalidate();
        autoHideHandler.postDelayed(() -> {
            ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
            a.setDuration(1500);
            a.addUpdateListener(anim -> {
                float val = (float) anim.getAnimatedValue();
                gd.setColor(Color.argb((int) (baseAlpha * val), 96, 125, 139));
                // icon KHÔNG mờ theo nền nữa — luôn giữ độ hiện rõ trong suốt lúc tàng hình
                invalidate();
            });
            a.start();
        }, hideDelay);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        iconPaint.setAlpha((int) (userIconAlpha * (iconAlphaFactor / 255f)));
        if (icons.isEmpty()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int n = icons.size();
        int gap = 8;
        boolean horizontal = w >= h;
        int mainDim = horizontal ? w : h;
        int crossDim = horizontal ? h : w;
        int userIconSize = icons.get(0).getWidth(); // kích thước user đã chọn ở slider

        // [MỚI] Tự động lấp đầy thanh: nếu tổng chiều dài các icon (theo size user
        // chọn) vượt quá chiều dài Bar, tự co đều lại để vừa khít — không tràn ra
        // ngoài. Nếu đủ chỗ thì giữ nguyên kích thước user đã chọn (không phóng to
        // thêm, tránh vỡ nét). Dùng drawBitmap(bitmap, null, destRect, paint) để co
        // giãn ngay trên GPU khi vẽ — KHÔNG tạo Bitmap mới, Zero cấp phát thêm,
        // tối ưu pin/RAM cho Pixel 2XL vì đây là hàm onDraw() gọi liên tục.
        int maxFit = Math.max(8, (mainDim - (n - 1) * gap) / n);
        int drawSize = Math.min(userIconSize, maxFit);
        drawSize = Math.min(drawSize, crossDim); // không vượt bề dày thanh

        int totalMain = n * drawSize + (n - 1) * gap;
        int startMain = (mainDim - totalMain) / 2; // luôn căn giữa khối icon trong thanh
        int crossOffset = (crossDim - drawSize) / 2;

        for (int i = 0; i < n; i++) {
            int pos = startMain + i * (drawSize + gap);
            android.graphics.Rect dst = horizontal
                ? new android.graphics.Rect(pos, crossOffset, pos + drawSize, crossOffset + drawSize)
                : new android.graphics.Rect(crossOffset, pos, crossOffset + drawSize, pos + drawSize);
            canvas.drawBitmap(icons.get(i), null, dst, iconPaint);
        }
    }
}
        private class CornerView extends View {
            private Paint pFill, pStroke; private int type; private String prefix;
            private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
            private boolean isInv = false;

            public CornerView(Context c, int type, String prefix) {  
                super(c); this.type = type; this.prefix = prefix; 
                setLayerType(LAYER_TYPE_HARDWARE, null);
                pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); 
                pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND); 
            }

            public int getCornerType() { return type; }

            public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) {
                pStroke.setStrokeWidth(thick);
                this.baseMoonAlpha = moonAlpha;
                this.baseStrokeAlpha = strokeAlpha;
                this.isAutoHiding = autoHide;
                this.hideDelay = delay;
                this.isInv = inv;
                if (inv) {
                    pFill.setAlpha(0);
                    pStroke.setAlpha(0);
                } else if (!autoHide) {
                    pFill.setColor(Color.argb(moonAlpha, 96, 125, 139));
                    pStroke.setColor(Color.argb(strokeAlpha, 255, 255, 255));
                } else {
                    pFill.setColor(Color.argb(0, 96, 125, 139));
                    pStroke.setColor(Color.argb(0, 255, 255, 255));
                }
                invalidate();
            }

            public void triggerFlash() { 
                if(!isAutoHiding || isInv) return; 
                autoHideHandler.removeCallbacksAndMessages(null); 
                pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha + 50), 96, 125, 139)); 
                pStroke.setColor(Color.argb(Math.min(255, baseStrokeAlpha + 50), 255, 255, 255)); 
                invalidate(); 
                autoHideHandler.postDelayed(() -> { 
                    ValueAnimator a = ValueAnimator.ofFloat(1f, 0f); 
                    a.setDuration(1500); 
                    a.addUpdateListener(anim -> { 
                        float val = (float)anim.getAnimatedValue(); 
                        pFill.setColor(Color.argb((int)(baseMoonAlpha * val), 96, 125, 139)); 
                        pStroke.setColor(Color.argb((int)(baseStrokeAlpha * val), 255, 255, 255)); 
                        invalidate(); 
                    }); 
                    a.start(); 
                }, hideDelay); 
            }

            @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas);
                float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(); float pad = thick/2;
                String ck = prefix + "corner_" + CORNERS[type] + "_";
                int shapeMode = prefs.getInt(ck+"shape", 0);
                float sRad = prefs.getInt(ck+"rad", 80) / 1000f; float mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
                float sw = prefs.getInt(ck+"w", 100); float sh = prefs.getInt(ck+"h", 100);
                float mw = prefs.getInt(ck+"moon_w", 100); float mh = prefs.getInt(ck+"moon_h", 100);

                Path moonPath = new Path(); Path strokePath = new Path();
                float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
                float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;

                if(type==0) { // BR
                    sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                    mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
                } else if(type==1) { // BL
                    sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                    mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
                } else if(type==2) { // TR
                    sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                    mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
                } else { // TL
                    sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                    mRootX=0; mRootY=0; mTipX=mw; mTipY=mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
                }

                if(shapeMode == 1) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sTipX, sRootY); }
                else if(shapeMode == 2) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sRootX, sTipY); }
                else { strokePath.moveTo(sRootX, sTipY); strokePath.quadTo(sCtrlX, sCtrlY, sTipX, sRootY); }

                if(type==0||type==1) { moonPath.moveTo(mRootX, mTipY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mTipX, mRootY); moonPath.quadTo(mCtrlX, mCtrlY, mRootX, mTipY); }
                else { moonPath.moveTo(mTipX, mRootY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mRootX, mTipY); moonPath.quadTo(mCtrlX, mCtrlY, mTipX, mRootY); }
                moonPath.close();

                canvas.drawPath(strokePath, pStroke);
                float mx = prefs.getInt(ck+"moon_x", 1250) - 1250;
                float my = prefs.getInt(ck+"moon_y", 1250) - 1250;
                canvas.save(); canvas.translate(mx, my); canvas.drawPath(moonPath, pFill); canvas.restore();
            }
        }
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        // =========================================================
        // [MỚI] ĐẨY ICON EB LACCK LÊN STATUS BAR
        // =========================================================
        try {
            String cidAcc = "eb_lacck_status";
            NotificationChannel cAcc = new NotificationChannel(cidAcc, "EB Lacck Status", NotificationManager.IMPORTANCE_LOW);
            cAcc.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(cAcc);
            Notification nAcc = new Notification.Builder(this, cidAcc)
                    .setContentTitle("EB Lacck")
                    .setSmallIcon(android.R.drawable.stat_notify_voicemail) // Đúng icon của QS Tile
                    .setOngoing(true)
                    .build();
            // Nếu SDK >= 29, hệ thống có thể yêu cầu Type, nhưng với AccessibilityService thì gọi trơn vẫn an toàn
            startForeground(99, nAcc);
        } catch (Exception ignored) {}
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction("com.manhmoc.edgebar.TEST_ANIM");
        // CODE MỚI — thêm ngay dưới dòng addAction cuối:
filter.addAction("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
filter.addAction("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED");
filter.addAction("com.manhmoc.edgebar.PANEL_TEST_TOGGLE");
filter.addAction("com.manhmoc.edgebar.PAUSE_WM_OPS");
        filter.addAction("com.manhmoc.edgebar.RESUME_WM_OPS");
        filter.addAction(VoiceRecorderService.TICK_ACTION);
        filter.addAction("com.manhmoc.edgebar.TEST_REC_INDICATOR");
        registerReceiver(stateReceiver, filter);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(ipcReceiver, new
                IntentFilter("com.manhmoc.edgebar.IPC_ACTION"));
                
        // [TỐI ƯU PIXEL 2XL] Đã gỡ bỏ Notification "ĐỘNG CƠ TRỢ NĂNG" thừa thãi
        // HomescreenService chỉ chạy ngầm, không cần startForeground nếu hệ thống cho phép.
        
        accHomeReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                String act = intent.getAction();
                if ("com.manhmoc.edgebar.ACC_HOME_DRAW".equals(act)) {
                    drawAccessibleHome();
                } else if ("com.manhmoc.edgebar.ACC_HOME_REMOVE".equals(act)) {
                    removeAccessibleHome();
                } else if ("com.manhmoc.edgebar.ACC_HOME_SLEEP".equals(act)) {
                    // [MỤC 5] Deep sleep: chỉ ẩn view, GIỮ service sống — đỡ tốn pin re-init
                    for (int i=0;i<12;i++) if (accHomeBars[i]!=null) accHomeBars[i].setVisibility(View.GONE);
                    for (int i=0;i<4;i++) if (accHomeCorners[i]!=null) accHomeCorners[i].setVisibility(View.GONE);
                } else if ("com.manhmoc.edgebar.ACC_HOME_WAKE".equals(act)) {
                    // [MỤC 5] Thức dậy: vẽ lại nếu view chưa tồn tại, hoặc hiện lại view cũ
                    if (accHomeBars[0] == null && accHomeCorners[0] == null) drawAccessibleHome();
                    else {
                        SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
                        for (int i=0;i<12;i++) if (accHomeBars[i]!=null && p.getBoolean("homacc_"+BARS[i]+"_en", false)) accHomeBars[i].setVisibility(View.VISIBLE);
                        for (int i=0;i<4;i++) if (accHomeCorners[i]!=null && p.getBoolean("homacc_corner_"+CORNERS[i]+"_en", false)) accHomeCorners[i].setVisibility(View.VISIBLE);
                        updateHomaccLive();
                    }
                }
            }
        };
        android.content.IntentFilter accFilter = new android.content.IntentFilter();
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_DRAW");
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_REMOVE");
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_SLEEP");
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_WAKE");
        registerReceiver(accHomeReceiver, accFilter);
// FIX RACE CONDITION: không tin biến static isRunning sau khi process
// vừa restart — đọc thẳng từ SharedPreferences (sống sót qua process death)
// và tự khởi động lại AccessibleHomeService nếu prefs nói là "đang bật"
// nhưng service thực tế chưa chạy (do broadcast ACC_HOME_DRAW bị rơi mất
// lúc race giữa 2 service restart không cùng lúc).
// [DUAL-SOUL] Accessibility vừa bật -> Homacc PHẢI bật theo NGAY, giống Lock.
// Homeb PHẢI dừng hẳn (không chỉ ẩn) — 2 engine không bao giờ cùng sống.
if (!AccessibleHomeService.isRunning) {
    Intent accIntent = new Intent(this, AccessibleHomeService.class);
    startService(accIntent);
} else {
    drawAccessibleHome();
}
if (HomescreenService.isRunning) {
    stopService(new Intent(this, HomescreenService.class));
}
prefs.edit().putBoolean("shortcut_home_on", false).apply();
createFloatingBars();
checkAndKickBlacklistOnAccEnable(); // [MỚI] tự thoát app Blacklist nếu đang mở lúc bật Acc
        panelEngine = new PanelEngine(this, wm, prefs, /* isAnyMode = */ true);
panelEngine.rebuildAll();
// V19.12.3.6.13: Set tường minh flag — một số ROM không parse đúng
// canRequestFingerprintGestures từ XML, gây fpController không bao giờ
// nhận gesture dù đăng ký callback "thành công".
try {
    AccessibilityServiceInfo info = getServiceInfo();
    if (info != null) {
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES;
        setServiceInfo(info);
    }
} catch (Exception e) {}

refreshFingerprintRegistration();
    } // <-- ĐÂY MỚI LÀ DẤU ĐÓNG ĐÚNG CỦA onServiceConnected()
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
    int eventType = event.getEventType();
    if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

    String pName = event.getPackageName() != null ? event.getPackageName().toString() : "";
    String cName = event.getClassName() != null ? event.getClassName().toString() : "";
    long nowMs = System.currentTimeMillis();

    // [FIX TỐC ĐỘ KHOÁ APP] Kiểm tra LockList NGAY, tách khỏi throttle chung —
    // đảm bảo không bao giờ bị "nuốt" mất sự kiện đổi app thật.
    if (!pName.isEmpty() && nowMs - lastLockCheckMs >= LOCK_CHECK_THROTTLE_MS) {
        lastLockCheckMs = nowMs;
        checkAppLock(pName);
    }

    if (nowMs - lastEventMs < EVENT_THROTTLE_MS) return;
    lastEventMs = nowMs;
    boolean accShouldRun = AccessibleHomeService.isRunning;
    if (accShouldRun != lastAccHomeRunningState) {
        lastAccHomeRunningState = accShouldRun;
        if (accShouldRun && accHomeBars[0] == null) drawAccessibleHome();
        else if (!accShouldRun && accHomeBars[0] != null) removeAccessibleHome();
    }
    // [FIX PUSH-AWAY BÀN PHÍM] Không gating theo tên gói/class của sự kiện hiện tại
    // nữa — sự kiện kích hoạt hàm này gần như luôn đến từ app đang mở chứ KHÔNG
    // phải từ chính cửa sổ bàn phím, nên điều kiện cũ hầu như luôn sai. Quét thẳng
    // danh sách cửa sổ (TYPE_INPUT_METHOD) mỗi lần có sự kiện — đã throttle 200ms
    // ở trên (EVENT_THROTTLE_MS) nên không tốn thêm CPU/pin đáng kể.
    int newKbdHeight = computeKbdHeightPx();
    boolean newIsKbd = newKbdHeight > 0;
String bl = prefs.getString("blacklist", "");
boolean newIsBl = !pName.isEmpty() && bl.contains(pName);
// [MỚI] Blacklist Auto-Homeb: app blacklist vừa mở (false→true)
if (newIsBl && !lastIsBl_cache && prefs.getBoolean("blacklist_auto_homeb_en", false)) {
    triggerBlacklistAutoHomeb();
}
boolean stateChanged = newIsKbd != lastIsKbd_cache
                    || newIsBl != lastIsBl_cache
                    || !pName.equals(lastEventPkg)
                    || Math.abs(newKbdHeight - cachedKbdHeight) >= KBD_HEIGHT_CHANGE_THRESHOLD;

isKbd = newIsKbd;
isBl = newIsBl;
cachedKbdHeight = newKbdHeight;

// [MỚI] Lưới an toàn: Accessibility đang bật (service này đang sống) thì Homeb
// TUYỆT ĐỐI không được sống cùng lúc. Chỉ kiểm tra khi có sự kiện thật (đã
// throttle 200ms bên trên) — zero cost polling, không phải vòng lặp định kỳ.
if (HomescreenService.isRunning) {
    stopService(new Intent(this, HomescreenService.class));
}

if (!stateChanged) return;
    lastEventPkg = pName;
    lastIsKbd_cache = newIsKbd;
    lastIsBl_cache = newIsBl;

    updateVisibility();
    sampleAndApplyIconColors();
    Intent syncIntent = new Intent("com.manhmoc.edgebar.SYNC_STATE");
    syncIntent.putExtra("isKbd", isKbd);
    syncIntent.putExtra("isBl", isBl);
    syncIntent.putExtra("foreground_pkg", pName);
    syncIntent.putExtra("kbd_height", cachedKbdHeight); // [MỚI]
    sendBroadcast(syncIntent);
}
    // V19.12.3.6.8 THE ETERNAL EGO
// getForegroundPackageFromWindows(): lấy package foreground từ getWindows() API
// Pixel 2XL opt: recycle NodeInfo ngay sau khi đọc, tránh leak native memory
// flagRetrieveInteractiveWindows trong config.xml mới cho phép method này hoạt động
private String getForegroundPackageFromWindows() {
    try {
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) return null;
        for (android.view.accessibility.AccessibilityWindowInfo w : windows) {
            // Chỉ lấy TYPE_APPLICATION — bỏ qua keyboard overlay, system panel
            if (w.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                android.view.accessibility.AccessibilityNodeInfo root = w.getRoot();
                if (root != null) {
                    String pkg = root.getPackageName() != null
                        ? root.getPackageName().toString() : "";
                    root.recycle(); // BẮT BUỘC recycle — tránh native memory leak Pixel 2XL
                    if (!pkg.isEmpty()) return pkg;
                }
            }
        }
    } catch (Exception e) {}
    return null;
}
private int computeKbdHeightPx() {
    try {
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return 0;
        for (android.view.accessibility.AccessibilityWindowInfo w : windows) {
            if (w.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                android.graphics.Rect r = new android.graphics.Rect();
                w.getBoundsInScreen(r);
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                return Math.max(0, dm.heightPixels - r.top);
            }
        }
    } catch (Exception ignored) {}
    return 0;
}
private String cachedOwnAppLabel = null;
private String getOwnAppLabel() {
    if (cachedOwnAppLabel == null) {
        try {
            cachedOwnAppLabel = getPackageManager()
                .getApplicationLabel(getApplicationInfo()).toString();
        } catch (Exception e) { cachedOwnAppLabel = "Edge Bar"; }
    }
    return cachedOwnAppLabel;
}
private boolean containsText(android.view.accessibility.AccessibilityNodeInfo node, String needle, int depth) {
    if (node == null || depth > 12) return false;
    CharSequence t = node.getText();
    if (t != null && t.toString().contains(needle)) return true;
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; i++) {
        android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
        if (child != null) {
            boolean r = containsText(child, needle, depth + 1);
            child.recycle();
            if (r) return true;
        }
    }
    return false;
}
/**
 * [MỚI] Đối xứng với triggerBlacklistAutoHomeb(): khi Accessibility vừa được BẬT LẠI
 * (qua Intent/QS Tile/Macro), kiểm tra app đang foreground có nằm trong Blacklist
 * không — nếu có, tự động về Home + kill app đó. Dùng UsageStatsManager vì lúc này
 * EdgeBarService vừa connect, chưa kịp có AccessibilityEvent nào để tự biết qua getWindows().
 */
private void checkAndKickBlacklistOnAccEnable() {
    if (!prefs.getBoolean("blacklist_auto_homeb_en", false)) return;
    String bl = prefs.getString("blacklist", "");
    if (bl.isEmpty()) return;
    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            // Mở rộng cửa sổ lên 24h — không tốn thêm chi phí đáng kể vì hàm này
// CHỈ chạy đúng 1 lần duy nhất mỗi khi Accessibility vừa bật lại (event-driven),
// không phải polling định kỳ. UsageEvents API duyệt tuần tự nên chỉ lấy phần tử
// CUỐI CÙNG (gần "now" nhất) là đủ, không cần giữ toàn bộ danh sách trong RAM.
android.app.usage.UsageEvents events = usm.queryEvents(now - 24 * 60 * 60 * 1000L, now);
            android.app.usage.UsageEvents.Event ev = new android.app.usage.UsageEvents.Event();
            String fgPkg = "";
            while (events.hasNextEvent()) {
                events.getNextEvent(ev);
                if (ev.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    fgPkg = ev.getPackageName();
                }
            }
            if (fgPkg.isEmpty()) return;
            for (String locked : bl.split(",")) {
                if (locked.trim().equals(fgPkg)) {
                    Intent home = new Intent(Intent.ACTION_MAIN);
                    home.addCategory(Intent.CATEGORY_HOME);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(home);
                    final String pkgToKill = fgPkg;
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            android.app.ActivityManager am =
                                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                            am.killBackgroundProcesses(pkgToKill);
                        } catch (Exception ignored) {}
                    }, 150);
                    break;
                }
            }
        } catch (Exception ignored) {}
    }, 700);
}
private void triggerBlacklistAutoHomeb() {
    try {
        String mySvc = getPackageName() + "/" + EdgeBarService.class.getName();
        String cur = android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (cur == null || !cur.contains(mySvc)) return;

        HomeEngineManager.turnOnHomeb(this); // bật Homeb TRƯỚC, tránh khoảng trống mất bar
        prefs.edit().putBoolean("acc_off_by_blacklist_auto", true).apply(); // cờ dành cho tính năng tự bật lại sau này

        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                String[] parts = cur.split(":");
                java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
                for (String pt : parts) if (!pt.trim().isEmpty() && !pt.trim().equals(mySvc)) set.add(pt.trim());
                android.provider.Settings.Secure.putString(
                    getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    android.text.TextUtils.join(":", set));
            } catch (Exception ignored) {}
        }, 150);
    } catch (Exception ignored) {}
}
// [FIX BADTOKEN + TỐI ƯU PIXEL 2XL] Dialog gọi từ Context của AccessibilityService
    // KHÔNG có Activity window token — .show() trực tiếp sẽ ném BadTokenException âm
    // thầm (bị nuốt bởi try-catch của exec()). Ép window type sang loại Overlay hệ
    // thống (TYPE_ACCESSIBILITY_OVERLAY vì đây là AccessibilityService) để dialog vẽ
    // được mà không cần token Activity nào — đây là dialog TẠM (chỉ tồn tại lúc user
    // đang chọn tùy chọn ghi), Zero RAM dư thừa vì object bị GC thu hồi ngay khi dismiss,
    // không giữ reference nào ở field cấp class.
    private void showScreenRecordOptionsThenCapture() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 10);

        android.widget.CheckBox cbAudio = new android.widget.CheckBox(this);
        cbAudio.setText("Ghi âm (Micro)");
        cbAudio.setChecked(prefs.getBoolean("screenrec_audio_en", false));
        box.addView(cbAudio);

        android.widget.CheckBox cbTouches = new android.widget.CheckBox(this);
        cbTouches.setText("Hiển thị vị trí thao tác chạm trên màn hình");
        cbTouches.setChecked(prefs.getBoolean("screenrec_showtouches_en", true));
        box.addView(cbTouches);

        android.app.AlertDialog dlgSR = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Bắt đầu ghi?")
            .setView(box)
            .setPositiveButton("Bắt đầu", (d, w) -> {
                prefs.edit()
                    .putBoolean("screenrec_audio_en", cbAudio.isChecked())
                    .putBoolean("screenrec_showtouches_en", cbTouches.isChecked())
                    .apply();
                Intent permIntent = new Intent(this, ScreenRecordPermissionActivity.class);
                permIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(permIntent);
            })
            .setNegativeButton("Hủy", null)
            .create();
        if (dlgSR.getWindow() != null) {
            dlgSR.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        }
        dlgSR.show();
    }
    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try {
            switch (a) {
                case "YTDL_DOWNLOAD": showYtdlQuickInput(); break;
                case "BACK": performGlobalAction(GLOBAL_ACTION_BACK); break;
                case "HOME": performGlobalAction(GLOBAL_ACTION_HOME); break;
                case "RECENTS": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case "SCREEN_OFF":
                    // [MỚI] Hồi sinh màn Lock ngay lập tức khi ấn nút khóa màn hình
                    SharedPreferences.Editor edOff = prefs.edit();
                    for (String b : BARS) edOff.putBoolean("lock_" + b + "_manual_hide", false);
                    for (String cn : CORNERS) edOff.putBoolean("lock_corner_" + cn + "_manual_hide", false);
                    edOff.apply();
                    updateVisibility();
                    
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); 
                    break;
                case "SPLIT_SCREEN":
                    performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN); 
                    break;
                case "SCREEN_ON":
                    // Thuật toán WakeLock giải phóng RAM nhanh
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null && !pm.isInteractive()) {
                        android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, 
                            "EdgeBar:ScreenOn");
                        wl.acquire(2000); // Chỉ giữ CPU trong 3 giây để bật màn, sau đó tự nhả RAM
                    }
                    break;
                case "POWER_DIALOG": 
                    // [MỚI] Hồi sinh màn Lock khi gọi lệnh mở Power Menu
                    SharedPreferences.Editor edPower = prefs.edit();
                    for (String b : BARS) edPower.putBoolean("lock_" + b + "_manual_hide", false);
                    for (String cn : CORNERS) edPower.putBoolean("lock_corner_" + cn + "_manual_hide", false);
                    edPower.apply();
                    updateVisibility();
                    
                    performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); 
                    break;
                case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break;
                case "SCREEN_RECORD": {
    if (ScreenRecorderService.isRunning) {
        Intent stopIntent = new Intent(this, ScreenRecorderService.class);
        stopIntent.setAction("STOP");
        startService(stopIntent);
    } else {
        // [1 CHẠM] Bỏ hẳn dialog tùy chọn nội bộ — bấm nút là bắn thẳng
        // sang bảng "Bắt đầu ngay" của hệ thống (Android bắt buộc phải có
        // bảng này, không có API nào bỏ qua được — đây là bước duy nhất còn lại).
        Intent permIntent = new Intent(this, ScreenRecordPermissionActivity.class);
        permIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(permIntent);
    }
    break;
}
                case "QUICK_SETTINGS": performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); break;
                case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break;
                case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break;
                case "VOLUME": ((AudioManager) getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break;
                case "AUTO_ROTATE_TOGGLE": {
                    try {
                        if (!android.provider.Settings.System.canWrite(this)) {
                            Toast.makeText(this, "Chưa có quyền 'Sửa đổi cài đặt hệ thống'!", Toast.LENGTH_LONG).show();
                            break;
                        }
                        int cur = android.provider.Settings.System.getInt(getContentResolver(),
                            android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                        android.provider.Settings.System.putInt(getContentResolver(),
                            android.provider.Settings.System.ACCELEROMETER_ROTATION, cur == 1 ? 0 : 1);
                    } catch (Exception e) {}
                    break;
                }
                case "TOGGLE_RECORD": {
                    Intent recIntent = new Intent(this, VoiceRecorderService.class);
                    recIntent.setAction(VoiceRecorderService.ACTION_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(recIntent);
                    else startService(recIntent);
                    break;
                }
                case "PAUSE_RECORD": {
                    if (!VoiceRecorderService.isRunning) break;
                    Intent pauseIntent = new Intent(this, VoiceRecorderService.class);
                    pauseIntent.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(pauseIntent);
                    else startService(pauseIntent);
                    break;
                }
                case "OPEN_STORAGE_SCAN": {
                    // [FIX] Dùng cờ bền trong SharedPreferences thay vì Intent extra —
                    // onResume() của MainActivity LUÔN chạy khi Activity hiện ra, đảm bảo
                    // tuyệt đối nhảy đúng tab Storage. Zero RAM/pin thêm.
                    prefs.edit().putBoolean("pending_storage_scan", true).apply();
                    Intent openStorage = new Intent(this, MainActivity.class);
                    openStorage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(openStorage);
                    break;
                }
                case "SCAN_QR": {
                    Intent qr = new Intent(this, QrScanActivity.class);
                    qr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    startActivity(qr);
                    break;
                }
                case "TOGGLE_OVERLAY": {
                    // [FIX] Explicit Intent (chỉ thẳng class) thay vì implicit broadcast —
                    // đảm bảo ToggleReceiver (khai báo tĩnh trong Manifest) luôn nhận được,
                    // không phụ thuộc giới hạn implicit-broadcast của Android 8+.
                    Intent toggleIntent = new Intent(this, ToggleReceiver.class);
                    toggleIntent.setAction("com.manhmoc.edgebar.TOGGLE_ACC");
                    sendBroadcast(toggleIntent);
                    break;
                }
                case "TOGGLE_WORK_PROFILE": {
                    try {
                        android.os.UserManager um =
                            (android.os.UserManager) getSystemService(Context.USER_SERVICE);
                        android.os.UserHandle work = null;
                        for (android.os.UserHandle uh : um.getUserProfiles()) {
                            if (!uh.equals(android.os.Process.myUserHandle())) { work = uh; break; }
                        }
                        if (work != null) {
                            boolean currentlyQuiet = um.isQuietModeEnabled(work);
                            um.requestQuietModeEnabled(!currentlyQuiet, work);
                        }
                    } catch (SecurityException ignored) {}
                    break;
                }
                                case "PLAY_MY_PLAYLIST": startMyPlaylist(); break;
                case "HIDE_SOME_OVERLAY":
                    hideSomeOverlay("lock_");
                    hideSomeOverlay("homacc_");
                    break;
                case "SHOW_ALL_OVERLAY":
                    showAllOverlay("lock_");
                    showAllOverlay("homacc_");
                    break;
                case "TRIGGER_TAP": case "TRIGGER_DTAP": case "TRIGGER_LONG":
                case "TRIGGER_UP": case "TRIGGER_DOWN": case "TRIGGER_LEFT":
                case "TRIGGER_RIGHT": case "TRIGGER_DIAG":
                case "TRIGGER_UP_DOWN": case "TRIGGER_DOWN_UP":
                case "TRIGGER_LEFT_RIGHT": case "TRIGGER_RIGHT_LEFT":
                case "TRIGGER_UP_HOLD": case "TRIGGER_DOWN_HOLD":
                case "TRIGGER_LEFT_HOLD": case "TRIGGER_RIGHT_HOLD": case "TRIGGER_DIAG_HOLD":
                    dispatchRealScreenGesture(a);
                    break;
                case "TRIGGER_ACC_MENU_2F":
                    dispatchTwoFingerAccMenuGesture();
                    break;
default:
                        if (a.startsWith("PANEL_")) {
                            // [THAY] Panel định danh bằng UUID Data Pack, không còn 1/2/3 cố định
                            Intent op = new Intent("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
                            op.putExtra("panel_id", a.substring(6));
                            sendBroadcast(op);
                        } else if (a.startsWith("INTENT_")) {
                            fireIntentById(a.substring(7)); // UUID thật, không phải số thứ tự
                        } else if (a.startsWith("MACRO_")) {
                            String macroId = a.substring(6);
                            Intent iM = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                            iM.putExtra("services", prefs.getString("macro_" + macroId + "_svcs", ""));
                            sendBroadcast(iM);
                        } else if (a.startsWith("RUN_SHORTCUT_")) {
                            // [TỐI ƯU PIXEL 2XL] Xử lý trực tiếp Shortcut qua UUID tại chỗ, Zero-IPC overhead
                            // Giải quyết triệt để lỗi Lenap/Frontier/Ecosystem không gọi được shortcut
                            String scId = a.substring(13); // Độ dài của "RUN_SHORTCUT_"
                            try {
                                String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                                if (!uri.isEmpty()) {
                                    Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                                    scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(scIntent);
                                }
                            } catch (Exception ignored) {}
                        }
                        break;
            }
        } catch (Exception e) {}
    }

    private void fireIntent(String idx) {
        try {
            String act = prefs.getString("i" + idx + "_act", "");
            String pkg = prefs.getString("i" + idx + "_pkg", "");
            Intent i;
            if (act.isEmpty() && !pkg.isEmpty()) {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) return;
            } else {
                i = new Intent(act);
                if (!pkg.isEmpty()) i.setPackage(pkg);
                String cls = prefs.getString("i" + idx + "_cls", "");
                if (!pkg.isEmpty() && !cls.isEmpty())
                    i.setComponent(new android.content.ComponentName(pkg, cls));
                String data = prefs.getString("i" + idx + "_data", "");
                if (!data.isEmpty()) i.setData(android.net.Uri.parse(data));
                String cat = prefs.getString("i" + idx + "_cat", "");
                if (!cat.isEmpty()) i.addCategory(cat);
                String flg = prefs.getString("i" + idx + "_flags", "");
                if (!flg.isEmpty()) i.addFlags(Integer.parseInt(flg));
            }
            if (prefs.getBoolean("i" + idx + "_br", true) && !act.isEmpty()) {
                sendBroadcast(i);
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception e) {}
    }
    /** Bản thay thế fireIntent() cho hệ Intent động (UUID) — đọc đúng field mà
 *  openIntentEditorV2() đã lưu ("intent_<uuid>_act/_pkg/_cls/_data/_cat/_flags/_br"). */
private void fireIntentById(String id) {
    try {
        String act = prefs.getString("intent_" + id + "_act", "");
        String pkg = prefs.getString("intent_" + id + "_pkg", "");
        Intent i;
        if (act.isEmpty() && !pkg.isEmpty()) {
            i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                Toast.makeText(this, "Không tìm thấy app: " + pkg, Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (act.isEmpty()) {
            // [FIX] Trước đây rơi vào new Intent("") im lặng thất bại.
            Toast.makeText(this, "Thiếu Action hoặc Package — không thể chạy Intent!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            i = new Intent(act);
            if (!pkg.isEmpty()) i.setPackage(pkg);
            String cls = prefs.getString("intent_" + id + "_cls", "");
            if (!pkg.isEmpty() && !cls.isEmpty())
                i.setComponent(new android.content.ComponentName(pkg, cls));
            String data = prefs.getString("intent_" + id + "_data", "");
            if (!data.isEmpty()) i.setData(android.net.Uri.parse(data));
            String cat = prefs.getString("intent_" + id + "_cat", "");
            if (!cat.isEmpty()) i.addCategory(cat);
            String flg = prefs.getString("intent_" + id + "_flags", "");
            if (!flg.isEmpty()) i.addFlags(Integer.parseInt(flg));
        }
        if (prefs.getBoolean("intent_" + id + "_br", false) && !act.isEmpty()) {
            sendBroadcast(i);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    } catch (Exception e) {
        // [FIX] Không nuốt lỗi im lặng nữa — báo rõ nguyên nhân cho người dùng.
        Toast.makeText(this, "Lỗi chạy Intent: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}
    private Animator activeAnimAnimator;
    private void playAnim() {
        if (fV == null) return;
        // [TỐI ƯU] Không còn wm.updateViewLayout() ở đây nữa (view đã MATCH_PARENT cố định) —
        // Rung/Trigger/Animation giờ bắn ra gần như đồng thời, không còn trễ 1 nhịp IPC.
        if (activeAnimAnimator != null) { activeAnimAnimator.cancel(); activeAnimAnimator = null; }

        int style = prefs.getInt("anim_style", 0);
        int dur = Math.max(60, prefs.getInt("anim_dur", 1500));
        int holdDur = Math.max(0, prefs.getInt("anim_hold_dur", 400)); // [MỚI] thời gian giữ đỉnh sáng

        fV.setVisibility(View.VISIBLE);
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        if (style == 0) {
            int halfDur = Math.max(1, dur / 2);
            ValueAnimator fadeIn = ValueAnimator.ofFloat(0f, 1f);
            fadeIn.setDuration(halfDur);
            fadeIn.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            ValueAnimator fadeOut = ValueAnimator.ofFloat(1f, 0f);
            fadeOut.setDuration(halfDur);
            fadeOut.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            set.play(fadeOut).after(holdDur).after(fadeIn); // fadeIn -> giữ holdDur -> fadeOut
        } else {
            fV.setAlpha(1f);
            ValueAnimator dash = ValueAnimator.ofFloat(0f, 1f);
            dash.setDuration(dur);
            dash.addUpdateListener(a -> fV.setPhase((float) a.getAnimatedValue()));
            ValueAnimator fadeOut = ValueAnimator.ofFloat(1f, 0f);
            fadeOut.setDuration(200);
            fadeOut.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            set.play(fadeOut).after(holdDur).after(dash);
        }
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                fV.setAlpha(0f); fV.setVisibility(View.GONE);
                if (activeAnimAnimator == set) activeAnimAnimator = null;
            }
            @Override public void onAnimationCancel(Animator a) {
                fV.setAlpha(0f); fV.setVisibility(View.GONE);
            }
        });
        activeAnimAnimator = set;
        set.start();
    }
    // 1. Thêm hàm này để xử lý xuyên thấu cảm ứng
    private void setTransientUntouchable(boolean untouchable) {
        try {
            for (View[] arr : new View[][]{bars, corners, accHomeBars, accHomeCorners}) {
                for (int i = 0; i < arr.length; i++) {
                    View v = arr[i];
                    if (v == null || v.getWindowToken() == null || v.getVisibility() != View.VISIBLE || v.getLayoutParams() == null) continue;
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
                    if (untouchable) {
                        p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    } else {
                        int priMode = 0;
                        if (arr == bars) priMode = prefs.getInt("lock_" + BARS[i] + "_pri_mode", 0);
                        else if (arr == corners) priMode = prefs.getInt("lock_corner_" + CORNERS[i] + "_pri_mode", 0);
                        else if (arr == accHomeBars) priMode = prefs.getInt("homacc_" + BARS[i] + "_pri_mode", 0);
                        else if (arr == accHomeCorners) priMode = prefs.getInt("homacc_corner_" + CORNERS[i] + "_pri_mode", 0);
                        if (priMode == 0) p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    }
                    updateLayoutIfChanged(v, p); // <--- ĐÃ SỬA THÀNH HÀM CÓ CACHE
                }
            }
        } catch (Exception ignored) {}
    }
// 2. Bắn cử chỉ đích xác dựa trên tọa độ điểm chạm thực tế của ngón tay
    private void dispatchRealScreenGesture(String trigger) {
        if (Build.VERSION.SDK_INT < 24) return;

                isDispatchingSyntheticGesture = true;
        setTransientUntouchable(true); // Xuyên thấu toàn bộ Bar/Corner

        if (syntheticGuardResetRunnable != null) syntheticGuardHandler.removeCallbacks(syntheticGuardResetRunnable);
        syntheticGuardResetRunnable = () -> {
            isDispatchingSyntheticGesture = false;
            setTransientUntouchable(false); // Khôi phục lại trạng thái cảm ứng
        };
        // [FIX GIẬT TAP LIÊN TỤC] Mốc an toàn dự phòng giờ co giãn theo đúng "sức nặng"
        // của từng loại trigger, thay vì cố định 350ms cho mọi trường hợp — TAP chỉ
        // khoá cảm ứng ~150ms (đủ nhả tay ra là chạm lại được ngay), LONG/DTAP vẫn có
        // đủ thời gian an toàn hơn cả duration thật của gesture.
        int guardFallbackMs;
        if (trigger.equals("TRIGGER_TAP")) guardFallbackMs = 150;
        else if (trigger.equals("TRIGGER_DTAP")) guardFallbackMs = 220;
        else if (trigger.equals("TRIGGER_LONG")) guardFallbackMs = 750;
        else if (trigger.endsWith("_HOLD")) guardFallbackMs = 200 + prefs.getInt("sim_gesture_hold_dur", 500);
        else if (trigger.contains("_")) guardFallbackMs = 300; // combo 2 nhịp
        else guardFallbackMs = 200; // swipe đơn hướng
        syntheticGuardHandler.postDelayed(syntheticGuardResetRunnable, guardFallbackMs);

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        final float cx = dm.widthPixels / 2f;
        final float cy = dm.heightPixels / 2f;
        final float ox = globalTouchStartX >= 0 ? globalTouchStartX : cx;
        final float oy = globalTouchStartY >= 0 ? globalTouchStartY : cy;
        final float ex = globalTouchEndX >= 0 ? globalTouchEndX : ox;
        final float ey = globalTouchEndY >= 0 ? globalTouchEndY : oy;
        final float actualDist = (float) Math.hypot(ex - ox, ey - oy);

        // [MỚI] Đọc từ thanh trượt General Options — riêng TAP/DTAP dùng độ trễ
        // tối thiểu (max 2ms) vì không cần thời gian ổn định hướng vuốt như
        // Swipe/Hold, giúp tap giả lập nhạy gần bằng chạm thật của OS.
        boolean isTapLikeTrigger = trigger.equals("TRIGGER_TAP") || trigger.equals("TRIGGER_DTAP");
        int dispatchDelay = isTapLikeTrigger
            ? Math.min(prefs.getInt("sim_gesture_delay", 10), 2)
            : prefs.getInt("sim_gesture_delay", 10);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                float defaultSwipeDist = Math.min(cx, cy) * (prefs.getInt("sim_swipe_dist_pct", 80) / 100f);
                android.graphics.Path path = new android.graphics.Path();
                int duration = prefs.getInt("sim_gesture_dur", 20);

                boolean isTapOrLong = trigger.contains("TAP") || trigger.contains("LONG");
                boolean isCombo = trigger.contains("UP_DOWN") || trigger.contains("DOWN_UP") || trigger.contains("LEFT_RIGHT") || trigger.contains("RIGHT_LEFT");
                boolean isHoldGesture = trigger.endsWith("_HOLD");
                
                boolean useExactPath = actualDist > 40f && !isTapOrLong && !isCombo && !isHoldGesture;

                if (useExactPath) {
                    path.moveTo(ox, oy);
                    path.lineTo(ex, ey);
                } else {
                    switch (trigger) {
                        case "TRIGGER_UP": path.moveTo(ox, oy); path.lineTo(ox, oy - defaultSwipeDist); break;
                        case "TRIGGER_DOWN": path.moveTo(ox, oy); path.lineTo(ox, oy + defaultSwipeDist); break;
                        case "TRIGGER_LEFT": path.moveTo(ox, oy); path.lineTo(ox - defaultSwipeDist, oy); break;
                        case "TRIGGER_RIGHT": path.moveTo(ox, oy); path.lineTo(ox + defaultSwipeDist, oy); break;
                        case "TRIGGER_DIAG": path.moveTo(ox, oy); path.lineTo(ox - defaultSwipeDist, oy - defaultSwipeDist); break;
                        case "TRIGGER_TAP": path.moveTo(ox, oy); path.lineTo(ox, oy + 1); duration = prefs.getInt("sim_tap_dur", 5); break;
                        case "TRIGGER_LONG": path.moveTo(ox, oy); path.lineTo(ox, oy + 1); duration = prefs.getInt("sim_long_dur", 600); break;
                        case "TRIGGER_DTAP": path.moveTo(ox, oy); path.lineTo(ox, oy + 1); duration = prefs.getInt("sim_tap_dur", 5); break;
                        case "TRIGGER_UP_DOWN": path.moveTo(ox, oy); path.lineTo(ox, oy - defaultSwipeDist); path.lineTo(ox, oy + defaultSwipeDist * 0.35f); duration = 90; break;
                        case "TRIGGER_DOWN_UP": path.moveTo(ox, oy); path.lineTo(ox, oy + defaultSwipeDist); path.lineTo(ox, oy - defaultSwipeDist * 0.35f); duration = 90; break;
                        case "TRIGGER_LEFT_RIGHT": path.moveTo(ox, oy); path.lineTo(ox - defaultSwipeDist, oy); path.lineTo(ox + defaultSwipeDist * 0.35f, oy); duration = 90; break;
                        case "TRIGGER_RIGHT_LEFT": path.moveTo(ox, oy); path.lineTo(ox + defaultSwipeDist, oy); path.lineTo(ox - defaultSwipeDist * 0.35f, oy); duration = 90; break;
                        case "TRIGGER_UP_HOLD": path.moveTo(ox, oy); path.lineTo(ox, oy - defaultSwipeDist); break;
                        case "TRIGGER_DOWN_HOLD": path.moveTo(ox, oy); path.lineTo(ox, oy + defaultSwipeDist); break;
                        case "TRIGGER_LEFT_HOLD": path.moveTo(ox, oy); path.lineTo(ox - defaultSwipeDist, oy); break;
                        case "TRIGGER_RIGHT_HOLD": path.moveTo(ox, oy); path.lineTo(ox + defaultSwipeDist, oy); break;
                        case "TRIGGER_DIAG_HOLD": path.moveTo(ox, oy); path.lineTo(ox - defaultSwipeDist, oy - defaultSwipeDist); break;
                    }
                }
                android.accessibilityservice.GestureDescription.Builder builder =
                    new android.accessibilityservice.GestureDescription.Builder();

                final boolean isDtap = trigger.equals("TRIGGER_DTAP");
                GestureResultCallback cb = new GestureResultCallback() {
                    @Override public void onCompleted(android.accessibilityservice.GestureDescription g) {
                        if (!isDtap) { isDispatchingSyntheticGesture = false; setTransientUntouchable(false); }
                    }
                    @Override public void onCancelled(android.accessibilityservice.GestureDescription g) {
                        isDispatchingSyntheticGesture = false; setTransientUntouchable(false);
                    }
                };

                if (isHoldGesture) {
                    // [MỚI] Vuốt tới đích rồi GIỮ NGUYÊN tại đó (dùng continueStroke nối tiếp
                    // 1 đoạn gần như đứng yên) — willContinue=true ở stroke 1 để OS không nhả tay.
                    int holdDur = Math.max(60, prefs.getInt("sim_gesture_hold_dur", 500));
                    android.accessibilityservice.GestureDescription.StrokeDescription moveStroke =
                        new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration, true);
                    android.graphics.PathMeasure pmHold = new android.graphics.PathMeasure(path, false);
                    float[] endXY = new float[2];
                    pmHold.getPosTan(pmHold.getLength(), endXY, null);
                    android.graphics.Path holdPath = new android.graphics.Path();
                    holdPath.moveTo(endXY[0], endXY[1]);
                    holdPath.lineTo(endXY[0], endXY[1] + 0.01f);
                    android.accessibilityservice.GestureDescription.StrokeDescription holdStroke =
                        moveStroke.continueStroke(holdPath, 0, holdDur, false);
                    builder.addStroke(moveStroke);
                    builder.addStroke(holdStroke);
                    dispatchGesture(builder.build(), cb, null);
                } else {
                    builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration));
                    dispatchGesture(builder.build(), cb, null);
                }
                if (isDtap) {
                    final android.graphics.Path finalPath = path;
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            android.accessibilityservice.GestureDescription.Builder b2 = new android.accessibilityservice.GestureDescription.Builder();
                            b2.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(finalPath, 0, prefs.getInt("sim_tap_dur", 5)));
                            dispatchGesture(b2.build(), new GestureResultCallback() {
                                @Override public void onCompleted(android.accessibilityservice.GestureDescription g) {
                                    isDispatchingSyntheticGesture = false; setTransientUntouchable(false);
                                }
                                @Override public void onCancelled(android.accessibilityservice.GestureDescription g) {
                                    isDispatchingSyntheticGesture = false; setTransientUntouchable(false);
                                }
                            }, null);
                        } catch (Exception ignored) {}
                    // [TĂNG TỐC] Ép độ trễ nhịp 2 của Double Tap xuống còn 40ms
                    }, 40);
                }
            } catch (Exception ignored) {}
        }, dispatchDelay);
    }
// [MỚI] Trigger đặc biệt TRIGGER_ACC_MENU_2F — CHỈ chạy được ở EdgeBarService
// (Lock/Homacc), Homeb không có Accessibility nên không giả lập được cử chỉ này.
// Luôn xuất phát 2 ngón từ MÉP DƯỚI màn hình (vùng nav bar), KHÔNG dùng toạ độ
// chạm thật (globalTouchStartX/Y) như 17 trigger còn lại.
private void dispatchTwoFingerAccMenuGesture() {
    if (Build.VERSION.SDK_INT < 24) return;
    isDispatchingSyntheticGesture = true;
    setTransientUntouchable(true);
    if (syntheticGuardResetRunnable != null) syntheticGuardHandler.removeCallbacks(syntheticGuardResetRunnable);
    syntheticGuardResetRunnable = () -> {
        isDispatchingSyntheticGesture = false;
        setTransientUntouchable(false);
    };
    syntheticGuardHandler.postDelayed(syntheticGuardResetRunnable, 600);

    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
    float w = dm.widthPixels, h = dm.heightPixels;
    float startY = h - 4f;                 // sát mép dưới cùng — đúng vùng nav bar
    float endY = h - (h * 0.55f);           // vuốt lên khoảng nửa màn hình
    float f1x = w * 0.40f, f2x = w * 0.60f; // 2 ngón song song, cách nhau 20% bề ngang

    android.graphics.Path p1 = new android.graphics.Path();
    p1.moveTo(f1x, startY); p1.lineTo(f1x, endY);
    android.graphics.Path p2 = new android.graphics.Path();
    p2.moveTo(f2x, startY); p2.lineTo(f2x, endY);

    int dur = Math.max(80, prefs.getInt("sim_gesture_dur", 20) * 4);

    android.accessibilityservice.GestureDescription.Builder builder =
        new android.accessibilityservice.GestureDescription.Builder();
    builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p1, 0, dur));
    builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p2, 0, dur));

    dispatchGesture(builder.build(), new GestureResultCallback() {
        @Override public void onCompleted(android.accessibilityservice.GestureDescription g) {
            isDispatchingSyntheticGesture = false; setTransientUntouchable(false);
        }
        @Override public void onCancelled(android.accessibilityservice.GestureDescription g) {
            isDispatchingSyntheticGesture = false; setTransientUntouchable(false);
        }
    }, null);
}
    // [MỚI] Ẩn thủ công đúng danh sách bar/corner user đã chọn cho rule này — tái dùng
    // NGUYÊN VẸN cờ "_manual_hide" đã có sẵn (đọc trong updateVisibility()/updateHomaccLive()),
    // Zero-cost khi rule không gán HIDE_SOME_OVERLAY: chỉ 1 lệnh đọc prefs, return ngay nếu rỗng.
        private void hideSomeOverlay(String key) {
        String prefix = key.startsWith("homacc_") ? "homacc_" : "lock_";
        String targetsBar = prefs.getString(prefix + "bar_hide_targets", "");
        String targetsCorner = prefs.getString(prefix + "corner_hide_targets", "");
        String targets = targetsBar + (targetsBar.isEmpty() || targetsCorner.isEmpty() ? "" : ",") + targetsCorner;
        
        if (targets.isEmpty()) return;
        // [TỐI ƯU] Ẩn TRỰC TIẾP đúng view thay vì gọi updateVisibility()/updateHomaccLive()
        // (duyệt lại toàn bộ 16 Bar/Corner + panelEngine.rebuildAll()) — đây là nguồn giật
        // chính khi HIDE_SOME_OVERLAY chạy cùng lúc với TRIGGER_* giả lập cử chỉ thật.
        boolean isHomacc = prefix.equals("homacc_");
        View[] barArr = isHomacc ? accHomeBars : bars;
        View[] cornerArr = isHomacc ? accHomeCorners : corners;
        SharedPreferences.Editor ed = prefs.edit();
        boolean changed = false;
        for (String t : targets.split(",")) {
            String tt = t.trim();
            if (tt.isEmpty()) continue;
            boolean isCorner = tt.equals("br") || tt.equals("bl") || tt.equals("tr") || tt.equals("tl");
            String mid = isCorner ? "corner_" + tt : tt;
            String k = prefix + mid + "_manual_hide";
            if (!prefs.getBoolean(k, false)) { ed.putBoolean(k, true); changed = true; }
            if (isCorner) {
                int idx = java.util.Arrays.asList(CORNERS).indexOf(tt);
                if (idx >= 0 && cornerArr[idx] != null) cornerArr[idx].setVisibility(View.GONE);
            } else {
                int idx = java.util.Arrays.asList(BARS).indexOf(tt);
                if (idx >= 0 && barArr[idx] != null) barArr[idx].setVisibility(View.GONE);
            }
        }
        if (changed) ed.apply();
    }
    private void showAllOverlay(String key) {
        // [MỚI] Hồi sinh hoàn toàn: Hủy mọi cờ xuyên thấu/giả lập đang kẹt
        isDispatchingSyntheticGesture = false;
        setTransientUntouchable(false);

        String prefix = key.startsWith("homacc_") ? "homacc_" : "lock_";
        boolean changed = false;
        SharedPreferences.Editor ed = prefs.edit();
        for (String barKey : BARS) {
            String k = prefix + barKey + "_manual_hide";
            if (prefs.getBoolean(k, false)) { ed.putBoolean(k, false); changed = true; }
        }
        for (String cornerKey : CORNERS) {
            String k = prefix + "corner_" + cornerKey + "_manual_hide";
            if (prefs.getBoolean(k, false)) { ed.putBoolean(k, false); changed = true; }
        }
        if (changed) { 
            ed.apply(); 
            if (prefix.equals("homacc_")) updateHomaccLive();
            else updateVisibility(); 
        }
    }
    private void doVibrate(int dur) { if (dur<=0) return; try { if (Build.VERSION.SDK_INT>=26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }

    // [MỚI] Phát nhạc từ Download/My Playlist — xem MyPlaylistService.java
    private void startMyPlaylist() {
        Intent i = new Intent(this, MyPlaylistService.class);
        i.setAction(MyPlaylistService.ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }
// ===== YTDL QUICK INPUT OVERLAY — chỉ tồn tại đúng lúc dùng, Zero-RAM lúc đóng =====
    private View ytdlOverlay;

    private void showYtdlQuickInput() {
        if (ytdlOverlay != null) return; // đang mở sẵn -> không chồng lần 2
        if (!Settings.canDrawOverlays(this)) return;

        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setPadding(40, 40, 40, 30);
        card.setOnClickListener(v -> {}); // chặn chạm xuyên qua card làm đóng nhầm
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F2121212"));
        bg.setCornerRadius(28f);
        card.setBackground(bg);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("🎵 YTDLnis — Tải nhạc/video");
        title.setTextColor(Color.parseColor("#FFD700"));
        title.setTextSize(15f);
        title.setPadding(0, 0, 0, 20);
        card.addView(title);

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Dán link hoặc nhập tên bài hát");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#2C2C2C"));
        inputBg.setCornerRadius(20f);
        input.setBackground(inputBg);
        input.setPadding(24, 20, 24, 20);
        input.setText(prefs.getString("ytdl_last_link", ""));
        card.addView(input);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = 24;
        btnRow.setLayoutParams(rowLp);

        android.widget.Button bCancel = ytdlBtn("HỦY", "#333333", Color.WHITE);
        android.widget.Button bSave = ytdlBtn("LƯU LINK", "#4CAF50", Color.WHITE);
        android.widget.Button bDownload = ytdlBtn("TẢI", "#00E5FF", Color.BLACK);
        btnRow.addView(bCancel); btnRow.addView(bSave); btnRow.addView(bDownload);
        card.addView(btnRow);

        bCancel.setOnClickListener(v -> removeYtdlOverlay());
        bSave.setOnClickListener(v -> {
            prefs.edit().putString("ytdl_last_link", input.getText().toString().trim()).apply();
            removeYtdlOverlay();
        });
        bDownload.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (!q.isEmpty()) {
                prefs.edit().putString("ytdl_last_link", q).apply();
                try {
                    Intent y = new Intent(Intent.ACTION_SEND);
                    y.setType("text/plain");
                    y.putExtra(Intent.EXTRA_TEXT, q);
                    y.setPackage("com.deniscerri.ytdl");
                    y.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(y);
                } catch (Exception ignored) {}
            }
            removeYtdlOverlay();
        });

        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams cardLp = new android.widget.FrameLayout.LayoutParams(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
        cardLp.gravity = Gravity.CENTER;
        wrap.addView(card, cardLp);
        wrap.setOnClickListener(v -> removeYtdlOverlay()); // chạm ra ngoài thẻ -> hủy

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        lp.gravity = Gravity.CENTER;

        try {
            wm.addView(wrap, lp);
            ytdlOverlay = wrap;
            input.requestFocus();
        } catch (Exception ignored) {}
    }

    private android.widget.Button ytdlBtn(String text, String bg, int textColor) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(text); b.setTextColor(textColor); b.setTextSize(12.5f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bg)); g.setCornerRadius(16f);
        b.setBackground(g);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(6, 0, 6, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void removeYtdlOverlay() {
    if (ytdlOverlay == null) return;
    // [FIX GBOARD BỊ CHẶN] Bắt buộc giải phóng IME TRƯỚC khi gỡ cửa sổ, nếu không
    // InputMethodManagerService sẽ "kẹt" nghĩ cửa sổ này vẫn giữ focus bàn phím,
    // khiến Gboard không hiện được ở bất kỳ app nào khác trên toàn máy.
    try {
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(ytdlOverlay.getWindowToken(), 0);
        ytdlOverlay.clearFocus();
    } catch (Exception ignored) {}
    try {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) ytdlOverlay.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(ytdlOverlay, lp);
    } catch (Exception ignored) {}
    try { wm.removeView(ytdlOverlay); } catch (Exception ignored) {}
    ytdlOverlay = null;
}
    // Battery opt Pixel 2XL: CHỈ đăng ký callback khi user thực sự gán ít nhất
// 1 rule cho "fingerprint" (ở tab HOMACC hoặc HOME). Nếu không có rule nào,
// KHÔNG đăng ký — tránh giữ sensor driver ở trạng thái lắng nghe vô ích.
private boolean hasFingerprintRule() {
    String[] gestures = {"up","down","left","right","up_hold","down_hold","left_hold","right_hold"};
    for (String prefix : new String[]{"home_fingerprint_", "homacc_fingerprint_", "texture_fingerprint_"}) {
        for (String g : gestures) {
            if (!prefs.getString(prefix + g, "NONE").equals("NONE")) return true;
        }
    }
    return false;
}
private void refreshFingerprintRegistration() {
    if (Build.VERSION.SDK_INT < 26) return;
    boolean needed = hasFingerprintRule();
    if (needed && !fpRegistered) {
        if (fpController == null) fpController = getFingerprintGestureController();
        // FIX: đăng ký NGAY khi có fpController, KHÔNG gate theo isGestureDetectionAvailable().
        // Đăng ký callback không đòi hỏi cảm biến phải sẵn sàng ngay lúc đó — gesture sẽ chỉ
        // không bắn cho tới khi availability = true, và onGestureDetectionAvailabilityChanged()
        // sẽ tự báo khi đủ điều kiện. Gate cũ khiến callback không bao giờ được đăng ký nếu
        // lần gọi đầu tiên rơi đúng lúc cảm biến bận (rất hay gặp lúc service vừa connect).
        if (fpController != null) {
            if (fpCallback == null) {
                fpCallback = new FingerprintGestureController.FingerprintGestureCallback() {
                    @Override public void onGestureDetected(int gesture) {
                        String dir;
                        switch (gesture) {
                            case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP: dir = "up"; break;
                            case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN: dir = "down"; break;
                            case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT: dir = "left"; break;
                            case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT: dir = "right"; break;
                            default: return;
        }
        String prefix = AccessibleHomeService.isRunning ? "homacc_fingerprint_" : "home_fingerprint_";
        handleAction(prefix + dir);
        handleAction("texture_fingerprint_" + dir);
    }
    @Override public void onGestureDetectionAvailabilityChanged(boolean available) {
    android.util.Log.d("EdgeBar_FP", "Fingerprint availability = " + available);
    // [FIX] Một số ROM âm thầm huỷ binding của callback khi cảm biến chuyển
    // trạng thái không khả dụng → khả dụng. Ép đăng ký lại tại đúng thời điểm
    // "available == true" để tự phục hồi, không cần chờ SCREEN_OFF/ON mới retry.
    if (available && fpController != null && fpCallback != null) {
        try {
            fpController.unregisterFingerprintGestureCallback(fpCallback);
            fpController.registerFingerprintGestureCallback(fpCallback, null);
        } catch (Exception ignored) {}
    }
}
                };
            }
            fpController.registerFingerprintGestureCallback(fpCallback, null);
            fpRegistered = true;
        }
    } else if (!needed && fpRegistered) {
        if (fpController != null && fpCallback != null) {
            fpController.unregisterFingerprintGestureCallback(fpCallback);
        }
        fpRegistered = false;
    }
}
        private void createFloatingBars() {
        fV = new FlashView(this);
        fV.setAlpha(0f); fV.setVisibility(View.GONE);
        // [TỐI ƯU] Giữ cố định MATCH_PARENT ngay từ đầu — không còn co giãn 0x0↔MATCH_PARENT
        // qua wm.updateViewLayout() mỗi lần chạy Animation (xem playAnim()), giúp hiệu ứng bắt
        // đầu gần như tức thời, đồng bộ với Rung/Hành động thay vì trễ 1 nhịp IPC hệ thống.
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { wm.addView(fV, fp); } catch(Exception e){}
        for (int i=0;i<12;i++) {
            bars[i] = new BarView(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1,1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch(Exception e){}
            bars[i].setOnTouchListener(new SidebarTouchListener("lock_"+BARS[i], bars[i]));
        }
        for (int i=0;i<4;i++) {
            corners[i] = new CornerView(this, i, "lock_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1,1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            try { wm.addView(corners[i], p); } catch(Exception e){}
            corners[i].setOnTouchListener(new SidebarTouchListener("lock_corner_"+CORNERS[i], corners[i]));
        }
        updateVisibility();
    }
    /**
 * Đồng bộ preview Homacc cho không gian Frontier — KHÔNG đụng tới
 * AccessibleHomeService thật. Zero cost khi trạng thái không đổi
 * (so sánh cache trước, chỉ gọi WM khi thay đổi thực sự).
 */
private void syncHomaccPreviewState() {
    boolean wantPreview = prefs.getBoolean("preview_homacc", false);
    if (wantPreview == lastPreviewHomaccState) return; // không đổi → zero IPC
    lastPreviewHomaccState = wantPreview;
    if (wantPreview) {
        if (!isHomaccDrawn) drawAccessibleHome();
    } else {
        // CHỈ gỡ nếu Homacc THẬT (AccessibleHomeService) không đang chạy —
        // tránh gỡ nhầm overlay thật khi thoát Frontier giữa lúc Homacc đang bật
        if (!AccessibleHomeService.isRunning && isHomaccDrawn) removeAccessibleHome();
    }
}
    private void updateVisibility() {
    syncHomaccPreviewState(); // THÊM DÒNG NÀY — đồng bộ preview Homacc, zero cost nếu không đổi
    boolean isPreview = prefs.getBoolean("preview_lock", false);
        boolean isLocked = km.isKeyguardLocked() || isPreview;
        boolean avoidKbd = prefs.getBoolean("avoid_kbd", true);
        boolean hide = isBl; // isKbd không ẩn nữa — đẩy lên thay vì ẩn
boolean pushForKbd = avoidKbd && cachedKbdHeight > 0;
if (hide && fV != null) fV.setVisibility(View.GONE);
for (int i=0;i<12;i++) {
            if (bars[i]==null) continue;
            boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", false);
        bars[i].setVisibility((en && isLocked && !hide && !prefs.getBoolean("lock_"+BARS[i]+"_manual_hide", false)) ? View.VISIBLE : View.GONE);
    if (en && isLocked) {
                int alpha = prefs.getInt("lock_"+BARS[i]+"_alpha",50);
                int w = prefs.getInt("lock_"+BARS[i]+"_w",300);
                int h = prefs.getInt("lock_"+BARS[i]+"_h",60);
                int x = prefs.getInt("lock_"+BARS[i]+"_x",0);
                int y = prefs.getInt("lock_"+BARS[i]+"_y",0);
                int visMode = prefs.getInt("lock_"+BARS[i]+"_vis_mode",0);
                int barHideDur = prefs.getInt("lock_bar_hide_dur", 2500);
                ((BarView)bars[i]).updateProps(alpha, visMode==1, barHideDur, visMode==2, prefs.getInt("lock_bar_radius", 24));
                int iconSize = prefs.getInt("lock_"+BARS[i]+"_icon_size", prefs.getInt("lock_bar_icon_size", 40));
int iconAlpha = prefs.getInt("lock_"+BARS[i]+"_icon_alpha", prefs.getInt("lock_bar_icon_alpha", 255));
((BarView)bars[i]).setIcons(resolveBarIcons(prefs.getString("lock_"+BARS[i]+"_icons",""), iconSize), iconAlpha);
                int priMode = prefs.getInt("lock_"+BARS[i]+"_pri_mode",0);
                int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode==1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        int pushY = (pushForKbd && (i==0 || i==1 || i==5 || i==9 || i==11)) ? cachedKbdHeight : 0;
// Ở HomescreenService thì thay thành: int pushY = (pushForKbd && (i==0 || i==1 || i==5 || i==9 || i==11)) ? lastKbdHeight : 0;
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
        p.flags = baseFlags; p.width = w; p.height = h; p.x = x; p.y = y + pushY; p.gravity = GRAV[i];
        updateLayoutIfChanged(bars[i], p);
        if (priMode==0) applyAntiTapjacking(bars[i], w, h);
    }
}
        for (int i=0;i<4;i++) {
            if (corners[i]==null) continue;
            boolean cornEn = prefs.getBoolean("lock_corner_"+CORNERS[i]+"_en", false);
            corners[i].setVisibility((cornEn && isLocked && !hide && !prefs.getBoolean("lock_corner_"+CORNERS[i]+"_manual_hide", false)) ? View.VISIBLE : View.GONE);
            if (cornEn && isLocked) {
                String ck = "lock_corner_"+CORNERS[i]+"_";
                int moonAlpha = prefs.getInt("lock_corner_moon_alpha",100);
                int strokeAlpha = prefs.getInt("lock_corner_stroke_alpha",200);
                int hideDelay = prefs.getInt("lock_corner_hide_dur",2500);
                int visMode = prefs.getInt(ck+"vis_mode",0);
                boolean isAuto = (visMode==1), isInv = (visMode==2);
                ((CornerView)corners[i]).updateProps(prefs.getInt("lock_corner_thick",8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                int priMode = prefs.getInt(ck+"pri_mode",0);
                int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode==1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
                p.flags = baseFlags; p.gravity = C_GRAV[i];
                int wPref = prefs.getInt(ck+"w",100), hPref = prefs.getInt(ck+"h",100);
                int mwPref = prefs.getInt(ck+"moon_w",100), mhPref = prefs.getInt(ck+"moon_h",100);
                int mxOffset = Math.abs(prefs.getInt(ck+"moon_x",1250)-1250);
                int myOffset = Math.abs(prefs.getInt(ck+"moon_y",1250)-1250);
                p.width = Math.max(10, Math.max(wPref, mwPref) + mxOffset);
                p.height = Math.max(10, Math.max(hPref, mhPref) + myOffset);
                int pushY = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0; // "br","bl" là 2 góc đáy
p.x = prefs.getInt(ck+"x",0); p.y = prefs.getInt(ck+"y",0) + pushY;
                updateLayoutIfChanged(corners[i], p);
                if (priMode==0) applyAntiTapjacking(corners[i], p.width, p.height);
            }
        }
// CODE MỚI — thêm ngay trước dấu } đóng hàm:
if (panelEngine != null) panelEngine.rebuildAll();

        // [FIX BUG LOGIC] Luôn đồng bộ cả Homacc khi có lệnh cập nhật hiển thị chung, 
        // phòng trường hợp trạng thái Lock thay đổi khiến Homacc cần được ẩn/hiện.
        updateHomaccLive();
    }
private static final int MAX_TRIGGER_DEPTH = 3;
    private static final String[] GESTURE_SUFFIXES = {
        "_up_hold","_down_hold","_left_hold","_right_hold","_diag_hold",
        "_dtap","_long","_diag","_up","_down","_left","_right","_tap"
    };

    private String stripGestureSuffix(String key) {
        for (String suf : GESTURE_SUFFIXES) {
            if (key.endsWith(suf)) return key.substring(0, key.length() - suf.length());
        }
        return key;
    }

    private void handleAction(String key) { 
        handleAction(key, 0, true); 
    }

    private void handleAction(String key, int depth, boolean applyVibAnim) {
        String action = prefs.getString(key, "NONE");
        boolean isOn = prefs.getBoolean(key + "_on", true);
        if (action.equals("NONE") || !isOn) return;

        // [FIX KHỰNG HÌNH] Anima dùng LAYER_TYPE_SOFTWARE (bắt buộc cho BlurMaskFilter)
        // -> vẽ CPU toàn màn hình, có thể mất vài chục ms. Nếu gọi playAnim() TRƯỚC
        // khi xử lý TRIGGER_*, lượt vẽ software này chiếm main thread đúng lúc
        // Runnable bắn touch giả lập (đã lên lịch qua postDelayed trong
        // dispatchRealScreenGesture) tới giờ chạy -> touch bị delay theo, gây khựng
        // hẳn 1-2 khung hình. Giải pháp: nếu action có TRIGGER_*, hoãn playAnim()
        // ra SAU khi việc bắn touch đã chắc chắn được lên lịch xong (không phải chạy
        // xong, chỉ cần lên lịch xong là đủ tách rời 2 luồng công việc), để traversal
        // vẽ nặng của Anima không còn chen ngang & chặn Runnable touch nữa.
        boolean hasTriggerAction = action.contains("TRIGGER_");
        if (applyVibAnim) {
            if (prefs.getBoolean(key + "_vib", true)) doVibrate(prefs.getInt("vib_dur", 30));
            if (prefs.getBoolean(key + "_anim", true)) {
                if (hasTriggerAction) {
                    int animDelay = prefs.getInt("sim_gesture_delay", 10) + 25;
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(this::playAnim, animDelay);
                } else {
                    playAnim();
                }
            }
        }

        String[] acts = action.split(",");
        for (String a : acts) {
            String at = a.trim();
            if (at.equals("TRIGGER_ACC_MENU_2F")) {
                dispatchTwoFingerAccMenuGesture();
            } else if (at.startsWith("TRIGGER_")) {
                // [FIX QUAN TRỌNG] Bắn trực tiếp tọa độ vuốt xuống hệ thống, chấm dứt đệ quy!
                dispatchRealScreenGesture(at);
            } else if (at.equals("HIDE_SOME_OVERLAY")) {
                hideSomeOverlay(key);
            } else if (at.equals("SHOW_ALL_OVERLAY")) {
                showAllOverlay(key);
            } else if (at.equals("RUN_SHORTCUT")) {
                String scId = prefs.getString(key + "_shortcut_id", "");
                if (!scId.isEmpty()) {
                    try {
                        String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                        if (!uri.isEmpty()) {
                            Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                            scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(scIntent);
                        }
                    } catch (Exception ignored) {}
                }
            } else if (at.equals("LAUNCH_APP")) {
                String pkg = prefs.getString(key + "_launch_pkg", "");
                if (!pkg.isEmpty()) {
                    try {
                        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (li != null) { 
                            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                            startActivity(li); 
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                exec(at);
            }
        }
    }
    private class SidebarTouchListener implements View.OnTouchListener {
        private String prefKeyBase;
        private View myView;
        private float sx, sy, lastX, lastY;
        private long st;
        private boolean longFired = false;
        private final Handler lpHandler = new Handler(android.os.Looper.getMainLooper());
private void checkAndYieldOS(String actionKey) {
            if (prefs.getBoolean(actionKey + "_os", false)) {
                try {
                    // [FIX] KHÔNG ẩn View đồng bộ ngay trong lúc đang xử lý ACTION_UP
                    // của CHÍNH View này — làm vậy buộc hệ thống đồng bộ lại
                    // WindowManager/InputDispatcher ngay giữa chừng (cùng gốc bug với
                    // updateViewLayout() vô điều kiện từng làm hỏng bộ đếm long-press,
                    // xem updateLayoutIfChanged()), gây khựng hẳn 1 nhịp TRƯỚC khi
                    // TRIGGER_* kịp bắn cử chỉ thật xuống OS. Dời việc ẩn sang đúng
                    // mốc VSYNC kế tiếp bằng postOnAnimation() thay vì Handler.post()
                    // thường (tránh xếp hàng chung Looper với holdCheckRunnable/anim
                    // callback — đây là lý do bản post() cũ từng bị giật) — vẫn xảy ra
                    // gần như tức thời (≤1 frame, ~16ms), không còn chặn dispatch chạm
                    // hiện tại nên không còn giật hình dù trùng lúc TRIGGER_* đang chạy.
                    String hideKey = prefKeyBase + "_manual_hide";
                    myView.postOnAnimation(() -> {
                        myView.setVisibility(View.GONE);
                        prefs.edit().putBoolean(hideKey, true).apply();
                    });

                    lpHandler.postDelayed(() -> {
                        myView.setVisibility(View.VISIBLE);
                        prefs.edit().putBoolean(hideKey, false).apply();
                    }, prefs.getInt("os_yield_dur", 3000));
                } catch (Exception ignored) {}
            }
        }
        private long lastTapUpTime = 0;
        private float lastTapUpX = -1f, lastTapUpY = -1f; // [MỚI] Tọa độ của cú chạm trước
        private static final long DTAP_WINDOW_MS = 280;
        private static final float DTAP_MAX_DIST_PX = 40f; // [SIẾT CHẶT] Giảm xuống 40px
        private static final float SWIPE_CANCEL_SLOP_PX = 60f;
        private static final float COMBO_THRESHOLD_PX = 130f;
        private static final float COMBO_RETURN_SLOP_PX = 35f; // [FIX] Ngưỡng "quay đầu" riêng cho combo, không dùng chung SWIPE_CANCEL_SLOP_PX
        private Runnable pendingTapRunnable = null;
        private boolean multiTouchCanceled = false; // [MỚI] Cờ kiểm soát cảm ứng đa điểm
        public SidebarTouchListener(String keyBase, View v) {
            this.prefKeyBase = keyBase;
            this.myView = v;
        }
        // [SỬA LỖI TOẠ ĐỘ] Lấy tọa độ tuyệt đối trực tiếp từ màn hình để đo khoảng cách chuẩn xác 100%
        private float getFixedX(MotionEvent e) { return e.getRawX(); }
        private float getFixedY(MotionEvent e) { return e.getRawY(); }

        private float[] computeJumpDirForTap() {
            float dxDir = 0f, dyDir = 0f;
            if (myView instanceof CornerView) {
                int idx = ((CornerView) myView).getCornerType();
                dxDir = (idx == 0 || idx == 2) ? -1f : 1f;
                dyDir = (idx == 0 || idx == 1) ? -1f : 1f;
            } else {
                if (prefKeyBase.contains("_r_") || prefKeyBase.endsWith("_r")) dxDir = -1f;
                else if (prefKeyBase.contains("_l_") || prefKeyBase.endsWith("_l")) dxDir = 1f;
            }
            int mode = prefs.getInt(prefKeyBase + "_jumpdir", 0);
            switch (mode) {
                case 1: return new float[]{dxDir, -1f}; case 2: return new float[]{dxDir, 1f};
                case 3: return new float[]{0f, -1f}; case 4: return new float[]{0f, 1f};
                case 5: return new float[]{-1f, 0f}; case 6: return new float[]{1f, 0f};
                default: return new float[]{dxDir, dyDir};
            }
        }

        private final Runnable holdCheckRunnable = () -> {
            float cdx = lastX - sx, cdy = lastY - sy;
            if (Math.abs(cdx) > SWIPE_CANCEL_SLOP_PX || Math.abs(cdy) > SWIPE_CANCEL_SLOP_PX) {
                // [SWIPE + HOLD CŨ] Đã vuốt ra xa rồi đứng im
                longFired = true; 
                String actionName;
                if (myView instanceof CornerView && Math.abs(cdx) > 40 && Math.abs(cdy) > 40) actionName = "diag_hold";
                else {
                    if (Math.abs(cdx) > Math.abs(cdy)) actionName = cdx > 0 ? "right_hold" : "left_hold";
                    else actionName = cdy > 0 ? "down_hold" : "up_hold";
                }
                handleAction(prefKeyBase + "_" + actionName);
                checkAndYieldOS(prefKeyBase + "_" + actionName); // THÊM DÒNG NÀY
                if (rippleView != null) {
                    float swipeMag = (float) Math.sqrt(cdx * cdx + cdy * cdy);
                    rippleView.jumpIcon(sx, sy, actionName, Color.argb(180, 96, 125, 139), cdx/swipeMag, cdy/swipeMag);
                }
                        } else {
                // [GÀI SỐ MỚI] Tay đứng im tại chỗ -> Rung báo hiệu vào trạng thái "Giữ + Vuốt"
                isHolding = true;
                holdAnchorX = lastX; holdAnchorY = lastY; // [FIX] chốt mốc TẠI ĐÂY, không tính lẫn độ trôi trước đó
                doVibrate(30);
            }
        };
private boolean isHolding = false;
private float holdAnchorX = 0f, holdAnchorY = 0f; // [FIX] mốc đo swipe SAU khi giữ, tách khỏi điểm chạm ban đầu
private float minDx = 0f, maxDx = 0f, minDy = 0f, maxDy = 0f;
                @Override public boolean onTouch(View v, MotionEvent e) {
            if (isDispatchingSyntheticGesture) return false;
            if (myView instanceof CornerView) ((CornerView)myView).triggerFlash();
            else if (myView instanceof BarView) ((BarView)myView).triggerFlash();
            
            // [FIX LỖI GÕ PHÍM 5-7] Sử dụng getActionMasked() để bắt chính xác đa điểm
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    multiTouchCanceled = false;
                    sx = getFixedX(e); sy = getFixedY(e);
                    lastX = sx; lastY = sy;
                    globalTouchStartX = sx; globalTouchStartY = sy; globalTouchEndX = sx; globalTouchEndY = sy;
                    maxDx = 0; minDx = 0; maxDy = 0; minDy = 0;
                    st = System.currentTimeMillis();
                    longFired = false;
                    isHolding = false;
                    
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    if (pendingTapRunnable != null) {
                        lpHandler.removeCallbacks(pendingTapRunnable);
                        pendingTapRunnable = null;
                    }
                                        ensureRippleView();
                    if (rippleView != null) rippleView.showAt(sx, sy);
                    lpHandler.postDelayed(holdCheckRunnable, prefs.getInt("hold_dur", 600));
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    // [BẢO VỆ CHỐNG NHẦM LẪN] Ngón tay thứ 2 chạm vào (vd: gõ phím nhanh) -> hủy ngay mọi cử chỉ đang chờ
                    multiTouchCanceled = true;
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    if (rippleView != null) rippleView.popRipple();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (multiTouchCanceled) return true;
                    lastX = getFixedX(e); lastY = getFixedY(e);
                    globalTouchEndX = lastX; globalTouchEndY = lastY;
                    float cdx = lastX - sx; float cdy = lastY - sy;
                    if (cdx > maxDx) maxDx = cdx; if (cdx < minDx) minDx = cdx;
                    if (cdy > maxDy) maxDy = cdy; if (cdy < minDy) minDy = cdy;
                    if (rippleView != null) rippleView.moveTo(lastX, lastY);
                    return true;
                    
                case MotionEvent.ACTION_CANCEL:
                    if (multiTouchCanceled) return true;
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    isHolding = false;
                    if (rippleView != null) rippleView.popRipple();
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    if (multiTouchCanceled) return true;
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    if (longFired) {
                        if (rippleView != null) rippleView.popRipple();
                        return true;
                    }
                    float finalDx = lastX - sx, finalDy = lastY - sy;
                    float absDx = Math.abs(finalDx), absDy = Math.abs(finalDy);
                    String actionName = "";
                    boolean isDiag = (myView instanceof CornerView && absDx > 40 && absDy > 40);

                     if (isHolding) {
                        // [FIX] Đo từ mốc lúc rung xác nhận (holdAnchor), không phải từ điểm chạm
                        // ban đầu — tránh độ trôi tay tự nhiên trước 600ms triệt tiêu quãng vuốt thật.
                        float holdDx = lastX - holdAnchorX, holdDy = lastY - holdAnchorY;
                        float holdAbsDx = Math.abs(holdDx), holdAbsDy = Math.abs(holdDy);
                        if (holdAbsDx < SWIPE_CANCEL_SLOP_PX && holdAbsDy < SWIPE_CANCEL_SLOP_PX) {
                            actionName = "long";
                        } else {
                            boolean isDiagHold = (myView instanceof CornerView && holdAbsDx > 40 && holdAbsDy > 40);
                            if (isDiagHold) actionName = "diag_hold";
                            else {
                                if (holdAbsDx > holdAbsDy) actionName = holdDx > 0 ? "hold_right" : "hold_left";
                                else actionName = holdDy > 0 ? "hold_down" : "hold_up";
                            }
                        }
                    } else {
                        if (absDx < SWIPE_CANCEL_SLOP_PX && absDy < SWIPE_CANCEL_SLOP_PX) {
                            long now = System.currentTimeMillis();
                            boolean hasDtap = !prefs.getString(prefKeyBase + "_dtap", "NONE").equals("NONE");
                            float[] dirTap = computeJumpDirForTap();
                            
                            if (!hasDtap) {
                                lastTapUpTime = 0; handleAction(prefKeyBase + "_tap");
                                checkAndYieldOS(prefKeyBase + "_tap");
                                if (rippleView != null && prefs.getBoolean(prefKeyBase + "_tap" + "_jump_on", true))
                                    rippleView.jumpIcon(lastX, lastY, "tap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                            } else {

                                long gap = now - lastTapUpTime;
                                float tapDist = 0f;
                                if (lastTapUpTime > 0) {
                                    float dX = sx - lastTapUpX;
                                    float dY = sy - lastTapUpY;
                                    tapDist = (float) Math.sqrt(dX * dX + dY * dY);
                                }

                                if (lastTapUpTime > 0 && gap <= DTAP_WINDOW_MS && tapDist <= DTAP_MAX_DIST_PX) {
                                    if (gap > 40) { 
                                        lastTapUpTime = 0; 
                                        handleAction(prefKeyBase + "_dtap");
                                        checkAndYieldOS(prefKeyBase + "_dtap");
                                        if (rippleView != null && prefs.getBoolean(prefKeyBase + "_dtap" + "_jump_on", true))
                                            rippleView.jumpIcon(lastX, lastY, "dtap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                                    } else {

                                        lastTapUpTime = now; lastTapUpX = sx; lastTapUpY = sy;
                                        final long myUpTs = now;
                                        pendingTapRunnable = () -> {
                                            if (lastTapUpTime == myUpTs) {
                                                lastTapUpTime = 0; handleAction(prefKeyBase + "_tap");
                                                checkAndYieldOS(prefKeyBase + "_tap"); // THÊM DÒNG NÀY Ở VỊ TRÍ 2
                                                if (rippleView != null && prefs.getBoolean(prefKeyBase + "_tap" + "_jump_on", true))
                                                    rippleView.jumpIcon(lastX, lastY, "tap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                                            }
                                        };

                                        lpHandler.postDelayed(pendingTapRunnable, DTAP_WINDOW_MS + 20);
                                    }
                                } else {
                                    if (pendingTapRunnable != null) {
                                        lpHandler.removeCallbacks(pendingTapRunnable);
                                        pendingTapRunnable.run(); 
                                        pendingTapRunnable = null;
                                    }
                                    
                                    lastTapUpTime = now; lastTapUpX = sx; lastTapUpY = sy;
                                    final long myUpTs = now;
                                    pendingTapRunnable = () -> {
                                        if (lastTapUpTime == myUpTs) {
                                            lastTapUpTime = 0; handleAction(prefKeyBase + "_tap");
                                            checkAndYieldOS(prefKeyBase + "_tap"); // THÊM DÒNG NÀY Ở VỊ TRÍ 3
                                            if (rippleView != null && prefs.getBoolean(prefKeyBase + "_tap" + "_jump_on", true))
                                                rippleView.jumpIcon(lastX, lastY, "tap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                                        }
                                    };

                                    lpHandler.postDelayed(pendingTapRunnable, DTAP_WINDOW_MS + 20);
                                }
                            }
                            if (rippleView != null) rippleView.popRipple();
                            return true;
                        } else {
                            if (!isDiag) {
                                if (minDy < -COMBO_THRESHOLD_PX && finalDy > minDy + COMBO_RETURN_SLOP_PX) actionName = "up_down";
                                else if (maxDy > COMBO_THRESHOLD_PX && finalDy < maxDy - COMBO_RETURN_SLOP_PX) actionName = "down_up";
                                else if (minDx < -COMBO_THRESHOLD_PX && finalDx > minDx + COMBO_RETURN_SLOP_PX) actionName = "left_right";
                                else if (maxDx > COMBO_THRESHOLD_PX && finalDx < maxDx - COMBO_RETURN_SLOP_PX) actionName = "right_left";
                                else if (absDx > absDy) actionName = finalDx > 0 ? "right" : "left";
                                else actionName = finalDy > 0 ? "down" : "up";
                            } else {
                                actionName = "diag";
                            }
                        }
                    }

                    if (!actionName.isEmpty()) {
                        handleAction(prefKeyBase + "_" + actionName);
                        checkAndYieldOS(prefKeyBase + "_" + actionName);
                        if (rippleView != null) {
                            rippleView.popRipple();
                            if (prefs.getBoolean(prefKeyBase + "_" + actionName + "_jump_on", true)) {
                                float swipeMag = (float) Math.sqrt(finalDx * finalDx + finalDy * finalDy);
                                float dirX = swipeMag > 0.001f ? finalDx / swipeMag : 0f;
                                float dirY = swipeMag > 0.001f ? finalDy / swipeMag : 0f;
                                rippleView.jumpIcon(lastX, lastY, actionName, Color.argb(200, 255, 255, 255), dirX, dirY);
                            }
                        }
                    }
                    return true;
            }
            return true;
        }
    }
private void drawAccessibleHome() {
    if (isHomaccDrawn) return; // đã vẽ rồi, tránh addView() 2 lần gây crash
    for (int i = 0; i < 12; i++) {
        View bar = new BarView(this);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        try { wm.addView(bar, p); } catch (Exception e) { continue; }
        bar.setOnTouchListener(new SidebarTouchListener("homacc_" + BARS[i], bar));
        accHomeBars[i] = bar;
    }
    for (int i = 0; i < 4; i++) {
        View corner = new CornerView(this, i, "homacc_");
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        try { wm.addView(corner, p); } catch (Exception e) { continue; }
        corner.setOnTouchListener(new SidebarTouchListener("homacc_corner_" + CORNERS[i], corner));
        accHomeCorners[i] = corner;
    }
    isHomaccDrawn = true;
    updateHomaccLive();
}

private void removeAccessibleHome() {
    if (!isHomaccDrawn) return;
    for (int i = 0; i < 12; i++) {
        if (accHomeBars[i] != null) {
            try { wm.removeView(accHomeBars[i]); } catch (Exception ignored) {}
            accHomeBars[i] = null;
        }
    }
    for (int i = 0; i < 4; i++) {
        if (accHomeCorners[i] != null) {
            try { wm.removeView(accHomeCorners[i]); } catch (Exception ignored) {}
            accHomeCorners[i] = null;
        }
    }
    isHomaccDrawn = false;
}

private void updateHomaccLive() {
    if (!isHomaccDrawn) return;
    
    // [FIX BUG LOGIC] Kiểm tra cờ xem trước và trạng thái khóa màn hình.
    // Homacc chỉ được hiện khi: Đang KHÔNG ở màn hình khóa, HOẶC đang bật xem trước Homacc.
    boolean isPreviewHomacc = prefs.getBoolean("preview_homacc", false);
    boolean isLocked = km != null && km.isKeyguardLocked();
    boolean shouldShowHomacc = !isLocked || isPreviewHomacc;

    for (int i = 0; i < 12; i++) {
        View v = accHomeBars[i];
        if (v == null || !(v instanceof BarView)) continue;
        boolean en = prefs.getBoolean("homacc_" + BARS[i] + "_en", false);
        boolean manualHidden = prefs.getBoolean("homacc_" + BARS[i] + "_manual_hide", false);
        
        // Ép thêm điều kiện shouldShowHomacc
        v.setVisibility((en && !manualHidden && shouldShowHomacc) ? View.VISIBLE : View.GONE);
        if (!en || manualHidden || !shouldShowHomacc) continue;
        int alpha = prefs.getInt("homacc_" + BARS[i] + "_alpha", 50);
        int w = prefs.getInt("homacc_" + BARS[i] + "_w", 300);
        int h = prefs.getInt("homacc_" + BARS[i] + "_h", 60);
        int x = prefs.getInt("homacc_" + BARS[i] + "_x", 0);
        int y = prefs.getInt("homacc_" + BARS[i] + "_y", 0);
        int visMode = prefs.getInt("homacc_" + BARS[i] + "_vis_mode", 0);
        int hideDur = prefs.getInt("homacc_bar_hide_dur", 2500);
        ((BarView) v).updateProps(alpha, visMode == 1, hideDur, visMode == 2, prefs.getInt("homacc_bar_radius", 24));
        int iconSize = prefs.getInt("homacc_" + BARS[i] + "_icon_size", prefs.getInt("homacc_bar_icon_size", 40));
        int iconAlpha = prefs.getInt("homacc_" + BARS[i] + "_icon_alpha", prefs.getInt("homacc_bar_icon_alpha", 255));
        ((BarView) v).setIcons(resolveBarIcons(prefs.getString("homacc_" + BARS[i] + "_icons", ""), iconSize), iconAlpha);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        int priMode = prefs.getInt("homacc_" + BARS[i] + "_pri_mode", 0);
        if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        p.flags = baseFlags; p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i];
        updateLayoutIfChanged(v, p);
        if (priMode == 0) applyAntiTapjacking(v, w, h);
    }
    for (int i = 0; i < 4; i++) {
        View v = accHomeCorners[i];
        if (v == null || !(v instanceof CornerView)) continue;
        boolean en = prefs.getBoolean("homacc_corner_" + CORNERS[i] + "_en", false);
        boolean manualHidden = prefs.getBoolean("homacc_corner_" + CORNERS[i] + "_manual_hide", false);
        
        // Ép thêm điều kiện shouldShowHomacc
        v.setVisibility((en && !manualHidden && shouldShowHomacc) ? View.VISIBLE : View.GONE);
        if (!en || manualHidden || !shouldShowHomacc) continue;
        String ck = "homacc_corner_" + CORNERS[i] + "_";
        int moonAlpha = prefs.getInt("homacc_corner_moon_alpha", 100);
        int strokeAlpha = prefs.getInt("homacc_corner_stroke_alpha", 200);
        int hideDelay = prefs.getInt("homacc_corner_hide_dur", 2500);
        int visMode = prefs.getInt(ck + "vis_mode", 0);
        ((CornerView) v).updateProps(prefs.getInt("homacc_corner_thick", 8), moonAlpha, strokeAlpha,
            visMode == 1, hideDelay, visMode == 2);
        int priMode = prefs.getInt(ck + "pri_mode", 0);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        p.flags = baseFlags; p.gravity = C_GRAV[i];
        int wPref = prefs.getInt(ck + "w", 100), hPref = prefs.getInt(ck + "h", 100);
        int mwPref = prefs.getInt(ck + "moon_w", 100), mhPref = prefs.getInt(ck + "moon_h", 100);
        int mxOff = Math.abs(prefs.getInt(ck + "moon_x", 1250) - 1250);
        int myOff = Math.abs(prefs.getInt(ck + "moon_y", 1250) - 1250);
        p.width = Math.max(10, Math.max(wPref, mwPref) + mxOff);
        p.height = Math.max(10, Math.max(hPref, mhPref) + myOff);
        p.x = prefs.getInt(ck + "x", 0); p.y = prefs.getInt(ck + "y", 0);
        updateLayoutIfChanged(v, p);
        if (priMode == 0) applyAntiTapjacking(v, p.width, p.height);
    }
}
// [BẮT BUỘC] AccessibilityService yêu cầu override hàm này
@Override
public void onInterrupt() {}

// ===== CHỈ BÁO GHI ÂM (chấm đỏ + mm:ss) — bản dành cho EdgeBarService =====
private void ensureRecIndicator() {
    if (recIndicatorView != null) return;
    recIndicatorView = new LinearLayout(this);
    recIndicatorView.setOrientation(LinearLayout.HORIZONTAL);
    recIndicatorView.setGravity(Gravity.CENTER_VERTICAL);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.argb(200, 20, 20, 20));
    bg.setCornerRadius(100f);
    recIndicatorView.setBackground(bg);
    int pad = 16;
    recIndicatorView.setPadding(pad*2, pad, pad*2, pad);
    recIndicatorView.setMinimumWidth(prefs.getInt("anim_rec_width", 260));
    recIndicatorView.setMinimumHeight(prefs.getInt("anim_rec_height", 90));

    recIndicatorDot = new View(this);
    GradientDrawable dot = new GradientDrawable();
    dot.setShape(GradientDrawable.OVAL);
    dot.setColor(Color.parseColor("#FF3B30"));
    recIndicatorDot.setBackground(dot);
    int dotSize = Math.round(24 * (prefs.getInt("anim_rec_size", 140) / 140f));
    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
    dotLp.setMargins(0, 0, 16, 0);
    recIndicatorDot.setLayoutParams(dotLp);

    recIndicatorText = new TextView(this);
    recIndicatorText.setTextColor(Color.WHITE);
    recIndicatorText.setTextSize(13 * (prefs.getInt("anim_rec_size", 140) / 140f));
    recIndicatorText.setText("00:00");

    recIndicatorView.addView(recIndicatorDot);
    recIndicatorView.addView(recIndicatorText);

    // Handler phân biệt chạm 1 lần (Pause/Play) và chạm 2 lần (Stop & Mở file)
    final android.os.Handler tapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable singleTapRunnable = () -> {
        // [1 CHẠM]: Tạm dừng ↔ Tiếp tục
        if (VoiceRecorderService.isRunning) {
            Intent p2 = new Intent(this, VoiceRecorderService.class);
            p2.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(p2); else startService(p2);
        } else if (ScreenRecorderService.isRunning) {
            Intent p3 = new Intent(this, ScreenRecorderService.class);
            p3.setAction(ScreenRecorderService.ACTION_PAUSE_TOGGLE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(p3); else startService(p3);
        } else if (recIndicatorTestMode) {
            recIndicatorTestPaused = !recIndicatorTestPaused;
            updateRecIndicator(recIndicatorTestPaused ? "PAUSED" : "RECORDING", 0);
        }
    };

    final long[] lastClickTime = {0};
    recIndicatorView.setOnClickListener(v -> {
        long now = System.currentTimeMillis();
        if (now - lastClickTime[0] < 300) {
            // [DOUBLE TAP]: Dừng hẳn + Rung + Xem/Phát File
            tapHandler.removeCallbacks(singleTapRunnable);
            lastClickTime[0] = 0;
            doVibrate(50); // Rung 50ms báo hiệu
            
            if (VoiceRecorderService.isRunning) {
                Intent s2 = new Intent(this, VoiceRecorderService.class);
                s2.setAction("com.manhmoc.edgebar.VOICEREC_STOP_PLAY");
                startService(s2);
            } else if (ScreenRecorderService.isRunning) {
                Intent s3 = new Intent(this, ScreenRecorderService.class);
                s3.setAction("com.manhmoc.edgebar.SCREENREC_STOP_PLAY");
                startService(s3);
            } else if (recIndicatorTestMode) {
                recIndicatorTestMode = false;
                recIndicatorTestPaused = false;
                updateRecIndicator("STOPPED", 0);
            }
        } else {
            // Đợi xem có cú chạm thứ 2 không, nếu sau 300ms không có thì gọi 1 Chạm
            lastClickTime[0] = now;
            tapHandler.postDelayed(singleTapRunnable, 300);
        }
    });

    // [NHẤN GIỮ]: Chỉ dừng hẳn (không phát file)
    recIndicatorView.setOnLongClickListener(v -> {
        tapHandler.removeCallbacks(singleTapRunnable); // Hủy nếu đang chờ 1 chạm
        lastClickTime[0] = 0;
        doVibrate(35);
        if (VoiceRecorderService.isRunning) {
            Intent s2 = new Intent(this, VoiceRecorderService.class);
            s2.setAction(VoiceRecorderService.ACTION_STOP);
            startService(s2);
        } else if (ScreenRecorderService.isRunning) {
            Intent s3 = new Intent(this, ScreenRecorderService.class);
            s3.setAction(ScreenRecorderService.ACTION_STOP);
            startService(s3);
        } else if (recIndicatorTestMode) {
            recIndicatorTestMode = false;
            recIndicatorTestPaused = false;
            updateRecIndicator("STOPPED", 0);
        }
        return true;
    });
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
    lp.x = prefs.getInt("anim_rec_x", 1000) - 1000;
    lp.y = Math.max(100, prefs.getInt("anim_rec_y", 1000) - 1000);
    try { wm.addView(recIndicatorView, lp); } catch (Exception ignored) {}
    recBlinkAnim = ValueAnimator.ofFloat(1f, 0.25f, 1f);
    recBlinkAnim.setDuration(1000);
    recBlinkAnim.setRepeatCount(ValueAnimator.INFINITE);
    recBlinkAnim.addUpdateListener(a -> { if (recIndicatorDot != null) recIndicatorDot.setAlpha((float) a.getAnimatedValue()); });
}

private void liveUpdateRecIndicatorPosition() {
    if (recIndicatorView == null) return;
    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) recIndicatorView.getLayoutParams();
    lp.x = prefs.getInt("anim_rec_x", 1000) - 1000;
    lp.y = Math.max(100, prefs.getInt("anim_rec_y", 1000) - 1000);
    try { wm.updateViewLayout(recIndicatorView, lp); } catch (Exception ignored) {}
    recIndicatorView.setMinimumWidth(prefs.getInt("anim_rec_width", 260));
    recIndicatorView.setMinimumHeight(prefs.getInt("anim_rec_height", 90));
    float scale = prefs.getInt("anim_rec_size", 140) / 140f;
    int dotSize = Math.round(24 * scale);
    if (recIndicatorDot != null) {
        LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) recIndicatorDot.getLayoutParams();
        dlp.width = dotSize; dlp.height = dotSize;
        recIndicatorDot.setLayoutParams(dlp);
    }
    if (recIndicatorText != null) recIndicatorText.setTextSize(scale * 13);
}

private void updateRecIndicator(String state, long sec) {
    if (state == null || "STOPPED".equals(state)) {
        if (recBlinkAnim != null) recBlinkAnim.cancel();
        if (recIndicatorView != null) { try { wm.removeView(recIndicatorView); } catch (Exception ignored) {} recIndicatorView = null; }
        return;
    }
    ensureRecIndicator();
    recIndicatorText.setText(String.format("%02d:%02d", sec/60, sec%60));
    if ("PAUSED".equals(state)) {
        if (recBlinkAnim != null && recBlinkAnim.isRunning()) recBlinkAnim.cancel();
        if (recIndicatorDot != null) recIndicatorDot.setAlpha(1f);
    } else {
        if (recBlinkAnim != null && !recBlinkAnim.isRunning()) recBlinkAnim.start();
    }
}
} // <-- Dấu ngoặc nhọn kết thúc toàn bộ class EdgeBarService
