package com.manhmoc.edgebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Âm chạm ĐỘC LẬP — dùng ToneGenerator route qua STREAM_MUSIC để không bị
 * Do Not Disturb / chặn sonification của ROM làm câm.
 *
 * V19.12.3.6.44 FIX:
 *  - Bỏ AudioTrack MODE_STATIC (stop() throw IllegalStateException khi track
 *    chưa từng play -> câm vĩnh viễn dù vol=100).
 *  - ToneGenerator có sẵn cache nội bộ, chỉ cần setVolume() + startTone(),
 *    không phải write/reload PCM thủ công.
 *  - Auto-release sau 30s không dùng để nhả codec.
 */
public class TouchSoundHelper {
    private static final long MIN_GAP_MS = 35;         // chống double-fire
    private static final long IDLE_RELEASE_MS = 30_000;
    private static final int TONE_MS = 30;             // độ dài tiếng "tách"

    private static ToneGenerator tg;
    private static int lastVol = -1;
    private static long lastPlayMs = 0;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static final Runnable idleRelease = TouchSoundHelper::release;

    public static synchronized void play(Context c, SharedPreferences prefs) {
        int vol = prefs.getInt("touch_sound_vol", 40);
        if (vol <= 0) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastPlayMs < MIN_GAP_MS) return;
        lastPlayMs = now;

        try {
            if (tg == null) {
                // Route qua STREAM_MUSIC: KHÔNG bị DND chặn, KHÔNG phụ thuộc
                // cài đặt "Touch sounds" của hệ thống.
                tg = new ToneGenerator(AudioManager.STREAM_MUSIC, clampVol(vol));
                lastVol = vol;
            } else if (vol != lastVol) {
                tg.setVolume(clampVol(vol));
                lastVol = vol;
            }
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS);

            h.removeCallbacks(idleRelease);
            h.postDelayed(idleRelease, IDLE_RELEASE_MS);
        } catch (Exception e) {
            release();
        }
    }

    public static synchronized void release() {
        h.removeCallbacks(idleRelease);
        if (tg != null) {
            try { tg.release(); } catch (Exception ignored) {}
            tg = null;
            lastVol = -1;
        }
    }

    /** ToneGenerator nhận volume trong [0, 100], không phải [0.0, 1.0]. */
    private static int clampVol(int v) {
        return Math.max(1, Math.min(100, v));
    }
}
