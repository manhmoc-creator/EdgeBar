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
// ĐẰNG TRƯỚC (Có thể là các dòng import cuối cùng)
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast; 

public class EdgeBarService extends AccessibilityService {

    // === CHÈN CODE BIẾN TOÀN CỤC CỦA BẠN VÀO ĐÂY ===
    // Động cơ Twin-Engine Trợ năng
private android.view.View[] accHomeBars = new android.view.View[5];
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


    // ĐẰNG SAU (Các biến cũ của EdgeBarService)
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private View kbdSensorView;
    private FlashView fV;
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private boolean isInRecents = false;
    private String lastForegroundPkg = ""; 
    private KeyguardManager km;
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private PanelEngine panelEngine; 
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
    String sig = w + "x" + h;
    if (sig.equals(lastGestureSig.get(v))) return;
    lastGestureSig.put(v, sig);
    try {
        v.setSystemGestureExclusionRects(
            java.util.Collections.singletonList(new android.graphics.Rect(0, 0, w, h)));
    } catch (Exception ignored) {}
}
private final Handler homaccDebounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable homaccDebounceRunnable = null;
private final Handler panelDebounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable panelDebounceRunnable = null;
private static final long PANEL_DEBOUNCE_MS = 120;

private final Handler debounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable debounceRunnable = null;

private boolean lastAccHomeRunningState = false;
private long lastHomaccUpdateMs = 0;

