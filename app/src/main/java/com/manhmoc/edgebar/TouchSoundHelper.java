package com.manhmoc.edgebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Âm chạm ĐỘC LẬP — không phụ thuộc cài đặt "Âm chạm" của hệ thống. */
public class TouchSoundHelper {
    private static final int RATE = 22050;
    private static final long MIN_GAP_MS = 35;
    private static final long IDLE_RELEASE_MS = 30_000;
    private static AudioTrack track;
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
            if (track == null) track = build();
            if (track == null) return;
            track.setVolume(Math.min(1f, vol / 100f));
            track.stop();
            track.reloadStaticData();
            track.play();
            h.removeCallbacks(idleRelease);
            h.postDelayed(idleRelease, IDLE_RELEASE_MS);
        } catch (Exception e) { release(); }
    }

    public static synchronized void release() {
        h.removeCallbacks(idleRelease);
        if (track != null) {
            try { track.release(); } catch (Exception ignored) {}
            track = null;
        }
    }

    private static AudioTrack build() {
        try {
            short[] pcm = new short[RATE * 14 / 1000]; // ~14ms
            for (int i = 0; i < pcm.length; i++) {
                double t = i / (double) RATE;
                double env = Math.exp(-t * 380);
                pcm[i] = (short) (Math.sin(2 * Math.PI * 1900 * t) * env * 28000);
            }
            AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (Exception e) { return null; }
    }
}
