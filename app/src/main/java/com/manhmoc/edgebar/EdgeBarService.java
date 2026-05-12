package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager windowManager;
    private View floatingBar;
    private GestureDetector gestureDetector;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cameraId = cameraManager.getCameraIdList()[0]; } catch (Exception e) {}

        setupGestureDetector();
        createFloatingBar();
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); // 1 tap: Hộp âm thanh/QS
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); // Double tap: Hộp thoại nguồn
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); // Long press: Chụp màn hình
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (diffX > 100) { // Swipe Right
                        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } else if (diffX < -100) { // Swipe Left
                        toggleFlash();
                    }
                } else {
                    if (diffY > 100) { // Swipe Down
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); // Swipe Down: Tắt màn hình
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

    private void createFloatingBar() {
        floatingBar = new View(this);
        floatingBar.setBackgroundColor(Color.TRANSPARENT); // TRONG SUỐT 100%

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                100, // Độ dày vùng nhận diện (thu nhỏ/phình to tùy ý bạn)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM;

        floatingBar.setOnTouchListener((v, event) -> {
            // Kiểm tra: Nếu không phải màn hình khóa thì không nhận touch (để Sidebar của bạn hoạt động)
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (!km.isKeyguardLocked()) return false; 
            
            return gestureDetector.onTouchEvent(event);
        });

        windowManager.addView(floatingBar, params);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
