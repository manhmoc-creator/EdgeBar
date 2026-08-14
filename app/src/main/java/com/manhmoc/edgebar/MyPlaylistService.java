      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V19.12.3.6.39 — Phát nhạc từ "Download/My Playlist" (Bộ nhớ trong).
 * Thay thế hoàn toàn PLAY_LAST_MUSIC cũ (điều khiển session "Files by Google"
 * qua NotificationListener). Không còn cần quyền Notification Access.
 *
 * Tối ưu Pixel 2XL:
 * - 1 MediaPlayer duy nhất sống suốt vòng đời Service, reset() giữa các bài
 *   thay vì release()+new mỗi lần -> tiết kiệm cấp phát native object.
 * - setWakeMode(PARTIAL_WAKE_LOCK) giao thẳng cho MediaPlayer -> zero WakeLock
 *   tự quản lý, không rủi ro leak khi Service bị OS kill đột ngột.
 * - prepareAsync() thay vì prepare() -> không block main thread khi mở file.
 * - Danh sách bài quét ĐÚNG 1 LẦN lúc bấm action (MediaStore query có index),
 *   không Thread/Handler polling nào chạy nền.
 * - Next-track 100% event-driven qua OnCompletionListener, zero CPU lúc đang phát.
 */
public class MyPlaylistService extends Service {
    public static boolean isRunning = false;
    public static boolean isPaused = false;

    public static final String ACTION_TOGGLE = "com.manhmoc.edgebar.MYPLAYLIST_TOGGLE";
    public static final String ACTION_NEXT = "com.manhmoc.edgebar.MYPLAYLIST_NEXT";
    public static final String ACTION_PREV = "com.manhmoc.edgebar.MYPLAYLIST_PREV";
    public static final String ACTION_STOP = "com.manhmoc.edgebar.MYPLAYLIST_STOP";

    private static final String RELATIVE_PATH_PREFIX = "Download/My Playlist";
    private static final String CHANNEL_ID = "eb_my_playlist";
    private static final int NOTIF_ID = 95;

