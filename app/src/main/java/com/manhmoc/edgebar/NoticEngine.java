package com.manhmoc.edgebar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.widget.FrameLayout;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.view.animation.PathInterpolator;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class NoticEngine {
    public static volatile NoticEngine instance;
    private static final List<NoticEngine> ALL = new ArrayList<>();
static synchronized boolean previewAll(boolean toggle) {
    for (NoticEngine e : new ArrayList<>(ALL)) if (toggle ? e.previewToggle() : e.previewStart()) return true;
    return false;
}
static synchronized void endPreviewAll() { for (NoticEngine e : new ArrayList<>(ALL)) e.endPreview(); }
static volatile boolean suppressed;
static void setSuppressed(boolean on) {
    suppressed = on;
    synchronized (ALL) { for (NoticEngine e : new ArrayList<>(ALL)) e.h.post(e::syncVisible); }
}
    private static final Set<String> selfCancel = new HashSet<>();
    public static synchronized void markSelfCancel(String k) { selfCancel.add(k); }
    public static synchronized boolean consumeSelfCancel(String k) { return selfCancel.remove(k); }
public static class Item {
    String key, pkg, title, text, sig, appName;
    Bitmap icon, art, artCircle, senderBmp, appDisc;
    Icon largeIcon;
    PendingIntent intent;
    Notification.Action[] actions;
    Notification.Action reply;
    boolean media, transientItem, call, ongoing, person, playing = true, msg;
    boolean senderIsApp;
    Notification.Action snooze;
    Object token;                       // MediaSession.Token do Listener truyền sang
    MediaController mc;
    MediaController.Callback mcCb;
    int color;                          // 0 = chưa tính
    /* UX: artColor = màu chủ đạo trích từ ảnh bìa bài nhạc, cache lại để không sample mỗi frame.
       Dùng cho tuỳ chọn "màu visualizer = màu bài nhạc" (cVizColor == 8). */
    int artColor;
    float spin; long spinT;
}
    private static final int NOT_TOUCHABLE = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    private static final int NOT_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    private static final int BASE_FLAGS = NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;   // ép vẽ bằng GPU cho cả viên thuốc lẫn bảng mở rộng
    private static final int POPUP_FLAGS = BASE_FLAGS
        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
    private static final int BAR_COLOR = Color.argb(200, 96, 125, 139);
    private static final int BADGE_BLUE = 0xFF0A84FF;   // [P2] viền ô tròn số thông báo: xanh lam
    private static final int CARD_GRAY = 0xFF3A3A3C;   // [P7] nền thẻ thông báo trong bảng mở rộng: xám
    private static final int CARD_DARK = 0xFF1C1C1E;
private static final int PM_SOLID = 0, PM_CLEAR = 1, PM_GLASS = 2;
    private final Context ctx;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final int wmType;
    private final Supplier<Object[]> hostFinder;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final float dens;
    private final List<Item> items = new ArrayList<>();
    private final Map<String, Bitmap> iconCache = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 24; }
    };

    private View hostView;
    private String hostKey;
    private float scrW, scrH, pillH, ew, ewApp, cw, px, cy, hl, ht, hr, hb, barR;
    private String lastGeo = "";
    private PillView pill;
    private WindowManager.LayoutParams pillLp;
    private boolean fullWin, expanded, statusVisible = true, insetsVisible = true, shadeOpen, hostChanged, sysRegistered, fadeNext, secureOverlay;
    private boolean previewForce;   // Notic Space: ép hiện dù dialog fullscreen làm status bar bị ẩn
private String pillHostKey;     // host pill đang vẽ cho -> nhận ra chuyển Lock<->Home để không trượt
    private int cur = 0;

    private LinearLayout popup;
    private LinearLayout popupBody;
    private WindowManager.LayoutParams popupLp;
    private long lastCloseMs = 0;
    private Ringtone ringtone;

    private BroadcastReceiver sysRx, fullRx;
    private ConnectivityManager cmg;
    private ConnectivityManager.NetworkCallback netCb, wifiCb, vpnCb;
    private AudioManager audio;
    private AudioManager.AudioRecordingCallback micCb;
    private CameraManager camMgr;
    private CameraManager.AvailabilityCallback camCb;
    private CameraManager.TorchCallback torchCb;
    private final Set<String> camBusy = new HashSet<>();
    private long armedAt, lastTorchMs;
    private boolean wifiUp, vpnUp, micOn, recOn;
    private final Runnable settleRun = this::settle;
    private boolean userHidden;
    private final Runnable reshowRun = this::reshowNotic;
    private boolean inCall, replayNext;
private long lastReplayMs;
private final Runnable callCheckRun = this::checkCall;
private final Runnable recoverRun;
private boolean callCheckQueued;

private void scheduleRecover() {
    h.removeCallbacks(recoverRun);
    h.postDelayed(recoverRun, 350);
    h.postDelayed(recoverRun, 1500);
}

private void scheduleCallCheck(long delayMs) {
    if (callCheckQueued) return;
    callCheckQueued = true;
    h.postDelayed(callCheckRun, delayMs);
}
private void checkCall() {
    h.removeCallbacks(callCheckRun);
    callCheckQueued = false;
    if (audio == null) audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    int m = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
    boolean c = m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION;
    if (c != inCall) { inCall = c; syncVisible(); }
    if (screenOn && hideOn("call", true) && (inCall || !items.isEmpty())) {   // UX: dò nhẹ 1,5s/lần, CHỈ khi có thông báo hoặc đang gọi; hết thông báo + hết gọi là tự dừng (tiết kiệm pin)
        callCheckQueued = true;
        h.postDelayed(callCheckRun, 1500);
    }
}
    private void reshowNotic() {
        if (previewForce) return;
        if (!userHidden) return;
        if (items.isEmpty() || !prefs.getBoolean("notic_en", false)) return;
        userHidden = false;
        announce();
    }
    // [P1] Chống giật Khoá -> Home: chỉ di chuyển pill khi hình học của bar host đã ĐỨNG YÊN (2 lần đo cách nhau 80ms giống nhau)
    private String pendingGeo = "";
    private int geoTries;
    private long lastSoundMs, fadeUntil;
    private final Runnable geoRun = this::geoSettle;
    private int noHostRetry;
    /* UX: không bao giờ bỏ cuộc: mất host thì sau ~1,5s mới thu pill (có animation), rồi dò thưa 2,5s/lần
       cho tới khi host quay lại. Tự dừng khi hết thông báo hoặc tắt màn nên không tốn pin. */
    private final Runnable noHostRun = () -> {
        if (items.isEmpty() || !this.screenOn) { noHostRetry = 0; return; }
        if (bindHost()) { noHostRetry = 0; relayout(); return; }
        if (++noHostRetry == 3 && !this.closing) { closePopup(false, false); shrinkRemove(false, false); }
        h.postDelayed(this.noHostRun, noHostRetry < 3 ? 500 : 2500);
    };
private static final TimeInterpolator EMPH = new PathInterpolator(0.2f, 0f, 0f, 1f);
private static final TimeInterpolator EMPH_IN = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
private static final int[] VIZ_COLORS = {0, 0xFF00E5FF, 0xFF8AB4F8, 0xFFFF4081, 0xFFFFC107, 0xFF69F0AE, 0xFFFFFFFF, 0xFFB388FF, 0};
static final String[] PILL_HEX = {"#0A84FF","#30D158","#FF453A","#FF9F0A","#BF5AF2","#64D2FF","#FF375F","#8E8E93",
    "#FFD60A","#5E5CE6","#40C8E0","#AC8E68","#FF6482","#32ADE6","#66D4CF"};
static final String[] CARD_HEX = PILL_HEX;   // UX: 1 bảng 15 màu glass dùng chung cho cả viên notic và bảng mở rộng
private static final long DTAP_MS = 280;
private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d .\\-()]{6,}\\d");
private final Map<String, Integer> colorCache = new HashMap<>();
private boolean screenOn = true, popupHistory;
private LinearLayout popupHead;
private GradientDrawable popupBg;
private Runnable pendingTap;
private long lastTapMs;
private int cPill = Color.BLACK, cRadPct = 100, cPillMode = PM_SOLID, cPillA = 100, cPopA = 85,
    cCardMode, cGlass = 60, cPopGap, cLedSpeed = 100;
    private int cPillTint;   // 0 = glass trung tính, khác 0 = glass có màu
private boolean cLed = true;
private boolean badgeLeft, popMorphing;
private boolean closing, reopen, swiping;
private float morphF;
private float oL, oT, oR, oB, oRad, popRad;
private ValueAnimator morphAnim;
private final Runnable autoCloseRun = this::autoClose;
    private boolean cBorder = true, cSpin = true, cViz = true;
    private boolean appHidden;
private final Map<String, String> drafts = new HashMap<>();   // UX: nháp reply theo key thông báo, đóng/mở lại bảng không mất chữ
private boolean hideOn(String id, boolean def) { return prefs.getBoolean("notic_hide_" + id, def); }
private boolean hasDraft() { for (String v : drafts.values()) if (v != null && !v.trim().isEmpty()) return true; return false; }
public void setForegroundPkg(String pkg) {
    if (pkg == null || pkg.isEmpty() || pkg.contains("inputmethod") || pkg.contains("systemui")) return;   // bàn phím/systemui không được tính là đổi app
    boolean hit = prefs.getBoolean("notic_hide_app_en", false)
        && ("," + prefs.getString("notic_hide_apps", "") + ",").contains("," + pkg + ",");
    if (hit == appHidden) return;
    appHidden = hit;
    syncVisible();
}
private Bitmap appDisc(Item it) {
    if (it.appDisc == null && it.icon != null) it.appDisc = makeAppDisc(it.icon, appColor(it));
    return it.appDisc;
}
private boolean canFallbackReply(Item it) {   // app không có RemoteInput (Claude, Gemini...): chép chữ + mở app
    return it.reply == null && !it.media && !it.call && !it.transientItem && !isDemo(it) && it.intent != null
        && it.pkg != null && prefs.getBoolean("notic_fallback_reply", false)
        && ("," + prefs.getString("notic_reply_apps", "") + ",").contains("," + it.pkg + ",");
}
    private int cVizColor = 0, cVizSpeed = 100;
    private boolean cBadge = true;
    private float cAnimScale = 1f;
    private void cachePrefs() {
    cBorder = prefs.getBoolean("notic_border_en", true);
    cSpin = prefs.getBoolean("notic_art_spin", true);
    cViz = prefs.getBoolean("notic_viz_en", true);
    cVizColor = prefs.getInt("notic_viz_color", 0);
    cVizSpeed = prefs.getInt("notic_viz_speed", 100);
    cRadPct = prefs.getInt("notic_rad_pct", 100);
    cBadge = prefs.getBoolean("notic_badge_en", true);
    cAnimScale = Math.max(20, Math.min(300, prefs.getInt("notic_anim_speed", 100))) / 100f;
    int pi = prefs.getInt("notic_pill_color", 0);   // 0 đen, 1 trong suốt, 2 glass, >=3 glass có màu (PILL_HEX)
cPillMode = pi == 1 ? PM_CLEAR : (pi >= 2 ? PM_GLASS : PM_SOLID);
cPillTint = (pi >= 3 && pi - 3 < PILL_HEX.length) ? Color.parseColor(PILL_HEX[pi - 3]) : 0;
cPill = Color.BLACK;
cPillA = Math.max(0, Math.min(100, prefs.getInt("notic_pill_alpha", 100)));
cPopA = Math.max(0, Math.min(100, prefs.getInt("notic_pop_alpha", 85)));
cCardMode = prefs.getInt("notic_card_color", 0);
cGlass = Math.max(0, Math.min(100, prefs.getInt("notic_glass_relief", 60)));
cPopGap = Math.max(0, prefs.getInt("notic_pop_gap", 0));
cLed = prefs.getBoolean("notic_led_en", true);
cLedSpeed = Math.max(20, prefs.getInt("notic_led_speed", 100));
}

/* UX: mọi duration Notic đi qua đây — 1 slider duy nhất scale toàn hệ thống
   (mở/thu gọn/morph/slide host), không phải sửa 8 chỗ riêng. Clamp 40ms bảo vệ
   cảm giác mượt kể cả khi user kéo về 20%. Cache sẵn vào cAnimScale nên không
   đọc prefs mỗi frame -> 0 IPC trong lúc animate, tốt cho pin Pixel 2XL. */
private long aDur(long base) { return Math.max(40L, Math.round(base * cAnimScale)); }
private float speed() { return cVizSpeed / 100f; }
private int vizColor(Item it) {
    int i = cVizColor;
    if (i == 8) {   // màu bài nhạc: lấy 1 lần từ ảnh bìa, cache trong Item
        if (it.artColor == 0) { Bitmap a = loadLarge(it); it.artColor = sampleColor(a != null ? a : it.icon); }
        return it.artColor;
    }
    return (i <= 0 || i >= VIZ_COLORS.length) ? appColor(it) : VIZ_COLORS[i];
}
private int appColor(Item it) {
    if (it.color != 0) return it.color;
    String k = it.pkg != null ? it.pkg : it.key;
    Integer c = colorCache.get(k);
    if (c == null) { c = sampleColor(it.icon); colorCache.put(k, c); } // cache theo package, chỉ tính 1 lần
    it.color = c;
    return c;
}

private int sampleColor(Bitmap b) {
    if (b == null) return 0xFF8AB4F8;
    Bitmap s = Bitmap.createScaledBitmap(b, 12, 12, true);
    float[] hsv = new float[3];
    long r = 0, g = 0, bl = 0, n = 0;
    for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
        int p = s.getPixel(x, y);
        if (Color.alpha(p) < 128) continue;
        Color.colorToHSV(p, hsv);
        if (hsv[1] < 0.35f || hsv[2] < 0.35f) continue;
        r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
    }
    s.recycle();
    if (n == 0) return 0xFF8AB4F8;
    Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)), hsv);
    hsv[1] = Math.max(hsv[1], 0.55f);
    hsv[2] = Math.max(hsv[2], 0.85f);
    return Color.HSVToColor(hsv);
}

