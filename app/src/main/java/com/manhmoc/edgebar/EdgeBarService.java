package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager windowManager;
    private View leftBar, rightBar;
    private GestureDetector gestureDetector;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;
    private KeyguardManager keyguardManager;

    // Bộ phát hiện Tắt/Mở màn hình để thu hồi App
    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // Đã mở khoá -> Ẩn cmn luôn cho Sidebar lộng hành
                if (leftBar != null) leftBar.setVisibility(View.GONE);
                if (rightBar != null) rightBar.setVisibility(View.GONE);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                // Tắt màn hình -> Triệu hồi lại
                if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
                    if (leftBar != null) leftBar.setVisibility(View.VISIBLE);
                    if (rightBar != null) rightBar.setVisibility(View.VISIBLE);
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cameraId = cameraManager.getCameraIdList()[0]; } catch (Exception e) {}

        setupGestureDetector();
        createFloatingBars();

        // Đăng ký bộ phát hiện
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); // 1 TAP: Tắt màn hình
                return true;
            }
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); // 2 TAP: Hộp thoại nguồn
                return true;
            }
            @Override
            public void onLongPress(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); // GIỮ LÂU: Chụp màn hình
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                
                // Thuật toán nhận diện nhạy hơn cho Swipe
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > 40) {
                        if (diffX > 0) {
                            // Vuốt phải: Mở Camera
                            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            // Vuốt trái: Đèn pin
                            toggleFlash();
                        }
                    }
                } else {
                    if (Math.abs(diffY) > 40) {
                        if (diffY < 0) {
                            performGlobalAction(GLOBAL_ACTION_HOME); // Vuốt lên
                        } else {
                            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); // Vuốt xuống
                        }
                    }
                }
                return true;
            }
        });
    }

    private void toggleFlash() {
        try {
            isFlashOn = !isFlashOn;
            cameraManager.setTorchMode(cameraId, isFlashOn);
        } catch (Exception e) {}
    }

    private void createFloatingBars() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int gap = 250; // Khoảng trống ở giữa (Pixel 2XL ~1.5cm)
        int barWidth = (screenWidth - gap) / 2;

        leftBar = new View(this);
        rightBar = new View(this);
        leftBar.setBackgroundColor(Color.TRANSPARENT);
        rightBar.setBackgroundColor(Color.TRANSPARENT);

        // Khởi tạo Bar Trái
        WindowManager.LayoutParams paramsLeft = new WindowManager.LayoutParams(
                barWidth, 80, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        paramsLeft.gravity = Gravity.BOTTOM | Gravity.LEFT;

        // Khởi tạo Bar Phải
        WindowManager.LayoutParams paramsRight = new WindowManager.LayoutParams(
                barWidth, 80, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        paramsRight.gravity = Gravity.BOTTOM | Gravity.RIGHT;

        View.OnTouchListener touchListener = (v, event) -> {
            if (keyguardManager != null && !keyguardManager.isKeyguardLocked()) return false;
            return gestureDetector.onTouchEvent(event);
        };

        leftBar.setOnTouchListener(touchListener);
        rightBar.setOnTouchListener(touchListener);

        windowManager.addView(leftBar, paramsLeft);
        windowManager.addView(rightBar, paramsRight);

        // Khởi tạo mặc định: Ẩn nếu đang mở khoá
        if (keyguardManager != null && !keyguardManager.isKeyguardLocked()) {
            leftBar.setVisibility(View.GONE);
            rightBar.setVisibility(View.GONE);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(screenReceiver); } catch (Exception e) {}
        if (leftBar != null) windowManager.removeView(leftBar);
        if (rightBar != null) windowManager.removeView(rightBar);
    }
}
