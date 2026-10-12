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
    private static final Set<String> selfCancel = new HashSet<>();
    public static synchronized void markSelfCancel(String k) { selfCancel.add(k); }
    public static synchronized boolean consumeSelfCancel(String k) { return selfCancel.remove(k); }
public static class Item {
    String key, pkg, title, text, sig, appName;
    Bitmap icon, art, artCircle, senderBmp;   // [P7] senderBmp = ảnh người gửi/người gọi, cắt tròn
    Icon largeIcon;
    PendingIntent intent;
    Notification.Action[] actions;
    Notification.Action reply;
    boolean media, transientItem, call, ongoing, person, playing = true;
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
    private boolean fullWin, expanded, statusVisible = true, insetsVisible = true, shadeOpen, hostChanged, sysRegistered, fadeNext;   // [P1][P4][P7]
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
    private final Runnable noHostRun = () -> { if (!bindHost()) { closePopup(false, false); shrinkRemove(false, false); } else relayout(); };
private static final TimeInterpolator EMPH = new PathInterpolator(0.2f, 0f, 0f, 1f);
private static final TimeInterpolator EMPH_IN = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
private static final int[] VIZ_COLORS = {0, 0xFF00E5FF, 0xFF8AB4F8, 0xFFFF4081, 0xFFFFC107, 0xFF69F0AE, 0xFFFFFFFF, 0xFFB388FF, 0};
static final String[] PILL_HEX = {"#0A84FF","#30D158","#FF453A","#FF9F0A","#BF5AF2","#64D2FF","#FF375F","#8E8E93"};
static final String[] CARD_HEX = {"#1E3A5F","#3B1F5E","#5E1F3B","#1F5E3B","#5E4A1F","#1F4F5E","#4A4A4A","#5E2A1F"};
private static final long DTAP_MS = 280;
private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d .\\-()]{6,}\\d");
private final Map<String, Integer> colorCache = new HashMap<>();
private boolean screenOn = true, popupHistory;
private LinearLayout popupHead;
private GradientDrawable popupBg;
private Runnable pendingTap;
private long lastTapMs;
private int cPill = Color.BLACK, cRadPct = 100;
private boolean badgeLeft, popMorphing;
private boolean closing, reopen, swiping;
private float morphF;
private float oL, oT, oR, oB, oRad, popRad;
private ValueAnimator morphAnim;
private final Runnable autoCloseRun = this::autoClose;
private boolean cBorder = true, cSpin = true, cViz = true;
private int cVizColor = 0, cVizSpeed = 100;
private void cachePrefs() {
    cBorder = prefs.getBoolean("notic_border_en", true);
    cSpin = prefs.getBoolean("notic_art_spin", true);
    cViz = prefs.getBoolean("notic_viz_en", true);
    cVizColor = prefs.getInt("notic_viz_color", 0);
    cVizSpeed = prefs.getInt("notic_viz_speed", 100);
    cRadPct = prefs.getInt("notic_rad_pct", 100);
    int pi = prefs.getInt("notic_pill_color", 0);
    cPill = (pi <= 0 || pi > PILL_HEX.length) ? Color.BLACK : Color.parseColor(PILL_HEX[pi - 1]);
}
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
    final int n = 7;                       // muốn 9 thanh: đổi 7 -> 9 (thanh sẽ mảnh lại, nên tăng thêm bề rộng vùng vẽ)
    float gap = w * 0.07f, bw = (w - gap * (n - 1)) / n;
    float t = (SystemClock.uptimeMillis() % 100000L) / 1000f * speed;
    p.setColor(color);
    for (int i = 0; i < n; i++) {
        float f = playing ? 0.30f + 0.70f * Math.abs((float) Math.sin(t * (2.1f + i * 0.7f) + i * 1.7f)) : 0.22f;
        float bh = h * f, l = x + i * (bw + gap);
        c.drawRoundRect(l, cy - bh / 2f, l + bw, cy + bh / 2f, bw / 2f, bw / 2f, p);
    }
}
private Bitmap circleArt(Item it) {
    if (it.artCircle != null) return it.artCircle;
    Bitmap src = loadLarge(it);
    if (src == null) src = it.icon;
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
    c.drawCircle(s / 2f, s / 2f, 7, p);       // lỗ giữa kiểu đĩa than
    it.artCircle = out;                       // vẽ 1 lần, về sau chỉ rotate bitmap
    return out;
}
private Bitmap senderCircle(Item it) {
    if (it.senderBmp != null) return it.senderBmp;
    if (it.media || it.transientItem) return null;
    Bitmap src = it.largeIcon != null ? loadLarge(it) : null;
    if (src == null && !it.person) return null;      // thông báo thường: giữ icon app bên trái như cũ
    int sz = 96;
    Bitmap out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
    Canvas cv = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    if (src != null) {
        cv.drawCircle(sz / 2f, sz / 2f, sz / 2f, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        cv.drawBitmap(src, null, new RectF(0, 0, sz, sz), p);
    } else {                                          // không có avatar -> hình tròn + chữ cái đầu
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
    }
    it.senderBmp = out;                               // vẽ 1 lần, cache trong Item
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
    if (popup != null) renderPopup();
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
        if (prefs.getBoolean("notic_en", false)) registerSystemEvents();   // [P5] Notic tắt = không đăng ký receiver/camera/mic/network callback
    }

    private int dp(float v) { return Math.round(v * dens); }
    private float badgeExtra() { return pillH + 6 * dens; }   // [P2] đường kính ô tròn số (= bề dày pill) + khoảng cách 6dp
    public boolean hasHost() {
        Object[] hs = hostFinder.get();
        return hs != null && ((View) hs[0]).getVisibility() == View.VISIBLE;
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

    private Bitmap emojiIcon(String emoji, int color) {
        String k = "ev:" + emoji + color;
        Bitmap b = iconCache.get(k);
        if (b != null) return b;
        b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(48, 48, 48, p);
        p.setColor(Color.WHITE);
        p.setTextSize(52);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(emoji, 48, 48 - (fm.ascent + fm.descent) / 2f, p);
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
                    event("unlock", "🔓", "Phone unlocked", "", 0xFF455A64);
                } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
    screenOn = true;
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
                    boolean active = l != null && !l.isEmpty();
                    if (active && !micOn) {
                        micOn = true;
                        if (!VoiceRecorderService.isRunning && !ScreenRecorderService.isRunning && SystemClock.elapsedRealtime() > armedAt)
                            event("mic", "🎙", "Microphone in use", "", 0xFFF57C00);
                    } else if (!active) micOn = false;
                }
            };
            audio.registerAudioRecordingCallback(micCb, h);
        } catch (Exception e) { micCb = null; }
        try {
            camMgr = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            torchCb = new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean en) { lastTorchMs = SystemClock.elapsedRealtime(); }
            };
            camCb = new CameraManager.AvailabilityCallback() {
                @Override public void onCameraUnavailable(String id) {
                    long now = SystemClock.elapsedRealtime();
                    if (now < armedAt || now - lastTorchMs < 1500) return;
                    if (camBusy.add(id) && camBusy.size() == 1) event("camera", "📷", "Camera in use", "", 0xFFEF6C00);
                }
                @Override public void onCameraAvailable(String id) { camBusy.remove(id); }
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
        it.icon = emojiIcon(emoji, color);
        it.transientItem = true;
        post(it, false, false);
    }
    public boolean post(final Item it, boolean wake, boolean sound) {
    Item old = null;
    for (Item x : items) if (x.key.equals(it.key)) { old = x; break; }
    boolean ongoingUpd = old != null && it.ongoing && !it.media && !it.call;
    boolean same = old != null && old.sig.equals(it.sig);
    // "mới" = key mới HOẶC nội dung mới: Zalo cùng 1 người nhắn cách 5 phút vẫn dùng chung key nhưng phải bật màn + kêu lại
    boolean fresh = !ongoingUpd && !same;
    if (fresh) { if (wake) wakeScreen(); if (sound) playSound(); }
    if (!hasHost()) return false;
    if (it.icon == null && it.pkg != null) it.icon = appIcon(it.pkg);
    if (ongoingUpd) {
        old.title = it.title; old.text = it.text; old.sig = it.sig;
        old.intent = it.intent; old.actions = it.actions;
        return true;
    }
    if (same) {
        if (it.media) {
            old.actions = it.actions; old.intent = it.intent; old.largeIcon = it.largeIcon;
            old.art = null; old.artCircle = null; old.artColor = 0;
            if (old.mc == null && it.token != null) bindMedia(old, it.token);
            if (old.mc == null) refreshMedia(old);
        }
        return true;
    }
    if (old != null) { unbindMedia(old); items.remove(old); }
    if (it.media && it.token != null) bindMedia(it, it.token);
    items.add(0, it);
    cur = 0;
    while (items.size() > 20) unbindMedia(items.remove(items.size() - 1));
    announce();
    if (popup != null) renderPopup();
    if (it.call) h.post(() -> showPopup(false, true));
    else if (!it.transientItem && !it.media && screenOn && prefs.getBoolean("notic_auto_expand", false)) {
        // đợi animation chấm→tròn→thuốc xong (~500ms) rồi mới mở bảng
        h.postDelayed(() -> { showPopup(false, true); scheduleAutoClose(); }, 520);
    }
    if (it.transientItem) h.postDelayed(() -> removeItem(it), 3600);
    return true;
}