private float spinAngle(Item it) {
    long now = SystemClock.uptimeMillis();
    if (it.playing && it.spinT != 0) it.spin = (it.spin + (now - it.spinT) * 0.04f) % 360f;
    it.spinT = now;   // pause thì đĩa đứng yên đúng góc đang quay
    return it.spin;
}
static void drawViz(Canvas c, Paint p, float x, float cy, float w, float h, int color, boolean playing, float speed) {
    drawViz(c, p, x, cy, w, h, color, playing, speed, 0.07f);
}
static void drawViz(Canvas c, Paint p, float x, float cy, float w, float h, int color, boolean playing, float speed, float gapFrac) {
    final int n = 7;
    float gap = w * gapFrac, bw = (w - gap * (n - 1)) / n;
    float t = (SystemClock.uptimeMillis() % 100000L) / 1000f * speed;
    p.setColor(color);
    for (int i = 0; i < n; i++) {
        float f = playing ? 0.30f + 0.70f * Math.abs((float) Math.sin(t * (2.1f + i * 0.7f) + i * 1.7f)) : 0.22f;
        float bh = h * f, l = x + i * (bw + gap);
        c.drawRoundRect(l, cy - bh / 2f, l + bw, cy + bh / 2f, bw / 2f, bw / 2f, p);
    }
}
private static int withA(int color, int pct) {
    return (Math.round(Color.alpha(color) * Math.max(0, Math.min(100, pct)) / 100f) << 24) | (color & 0x00FFFFFF);
}
private static final int GLASS_BODY_A = 150;   // UX: độ đậm tối đa của nền glass khi thanh kéo = 100%; chỉnh số này nếu muốn glass đậm/nhạt hơn
private static int glassTint(int rgb, int pct) {
    int c = blend(rgb | 0xFF000000, Color.BLACK, 0.25f);   // tối nhẹ để chữ trắng luôn đọc được
    return (Math.round(GLASS_BODY_A * Math.max(0, Math.min(100, pct)) / 100f) << 24) | (c & 0x00FFFFFF);
}
private int pillFill() {
    if (cPillMode == PM_CLEAR) return 0;
    if (cPillMode == PM_GLASS) return glassTint(cPillTint != 0 ? cPillTint : 0xFF14181E, cPillA);
    return withA(cPill, cPillA);
}
private static final class Glass {
    private final Paint sheenP = new Paint(Paint.ANTI_ALIAS_FLAG), rimP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient sheen = new LinearGradient(0, 0, 0, 1,
        new int[]{0xFFFFFFFF, 0x00FFFFFF, 0x00000000, 0xFF000000}, new float[]{0f, 0.40f, 0.60f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient edge = new LinearGradient(0, 0, 1, 1,
        new int[]{0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0xCCFFFFFF}, new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP);
    private final Matrix m = new Matrix();
    private final RectF rim = new RectF();
    Glass(float stroke) { rimP.setStyle(Paint.Style.STROKE); rimP.setStrokeWidth(stroke); }
    void draw(Canvas c, RectF r, float rad, int relief, float f) {
        float k = Math.max(0, Math.min(100, relief)) / 100f * f;
        if (k <= 0.01f || r.width() < 2 || r.height() < 2) return;
        m.setScale(r.width(), r.height());
        m.postTranslate(r.left, r.top);
        sheen.setLocalMatrix(m); edge.setLocalMatrix(m);
        sheenP.setShader(sheen); sheenP.setAlpha((int) (120 * k));   // sáng phía trên, tối phía dưới = giọt nước nổi khối
        c.drawRoundRect(r, rad, rad, sheenP);
        float in = rimP.getStrokeWidth() / 2f;
        rim.set(r.left + in, r.top + in, r.right - in, r.bottom - in);
        rimP.setShader(edge); rimP.setAlpha((int) (255 * k));        // viền phản quang chéo
        c.drawRoundRect(rim, Math.max(0, rad - in), Math.max(0, rad - in), rimP);
    }
}
private final class GlassDrawable extends Drawable {
    private final Glass g = new Glass(1.6f * dens);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float rad, k;
    GlassDrawable(float rad, int tint, float k) { this.rad = rad; this.k = k; body.setColor(tint); }
    @Override public void draw(Canvas c) {
        r.set(getBounds());
        c.drawRoundRect(r, rad, rad, body);
        g.draw(c, r, rad, cGlass, k);   // UX: viền/bóng glass cũng mờ theo độ mờ để thanh kéo có tác dụng thật
    }
    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

// Đĩa tròn tô màu app + icon vuông ở giữa -> 4 phần thừa của đĩa mang màu icon
private Bitmap makeAppDisc(Bitmap src, int color) {
    if (src == null) return null;
    int s = 96;
    Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    p.setColor(color);
    c.drawCircle(s / 2f, s / 2f, s / 2f, p);
    float q = s * 0.22f;
    c.drawBitmap(src, null, new RectF(q, q, s - q, s - q), p);
    return out;
}
private Bitmap makeDisc(Bitmap src, boolean vinyl) {
    if (src == null) return null;
    int s = 96;
    Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    c.drawCircle(s / 2f, s / 2f, s / 2f - 3, p);
    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    c.drawBitmap(src, null, new RectF(3, 3, s - 3, s - 3), p);
    p.setXfermode(null);
    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(Color.argb(200, 20, 20, 20));
    c.drawCircle(s / 2f, s / 2f, s / 2f - 3, p);
    p.setStyle(Paint.Style.FILL);
    if (vinyl) c.drawCircle(s / 2f, s / 2f, 7, p);   // UX: lỗ đĩa than chỉ cho app nhạc (đĩa quay + visualizer)
    return out;
}

private Bitmap circleArt(Item it) {
    if (it.artCircle != null) return it.artCircle;
    Bitmap src = loadLarge(it);
// UX: có ảnh bìa thật -> cắt tròn; không có (chỉ icon vuông) -> icon nằm giữa đĩa, 4 góc thừa tô màu icon app
it.artCircle = src != null ? makeDisc(src, true) : makeAppDisc(it.icon, appColor(it));
    return it.artCircle;
}

private Bitmap senderCircle(Item it) {
    if (it.senderBmp != null) return it.senderBmp;
    if (it.media || it.transientItem) return null;
    Bitmap src = it.largeIcon != null ? loadLarge(it) : null;
    if (src != null) { it.senderBmp = makeDisc(src, false); return it.senderBmp; }
    if (!it.person) {                              // thông báo thường: icon app thành đĩa
        it.senderIsApp = true;
        it.senderBmp = makeAppDisc(it.icon, appColor(it));
        return it.senderBmp;
    }
    int sz = 96;                                   // người nhắn/gọi không có avatar: chữ cái đầu như cũ
    Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
    Canvas cv = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    String t = it.title == null ? "" : it.title.trim();
    String ch = t.isEmpty() ? "?" : new String(Character.toChars(t.codePointAt(0))).toUpperCase(Locale.getDefault());
    p.setColor(blend(0xFF3A3A3C, appColor(it), 0.55f));
    cv.drawCircle(sz / 2f, sz / 2f, sz / 2f, p);
    p.setColor(Color.WHITE);
    p.setTextSize(sz * 0.5f);
    p.setTextAlign(Paint.Align.CENTER);
    p.setTypeface(Typeface.DEFAULT_BOLD);
    Paint.FontMetrics fm = p.getFontMetrics();
    cv.drawText(ch, sz / 2f, sz / 2f - (fm.ascent + fm.descent) / 2f, p);
    it.senderBmp = out;
    return out;
}
private Bitmap square96(Bitmap b) {
    Bitmap o = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
    int m = Math.min(b.getWidth(), b.getHeight());
    new Canvas(o).drawBitmap(b, new android.graphics.Rect((b.getWidth() - m) / 2, (b.getHeight() - m) / 2,
        (b.getWidth() + m) / 2, (b.getHeight() + m) / 2), new RectF(0, 0, 96, 96), new Paint(Paint.FILTER_BITMAP_FLAG));
    return o;
}

private void bindMedia(final Item it, Object token) {
    try {
        it.mc = new MediaController(ctx, (android.media.session.MediaSession.Token) token);
        PlaybackState ps = it.mc.getPlaybackState();
        it.playing = ps == null || ps.getState() == PlaybackState.STATE_PLAYING || ps.getState() == PlaybackState.STATE_BUFFERING;
        it.mcCb = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState s) {
                boolean p = s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
                if (p == it.playing) return;      // chỉ vẽ lại khi thật sự đổi play/pause
                it.playing = p;
                refreshMedia(it);
            }
            @Override public void onMetadataChanged(MediaMetadata m) {
                if (m == null) return;
                Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b == null) return;
                it.art = square96(b); it.artCircle = null; it.artColor = 0;   // trong onMetadataChanged
                refreshMedia(it);
            }
        };
        it.mc.registerCallback(it.mcCb, h);
    } catch (Exception e) { it.mc = null; it.mcCb = null; }
}

private void unbindMedia(Item it) {
    if (it.mc != null && it.mcCb != null) { try { it.mc.unregisterCallback(it.mcCb); } catch (Exception ignored) {} }
    it.mc = null; it.mcCb = null;
}

private void refreshMedia(Item it) {
    if (pill != null && !items.isEmpty() && items.get(Math.min(cur, items.size() - 1)) == it) pill.invalidate();
    if (popup != null) renderPopup(true);
}
    public NoticEngine(Context ctx, WindowManager wm, SharedPreferences prefs, int wmType, Supplier<Object[]> hostFinder) {
        this.ctx = ctx;
        this.wm = wm;
        this.prefs = prefs;
        this.wmType = wmType;
        this.hostFinder = hostFinder;
        this.dens = ctx.getResources().getDisplayMetrics().density;
        instance = this;
        synchronized (ALL) { ALL.add(this); }
        cachePrefs();
        if (prefs.getBoolean("notic_en", false)) registerSystemEvents();
        this.recoverRun = () -> {
            if (items.isEmpty() || userHidden || !screenOn || !prefs.getBoolean("notic_en", false)) return;
            if (pill == null || pill.hidden || replayNext) { if (statusVisible) announce(); }
            else relayout();
        };
    }
    private int dp(float v) { return Math.round(v * dens); }
    private float badgeExtra() { return pillH + 6 * dens; }   // [P2] đường kính ô tròn số (= bề dày pill) + khoảng cách 6dp
    public boolean hasHost() {
        Object[] hs = hostFinder.get();
        return hs != null && ((View) hs[0]).getVisibility() == View.VISIBLE;
    }
    boolean isHostCovered(View v) { return v != null && v == hostView && pill != null && !pill.hidden && !closing; }
public void onOverlayTouch(View touched) {
    if (popup != null && touched != hostView) closePopup(false, true);
}
    public boolean dndBlocks() {
        if (!prefs.getBoolean("notic_dnd_skip", true)) return false;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        int f = nm.getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    public Bitmap appIcon(String pkg) {
        Bitmap b = iconCache.get("app:" + pkg);
        if (b != null) return b;
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 96, 96);
            d.draw(c);
            iconCache.put("app:" + pkg, b);
            return b;
        } catch (Exception e) { return null; }
    }

    // UX: muốn đổi icon sự kiện nào chỉ cần sửa 1 dòng trong bảng này (tên drawable trong CUSTOM_ICON_NAMES).
private static final String[][] EV_ICONS = {
    {"charging", "flash_on_24px"}, {"full", "battery_charging_full_24px"}, {"low", "warning_24px"},
    {"nonet", "mobile_question_24px"}, {"wifi", "android_wifi_3_bar_lock_24px"}, {"vpn", "security_24px"},
    {"hotspot", "share_location_24px"}, {"bt", "settings_bluetooth_24px"}, {"silent", ""},
    {"vibrate", "stream_24px"}, {"ring", ""}, {"unlock", "mobile_lock_portrait_24px"},
    {"screenon", "light_mode_24px"}, {"airplane", "flight_24px"}, {"headset", "headphones_24px"},
    {"dnd", "dark_mode_24px"}, {"storage", "data_usage_24px"}, {"screenshot", "crop_24px"},
    {"rec", "movie_24px"}, {"mic", "settings_voice_24px"}, {"camera", "photo_camera_24px"}
};

private Drawable eventDrawable(String id) {
    String name = "";
    for (String[] e : EV_ICONS) if (e[0].equals(id)) { name = e[1]; break; }
    int res = name.isEmpty() ? 0 : ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    if (res == 0) res = id.equals("silent") ? android.R.drawable.ic_lock_silent_mode
        : id.equals("ring") ? android.R.drawable.ic_lock_silent_mode_off : android.R.drawable.ic_dialog_info;
    try { return ctx.getDrawable(res); } catch (Exception e) { return null; }
}

