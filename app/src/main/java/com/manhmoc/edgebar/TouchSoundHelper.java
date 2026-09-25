package com.manhmoc.edgebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public class TouchSoundHelper {
    private static final long MIN_GAP_MS = 35;
    private static final long IDLE_RELEASE_MS = 30_000;
    private static final int TONE_MS = 18;
    private static final int DEFAULT_VOL = 70; // % của âm lượng STREAM_ALARM hiện tại

    // [MỚI] 8 kiểu âm — chỉ là hằng số có sẵn trong Android SDK (ToneGenerator),
    // KHÔNG phải file âm thanh -> Zero dung lượng/RAM thêm, chỉ đổi index int lúc phát.
    static final int[] TONE_TYPES = {
        ToneGenerator.TONE_PROP_ACK,
        ToneGenerator.TONE_PROP_BEEP,
        ToneGenerator.TONE_PROP_NACK,
        ToneGenerator.TONE_PROP_PROMPT,
        ToneGenerator.TONE_CDMA_PIP,
        ToneGenerator.TONE_SUP_PIP,
        ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
        ToneGenerator.TONE_SUP_CONFIRM
    };
    static final String[] TONE_TYPE_NAMES_EN = {
        "Soft Tick", "Sharp Beep", "Low Tock", "Gentle Prompt",
        "Short Pip", "Double Pip", "Alert Guard", "Confirm"
    };
    static final String[] TONE_TYPE_NAMES_VI = {
        "Tách mềm", "Bíp sắc", "Tạch trầm", "Nhắc nhẹ",
        "Pip ngắn", "Pip đôi", "Cảnh báo nhẹ", "Xác nhận"
    };

    private static ToneGenerator tg;
    private static int lastVol = -1;
    private static long lastPlayMs = 0;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static final Runnable idleRelease = TouchSoundHelper::release;

    public static synchronized void play(Context c, SharedPreferences prefs) {
        int vol = prefs.getInt("touch_sound_vol", DEFAULT_VOL);
        if (vol <= 0) return;
        int typeIdx = Math.max(0, Math.min(TONE_TYPES.length - 1, prefs.getInt("touch_sound_type", 0)));

        long now = SystemClock.elapsedRealtime();
        if (now - lastPlayMs < MIN_GAP_MS) return;
        lastPlayMs = now;

        try {
            // [SỬA] STREAM_ALARM thay STREAM_MUSIC — âm báo thức thường luôn để to
            // và hiếm khi chỉnh, nên âm chạm không còn bị "nhỏ xíu" theo âm lượng media.
            if (tg == null || vol != lastVol) {
                if (tg != null) { try { tg.release(); } catch (Exception ignored) {} tg = null; }
                tg = new ToneGenerator(AudioManager.STREAM_ALARM, clampVol(vol));
                lastVol = vol;
            }
            // Đổi TYPE chỉ cần truyền tham số vào startTone(), không cần tạo lại ToneGenerator
            tg.startTone(TONE_TYPES[typeIdx], TONE_MS);

            h.removeCallbacks(idleRelease);
            h.postDelayed(idleRelease, IDLE_RELEASE_MS);
        } catch (Exception e) {
            release();
        }
    }

    public static synchronized void release() {
        h.removeCallbacks(idleRelease);
        if (tg != null) { try { tg.release(); } catch (Exception ignored) {} tg = null; lastVol = -1; }
    }

    private static int clampVol(int v) { return Math.max(1, Math.min(100, v)); }
}