// V19.12.3.6.8 THE ETERNAL EGO — throttle biến event
private long lastEventMs = 0;
private static final long EVENT_THROTTLE_MS = 200;
private String lastEventPkg = "";
private boolean lastIsKbd_cache = false;
private int cachedKbdHeight = 0;
private static final int KBD_HEIGHT_CHANGE_THRESHOLD = 20;
private boolean lastIsBl_cache = false;
// V19.12.3.6.8: Pipeline A riêng cho MorseLock — throttle nhanh hơn SYNC_STATE
private long lastMorseLockCheckMs = 0;
private static final long MORSE_LOCK_CHECK_THROTTLE = 250;
private long lastUninstallCheckMs = 0;
private static final long UNINSTALL_CHECK_THROTTLE = 400;
private long lastAdminRevokeCheckMs = 0;
private static final long ADMIN_REVOKE_CHECK_THROTTLE = 400;
// V19.12.3.6.6 — Whitelist key của EdgeBar, chặn key lạ của Zalo/Messenger
private static final java.util.Set<String> EB_KEY_PREFIXES =
    new java.util.HashSet<>(java.util.Arrays.asList(
        "lock_","home_","morse_","homacc_","anim_","vib_","hold_",
        "blacklist","locklist","avoid_kbd","shortcut_","preview_",
        "lang_","ytdl_","intent_","tile_","macro_",
        // [FIX] Khóa thật của Panel là "pack_panel_<id>_..." — không phải "panel".
        // Thiếu tiền tố đúng khiến isOurKey() chặn TOÀN BỘ thay đổi live của Panel
        // (Preview Handle, Enable, slider...) ngay từ vòng lọc whitelist.
        "pack_panel_",
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
    // Cho phép cập nhật live nếu ĐANG preview (Frontier) HOẶC Homacc thật đang chạy
    if ((!AccessibleHomeService.isRunning && !previewOn) || !isHomaccDrawn) return;
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
    // TẦNG 4: lock bars → debounce 400ms như cũ
    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    debounceRunnable = () -> updateVisibility();
    debounceHandler.postDelayed(debounceRunnable, LOCK_DEBOUNCE_MS);
};
   // SAU:
private BroadcastReceiver stateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        if ("com.manhmoc.edgebar.TEST_ANIM".equals(act)) {
            playAnim();
        } else if (Intent.ACTION_SCREEN_OFF.equals(act)) {
    if (isHomaccDrawn) removeAccessibleHome();
    // Cảm biến chắc chắn không khả dụng khi màn tắt — huỷ đăng ký, đỡ giữ callback vô ích
    if (fpRegistered && fpController != null && fpCallback != null) {
        try { fpController.unregisterFingerprintGestureCallback(fpCallback); } catch (Exception e) {}
        fpRegistered = false;
  }
} else if (Intent.ACTION_USER_PRESENT.equals(act)) {
    if (AccessibleHomeService.isRunning) drawAccessibleHome();
    refreshFingerprintRegistration(); // ← THÊM: thử đăng ký lại đúng lúc cảm biến rảnh nhất
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
    for (int j=0;j<5;j++) if (bars[j]!=null) bars[j].setVisibility(View.GONE);
    for (int j=0;j<4;j++) if (corners[j]!=null) corners[j].setVisibility(View.GONE);
    for (int j=0;j<5;j++) if (accHomeBars[j]!=null) accHomeBars[j].setVisibility(View.GONE);
    for (int j=0;j<4;j++) if (accHomeCorners[j]!=null) accHomeCorners[j].setVisibility(View.GONE);
} else if ("com.manhmoc.edgebar.RESUME_WM_OPS".equals(act)) {
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
    private class FlashView extends View {
    private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE";
    int aStyle = 0; private float phaseFraction = 0f;
    private int effW = 0, effH = 0; // [FIX] cache 2 slider anim_w/anim_h trước đây bị bỏ quên
    public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p);
        updateStyle(); }

    public void updateStyle() {
        p.setAlpha(prefs.getInt("anim_alpha", 255));
        p.setStrokeWidth(prefs.getInt("anim_thick", 12));
        radius = prefs.getInt("anim_rad", 40);
        cTheme = prefs.getString("anim_color", "WHITE");
        aStyle = prefs.getInt("anim_style", 0);
        // [FIX] Trước đây 2 dòng này không tồn tại -> slider "Chiều ngang/dọc
        // Hiệu ứng" ghi vào prefs nhưng chưa bao giờ được đọc lại ở đâu cả.
        effW = prefs.getInt("anim_w", 0);
        effH = prefs.getInt("anim_h", 0);
        if(getWidth() > 0) applyGradient(getWidth(), getHeight());
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h);
    }

    private void applyGradient(int w, int h) {  /* giống các bản trước */ 
            int[] cArr; switch(cTheme) {
                case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#FF00FF")}; break;
                case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700"), Color.parseColor("#8A2BE2")}; break;
                case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00"), Color.parseColor("#FF4500")}; break;
                case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF"), Color.parseColor("#00BFFF")}; break;
                case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000"), Color.parseColor("#00FF00")}; break;
                case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00"), Color.parseColor("#FF1493")}; break;
                case "GOOGLE": cArr=new int[]{Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853"), Color.parseColor("#4285F4"), Color.parseColor("#EA4335")}; break;
                case "AURORA": cArr=new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"), Color.parseColor("#FF4081")}; break;
                case "ABYSS": cArr=new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#1DE9B6"), Color.parseColor("#2979FF")}; break;
                case "COSMIC": cArr=new int[]{Color.parseColor("#4A148C"), Color.parseColor("#E91E63"), Color.parseColor("#FFD700")}; break;
                case "FOREST": cArr=new int[]{Color.parseColor("#1B5E20"), Color.parseColor("#4CAF50"), Color.parseColor("#FFEB3B")}; break;
                case "FLAME": cArr=new int[]{Color.parseColor("#B71C1C"), Color.parseColor("#FF9800"), Color.parseColor("#FFEB3B")}; break;
                case "MIDNIGHT": cArr=new int[]{Color.parseColor("#1A237E"), Color.parseColor("#7B1FA2"), Color.parseColor("#03A9F4")}; break;
                case "TROPICAL": cArr=new int[]{Color.parseColor("#00695C"), Color.parseColor("#8BC34A"), Color.parseColor("#FF9800")}; break;
                case "CANDY": cArr=new int[]{Color.parseColor("#F06292"), Color.parseColor("#4DD0E1"), Color.parseColor("#FFF176")}; break;
                default: cArr=new int[]{Color.WHITE, Color.WHITE}; break;
            }
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.MIRROR)); p.setShadowLayer(15f, 0, 0, cArr[0]);
        }
        public void setPhase(float fraction) { this.phaseFraction = fraction; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        float drawW = getWidth(); float drawH = getHeight();
        if(drawW <= 0 || drawH <= 0) return;
        float off = p.getStrokeWidth()/2;
        // [FIX] Áp dụng anim_w/anim_h làm khoảng thu vào từ mép màn hình.
        // Giá trị 0 = giữ nguyên Full màn hình (đúng như nhãn "(0=Full)").
        // Có chặn Math.min để không bao giờ thu quá nửa màn hình (tránh khung
        // bị lật ngược nếu người dùng kéo slider lên mức quá lớn).
        float insetW = effW > 0 ? Math.min(effW, drawW/2f - 1) : 0f;
        float insetH = effH > 0 ? Math.min(effH, drawH/2f - 1) : 0f;
        float left = off + insetW; float top = off + insetH;
        float right = drawW - off - insetW; float bottom = drawH - off - insetH;
        p.setStrokeCap(Paint.Cap.ROUND);
        if(aStyle > 0) {
            float perim = 2 * ((right - left) + (bottom - top)); // [FIX] tính chu vi theo khung ĐÃ thu, không theo full màn hình nữa, để hiệu ứng chạy viền (Nhấp Nháy/Tia sáng) bám sát đúng khung mới
            float currentPhase = -perim * phaseFraction;
            if (aStyle == 1) p.setPathEffect(new DashPathEffect(new float[]{perim/4f, 3*perim/4f}, currentPhase));
            else if (aStyle == 2) p.setPathEffect(new DashPathEffect(new float[]{perim/8f, 3*perim/8f}, currentPhase));
            else if (aStyle == 3) p.setPathEffect(new DashPathEffect(new float[]{perim/12f, 3*perim/12f}, currentPhase));
        } else { p.setPathEffect(null); }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, p);
    }
}
    private class CornerView extends View {
        private Paint pFill, pStroke; private int type; private String prefix = "lock_";
        private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
        private boolean isInv = false;

        public CornerView(Context c, int type) { this(c, type, "lock_"); }
    public CornerView(Context c, int type, String prefix) { super(c); this.type = type; this.prefix = prefix; pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND); }

    public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) {
        pStroke.setStrokeWidth(thick);
        this.baseMoonAlpha = moonAlpha;
        this.baseStrokeAlpha = strokeAlpha;
        this.isAutoHiding = autoHide;
        this.hideDelay = delay;
        this.isInv = inv;
        // KHÔNG gọi triggerFlash() ở đây — chỉ gọi khi user thực sự CHẠM
if (inv) {
    pFill.setAlpha(0);
    pStroke.setAlpha(0);
} else if (!autoHide) {
    pFill.setColor(Color.argb(moonAlpha, 96, 125, 139));
    pStroke.setAlpha(strokeAlpha);
} else {
    pFill.setColor(Color.argb(0, 96, 125, 139));
    pStroke.setAlpha(0);
  }
  invalidate();
}
        public void triggerFlash() { if(!isAutoHiding || isInv) return; autoHideHandler.removeCallbacksAndMessages(null); pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha+50), 96,125,139)); pStroke.setAlpha(Math.min(255, baseStrokeAlpha+50)); invalidate(); autoHideHandler.postDelayed(() -> { ValueAnimator a = ValueAnimator.ofFloat(1f,0f); a.setDuration(1500); a.addUpdateListener(anim -> { float val = (float)anim.getAnimatedValue(); pFill.setColor(Color.argb((int)(baseMoonAlpha*val), 96,125,139)); pStroke.setAlpha((int)(baseStrokeAlpha*val)); invalidate(); }); a.start(); }, hideDelay); }

        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas);
            float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(), pad = thick/2;
            String ck = prefix + "corner_" + CORNERS[type] + "_";
            int shapeMode = prefs.getInt(ck+"shape", 0);
            float sRad = prefs.getInt(ck+"rad", 80) / 1000f; float mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
            float sw = prefs.getInt(ck+"w", 100), sh = prefs.getInt(ck+"h", 100);
            float mw = prefs.getInt(ck+"moon_w", 100), mh = prefs.getInt(ck+"moon_h", 100);

            Path moonPath = new Path(), strokePath = new Path();
            float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
            float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;

            if (type == 0) { // BR
                sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad;
                sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh;
                mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
            } else if (type == 1) { // BL
                sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad;
                sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh;
                mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
            } else if (type == 2) { // TR
                sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad;
                sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh;
                mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
            } else { // TL
                sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad;
                sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=0; mTipX=mw; mTipY=mh;
                mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
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
        // CODE MỚI — thêm ngay dưới dòng addAction cuối:
filter.addAction("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
filter.addAction("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED");
filter.addAction("com.manhmoc.edgebar.PANEL_TEST_TOGGLE");
filter.addAction("com.manhmoc.edgebar.PAUSE_WM_OPS");
filter.addAction("com.manhmoc.edgebar.RESUME_WM_OPS");
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
                    for (int i=0;i<5;i++) if (accHomeBars[i]!=null) accHomeBars[i].setVisibility(View.GONE);
                    for (int i=0;i<4;i++) if (accHomeCorners[i]!=null) accHomeCorners[i].setVisibility(View.GONE);
                } else if ("com.manhmoc.edgebar.ACC_HOME_WAKE".equals(act)) {
                    // [MỤC 5] Thức dậy: vẽ lại nếu view chưa tồn tại, hoặc hiện lại view cũ
                    if (accHomeBars[0] == null && accHomeCorners[0] == null) drawAccessibleHome();
                    else {
                        SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
                        for (int i=0;i<5;i++) if (accHomeBars[i]!=null && p.getBoolean("homacc_"+BARS[i]+"_en", false)) accHomeBars[i].setVisibility(View.VISIBLE);
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
// [MỚI] Không tin bất kỳ trạng thái nào có sẵn — mỗi lần Service này
// thật sự khởi động (dù do mở app, reboot, hay Android rebind sau cập
// nhật), luôn đọc lại 100% từ SharedPreferences hiện tại làm chuẩn.
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
    String locklist = prefs.getString("locklist", "");
    if (!locklist.isEmpty()) {
        long nowA = System.currentTimeMillis();
        if (nowA - lastMorseLockCheckMs >= MORSE_LOCK_CHECK_THROTTLE) {
            lastMorseLockCheckMs = nowA;
            String foregroundFromWindows = getForegroundPackageFromWindows();
            if (foregroundFromWindows != null && !foregroundFromWindows.isEmpty()) {
                checkAndEngageMorseLock(foregroundFromWindows, locklist);
            }
        }
    }
    if (pName.contains("packageinstaller") || pName.contains("vending")
        || pName.contains("permissioncontroller") || pName.equals("android")) {
        long nowU = System.currentTimeMillis();
        if (nowU - lastUninstallCheckMs >= UNINSTALL_CHECK_THROTTLE) {
            lastUninstallCheckMs = nowU;
            if (uninstallTargetsSelf()) {
                sendBroadcast(new Intent("com.manhmoc.edgebar.UNINSTALL_DETECTED"));
            }
        }
    }
// [MỚI] Phát hiện màn hình "Vô hiệu hoá quyền Admin thiết bị" trong Settings.
// Throttle riêng 400ms — cùng cơ chế Zero-overhead như Uninstall check.
if (pName.contains("settings")) {
    long nowR = System.currentTimeMillis();
    if (nowR - lastAdminRevokeCheckMs >= ADMIN_REVOKE_CHECK_THROTTLE) {
        lastAdminRevokeCheckMs = nowR;
        if (adminRevokeTargetsSelf()) {
            sendBroadcast(new Intent("com.manhmoc.edgebar.ADMIN_REVOKE_DETECTED"));
        }
    }
}
    long nowMs = System.currentTimeMillis();
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

if (!stateChanged) return;
    lastEventPkg = pName;
    lastIsKbd_cache = newIsKbd;
    lastIsBl_cache = newIsBl;

    updateVisibility();
    // [FIX BUG 2] Trước đây chỉ cần pName chứa "systemui" (kéo thanh thông báo, mở
// Volume panel, chụp màn hình...) là đã bị hiểu nhầm thành Recents → MorseLock che
// nhầm dù không hề mở đa nhiệm. Giờ bắt buộc khớp đúng tên class Recents/Overview.
boolean nowInRecents = cName.contains("RecentsActivity") || cName.contains("RecentTasksActivity")
        || cName.contains("Recents") || cName.contains("Overview")
        || (pName.contains("quickstep") && cName.toLowerCase().contains("recent"));
    if (nowInRecents && !isInRecents) {
        isInRecents = true;
        Intent coverIntent = new Intent("com.manhmoc.edgebar.MORSE_OS_RECENTS_SHOW");
        coverIntent.putExtra("last_pkg", lastForegroundPkg);
        sendBroadcast(coverIntent);
    } else if (!nowInRecents && isInRecents) {
        isInRecents = false;
        sendBroadcast(new Intent("com.manhmoc.edgebar.MORSE_OS_RECENTS_HIDE"));
    }
    if (!nowInRecents && !pName.isEmpty() && !isKbd) lastForegroundPkg = pName;

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
private boolean uninstallTargetsSelf() {
    android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return false;
    boolean hasAppName = containsText(root, getOwnAppLabel(), 0);
    boolean hasUninstallKeyword = hasAppName && (
        containsText(root, "Uninstall", 0) || containsText(root, "Gỡ cài đặt", 0)
        || containsText(root, "gỡ cài đặt", 0) || containsText(root, "GỠ CÀI ĐẶT", 0)
    );
    root.recycle();
    return hasUninstallKeyword;
}
/** [MỚI] Tái sử dụng containsText() đã có sẵn cho Uninstall — không cấp phát thêm gì,
 *  chỉ duyệt lại đúng cây node hiện có, đã throttle 400ms nên không tốn CPU liên tục. */
private boolean adminRevokeTargetsSelf() {
    android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return false;
    boolean hasAppName = containsText(root, getOwnAppLabel(), 0);
    boolean hasRevokeKeyword = hasAppName && (
        containsText(root, "Deactivate", 0) || containsText(root, "deactivate", 0)
        || containsText(root, "Vô hiệu hoá", 0) || containsText(root, "Vô hiệu hóa", 0)
        || containsText(root, "device admin app", 0)
    );
    root.recycle();
    return hasRevokeKeyword;
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
private void checkAndEngageMorseLock(String pkg, String locklist) {
    // [FIX #3] Tôn trọng QS Tile tuyệt đối — nếu MorseLock đang TẮT, không bao giờ
    // được phép tự động engage cho bất kỳ app nào trong Locklist.
    if (!prefs.getBoolean("morse_mode_en", false)) return;
    // Throttle đã xử lý tại nơi gọi (trước getWindows()), hàm này chỉ còn
    // logic đối chiếu package.
    if (pkg.contains("launcher") || pkg.contains("nexuslauncher")
            || pkg.contains("quickstep") || pkg.contains("systemui")
            || pkg.equals("android") || pkg.contains("inputmethod")
            || pkg.contains("recents") || pkg.contains("packageinstaller")) {
        return;
    }
    for (String locked : locklist.split(",")) {
        if (locked.trim().equals(pkg)) {
            Intent lockIntent = new Intent("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
            lockIntent.putExtra("pkg", pkg);
            lockIntent.putExtra("from_windows_api", true);
            sendBroadcast(lockIntent);
            return;
        }
    }
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
/**
 * Tự tắt Accessibility của EdgeBar rồi chuyển hẳn sang Homeb (chỉ cần
 * SYSTEM_ALERT_WINDOW) để bar không "để lộ" Accessibility đang bật trong
 * app nhạy cảm. LƯU Ý: BACK/RECENTS/SCREENSHOT... sẽ ngừng hoạt động vì
 * cần Accessibility, không có API thay thế công khai — HOME vẫn chạy
 * (xem case "HOME" mới trong HomescreenService.exec()). Việc tự bật lại
 * Accessibility sau khi rời app blacklist sẽ làm ở lần sau.
 */
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
    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try {
            switch (a) {
                    case "TOGGLE_MORSE":
                    Intent m = new Intent("com.manhmoc.edgebar.TOGGLE_MORSE");
                    sendBroadcast(m); break;
                case "YTDL_DOWNLOAD":
try{
    // [FIX] Ưu tiên đọc đúng nội dung người dùng đã gõ/lưu trong ô nhập YTDLnis
    // thay vì chỉ trông chờ Clipboard — hỗ trợ cả link lẫn tên bài hát dạng chữ.
    SharedPreferences p2 = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String query = p2.getString("ytdl_last_link", "").trim();
    if (query.isEmpty()) {
        // Không có gì đã lưu -> fallback về Clipboard như hành vi cũ
        android.content.ClipboardManager cb = (android.content.ClipboardManager)
            getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence txt = cb.getPrimaryClip().getItemAt(0).getText();
            if (txt != null) query = txt.toString().trim();
        }
    }
    if (!query.isEmpty()) {
        Intent y = new Intent(Intent.ACTION_SEND);
        y.setType("text/plain");
        y.putExtra(Intent.EXTRA_TEXT, query);
        y.setPackage("com.deniscerri.ytdl");
        y.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(y);
    }
} catch (Exception e) {} break;
                case "BACK": performGlobalAction(GLOBAL_ACTION_BACK); break;
                case "HOME": performGlobalAction(GLOBAL_ACTION_HOME); break;
                case "RECENTS": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case "SCREEN_OFF":
performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
case "SPLIT_SCREEN":
performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN); break;
case "SCREEN_ON":
// Thuật toán WakeLock giải phóng RAM nhanh
                    // Thuật toán WakeLock giải phóng RAM nhanh
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null && !pm.isInteractive()) {
                        android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, 
                            "EdgeBar:ScreenOn");
                        wl.acquire(2000); // Chỉ giữ CPU trong 3 giây để bật màn, sau đó tự nhả RAM
                    }
                    break;
                case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
                case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break;
                case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
                case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break;
                case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break;
                case "VOLUME": ((AudioManager) getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break;
                case "VOICE_RECORD": {
                    Intent recIntent = new Intent(this, VoiceRecorderService.class);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(recIntent);
                    else startService(recIntent);
                    break;
                }
                case "TOGGLE_OVERLAY": {
                    // [MỚI] Bật/tắt Trợ năng (Homeb ⇄ Overlay Trợ năng) ngay từ Action —
                    // tái sử dụng NGUYÊN VẸN pipeline PAUSE_WM_OPS -> ghi Settings.Secure ->
                    // RESUME_WM_OPS đã có sẵn và ổn định trong ToggleReceiver. Zero code
                    // trùng lặp, Zero RAM/CPU thêm ngoài 1 Intent nội bộ nhỏ cùng process.
                    sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC"));
                    break;
                }
                // THÊM case mới trong switch(a) của cả 2 file:
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
    private void playAnim() {
        WindowManager.LayoutParams fp = (WindowManager.LayoutParams) fV.getLayoutParams();
        fp.width = WindowManager.LayoutParams.MATCH_PARENT; fp.height = WindowManager.LayoutParams.MATCH_PARENT;
        wm.updateViewLayout(fV, fp);
        fV.setVisibility(View.VISIBLE);
        fV.post(() -> {
            int style = prefs.getInt("anim_style", 0);
            int dur = prefs.getInt("anim_dur", 1500);
            ValueAnimator anim;
            if (style == 0) {
                anim = ValueAnimator.ofFloat(0f,1f,0f);
                anim.addUpdateListener(a -> fV.setAlpha((float)a.getAnimatedValue()));
            } else {
                fV.setAlpha(1f);
                anim = ValueAnimator.ofFloat(0f,1f);
                anim.addUpdateListener(a -> fV.setPhase((float)a.getAnimatedValue()));
            }
            anim.setDuration(dur);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    fV.setAlpha(0f);
                    fV.setVisibility(View.GONE);
                    fp.width = 0; fp.height = 0;
                    wm.updateViewLayout(fV, fp);
                }
            });
            anim.start();
        });
    }

    private void handleAction(String key) {
        String action = prefs.getString(key, "NONE");
        boolean isOn = prefs.getBoolean(key + "_on", true);
        if (!action.equals("NONE") && isOn) {
            if (prefs.getBoolean(key+"_vib", true)) doVibrate(prefs.getInt("vib_dur",30));
            if (prefs.getBoolean(key+"_anim", true)) playAnim();
            String[] acts = action.split(",");
         for (String a : acts) {
    if (a.trim().equals("RUN_SHORTCUT")) {
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
} else if (a.trim().equals("LAUNCH_APP")) {
        String pkg = prefs.getString(key + "_launch_pkg", "");
        if (!pkg.isEmpty()) {
            try {
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
            } catch (Exception ignored) {}
        }
    } else exec(a.trim());
}

        }
    }
    private void doVibrate(int dur) { if (dur<=0) return; try { if (Build.VERSION.SDK_INT>=26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }
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
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(0,0, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { wm.addView(fV, fp); } catch(Exception e){}

        // [FIX PUSH-AWAY BÀN PHÍM v2] fV có kích thước 0x0 ngoài lúc chạy hiệu ứng
        // Flash -> không phủ vùng bàn phím -> hệ thống không đảm bảo gửi insets update.
        // Tạo 1 View cảm biến RIÊNG, luôn giữ MATCH_PARENT, trong suốt (alpha=0),
        // không nhận cảm ứng — chỉ để đo insets bàn phím một cách đáng tin cậy.
        kbdSensorView = new View(this);
        kbdSensorView.setAlpha(0f);
        // [FIX PUSH-AWAY BÀN PHÍM] Đổi TYPE_ACCESSIBILITY_OVERLAY -> TYPE_APPLICATION_OVERLAY
        // và bỏ FLAG_LAYOUT_NO_LIMITS. Cửa sổ accessibility-overlay được OS coi là luôn nổi
        // trên IME nên hệ thống không buồn tính lại/gửi WindowInsets.Type.ime() cho nó —
        // đây là lý do listener bên dưới gần như không bao giờ được gọi. TYPE_APPLICATION_OVERLAY
        // (chỉ cần quyền SYSTEM_ALERT_WINDOW đã có sẵn) tham gia đúng vòng tính insets chuẩn.
        // Zero thêm chi phí pin/RAM: vẫn 1 View trong suốt, 1 listener, không thêm timer nào.
        WindowManager.LayoutParams kp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        try { wm.addView(kbdSensorView, kp); } catch (Exception e) {}
        if (Build.VERSION.SDK_INT >= 30) {
            kbdSensorView.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeH = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                if (Math.abs(imeH - cachedKbdHeight) >= KBD_HEIGHT_CHANGE_THRESHOLD) {
                    cachedKbdHeight = imeH;
                    isKbd = imeH > 0;
                    updateVisibility();
                    Intent syncIntent = new Intent("com.manhmoc.edgebar.SYNC_STATE");
                    syncIntent.putExtra("isKbd", isKbd);
                    syncIntent.putExtra("isBl", isBl);
                    syncIntent.putExtra("kbd_height", cachedKbdHeight);
                    sendBroadcast(syncIntent);
                }
                return insets;
            });
        }
        for (int i=0;i<5;i++) {
            bars[i] = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1,1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch(Exception e){}
            bars[i].setOnTouchListener(new SidebarTouchListener("lock_"+BARS[i], null));
        }
        for (int i=0;i<4;i++) {
            corners[i] = new CornerView(this,i);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1,1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT);
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
for (int i=0;i<5;i++) {
    if (bars[i]==null) continue;
    boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", false);
    bars[i].setVisibility((en && isLocked && !hide) ? View.VISIBLE : View.GONE);
    if (en && isLocked) {
                int alpha = prefs.getInt("lock_"+BARS[i]+"_alpha",50);
                int w = prefs.getInt("lock_"+BARS[i]+"_w",300);
                int h = prefs.getInt("lock_"+BARS[i]+"_h",60);
                int x = prefs.getInt("lock_"+BARS[i]+"_x",0);
                int y = prefs.getInt("lock_"+BARS[i]+"_y",0);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(Color.argb(alpha,96,125,139));
                gd.setCornerRadius(24f);
                bars[i].setBackground(gd);
                int priMode = prefs.getInt("lock_"+BARS[i]+"_pri_mode",0);
                int baseFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode==1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        int pushY = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0; // chỉ "r","l" (2 bar đáy)
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
        p.flags = baseFlags; p.width = w; p.height = h; p.x = x; p.y = y + pushY; p.gravity = GRAV[i];
        updateLayoutIfChanged(bars[i], p);
        if (priMode==0) applyAntiTapjacking(bars[i], w, h);
    }
}
        for (int i=0;i<4;i++) {
            if (corners[i]==null) continue;
            boolean cornEn = prefs.getBoolean("lock_corner_"+CORNERS[i]+"_en", false);
            corners[i].setVisibility((cornEn && isLocked && !hide) ? View.VISIBLE : View.GONE);
            if (cornEn && isLocked) {
                String ck = "lock_corner_"+CORNERS[i]+"_";
                int moonAlpha = prefs.getInt("lock_corner_moon_alpha",100);
                int strokeAlpha = prefs.getInt("lock_corner_stroke_alpha",200);
                int hideDelay = prefs.getInt("lock_corner_hide_dur",2500);
                int visMode = prefs.getInt(ck+"vis_mode",0);
                boolean isAuto = (visMode==1), isInv = (visMode==2);
                ((CornerView)corners[i]).updateProps(prefs.getInt("lock_corner_thick",8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                int priMode = prefs.getInt(ck+"pri_mode",0);
                int baseFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode==1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
                p.flags = baseFlags; p.gravity = C_GRAV[i];
                int wPref = prefs.getInt(ck+"w",100), hPref = prefs.getInt(ck+"h",100);
                int mwPref = prefs.getInt(ck+"moon_w",100), mhPref = prefs.getInt(ck+"moon_h",100);
                int mxOffset = Math.abs(prefs.getInt(ck+"moon_x",1250)-1250);
                int myOffset = Math.abs(prefs.getInt(ck+"moon_y",1250)-1250);
                p.width = Math.max(10, Math.max(wPref, mwPref)+mxOffset);
                p.height = Math.max(10, Math.max(hPref, mhPref)+myOffset);
                int pushY = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0; // "br","bl" là 2 góc đáy
p.x = prefs.getInt(ck+"x",0); p.y = prefs.getInt(ck+"y",0) + pushY;
                updateLayoutIfChanged(corners[i], p);
                if (priMode==0) applyAntiTapjacking(corners[i], p.width, p.height);
            }
        }
// CODE MỚI — thêm ngay trước dấu } đóng hàm:
if (panelEngine != null) panelEngine.rebuildAll();
    }
    private class SidebarTouchListener implements View.OnTouchListener {
    private String prefKeyBase;
    private View myView;
    private float sx, sy;
    private long st;
    private boolean longFired = false;
    private final Handler lpHandler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable longPressRunnable = () -> {
        longFired = true;
        handleAction(prefKeyBase + "_long");
    };
    private long lastTapUpTime = 0;
private static final long DTAP_WINDOW_MS = 300;
// Ngưỡng "coi là vuốt" — dùng CHUNG cho việc huỷ timer long-press (MOVE) lẫn
// phân loại swipe-vs-tap (UP). Đặt 60px raw (~17dp trên Pixel 2XL) — đủ lớn để
// không bị kích hoạt bởi rung tay tự nhiên lúc giữ yên, đủ nhỏ để vẫn nhạy swipe.
private static final float SWIPE_CANCEL_SLOP_PX = 60f;
    public SidebarTouchListener(String keyBase, View v) {
        this.prefKeyBase = keyBase;
        this.myView = v;
    }
    @Override public boolean onTouch(View v, MotionEvent e) {
        if (myView != null && myView instanceof CornerView) ((CornerView)myView).triggerFlash();
        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE: {
    // [FIX LONG-PRESS] Ngưỡng dùng chung SWIPE_CANCEL_SLOP_PX — chỉ huỷ timer khi
    // tay THỰC SỰ đang vuốt, không phải rung tay tự nhiên lúc giữ yên 600ms.
    float mdx = e.getRawX() - sx, mdy = e.getRawY() - sy;
    if (!longFired && (Math.abs(mdx) > SWIPE_CANCEL_SLOP_PX || Math.abs(mdy) > SWIPE_CANCEL_SLOP_PX)) {
        lpHandler.removeCallbacks(longPressRunnable);
    }
    return true;
}
            case MotionEvent.ACTION_DOWN:
                sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis();
                longFired = false;
                lpHandler.removeCallbacks(longPressRunnable);
                // [FIX #3] Timer long-press độc lập hoàn toàn với updateViewLayout() —
                // không còn phụ thuộc GestureDetector nên KHÔNG bị "quên" khi
                // updateVisibility()/updateHomaccLive() đụng vào view giữa chừng cử chỉ.
                lpHandler.postDelayed(longPressRunnable, prefs.getInt("hold_dur", 600));
                return true;
            case MotionEvent.ACTION_UP: {
                lpHandler.removeCallbacks(longPressRunnable);
                float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                long duration = System.currentTimeMillis() - st;
                if (Math.abs(dx) > SWIPE_CANCEL_SLOP_PX || Math.abs(dy) > SWIPE_CANCEL_SLOP_PX) {
                    if (longFired) return true;
                    boolean isHold = duration > prefs.getInt("hold_dur", 600);
                    String actionName;
                    if (myView instanceof CornerView && Math.abs(dx) > 40 && Math.abs(dy) > 40) {
                        actionName = "diag" + (isHold ? "_hold" : "");
                    } else {
                        if (Math.abs(dx) > Math.abs(dy)) actionName = dx > 0 ? "right" : "left";
                        else actionName = dy > 0 ? "down" : "up";
                        if (isHold) actionName += "_hold";
                    }
                    handleAction(prefKeyBase + "_" + actionName);
                    return true;
                }
                if (longFired) return true;
                // [FIX LONG-PRESS] Dự phòng: nếu Handler.postDelayed(longPressRunnable) chưa
                // kịp tự bắn trước khi ACTION_UP tới (main thread bận, hoặc buông tay đúng lúc
                // callback đang chờ trong hàng đợi), tính lại duration tại đây — y hệt cách
                // ACTION_CANCEL đã làm bên dưới. Zero cost: chỉ 1 phép so sánh số, không tạo
                // thêm Handler/timer nào.
                if (duration >= prefs.getInt("hold_dur", 600)) {
                    longFired = true;
                    handleAction(prefKeyBase + "_long");
                    return true;
                }
                long now = System.currentTimeMillis();
                if (now - lastTapUpTime <= DTAP_WINDOW_MS) {
                    lastTapUpTime = 0;
                    handleAction(prefKeyBase + "_dtap");
                } else {
                    lastTapUpTime = now;
                    final long myUpTs = now;
                    lpHandler.postDelayed(() -> {
                        if (lastTapUpTime == myUpTs) {
                            lastTapUpTime = 0;
                            handleAction(prefKeyBase + "_tap");
                        }
                    }, DTAP_WINDOW_MS + 20);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                lpHandler.removeCallbacks(longPressRunnable);
                if (!longFired) {
                    long duration = System.currentTimeMillis() - st;
                    float cdx = e.getRawX() - sx, cdy = e.getRawY() - sy;
                    if (duration >= prefs.getInt("hold_dur", 600)
                            && Math.abs(cdx) < SWIPE_CANCEL_SLOP_PX && Math.abs(cdy) < SWIPE_CANCEL_SLOP_PX) {
                        longFired = true;
                        handleAction(prefKeyBase + "_long");
                    }
                }
                return true;
            }
        }
        return true;
    }
}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        super.onDestroy();
        // [AUTO-HOMEB] Trợ năng vừa bị tắt — Lock/Homacc từ giờ vô hiệu vì cả
        // hai đều cần Accessibility. Tự bật Homeb (chỉ cần quyền Overlay) để
        // vẫn còn 1 lớp bảo vệ hoạt động.
        // [DUAL-SOUL] Accessibility vừa tắt -> Homacc PHẢI dừng hẳn NGAY LẬP TỨC.
if (AccessibleHomeService.isRunning) {
    try { stopService(new Intent(this, AccessibleHomeService.class)); } catch (Exception ignored) {}
}
try {
    HomebWatchdogReceiver.scheduleImmediate(this);
} catch (Exception ignored) {}
        try{ unregisterReceiver(stateReceiver); }catch(Exception e){}
        try{ unregisterReceiver(ipcReceiver); }catch(Exception e){}
        if (accHomeReceiver != null) try{ unregisterReceiver(accHomeReceiver); }catch(Exception e){}
        if (fpRegistered && fpController != null && fpCallback != null) {
    try { fpController.unregisterFingerprintGestureCallback(fpCallback); } catch (Exception e) {}
}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        for (int i=0;i<5;i++) if (bars[i]!=null) wm.removeView(bars[i]);
        for (int i=0;i<4;i++) if (corners[i]!=null) wm.removeView(corners[i]);
        if (fV != null) wm.removeView(fV);
        removeAccessibleHome(); 
    }
    // SAU (code thay thế):
private void drawAccessibleHome() {
    // V19.12.3.6.9 TWIN-ENGINE PHANTOM — Fix Bug A:
    // FLAG_SHOW_WHEN_LOCKED KHÔNG có tác dụng với TYPE_ACCESSIBILITY_OVERLAY
    // (chỉ có tác dụng với Activity) — đây là lý do checkbox cũ vô hiệu.
    // Guard cứng tại gốc: locked thì không vẽ, y hệt Homeb.
    if (km != null && km.isKeyguardLocked()) return;

    removeAccessibleHome();
    SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String px = "homacc_";
    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    if (wm == null) return;
    int type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    // Đã xoá pref "homacc_show_on_lock" và FLAG_SHOW_WHEN_LOCKED — hành vi
    // giờ cố định, không còn là tuỳ chọn.
    boolean avoidKbd = p.getBoolean("avoid_kbd", true);
boolean pushForKbd = avoidKbd && cachedKbdHeight > 0;
int baseF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    // --- 5 EDGE BARS ---
    for (int i = 0; i < 5; i++) {
        boolean en = p.getBoolean(px + BARS[i] + "_en", false);
        if (!en) { accHomeBars[i] = null; continue; }

        View bar = new View(this);
        int alpha   = p.getInt(px + BARS[i] + "_alpha", 50);
        int w       = p.getInt(px + BARS[i] + "_w", 300);
        int h       = p.getInt(px + BARS[i] + "_h", 60);
        int x       = p.getInt(px + BARS[i] + "_x", 0);
        int y       = p.getInt(px + BARS[i] + "_y", 0);
        int priMode = p.getInt(px + BARS[i] + "_pri_mode", 0);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.argb(alpha, 96, 125, 139));
        gd.setCornerRadius(24f);
        bar.setBackground(gd);

        int f = baseF;
        // priMode==1: xuyên thấu hoàn toàn — KHÔNG nhận touch
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            w, h, type, f, PixelFormat.TRANSLUCENT);
        int pushY = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0; // r,l = 2 bar đáy
lp.x = x; lp.y = y + pushY;
lp.gravity = GRAV[i];
        try {
            wm.addView(bar, lp);
            accHomeBars[i] = bar;
        } catch (Exception e) {
            accHomeBars[i] = null;
            continue;
        }
        if (priMode == 0) applyAntiTapjacking(bar, w, h);
        final int barIdx = i;
        bar.setOnTouchListener(new SidebarTouchListener("homacc_" + BARS[barIdx], null));
    }

    // --- 4 FRAME CORNERS ---
    for (int i = 0; i < 4; i++) {
        boolean en = p.getBoolean(px + "corner_" + CORNERS[i] + "_en", false);
        if (!en) { accHomeCorners[i] = null; continue; }

        CornerView corner = new CornerView(this, i, "homacc_");

        int moonAlpha   = p.getInt(px + "corner_moon_alpha", 100);
        int strokeAlpha = p.getInt(px + "corner_stroke_alpha", 200);
        int hideDelay   = p.getInt(px + "corner_hide_dur", 2500);
        int visMode     = p.getInt(px + "corner_" + CORNERS[i] + "_vis_mode", 0);
        int priMode     = p.getInt(px + "corner_" + CORNERS[i] + "_pri_mode", 0);

        corner.updateProps(
            p.getInt(px + "corner_thick", 8),
            moonAlpha, strokeAlpha,
            visMode == 1,  // auto-hide
            hideDelay,
            visMode == 2   // invisible
        );

        int f = baseF;
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        String ck = px + "corner_" + CORNERS[i] + "_";
        int wP    = p.getInt(ck + "w", 100),       hP    = p.getInt(ck + "h", 100);
        int mwP   = p.getInt(ck + "moon_w", 100),  mhP   = p.getInt(ck + "moon_h", 100);
        int mxOff = Math.abs(p.getInt(ck + "moon_x", 1250) - 1250);
        int myOff = Math.abs(p.getInt(ck + "moon_y", 1250) - 1250);
        int cw = Math.max(10, Math.max(wP, mwP) + mxOff);
        int ch = Math.max(10, Math.max(hP, mhP) + myOff);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            cw, ch, type, f, PixelFormat.TRANSLUCENT);
        int pushYc = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0; // br,bl = 2 góc đáy
lp.x = p.getInt(ck + "x", 0);
lp.y = p.getInt(ck + "y", 0) + pushYc;
lp.gravity = C_GRAV[i];
        try {
            wm.addView(corner, lp);
            accHomeCorners[i] = corner;
        } catch (Exception e) {
            accHomeCorners[i] = null;
            continue;
        }

        if (priMode == 0) applyAntiTapjacking(corner, cw, ch);
        final int cornIdx = i;
        corner.setOnTouchListener(
            new SidebarTouchListener("homacc_corner_" + CORNERS[cornIdx], corner));
    }
    isHomaccDrawn = true; // Đánh dấu đã vẽ xong
}

    private void removeAccessibleHome() {
    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
    for (int i = 0; i < 5; i++) {
        if (accHomeBars[i] != null) { wm.removeView(accHomeBars[i]); accHomeBars[i] = null; }
    }
    for (int i = 0; i < 4; i++) {
        if (accHomeCorners[i] != null) { wm.removeView(accHomeCorners[i]); accHomeCorners[i] = null; }
    }
    isHomaccDrawn = false; // Reset guard để lần sau có thể vẽ lại
  }
