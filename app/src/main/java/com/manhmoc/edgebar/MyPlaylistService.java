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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.media.MediaMetadataRetriever;
import android.media.MediaMetadata;

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
    public static final String ACTION_SHUFFLE = "com.manhmoc.edgebar.MYPLAYLIST_SHUFFLE";
    public static final String ACTION_REPEAT = "com.manhmoc.edgebar.MYPLAYLIST_REPEAT";

    private static final long SEEK_STEP_MS = 10000;
    private final android.os.Handler posHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable posTicker;
    private boolean pausedByFocusLoss = false;

    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = Không lặp, 1 = Lặp 1 bài, 2 = Lặp tất cả

    private static final String CHANNEL_ID = "eb_my_playlist";
    private static final int NOTIF_ID = 95;

    private MediaPlayer player;
    private MediaSession session;
    private final List<Uri> tracks = new ArrayList<>();
    private final List<String> trackNames = new ArrayList<>();
    private int currentIndex = 0;
    private android.graphics.Bitmap currentArt;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener = fc -> {
        switch (fc) {
            case AudioManager.AUDIOFOCUS_LOSS:
                if (isRunning) stopPlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isRunning && !isPaused) { pausedByFocusLoss = true; togglePause(); }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null && isRunning && !isPaused) {
                    try { player.setVolume(0.25f, 0.25f); } catch (Exception ignored) {}
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (player != null) { try { player.setVolume(1f, 1f); } catch (Exception ignored) {} }
                if (pausedByFocusLoss && isRunning && isPaused) { pausedByFocusLoss = false; togglePause(); }
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
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
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
        if (ACTION_SHUFFLE.equals(action)) { toggleShuffle(); return START_NOT_STICKY; }
        if (ACTION_REPEAT.equals(action)) { toggleRepeat(); return START_NOT_STICKY; }

        if (ACTION_TOGGLE.equals(action) || action == null) {
            if (isRunning) togglePause();
            else loadPlaylistAndStart();
        }
        return START_NOT_STICKY;
    }

    private void toggleShuffle() {
        isShuffle = !isShuffle;
        if (isRunning) startForegroundNotif(trackNames.get(currentIndex), isPaused);
    }

    private void toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        if (isRunning) startForegroundNotif(trackNames.get(currentIndex), isPaused);
    }

    private void loadPlaylistAndStart() {
        if (!requestAudioFocusNow()) {
            showErrorNotif("⚠️ App khác đang giữ quyền phát âm thanh");
            return;
        }
        startForegroundNotif("Đang tải playlist…", false);
        refreshTrackList();

        if (tracks.isEmpty()) {
            showErrorNotif("⚠️ My Playlist trống");
            return;
        }
        ensureSession();
        playIndex(0);
    }

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
            catch (Exception ignored) {} 
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
            @Override public void onSkipToNext() { nextTrack(); }
            @Override public void onSkipToPrevious() { prevTrack(); }
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
        if (idx >= tracks.size()) idx = 0;
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
            player.prepareAsync();
        } catch (Exception e) {
            if (tracks.size() > 1) playIndex(currentIndex + 1); else stopPlayback();
        }
    }

    private void nextTrack() {
        refreshTrackList();
        if (tracks.isEmpty()) { stopPlayback(); return; }
        if (repeatMode == 1) {
            playIndex(currentIndex);
        } else if (isShuffle) {
            int next = new Random().nextInt(tracks.size());
            playIndex(next);
        } else {
            playIndex(currentIndex + 1);
        }
    }
    
    private void prevTrack() { 
        refreshTrackList(); 
        if (isShuffle) playIndex(new Random().nextInt(tracks.size()));
        else playIndex(currentIndex - 1); 
    }

    private void openCurrentTrackFile() {
        if (tracks.isEmpty() || currentIndex < 0 || currentIndex >= tracks.size()) return;
        Uri uri = tracks.get(currentIndex);
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
            } catch (Exception ignored) {}
        }
    }

    private void togglePause() {
        if (player == null || !isRunning) return;
        try {
            if (isPaused) { player.start(); isPaused = false; startPosTicker(); }
            else { player.pause(); isPaused = true; stopPosTicker(); }
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
        pausedByFocusLoss = false;
        currentArt = null; 
        abandonAudioFocusNow(); 
        if (player != null) { try { player.stop(); player.release(); } catch (Exception ignored) {} player = null; }
        if (session != null) { session.setActive(false); session.release(); session = null; }
        stopForeground(true);
        stopSelf();
    }

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

    private void startForegroundNotif(String title, boolean paused) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "My Playlist", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        int repeatIcon = android.R.drawable.ic_menu_revert;
        if (repeatMode == 1) repeatIcon = android.R.drawable.stat_notify_sync_noanim;
        else if (repeatMode == 2) repeatIcon = android.R.drawable.stat_notify_sync;
        int shuffleIcon = isShuffle ? android.R.drawable.ic_menu_directions : android.R.drawable.ic_menu_sort_alphabetically;

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(paused ? "⏸️ " + title : "🎵 " + title)
            .setContentText("My Playlist")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(currentArt)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isRunning)
            .setContentIntent(contentTapPI())
            .setDeleteIntent(actionPI(ACTION_STOP))
            .addAction(shuffleIcon, "Shuffle", actionPI(ACTION_SHUFFLE)) // 0
            .addAction(android.R.drawable.ic_media_rew, "Lùi 10s", actionPI(ACTION_SEEK_BACK)) // 1
            .addAction(android.R.drawable.ic_media_previous, "Trước", actionPI(ACTION_PREV)) // 2
            .addAction(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                paused ? "Phát" : "Tạm Dừng", actionPI(ACTION_TOGGLE)) // 3
            .addAction(android.R.drawable.ic_media_next, "Tiếp", actionPI(ACTION_NEXT)) // 4
            .addAction(android.R.drawable.ic_media_ff, "Tới 10s", actionPI(ACTION_SEEK_FWD)) // 5
            .addAction(repeatIcon, "Repeat", actionPI(ACTION_REPEAT)); // 6

        if (session != null) b.setStyle(new Notification.MediaStyle()
            .setMediaSession(session.getSessionToken())
            .setShowActionsInCompactView(2, 3, 4)); // Giữ Lùi, Phát, Tiến ở bản thu nhỏ

        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);
    }

    private void showErrorNotif(String message) {
        abandonAudioFocusNow();
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
        if (isRunning) stopPlayback();
        super.onDestroy();
    }
}