private void scheduleAutoClose() {
    h.removeCallbacks(autoCloseRun);
    h.postDelayed(autoCloseRun, Math.max(1, prefs.getInt("notic_hold_sec", 4)) * 1000L);
}
private void autoClose() {
    if (popup == null) return;
    if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) { h.postDelayed(autoCloseRun, 2000); return; }   // đang gõ reply thì hoãn, không thu giữa chừng
    closePopup(false, true);
}
private void removeItem(Item it) {
    if (items.remove(it)) { unbindMedia(it); afterChange(); }
}

public void onScreenOff() {
    screenOn = false;
    setShadeOpen(false);   // [P4]    
    prefs.edit().putBoolean("notic_paused", false).apply();
    closePopup(false, false);
}

// 1 chạm = bảng của thông báo hiện tại; 2 chạm = lịch sử toàn bộ
public boolean interceptGesture(String key) {
    if (pill == null || pill.hidden || closing || items.isEmpty() || !statusVisible || key == null || hostKey == null) return false;
    if (key.equals(hostKey + "_dtap")) { cancelPendingTap(); showPopup(true, false); return true; }
    if (key.equals(hostKey + "_tap")) {
        long now = SystemClock.elapsedRealtime();
        if (pendingTap != null && now - lastTapMs <= DTAP_MS) {
            cancelPendingTap();
            showPopup(true, false);
        } else {
            lastTapMs = now;
            cancelPendingTap();
            pendingTap = () -> { pendingTap = null; showPopup(false, false); }; // chờ 280ms để phân biệt 1 chạm / 2 chạm
            h.postDelayed(pendingTap, DTAP_MS);
        }
        return true;
    }
    if (key.equals(hostKey + "_long")) { openCurrentApp(); return true; }
    if (key.equals(hostKey + "_left")) { step(1); return true; }
    if (key.equals(hostKey + "_right")) { step(-1); return true; }
    if (key.equals(hostKey + "_up")) { hideNotic(); return true; }
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
        if (!prefs.getBoolean("notic_en", false)) {
            unregisterSystemEvents();   // [P5]
            h.removeCallbacks(geoRun); h.removeCallbacks(noHostRun); pendingGeo = "";
            if (!items.isEmpty() || pill != null) { items.clear(); closePopup(false, false); removePill(); }
            return;
        }
        if (closing) return;
        if (!sysRegistered) registerSystemEvents();   // [P5] bật Notic -> mới đăng ký receiver
        if (popup != null && bindHost()) { renderPopup(); placePopup(); }
        if (items.isEmpty()) return;
        if (!bindHost()) {
            h.removeCallbacks(noHostRun);
            h.postDelayed(noHostRun, 700);
            return;
        }
        h.removeCallbacks(noHostRun);     
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
    private String geoKey() { return (int) hl + "|" + (int) ht + "|" + (int) hr + "|" + (int) hb + "|" + (int) px + "|" + (int) cy + "|" + (int) ew + "|" + (int) pillH + "|" + badgeLeft + "|" + cRadPct; }

    private void geoSettle() {
        if (items.isEmpty() || !prefs.getBoolean("notic_en", false)) { pendingGeo = ""; return; }
        if (!bindHost()) { pendingGeo = ""; h.removeCallbacks(noHostRun); h.postDelayed(noHostRun, 700); return; }
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
float[] rc = barRectFromPrefs(hostKey, scrW, scrH);
if (rc == null) rc = rectFromLayoutParams(v, scrW, scrH);
if (rc == null) return false;
    hl = rc[0]; ht = rc[1]; hr = rc[2]; hb = rc[3];
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
        pv.setOnApplyWindowInsetsListener((v, ins) -> {
            boolean vis = Build.VERSION.SDK_INT < 30 || ins.isVisible(WindowInsets.Type.statusBars());
            if (vis != insetsVisible) { insetsVisible = vis; syncVisible(); }   // [P4] gộp với trạng thái "đang kéo bảng thông báo"
            return ins;
        });
        try { wm.addView(pv, pillLp); } catch (Exception e) { return false; }
        pill = pv;
        statusVisible = true; insetsVisible = true;
        pillHostKey = hostKey;
        fullWin = true;
        expanded = false;
        pv.requestApplyInsets();
        return true;
    }
    public void setShadeOpen(boolean open) {
        if (shadeOpen == open) return;
        shadeOpen = open;
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
    if (items.isEmpty()) return;
    if (closing) { reopen = true; return; }
    if (!bindHost()) return;
    if (shadeOpen && !previewForce) return;
    userHidden = false;
    h.removeCallbacks(settleRun);
    Item it = items.get(Math.min(cur, items.size() - 1));
    final float tw = ew;
    final boolean fresh = pill == null || pill.hidden;
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
        pv.go(px, cy, pillH, pillH, pillH / 2f, 1f, 0f, 240, new OvershootInterpolator(1.3f),
            () -> { if (pill == pv) pv.go(px, cy, tw, pillH, pillRad(), 1f, 1f, 340, EMPH, this::afterExpand); });
    } else {
        boolean sameState = (expanded || pv.towardsExpanded()) && Math.abs(pv.targetW() - tw) < 1f;
        pv.setContent(it, items.size(), tw);
        if (sameState) pv.invalidate();
        else {
            if (fullWin || tw + 4 * dens > pillLp.width) winFull();
            pv.go(px, cy, tw, pillH, pillRad(), 1f, 1f, 300, EMPH, this::afterExpand);
        }
    }
}