/**
 * V19.12.3.6.2 Obsidian Veil Phantom
 * updateHomaccLive(): Cập nhật Homacc overlay khi kéo slider trong Design.
 * KHÔNG removeView/addView — chỉ updateViewLayout + invalidate.
 * Zero object allocation → Adreno 540 không bị GC pause.
 */
private void updateHomaccLive() {
    if (!isHomaccDrawn) return;
    SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String px = "homacc_";
    boolean avoidKbd = p.getBoolean("avoid_kbd", true);
boolean pushForKbd = avoidKbd && cachedKbdHeight > 0;
    for (int i = 0; i < 5; i++) {
        if (accHomeBars[i] == null) continue;
        boolean en = p.getBoolean(px + BARS[i] + "_en", false);
        accHomeBars[i].setVisibility(en ? View.VISIBLE : View.GONE);
        if (!en) continue;
        int alpha   = p.getInt(px + BARS[i] + "_alpha", 50);
        int w       = p.getInt(px + BARS[i] + "_w", 300);
        int h       = p.getInt(px + BARS[i] + "_h", 60);
        int x       = p.getInt(px + BARS[i] + "_x", 0);
        int y       = p.getInt(px + BARS[i] + "_y", 0);
        int priMode = p.getInt(px + BARS[i] + "_pri_mode", 0);
        int visMode = p.getInt(px + BARS[i] + "_vis_mode", 0);
        // MỚI - chỉ update nếu color thay đổi
// MỚI - chỉ update nếu color thay đổi
GradientDrawable oldBg = (GradientDrawable) accHomeBars[i].getBackground();
int targetColor = Color.argb(alpha, 96, 125, 139);
int oldColor = 0;
if (oldBg != null && oldBg.getColor() != null) {
    oldColor = oldBg.getColor().getDefaultColor();
}
if (oldBg == null || oldColor != targetColor) {
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(targetColor);
    gd.setCornerRadius(24f);
    accHomeBars[i].setBackground(gd);
}
// KHÔNG có thêm gì ở đây — tiếp theo là vis_mode
accHomeBars[i].setAlpha(visMode == 0 ? 1f : 0f);
int f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
              | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) accHomeBars[i].getLayoutParams();
        int pushY = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0;
lp.flags = f; lp.width = w; lp.height = h; lp.x = x; lp.y = y + pushY;
lp.gravity = GRAV[i];
        try { updateLayoutIfChanged(accHomeBars[i], lp); } catch (Exception ignored) {}
        if (priMode == 0) applyAntiTapjacking(accHomeBars[i], w, h);
    }
    for (int i = 0; i < 4; i++) {
        if (accHomeCorners[i] == null) continue;
        boolean en = p.getBoolean(px + "corner_" + CORNERS[i] + "_en", false);
        accHomeCorners[i].setVisibility(en ? View.VISIBLE : View.GONE);
        if (!en) continue;
        String ck = px + "corner_" + CORNERS[i] + "_";
        int moonAlpha   = p.getInt(px + "corner_moon_alpha", 100);
        int strokeAlpha = p.getInt(px + "corner_stroke_alpha", 200);
        int hideDelay   = p.getInt(px + "corner_hide_dur", 2500);
        int visMode     = p.getInt(ck + "vis_mode", 0);
        int priMode     = p.getInt(ck + "pri_mode", 0);
        ((CornerView) accHomeCorners[i]).updateProps(
            p.getInt(px + "corner_thick", 8),
            moonAlpha, strokeAlpha,
            visMode == 1, hideDelay, visMode == 2);
        int f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
              | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        int wP  = p.getInt(ck + "w", 100),      hP  = p.getInt(ck + "h", 100);
        int mwP = p.getInt(ck + "moon_w", 100), mhP = p.getInt(ck + "moon_h", 100);
        int mxO = Math.abs(p.getInt(ck + "moon_x", 1250) - 1250);
        int myO = Math.abs(p.getInt(ck + "moon_y", 1250) - 1250);
        int cw = Math.max(10, Math.max(wP, mwP) + mxO);
        int ch = Math.max(10, Math.max(hP, mhP) + myO);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) accHomeCorners[i].getLayoutParams();
        int pushYc = (pushForKbd && (i==0 || i==1)) ? cachedKbdHeight : 0;
lp.flags = f; lp.width = cw; lp.height = ch;
lp.x = p.getInt(ck + "x", 0); lp.y = p.getInt(ck + "y", 0) + pushYc;
lp.gravity = C_GRAV[i];
        try { updateLayoutIfChanged(accHomeCorners[i], lp); } catch (Exception ignored) {}
        if (priMode == 0) applyAntiTapjacking(accHomeCorners[i], cw, ch);
        accHomeCorners[i].invalidate();
    }
  }
} // <-- Dấu ngoặc nhọn kết thúc toàn bộ class EdgeBarService
