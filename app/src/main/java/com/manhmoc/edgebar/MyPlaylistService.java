      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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
import android.media.MediaMetadataRetriever;
import android.media.MediaMetadata;

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
public static final String ACTION_SEEK_BACK = "com.manhmoc.edgebar.MYPLAYLIST_SEEK_BACK";
public static final String ACTION_SEEK_FWD  = "com.manhmoc.edgebar.MYPLAYLIST_SEEK_FWD";
public static final String ACTION_OPEN_CURRENT = "com.manhmoc.edgebar.MYPLAYLIST_OPEN_CURRENT";
private static final long SEEK_STEP_MS = 10000;

private final android.os.Handler posHandler = new android.os.Handler(android.os.Looper.getMainLooper());
private Runnable posTicker;
private boolean waitingReturnFromViewer = false; // đang chờ user thoát khỏi app xem file
private boolean everLeftEdgeBar = false;          // đã thực sự rời khỏi EdgeBar chưa
private boolean pausedByFocusLoss = false;        // đánh dấu việc pause là do mở app xem file, không phải do người dùng chủ động bấm dừng

    private static final String RELATIVE_PATH_PREFIX = "Download/My Playlist";
    private static final String CHANNEL_ID = "eb_my_playlist";
    private static final int NOTIF_ID = 95;

    private MediaPlayer player;
    private MediaSession session;
    private final List<Uri> tracks = new ArrayList<>();
    private final List<String> trackNames = new ArrayList<>();
    private int currentIndex = 0;
    private android.graphics.Bitmap currentArt; // [MỚI] ảnh bìa bài đang phát

    // [MỚI] Audio Focus — xin quyền phát với hệ thống để app khác (Files by Google,
    // Spotify...) tự dừng khi EdgeBar phát, và ngược lại EdgeBar tự dừng khi app khác giành lại.
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener = fc -> {
    switch (fc) {
                case AudioManager.AUDIOFOCUS_LOSS:
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            // App khác xen ngang (YouTube, Files by Google, cuộc gọi đến/đi...) ->
            // toggle dừng NGAY, đánh dấu pause DO HỆ THỐNG, rồi bắt đầu dò xem khi
            // nào app kia tắt hẳn để tự phát tiếp (không chờ callback GAIN vì nhiều
            // app xin quyền vĩnh viễn, hệ thống sẽ không tự gọi lại cho mình).
            if (isRunning && !isPaused) {
                togglePause(true);
                pausedByFocusLoss = true;
                startFocusRecoveryPoll();
            }
            break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            if (player != null && isRunning && !isPaused) {
                try { player.setVolume(0.25f, 0.25f); } catch (Exception ignored) {}
            }
            break;
                case AudioManager.AUDIOFOCUS_GAIN:
            if (player != null) { try { player.setVolume(1f, 1f); } catch (Exception ignored) {} }
            // Trình phát khác vừa TẮT (nhả quyền phát) -> chỉ tự phát tiếp nếu
            // chính hệ thống là người tạm dừng trước đó; nếu user tự bấm Dừng
            // trong thông báo thì tuyệt đối không tự bật nhạc lại.
            if (isRunning && isPaused && pausedByFocusLoss) {
                pausedByFocusLoss = false;
                stopFocusRecoveryPoll();
                togglePause(true);
            }
            break;

    }
};
    private boolean requestAudioFocusNow() {
        if (audioManager == null) audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build();
        int result = audioManager.requestAudioFocus(focusRequest);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocusNow() {
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) { stopPlayback(); return START_NOT_STICKY; }
if (ACTION_NEXT.equals(action)) { nextTrack(); return START_NOT_STICKY; }
if (ACTION_PREV.equals(action)) { prevTrack(); return START_NOT_STICKY; }
if (ACTION_SEEK_BACK.equals(action)) { seekBy(-SEEK_STEP_MS); return START_NOT_STICKY; }
if (ACTION_SEEK_FWD.equals(action)) { seekBy(SEEK_STEP_MS); return START_NOT_STICKY; }
if (ACTION_OPEN_CURRENT.equals(action)) { openCurrentTrackFile(); return START_NOT_STICKY; }
        if (ACTION_TOGGLE.equals(action) || action == null) {
            if (isRunning) togglePause();
            else loadPlaylistAndStart();
        }
        return START_NOT_STICKY;
    }

    // ==================== NẠP PLAYLIST & PHÁT BÀI ĐẦU TIÊN ====================
    private void loadPlaylistAndStart() {
        if (!requestAudioFocusNow()) {
            showErrorNotif("⚠️ App khác đang giữ quyền phát âm thanh, thử lại sau");
            return;
        }
        startForegroundNotif("Đang tải playlist…", false);
        refreshTrackList();

        if (tracks.isEmpty()) {
            showErrorNotif("⚠️ My Playlist trống — mở EdgeBar > Sound & Media > My Playlist để thêm bài");
            return;
        }
        ensureSession();
        playIndex(0); // LUÔN bắt đầu từ bài đầu tiên theo đúng thứ tự user đã sắp xếp
    }

    // Đọc danh sách bài hát THEO ĐÚNG THỨ TỰ user đã kéo-thả trong MainActivity
    // (key "myplaylist_ids"), bỏ qua bài nào bị xoá file gốc trong Files by Google.
    private void refreshTrackList() {
        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        String csv = prefs.getString("myplaylist_ids", "");
        String currentUri = (currentIndex >= 0 && currentIndex < tracks.size()) ? tracks.get(currentIndex).toString() : null;
        tracks.clear(); trackNames.clear();
        if (csv.isEmpty()) return;
        for (String id : csv.split(",")) {
            String t = id.trim(); if (t.isEmpty()) continue;
            String uriStr = prefs.getString("myplaylist_" + t + "_uri", "");
            if (uriStr.isEmpty()) continue;
            Uri u = Uri.parse(uriStr);
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {} // đã cấp trước đó, hoặc file không còn -> vẫn thử phát, lỗi sẽ tự next
            tracks.add(u);
            trackNames.add(prefs.getString("myplaylist_" + t + "_name", "Song"));
        }
        if (currentUri != null) {
            int newPos = -1;
            for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).toString().equals(currentUri)) { newPos = i; break; }
            if (newPos >= 0) currentIndex = newPos;
        }
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
    @Override public void onRewind() { seekBy(-SEEK_STEP_MS); }
    @Override public void onFastForward() { seekBy(SEEK_STEP_MS); }
    @Override public void onSeekTo(long pos) {
        if (player == null) return;
        try { player.seekTo((int) pos); updateSessionState(!isPaused); } catch (Exception ignored) {}
    }
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
            player.setOnCompletionListener(mp -> nextTrack());
            player.setOnErrorListener((mp, what, extra) -> { nextTrack(); return true; });
        } else {
            try { player.reset(); } catch (Exception ignored) {}
        }
        final int idxForArt = idx;
        player.setOnPreparedListener(mp -> {
            mp.start();
            isRunning = true; isPaused = false;
            currentArt = extractAlbumArt(tracks.get(idxForArt));
            if (session != null) {
                MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, trackNames.get(idxForArt))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "My Playlist");
                if (currentArt != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArt);
        meta.putLong(MediaMetadata.METADATA_KEY_DURATION, mp.getDuration());
        session.setMetadata(meta.build());
    }
    updateSessionState(true);
    startPosTicker();
    startForegroundNotif(trackNames.get(currentIndex), false);
});
        try {
            player.setDataSource(this, tracks.get(currentIndex));
            player.prepareAsync(); // không block main thread
        } catch (Exception e) {
            if (tracks.size() > 1) playIndex(currentIndex + 1); else stopPlayback();
        }
    }
    private void nextTrack() { refreshTrackList(); playIndex(currentIndex + 1); }
    private void prevTrack() { refreshTrackList(); playIndex(currentIndex - 1); }

    // 1 chạm vào thông báo -> mở đúng file bài hát ĐANG PHÁT bằng Files by Google
    // (fallback chooser), tái dùng cùng cơ chế đã có ở VoiceRecorderService.
    private void openCurrentTrackFile() {
        if (tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
        Uri uri = tracks.get(currentIndex);
        // [MỚI] Chủ động pause trước khi mở app xem file, đồng thời tái dùng ĐÚNG cơ chế
        // Audio Focus có sẵn (pausedByFocusLoss) — không polling, không cần biết app nào.
        // Hễ có sự kiện AUDIOFOCUS_GAIN sau đó (app xem file nhả quyền phát ra, thường là
        // lúc bạn thoát nó) thì focusListener() sẽ tự resume đúng chỗ đang dừng.
        if (isRunning && !isPaused) {
    pausedByFocusLoss = true;
    togglePause(true);
}
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setPackage("com.google.android.apps.nbu.files");
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i2 = new Intent(Intent.ACTION_VIEW);
                i2.setDataAndType(uri, "audio/*");
                i2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(i2, "Mở bằng").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                pausedByFocusLoss = false; // mở thất bại -> không cần chờ resume nữa
            }
        }
    }

    // [MỚI] Vòng lặp nhẹ (500ms/lần, TỰ DỪNG khi xong việc) dùng UsageStatsManager để
    // phát hiện đúng lúc user đã rời app xem file (Files by Google / app khác qua
    // chooser) và quay lại EdgeBar — CHỈ lúc đó mới tự phát tiếp đúng vị trí đang dừng.
    // Không phụ thuộc Accessibility bật/tắt (khác SYNC_STATE vốn chỉ có khi Accessibility
    // chạy) nên hoạt động đúng ở mọi trạng thái Lock/Homacc/Homeb. Cần quyền "Usage access"
    // đã có sẵn trong app (dùng chung với Blacklist Auto-Homeb) — nếu chưa cấp, catch()
    // sẽ bỏ qua êm, không crash, chỉ đơn giản là tính năng tự-tiếp-tục không hoạt động.
    private final android.os.Handler focusPollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
