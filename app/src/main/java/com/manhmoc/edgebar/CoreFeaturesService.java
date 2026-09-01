      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

/**
 * Service sống SUỐT vòng đời app, KHÔNG phụ thuộc Accessibility bật/tắt,
 * KHÔNG bị bất kỳ watchdog Homacc/Homeb nào đụng tới.
 * Chứa Panel (Lenap) + Bubble (Assistive Touch) — 2 tính năng phải luôn sống
 * bất kể đang ở Lock/Homacc/Homeb.
 */
public class CoreFeaturesService extends Service {
    public static boolean isRunning = false;
    private WindowManager wm;
    private SharedPreferences prefs;
    private PanelEngine panelEngine;
    private AssistiveBubbleEngine bubbleEngine;
    private BroadcastReceiver receiver;

    @Override public IBinder onBind(Intent i) { return null; }
        private final android.os.Handler panelDebounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable panelDebounceRunnable = null;
    private static final long PANEL_DEBOUNCE_MS = 120;

    private final SharedPreferences.OnSharedPreferenceChangeListener coreFeaturesPrefListener = (p, k) -> {
        if (k == null) return;
        if (k.startsWith("bubble_")) {
            bubbleEngine.onPrefChanged(k);
            return;
        }
        if (k.startsWith("pack_panel_") || (k.startsWith("shortcut_") && k.endsWith("_icon_override"))) {
            if (panelDebounceRunnable != null) panelDebounceHandler.removeCallbacks(panelDebounceRunnable);
            panelDebounceRunnable = () -> panelEngine.onPrefChanged(k);
            panelDebounceHandler.postDelayed(panelDebounceRunnable, PANEL_DEBOUNCE_MS);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        String cid = "eb_core_features";
        NotificationChannel c = new NotificationChannel(cid, "Core Features", NotificationManager.IMPORTANCE_LOW);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("EdgeBar Core")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true).build();
        startForeground(88, n);

        // isAnyMode=false -> luôn dùng TYPE_APPLICATION_OVERLAY (SYSTEM_ALERT_WINDOW),
        // không phụ thuộc AccessibilityService còn sống hay không.
        panelEngine = new PanelEngine(this, wm, prefs, false);
        bubbleEngine = new AssistiveBubbleEngine(this, wm, prefs, false);
        panelEngine.rebuildAll();
        bubbleEngine.rebuild();
        prefs.registerOnSharedPreferenceChangeListener(coreFeaturesPrefListener);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String act = i.getAction();
                if ("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED".equals(act)) {
                    panelEngine.rebuildAll();
                } else if ("com.manhmoc.edgebar.OPEN_PANEL_REQUEST".equals(act)) {
                    String id = i.getStringExtra("panel_id");
                    if (id != null) panelEngine.togglePanel(id);
                } else if ("com.manhmoc.edgebar.PANEL_TEST_TOGGLE".equals(act)) {
                    String id = i.getStringExtra("panel_id");
                    if (id != null) panelEngine.setForceTest(id, i.getBooleanExtra("on", false));
                                } else if ("com.manhmoc.edgebar.SYNC_STATE".equals(act)
                        || Intent.ACTION_SCREEN_ON.equals(act)
                        || Intent.ACTION_USER_PRESENT.equals(act)
                        || Intent.ACTION_SCREEN_OFF.equals(act)) {
                    panelEngine.rebuildAll(); // Panel tự đọc KeyguardManager để show/hide đúng
                } else if ("com.manhmoc.edgebar.BUBBLE_SET_TOUCHABLE".equals(act)) {
                    bubbleEngine.setBubbleTouchable(i.getBooleanExtra("touchable", true));
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction("com.manhmoc.edgebar.BUBBLE_SET_TOUCHABLE"); 
        f.addAction("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED");
        f.addAction("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
        f.addAction("com.manhmoc.edgebar.PANEL_TEST_TOGGLE");
        f.addAction("com.manhmoc.edgebar.SYNC_STATE");
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

        @Override public void onDestroy() {
        isRunning = false;
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        prefs.unregisterOnSharedPreferenceChangeListener(coreFeaturesPrefListener);
        if (bubbleEngine != null) bubbleEngine.destroy();
        super.onDestroy();
    }
}