private Bitmap eventIcon(String id) {
    String k = "ev:" + id;
    Bitmap b = iconCache.get(k);
    if (b != null) return b;
    Drawable d = eventDrawable(id);
    if (d == null) return null;
    d = d.mutate();
    d.setTint(0xFFE8EAED);
    Bitmap g = PanelEngine.normalizeIconBitmap(d, 96, 0.5f);
    b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(CARD_GRAY);
    c.drawCircle(48, 48, 48, p);
    if (g != null) { c.drawBitmap(g, 0, 0, null); g.recycle(); }
    iconCache.put(k, b);
    return b;
}
    private Bitmap loadLarge(Item it) {
        if (it.art != null) return it.art;
        if (it.largeIcon == null) return null;
        try {
            Drawable d = it.largeIcon.loadDrawable(ctx);
            if (d == null) return null;
            Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 96, 96);
            d.draw(c);
            it.art = b;
            return b;
        } catch (Exception e) { return null; }
    }
    private void unregisterSystemEvents() {
        if (!sysRegistered) return;
        sysRegistered = false;
        try { if (sysRx != null) ctx.unregisterReceiver(sysRx); } catch (Exception ignored) {}
        sysRx = null;
        stopFullWatch();
        try { if (cmg != null) { if (netCb != null) cmg.unregisterNetworkCallback(netCb); if (wifiCb != null) cmg.unregisterNetworkCallback(wifiCb); if (vpnCb != null) cmg.unregisterNetworkCallback(vpnCb); } } catch (Exception ignored) {}
        netCb = null; wifiCb = null; vpnCb = null;
        try { if (audio != null && micCb != null) audio.unregisterAudioRecordingCallback(micCb); } catch (Exception ignored) {}
        micCb = null;
        try { if (camMgr != null) { if (camCb != null) camMgr.unregisterAvailabilityCallback(camCb); if (torchCb != null) camMgr.unregisterTorchCallback(torchCb); } } catch (Exception ignored) {}
        camCb = null; torchCb = null;
        camBusy.clear();
        wifiUp = false; vpnUp = false; micOn = false; recOn = false;
    }

    private void registerSystemEvents() {
        if (sysRegistered) return;
        sysRegistered = true;
        armedAt = SystemClock.elapsedRealtime() + 2500;
        sysRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (a == null) return;
                if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                    event("charging", "⚡", "Phone is charging", "", 0xFF2E7D32);
                    startFullWatch();
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                    stopFullWatch();
                } else if (Intent.ACTION_BATTERY_LOW.equals(a)) {
                    event("low", "⚠", "Battery low (" + batteryLevel() + "%)", "", 0xFFC62828);
                } else if (Intent.ACTION_USER_PRESENT.equals(a)) {
                    replayNext = true; h.post(callCheckRun); scheduleRecover();
                    event("unlock", "🔓", "Phone unlocked", "", 0xFF455A64);
                } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
    screenOn = true;
    replayNext = true; h.post(callCheckRun); scheduleRecover();
    if (pill != null) pill.invalidate();   // khởi động lại vòng vẽ đĩa/visualizer
    event("screenon", "💡", "Screen on", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 0xFF37474F);
                } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(a)) {
                    event("storage", "💾", "Storage is almost full", "", 0xFFEF6C00);
                } else if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(a)) {
                    int st = i.getIntExtra("wifi_state", 0);
                    if (st == 13) event("hotspot", "📡", "Hotspot on", "", 0xFF00796B);
                    else if (st == 11) event("hotspot", "📡", "Hotspot off", "", 0xFF616161);
                } else if (VoiceRecorderService.TICK_ACTION.equals(a)) {
                    String s = i.getStringExtra("state");
                    if ("RECORDING".equals(s) && !recOn) { recOn = true; event("rec", "🔴", "Recording started", "", 0xFFC62828); }
                    else if ("STOPPED".equals(s) && recOn) { recOn = false; event("rec", "⏹", "Recording stopped", "", 0xFF616161); }
                } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(a)) {
                    int m = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
                    if (m == AudioManager.RINGER_MODE_SILENT) event("silent", "🔕", "Silent", "", 0xFFD32F2F);
                    else if (m == AudioManager.RINGER_MODE_VIBRATE) event("vibrate", "📳", "Vibrate", "", 0xFFD32F2F);
                    else event("ring", "🔔", "Ring", "", 0xFF263238);
                } else if ("android.bluetooth.device.action.ACL_CONNECTED".equals(a)) {
                    String name = "";
                    try {
                        android.bluetooth.BluetoothDevice d = i.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);
                        if (d != null && d.getName() != null) name = d.getName();
                    } catch (Exception ignored) {}
                    event("bt", "🔵", "Bluetooth connected", name, 0xFF1565C0);
                } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(a)) {
                    event("airplane", "✈", i.getBooleanExtra("state", false) ? "Airplane mode on" : "Airplane mode off", "", 0xFF6A1B9A);
                } else if (Intent.ACTION_HEADSET_PLUG.equals(a)) {
                    if (i.getIntExtra("state", 0) == 1) event("headset", "🎧", "Headphones connected", "", 0xFF00838F);
                } else if (NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED.equals(a)) {
                    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                    boolean on = nm != null && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
                    event("dnd", "🌙", on ? "Do Not Disturb on" : "Do Not Disturb off", "", 0xFF4527A0);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        f.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        f.addAction(VoiceRecorderService.TICK_ACTION);
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        f.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(Intent.ACTION_HEADSET_PLUG);
        f.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(sysRx, f, Context.RECEIVER_NOT_EXPORTED);
            else ctx.registerReceiver(sysRx, f);
        } catch (Exception ignored) {}

        cmg = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onLost(Network n) {
                    h.postDelayed(() -> {
                        if (cmg != null && cmg.getActiveNetwork() == null) event("nonet", "🚫", "No Internet", "", 0xFFE53935);
                    }, 1500);
                }
            };
            cmg.registerDefaultNetworkCallback(netCb, h);
        } catch (Exception e) { netCb = null; }
        try {
            wifiCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) {
                    if (wifiUp) return;
                    wifiUp = true;
                    if (SystemClock.elapsedRealtime() < armedAt) return;
                    event("wifi", "📶", "Wi-Fi connected", "", 0xFF1E88E5);
                }
                @Override public void onLost(Network n) {
                    if (!wifiUp) return;
                    wifiUp = false;
                    event("wifi", "📵", "Wi-Fi disconnected", "", 0xFF757575);
                }
            };
            cmg.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCb, h);
        } catch (Exception e) { wifiCb = null; }
        try {
            vpnCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) {
                    if (vpnUp) return;
                    vpnUp = true;
                    if (SystemClock.elapsedRealtime() < armedAt) return;
                    event("vpn", "🛡", "VPN on", "", 0xFF2E7D32);
                }
                @Override public void onLost(Network n) {
                    if (!vpnUp) return;
                    vpnUp = false;
                    event("vpn", "🛡", "VPN off", "", 0xFF757575);
                }
            };
            cmg.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), vpnCb, h);
        } catch (Exception e) { vpnCb = null; }
        try {
            audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            micCb = new AudioManager.AudioRecordingCallback() {
                @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> l) {
                    h.removeCallbacks(callCheckRun); h.postDelayed(callCheckRun, 500);
                    boolean active = l != null && !l.isEmpty();
                    if (active && !micOn) {
    micOn = true;
    if (!hideOn("mic", false) && !VoiceRecorderService.isRunning && !ScreenRecorderService.isRunning && SystemClock.elapsedRealtime() > armedAt)
        event("mic", "🎙", "Microphone in use", "", 0xFFF57C00);
    syncVisible();
} else if (!active && micOn) { micOn = false; syncVisible(); }
                }
            };
            audio.registerAudioRecordingCallback(micCb, h);
            micOn = !audio.getActiveRecordingConfigurations().isEmpty();   // UX: mic đã mở sẵn trước khi đăng ký callback thì trước đây không bao giờ được tính
        } catch (Exception e) { micCb = null; }
        try {
            camMgr = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            torchCb = new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean en) { lastTorchMs = SystemClock.elapsedRealtime(); }
            };
            camCb = new CameraManager.AvailabilityCallback() {
                @Override public void onCameraUnavailable(String id) {
    long now = SystemClock.elapsedRealtime();
if (now - lastTorchMs < 1500) return;   // UX: chỉ loại đèn pin; bỏ điều kiện armedAt để camera mở ngay sau khi bật Notic vẫn được tính
if (camBusy.add(id)) {
    if (now >= armedAt && camBusy.size() == 1 && !hideOn("cam", false)) event("camera", "📷", "Camera in use", "", 0xFFEF6C00);
    syncVisible();
    }
}
@Override public void onCameraAvailable(String id) { if (camBusy.remove(id)) syncVisible(); }
            };
            camMgr.registerTorchCallback(torchCb, h);
            camMgr.registerAvailabilityCallback(camCb, h);
        } catch (Exception e) { camCb = null; }
    }

    private int batteryLevel() {
        try {
            Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return 15;
            int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 15);
            int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            return Math.round(lv * 100f / Math.max(1, sc));
        } catch (Exception e) { return 15; }
    }

    private void startFullWatch() {
        if (fullRx != null) return;
        fullRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL) {
                    event("full", "🔋", "Phone is fully charged", "", 0xFF43A047);
                    stopFullWatch();
                }
            }
        };
        try { ctx.registerReceiver(fullRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); }
        catch (Exception e) { fullRx = null; }
    }

    private void stopFullWatch() {
        if (fullRx == null) return;
        try { ctx.unregisterReceiver(fullRx); } catch (Exception ignored) {}
        fullRx = null;
    }

    public void fireEvent(String id, String emoji, String title, String text, int color) { event(id, emoji, title, text, color); }

    private void event(String id, String emoji, String title, String text, int color) {
        if (!prefs.getBoolean("notic_en", false) || prefs.getBoolean("notic_paused", false)) return;
        if (!prefs.getBoolean("notic_ev_" + id, !("unlock".equals(id) || "screenon".equals(id)))) return;   // [P1] unlock/screenon mặc định tắt
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null || !pm.isInteractive()) return;
        if (!"dnd".equals(id) && dndBlocks()) return;
        Item it = new Item();
        it.key = "ev_" + id;
        it.title = title;
        it.text = text;
        it.appName = "Edge Bar";
        it.sig = title + text + SystemClock.elapsedRealtime();
        it.icon = eventIcon(id);
        it.transientItem = true;
        post(it, false, false);
    }
public boolean post(final Item it, boolean wake, boolean sound) {
    final boolean hosted = hasHost();
    if (it.transientItem || it.call) { userHidden = false; h.removeCallbacks(reshowRun); }   // UX: vuốt-ẩn không được nuốt event rung/im lặng
    if (!hosted && it.transientItem) return false;   // sự kiện hệ thống mà không có chỗ hiện thì bỏ
    Item old = null;
    for (Item x : items) if (x.key.equals(it.key)) { old = x; break; }
    boolean ongoingUpd = old != null && it.ongoing && !it.media && !it.call;
    boolean same = old != null && old.sig.equals(it.sig);
    boolean fresh = !ongoingUpd && !same;
    if (fresh) { if (wake) wakeScreen(); if (sound) playSound(); }
    if (it.icon == null && it.pkg != null) it.icon = appIcon(it.pkg);
    if (ongoingUpd) {
        old.title = it.title; old.text = it.text; old.sig = it.sig;
        old.intent = it.intent; old.actions = it.actions;
        return hosted;
    }
    if (same) {
        if (it.media) {
            old.actions = it.actions; old.intent = it.intent; old.largeIcon = it.largeIcon;
            old.art = null; old.artCircle = null; old.artColor = 0;
            if (old.mc == null && it.token != null) bindMedia(old, it.token);
            if (old.mc == null) refreshMedia(old);
        }
        return hosted;
    }
    if (old != null) { unbindMedia(old); items.remove(old); }
    if (it.media && it.token != null) bindMedia(it, it.token);
    items.add(0, it);
    cur = 0;
    while (items.size() > 20) unbindMedia(items.remove(items.size() - 1));
    syncVisible();
    if (!callCheckQueued) h.post(callCheckRun);   // thông báo mới tới lúc đang gọi là ẩn ngay
    long nowR = SystemClock.elapsedRealtime();
    // UX: chat dồn dập thì không replay liên tục (>=1.5s mới replay), đỡ nháy + đỡ tốn pin.
    if (popup == null && nowR - lastReplayMs > 1500) { replayNext = true; lastReplayMs = nowR; }
    announce();
    if (popup != null) renderPopup();
    if (it.call) {
        h.postDelayed(() -> removeItem(it), 60000);   // chốt an toàn nếu hệ thống không gửi onRemoved
    } else if (!it.transientItem && !it.media && screenOn && statusVisible
            && prefs.getBoolean("notic_auto_expand", false)) {
        h.postDelayed(() -> { showPopup(false, true); scheduleAutoClose(); }, Math.max(200, aDur(520)));
    }
    if (it.transientItem) h.postDelayed(() -> removeItem(it), 3600);
    return hosted;
}
private void scheduleAutoClose() {
    h.removeCallbacks(autoCloseRun);
    h.postDelayed(autoCloseRun, Math.max(1, prefs.getInt("notic_hold_sec", 4)) * 1000L);
}
private void autoClose() {
    if (hasDraft()) return;   // đang soạn dở: không tự thu gọn (UP sẽ lên lịch lại)
    if (popup == null) return;
    if (popupTouching || swiping || (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0)) {
        h.postDelayed(autoCloseRun, 1500); return;   // đang vuốt/gõ -> hoãn
    }
    closePopup(false, true);
}
private void removeItem(Item it) {
    if (items.remove(it)) { unbindMedia(it); afterChange(); }
}
public void onScreenOff() {
    screenOn = false;
    shadeOpen = false;
    userHidden = false;
    statusVisible = wantVisible();
    cancelPendingTap();
    h.removeCallbacks(settleRun); h.removeCallbacks(autoCloseRun); h.removeCallbacks(geoRun);
    h.removeCallbacks(showRun);
    h.removeCallbacks(noHostRun); h.removeCallbacks(recoverRun); h.removeCallbacks(reshowRun);
    h.removeCallbacks(callCheckRun);
    pendingGeo = ""; noHostRetry = 0;
    prefs.edit().putBoolean("notic_paused", false).apply();
    closePopup(false, false);
    // UX: gỡ pill ngay khi tắt màn để lần bật màn / mở khoá tiếp theo luôn chạy animation sinh từ tâm
    if (pill != null) { closing = false; reopen = false; removePill(); }
}
public boolean interceptGesture(String key) {
    if (pill == null || pill.hidden || closing || items.isEmpty() || !statusVisible || key == null || hostKey == null) return false;
    if (key.equals(hostKey + "_dtap")) { cancelPendingTap(); showPopup(true, false); return true; }
    if (key.equals(hostKey + "_tap")) {
        long now = SystemClock.elapsedRealtime();
        if (pendingTap != null && now - lastTapMs <= DTAP_MS) {
            cancelPendingTap();
            showPopup(true, false);
            scheduleAutoClose();          // UX: 2 chạm cũng auto thu gọn sau vài giây
        } else {
            lastTapMs = now;
            cancelPendingTap();
            pendingTap = () -> { pendingTap = null; showPopup(false, false); scheduleAutoClose(); };
            h.postDelayed(pendingTap, DTAP_MS);
        }
        return true;
    }
    if (key.equals(hostKey + "_long")) { openCurrentApp(); return true; }
    if (key.equals(hostKey + "_left")) { step(1); return true; }
    if (key.equals(hostKey + "_right")) { step(-1); return true; }
    return false;
}

private void cancelPendingTap() {
    if (pendingTap != null) { h.removeCallbacks(pendingTap); pendingTap = null; }
}
    private void step(int d) {
        int n = items.size();
        if (n < 2) return;
        cur = ((cur + d) % n + n) % n;
        announce();
    }

    private void openItem(Item it) {
        if (isDemo(it)) return;
        try {
            if (it.intent != null) it.intent.send();
            else if (it.pkg != null) {
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(it.pkg);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(li); }
            }
        } catch (Exception ignored) {}
    }
    public boolean testDemo() {
    if (!prefs.getBoolean("notic_en", false) || !hasHost()) return false;
    for (int i = 2; i >= 1; i--) {
        Item a = new Item();
        a.key = "demo_" + i; a.pkg = ctx.getPackageName(); a.appName = "Edge Bar";
        a.title = "Notic demo " + i;
        a.text = "Tap: expand · 2 taps: all · Long: open · Swipe L/R: switch · Swipe up: hide";
        a.sig = a.title + SystemClock.elapsedRealtime();
        post(a, false, false);
    }
    return true;
}
public void clearDemo() {
    boolean ch = false;
    for (Item x : new ArrayList<>(items)) if (x.key != null && x.key.startsWith("demo_")) { items.remove(x); ch = true; }
    if (ch) afterChange();
}
    private void openCurrentApp() {
        if (items.isEmpty()) return;
        Item it = items.get(Math.min(cur, items.size() - 1));
        if (isDemo(it)) return;
        openItem(it);
        if (!it.media) items.remove(it);
        closePopup(false, true);   // đóng có animation, không "biến mất cái bụp" lúc nhấn giữ
        afterChange();
    }

    public void relayout() {
        cachePrefs();   // service gọi relayout() mỗi khi key notic_* đổi -> nơi duy nhất cần nạp lại cache
        syncVisible();   // UX: công tắc "ẩn khi..." và mọi thay đổi trạng thái có hiệu lực ngay, không chờ sự kiện sau
        if (!prefs.getBoolean("notic_en", false)) {
            unregisterSystemEvents();   // [P5]
            h.removeCallbacks(geoRun); h.removeCallbacks(noHostRun); pendingGeo = "";
            if (!items.isEmpty() || pill != null) { items.clear(); closePopup(false, false); removePill(); }
            return;
        }
        if (closing) return;
        if (!sysRegistered) registerSystemEvents();   // [P5] bật Notic -> mới đăng ký receiver
        if (popup != null && bindHost()) { renderPopup(); placePopup(); }
        if (items.isEmpty() || !screenOn) return;
        if (!bindHost()) {
            h.removeCallbacks(noHostRun);
            h.postDelayed(noHostRun, 300);
            return;
        }
        h.removeCallbacks(noHostRun);
        noHostRetry = 0;
                String s = geoKey();
        if (pill != null && s.equals(lastGeo)) {
            hostChanged = false;
            pillHostKey = hostKey;
            h.removeCallbacks(geoRun); pendingGeo = "";
            pill.invalidate();
            applyVisibility();
            return;
        }
        if (!s.equals(pendingGeo)) {
            pendingGeo = s; geoTries = 0;
            h.removeCallbacks(geoRun);
            if (previewForce) geoRun.run();          // kéo slider: pill bám tay ngay, không debounce
            else h.postDelayed(geoRun, 80);
        }
    }
    private String geoKey() { return (int) hl + "|" + (int) ht + "|" + (int) hr + "|" + (int) hb + "|" + (int) px + "|" + (int) cy + "|" + (int) ew + "|" + (int) pillH + "|" + badgeLeft + "|" + cRadPct + "|" + hostKey; }

    private void geoSettle() {
        if (items.isEmpty() || !prefs.getBoolean("notic_en", false)) { pendingGeo = ""; return; }
        if (!bindHost()) { pendingGeo = ""; h.removeCallbacks(noHostRun); h.postDelayed(this.noHostRun, 700); return; }
        h.removeCallbacks(noHostRun);
        String s = geoKey();
        if (!previewForce && !s.equals(pendingGeo) && geoTries++ < 8) { pendingGeo = s; h.postDelayed(geoRun, 80); return; }   // còn đang trôi -> đo lại (tối đa ~640ms)
        pendingGeo = ""; geoTries = 0;
        applyGeo(s);
    }
    private static final String[] N_BARS = {"b_c","r","l","r_u","r_c","r_d","t_c","t_r","t_l","l_u","l_c","l_d"};