// Co lại: viên thuốc → tròn → chấm ở tâm. keepWindow=true: giữ cửa sổ rỗng để còn nghe insets.
private void shrinkRemove(final boolean thenReopen, final boolean keepWindow) {
    if (pill == null) { if (thenReopen) announce(); return; }
    if (pill.hidden) { if (!keepWindow) removePill(); return; }
    if (closing) { reopen |= thenReopen; return; }
    closing = true; reopen = thenReopen;
    h.removeCallbacks(settleRun);
    expanded = false;
    final PillView pv = pill;
    if (hostView != null) hostView.animate().alpha(1f).setStartDelay(150).setDuration(320).start();
    pv.go(px, cy, pillH, pillH, pillH / 2f, 1f, 0f, 220, EMPH_IN, () -> {
        if (pill != pv) return;
        pv.go(px, cy, 2f, 2f, 1f, 1f, 0f, 170, EMPH_IN, () -> {
            if (pill != pv) return;
            boolean again = reopen;
            closing = false; reopen = false;
            if (keepWindow) { pv.hidden = true; pv.invalidate(); } else removePill();
            if (again && !items.isEmpty() && !userHidden) announce();
        });
    });
}

private void collapse() { shrinkRemove(false, false); }

private void hideNotic() {
    cancelPendingTap();
    h.removeCallbacks(settleRun); h.removeCallbacks(autoCloseRun);
    closePopup(false, false);
    userHidden = true;
    shrinkRemove(false, false);
    // UX: lên lịch tự hồi lại; user có thể chỉnh N trong Notic Space
    h.removeCallbacks(reshowRun);
    int sec = Math.max(3, prefs.getInt("notic_hide_duration_sec", 12));
    h.postDelayed(reshowRun, sec * 1000L);
}
private void afterChange() {
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

private void syncVisible() {
    boolean v = previewForce || (insetsVisible && !shadeOpen);
    if (v == statusVisible) return;
    statusVisible = v;
    h.post(() -> {
        if (!statusVisible) { closePopup(false, false); shrinkRemove(false, true); }
        else if (!items.isEmpty() && !userHidden) announce();
    });
}

private void applyVisibility() {
    if (pill == null || pill.hidden) return;
    if (fadeNext) {
        fadeNext = false;
        fadeUntil = SystemClock.uptimeMillis() + 220;
        if (hostView != null) { hostView.animate().cancel(); hostView.animate().setStartDelay(0).alpha(0f).setDuration(200).start(); }
    } else if (!closing && hostView != null && SystemClock.uptimeMillis() >= fadeUntil) {
        hostView.animate().cancel();
        hostView.setAlpha(0f);
    }
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
    if (hostSwitch && !previewForce) { shrinkRemove(true, false); return; }   // Lock↔Home khác vị trí: co lại rồi nở ra ở chỗ mới
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
        if (!history || popupHistory) { closePopup(false, true); return; }  // chạm lại = đóng
        popupHistory = true;                                                // đang xem 1 thông báo, 2 chạm = mở rộng ra lịch sử
        if (popupHead != null) popupHead.setVisibility(View.VISIBLE);
        renderPopup(); placePopup();
        return;
    }
    if (!force && SystemClock.elapsedRealtime() - lastCloseMs < 400) return;
    popupHistory = history;
    openPopup();
}
private int popW() { return (int) Math.min(scrW - dp(24), dp(420) * prefs.getInt("notic_pop_w_pct", 100) / 100f); }
private int popX(int pw) { return (int) Math.max(dp(8), Math.min(scrW - pw - dp(8), px - pw / 2f)); }
private int popY(int ph) {
    int y = cy < scrH / 2f ? (int) (cy - pillH / 2f) : (int) (cy + pillH / 2f - ph);
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
    root.setOnTouchListener((v, e) -> {
    if (previewForce) return false;
    final int act = e.getActionMasked();
    if (act == MotionEvent.ACTION_OUTSIDE) {
        closePopup(false, true);
        return true;
    }
    // UX: chạm vùng trống bên trong bảng (viền đệm, khe giữa các thẻ) cũng đóng về viên thuốc
    // Nút/card/chip/EditText là con của root nên đã tiêu thụ sự kiện trước, không bị đóng nhầm
    if (act == MotionEvent.ACTION_UP) {
        closePopup(false, true);
        return true;
    }
    return act == MotionEvent.ACTION_DOWN;
});
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
    TextView clear = new TextView(ctx);
    clear.setText("Clear all");
    clear.setTextColor(Color.parseColor("#8AB4F8"));
    clear.setTextSize(12.5f);
    clear.setPadding(dp(8), dp(6), dp(8), dp(6));
    clear.setOnClickListener(v -> clearAll());
    head.addView(ttl);
    head.addView(clear);
    head.setVisibility(popupHistory ? View.VISIBLE : View.GONE);
    popupHead = head;
    root.addView(head);

    popupBody = new LinearLayout(ctx);
    popupBody.setOrientation(LinearLayout.VERTICAL);
    ScrollView sv = new ScrollView(ctx) {
        @Override protected void onMeasure(int wSpec, int hSpec) {
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(popMaxH(), MeasureSpec.AT_MOST));
        }
    };
    sv.addView(popupBody);
    root.addView(sv, new LinearLayout.LayoutParams(-1, -2));

    int pw = popW();
    popup = root;
    renderPopup();
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
    final LinearLayout fr = root; final WindowManager.LayoutParams flp = popupLp; final GradientDrawable fbg = popupBg;