private Runnable focusPollRunnable;
private static final long FOCUS_POLL_INTERVAL_MS = 2000; // dò mỗi 2 giây, tự dừng ngay khi phát hiện xong

private final android.os.Handler viewerPollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
private final Runnable viewerPollRunnable = new Runnable() {

        @Override public void run() {
            if (!waitingReturnFromViewer) return; // đã xử lý xong hoặc đã huỷ -> dừng hẳn
            try {
                android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                android.app.usage.UsageEvents events = usm.queryEvents(now - 3000, now);
                android.app.usage.UsageEvents.Event ev = new android.app.usage.UsageEvents.Event();
                String fg = null;
                while (events.hasNextEvent()) {
                    events.getNextEvent(ev);
                    if (ev.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        fg = ev.getPackageName();
                    }
                }
                if (fg != null) {
                    if (!fg.equals(getPackageName())) {
                        everLeftEdgeBar = true;
                    } else if (everLeftEdgeBar) {
                        // Đã rời đi xem file và giờ quay lại đúng EdgeBar -> tiếp tục phát
                        waitingReturnFromViewer = false;
                        everLeftEdgeBar = false;
                        if (isRunning && isPaused) togglePause();
                        return; // không postDelayed nữa, tự kết thúc vòng lặp
                    }
                }
            } catch (Exception ignored) {}
            if (waitingReturnFromViewer) viewerPollHandler.postDelayed(this, 500);
        }
    };
    /** Dò định kỳ bằng isMusicActive() — KHÔNG xin giành lại quyền phát (không làm
     *  gián đoạn app đang phát), chỉ kiểm tra xem có ai còn phát nhạc/video không.
     *  Hết ai phát -> tự xin lại quyền & phát tiếp đúng chỗ đang dừng. Tự huỷ ngay
     *  khi xong việc hoặc khi user tự bấm Dừng — Zero-CPU lúc không cần dò nữa. */
    private void startFocusRecoveryPoll() {
        stopFocusRecoveryPoll();
        focusPollRunnable = () -> {
            if (!pausedByFocusLoss) return; // đã resume bằng cách khác hoặc user tự bấm Dừng
            if (audioManager == null) audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            boolean someoneElsePlaying = audioManager != null && audioManager.isMusicActive();
            if (!someoneElsePlaying) {
                pausedByFocusLoss = false;
                if (requestAudioFocusNow() && isRunning && isPaused) togglePause(true);
                return;
            }
            focusPollHandler.postDelayed(focusPollRunnable, FOCUS_POLL_INTERVAL_MS);
        };
        focusPollHandler.postDelayed(focusPollRunnable, FOCUS_POLL_INTERVAL_MS);
    }
    private void stopFocusRecoveryPoll() {
        if (focusPollRunnable != null) focusPollHandler.removeCallbacks(focusPollRunnable);
        focusPollRunnable = null;
    }
    private void togglePause() { togglePause(false); }