private static final int[] N_GRAV = {
    Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
    Gravity.TOP|Gravity.RIGHT, Gravity.CENTER_VERTICAL|Gravity.RIGHT, Gravity.BOTTOM|Gravity.RIGHT,
    Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT,
    Gravity.TOP|Gravity.LEFT, Gravity.CENTER_VERTICAL|Gravity.LEFT, Gravity.BOTTOM|Gravity.LEFT
};
/** Toạ độ màn hình của bar tính thuần từ prefs (không đo view -> không phụ thuộc thời điểm layout). null nếu là corner. */
private float[] barRectFromPrefs(String key, float W, float H) {
    if (key == null || key.length() < 6) return null;
    String member = key.substring(5);                 // "lock_r_u" -> "r_u"
    int idx = -1;
    for (int i = 0; i < N_BARS.length; i++) if (N_BARS[i].equals(member)) { idx = i; break; }
    if (idx < 0) return null;                         // corner_* -> dùng cách đo cũ
    int w = prefs.getInt(key + "_w", 300), hh = prefs.getInt(key + "_h", 60);
    int x = prefs.getInt(key + "_x", 0), y = prefs.getInt(key + "_y", 0);
    int g = N_GRAV[idx];
    int hg = g & Gravity.HORIZONTAL_GRAVITY_MASK, vg = g & Gravity.VERTICAL_GRAVITY_MASK;
    float left = hg == Gravity.LEFT ? x : hg == Gravity.RIGHT ? W - w - x : (W - w) / 2f + x;
    float top  = vg == Gravity.TOP  ? y : vg == Gravity.BOTTOM ? H - hh - y : (H - hh) / 2f + y;
    return new float[]{left, top, left + w, top + hh};
}    
    private float[] rectFromLayoutParams(View v, float W, float H) {
    if (!(v.getLayoutParams() instanceof WindowManager.LayoutParams)) return null;
    WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
    if (p.width <= 1 || p.height <= 1) return null;
    int hg = p.gravity & Gravity.HORIZONTAL_GRAVITY_MASK, vg = p.gravity & Gravity.VERTICAL_GRAVITY_MASK;
    float left = hg == Gravity.LEFT ? p.x : hg == Gravity.RIGHT ? W - p.width - p.x : (W - p.width) / 2f + p.x;
    float top = vg == Gravity.TOP ? p.y : vg == Gravity.BOTTOM ? H - p.height - p.y : (H - p.height) / 2f + p.y;
    return new float[]{left, top, left + p.width, top + p.height};
}