    private MediaPlayer player;
    private MediaSession session;
    private final List<Uri> tracks = new ArrayList<>();
    private final List<String> trackNames = new ArrayList<>();
    private int currentIndex = 0;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) { stopPlayback(); return START_NOT_STICKY; }
        if (ACTION_NEXT.equals(action)) { playIndex(currentIndex + 1); return START_NOT_STICKY; }
        if (ACTION_PREV.equals(action)) { playIndex(currentIndex - 1); return START_NOT_STICKY; }

        if (ACTION_TOGGLE.equals(action) || action == null) {
            if (isRunning) togglePause();
            else loadPlaylistAndStart();
        }
        return START_NOT_STICKY;
    }

    // ==================== NẠP PLAYLIST & PHÁT BÀI ĐẦU TIÊN ====================
    private void loadPlaylistAndStart() {
        startForegroundNotif("Đang tải playlist…", false);
        tracks.clear(); trackNames.clear();

        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME};
        String sel = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = {RELATIVE_PATH_PREFIX + "%"};
        List<Object[]> found = new ArrayList<>(); // {name, uri}
        try (Cursor c = getContentResolver().query(collection, proj, sel, args, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    found.add(new Object[]{name, android.content.ContentUris.withAppendedId(collection, id)});
                }
            }
        } catch (SecurityException se) {
            showErrorNotif("⚠️ Chưa cấp quyền Truy cập Nhạc — mở app EdgeBar để cấp quyền.");
            return;
        } catch (Exception ignored) {}

        // Sắp xếp tự nhiên theo tên file: 01,02,...,10 (không phải 1,10,2 kiểu ASCII)
        Collections.sort(found, (a, b) -> naturalCompare((String) a[0], (String) b[0]));
        for (Object[] item : found) { trackNames.add((String) item[0]); tracks.add((Uri) item[1]); }

        if (tracks.isEmpty()) {
            showErrorNotif("⚠️ Không tìm thấy bài hát nào trong Download/My Playlist");
            return;
        }
        ensureSession();
        playIndex(0); // LUÔN bắt đầu từ bài đầu tiên của playlist
    }
    private void ensureSession() {
        if (session != null) return;
        session = new MediaSession(this, "EdgeBarMyPlaylist");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { togglePause(); }
            @Override public void onPause() { togglePause(); }
            @Override public void onSkipToNext() { playIndex(currentIndex + 1); }
            @Override public void onSkipToPrevious() { playIndex(currentIndex - 1); }
            @Override public void onStop() { stopPlayback(); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
    }

    private void playIndex(int idx) {
        if (tracks.isEmpty()) return;
        if (idx < 0) idx = tracks.size() - 1;
        if (idx >= tracks.size()) idx = 0; // hết playlist -> lặp lại từ đầu
        currentIndex = idx;

        if (player == null) {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnCompletionListener(mp -> playIndex(currentIndex + 1));
            player.setOnErrorListener((mp, what, extra) -> { playIndex(currentIndex + 1); return true; });
        } else {
            try { player.reset(); } catch (Exception ignored) {}
        }
        player.setOnPreparedListener(mp -> {
            mp.start();
            isRunning = true; isPaused = false;
            updateSessionState(true);
            startForegroundNotif(trackNames.get(currentIndex), false);
        });
        try {
            player.setDataSource(this, tracks.get(currentIndex));
            player.prepareAsync(); // không block main thread
        } catch (Exception e) {
            if (tracks.size() > 1) playIndex(currentIndex + 1); else stopPlayback();
        }
    }

    private void togglePause() {
        if (player == null || !isRunning) return;
        try {
            if (isPaused) { player.start(); isPaused = false; }
            else { player.pause(); isPaused = true; }
            updateSessionState(!isPaused);
            startForegroundNotif(trackNames.get(currentIndex), isPaused);
        } catch (Exception ignored) {}
    }

    private void updateSessionState(boolean playing) {
        if (session == null) return;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, 1f)
            .build());
    }

    private void stopPlayback() {
        isRunning = false; isPaused = false;
        if (player != null) { try { player.stop(); player.release(); } catch (Exception ignored) {} player = null; }
        if (session != null) { session.setActive(false); session.release(); session = null; }
        stopForeground(true);
        stopSelf();
    }

    // ==================== THÔNG BÁO — NGOẠI LỆ ĐƯỢC HIỆN TRÊN MÀN KHOÁ ====================
    private PendingIntent actionPI(String action) {
        Intent i = new Intent(this, MyPlaylistService.class);
        i.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, action.hashCode(), i, flags);
    }

    private void startForegroundNotif(String title, boolean paused) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "My Playlist", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        // [QUAN TRỌNG] PUBLIC = luôn hiện trên màn khoá — ngoại lệ DUY NHẤT cho nhạc,
        // dù Lock/Homacc đang ẩn mọi thông báo khác theo cấu hình chung của bạn.
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(paused ? "⏸️ " + title : "🎵 " + title)
            .setContentText("My Playlist")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isRunning)
            .addAction(android.R.drawable.ic_media_previous, "Trước", actionPI(ACTION_PREV))
            .addAction(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                paused ? "Phát" : "Tạm dừng", actionPI(ACTION_TOGGLE))
            .addAction(android.R.drawable.ic_media_next, "Tiếp", actionPI(ACTION_NEXT))
            .addAction(android.R.drawable.ic_delete, "Dừng", actionPI(ACTION_STOP));
        if (session != null) b.style(new Notification.MediaStyle()
            .setMediaSession(session.getSessionToken())
            .setShowActionsInCompactView(0, 1, 2));

        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);
    }
/** Hiện được cả khi màn khoá/tắt — thay Toast vô hình trong các tình huống đó.
     *  Không ongoing -> tự cho phép vuốt tắt, tự dừng Service ngay sau khi hiện. */
    private void showErrorNotif(String message) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "My Playlist", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build();
        nm.notify(NOTIF_ID, n);
        stopForeground(true);
        stopSelf();
    }
    // ==================== SO SÁNH TÊN FILE "TỰ NHIÊN" (01,02,...,10) ====================
    private static final Pattern NUM_CHUNK = Pattern.compile("\\d+|\\D+");
    private static int naturalCompare(String a, String b) {
        Matcher ma = NUM_CHUNK.matcher(a), mb = NUM_CHUNK.matcher(b);
        while (ma.find() && mb.find()) {
            String ca = ma.group(), cb = mb.group();
            int cmp = (Character.isDigit(ca.charAt(0)) && Character.isDigit(cb.charAt(0)))
                ? Long.compare(Long.parseLong(ca), Long.parseLong(cb))
                : ca.compareToIgnoreCase(cb);
            if (cmp != 0) return cmp;
        }
        return a.length() - b.length();
    }

    @Override public void onDestroy() {
        if (isRunning) stopPlayback();
        super.onDestroy();
    }
}
