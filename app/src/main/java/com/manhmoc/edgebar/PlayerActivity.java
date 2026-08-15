      package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Màn phát nhạc dùng chung: MODE_PLAYLIST_LIVE bám vào MyPlaylistService đang chạy
 *  (điều khiển qua Intent broadcast tới chính service, KHÔNG tạo MediaPlayer mới —
 *  0 RAM/CPU thêm ngoài UI). MODE_SINGLE_URI tự phát 1 file ghi âm bằng MediaPlayer
 *  cục bộ trong Activity, giải phóng ngay khi đóng màn — không giữ Service nền. */
public class PlayerActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_START_ID = "start_id";
    public static final int MODE_PLAYLIST_LIVE = 0;
    public static final int MODE_SINGLE_URI = 1;
    public static final int MODE_PLAYLIST = 2;

    private int mode;
    private android.media.MediaPlayer localPlayer;
    private boolean localPaused = false;
    private int localRepeat = 0;
    private TextView tvTitle, tvTime;
    private SeekBar seekBar;
    private ImageButton btnShuffle, btnPrev, btnPlay, btnNext, btnRepeat;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_SINGLE_URI);
        buildUi();
        if (mode == MODE_SINGLE_URI) startLocalPlayback(Uri.parse(getIntent().getStringExtra(EXTRA_URI)),
                getIntent().getStringExtra(EXTRA_TITLE));
        else startLiveSync();
    }

    private void buildUi() {
        RelativeLayout root = new RelativeLayout(this);
        root.setBackgroundColor(Color.parseColor("#121212"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(60, 140, 60, 60);
        RelativeLayout.LayoutParams clp = new RelativeLayout.LayoutParams(-1, -1);
        col.setLayoutParams(clp);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(android.R.drawable.ic_media_rew);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setColorFilter(Color.WHITE);
        btnBack.setOnClickListener(v -> finish());
        ImageButton btnMore = new ImageButton(this);
        btnMore.setImageResource(android.R.drawable.ic_menu_more);
        btnMore.setBackgroundColor(Color.TRANSPARENT);
        btnMore.setColorFilter(Color.WHITE);
        btnMore.setOnClickListener(this::showOverflowMenu);
        LinearLayout.LayoutParams fillLp = new LinearLayout.LayoutParams(0, -2, 1f);
        View spacer = new View(this); spacer.setLayoutParams(fillLp);
        topBar.addView(btnBack); topBar.addView(spacer); topBar.addView(btnMore);
        col.addView(topBar);

        tvTitle = new TextView(this);
        tvTitle.setTextColor(Color.WHITE); tvTitle.setTextSize(18);
        tvTitle.setPadding(0, 60, 0, 40);
        col.addView(tvTitle);

        seekBar = new SeekBar(this);
        col.addView(seekBar);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvCur = new TextView(this); tvCur.setTextColor(Color.GRAY); tvCur.setText("00:00");
        tvTime = new TextView(this); tvTime.setTextColor(Color.GRAY); tvTime.setText("00:00");
        LinearLayout.LayoutParams timeFillLp = new LinearLayout.LayoutParams(0, -2, 1f);
        tvCur.setLayoutParams(timeFillLp);
        timeRow.addView(tvCur); timeRow.addView(tvTime);
        col.addView(timeRow);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER);
        ctrlRow.setPadding(0, 60, 0, 0);
        btnShuffle = makeCtrlBtn(android.R.drawable.ic_menu_sort_alphabetically, v -> toggleShuffle());
        btnPrev = makeCtrlBtn(android.R.drawable.ic_media_previous, v -> prev());
        btnPlay = makeCtrlBtn(android.R.drawable.ic_media_pause, v -> togglePlay());
        btnNext = makeCtrlBtn(android.R.drawable.ic_media_next, v -> next());
        btnRepeat = makeCtrlBtn(android.R.drawable.ic_menu_rotate, v -> toggleRepeat());
        ctrlRow.addView(btnShuffle); ctrlRow.addView(btnPrev); ctrlRow.addView(btnPlay);
        ctrlRow.addView(btnNext); ctrlRow.addView(btnRepeat);
        col.addView(ctrlRow);

        if (mode == MODE_SINGLE_URI) { btnShuffle.setVisibility(View.GONE); btnPrev.setVisibility(View.GONE); btnNext.setVisibility(View.GONE); }

        root.addView(col);
        setContentView(root);
    }

    private ImageButton makeCtrlBtn(int res, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res); b.setColorFilter(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, 120);
        lp.setMargins(16, 0, 16, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    // ---------- MODE_SINGLE_URI: phát cục bộ, tự giải phóng khi đóng màn ----------
    private void startLocalPlayback(Uri uri, String title) {
        tvTitle.setText(title != null ? title : "Ghi âm");
        try {
            localPlayer = new android.media.MediaPlayer();
            localPlayer.setDataSource(this, uri);
            localPlayer.setOnPreparedListener(mp -> {
                mp.start();
                seekBar.setMax(mp.getDuration());
                tvTime.setText(fmt(mp.getDuration()));
                startTicker();
            });
            localPlayer.setOnCompletionListener(mp -> {
                if (localRepeat == 2) { mp.seekTo(0); mp.start(); } else { btnPlay.setImageResource(android.R.drawable.ic_media_play); }
            });
            localPlayer.prepareAsync();
        } catch (Exception e) { Toast.makeText(this, "Không phát được file này", Toast.LENGTH_SHORT).show(); finish(); }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser && localPlayer != null) localPlayer.seekTo(p); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }
    private void startTicker() {
        tick = () -> {
            if (localPlayer != null) {
                try { seekBar.setProgress(localPlayer.getCurrentPosition()); } catch (Exception ignored) {}
            }
            h.postDelayed(tick, 500);
        };
        h.post(tick);
    }
    private void togglePlay() {
        if (mode == MODE_SINGLE_URI) {
            if (localPlayer == null) return;
            if (localPaused) { localPlayer.start(); btnPlay.setImageResource(android.R.drawable.ic_media_pause); }
            else { localPlayer.pause(); btnPlay.setImageResource(android.R.drawable.ic_media_play); }
            localPaused = !localPaused;
        } else {
            sendPlaylistAction(MyPlaylistService.ACTION_TOGGLE);
        }
    }
    private void toggleRepeat() {
        if (mode == MODE_SINGLE_URI) { localRepeat = (localRepeat + 1) % 3; Toast.makeText(this, "Lặp: " + (localRepeat==0?"Tắt":localRepeat==1?"Tất cả":"1 bài"), Toast.LENGTH_SHORT).show(); }
        else sendPlaylistAction(MyPlaylistService.ACTION_TOGGLE_REPEAT);
    }
    private void toggleShuffle() { if (mode != MODE_SINGLE_URI) sendPlaylistAction(MyPlaylistService.ACTION_TOGGLE_SHUFFLE); }
    private void prev() { if (mode != MODE_SINGLE_URI) sendPlaylistAction(MyPlaylistService.ACTION_PREV); }
    private void next() { if (mode != MODE_SINGLE_URI) sendPlaylistAction(MyPlaylistService.ACTION_NEXT); }

    // ---------- MODE_PLAYLIST(_LIVE): điều khiển service đang chạy qua broadcast ----------
    private BroadcastReceiver tickReceiver;
    private void startLiveSync() {
        if (!MyPlaylistService.isRunning) {
            Intent i = new Intent(this, MyPlaylistService.class);
            i.setAction(MyPlaylistService.ACTION_TOGGLE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        }
        tvTitle.setText(T());
        seekBar.setEnabled(false); // seek qua service chưa expose vị trí -> chỉ hiển thị trạng thái play/pause
        btnPlay.setImageResource(MyPlaylistService.isPaused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause);
    }
    private String T() { return "My Playlist"; }
    private void sendPlaylistAction(String action) {
        Intent i = new Intent(this, MyPlaylistService.class);
        i.setAction(action);
        startService(i);
        h.postDelayed(() -> btnPlay.setImageResource(MyPlaylistService.isPaused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause), 200);
    }

    // ---------- Menu 3 chấm: tương đương "Chia sẻ / Xoá / Thông tin / Tốc độ phát" ----------
    private void showOverflowMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add("Chia sẻ");
        pm.getMenu().add("Xoá (Chuyển vào Kho Cũ)");
        pm.getMenu().add("Thông tin tệp");
        pm.getMenu().add("Tốc độ phát");
        pm.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.startsWith("Chia sẻ")) doShare();
            else if (t.startsWith("Xoá")) doDelete();
            else if (t.startsWith("Thông tin")) doInfo();
            else doSpeedDialog();
            return true;
        });
        pm.show();
    }
    private Uri currentUriForFileOps() {
        if (mode == MODE_SINGLE_URI) return Uri.parse(getIntent().getStringExtra(EXTRA_URI));
        return null; // MODE_PLAYLIST_LIVE: file thao tác theo bài đang phát, cần service expose thêm nếu muốn hỗ trợ đầy đủ
    }
    private void doShare() {
        Uri u = currentUriForFileOps();
        if (u == null) { Toast.makeText(this, "Chưa hỗ trợ cho danh sách phát", Toast.LENGTH_SHORT).show(); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("audio/*");
        share.putExtra(Intent.EXTRA_STREAM, u);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Chia sẻ"));
    }
    private void doDelete() {
        Uri u = currentUriForFileOps();
        if (u == null) { Toast.makeText(this, "Chưa hỗ trợ cho danh sách phát", Toast.LENGTH_SHORT).show(); return; }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Xoá file ghi âm này?")
            .setPositiveButton("XOÁ", (d, w) -> {
                try { getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
                finish();
            }).setNegativeButton("HỦY", null).show();
    }
    private void doInfo() {
        Uri u = currentUriForFileOps();
        if (u == null) return;
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, u);
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mmr.release();
            Toast.makeText(this, "Thời lượng: " + (dur != null ? (Long.parseLong(dur)/1000) + "s" : "?"), Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }
    private void doSpeedDialog() {
        String[] speeds = {"0.5x", "1.0x", "1.5x", "2.0x"};
        float[] vals = {0.5f, 1.0f, 1.5f, 2.0f};
        new android.app.AlertDialog.Builder(this).setTitle("Tốc độ phát")
            .setItems(speeds, (d, which) -> {
                if (localPlayer != null && Build.VERSION.SDK_INT >= 23) {
                    try { localPlayer.setPlaybackParams(localPlayer.getPlaybackParams().setSpeed(vals[which])); } catch (Exception ignored) {}
                }
            }).show();
    }
    private String fmt(int ms) { int s = ms/1000; return String.format("%02d:%02d", s/60, s%60); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (tick != null) h.removeCallbacks(tick);
        if (localPlayer != null) { try { localPlayer.release(); } catch (Exception ignored) {} localPlayer = null; }
    }
}