private boolean bindHost() {
    Object[] hs = hostFinder.get();
    if (hs == null) return false;
    View v = (View) hs[0];
    if (hostView != v) { restoreHost(); hostView = v; hostChanged = true; }
    hostKey = (String) hs[1];
    if (v.getVisibility() != View.VISIBLE || !v.isAttachedToWindow()) return false;
    DisplayMetrics dm = new DisplayMetrics();
    try { wm.getDefaultDisplay().getRealMetrics(dm); } catch (Exception e) { dm = ctx.getResources().getDisplayMetrics(); }
    scrW = dm.widthPixels;
    scrH = dm.heightPixels;
int[] loc = new int[2];
v.getLocationOnScreen(loc);
hl = loc[0];
ht = loc[1];
hr = hl + v.getWidth();
hb = ht + v.getHeight();
if (hr <= hl || hb <= ht) {
    /* View chưa kịp layout (width/height = 0 lúc service vừa start) → fallback
       sang 2 cách cũ. updateVisibility() sẽ tự gọi noticEngine.relayout() lại
       sau vài chục ms nên lần 2 sẽ có toạ độ thật. */
    float[] rc = rectFromLayoutParams(v, scrW, scrH);
    if (rc == null) rc = barRectFromPrefs(hostKey, scrW, scrH);
    if (rc == null) return false;
    hl = rc[0]; ht = rc[1]; hr = rc[2]; hb = rc[3];
}
    float wP = prefs.getInt("notic_w_pct", 100) / 100f, hP = prefs.getInt("notic_h_pct", 100) / 100f;
    float barW = hr - hl, barH = hb - ht;
    pillH = Math.min(barH, Math.max(8 * dens, Math.min(barH, 44 * dens) * hP));
    badgeLeft = prefs.getBoolean("notic_badge_left", false);
    float maxW = Math.max(pillH, Math.min(barW, scrW - 16 * dens) - badgeExtra());
    ew = Math.min(maxW, pillH * 4.0f * wP);   // một độ dài duy nhất cho MỌI loại thông báo
    ewApp = ew;
    cw = ew;
    float lo = hl + ew / 2f + (badgeLeft ? badgeExtra() : 0f);
    float hi = hr - ew / 2f - (badgeLeft ? 0f : badgeExtra());
    float cxw = (hl + hr) / 2f + prefs.getInt("notic_off_x", 0);
    px = lo > hi ? (hl + hr) / 2f : Math.max(lo, Math.min(hi, cxw));
    float cyw = (ht + hb) / 2f + prefs.getInt("notic_off_y", 0);
    cy = Math.max(ht + pillH / 2f, Math.min(hb - pillH / 2f, cyw));
    barR = hostKey.contains("corner") ? 40f : prefs.getInt((hostKey.startsWith("lock_") ? "lock_" : "home_") + "bar_radius", 24);
    return true;
}
    private void restoreHost() {
        if (hostView != null) {
            hostView.animate().cancel();
            hostView.setAlpha(1f);
            hostView = null;
        }
    }

    private boolean createPill() {
        final PillView pv = new PillView(ctx);
        pillLp = new WindowManager.LayoutParams((int) scrW, (int) (hb - ht), wmType, BASE_FLAGS | NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        pillLp.gravity = Gravity.TOP | Gravity.LEFT;
        try { wm.addView(pv, pillLp); } catch (Exception e) { return false; }
        pill = pv;
        statusVisible = true; insetsVisible = true;
        pillHostKey = hostKey;
        fullWin = true;
        expanded = false;
        return true;
    }
    public void setShadeOpen(boolean open) {
        if (shadeOpen == open) return;
        shadeOpen = open;
        syncVisible();
    }

    /* UX: EdgeBarService gọi khi PIN/camera/call/QS xuất hiện/biến mất → tạm ẩn
       notic giống cách thu gọn, không xoá item, hiện lại khi overlay gone. */
    public void setSecureOverlay(boolean on) {
        if (secureOverlay == on) return;
        secureOverlay = on;
        syncVisible();
    }

    private static boolean isDemo(Item it) { return it.key != null && it.key.startsWith("demo_"); }

public void setPreview(boolean on) {
    if (previewForce == on) return;
    previewForce = on;
    syncVisible();
}

public boolean previewStart() {
    if (!prefs.getBoolean("notic_en", false) || !hasHost()) return false;
    setPreview(true);
    return testDemo();
}

public void endPreview() {
    previewForce = false;
    closePopup(false, true);
    clearDemo();
    syncVisible();
}
public boolean previewToggle() {
    if (popup != null) { closePopup(false, true); return true; }
    final boolean fresh = pill == null;
    if (!previewStart()) return false;
    h.postDelayed(() -> { if (previewForce && popup == null && pill != null) showPopup(false, true); }, fresh ? 650 : 0);
    return true;
}

private void clearAll() {
    List<Item> all = new ArrayList<>(items);
    items.clear();
    for (Item x : all) {
        unbindMedia(x);
        // media chỉ gỡ khỏi Notic, không cancel notification nhạc của hệ thống
        if (!x.transientItem && !x.media && !isDemo(x) && x.key != null && EdgeBarNotificationListener.cancel(x.key)) markSelfCancel(x.key);
    }
    closePopup(false, true);
    afterChange();
}
    private void winFull() {
    if (pill == null) return;
    // Cửa sổ phải phủ CẢ vị trí cũ lẫn mới của pill, nếu không khung đầu bị cắt -> giật khi Lock <-> Home
    float y0 = (float) Math.floor(Math.min(Math.min(ht, cy - pillH / 2f), pill.ccy - pill.h / 2f)) - 2;
    float y1 = (float) Math.ceil(Math.max(Math.max(hb, cy + pillH / 2f), pill.ccy + pill.h / 2f)) + 2;
    pillLp.x = 0;
    pillLp.y = (int) y0;
    pillLp.width = (int) scrW;
    pillLp.height = (int) (y1 - y0);
    pillLp.flags |= NOT_TOUCHABLE;
    pill.ox = 0;
    pill.oy = y0;
    fullWin = true;
    try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
}
    private float expW() { return ew; }
private float pillRad() { return pillH / 2f * cRadPct / 100f; }
    private void winFit() {
        if (pill == null) return;
        float sw = ew + 4 * dens, sh = pillH + 4 * dens;
        int x = (int) Math.floor(px - sw / 2f) - (badgeLeft ? (int) Math.ceil(badgeExtra()) : 0);
int y = (int) Math.floor(cy - sh / 2f);
int w = (int) Math.ceil(sw + badgeExtra()), hh = (int) Math.ceil(sh);
        int flags = statusVisible ? (pillLp.flags & ~NOT_TOUCHABLE) : (pillLp.flags | NOT_TOUCHABLE);
        pill.ox = x;
        pill.oy = y;
        boolean same = !fullWin && pillLp.x == x && pillLp.y == y && pillLp.width == w && pillLp.height == hh && pillLp.flags == flags;
        fullWin = false;
        if (same) return;
        pillLp.x = x; pillLp.y = y; pillLp.width = w; pillLp.height = hh; pillLp.flags = flags;
        try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
    }
    private void removePill() {
        h.removeCallbacks(closingGuardRun);
        closing = false; reopen = false;
        h.removeCallbacks(settleRun);
        h.removeCallbacks(geoRun);
        pendingGeo = "";        
        if (pill != null) {
            pill.cancelAnim();
            try { wm.removeView(pill); } catch (Exception ignored) {}
            pill = null;
        }
        expanded = false;
        lastGeo = "";
        pillHostKey = null;
        restoreHost();
    }
    private void announce() {
    if (items.isEmpty() || !screenOn) return;
    if (closing) { reopen = true; return; }
if (!statusVisible) return;
if (!bindHost()) {   // UX: chưa có host (đang đổi Lock<->Home) thì tự thử lại, không bỏ cuộc
    h.removeCallbacks(noHostRun); h.postDelayed(noHostRun, 300);
    return;
}
    userHidden = false;
    h.removeCallbacks(settleRun);
    Item it = items.get(Math.min(cur, items.size() - 1));
    final float tw = ew;
final boolean fresh = pill == null || pill.hidden || replayNext;
replayNext = false;
    if (pill == null) {
        if (!createPill()) return;
        hostChanged = false;
    }
    final PillView pv = pill;
    if (fresh) {
        pv.hidden = false;
        expanded = false;
        pv.setContent(it, items.size(), tw);
        lastGeo = geoKey();
        pv.set(px, cy, 2f, 2f, 1f, 1f, 0f);
        fadeNext = true;
        winFit();
        applyVisibility();
        /* UX: Overshoot 1.3 gây "bật" mạnh ở đỉnh → cảm giác khựng. Đổi sang EMPH
           và giãn duration 240→340 / 340→480 ms để chuyển động mềm, không giật
           trên Adreno 540 của Pixel 2XL. */
        pv.go(px, cy, pillH, pillH, pillH / 2f, 1f, 0f, aDur(340), EMPH,
            () -> { if (pill == pv) pv.go(px, cy, tw, pillH, pillRad(), 1f, 1f, aDur(480), EMPH, this::afterExpand); });
    } else {
        boolean sameState = (expanded || pv.towardsExpanded()) && Math.abs(pv.targetW() - tw) < 1f;
        pv.setContent(it, items.size(), tw);
        if (sameState) pv.invalidate();
        else {
            if (fullWin || tw + 4 * dens > pillLp.width) winFull();
            pv.go(px, cy, tw, pillH, pillRad(), 1f, 1f, aDur(300), EMPH, this::afterExpand);
        }
    }
}
private final Runnable closingGuardRun = () -> {
    if (!closing) return;
    boolean again = reopen;
    removePill();   // reset closing/reopen, gỡ cửa sổ, trả alpha bar
    if (again && !items.isEmpty() && !userHidden && statusVisible) announce();
};
// Co lại: viên thuốc → tròn → chấm ở tâm. keepWindow=true: giữ cửa sổ rỗng để còn nghe insets.
private void shrinkRemove(final boolean thenReopen, final boolean keepWindow) {
    if (pill == null) { if (thenReopen) announce(); return; }
    if (pill.hidden) { if (!keepWindow) removePill(); return; }
    if (closing) { reopen |= thenReopen; return; }
    closing = true; reopen = thenReopen;
    h.removeCallbacks(closingGuardRun);
    h.postDelayed(closingGuardRun, aDur(1500));   // UX: guard phải scale theo slider, nếu cứng 1500ms thì slider >150% sẽ bị gỡ giữa animation, mất luôn hiệu ứng đóng
    h.removeCallbacks(settleRun);
    expanded = false;
    final PillView pv = pill;
    if (hostView != null) hostView.animate().alpha(1f).setStartDelay(aDur(150)).setDuration(aDur(320)).start();
    pv.go(px, cy, pillH, pillH, pillH / 2f, 1f, 0f, aDur(220), EMPH_IN, () -> {
        if (pill != pv) return;
        pv.animate().alpha(0f).setDuration(aDur(170)).start();
        pv.go(px, cy, 2f, 2f, 1f, 1f, 0f, aDur(170), EMPH_IN, () -> {
            if (pill != pv) return;
            h.removeCallbacks(closingGuardRun);
            boolean again = reopen;
            closing = false; reopen = false;
            if (keepWindow) { pv.hidden = true; pv.invalidate(); } else removePill();
            if (again && !items.isEmpty() && !userHidden) announce();
        });
    });
}

private void collapse() { shrinkRemove(false, false); }
private void afterChange() {
    syncVisible();
    if (cur >= items.size()) cur = Math.max(0, items.size() - 1);
    if (items.isEmpty()) {
        h.removeCallbacks(settleRun);
        if (popup != null) closePopup(false, true);   // end callback sẽ gọi collapse()
        else collapse();
        return;
    }
    if (pill != null && !pill.hidden) {
        pill.setContent(items.get(cur), items.size(), ew);
        pill.invalidate();
    }
    if (popup != null) renderPopup();
}

private void afterPopupGone() {
    if (pill != null) pill.setVisibility(View.VISIBLE);
    if (items.isEmpty()) collapse();
}
private final Runnable showRun = () -> { if (wantVisible()) applyVisible(true); };
private boolean wantVisible() {
    if (suppressed) return false;
    if (previewForce) return true;
    if (shadeOpen && hideOn("shade", true)) return false;
    if (secureOverlay && hideOn("pin", true)) return false;
    if (inCall && hideOn("call", true)) return false;
    if (!camBusy.isEmpty() && hideOn("cam", false)) return false;
    if (micOn && hideOn("mic", false)) return false;
    return !appHidden;
}
private void syncVisible() {
    h.removeCallbacks(showRun);
    if (!wantVisible()) { applyVisible(false); return; }   // ẩn: tức thì
    if (statusVisible) return;
    // UX: hiện lại phải ổn định 200ms mới làm, chống nháy khi PIN/shade/call dao động
    if (previewForce) applyVisible(true); else h.postDelayed(showRun, aDur(200));
}
private void applyVisible(boolean v) {
    if (v == statusVisible) return;
    statusVisible = v;
    h.post(() -> {
        if (statusVisible != v) return;   // đã đổi ý trong lúc chờ
        if (!v) { closePopup(false, false); shrinkRemove(false, true); }
        else if (!items.isEmpty() && !userHidden) announce();
    });
}
private void applyVisibility() {
    if (pill == null || pill.hidden) return;
    if (fadeNext) {
        fadeNext = false;
        fadeUntil = SystemClock.uptimeMillis() + aDur(220);
        if (hostView != null) { hostView.animate().cancel(); hostView.animate().setStartDelay(0).alpha(0f).setDuration(aDur(200)).start(); }
    } else if (!closing && hostView != null && SystemClock.uptimeMillis() >= fadeUntil) {
        hostView.animate().cancel();
        hostView.setAlpha(0f);
    }
    pill.animate().cancel(); // UX: huỷ fade-out dở của lần thu gọn trước, nếu không pill hiện lại sẽ bị trong suốt
    pill.setAlpha(1f);
    pill.invalidate();
    if (!fullWin) {
        boolean want = !statusVisible;
        if (((pillLp.flags & NOT_TOUCHABLE) != 0) != want) {
            if (want) pillLp.flags |= NOT_TOUCHABLE; else pillLp.flags &= ~NOT_TOUCHABLE;
            try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
        }
    }
}

private void applyGeo(String s) {
    if (closing) return;
    boolean hostSwitch = pillHostKey != null && !pillHostKey.equals(hostKey);
    pillHostKey = hostKey;
    hostChanged = false;
    if (pill == null) { lastGeo = s; if (!userHidden && !items.isEmpty()) announce(); return; }
    if (pill.hidden) { lastGeo = s; return; }
    if (s.equals(lastGeo)) { pill.invalidate(); applyVisibility(); return; }
    boolean first = lastGeo.isEmpty();
    lastGeo = s;
        if (hostSwitch && !previewForce) {
        if (popup != null && popupLp != null) {
            slidePopupToNewHost();
        } else {
            shrinkRemove(true, false);
        }
        return;
    }
    final boolean want = expanded || pill.towardsExpanded() || pill.animating();
    float tw = want ? ew : pillH;
    if (first || previewForce) {
        pill.set(px, cy, tw, pillH, pillRad(), 1f, want ? 1f : 0f);
        winFit();
    } else {
        winFull();
        pill.go(px, cy, tw, pillH, pillRad(), 1f, want ? 1f : 0f, 320, EMPH, () -> { if (want) afterExpand(); else winFit(); });
    }
    pill.invalidate();
    applyVisibility();
}
private void slidePopupToNewHost() {
    if (popup == null || popupLp == null) return;
    if (morphAnim != null) { morphAnim.cancel(); morphAnim = null; popMorphing = false; }

    final int oldX = popupLp.x, oldY = popupLp.y;
    int pw = popW();
    popupLp.width = pw;
    popup.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec((int) scrH, View.MeasureSpec.AT_MOST));
    final int newX = popX(pw);
    final int newY = popY(popup.getMeasuredHeight());

    if (pill != null) {
        pill.set(px, cy, ew, pillH, pillRad(), 1f, 1f);
        winFit();
    }

    final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
    va.setDuration(aDur(280));
    va.setInterpolator(EMPH);
    va.addUpdateListener(a -> {
        float f = (float) a.getAnimatedValue();
        popupLp.x = Math.round(oldX + (newX - oldX) * f);
        popupLp.y = Math.round(oldY + (newY - oldY) * f);
        try { wm.updateViewLayout(popup, popupLp); } catch (Exception ignored) {}
    });
    va.start();
}
    private void afterExpand() {
    expanded = true;
    winFit();
}
    private void settle() { /* pill không còn tự thu nhỏ */ }
    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive())
                pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "EdgeBar:Notic").acquire(3000);
        } catch (Exception ignored) {}
    }

    private void playSound() {
        try {
            long now = SystemClock.elapsedRealtime();
            if (now - lastSoundMs < 2000) return;   // [P3] tối đa 1 tiếng / 2 giây
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {                        // [P3] đang gọi / đang đàm thoại (Grab, Zalo...) -> tuyệt đối không kêu
                int md = am.getMode();
                if (md == AudioManager.MODE_IN_CALL || md == AudioManager.MODE_IN_COMMUNICATION || md == AudioManager.MODE_RINGTONE) return;
            }
            lastSoundMs = now;           
            if (ringtone != null) ringtone.stop();
            ringtone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void showPopup(boolean history, boolean force) {
    if (items.isEmpty() || pill == null || pill.hidden || !statusVisible || !bindHost()) return;
if (closing || pill.animating()) { if (force) { final boolean hh = history; h.postDelayed(() -> showPopup(hh, true), 250); } return; }
    if (popup != null) {
        if (force) { renderPopup(); placePopup(); return; }
        if (!history || popupHistory) { closePopup(false, true); return; }
        popupHistory = true;
        if (popupHead != null) popupHead.setVisibility(View.VISIBLE);
        renderPopup(); placePopup();
        scheduleAutoClose();   // UX: đổi sang lịch sử cũng tự thu gọn sau vài giây
        return;
    }
    if (!force && SystemClock.elapsedRealtime() - lastCloseMs < 400) return;
    popupHistory = history;
    openPopup();
    scheduleAutoClose();   // UX: mọi đường mở popup đều tự thu gọn sau notic_hold_sec giây
}
private int popW() {   // UX: 100% = gần full màn (lề 12dp mỗi bên); trước đây cap 420dp nên kéo >92% không đổi
    float full = scrW - dp(12);
    return Math.round(Math.max(dp(160), Math.min(full, full * prefs.getInt("notic_pop_w_pct", 100) / 100f)));
}
private int popX(int pw) { return Math.round((scrW - pw) / 2f); }   // UX: luôn cách đều 2 mép; notic lệch bao nhiêu cũng không kéo bảng lệch theo
private int popY(int ph) {
    int g = dp(cPopGap);
    int y;
    if (g == 0) y = cy < scrH / 2f ? (int) (cy - pillH / 2f) : (int) (cy + pillH / 2f - ph);
    else y = cy < scrH / 2f ? (int) (cy + pillH / 2f) + g : (int) (cy - pillH / 2f) - g - ph;
    return (int) Math.max(dp(4), Math.min(scrH - ph - dp(4), y));
}
private int popMaxH() { return Math.round(scrH * prefs.getInt("notic_pop_h_pct", 55) / 100f); }
private void openPopup() {
    swiping = false;
    h.removeCallbacks(settleRun);
    popRad = dp(prefs.getInt("notic_pop_rad", 26));
    MorphRoot root = new MorphRoot(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    popupBg = rounded(0xF22C2C2E, popRad);
    root.setBackground(popupBg);
    root.setPadding(dp(8), dp(8), dp(8), dp(8));
    LinearLayout head = new LinearLayout(ctx);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView ttl = new TextView(ctx);
    ttl.setText("Notifications");
    ttl.setTextColor(Color.WHITE);
    ttl.setTextSize(15f);
    ttl.setTypeface(Typeface.DEFAULT_BOLD);
    ttl.setPadding(dp(8), dp(6), dp(8), dp(6));
    ttl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    /* UX: tap vào "Notifications" = đóng popup — tránh trường hợp user tap
       nhầm chỗ này rồi tưởng app treo (trước đây không làm gì cả). */
    ttl.setOnClickListener(v -> closePopup(false, true));
    TextView clear = new TextView(ctx);
    clear.setText("Clear all");
    clear.setTextColor(Color.parseColor("#8AB4F8"));
    clear.setTextSize(12.5f);
    clear.setPadding(dp(8), dp(6), dp(4), dp(6));
    clear.setOnClickListener(v -> clearAll());
    head.addView(ttl);
    head.addView(clear);
    popupHead = head;
    root.addView(head);

    popupBody = new LinearLayout(ctx);
    popupBody.setOrientation(LinearLayout.VERTICAL);
    ScrollView sv = new ScrollView(ctx) {
        @Override protected void onMeasure(int wSpec, int hSpec) {
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(popMaxH(), MeasureSpec.AT_MOST));
        }
        /* UX: chỉ ăn MOVE dọc khi content THỰC SỰ overflow — nếu không, nhường
           gesture lên MorphRoot để vuốt-lên-thu-gọn và vuốt-ngang-xoá hoạt động. */
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!canScrollVertically(1) && !canScrollVertically(-1)) return false;
            return super.onInterceptTouchEvent(e);
        }
    };
    sv.addView(popupBody);
    sv.setVerticalFadingEdgeEnabled(true); sv.setFadingEdgeLength(dp(18)); sv.setVerticalScrollBarEnabled(false); // UX: mép mờ thay vì cắt vuông
    root.addView(sv, new LinearLayout.LayoutParams(-1, -2));
    int pw = popW();
    popup = root;
    popupSigLast = "";
    renderPopup(true);
    popupLp = new WindowManager.LayoutParams(pw, WindowManager.LayoutParams.WRAP_CONTENT, wmType, POPUP_FLAGS, PixelFormat.TRANSLUCENT);
    popupLp.gravity = Gravity.TOP | Gravity.LEFT;
    popupLp.x = popX(pw);
    root.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) scrH, View.MeasureSpec.AT_MOST));
    popupLp.y = popY(root.getMeasuredHeight());
    root.setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) {
            if (popMorphing) o.setRoundRect(Math.round(oL), Math.round(oT), Math.round(oR), Math.round(oB), oRad);
            else o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), popRad);
        }
    });
    root.setClipToOutline(true);
    setMorph(root, popupLp, popupBg, 0f);
    try { wm.addView(root, popupLp); } catch (Exception e) { popup = null; popupBody = null; popupHead = null; popupBg = null; return; }
    if (hideOn("expand", true) && pill != null) pill.setVisibility(View.INVISIBLE);   // UX: MorphRoot tự vẽ hình viên thuốc lúc morph nên nhìn như pill biến thành bảng; INVISIBLE còn tiết kiệm pin vì ngừng vẽ LED/đĩa. afterPopupGone() đã có sẵn setVisibility(VISIBLE) khi thu gọn
    final LinearLayout fr = root; final WindowManager.LayoutParams flp = popupLp; final GradientDrawable fbg = popupBg;
root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
    @Override public boolean onPreDraw() {
        fr.getViewTreeObserver().removeOnPreDrawListener(this);
        runMorph(fr, flp, fbg, true, () -> { popMorphing = false; fr.invalidateOutline(); fr.invalidate(); });
        final PillView pv = pill;
        return true;
    }
});
}

