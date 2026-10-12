package com.manhmoc.edgebar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.BatteryManager;
import android.os.Build;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class NoticEngine {
    public static volatile NoticEngine instance;
    private static final Set<String> selfCancel = new HashSet<>();
    public static synchronized void markSelfCancel(String k) { selfCancel.add(k); }
    public static synchronized boolean consumeSelfCancel(String k) { return selfCancel.remove(k); }

    public static class Item {
        String key, pkg, title, text, sig;
        Bitmap icon;
        PendingIntent intent;
        boolean transientItem;
    }

    private static final int PILL_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
    private static final int POPUP_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

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

    private PillView pill;
    private WindowManager.LayoutParams pillLp;
    private String lastSig = "";
    private int pillH = 100;
    private boolean statusVisible = true;
    private LinearLayout popup;
    private LinearLayout popupBody;
    private long lastCloseMs = 0;
    private Ringtone ringtone;
    private BroadcastReceiver sysRx, fullRx;
    private ConnectivityManager cmg;
    private ConnectivityManager.NetworkCallback netCb;
    private final Runnable settleRun = this::settle;

    public NoticEngine(Context ctx, WindowManager wm, SharedPreferences prefs, int wmType, Supplier<Object[]> hostFinder) {
        this.ctx = ctx;
        this.wm = wm;
        this.prefs = prefs;
        this.wmType = wmType;
        this.hostFinder = hostFinder;
        this.dens = ctx.getResources().getDisplayMetrics().density;
        instance = this;
        registerSystemEvents();
    }

    public boolean hasHost() { return hostFinder.get() != null; }

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

    private void registerSystemEvents() {
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
                    boolean on = i.getBooleanExtra("state", false);
                    event("airplane", "✈", on ? "Airplane mode on" : "Airplane mode off", "", 0xFF6A1B9A);
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
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        f.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(Intent.ACTION_HEADSET_PLUG);
        f.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(sysRx, f, Context.RECEIVER_NOT_EXPORTED);
            else ctx.registerReceiver(sysRx, f);
        } catch (Exception ignored) {}
        try {
            cmg = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onLost(Network n) {
                    h.postDelayed(() -> {
                        if (cmg != null && cmg.getActiveNetwork() == null)
                            event("nonet", "🚫", "No Internet", "", 0xFFE53935);
                    }, 1500);
                }
            };
            cmg.registerDefaultNetworkCallback(netCb, h);
        } catch (Exception ignored) { netCb = null; }
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

    private void event(String id, String emoji, String title, String text, int color) {
        if (!prefs.getBoolean("notic_en", false) || prefs.getBoolean("notic_paused", false)) return;
        if (!prefs.getBoolean("notic_ev_" + id, true)) return;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null || !pm.isInteractive()) return;
        if (!"dnd".equals(id) && dndBlocks()) return;
        if (!hasHost()) return;
        Item it = new Item();
        it.key = "ev_" + id;
        it.title = title;
        it.text = text;
        it.sig = title + text + SystemClock.elapsedRealtime();
        it.icon = emojiIcon(emoji, color);
        it.transientItem = true;
        post(it, false, false);
    }

    public void post(Item it, boolean wake, boolean sound) {
        Item old = null;
        for (Item x : items) if (x.key.equals(it.key)) { old = x; break; }
        if (old != null) {
            if (old.sig.equals(it.sig)) return;
            items.remove(old);
        }
        items.add(0, it);
        while (items.size() > 20) items.remove(items.size() - 1);
        Object[] host = hostFinder.get();
        if (host == null) return;
        ensurePill(host);
        if (pill == null) return;
        if (wake) wakeScreen();
        if (sound) playSound();
        announce();
        if (popup != null) renderPopup();
        if (it.transientItem) {
            final Item f = it;
            h.postDelayed(() -> removeItem(f), 3600);
        }
    }

    public void onRemoved(String key) {
        for (Item x : new ArrayList<>(items)) if (x.key.equals(key)) { removeItem(x); return; }
    }

    private void removeItem(Item it) {
        if (items.remove(it)) afterChange();
    }

    public void onScreenOff() {
        prefs.edit().putBoolean("notic_paused", false).apply();
        closePopup(false);
    }

    public boolean interceptTap(String key) {
        if (pill == null || items.isEmpty() || !statusVisible || key == null || !key.endsWith("_tap")) return false;
        Object[] host = hostFinder.get();
        if (host == null || !key.equals(host[1] + "_tap")) return false;
        if (popup != null) closePopup(true); else openPopup();
        return true;
    }

    public void relayout() {
        if (!prefs.getBoolean("notic_en", false)) {
            if (!items.isEmpty() || pill != null) { items.clear(); closePopup(false); removePill(); }
            return;
        }
        Object[] host = hostFinder.get();
        if (host == null) { closePopup(false); removePill(); return; }
        if (items.isEmpty()) return;
        boolean created = pill == null;
        boolean changed = ensurePill(host);
        if (pill != null && (created || changed)) showCompactNow();
    }

    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "EdgeBar:Notic").acquire(3000);
            }
        } catch (Exception ignored) {}
    }

    private void playSound() {
        try {
            if (ringtone != null) ringtone.stop();
            ringtone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {}
    }

    private String sig(WindowManager.LayoutParams lp) {
        return lp.width + "|" + lp.height + "|" + lp.x + "|" + lp.y + "|" + lp.gravity + "|" + pillH;
    }

    private WindowManager.LayoutParams computeLp(View hv) {
        if (!(hv.getLayoutParams() instanceof WindowManager.LayoutParams)) return null;
        WindowManager.LayoutParams b = (WindowManager.LayoutParams) hv.getLayoutParams();
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        pillH = Math.max(Math.round(28 * dens), Math.min(Math.max(b.height, 1), Math.round(40 * dens)));
        int w = Math.min(dm.widthPixels - Math.round(16 * dens), Math.round(300 * dens));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, Math.max(pillH, b.height), wmType, PILL_FLAGS, PixelFormat.TRANSLUCENT);
        lp.gravity = b.gravity;
        lp.x = b.x;
        lp.y = b.y;
        return lp;
    }

    private boolean ensurePill(Object[] host) {
        WindowManager.LayoutParams lp = computeLp((View) host[0]);
        if (lp == null) return false;
        String s = sig(lp);
        if (pill == null) {
            pill = new PillView(ctx);
            pill.setOnApplyWindowInsetsListener((v, ins) -> {
                boolean vis = Build.VERSION.SDK_INT < 30 || ins.isVisible(WindowInsets.Type.statusBars());
                statusVisible = vis;
                if (pill != null) pill.setAlpha(vis ? 1f : 0f);
                if (!vis) closePopup(false);
                return ins;
            });
            pillLp = lp;
            try { wm.addView(pill, pillLp); } catch (Exception e) { pill = null; return false; }
            lastSig = s;
            pill.requestApplyInsets();
            return true;
        }
        if (!s.equals(lastSig)) {
            pillLp.width = lp.width;
            pillLp.height = lp.height;
            pillLp.x = lp.x;
            pillLp.y = lp.y;
            pillLp.gravity = lp.gravity;
            try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
            lastSig = s;
            return true;
        }
        return false;
    }

    private void removePill() {
        h.removeCallbacks(settleRun);
        if (pill == null) return;
        pill.cancelAnim();
        try { wm.removeView(pill); } catch (Exception ignored) {}
        pill = null;
        lastSig = "";
    }

    private float compactW() { return Math.min(pillLp.width, pillH * 2.3f); }

    private void showCompactNow() {
        if (pill == null || items.isEmpty()) return;
        pill.cancelAnim();
        pill.shown = true;
        pill.setContent(items.get(0), items.size(), pillLp.width);
        pill.w = compactW();
        pill.hh = pillH;
        pill.textAlpha = 0f;
        pill.invalidate();
    }

    private void announce() {
        if (pill == null || items.isEmpty()) return;
        h.removeCallbacks(settleRun);
        final float ww = pillLp.width;
        final float cw = compactW();
        final int ph = pillH;
        pill.setContent(items.get(0), items.size(), ww);
        if (!pill.shown) {
            pill.shown = true;
            pill.w = ww;
            pill.hh = 5f * dens;
            pill.textAlpha = 0f;
            pill.animateTo(cw, ph, 0f, 260, new DecelerateInterpolator(), () -> {
                if (pill != null) pill.animateTo(ww, ph, 1f, 320, new OvershootInterpolator(0.9f), null);
            });
        } else {
            pill.animateTo(ww, ph, 1f, 300, new OvershootInterpolator(0.9f), null);
        }
        h.postDelayed(settleRun, 3200);
    }

    private void settle() {
        if (pill == null || items.isEmpty()) return;
        pill.animateTo(compactW(), pillH, 0f, 260, new DecelerateInterpolator(), null);
    }

    private void afterChange() {
        if (items.isEmpty()) {
            h.removeCallbacks(settleRun);
            closePopup(false);
            if (pill != null) {
                pill.animateTo(pill.w * 0.5f, 4f * dens, 0f, 200, new DecelerateInterpolator(), () -> { if (items.isEmpty()) removePill(); });
            }
            return;
        }
        if (pill != null) {
            pill.setContent(items.get(0), items.size(), pillLp.width);
            pill.invalidate();
        }
        if (popup != null) renderPopup();
    }

    private void openPopup() {
        if (items.isEmpty() || pill == null || !statusVisible) return;
        if (SystemClock.elapsedRealtime() - lastCloseMs < 400) return;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int maxH = Math.round(dm.heightPixels * 0.55f);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(240, 0, 0, 0));
        bg.setCornerRadius(28f * dens);
        root.setBackground(bg);
        int pd = Math.round(8 * dens);
        root.setPadding(pd, pd, pd, pd);
        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { closePopup(true); return true; }
            return false;
        });

        TextView clear = new TextView(ctx);
        clear.setText("Clear all  ✕");
        clear.setTextColor(Color.parseColor("#8AB4F8"));
        clear.setTextSize(12f);
        clear.setGravity(Gravity.END);
        clear.setPadding(pd, pd, pd, pd);
        clear.setOnClickListener(v -> closePopup(true));
        root.addView(clear, new LinearLayout.LayoutParams(-1, -2));

        popupBody = new LinearLayout(ctx);
        popupBody.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(ctx) {
            @Override protected void onMeasure(int wSpec, int hSpec) {
                super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        sv.addView(popupBody);
        root.addView(sv, new LinearLayout.LayoutParams(-1, -2));

        int pw = Math.min(dm.widthPixels - Math.round(24 * dens), Math.round(420 * dens));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(pw, WindowManager.LayoutParams.WRAP_CONTENT, wmType, POPUP_FLAGS, PixelFormat.TRANSLUCENT);
        boolean bottom = (pillLp.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM;
        lp.gravity = (bottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
        lp.y = pillLp.y + pillLp.height + Math.round(8 * dens);
        popup = root;
        renderPopup();
        try { wm.addView(root, lp); } catch (Exception e) { popup = null; popupBody = null; return; }
        root.setAlpha(0f);
        root.setScaleX(0.85f);
        root.setScaleY(0.85f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new OvershootInterpolator(1.0f)).start();
        h.removeCallbacks(settleRun);
        settle();
    }

    private void renderPopup() {
        if (popupBody == null) return;
        popupBody.removeAllViews();
        for (Item it : new ArrayList<>(items)) popupBody.addView(buildRow(it));
    }

    private View buildRow(final Item it) {
        int pd = Math.round(8 * dens);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pd, pd, pd, pd);
        ImageView iv = new ImageView(ctx);
        int is = Math.round(38 * dens);
        iv.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        if (it.icon != null) iv.setImageBitmap(it.icon);
        row.addView(iv);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
        clp.setMargins(pd, 0, pd, 0);
        col.setLayoutParams(clp);
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
            t2.setTextColor(Color.parseColor("#9AA0A6"));
            t2.setTextSize(12f);
            t2.setMaxLines(2);
            t2.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t2);
        }
        row.addView(col);
        TextView x = new TextView(ctx);
        x.setText("✕");
        x.setTextColor(Color.parseColor("#9AA0A6"));
        x.setPadding(pd * 2, pd, pd, pd);
        x.setOnClickListener(v -> { items.remove(it); afterChange(); });
        row.addView(x);
        row.setOnClickListener(v -> {
            if (it.intent != null) { try { it.intent.send(); } catch (Exception ignored) {} }
            items.remove(it);
            closePopup(false);
            afterChange();
        });
        return row;
    }

    private void closePopup(boolean markRead) {
        if (popup != null) {
            try { wm.removeView(popup); } catch (Exception ignored) {}
            popup = null;
            popupBody = null;
            lastCloseMs = SystemClock.elapsedRealtime();
        }
        if (markRead) { items.clear(); afterChange(); }
    }

    public void destroy() {
        h.removeCallbacksAndMessages(null);
        try { if (sysRx != null) ctx.unregisterReceiver(sysRx); } catch (Exception ignored) {}
        stopFullWatch();
        try { if (netCb != null && cmg != null) cmg.unregisterNetworkCallback(netCb); } catch (Exception ignored) {}
        closePopup(false);
        removePill();
        items.clear();
        if (instance == this) instance = null;
    }

    private class PillView extends View {
        Item item;
        int count;
        String line;
        float w, hh, textAlpha;
        boolean shown;
        ValueAnimator anim;
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint np = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        final RectF irect = new RectF();

        PillView(Context c) {
            super(c);
            bg.setColor(Color.BLACK);
            tp.setColor(Color.WHITE);
            tp.setTextSize(13f * dens);
            np.setColor(Color.WHITE);
            np.setTextSize(11f * dens);
            np.setTextAlign(Paint.Align.CENTER);
            np.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setContent(Item it, int cnt, float expandedW) {
            item = it;
            count = cnt;
            String s = it.title == null ? "" : it.title;
            if (it.text != null && !it.text.isEmpty()) s = s.isEmpty() ? it.text : s + ": " + it.text;
            s = s.replace('\n', ' ');
            float avail = expandedW - pillH * 2.0f;
            line = TextUtils.ellipsize(s, tp, Math.max(avail, 10f), TextUtils.TruncateAt.END).toString();
        }

        void cancelAnim() {
            if (anim != null) { ValueAnimator a = anim; anim = null; a.cancel(); }
        }

        void animateTo(final float tw, final float th, final float ta, long dur, TimeInterpolator in, final Runnable end) {
            cancelAnim();
            final float w0 = w, h0 = hh, a0 = textAlpha;
            final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            anim = va;
            va.setDuration(dur);
            va.setInterpolator(in);
            va.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                w = w0 + (tw - w0) * f;
                hh = h0 + (th - h0) * f;
                textAlpha = Math.max(0f, Math.min(1f, a0 + (ta - a0) * f));
                invalidate();
            });
            va.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;
                @Override public void onAnimationCancel(Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(Animator a) { if (!cancelled && end != null) end.run(); }
            });
            va.start();
        }

        @Override protected void onDraw(Canvas c) {
            if (!shown || item == null) return;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float l = cx - w / 2f, t = cy - hh / 2f, r = cx + w / 2f, b = cy + hh / 2f;
            rect.set(l, t, r, b);
            c.drawRoundRect(rect, hh / 2f, hh / 2f, bg);
            if (hh < pillH * 0.6f) return;
            float isz = hh * 0.62f;
            float ix = l + hh * 0.2f;
            if (item.icon != null) {
                irect.set(ix, cy - isz / 2f, ix + isz, cy + isz / 2f);
                c.drawBitmap(item.icon, null, irect, ip);
            }
            float bx = r - hh * 0.5f;
            if (count > 1) {
                bp.setColor(Color.parseColor("#FF3B30"));
                c.drawCircle(bx, cy, hh * 0.26f, bp);
                Paint.FontMetrics fm = np.getFontMetrics();
                c.drawText(String.valueOf(Math.min(count, 99)), bx, cy - (fm.ascent + fm.descent) / 2f, np);
            } else {
                bp.setColor(Color.parseColor("#34C759"));
                c.drawCircle(bx, cy, hh * 0.1f, bp);
            }
            if (textAlpha > 0.02f && line != null) {
                tp.setAlpha((int) (255 * textAlpha));
                float tx = ix + isz + hh * 0.2f;
                Paint.FontMetrics fm = tp.getFontMetrics();
                c.save();
                c.clipRect(tx, t, r - hh * 0.9f, b);
                c.drawText(line, tx, cy - (fm.ascent + fm.descent) / 2f, tp);
                c.restore();
            }
        }
    }
}
