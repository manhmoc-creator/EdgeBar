package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class EdgeBarPlayerActivity extends Activity {
    private MediaPlayer player;
    private SharedPreferences prefs;
    private final List<String> playlistIds = new ArrayList<>();
    private int playlistIndex = -1;
    private boolean isPlaylistMode;
    private TextView tvTitle, tvTime;
    private SeekBar seekBar;
    private Button btnPlayPause;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable tick;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(50, 120, 50, 50);
        root.setGravity(Gravity.CENTER);

        tvTitle = new TextView(this);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setGravity(Gravity.CENTER);
        root.addView(tvTitle);

        seekBar = new SeekBar(this);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
        sLp.setMargins(0, 40, 0, 20);
        seekBar.setLayoutParams(sLp);
        root.addView(seekBar);

        tvTime = new TextView(this);
        tvTime.setTextColor(Color.parseColor("#9AA0A6"));
        tvTime.setGravity(Gravity.CENTER);
        root.addView(tvTime);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-2, -2);
        rowLp.topMargin = 40;
        btnRow.setLayoutParams(rowLp);

        Button btnPrev = new Button(this);
        btnPrev.setText("⏮");
        btnPrev.setOnClickListener(v -> playlistStep(-1));
        btnRow.addView(btnPrev);

        btnPlayPause = new Button(this);
        btnPlayPause.setText("⏸");
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRow.addView(btnPlayPause);

        Button btnNext = new Button(this);
        btnNext.setText("⏭");
        btnNext.setOnClickListener(v -> playlistStep(1));
        btnRow.addView(btnNext);

        root.addView(btnRow);
        setContentView(root);

        String plIds = getIntent().getStringExtra("playlist_ids");
        isPlaylistMode = plIds != null && !plIds.isEmpty();
        if (isPlaylistMode) {
            for (String id : plIds.split(",")) if (!id.trim().isEmpty()) playlistIds.add(id.trim());
            playlistIndex = getIntent().getIntExtra("playlist_index", 0);
            playCurrentPlaylistTrack();
        } else {
            Uri data = getIntent().getData();
            String title = getIntent().getStringExtra("title");
            tvTitle.setText(title != null ? title : "Đang phát");
            if (data != null) startPlayback(data);
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && player != null) { try { player.seekTo(p); } catch (Exception ignored) {} }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void playlistStep(int delta) {
        if (playlistIds.isEmpty()) return;
        playlistIndex = (playlistIndex + delta + playlistIds.size()) % playlistIds.size();
        playCurrentPlaylistTrack();
    }

    private void playCurrentPlaylistTrack() {
        if (playlistIndex < 0 || playlistIndex >= playlistIds.size()) return;
        String id = playlistIds.get(playlistIndex);
        String uriStr = prefs.getString("myplaylist_" + id + "_uri", "");
        String name = prefs.getString("myplaylist_" + id + "_name", "Song");
        tvTitle.setText(name);
        if (!uriStr.isEmpty()) startPlayback(Uri.parse(uriStr));
    }

    private void startPlayback(Uri uri) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(this, uri);
            player.setOnPreparedListener(mp -> {
                mp.start();
                seekBar.setMax(mp.getDuration());
                startTicker();
                btnPlayPause.setText("⏸");
            });
            player.setOnCompletionListener(mp -> { if (isPlaylistMode) playlistStep(1); });
            player.prepareAsync();
        } catch (Exception e) {
            tvTitle.setText("Không thể phát file này");
        }
    }

    private void togglePlayPause() {
        if (player == null) return;
        try {
            if (player.isPlaying()) { player.pause(); btnPlayPause.setText("▶"); }
            else { player.start(); btnPlayPause.setText("⏸"); }
        } catch (Exception ignored) {}
    }

    private void startTicker() {
        stopTicker();
        tick = () -> {
            if (player != null) {
                try {
                    int pos = player.getCurrentPosition();
                    seekBar.setProgress(pos);
                    tvTime.setText(formatTime(pos) + " / " + formatTime(player.getDuration()));
                } catch (Exception ignored) {}
            }
            h.postDelayed(tick, 500);
        };
        h.post(tick);
    }

    private void stopTicker() { if (tick != null) h.removeCallbacks(tick); }

    private String formatTime(int ms) {
        int sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private void releasePlayer() {
        stopTicker();
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