// f=0: đúng hình viên thuốc | f=1: toàn bảng
private void setMorph(View p, WindowManager.LayoutParams lp, GradientDrawable bgd, float f) {
    morphF = f;
p.invalidate();
    float pw0 = pill != null ? pill.w : ew;
    float l0 = px - pw0 / 2f - lp.x, t0 = cy - pillH / 2f - lp.y, r0 = l0 + pw0, b0 = t0 + pillH;
    float W = lp.width, H = p.getHeight() > 0 ? p.getHeight() : p.getMeasuredHeight();
    oL = l0 * (1 - f); oT = t0 * (1 - f); oR = r0 + (W - r0) * f; oB = b0 + (H - b0) * f;
    float pr = pillRad();
    oRad = pr + (popRad - pr) * f;
    popMorphing = true;
    p.invalidateOutline();
    float ca = Math.max(0f, Math.min(1f, (f - 0.3f) / 0.6f));
    if (p instanceof LinearLayout) for (int i = 0; i < ((LinearLayout) p).getChildCount(); i++) ((LinearLayout) p).getChildAt(i).setAlpha(ca);
    if (bgd != null) bgd.setAlpha((int) (255 * Math.min(1f, f * 3f)));
}
private void runMorph(final View p, final WindowManager.LayoutParams lp, final GradientDrawable bgd, boolean open, final Runnable end) {
    if (!open && morphAnim != null) morphAnim.cancel();
    final ValueAnimator va = ValueAnimator.ofFloat(open ? 0f : morphF, open ? 1f : 0f);
    morphAnim = va;
    p.setLayerType(View.LAYER_TYPE_HARDWARE, null);    
    va.setDuration(aDur(open ? 380 : 300));
    va.setInterpolator(open ? EMPH : EMPH_IN);
    va.addUpdateListener(a -> setMorph(p, lp, bgd, (float) a.getAnimatedValue()));
    va.addListener(new AnimatorListenerAdapter() {
        boolean cancelled;
        @Override public void onAnimationCancel(Animator a) { cancelled = true; }
        @Override public void onAnimationEnd(Animator a) {
            p.setLayerType(View.LAYER_TYPE_NONE, null);   // nhả bộ đệm GPU ngay khi xong để tiết kiệm RAM            
            if (morphAnim == va) morphAnim = null;
            if (!cancelled && end != null) end.run();
        }
    });
    va.start();
}
private void placePopup() {
    if (popup == null || popupLp == null) return;
    int pw = popW();
    popupLp.width = pw;
    popRad = dp(prefs.getInt("notic_pop_rad", 26));
    if (popupBg != null) popupBg.setCornerRadius(popRad);
    popup.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) scrH, View.MeasureSpec.AT_MOST));
    popupLp.x = popX(pw);
    popupLp.y = popY(popup.getMeasuredHeight());
    try { wm.updateViewLayout(popup, popupLp); } catch (Exception ignored) {}
    popup.invalidateOutline();
}
    private void closePopup(boolean markRead, boolean animate) {
        h.removeCallbacks(autoCloseRun);
        swiping = false; popupTouching = false; renderPending = false;
        final LinearLayout p = popup;
    if (p != null) {
        final WindowManager.LayoutParams lp = popupLp;
        final GradientDrawable bgd = popupBg;
        releaseIme(p);
        popup = null; popupBody = null; popupHead = null; popupBg = null;
        lastCloseMs = SystemClock.elapsedRealtime();
        if (animate && pill != null && lp != null)
            runMorph(p, lp, bgd, false, () -> {
                afterPopupGone();
                h.postDelayed(() -> { try { wm.removeView(p); } catch (Exception ignored) {} }, 48);   // viên thuốc hiện trước rồi mới gỡ bảng, tránh nháy
            });
        else { try { wm.removeView(p); } catch (Exception ignored) {} afterPopupGone(); }
    }
    if (markRead) {
        items.removeIf(x -> !x.media);
        afterChange();
    }
}
private String popupSigLast = "";
private boolean popupTouching, renderPending;
private String popupSig() {
    StringBuilder sb = new StringBuilder();
    sb.append(popupHistory).append(cur).append(cCardMode).append(cBorder).append(cSpin).append(cViz).append(cVizColor)
  .append(cPopA).append(cGlass).append(cPopGap);
    for (Item x : items) sb.append('|').append(x.key).append(x.sig).append(x.playing);
    return sb.toString();
}
private void renderPopup() { renderPopup(false); }
private void renderPopup(boolean force) {
    if (popupBody == null || items.isEmpty()) return;
    // UX: không dựng lại row khi ngón tay đang chạm/vuốt, nếu không view nhận DOWN bị huỷ và cử chỉ mất
    if (swiping || popupTouching) { renderPending = true; return; }
    if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) return;
    String sig = popupSig();
    if (!force && popupBody.getChildCount() > 0 && sig.equals(popupSigLast)) return;
    popupSigLast = sig;
    renderPending = false;
    popupBody.removeAllViews();
    Item curIt = items.get(Math.min(cur, items.size() - 1));
    if (popupHead != null) popupHead.setVisibility(popupHistory ? View.VISIBLE : View.GONE);
    if (popupBg != null) {
    if (popupHistory) {
        if (cCardMode == 3) { popupBg.setColor(Color.TRANSPARENT); popupBg.setStroke(0, 0); }
        else if (cCardMode >= 4) { popupBg.setColor(glassTint(0xFF14181E, cPopA)); popupBg.setStroke(0, 0); }
        else { popupBg.setColor(withA(0xFF121214, cPopA)); popupBg.setStroke(dp(2), withA(0xAA8AB4F8, cPopA)); }
        popup.setPadding(dp(10), dp(10), dp(10), dp(10));
    } else {
        popupBg.setColor(Color.TRANSPARENT); popupBg.setStroke(0, 0);
        popup.setPadding(0, 0, 0, 0);
    }
}
if (popupHistory) for (Item it : new ArrayList<>(items)) popupBody.addView(buildRow(it));
else popupBody.addView(buildRow(curIt));
}
    private void releaseIme(View p) {
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(p.getWindowToken(), 0);
            if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) {
                popupLp.flags |= NOT_FOCUSABLE;
                wm.updateViewLayout(p, popupLp);
            }
        } catch (Exception ignored) {}
    }

    private void makePopupFocusable() {
        if (popup == null || popupLp == null || (popupLp.flags & NOT_FOCUSABLE) == 0) return;
        popupLp.flags &= ~NOT_FOCUSABLE;
        popupLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        try { wm.updateViewLayout(popup, popupLp); } catch (Exception ignored) {}
    }

    private GradientDrawable rounded(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }
    private View buildRow(final Item it) { return buildRowCard(it); }
private View closeX(final Item it) {
    View v = new View(ctx) {
        final Paint pc = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override protected void onDraw(Canvas c) {
            float mx = getWidth() / 2f, my = getHeight() / 2f, r = Math.min(mx, my);
            pc.setStyle(Paint.Style.FILL); pc.setColor(0x33FFFFFF);
            c.drawCircle(mx, my, r, pc);
            pc.setStyle(Paint.Style.STROKE); pc.setStrokeWidth(2.2f * dens); pc.setStrokeCap(Paint.Cap.ROUND); pc.setColor(0xF2FFFFFF);
            float a = r * 0.36f;
            c.drawLine(mx - a, my - a, mx + a, my + a, pc);
            c.drawLine(mx + a, my - a, mx - a, my + a, pc);
        }
    };
    v.setOnClickListener(x -> dismissItem(it));
    return v;
}
    private LinearLayout buildRowCard(final Item it) {
        LinearLayout card = (it.call || !popupHistory) ? new LinearLayout(ctx) : new SwipeCard(ctx, it); // thẻ đơn do MorphRoot xử lý vuốt
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg(it, popupHistory ? dp(18) : popRad));
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        int vm = popupHistory ? dp(4) : 0;   // [P6]
        clp.setMargins(0, vm, 0, vm);
        card.setLayoutParams(clp);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (it.media && prefs.getBoolean("notic_art_spin", true)) {
    top.addView(new ArtView(ctx, it));
} else {
    ImageView iv = new ImageView(ctx);
    iv.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
    Bitmap sb = senderCircle(it);
if (sb == null && it.media) sb = circleArt(it);        // media tắt quay: đĩa tĩnh
if (sb == null && it.transientItem) sb = it.icon;      // huy hiệu sự kiện đã tròn sẵn
if (sb != null) {
        iv.setImageBitmap(sb);                       // tròn, không nền vuông
    } else {
        iv.setBackground(rounded(0xFF2C2C2E, dp(10)));
        iv.setClipToOutline(true);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap pic = loadLarge(it);
        if (pic != null) iv.setImageBitmap(pic);
        else if (it.icon != null) iv.setImageBitmap(it.icon);
    }
    top.addView(iv);
}
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(0, -2, 1f);
        colp.setMargins(dp(10), 0, dp(6), 0);
        col.setLayoutParams(colp);
        if (it.appName != null) {
            TextView t0 = new TextView(ctx);
            t0.setText(it.appName);
            t0.setTextColor(Color.parseColor("#8E8E93"));
            t0.setTextSize(10.5f);
            t0.setSingleLine(true);
            col.addView(t0);
        }
        TextView t1 = new TextView(ctx);
        t1.setText(it.title == null ? "" : it.title);
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(14f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t1);
        if (it.text != null && !it.text.isEmpty()) {
            TextView t2 = new TextView(ctx);
            t2.setText(it.text);
            t2.setTextColor(Color.parseColor("#B0B3B8"));
            t2.setTextSize(12f);
            t2.setMaxLines(2);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t2);
        }
        top.addView(col);       
        if (it.media && prefs.getBoolean("notic_viz_en", true)) top.addView(new VizView(ctx, it)); // visualizer nằm sát phải cùng
        if (!it.call) {
    LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(dp(36), dp(36));
    xl.gravity = Gravity.TOP;
    xl.setMargins(dp(6), 0, 0, 0);
    top.addView(closeX(it), xl);
}
        top.setOnClickListener(v -> {
            /* UX: nếu user đang cố swipe trên card (đã vượt slop) thì bỏ qua
               click của top — tránh mở app ngoài ý muốn khi swipe chéo không
               đủ để SwipeCard intercept. */
            View p = (View) v.getParent();
            if (p instanceof SwipeCard && ((SwipeCard) p).userMoved) return;
            openItem(it);
            if (!it.media) items.remove(it);
            closePopup(false, true);
            afterChange();
        });
        card.addView(top);
        if (it.call) card.addView(buildCallControls(it));
else if (it.media) card.addView(buildMediaControls(it));
else { if (it.reply != null || canFallbackReply(it)) card.addView(buildReplyBar(it)); card.addView(buildItemActions(it)); }
return card;
    }
    private Notification.Action findReadAction(Item it) {
    if (it.actions == null) return null;
    for (Notification.Action a : it.actions) {
        if (a == null || a.actionIntent == null) continue;
        if (Build.VERSION.SDK_INT >= 28 && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) return a;
        String t = a.title == null ? "" : a.title.toString().toLowerCase(Locale.ROOT);
        if (t.contains("mark as read") || t.contains("đánh dấu đã đọc")) return a;
    }
    return null;
}

private TextView actChip(String label, View.OnClickListener l) {
    TextView t = new TextView(ctx);
    t.setText(label);
    t.setTextColor(Color.parseColor("#8AB4F8"));
    t.setTextSize(12f);
    t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setPadding(dp(12), dp(6), dp(12), dp(6));
    t.setOnClickListener(l);
    return t;
}

private View buildItemActions(final Item it) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.END);
    row.setPadding(0, dp(4), 0, 0);
    final Notification.Action read = findReadAction(it);
    if (read != null) row.addView(actChip("Mark as read", v -> { try { read.actionIntent.send(); } catch (Exception ignored) {} dismissItem(it); }));
    if (it.snooze != null) row.addView(actChip("Snooze", v -> { try { it.snooze.actionIntent.send(); } catch (Exception ignored) {} dismissItem(it); }));
    return row;   // UX: bỏ chip Dismiss — thay bằng nút "^" thu gọn ở head (Patch 2B)
}
private class ChevronView extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    ChevronView(Context c) {
        super(c);
        p.setColor(Color.parseColor("#8AB4F8")); p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * dens); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }
    @Override protected void onDraw(Canvas c) {   // UX: tự vẽ nên dấu ^ luôn nằm đúng tâm, không lệch theo font
        float mx = getWidth() / 2f, my = getHeight() / 2f, a = 6f * dens;
        c.drawLine(mx - a, my + a / 2f, mx, my - a / 2f, p);
        c.drawLine(mx, my - a / 2f, mx + a, my + a / 2f, p);
    }
}

private View chevron() {
    ChevronView v = new ChevronView(ctx);
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(32)));
    v.setOnClickListener(x -> closePopup(false, true));
    return v;
}

private View buildSingleFooter(final Item it) {
    LinearLayout f = new LinearLayout(ctx);
    f.setOrientation(LinearLayout.HORIZONTAL);
    f.setGravity(Gravity.CENTER_VERTICAL);
    f.setBackground(rounded(0xF22C2C2E, dp(16)));
    f.setPadding(dp(10), dp(2), dp(4), dp(2));
    TextView dis = new TextView(ctx);
    dis.setText("Dismiss");
    dis.setTextColor(Color.parseColor("#8AB4F8"));
    dis.setTextSize(12.5f);
    dis.setPadding(dp(8), dp(6), dp(8), dp(6));
    dis.setOnClickListener(v -> dismissItem(it));   // chỉ xoá đúng thông báo đang mở
    f.addView(dis);
    f.addView(chevron());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.gravity = Gravity.END;
    lp.setMargins(0, dp(6), dp(14), dp(8));   // lề đủ rộng để không bị bo góc cửa sổ cắt
    f.setLayoutParams(lp);
    return f;
}
private Drawable cardBg(Item it, float r) {
    int m = cCardMode;
    if (m == 3) return rounded(Color.TRANSPARENT, r);   // Clear: không viền
    if (m == 4 || m >= 5) {
    int rgb = (m >= 5 && m - 5 < CARD_HEX.length) ? Color.parseColor(CARD_HEX[m - 5]) : 0xFF14181E;
    return new GlassDrawable(r, glassTint(rgb, cPopA), cPopA / 100f);
}
    int base;
    if (m == 1) base = blend(appColor(it), Color.BLACK, 0.65f);
    else if (m == 2) base = blend(CARD_DARK, appColor(it), 0.20f);
    else if (it.media && m == 0) base = blend(appColor(it), Color.BLACK, 0.70f);
    else base = CARD_DARK;
    GradientDrawable g = rounded(withA(base, cPopA), r);
    if (cBorder) g.setStroke(dp(1.5f), appColor(it));   // mọi thẻ, mọi app, mọi chế độ màu
    else if (it.msg && !it.call) g.setStroke(dp(1.8f), BADGE_BLUE);
    return g;
}
    private void dismissItem(Item it) {
    if (!items.remove(it)) return;
    unbindMedia(it);
    if (!it.transientItem && !it.media && !isDemo(it) && it.key != null && EdgeBarNotificationListener.cancel(it.key)) markSelfCancel(it.key);
    if (popup != null && !popupHistory) closePopup(false, true);
    afterChange();
    placePopup();
}
    private class MorphRoot extends LinearLayout {
    final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG), sp2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Glass glass = new Glass(1.6f * dens);
    final RectF rr = new RectF();
    MorphRoot(Context c) { super(c); setWillNotDraw(false); sp2.setStyle(Paint.Style.STROKE); sp2.setStrokeWidth(1.6f * dens); }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && event.getAction() == android.view.KeyEvent.ACTION_UP) {
            if (popup != null) releaseIme(popup);
            closePopup(false, true);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_OUTSIDE) {
    if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) { releaseIme(popup); return true; }   // UX: đang gõ -> chạm ngoài chỉ hạ bàn phím, lần 2 mới đóng (và chữ vẫn nằm trong drafts)
    closePopup(false, true);
    return true;
}
        if (a == MotionEvent.ACTION_MOVE && popupLp != null) {
            float rx = e.getRawX(), ry = e.getRawY();
            float slop = dp(40);
            if (rx < popupLp.x - slop || rx > popupLp.x + popupLp.width + slop
                    || ry < popupLp.y - slop || ry > popupLp.y + popupLp.height + slop) {
                if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) releaseIme(popup);
                closePopup(false, true);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
    @Override protected void dispatchDraw(Canvas c) {
    if (popMorphing) {
        float a = 1f - Math.max(0f, Math.min(1f, (morphF - 0.35f) / 0.5f));
        if (a > 0f) {
            rr.set(oL, oT, oR, oB);
            int pf = pillFill();
            fp.setColor(pf); fp.setAlpha(Math.round(Color.alpha(pf) * a));
            c.drawRoundRect(rr, oRad, oRad, fp);
            if (cBorder && cPillMode == PM_SOLID && !items.isEmpty()) {
                sp2.setColor(appColor(items.get(Math.min(cur, items.size() - 1)))); sp2.setAlpha((int) (255 * a));
                float in = sp2.getStrokeWidth() / 2f;
                rr.inset(in, in);
                c.drawRoundRect(rr, Math.max(0f, oRad - in), Math.max(0f, oRad - in), sp2);
            }
        }
    }
    if (popupHistory && cCardMode >= 4) {
    rr.set(0, 0, getWidth(), getHeight());
    glass.draw(c, rr, popRad, cGlass, (popMorphing ? Math.min(1f, morphF * 3f) : 1f) * cPopA / 100f);
}
    super.dispatchDraw(c);
}
    private float swX0, swY0, swDx; private boolean swDrag, swDown; private VelocityTracker swVt;
private final int swSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
@Override public boolean dispatchTouchEvent(MotionEvent e) {
    int act = e.getActionMasked();
    if (act == MotionEvent.ACTION_DOWN) { popupTouching = true; h.removeCallbacks(autoCloseRun); }
    boolean res = dispatchTouchInner(e);
    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
        popupTouching = false;
        if (renderPending && !swiping) h.post(NoticEngine.this::renderPopup);
        if (popup != null) scheduleAutoClose();   // tính lại từ lúc nhả tay
    }
    return res;
}
private boolean dispatchTouchInner(MotionEvent e) {
    View card = (popupHistory || popupBody == null || popupBody.getChildCount() == 0 || items.isEmpty()
        || (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0)) ? null : popupBody.getChildAt(0);
    if (card == null || items.get(Math.min(cur, items.size() - 1)).call) return super.dispatchTouchEvent(e);
    switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: swDown = true; swDrag = false; swX0 = e.getRawX(); swY0 = e.getRawY(); break;
        case MotionEvent.ACTION_MOVE:
            if (!swDown) break;
            if (!swDrag) {
                float mx = e.getRawX() - swX0, my = e.getRawY() - swY0;
                if (Math.abs(mx) > swSlop * 1.5f && Math.abs(mx) > Math.abs(my) * 0.6f) {
                    swDrag = true; swiping = true; swX0 = e.getRawX();
                    swVt = VelocityTracker.obtain(); swVt.addMovement(e);
                    MotionEvent c = MotionEvent.obtain(e); c.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c); c.recycle();   // hủy click/scroll của view con
                    return true;
                }
            } else {
                swVt.addMovement(e);
                swDx = e.getRawX() - swX0;
                card.setTranslationX(swDx);
                card.setAlpha(1f - Math.min(1f, Math.abs(swDx) / Math.max(1f, card.getWidth() * 0.9f)));
                return true;
            }
            break;
        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
            swDown = false;
            if (swDrag) { swDrag = false; finishSwipe(card, e.getActionMasked() == MotionEvent.ACTION_UP, e); return true; }
            break;
    }
    return super.dispatchTouchEvent(e);
}