root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
    @Override public boolean onPreDraw() {
        fr.getViewTreeObserver().removeOnPreDrawListener(this);
        runMorph(fr, flp, fbg, true, () -> { popMorphing = false; fr.invalidateOutline(); fr.invalidate(); });
        final PillView pv = pill;
        if (pv != null) fr.post(() -> pv.setVisibility(View.INVISIBLE));   // bảng đã vẽ đè đúng hình viên thuốc → ẩn viên thuốc, không còn hai thứ cùng tồn tại
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
    va.setDuration(open ? 380 : 300);
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
    swiping = false;
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
private void renderPopup() {
    if (swiping) return;
    if (popupBody == null || items.isEmpty()) return;
    if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) return; // đang gõ reply thì không dựng lại, tránh mất chữ
    popupBody.removeAllViews();
    Item curIt = items.get(Math.min(cur, items.size() - 1));
    if (popupBg != null) {
        if (popupHistory) {   // [P6] chế độ lịch sử: khung đen chung, mỗi thẻ có viền màu app riêng
            popupBg.setColor(0xF22C2C2E);
            popupBg.setStroke(dp(1.5f), cBorder ? 0x40FFFFFF : Color.TRANSPARENT);
            popup.setPadding(dp(8), dp(8), dp(8), dp(8));
        } else {              // [P6] xem 1 thông báo: bỏ khung ngoài (padding 8dp + margin 4dp là "khoảng cách" bạn thấy), viền màu nằm ngay trên thẻ
            popupBg.setColor(Color.TRANSPARENT);
            popupBg.setStroke(0, 0);
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

    private View buildRow(final Item it) {
        // UX: vuốt ngang card = xoá (media + cuộc gọi đến KHÔNG vuốt xoá để tránh lỡ tay mất trình phát/cuộc gọi)
        LinearLayout card = it.call ? new LinearLayout(ctx) : new SwipeCard(ctx, it);
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
        if (!it.media && it.senderBmp != null && it.icon != null) {   // icon app ở cùng bên phải
            ImageView ai = new ImageView(ctx);
            ai.setImageBitmap(it.icon);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(24), dp(24));
            alp.setMargins(dp(4), 0, 0, 0);
            top.addView(ai, alp);
        }        
        if (it.media && prefs.getBoolean("notic_viz_en", true)) top.addView(new VizView(ctx, it)); // visualizer nằm sát phải cùng
        top.setOnClickListener(v -> {
            openItem(it);
            if (!it.media) items.remove(it);
            closePopup(false, true);
            afterChange();
        });
        card.addView(top);
        if (it.call) card.addView(buildCallControls(it));
else if (it.media) card.addView(buildMediaControls(it));
else { if (it.reply != null) card.addView(buildReplyBar(it)); card.addView(buildItemActions(it)); }
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
    row.addView(actChip("Dismiss", v -> dismissItem(it)));
    return row;
}
    // 0 Auto | 1 màu icon app, không viền | 2 "chỉ màu": nền pha nhẹ màu app, không viền | 3..10 màu nền cố định + viền màu app
private GradientDrawable cardBg(Item it, float r) {
    int m = prefs.getInt("notic_card_color", 0);
    if (m == 1) return rounded(blend(appColor(it), Color.BLACK, 0.35f), r);
    if (m == 2) return rounded(blend(CARD_GRAY, appColor(it), 0.30f), r);
    if (it.media && m == 0) {                       // thẻ nhạc: nền theo màu thông báo, các thẻ khác vẫn xám
        GradientDrawable g = rounded(blend(appColor(it), Color.BLACK, 0.45f), r);
        if (cBorder) g.setStroke(dp(1.5f), appColor(it));
        return g;
    }
    GradientDrawable g = rounded(m >= 3 && m - 3 < CARD_HEX.length ? Color.parseColor(CARD_HEX[m - 3]) : CARD_GRAY, r);
    if (cBorder) g.setStroke(dp(1.5f), appColor(it));
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
    final RectF rr = new RectF();
    private float irX, irY;
    MorphRoot(Context c) { super(c); setWillNotDraw(false); sp2.setStyle(Paint.Style.STROKE); sp2.setStrokeWidth(1.6f * dens); }

    // UX: vuốt lên trên bảng mở rộng = ẩn nhanh Notic — HARDCODED, không phụ
    // thuộc rule user gán cho bar. Chặn ở onIntercept (trước cả SwipeCard) để
    // bắt được swipe bắt đầu từ bất kỳ điểm nào trong bảng, kể cả trên card.
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (previewForce) return super.onInterceptTouchEvent(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            irX = e.getRawX(); irY = e.getRawY();
        } else if (a == MotionEvent.ACTION_MOVE) {
            float dy = e.getRawY() - irY, dx = e.getRawX() - irX;
            if (dy < -dp(28) && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                h.post(NoticEngine.this::hideNotic);   // defer ra khỏi dispatch để tránh removeView giữa frame
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override protected void dispatchDraw(Canvas c) {
        if (popMorphing) {
            float a = 1f - Math.max(0f, Math.min(1f, (morphF - 0.35f) / 0.5f));
            if (a > 0f) {
                rr.set(oL, oT, oR, oB);
                fp.setColor(cPill); fp.setAlpha((int) (255 * a));
                c.drawRoundRect(rr, oRad, oRad, fp);
                if (cBorder && !items.isEmpty()) {
                    sp2.setColor(appColor(items.get(Math.min(cur, items.size() - 1)))); sp2.setAlpha((int) (255 * a));
                    float in = sp2.getStrokeWidth() / 2f;
                    rr.inset(in, in);
                    c.drawRoundRect(rr, Math.max(0f, oRad - in), Math.max(0f, oRad - in), sp2);
                }
            }
        }
        super.dispatchDraw(c);
    }
}
    private class SwipeCard extends LinearLayout {
        final Item it;
        final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        float x0, y0, dx;
        boolean dragging;
        VelocityTracker vt;
        SwipeCard(Context c, Item it) { super(c); this.it = it; }
        // ScrollView cha hay giành cử chỉ khi tay lệch dọc: chặn từ DOWN, chỉ trả quyền khi rõ ràng là vuốt dọc
@Override public boolean dispatchTouchEvent(MotionEvent e) {
    int a = e.getActionMasked();
    if (a == MotionEvent.ACTION_DOWN) {
        x0 = e.getRawX(); y0 = e.getRawY(); dragging = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    } else if (a == MotionEvent.ACTION_MOVE && !dragging) {
        float mx = Math.abs(e.getRawX() - x0), my = Math.abs(e.getRawY() - y0);
        if (my > slop && my > mx && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
    return super.dispatchTouchEvent(e);
}
        private boolean typing() { return popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0; }   // đang gõ reply -> không cướp cử chỉ

        private boolean horizontal(MotionEvent e) {
            float mx = e.getRawX() - x0, my = e.getRawY() - y0;
            return Math.abs(mx) > slop && Math.abs(mx) > Math.abs(my) * 1.2f;
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
                if (!dragging) return super.onTouchEvent(e);
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
        addBtn(row, android.R.drawable.ic_media_previous, tc::skipToPrevious);
        if ((acts & PlaybackState.ACTION_REWIND) != 0) addBtn(row, android.R.drawable.ic_media_rew, tc::rewind);
        addBtn(row, it.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
            () -> { if (it.playing) tc.pause(); else tc.play(); });
        if ((acts & PlaybackState.ACTION_FAST_FORWARD) != 0) addBtn(row, android.R.drawable.ic_media_ff, tc::fastForward);
        addBtn(row, android.R.drawable.ic_media_next, tc::skipToNext);
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
        et.setBackground(rounded(0xFF2C2C2E, dp(18)));
        et.setPadding(dp(14), dp(8), dp(14), dp(8));
        et.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
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
        if (s.isEmpty() || it.reply == null || it.reply.actionIntent == null) return;
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
                changed = true;
            }
        }
        if (changed) afterChange();   // cập nhật lại pill + popup + đếm số
    }
    public void destroy() {
        h.removeCallbacksAndMessages(null);
        cancelPendingTap();
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
final Runnable tick = () -> invalidate();
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint np = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        final RectF irect = new RectF();

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

        void set(float cx, float cyy, float ww, float hh, float rr, float m, float ta) {
            cancelAnim();
            ccx = cx; ccy = cyy; w = ww; h = hh; r = rr; mix = m; textAlpha = ta;
            invalidate();
        }
        boolean hidden, upFired;
float dX0, dY0;
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
    if (hv == null || hidden) return false;
    int a = e.getActionMasked();
    if (a == MotionEvent.ACTION_DOWN) { dX0 = e.getRawX(); dY0 = e.getRawY(); upFired = false; }
    else if (a == MotionEvent.ACTION_MOVE) {
        if (upFired) return true;
        float dy = e.getRawY() - dY0, dx = e.getRawX() - dX0;
        // bar sát mép trên không có chỗ vuốt dài, nên tự bắt vuốt lên ngắn ngay tại viên thuốc
        if (dy < -dp(14) && Math.abs(dy) > Math.abs(dx) * 1.5f) {
            upFired = true;
            MotionEvent c = MotionEvent.obtain(e); c.setAction(MotionEvent.ACTION_CANCEL);
            hv.dispatchTouchEvent(c); c.recycle();
            hideNotic();
            return true;
        }
    } else if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && upFired) { upFired = false; return true; }
    return hv.dispatchTouchEvent(e);
}
        @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
                @Override protected void onDraw(Canvas c) {
    if (item == null || hidden) return;
    float cx = ccx - ox, cyy = ccy - oy;
    float l = cx - w / 2f, t = cyy - h / 2f, rr = cx + w / 2f, b = cyy + h / 2f;
    rect.set(l, t, rr, b);
    bg.setColor(blend(BAR_COLOR, cPill, mix));
    float rad = Math.min(r, Math.min(w, h) / 2f);
    c.drawRoundRect(rect, rad, rad, bg);
    if (mix > 0.05f && cBorder) {
        sp.setColor(appColor(item));
        sp.setAlpha((int) (255 * Math.min(1f, mix)));
        rect.inset(sp.getStrokeWidth() / 2f, sp.getStrokeWidth() / 2f);
        c.drawRoundRect(rect, rad, rad, sp);
    }
    if (w < pillH * 1.4f || h < pillH * 0.8f) return;
    float isz = h * 0.62f, ix = l + h * 0.2f;
    boolean spin = item.media && cSpin, viz = item.media && cViz;
    Bitmap sender = (!item.media && !item.transientItem) ? item.senderBmp : null;
    Bitmap lead = spin ? circleArt(item) : (sender != null ? sender : item.icon);
    if (lead != null) {
        irect.set(ix, cyy - isz / 2f, ix + isz, cyy + isz / 2f);
        if (spin) { c.save(); c.rotate(spinAngle(item), ix + isz / 2f, cyy); c.drawBitmap(lead, null, irect, ip); c.restore(); }
        else c.drawBitmap(lead, null, irect, ip);
    }
    float textR = rr - h * 0.3f;
    if (sender != null && item.icon != null && w >= pillH * 1.8f) {
        float rx = rr - h * 0.2f - isz;
        irect.set(rx, cyy - isz / 2f, rx + isz, cyy + isz / 2f);
        c.drawBitmap(item.icon, null, irect, ip);
        textR = rx - h * 0.15f;
    }
    float vw = h * 0.85f, vl = rr - h * 0.25f - vw;
    if (viz) { drawViz(c, bp, vl, cyy, vw, h * 0.5f, vizColor(item), item.playing, speed()); textR = Math.min(textR, vl - h * 0.15f); }
    if (count > 1) {
        float ba = Math.max(0f, Math.min(1f, (w - pillH * 1.4f) / (pillH * 0.6f)));
        float br = h / 2f, bcx = badgeLeft ? l - 6 * dens - br : rr + 6 * dens + br;
        bp.setColor(Color.BLACK); bp.setAlpha((int) (255 * ba));
        c.drawCircle(bcx, cyy, br, bp);
        sp.setColor(BADGE_BLUE); sp.setAlpha((int) (255 * ba));
        c.drawCircle(bcx, cyy, br - sp.getStrokeWidth() / 2f, sp);
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
    if (item.media && item.playing && screenOn && statusVisible && (spin || viz)) postOnAnimationDelayed(tick, 50);
}
  }
}
