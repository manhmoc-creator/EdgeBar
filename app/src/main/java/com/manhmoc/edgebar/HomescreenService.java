package com.manhmoc.edgebar;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class HomescreenService extends Service {
    public static boolean isRunning = false;
    private WindowManager wm;
    private RelativeLayout morseContainer;
    private TextView tvMorseStatus;
    
    // Hệ thống 8 thanh cạnh và 4 góc bo (Kế thừa di sản đồ họa)
    private View[] mBars = new View[8];
    private View[] mCorners = new View[4];
    private SharedPreferences prefs;
    
    private boolean isMorseLockActive = false;
    private boolean previewMorse = false;
    private String currentMorseAttempt = "";
    private String lockedPkg = "";

    private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE".equals(action)) {
                isMorseLockActive = true;
                lockedPkg = intent.getStringExtra("pkg");
                currentMorseAttempt = "";
                tvMorseStatus.setText("LOCKED: Enter Morse");
                morseContainer.setVisibility(View.VISIBLE);
                updateMorseViewsVisibility(true);
            } else if ("com.manhmoc.edgebar.TOGGLE_MORSE".equals(action)) {
                previewMorse = !previewMorse;
                if (previewMorse) {
                    morseContainer.setVisibility(View.VISIBLE);
                    tvMorseStatus.setText("Morse Preview Mode");
                    updateMorseViewsVisibility(true);
                } else if (!isMorseLockActive) {
                    morseContainer.setVisibility(View.GONE);
                    updateMorseViewsVisibility(false);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
        filter.addAction("com.manhmoc.edgebar.TOGGLE_MORSE");
        registerReceiver(syncReceiver, filter);

        initMorseOverlay();
    }

    private void initMorseOverlay() {
        // Tạo container nền đen bao phủ khi khóa
        morseContainer = new RelativeLayout(this);
        morseContainer.setBackgroundColor(Color.argb(230, 0, 0, 0)); // Đen mờ nghệ thuật
        morseContainer.setVisibility(View.GONE);

        tvMorseStatus = new TextView(this);
        tvMorseStatus.setTextColor(Color.CYAN);
        tvMorseStatus.setTextSize(24);
        tvMorseStatus.setGravity(Gravity.CENTER);
        
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(-1, -2);
        tLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        morseContainer.addView(tvMorseStatus, tLp);

        WindowManager.LayoutParams bgP = new WindowManager.LayoutParams(
                -1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        wm.addView(morseContainer, bgP);

        // KHỞI TẠO CÁC VÙNG CẢM ỨNG MORSE (Không bị xung đột với EdgeBarService)
        View.OnTouchListener morseTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    String tag = (String) v.getTag();
                    handleMorseTap(tag);
                    return true;
                }
                return false;
            }
        };

        // Tạo giả lập 8 thanh cảm ứng ẩn để bắt mã Morse
        for (int i = 0; i < 8; i++) {
            mBars[i] = new View(this);
            mBars[i].setTag("morse_bar_" + i);
            mBars[i].setOnTouchListener(morseTouchListener);
            mBars[i].setVisibility(View.GONE);
            
            // Layout params mẫu cho các thanh ẩn (Kế thừa phân bổ không gian của 19.12.3.4.2)
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    100, 100, // Kích thước sẽ được tinh chỉnh theo prefs của bạn
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            p.gravity = Gravity.TOP | Gravity.LEFT;
            wm.addView(mBars[i], p);
        }

        // Tạo giả lập 4 góc bo ẩn để bắt phím chức năng X hoặc >
        for (int i = 0; i < 4; i++) {
            mCorners[i] = new View(this);
            mCorners[i].setTag("corner_" + i);
            mCorners[i].setOnTouchListener(morseTouchListener);
            mCorners[i].setVisibility(View.GONE);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    120, 120,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            p.gravity = Gravity.TOP | Gravity.LEFT;
            wm.addView(mCorners[i], p);
        }
    }

    private void updateMorseViewsVisibility(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        for (View v : mBars) { if (v != null) v.setVisibility(vis); }
        for (View v : mCorners) { if (v != null) v.setVisibility(vis); }
    }

    private String getMappedCharacter(String comp) {
        // Trả về ký tự đã map trong Cấu hình Két Sắt Morse
        String key = "morse_map_" + comp;
        return prefs.getString(key, "*");
    }

    private void handleMorseTap(String comp) {
        String mappedChar = getMappedCharacter(comp);
        if ("*".equals(mappedChar)) return;

        if ("X".equals(mappedChar)) {
            currentMorseAttempt = "";
            tvMorseStatus.setText("Cleared");
        } else if (">".equals(mappedChar)) {
            String masterPass = prefs.getString("morse_master_pass", "....");
            if (currentMorseAttempt.equals(masterPass)) {
                // MỞ KHÓA THÀNH CÔNG
                isMorseLockActive = false;
                previewMorse = false;
                morseContainer.setVisibility(View.GONE);
                updateMorseViewsVisibility(false);
                
                // Gửi tín hiệu báo cho EdgeBarService biết để rút lui
                Intent i = new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
                i.putExtra("pkg", lockedPkg);
                sendBroadcast(i);
            } else {
                currentMorseAttempt = "";
                tvMorseStatus.setText("WRONG PASSWORD!");
            }
        } else {
            currentMorseAttempt += mappedChar;
            tvMorseStatus.setText(currentMorseAttempt);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }
    
    @Override 
    public void onDestroy() { 
        super.onDestroy(); 
        isRunning = false;
        if(morseContainer != null) wm.removeView(morseContainer); 
        for (View v : mBars) { if (v != null) wm.removeView(v); }
        for (View v : mCorners) { if (v != null) wm.removeView(v); }
        unregisterReceiver(syncReceiver); 
    }
}