private void finishSwipe(final View card, boolean up, MotionEvent e) {
    swVt.addMovement(e); swVt.computeCurrentVelocity(1000);
    float vx = swVt.getXVelocity(); swVt.recycle(); swVt = null;
    boolean fling = Math.abs(vx) > 900f && vx * swDx > 0;
    if (up && (Math.abs(swDx) > card.getWidth() * 0.30f || fling)) {
        final Item target = items.get(Math.min(cur, items.size() - 1));
        card.animate().translationX(swDx > 0 ? card.getWidth() : -card.getWidth()).alpha(0f)
            .setDuration(140).setInterpolator(EMPH_IN)
            .withEndAction(() -> {
                swiping = false;
                if (items.contains(target)) dismissItem(target); else if (popup != null) renderPopup();
            }).start();
    } else {
        card.animate().translationX(0f).alpha(1f).setDuration(160).setInterpolator(EMPH)
            .withEndAction(() -> swiping = false).start();
    }
}
}
    private class SwipeCard extends LinearLayout {
        final Item it;
        final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        float x0, y0, dx;
        boolean dragging;
        boolean userMoved;
        VelocityTracker vt;
        SwipeCard(Context c, Item it) { super(c); this.it = it; }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
    int a = e.getActionMasked();
    if (a == MotionEvent.ACTION_DOWN) {
        x0 = e.getRawX(); y0 = e.getRawY(); dragging = false; userMoved = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    } else if (a == MotionEvent.ACTION_MOVE && !dragging) {
        float mx = Math.abs(e.getRawX() - x0), my = Math.abs(e.getRawY() - y0);
        /* UX: đánh dấu userMoved ngay khi vượt slop theo BẤT KỲ hướng — chặn
           top click fire khi user cố swipe chéo (mx < my*1.2 nên không intercept
           được, nhưng vẫn đủ để UP trúng top và mở app). */
        if (mx > slop || my > slop) userMoved = true;
        if (my > slop && my > mx && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
    return super.dispatchTouchEvent(e);
}
        private boolean typing() { return popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0; }   // đang gõ reply -> không cướp cử chỉ
        private boolean horizontal(MotionEvent e) {
            float mx = e.getRawX() - x0, my = e.getRawY() - y0;
            /* UX: hạ 0.7 → 0.45 cho phép swipe chéo ~30-55° vẫn intercept. */
            return Math.abs(mx) > slop && Math.abs(mx) > Math.abs(my) * 0.45f;
        }
        private void begin(MotionEvent e) {
            swiping = true;
            dragging = true;
            x0 = e.getRawX();   // tính dx từ điểm bắt đầu kéo thật -> card không bị nhảy 1 đoạn slop
            vt = VelocityTracker.obtain();
            vt.addMovement(e);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { x0 = e.getRawX(); y0 = e.getRawY(); dragging = false; }
            else if (a == MotionEvent.ACTION_MOVE && !dragging && !typing() && horizontal(e)) { begin(e); return true; }
            return dragging;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { x0 = e.getRawX(); y0 = e.getRawY(); dragging = false; return true; }
            if (a == MotionEvent.ACTION_MOVE) {
                if (!dragging) { if (!typing() && horizontal(e)) begin(e); return true; }
                vt.addMovement(e);
                dx = e.getRawX() - x0;
                setTranslationX(dx);
                setAlpha(1f - Math.min(1f, Math.abs(dx) / Math.max(1f, getWidth() * 0.9f)));
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                swiping = false;
                if (!dragging) {
                    if (a == MotionEvent.ACTION_UP) NoticEngine.this.closePopup(false, true);
                    return true;
                }
                dragging = false;
                vt.addMovement(e);
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity();
                vt.recycle(); vt = null;
                boolean fling = Math.abs(vx) > 900f && vx * dx > 0;
                if (a == MotionEvent.ACTION_UP && (Math.abs(dx) > getWidth() * 0.35f || fling)) {
                    animate().translationX(dx > 0 ? getWidth() : -getWidth()).alpha(0f).setDuration(140).setInterpolator(EMPH_IN)
                        .withLayer().withEndAction(() -> dismissItem(it)).start();
                } else {
                    animate().translationX(0f).alpha(1f).setDuration(160).setInterpolator(EMPH).withLayer().start();
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    private void addBtn(LinearLayout row, Drawable d, final Runnable r) {
    ImageView b = new ImageView(ctx);
    if (d != null) { d = d.mutate(); d.setTint(Color.WHITE); b.setImageDrawable(d); }
    b.setPadding(dp(10), dp(8), dp(10), dp(8));
    b.setOnClickListener(v -> { try { r.run(); } catch (Exception ignored) {} });
    row.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1f));
}
private void addBtn(LinearLayout row, int res, Runnable r) { addBtn(row, ctx.getDrawable(res), r); }

private View buildMediaControls(final Item it) {
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER);
    row.setPadding(0, dp(8), 0, 0);
    if (it.mc != null) {
        // Lấy nút từ chính MediaSession của trình phát -> đủ Trước / Tua / Play-Pause / Tới / nút tuỳ biến, không phụ thuộc notification rút gọn
        final MediaController.TransportControls tc = it.mc.getTransportControls();
        PlaybackState ps = it.mc.getPlaybackState();
        long acts = ps != null ? ps.getActions() : 0L;
        /* UX: khớp đúng icon ↔ chức năng với notification gốc của MyPlaylistService —
   |◀ ▶| = playlist · ◀◀ ▶▶ = bài nhạc · zero-alloc: vẫn 5 addBtn, không thêm object */
addBtn(row, android.R.drawable.ic_media_previous, tc::rewind);
if ((acts & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) addBtn(row, android.R.drawable.ic_media_rew, tc::skipToPrevious);
addBtn(row, it.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
    () -> { if (it.playing) tc.pause(); else tc.play(); });
if ((acts & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) addBtn(row, android.R.drawable.ic_media_ff, tc::skipToNext);
addBtn(row, android.R.drawable.ic_media_next, tc::fastForward);
        if (ps != null) for (final PlaybackState.CustomAction ca : ps.getCustomActions()) {
            Drawable d = null;
            try { d = ctx.createPackageContext(it.pkg, 0).getDrawable(ca.getIcon()); } catch (Exception ignored) {}
            addBtn(row, d, () -> tc.sendCustomAction(ca, null));
        }
        return row;
    }
    if (it.actions != null) {          // dự phòng: không có session -> dùng toàn bộ action của notification
        for (int i = 0; i < it.actions.length && i < 7; i++) {
            final Notification.Action a = it.actions[i];
            if (a == null || a.actionIntent == null) continue;
            Drawable d = null;
            try { Icon ic = a.getIcon(); if (ic != null) d = ic.loadDrawable(ctx); } catch (Exception ignored) {}
            addBtn(row, d, () -> { try { a.actionIntent.send(); } catch (Exception ignored) {} });
        }
    }
    return row;
}
    private TextView pillBtn(String label, int color, float weight, View.OnClickListener l) {
    TextView t = new TextView(ctx);
    t.setText(label);
    t.setTextColor(Color.WHITE);
    t.setTextSize(13f);
    t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setGravity(Gravity.CENTER);
    t.setBackground(rounded(color, dp(22)));
    t.setPadding(dp(8), dp(11), dp(8), dp(11));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, weight);
    lp.setMargins(dp(3), 0, dp(3), 0);
    t.setLayoutParams(lp);
    t.setOnClickListener(l);
    return t;
}

private View buildCallControls(final Item it) {
    LinearLayout box = new LinearLayout(ctx);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(0, dp(8), 0, 0);
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    final LinearLayout quick = new LinearLayout(ctx);
    quick.setOrientation(LinearLayout.VERTICAL);
    quick.setVisibility(View.GONE);
    final String[] presets = quickPresets();
    for (final String p : presets) {
        TextView chip = new TextView(ctx);
        chip.setText(p);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(12.5f);
        chip.setBackground(rounded(0xFF2C2C2E, dp(14)));
        chip.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, 0);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> { sendCall(it, false); smsQuick(it, p); closePopup(false, true); });
        quick.addView(chip);
    }
    row.addView(pillBtn("✕  Từ chối", 0xFFFF3B30, 1f, v -> { sendCall(it, false); closePopup(false, true); }));
    row.addView(pillBtn("📞  Trả lời", 0xFF34C759, 1f, v -> { sendCall(it, true); closePopup(false, true); }));
    TextView qm = new TextView(ctx);   // [P7] hàng riêng phía trên 2 nút
    qm.setText("Send Quick Message");
    qm.setTextColor(Color.parseColor("#8AB4F8"));
    qm.setTextSize(13f);
    qm.setTypeface(Typeface.DEFAULT_BOLD);
    qm.setGravity(Gravity.CENTER);
    qm.setPadding(dp(8), dp(8), dp(8), dp(10));
    qm.setOnClickListener(v -> {
        quick.setVisibility(quick.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        placePopup();
    });
    box.addView(qm);
    box.addView(quick);
    box.addView(row);
    return box;
}
// [P7] Câu trả lời nhanh: mặc định = 4 câu gốc của app Điện thoại Pixel. Muốn đổi: ghi pref "notic_quick_msgs" = "câu 1|câu 2|câu 3"
private String[] quickPresets() {
    String custom = prefs.getString("notic_quick_msgs", "");
    if (!custom.trim().isEmpty()) return custom.split("\\|");
    if ("vi".equals(Locale.getDefault().getLanguage()))
        return new String[]{"Hiện tại tôi không nói chuyện được. Có chuyện gì vậy?", "Tôi sẽ gọi lại ngay.", "Tôi sẽ gọi lại sau.", "Hiện tại tôi không nói chuyện được. Bạn gọi lại sau nhé?"};
    return new String[]{"Can't talk now. What's up?", "I'll call you right back.", "I'll call you later.", "Can't talk now. Call me later?"};
}
private Notification.Action findCallAction(Item it, boolean answer) {
    if (it.actions == null) return null;
    String[] ans = {"answer", "accept", "trả lời", "nghe", "nhận"};
    String[] dec = {"decline", "reject", "hang up", "hangup", "từ chối", "kết thúc", "end call", "cúp"};
    Notification.Action first = null, last = null;
    for (Notification.Action a : it.actions) {
        if (a == null || a.actionIntent == null) continue;
        if (first == null) first = a;
        last = a;
        String t = a.title == null ? "" : a.title.toString().toLowerCase(Locale.ROOT);
        for (String k : answer ? ans : dec) if (t.contains(k)) return a;
    }
    return first == last ? null : (answer ? last : first);  // CallStyle: [từ chối, trả lời]
}

private void sendCall(Item it, boolean answer) {
    Notification.Action a = findCallAction(it, answer);
    if (a == null) { if (answer) openItem(it); return; }
    try { a.actionIntent.send(); } catch (Exception ignored) {}
}

private void smsQuick(Item it, String body) {
    String src = (it.title == null ? "" : it.title) + " " + (it.text == null ? "" : it.text);
    java.util.regex.Matcher m = PHONE.matcher(src);
    String num = m.find() ? m.group().replaceAll("[ .\\-()]", "") : "";
    try {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num)));
        i.putExtra("sms_body", body);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    } catch (Exception ignored) {}
}
    private View buildReplyBar(final Item it) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        final EditText et = new EditText(ctx);
        et.setHint("Reply…");
        et.setHintTextColor(Color.parseColor("#8E8E93"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(13f);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_SEND);
        /* UX: ô Reply viền xanh lam BADGE_BLUE — đồng bộ với viền card tin nhắn
           và vòng tròn nút send, tạo "bộ 3 xanh" nhận diện MessagingStyle. */
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF2C2C2E);
        etBg.setCornerRadius(dp(18));
        etBg.setStroke(dp(1.5f), BADGE_BLUE);
        et.setBackground(etBg);
        et.setPadding(dp(14), dp(8), dp(14), dp(8));
        et.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        if (it.reply == null) et.setHint("Reply (chép & mở app)…");
