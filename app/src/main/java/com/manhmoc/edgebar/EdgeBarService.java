package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager windowManager;
    private View floatingBar;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingBar();
    }

    private void createFloatingBar() {
        floatingBar = new View(this);
        // Tạm thời để màu trắng mờ để bạn dễ nhìn thấy khi test. 
        // Sau này đổi Color.argb(50, 255,255,255) thành Color.TRANSPARENT là nó sẽ tàng hình.
        floatingBar.setBackgroundColor(Color.argb(50, 255, 255, 255)); 

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                80, // Chiều cao thanh bar
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM;

        // Cài đặt thao tác: Nhấn đúp (Double Tap) để tắt màn hình
        floatingBar.setOnTouchListener(new View.OnTouchListener() {
            private long lastClickTime = 0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < 300) {
                        // Gọi hàm tắt màn hình (Hỗ trợ Android 9 trở lên, Pixel 2 XL Android 11 chạy vô tư)
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    }
                    lastClickTime = clickTime;
                    return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingBar, params);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBar != null && windowManager != null) {
            windowManager.removeView(floatingBar);
        }
    }
}
