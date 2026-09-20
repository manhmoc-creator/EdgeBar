package com.manhmoc.edgebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * V19.12.3.6.44 FIX — Âm chạm độc lập route qua STREAM_MUSIC:
 *  (1) KHÔNG dùng tg.setVolume() — API đó KHÔNG tồn tại trên ToneGenerator,
 *      đây là nguyên nhân lỗi build "cannot find symbol method setVolume(int)".
 *      Muốn đổi volume -> PHẢI release() rồi new ToneGenerator() lại.
 *  (2) Đổi tone từ TONE_PROP_BEEP (sắc, "bíp") sang TONE_PROP_ACK (mềm, "tách"
 *      nhẹ như soft-key của điện thoại cũ) — nghe êm hơn hẳn.
 *  (3) Rút ngắn TONE_MS từ 30ms -> 18ms, volume mặc định 40 -> 25 để "mềm chút".
 *  (4) Cache ToneGenerator theo giá trị volume đã tạo — chỉ release/new khi
 *      user thực sự đổi slider. Bình thường = chỉ startTone() = 0 chi phí thêm.
 *  (5) Auto-release sau 30s idle để nhả codec — Zero RAM khi không dùng.
 */
public class TouchSoundHelper {
    private static final long MIN_GAP_MS = 35;         // chống double-fire
    private static final long IDLE_RELEASE_MS = 30_000;
    private static final int TONE_MS = 18;             // [FIX] ngắn hơn 30 -> 18ms
    private static final int DEFAULT_VOL = 25;         // [FIX] mặc định dịu hơn 40

    private static ToneGenerator tg;
    private static int lastVol = -1;
    private static long lastPlayMs = 0;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static final Runnable idleRelease = TouchSoundHelper::release;

    public static synchronized void play(Context c, SharedPreferences prefs) {
        int vol = prefs.getInt("touch_sound_vol", DEFAULT_VOL);
        if (vol <= 0) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastPlayMs < MIN_GAP_MS) return;
        lastPlayMs = now;

        try {
            // [FIX] Không còn setVolume() — chỉ tái tạo khi volume thực sự đổi.
            if (tg == null || vol != lastVol) {
                if (tg != null) {
                    try { tg.release(); } catch (Exception ignored) {}
                    tg = null;
                }
                tg = new ToneGenerator(AudioManager.STREAM_MUSIC, clampVol(vol));
                lastVol = vol;
            }
            // [FIX] TONE_PROP_ACK: tiếng "tách" mềm của soft-key, KHÔNG phải
            // TONE_PROP_BEEP (bíp sắc như lỗi máy). Đây là nguyên nhân "nghe sắc".
            tg.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS);

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

    private static int clampVol(int v) {
        return Math.max(1, Math.min(100, v));
    }
}
