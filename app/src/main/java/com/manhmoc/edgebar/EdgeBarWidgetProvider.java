package com.manhmoc.edgebar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.RemoteViews;
import java.util.HashMap;
import java.util.Map;

public class EdgeBarWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TAP = "com.manhmoc.edgebar.WIDGET_TAP";
    private static final String EXTRA_WIDGET_ID = "wid";
    private static final long DTAP_WINDOW_MS = 280;

    private static final Map<Integer, Long> lastTapMs = new HashMap<>();
    private static final Map<Integer, Runnable> pendingTap = new HashMap<>();
    private static final Handler dtapHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_edgebar);
            Intent i = new Intent(ctx, EdgeBarWidgetProvider.class);
            i.setAction(ACTION_TAP);
            i.putExtra(EXTRA_WIDGET_ID, id);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id, i, flags);
            v.setOnClickPendingIntent(R.id.widget_root, pi);
            mgr.updateAppWidget(id, v);
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_TAP.equals(intent.getAction())) {
            int wid = intent.getIntExtra(EXTRA_WIDGET_ID, -1);
            if (wid >= 0) handleTap(ctx, wid);
            return;
        }
        super.onReceive(ctx, intent);
    }

    private void handleTap(Context ctx, int wid) {
        SharedPreferences prefs = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        String tap = prefs.getString("widget_" + wid + "_tap_act", "NONE");
        String dtap = prefs.getString("widget_" + wid + "_dtap_act", "NONE");

        if (dtap == null || dtap.equals("NONE") || dtap.isEmpty()) {
            fireAction(ctx, wid, "tap", tap);
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastTapMs.get(wid);
        if (last != null && (now - last) < DTAP_WINDOW_MS) {
            Runnable p = pendingTap.remove(wid);
            if (p != null) dtapHandler.removeCallbacks(p);
            lastTapMs.remove(wid);
            fireAction(ctx, wid, "dtap", dtap);
        } else {
            lastTapMs.put(wid, now);
            final int fWid = wid;
            final String fTap = tap;
            Runnable r = () -> {
                pendingTap.remove(fWid);
                lastTapMs.remove(fWid);
                if (fTap != null && !fTap.equals("NONE") && !fTap.isEmpty())
                    fireAction(ctx, fWid, "tap", fTap);
            };
            pendingTap.put(wid, r);
            dtapHandler.postDelayed(r, DTAP_WINDOW_MS + 20);
        }
    }

    private void fireAction(Context ctx, int wid, String suffix, String action) {
        if (action == null || action.isEmpty() || action.equals("NONE")) return;
        SharedPreferences prefs = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);

        if (prefs.getBoolean("widget_" + wid + "_vib", true)) {
            try {
                Vibrator vb = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vb != null) {
                    int dur = prefs.getInt("vib_dur", 30);
                    if (Build.VERSION.SDK_INT >= 26)
                        vb.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
                    else vb.vibrate(dur);
                }
            } catch (Exception ignored) {}
        }
        if (prefs.getBoolean("widget_" + wid + "_snd", false)) TouchSoundHelper.play(ctx, prefs);
        if (prefs.getBoolean("widget_" + wid + "_anim", true)) {
            Intent anim = new Intent("com.manhmoc.edgebar.TEST_ANIM");
            anim.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(anim);
        }

        String[] acts = action.split(",");
        for (String a : acts) {
            String at = a.trim();
            if (at.isEmpty()) continue;
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            ipc.setPackage(ctx.getPackageName());
            if (at.equals("LAUNCH_APP")) {
                ipc.putExtra("act", "LAUNCH_APP");
                ipc.putExtra("launch_pkg", prefs.getString("widget_" + wid + "_" + suffix + "_pkg", ""));
            } else if (at.startsWith("RUN_SHORTCUT_")) {
                ipc.putExtra("act", "RUN_SHORTCUT");
                ipc.putExtra("shortcut_id", at.substring("RUN_SHORTCUT_".length()));
            } else if (at.equals("RUN_SHORTCUT")) {
                ipc.putExtra("act", "RUN_SHORTCUT");
                ipc.putExtra("shortcut_id", prefs.getString("widget_" + wid + "_" + suffix + "_shortcut_id", ""));
            } else {
                ipc.putExtra("act", at);
            }
            ctx.sendBroadcast(ipc);
        }
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor ed = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            ed.remove("widget_" + id + "_tap_act")
              .remove("widget_" + id + "_dtap_act")
              .remove("widget_" + id + "_tap_pkg")
              .remove("widget_" + id + "_dtap_pkg")
              .remove("widget_" + id + "_tap_shortcut_id")
              .remove("widget_" + id + "_dtap_shortcut_id")
              .remove("widget_" + id + "_vib")
              .remove("widget_" + id + "_snd");
              .remove("widget_" + id + "_anim");
        }
        ed.apply();
    }
}