final String dk = it.key == null ? "" : it.key;
String saved = drafts.get(dk);
if (saved != null) et.setText(saved);
et.addTextChangedListener(new android.text.TextWatcher() {
    public void afterTextChanged(android.text.Editable s) { if (s.length() == 0) drafts.remove(dk); else drafts.put(dk, s.toString()); }
    public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
    public void onTextChanged(CharSequence s, int a, int b, int c) {}
});
        et.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                makePopupFocusable();
                h.postDelayed(() -> {
                    et.requestFocus();
                    InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                }, 120);
            }
            return false;
        });
        et.setOnEditorActionListener((v, id, ev) -> { sendReply(it, et); return true; });
        ImageView send = new ImageView(ctx);
        Drawable sd = ctx.getDrawable(android.R.drawable.ic_menu_send);
        if (sd != null) { sd = sd.mutate(); sd.setTint(Color.WHITE); send.setImageDrawable(sd); }
        send.setScaleType(ImageView.ScaleType.FIT_CENTER);
        send.setPadding(dp(9), dp(9), dp(9), dp(9));
        GradientDrawable sbg = new GradientDrawable();
sbg.setShape(GradientDrawable.OVAL);
sbg.setColor(0xFF2C2C2E);                    // xám đậm
sbg.setStroke(dp(1.5f), BADGE_BLUE);         // viền xanh lam
send.setBackground(sbg);
        send.setOnClickListener(v -> sendReply(it, et));
        row.addView(et);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(40), dp(40));
        slp.setMargins(dp(8), 0, 0, 0);
        row.addView(send, slp);
        return row;
    }

    private void sendReply(Item it, EditText et) {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return;
if (it.reply == null || it.reply.actionIntent == null) {   // fallback: app không có ô trả lời trực tiếp
    if (!canFallbackReply(it)) return;
    ((android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE))
        .setPrimaryClip(android.content.ClipData.newPlainText("Notic", s));
    et.setText("");
    try { it.intent.send(); } catch (Exception ignored) {}
    android.widget.Toast.makeText(ctx, "Đã chép — dán vào ô chat", android.widget.Toast.LENGTH_SHORT).show();
    if (popup != null) releaseIme(popup);
    closePopup(false, true);
    return;
}
        try {
            RemoteInput[] ris = it.reply.getRemoteInputs();
            Intent fill = new Intent();
            Bundle b = new Bundle();
            b.putCharSequence(ris[0].getResultKey(), s);
            RemoteInput.addResultsToIntent(ris, fill, b);
            it.reply.actionIntent.send(ctx, 0, fill);
            et.setText("");
            et.setHint("Sent ✓");
            if (popup != null) releaseIme(popup);
        } catch (Exception e) { et.setHint("Failed"); }
    }
    public void onRemoved(String key) {
        if (key == null || items.isEmpty()) return;
        boolean changed = false;
        for (Item x : new ArrayList<>(items)) {
            if (key.equals(x.key)) {
                items.remove(x);
                unbindMedia(x);   // gỡ MediaController callback nếu là item media
                if (x.call) h.postDelayed(callCheckRun, 800); // trả lời cuộc gọi trên Notic -> cuộc gọi vừa chuyển sang "đang diễn ra"
                changed = true;
            }
        }
        if (changed) afterChange();   // cập nhật lại pill + popup + đếm số
    }
    public void destroy() {
        h.removeCallbacksAndMessages(null);
        cancelPendingTap();
        drafts.clear();
for (Item x : items) unbindMedia(x);
        unregisterSystemEvents();   // [P5] gom toàn bộ phần gỡ receiver/callback vào 1 hàm
        closePopup(false, false);
        removePill();
        items.clear();
        synchronized (ALL) { ALL.remove(this); }
        if (instance == this) instance = null;
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
            Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
            Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
    private class ArtView extends View {
    final Item it;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF r = new RectF();
    final Runnable tick = () -> invalidate();
    ArtView(Context c, Item it) { super(c); this.it = it; setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42))); }
    @Override protected void onDraw(Canvas c) {
        Bitmap b = circleArt(it);
        if (b == null) return;
        c.save();
        c.rotate(spinAngle(it), getWidth() / 2f, getHeight() / 2f);
        r.set(0, 0, getWidth(), getHeight());
        c.drawBitmap(b, null, r, p);
        c.restore();
        removeCallbacks(tick);
        if (it.playing && screenOn && isAttachedToWindow()) postOnAnimationDelayed(tick, 50);   // ăn theo vsync (postDelayed 40ms lệch nhịp vsync -> đĩa quay bị rung); tự dừng khi pause/tắt màn/đóng bảng
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
}

private class VizView extends View {
    final Item it;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Runnable tick = () -> invalidate();
    VizView(Context c, Item it) {
        super(c);
        this.it = it;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(26));
        lp.setMargins(dp(4), 0, 0, 0);
        setLayoutParams(lp);
    }
    @Override protected void onDraw(Canvas c) {
        drawViz(c, p, 0, getHeight() / 2f, getWidth(), getHeight(), vizColor(it), it.playing, speed());
        removeCallbacks(tick);
        if (it.playing && screenOn && isAttachedToWindow()) postOnAnimationDelayed(tick, 50);
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
}
    private class PillView extends View {
        Item item;
        int count;
        String line;
        float ox, oy, ccx, ccy, w, r, mix, textAlpha;
        float h;
        float tgtW, tgtMix;
        ValueAnimator anim;
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Glass glass = new Glass(1.6f * dens);
final Paint lp2 = new Paint(Paint.ANTI_ALIAS_FLAG);
final Runnable tick = () -> invalidate();
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint np = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        final RectF irect = new RectF();
        final RectF lr = new RectF();
        PillView(Context c) {
            super(c);
            tp.setColor(Color.WHITE);
            tp.setTextSize(13f * dens);
            np.setColor(Color.WHITE);
            np.setTextSize(11f * dens);
            np.setTextAlign(Paint.Align.CENTER);
            np.setTypeface(Typeface.DEFAULT_BOLD);
            sp.setStyle(Paint.Style.STROKE);
sp.setStrokeWidth(1.6f * dens);
        }
        void drawLaser(Canvas c, float x, float cyy, float d, Bitmap disc, int color) {
    long period = Math.max(300L, (long) (2200f / (cLedSpeed / 100f)));
    float ph = (SystemClock.uptimeMillis() % period) / (float) period;
    float a = 0.18f + 0.82f * (0.5f - 0.5f * (float) Math.cos(ph * 2 * Math.PI));
    float mx = x + d / 2f;
    lr.set(x, cyy - d / 2f, x + d, cyy + d / 2f);
    boolean gl = cPillMode == PM_GLASS;
    if (cPillMode != PM_CLEAR) { bp.setColor(pillFill()); c.drawCircle(mx, cyy, d / 2f, bp); }   // nền cùng kiểu với viên notic
    if (disc != null) { ip.setAlpha(gl ? 120 : 255); c.drawBitmap(disc, null, lr, ip); ip.setAlpha(255); }   // glass: icon app mờ để thấy lớp kính
    lp2.setColor(color);
    lp2.setAlpha((int) (40 + 150 * a));
    c.drawCircle(mx, cyy, d / 2f, lp2);
    lp2.setAlpha((int) (80 * a));
    c.drawCircle(mx, cyy, d * 0.58f, lp2);
    if (gl) glass.draw(c, lr, d / 2f, cGlass, cPillA / 100f);   // bóng + viền phản quang đè lên trên cùng
}
        void set(float cx, float cyy, float ww, float hh, float rr, float m, float ta) {
            cancelAnim();
            ccx = cx; ccy = cyy; w = ww; h = hh; r = rr; mix = m; textAlpha = ta;
            invalidate();
        }
        boolean hidden;
        boolean animating() { return anim != null; }
        boolean towardsExpanded() { return anim != null && tgtMix > 0.5f; }
        float targetW() { return anim != null ? tgtW : w; }
        void setContent(Item it, int cnt, float expandedW) {
    item = it; count = cnt;
    senderCircle(it);
    if (!it.transientItem) { line = null; return; }   // thông báo app + nhạc: chỉ icon/visualizer/số, không chữ. Sự kiện hệ thống giữ chữ vì không có nơi nào khác để đọc
    tp.setTextSize(Math.min(13f * dens, pillH * 0.38f));
    String s = it.title == null ? "" : it.title;
    if (it.text != null && !it.text.isEmpty()) s = s.isEmpty() ? it.text : s + ": " + it.text;
    s = s.replace('\n', ' ');
    line = TextUtils.ellipsize(s, tp, Math.max(expandedW - pillH * 1.32f, 10f), TextUtils.TruncateAt.END).toString();
}
        void cancelAnim() {
            if (anim != null) { ValueAnimator a = anim; anim = null; a.cancel(); }
        }

        void go(float tcx, float tcy, float tw, float th, float tr, float tm, float ta, long dur, TimeInterpolator in, final Runnable end) {
            cancelAnim();
            final float[] f0 = {ccx, ccy, w, h, r, mix, textAlpha};
            final float[] f1 = {tcx, tcy, tw, th, tr, tm, ta};
            tgtW = tw; tgtMix = tm;
            final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            anim = va;
            va.setDuration(dur);
            va.setInterpolator(in);
            va.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                ccx = f0[0] + (f1[0] - f0[0]) * f;
                ccy = f0[1] + (f1[1] - f0[1]) * f;
                w = Math.max(1f, f0[2] + (f1[2] - f0[2]) * f);
                h = Math.max(1f, f0[3] + (f1[3] - f0[3]) * f);
                r = f0[4] + (f1[4] - f0[4]) * f;
                mix = f0[5] + (f1[5] - f0[5]) * f;
                textAlpha = Math.max(0f, Math.min(1f, f0[6] + (f1[6] - f0[6]) * f));
                invalidate();
            });
            va.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;
                @Override public void onAnimationCancel(Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(Animator a) {
                    if (cancelled) return;
                    if (anim == va) anim = null;
                    if (end != null) end.run();
                }
            });
            va.start();
        }

    @Override public boolean onTouchEvent(MotionEvent e) {
            View hv = hostView;
            return hv != null && !hidden && hv.dispatchTouchEvent(e);
        }
        @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
                @Override protected void onDraw(Canvas c) {
    if (item == null || hidden) return;
    float cx = ccx - ox, cyy = ccy - oy;
    float l = cx - w / 2f, t = cyy - h / 2f, rr = cx + w / 2f, b = cyy + h / 2f;
    rect.set(l, t, rr, b);
    bg.setColor(blend(BAR_COLOR, pillFill(), mix));
    float rad = Math.min(r, Math.min(w, h) / 2f);
    c.drawRoundRect(rect, rad, rad, bg);
    if (mix > 0.05f) {
        if (cPillMode == PM_GLASS) glass.draw(c, rect, rad, cGlass, Math.min(1f, mix) * cPillA / 100f);
        else if (cBorder && cPillMode != PM_CLEAR) {   // Clear = không viền
            sp.setColor(appColor(item));
            sp.setAlpha((int) (255 * Math.min(1f, mix)));
            rect.inset(sp.getStrokeWidth() / 2f, sp.getStrokeWidth() / 2f);
            c.drawRoundRect(rect, rad, rad, sp);
        }
    }
    if (w < pillH * 1.4f || h < pillH * 0.8f) return;
    float isz = h * 0.62f, ix = l + h * 0.2f;
    boolean spin = item.media && cSpin, viz = item.media && cViz;
    boolean led = cLed && !item.media && !item.transientItem && w >= pillH * 1.8f;
    Bitmap sender = (!item.media && !item.transientItem) ? item.senderBmp : null;
    Bitmap lead = item.media ? circleArt(item) : (sender != null ? sender : item.icon);
    if (lead != null) {
        irect.set(ix, cyy - isz / 2f, ix + isz, cyy + isz / 2f);
        if (spin) { c.save(); c.rotate(spinAngle(item), ix + isz / 2f, cyy); c.drawBitmap(lead, null, irect, ip); c.restore(); }
        else c.drawBitmap(lead, null, irect, ip);
    }
    float textR = rr - h * 0.3f;
    if (led) {
    float lw = isz, lx = rr - h * 0.2f - lw;   // cùng kích thước với đĩa trái
    drawLaser(c, lx, cyy, lw, appDisc(item), appColor(item));
    textR = Math.min(textR, lx - h * 0.15f);
}
    float vw = h * 0.95f, vl = rr - h * 0.25f - vw;
    if (viz) { drawViz(c, bp, vl, cyy, vw, h * 0.5f, vizColor(item), item.playing, speed(), 0.05f); textR = Math.min(textR, vl - h * 0.15f); }
    if (count > 1 && cBadge) {
        float ba = Math.max(0f, Math.min(1f, (w - pillH * 1.4f) / (pillH * 0.6f)));
        float br = h / 2f, bcx = badgeLeft ? l - 6 * dens - br : rr + 6 * dens + br;
        rect.set(bcx - br, cyy - br, bcx + br, cyy + br);
int pf2 = pillFill();   // UX: badge luôn dùng đúng nền + viền của viên notic
bp.setColor(pf2); bp.setAlpha(Math.round(Color.alpha(pf2) * ba));
c.drawCircle(bcx, cyy, br, bp);
if (cPillMode == PM_GLASS) glass.draw(c, rect, br, cGlass, ba * cPillA / 100f);
else if (cBorder && cPillMode == PM_SOLID) {
    sp.setColor(appColor(item)); sp.setAlpha((int) (255 * ba));
    c.drawCircle(bcx, cyy, br - sp.getStrokeWidth() / 2f, sp);
}
        np.setTextSize(Math.min(13f * dens, h * 0.46f)); np.setAlpha((int) (255 * ba));
        Paint.FontMetrics fm = np.getFontMetrics();
        c.drawText(String.valueOf(Math.min(count, 99)), bcx, cyy - (fm.ascent + fm.descent) / 2f, np);
        bp.setAlpha(255); np.setAlpha(255);
    }
    if (line != null && textAlpha > 0.02f) {
        tp.setAlpha((int) (255 * textAlpha));
        float tx = ix + isz + h * 0.2f;
        Paint.FontMetrics fm = tp.getFontMetrics();
        c.save(); c.clipRect(tx, t, textR, b);
        c.drawText(line, tx, cyy - (fm.ascent + fm.descent) / 2f, tp);
        c.restore();
    }
    removeCallbacks(tick);
    // LED chỉ chạy 15fps và tự dừng khi tắt màn / Notic bị ẩn -> gần như 0 pin khi không nhìn
    if (screenOn && statusVisible && ((item.media && item.playing && (spin || viz)) || led))
        postOnAnimationDelayed(tick, led ? 66 : 50);
   }
 }
}
