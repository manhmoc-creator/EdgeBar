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
    private static final Set<String> selfCancel = new HashSet<>();
    public static synchronized void markSelfCancel(String k) { selfCancel.add(k); }
    public static synchronized boolean consumeSelfCancel(String k) { return selfCancel.remove(k); }

    public static class Item {
        String key, pkg, title, text, sig, appName;
        Bitmap icon, art;
        Icon largeIcon;
        PendingIntent intent;
        Notification.Action[] actions;
        Notification.Action reply;
        boolean media, transientItem;
    }

    private static final int NOT_TOUCHABLE = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    private static final int NOT_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    private static final int BASE_FLAGS = NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
    private static final int POPUP_FLAGS = BASE_FLAGS
        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
    private static final int BAR_COLOR = Color.argb(200, 96, 125, 139);

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
    private float scrW, scrH, pillH, ew, cw, px, cy, hl, ht, hr, hb, barR;
    private String lastGeo = "";
    private PillView pill;
    private WindowManager.LayoutParams pillLp;
    private boolean fullWin, expanded, statusVisible = true;
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

    private int dp(float v) { return Math.round(v * dens); }

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

    private void registerSystemEvents() {
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
        if (!prefs.getBoolean("notic_ev_" + id, true)) return;
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
        if (!hasHost()) return false;
        if (it.icon == null && it.pkg != null) it.icon = appIcon(it.pkg);
        Item old = null;
        for (Item x : items) if (x.key.equals(it.key)) { old = x; break; }
        if (old != null) {
            if (old.sig.equals(it.sig)) {
                if (it.media) {
                    old.actions = it.actions;
                    old.intent = it.intent;
                    old.largeIcon = it.largeIcon;
                    old.art = null;
                    if (popup != null) renderPopup();
                }
                return true;
            }
            items.remove(old);
        }
        items.add(0, it);
        cur = 0;
        while (items.size() > 20) items.remove(items.size() - 1);
        if (wake) wakeScreen();
        if (sound) playSound();
        announce();
        if (popup != null) renderPopup();
        if (it.transientItem) h.postDelayed(() -> removeItem(it), 3600);
        return true;
    }

    public void onRemoved(String key) {
        for (Item x : new ArrayList<>(items)) if (x.key.equals(key)) { removeItem(x); return; }
    }

    private void removeItem(Item it) {
        if (items.remove(it)) afterChange();
    }

    public void onScreenOff() {
        prefs.edit().putBoolean("notic_paused", false).apply();
        closePopup(false, false);
    }

    public boolean interceptGesture(String key) {
        if (pill == null || items.isEmpty() || !statusVisible || key == null || hostKey == null) return false;
        if (key.equals(hostKey + "_tap")) {
            if (popup != null) closePopup(false, true); else openPopup();
            return true;
        }
        if (key.equals(hostKey + "_long")) { openCurrentApp(); return true; }
        if (key.equals(hostKey + "_left")) { step(1); return true; }
        if (key.equals(hostKey + "_right")) { step(-1); return true; }
        return false;
    }

    private void step(int d) {
        int n = items.size();
        if (n < 2) return;
        cur = ((cur + d) % n + n) % n;
        announce();
    }

    private void openItem(Item it) {
        try {
            if (it.intent != null) it.intent.send();
            else if (it.pkg != null) {
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(it.pkg);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(li); }
            }
        } catch (Exception ignored) {}
    }

    private void openCurrentApp() {
        if (items.isEmpty()) return;
        Item it = items.get(Math.min(cur, items.size() - 1));
        openItem(it);
        if (!it.media) items.remove(it);
        closePopup(false, false);
        afterChange();
    }

    public void relayout() {
        if (!prefs.getBoolean("notic_en", false)) {
            if (!items.isEmpty() || pill != null) { items.clear(); closePopup(false, false); removePill(); }
            return;
        }
        if (items.isEmpty()) return;
        if (!bindHost()) { closePopup(false, false); removePill(); return; }
        if (pill == null) { announce(); return; }
        String s = (int) hl + "|" + (int) ht + "|" + (int) hr + "|" + (int) hb + "|" + (int) px + "|" + (int) cy;
        if (!s.equals(lastGeo)) {
            lastGeo = s;
            if (!pill.animating()) {
                pill.set(px, cy, expanded ? ew : cw, pillH, pillH / 2f, 1f, expanded ? 1f : 0f);
                winFit();
            }
        }
        applyVisibility();
    }

    private boolean bindHost() {
        Object[] hs = hostFinder.get();
        if (hs == null) return false;
        View v = (View) hs[0];
        if (hostView != v) { restoreHost(); hostView = v; }
        hostKey = (String) hs[1];
        if (v.getVisibility() != View.VISIBLE || !v.isAttachedToWindow() || v.getWidth() <= 0 || v.getHeight() <= 0) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        scrW = dm.widthPixels;
        scrH = dm.heightPixels;
        hl = loc[0];
        ht = loc[1];
        hr = hl + v.getWidth();
        hb = ht + v.getHeight();
        pillH = Math.max(30 * dens, Math.min(hb - ht, 44 * dens));
        ew = Math.min(scrW - 16 * dens, 280 * dens);
        cw = Math.min(ew, pillH * 2.3f);
        float m = 8 * dens;
        px = Math.max(ew / 2f + m, Math.min(scrW - ew / 2f - m, (hl + hr) / 2f));
        cy = Math.max(pillH / 2f, Math.min(scrH - pillH / 2f, (ht + hb) / 2f));
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
            if (vis != statusVisible) {
                statusVisible = vis;
                h.post(() -> { applyVisibility(); if (!statusVisible) closePopup(false, false); });
            }
            return ins;
        });
        try { wm.addView(pv, pillLp); } catch (Exception e) { return false; }
        pill = pv;
        fullWin = true;
        expanded = false;
        pv.requestApplyInsets();
        return true;
    }

    private void winFull() {
        if (pill == null) return;
        float y0 = (float) Math.floor(Math.min(ht, cy - pillH / 2f)) - 2;
        float h0 = (float) Math.ceil(Math.max(hb, cy + pillH / 2f)) + 2 - y0;
        pillLp.x = 0;
        pillLp.y = (int) y0;
        pillLp.width = (int) scrW;
        pillLp.height = (int) h0;
        pillLp.flags |= NOT_TOUCHABLE;
        pill.ox = 0;
        pill.oy = y0;
        fullWin = true;
        try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
    }

    private void winFit() {
        if (pill == null) return;
        int x = (int) Math.floor(pill.ccx - pill.w / 2f) - 1;
        int y = (int) Math.floor(pill.ccy - pill.h / 2f) - 1;
        pillLp.x = x;
        pillLp.y = y;
        pillLp.width = (int) Math.ceil(pill.w) + 2;
        pillLp.height = (int) Math.ceil(pill.h) + 2;
        if (statusVisible) pillLp.flags &= ~NOT_TOUCHABLE; else pillLp.flags |= NOT_TOUCHABLE;
        pill.ox = x;
        pill.oy = y;
        fullWin = false;
        try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
    }

    private void applyVisibility() {
        if (pill == null) return;
        pill.setAlpha(statusVisible ? 1f : 0f);
        if (hostView != null) {
            hostView.animate().cancel();
            hostView.setAlpha(statusVisible ? 0f : 1f);
        }
        if (!fullWin) {
            boolean want = !statusVisible;
            if (((pillLp.flags & NOT_TOUCHABLE) != 0) != want) {
                if (want) pillLp.flags |= NOT_TOUCHABLE; else pillLp.flags &= ~NOT_TOUCHABLE;
                try { wm.updateViewLayout(pill, pillLp); } catch (Exception ignored) {}
            }
        }
    }

    private void removePill() {
        h.removeCallbacks(settleRun);
        if (pill != null) {
            pill.cancelAnim();
            try { wm.removeView(pill); } catch (Exception ignored) {}
            pill = null;
        }
        expanded = false;
        lastGeo = "";
        restoreHost();
    }

    private void announce() {
        if (items.isEmpty() || !bindHost()) return;
        h.removeCallbacks(settleRun);
        Item it = items.get(Math.min(cur, items.size() - 1));
        final float hcx = (hl + hr) / 2f;
        if (pill == null) {
            if (!createPill()) return;
            final PillView pv = pill;
            pv.setContent(it, items.size(), ew);
            winFull();
            pv.set(hcx, cy, hr - hl, hb - ht, barR, 0f, 0f);
            applyVisibility();
            pv.go(hcx, cy, pillH, pillH, pillH / 2f, 0.6f, 0f, 240, new AccelerateDecelerateInterpolator(), () ->
                pv.go(px, cy, ew, pillH, pillH / 2f, 1f, 1f, 360, new OvershootInterpolator(0.9f), this::afterExpand));
        } else {
            pill.setContent(it, items.size(), ew);
            winFull();
            pill.go(px, cy, ew, pillH, pillH / 2f, 1f, 1f, 300, new OvershootInterpolator(0.9f), this::afterExpand);
        }
    }

    private void afterExpand() {
        expanded = true;
        winFit();
        h.removeCallbacks(settleRun);
        h.postDelayed(settleRun, 3200);
    }

    private void settle() {
        if (pill == null || items.isEmpty() || !bindHost()) return;
        winFull();
        expanded = false;
        pill.go(px, cy, cw, pillH, pillH / 2f, 1f, 0f, 260, new DecelerateInterpolator(), this::winFit);
    }

    private void afterChange() {
        if (cur >= items.size()) cur = Math.max(0, items.size() - 1);
        if (items.isEmpty()) {
            h.removeCallbacks(settleRun);
            closePopup(false, true);
            collapse();
            return;
        }
        if (pill != null) {
            pill.setContent(items.get(cur), items.size(), ew);
            pill.invalidate();
        }
        if (popup != null) renderPopup();
    }

    private void collapse() {
        if (pill == null) return;
        h.removeCallbacks(settleRun);
        if (!bindHost()) { removePill(); return; }
        final PillView pv = pill;
        final float hcx = (hl + hr) / 2f;
        winFull();
        expanded = false;
        pv.go(px, cy, pillH, pillH, pillH / 2f, 0.6f, 0f, 200, new AccelerateInterpolator(), () ->
            pv.go(hcx, cy, hr - hl, hb - ht, barR, 0f, 0f, 260, new DecelerateInterpolator(), () -> { if (items.isEmpty()) removePill(); }));
    }

    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive())
                pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "EdgeBar:Notic").acquire(3000);
        } catch (Exception ignored) {}
    }

    private void playSound() {
        try {
            if (ringtone != null) ringtone.stop();
            ringtone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {}
    }

    private void openPopup() {
        if (items.isEmpty() || pill == null || !statusVisible || !bindHost()) return;
        if (SystemClock.elapsedRealtime() - lastCloseMs < 400) return;
        h.removeCallbacks(settleRun);
        final int maxH = Math.round(scrH * 0.55f);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(244, 0, 0, 0));
        bg.setCornerRadius(dp(26));
        root.setBackground(bg);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { closePopup(true, true); return true; }
            return false;
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
        clear.setOnClickListener(v -> closePopup(true, true));
        head.addView(ttl);
        head.addView(clear);
        root.addView(head);

        popupBody = new LinearLayout(ctx);
        popupBody.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(ctx) {
            @Override protected void onMeasure(int wSpec, int hSpec) {
                super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        sv.addView(popupBody);
        root.addView(sv, new LinearLayout.LayoutParams(-1, -2));

        int pw = (int) Math.min(scrW - dp(24), dp(420));
        popup = root;
        renderPopup();
        root.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) scrH, View.MeasureSpec.AT_MOST));
        int ph = root.getMeasuredHeight();
        popupLp = new WindowManager.LayoutParams(pw, WindowManager.LayoutParams.WRAP_CONTENT, wmType, POPUP_FLAGS, PixelFormat.TRANSLUCENT);
        popupLp.gravity = Gravity.TOP | Gravity.LEFT;
        popupLp.x = (int) Math.max(dp(8), Math.min(scrW - pw - dp(8), px - pw / 2f));
        boolean below = cy < scrH / 2f;
        popupLp.y = below ? (int) (cy + pillH / 2f + dp(8)) : (int) Math.max(dp(8), cy - pillH / 2f - dp(8) - ph);
        try { wm.addView(root, popupLp); } catch (Exception e) { popup = null; popupBody = null; return; }
        root.setPivotX(px - popupLp.x);
        root.setPivotY(cy - popupLp.y);
        root.setAlpha(0f);
        root.setScaleX(0.12f);
        root.setScaleY(0.12f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(0.8f)).start();
    }

    private void closePopup(boolean markRead, boolean animate) {
        final LinearLayout p = popup;
        if (p != null) {
            popup = null;
            popupBody = null;
            releaseIme(p);
            lastCloseMs = SystemClock.elapsedRealtime();
            if (animate) {
                p.animate().alpha(0f).scaleX(0.1f).scaleY(0.1f).setDuration(220).setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> { try { wm.removeView(p); } catch (Exception ignored) {} }).start();
            } else {
                try { wm.removeView(p); } catch (Exception ignored) {}
            }
        }
        if (markRead) {
            items.removeIf(x -> !x.media);
            afterChange();
        }
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

    private void renderPopup() {
        if (popupBody == null) return;
        if (popupLp != null && (popupLp.flags & NOT_FOCUSABLE) == 0) return; // UX: đang gõ reply thì không dựng lại, tránh mất chữ
        popupBody.removeAllViews();
        for (Item it : new ArrayList<>(items)) popupBody.addView(buildRow(it));
    }

    private GradientDrawable rounded(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    private View buildRow(final Item it) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(0xFF1C1C1E, dp(18)));
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(clp);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(ctx);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        iv.setBackground(rounded(0xFF2C2C2E, dp(10)));
        iv.setClipToOutline(true);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap pic = loadLarge(it);
        if (pic != null) iv.setImageBitmap(pic);
        else if (it.icon != null) iv.setImageBitmap(it.icon);
        top.addView(iv);

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
        if (!it.media) {
            TextView x = new TextView(ctx);
            x.setText("✕");
            x.setTextColor(Color.parseColor("#8E8E93"));
            x.setPadding(dp(10), dp(8), dp(4), dp(8));
            x.setOnClickListener(v -> { items.remove(it); afterChange(); });
            top.addView(x);
        }
        top.setOnClickListener(v -> {
            openItem(it);
            if (!it.media) items.remove(it);
            closePopup(false, true);
            afterChange();
        });
        card.addView(top);
        if (it.media) card.addView(buildMediaControls(it));
        else if (it.reply != null) card.addView(buildReplyBar(it));
        return card;
    }

    private View buildMediaControls(final Item it) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(8), 0, 0);
        if (it.actions != null) {
            for (int i = 0; i < it.actions.length && i < 5; i++) {
                final Notification.Action a = it.actions[i];
                if (a == null || a.actionIntent == null) continue;
                ImageView b = new ImageView(ctx);
                try {
                    Icon ic = a.getIcon();
                    if (ic != null) {
                        Drawable d = ic.loadDrawable(ctx);
                        if (d != null) { d = d.mutate(); d.setTint(Color.WHITE); b.setImageDrawable(d); }
                    }
                } catch (Exception ignored) {}
                b.setPadding(dp(10), dp(8), dp(10), dp(8));
                b.setOnClickListener(v -> { try { a.actionIntent.send(); } catch (Exception ignored) {} });
                row.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1f));
            }
        }
        return row;
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
        TextView send = new TextView(ctx);
        send.setText("Send");
        send.setTextColor(Color.parseColor("#0A84FF"));
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setTextSize(13.5f);
        send.setPadding(dp(12), dp(8), dp(6), dp(8));
        send.setOnClickListener(v -> sendReply(it, et));
        row.addView(et);
        row.addView(send);
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

    public void destroy() {
        h.removeCallbacksAndMessages(null);
        try { if (sysRx != null) ctx.unregisterReceiver(sysRx); } catch (Exception ignored) {}
        stopFullWatch();
        try { if (cmg != null) { if (netCb != null) cmg.unregisterNetworkCallback(netCb); if (wifiCb != null) cmg.unregisterNetworkCallback(wifiCb); if (vpnCb != null) cmg.unregisterNetworkCallback(vpnCb); } } catch (Exception ignored) {}
        try { if (audio != null && micCb != null) audio.unregisterAudioRecordingCallback(micCb); } catch (Exception ignored) {}
        try { if (camMgr != null) { if (camCb != null) camMgr.unregisterAvailabilityCallback(camCb); if (torchCb != null) camMgr.unregisterTorchCallback(torchCb); } } catch (Exception ignored) {}
        closePopup(false, false);
        removePill();
        items.clear();
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

    private class PillView extends View {
        Item item;
        int count;
        String line;
        float ox, oy, ccx, ccy, w, r, mix, textAlpha;
        float h;
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
            tp.setColor(Color.WHITE);
            tp.setTextSize(13f * dens);
            np.setColor(Color.WHITE);
            np.setTextSize(11f * dens);
            np.setTextAlign(Paint.Align.CENTER);
            np.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void set(float cx, float cyy, float ww, float hh, float rr, float m, float ta) {
            cancelAnim();
            ccx = cx; ccy = cyy; w = ww; h = hh; r = rr; mix = m; textAlpha = ta;
            invalidate();
        }

        boolean animating() { return anim != null; }

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

        void go(float tcx, float tcy, float tw, float th, float tr, float tm, float ta, long dur, TimeInterpolator in, final Runnable end) {
            cancelAnim();
            final float[] f0 = {ccx, ccy, w, h, r, mix, textAlpha};
            final float[] f1 = {tcx, tcy, tw, th, tr, tm, ta};
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
            return hv != null && hv.dispatchTouchEvent(e); // UX: viên thuốc chuyển nguyên cử chỉ cho bar host nên 2 chạm/vuốt vẫn theo rule của bar
        }

        @Override protected void onDraw(Canvas c) {
            if (item == null) return;
            float cx = ccx - ox, cyy = ccy - oy;
            float l = cx - w / 2f, t = cyy - h / 2f, rr = cx + w / 2f, b = cyy + h / 2f;
            rect.set(l, t, rr, b);
            bg.setColor(blend(BAR_COLOR, Color.BLACK, mix));
            float rad = Math.min(r, Math.min(w, h) / 2f);
            c.drawRoundRect(rect, rad, rad, bg);
            if (w < pillH * 1.4f || h < pillH * 0.8f) return;
            float isz = h * 0.62f;
            float ix = l + h * 0.2f;
            if (item.icon != null) {
                irect.set(ix, cyy - isz / 2f, ix + isz, cyy + isz / 2f);
                c.drawBitmap(item.icon, null, irect, ip);
            }
            float bx = rr - h * 0.5f;
            if (count > 1) {
                bp.setColor(Color.parseColor("#FF3B30"));
                c.drawCircle(bx, cyy, h * 0.26f, bp);
                Paint.FontMetrics fm = np.getFontMetrics();
                c.drawText(String.valueOf(Math.min(count, 99)), bx, cyy - (fm.ascent + fm.descent) / 2f, np);
            } else {
                bp.setColor(Color.parseColor("#34C759"));
                c.drawCircle(bx, cyy, h * 0.1f, bp);
            }
            if (textAlpha > 0.02f && line != null) {
                tp.setAlpha((int) (255 * textAlpha));
                float tx = ix + isz + h * 0.2f;
                Paint.FontMetrics fm = tp.getFontMetrics();
                c.save();
                c.clipRect(tx, t, rr - h * 0.9f, b);
                c.drawText(line, tx, cyy - (fm.ascent + fm.descent) / 2f, tp);
                c.restore();
            }
        }
    }
}
