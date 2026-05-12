package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cameraId = cameraManager.getCameraIdList()[0]; } catch (Exception e) {}

        setupGestureDetector();
        createFloatingBars();
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // TẮT HẲN Ở MÀN HÌNH CHÍNH, CHỈ HOẠT ĐỘNG Ở LOCKSCREEN
                if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); 
                }
                return true;
            }
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); 
                return true;
            }
            @Override
            public void onLongPress(MotionEvent e) {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > 40) {
                        if (diffX > 0) {
                            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else toggleFlash();
                    }
                } else {
                    if (Math.abs(diffY) > 40) {
                        if (diffY < 0) performGlobalAction(GLOBAL_ACTION_HOME); // Vuốt lên
                        else triggerGemini(); // VUỐT XUỐNG -> GỌI GEMINI
                    }
                }
                return true;
            }
        });
    }

    private void triggerGemini() {
        try {
            Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
            intent.setPackage("com.google.android.googlequicksearchbox");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } catch (Exception e) { performGlobalAction(GLOBAL_ACTION_ASSIST); }
    }

    private void toggleFlash() {
        try { isFlashOn = !isFlashOn; cameraManager.setTorchMode(cameraId, isFlashOn); } catch (Exception e) {}
    }

    private void createFloatingBars() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int gap = 250;
        int barWidth = (metrics.widthPixels - gap) / 2;

        leftBar = new View(this); rightBar = new View(this);
        leftBar.setBackgroundColor(Color.TRANSPARENT); rightBar.setBackgroundColor(Color.TRANSPARENT);

        WindowManager.LayoutParams paramsLeft = new WindowManager.LayoutParams(barWidth, 80, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        paramsLeft.gravity = Gravity.BOTTOM | Gravity.LEFT;

        WindowManager.LayoutParams paramsRight = new WindowManager.LayoutParams(barWidth, 80, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        paramsRight.gravity = Gravity.BOTTOM | Gravity.RIGHT;

        View.OnTouchListener touchListener = (v, event) -> gestureDetector.onTouchEvent(event);
        leftBar.setOnTouchListener(touchListener); rightBar.setOnTouchListener(touchListener);

        windowManager.addView(leftBar, paramsLeft); windowManager.addView(rightBar, paramsRight);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        super.onDestroy();
        if (leftBar != null) windowManager.removeView(leftBar);
        if (rightBar != null) windowManager.removeView(rightBar);
    }
}