private void togglePause(boolean bySystem) {
    if (player == null || !isRunning) return;
    try {
        if (isPaused) { player.start(); isPaused = false; startPosTicker(); }
        else { player.pause(); isPaused = true; stopPosTicker(); }
        // Người dùng tự bấm nút (bySystem=false) -> huỷ cờ "pause do hệ thống",
        // tránh việc app khác nhả Audio Focus sau đó lại tự bật nhạc ngoài ý muốn.
                if (!bySystem) { pausedByFocusLoss = false; stopFocusRecoveryPoll(); }
        updateSessionState(!isPaused);
        startForegroundNotif(trackNames.get(currentIndex), isPaused);
    } catch (Exception ignored) {}
}

    private void updateSessionState(boolean playing) {
    if (session == null) return;
    long pos = 0;
    try { if (player != null) pos = player.getCurrentPosition(); } catch (Exception ignored) {}
    session.setPlaybackState(new PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP
            | PlaybackState.ACTION_REWIND | PlaybackState.ACTION_FAST_FORWARD
            | PlaybackState.ACTION_SEEK_TO)
        .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, playing ? 1f : 0f)
        .build());
}
        private void stopPlayback() {
    isRunning = false; isPaused = false;
    stopPosTicker();
    stopFocusRecoveryPoll();
    pausedByFocusLoss = false;
    waitingReturnFromViewer = false;
    currentArt = null; // [MỚI] giải phóng RAM ảnh bìa

        abandonAudioFocusNow(); // [MỚI]

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
private PendingIntent contentTapPI() {
        Intent i = new Intent(this, MyPlaylistService.class);
        i.setAction(ACTION_OPEN_CURRENT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, ACTION_OPEN_CURRENT.hashCode(), i, flags);
    }
        // Lấy đúng icon "nốt nhạc 2 chân" trong bộ 100 icon custom (music_note_2_24px);
    // nếu vì lý do gì không tìm thấy resource thì fallback về icon play mặc định
    // của hệ thống, đảm bảo notification luôn hiện được, không bao giờ crash.
    private int resolveMusicNoteIconRes() {
        int id = getResources().getIdentifier("music_note_2_24px", "drawable", getPackageName());
        return id != 0 ? id : android.R.drawable.ic_media_play;
    }

    private void startForegroundNotif(String title, boolean paused) {
    NotificationManager nm = getSystemService(NotificationManager.class);
    NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "My Playlist", NotificationManager.IMPORTANCE_LOW);
    ch.setSound(null, null);
    ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    nm.createNotificationChannel(ch);

    // Bộ nút giống media notification chuẩn của hệ thống: Lùi 10s - Trước - Play/Pause - Tiếp - Tới 10s,
    // nút Dừng đổi icon nguồn (power) để không còn giống dấu X gây hiểu nhầm.
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(paused ? "⏸️ " + title : "🎵 " + title)
        .setContentText("My Playlist")
        .setSmallIcon(resolveMusicNoteIconRes())
        .setLargeIcon(currentArt)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(isRunning)
        .setContentIntent(contentTapPI())
        .setDeleteIntent(actionPI(ACTION_STOP))
        .addAction(android.R.drawable.ic_media_rew, "Lùi 10s", actionPI(ACTION_SEEK_BACK))
        .addAction(android.R.drawable.ic_media_previous, "Trước", actionPI(ACTION_PREV))
        .addAction(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
            paused ? "Phát" : "Tạm Dừng", actionPI(ACTION_TOGGLE))
        .addAction(android.R.drawable.ic_media_next, "Tiếp", actionPI(ACTION_NEXT))
        .addAction(android.R.drawable.ic_media_ff, "Tới 10s", actionPI(ACTION_SEEK_FWD))
        .addAction(android.R.drawable.ic_lock_power_off, "Dừng", actionPI(ACTION_STOP));
    if (session != null) b.setStyle(new Notification.MediaStyle()
        .setMediaSession(session.getSessionToken())
        .setShowActionsInCompactView(1, 2, 3)); // Trước - Play/Pause - Tiếp trong khung thu gọn
    Notification n = b.build();
    if (Build.VERSION.SDK_INT >= 29)
        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(NOTIF_ID, n);
}
/** Hiện được cả khi màn khoá/tắt — thay Toast vô hình trong các tình huống đó.
     *  Không ongoing -> tự cho phép vuốt tắt, tự dừng Service ngay sau khi hiện. */
    private void showErrorNotif(String message) {
        abandonAudioFocusNow(); // [MỚI] không phát được thì nhả quyền ngay, tránh giữ vô ích
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
private android.graphics.Bitmap extractAlbumArt(Uri uri) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, uri);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();
            if (art != null) return android.graphics.BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Exception ignored) {}
        return null;
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
private void seekBy(long deltaMs) {
    if (player == null || !isRunning) return;
    try {
        int dur = player.getDuration();
        int pos = player.getCurrentPosition();
        int target = (int) Math.max(0, Math.min(dur, pos + deltaMs));
        player.seekTo(target);
        updateSessionState(!isPaused);
    } catch (Exception ignored) {}
}

private void startPosTicker() {
    stopPosTicker();
    posTicker = () -> {
        if (!isRunning || isPaused) return;
        updateSessionState(true);
        posHandler.postDelayed(posTicker, 1000);
    };
    posHandler.postDelayed(posTicker, 1000);
}

private void stopPosTicker() {
    if (posTicker != null) posHandler.removeCallbacks(posTicker);
    posTicker = null;
}
        @Override public void onDestroy() {
        viewerPollHandler.removeCallbacksAndMessages(null); // [MỚI] dừng vòng lặp chờ, tránh leak Handler
        focusPollHandler.removeCallbacksAndMessages(null);  // [MỚI] dừng vòng dò audio focus, tránh leak Handler
        if (isRunning) stopPlayback();
        super.onDestroy();
    }
}
