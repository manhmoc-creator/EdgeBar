package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MorseLockService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private FrameLayout container;
    private MorseInputView inputView;
    private TextView statusText;
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private String currentLockedPackage = "";
    private String inputBuffer = "";
    private String correctPassword = "";
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isOverlayShown = false;

    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            if ("com.manhmoc.edgebar.MORSE_COMMAND".equals(action)) {
                String cmd = i.getStringExtra("cmd");
                if ("show".equals(cmd)) {
                    String pkg = i.getStringExtra("pkg");
                    showOverlay(pkg);
                } else if ("hide".equals(cmd)) {
                    hideOverlay();
                } else if ("toggle".equals(cmd)) {
                    if (isOverlayShown) hideOverlay(); else {
                        String pkg = getForegroundPackage();
                        if (pkg != null && isPackageLocked(pkg)) showOverlay(pkg);
                    }
                }
            }
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        correctPassword = prefs.getString("morse_password", ".-");  // mặc định morse "A"
        IntentFilter f = new IntentFilter("com.manhmoc.edgebar.MORSE_COMMAND");
        registerReceiver(ipcReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        createOverlay();
        // Foreground notification (bắt buộc với Accessibility Service) nhưng giản lược
        String cid = "eb_morse_svc";
        NotificationChannel nc = new NotificationChannel(cid, "Morse Lock", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Morse Lock ready")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(false).build();
        startForeground(2, n);
    }

    private void createOverlay() {
        container = new FrameLayout(this);
        container.setBackgroundColor(0x00000000);
        
        // Input view chính (vẽ hiệu ứng sọc lag, vùng chạm)
        inputView = new MorseInputView(this, this::onMorseTap, this::onDelete, this::onSubmit);
        container.addView(inputView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Status text nhỏ ở giữa
        statusText = new TextView(this);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(24);
        statusText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.CENTER | Gravity.TOP;
        tp.topMargin = 200;
        container.addView(statusText, tp);

        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY :
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.LEFT;
        wlp.x = 0;
        wlp.y = 0;
        overlay = container;
        // Không add ngay, chỉ khi cần
    }

    private void showOverlay(String pkg) {
        if (isOverlayShown) return;
        currentLockedPackage = pkg;
        inputBuffer = "";
        inputView.reset();
        statusText.setText("");
        try {
            wm.addView(overlay, (WindowManager.LayoutParams) overlay.getLayoutParams());
        } catch (Exception e) { /* đã tồn tại */ }
        isOverlayShown = true;
    }

    private void hideOverlay() {
        if (!isOverlayShown) return;
        try { wm.removeView(overlay); } catch (Exception e) {}
        isOverlayShown = false;
        currentLockedPackage = "";
        // Gửi broadcast unlock
        Intent i = new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
        i.putExtra("pkg", currentLockedPackage);  // có thể rỗng
        sendBroadcast(i);
    }

    private void onMorseTap(String symbol) {
        inputBuffer += symbol;
        statusText.setText(inputBuffer);
        if (inputBuffer.length() > 10) inputBuffer = inputBuffer.substring(inputBuffer.length()-10);
    }

    private void onDelete() {
        if (inputBuffer.length() > 0) {
            inputBuffer = inputBuffer.substring(0, inputBuffer.length()-1);
            statusText.setText(inputBuffer);
        }
    }

    private void onSubmit() {
        if (inputBuffer.equals(correctPassword)) {
            hideOverlay();
        } else {
            // Rung báo sai
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                } else vibrator.vibrate(100);
            }
            statusText.setText("SAI");
            inputBuffer = "";
            inputView.reset();
            handler.postDelayed(() -> statusText.setText(""), 1000);
        }
    }

    private boolean isPackageLocked(String pkg) {
        String locklist = prefs.getString("locklist", "");
        if (locklist.isEmpty()) return false;
        for (String p : locklist.split(",")) {
            if (p.trim().equals(pkg)) return true;
        }
        return false;
    }

    private String getForegroundPackage() {
        // Có thể lấy từ AccessibilityEvent, nhưng tạm thời trả về package từ SharedPrefs lưu gần nhất
        return prefs.getString("last_fg_pkg", "");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
            prefs.edit().putString("last_fg_pkg", pkg).apply();
            if (isOverlayShown && !pkg.equals(currentLockedPackage)) {
                hideOverlay(); // App khác rồi thì ẩn đi
            }
            if (!isOverlayShown && isPackageLocked(pkg) && !pkg.equals(currentLockedPackage)) {
                // Kiểm tra xem có phải vừa unlock không? currentLockedPackage sẽ rỗng sau khi unlock
                showOverlay(pkg);
            }
        }
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(ipcReceiver); } catch (Exception e) {}
        if (isOverlayShown) hideOverlay();
    }

    // ==================== MORSE INPUT VIEW (vẽ hiệu ứng sọc lag) ====================
    public static class MorseInputView extends View {
        private Paint pLine;
        private Paint pDot;
        private Paint pDash;
        private Random rnd = new Random();
        private float[] lineOffsets;
        private int cols = 3, rows = 3;
        private float cellW, cellH;
        private int activeCol = -1, activeRow = -1;
        private long touchDownTime;
        private boolean longPress = false;
        private static final int LONG_PRESS_MS = 300;
        private Handler h = new Handler(Looper.getMainLooper());
        private Runnable onTap, onDelete, onSubmit;

        public MorseInputView(Context context, Runnable onTap, Runnable onDelete, Runnable onSubmit) {
            super(context);
            this.onTap = onTap;
            this.onDelete = onDelete;
            this.onSubmit = onSubmit;
            pLine = new Paint();
            pLine.setColor(Color.argb(80, 0, 255, 100));
            pLine.setStrokeWidth(3);
            pLine.setStyle(Paint.Style.STROKE);
            pDot = new Paint();
            pDot.setColor(Color.WHITE);
            pDot.setStyle(Paint.Style.FILL);
            pDash = new Paint();
            pDash.setColor(Color.RED);
            pDash.setStyle(Paint.Style.FILL);
            setOnTouchListener((v, e) -> {
                int action = e.getAction();
                float x = e.getX(), y = e.getY();
                int col = (int)(x / (getWidth()/3f));
                int row = (int)(y / (getHeight()/3f));
                if (col >= 3) col = 2; if (row >= 3) row = 2;
                if (action == MotionEvent.ACTION_DOWN) {
                    activeCol = col; activeRow = row;
                    touchDownTime = System.currentTimeMillis();
                    longPress = false;
                    invalidate();
                    h.postDelayed(() -> {
                        if (activeCol == col && activeRow == row && System.currentTimeMillis() - touchDownTime >= LONG_PRESS_MS) {
                            longPress = true;
                            if (onTap != null) onTap.run();  // tạm coi long press = dash, tap = dot
                        }
                    }, LONG_PRESS_MS);
                    return true;
                } else if (action == MotionEvent.ACTION_UP) {
                    if (activeCol == col && activeRow == row && !longPress) {
                        if (onTap != null) onTap.run();
                    }
                    activeCol = -1; activeRow = -1;
                    invalidate();
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    activeCol = -1; activeRow = -1;
                    invalidate();
                }
                return true;
            });
            // Nút X và > nằm ở góc phải dưới và góc phải trên? Ta sẽ vẽ trong onDraw
        }

        public void reset() { activeCol = -1; activeRow = -1; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            cellW = w/cols;
            cellH = h/rows;
            // Vẽ nền sọc lag (hiệu ứng đường kẻ răng cưa)
            if (lineOffsets == null || lineOffsets.length != rows) {
                lineOffsets = new float[rows];
                for (int i=0; i<rows; i++) lineOffsets[i] = rnd.nextFloat() * cellW;
            }
            pLine.setColor(Color.argb(100, 0, 255, 100));
            for (int i=0; i<rows; i++) {
                float y = i*cellH + 20;
                canvas.drawLine(0, y, w, y + lineOffsets[i], pLine);
                canvas.drawLine(0, y+cellH/2, w, y+cellH/2 + lineOffsets[i], pLine);
            }
            // Vẽ lưới mờ
            pLine.setColor(Color.argb(30,255,255,255));
            for (int c=0; c<=cols; c++) {
                canvas.drawLine(c*cellW, 0, c*cellW, h, pLine);
            }
            for (int r=0; r<=rows; r++) {
                canvas.drawLine(0, r*cellH, w, r*cellH, pLine);
            }
            // Highlight ô được chạm
            if (activeCol >= 0 && activeRow >= 0) {
                Paint highlight = new Paint();
                highlight.setColor(Color.argb(60, 0, 255, 255));
                highlight.setStyle(Paint.Style.FILL);
                canvas.drawRect(activeCol*cellW, activeRow*cellH, (activeCol+1)*cellW, (activeRow+1)*cellH, highlight);
            }
            // Vẽ nút X ở góc dưới phải
            Paint btn = new Paint();
            btn.setColor(Color.RED);
            btn.setTextSize(60);
            canvas.drawText("X", w-120, h-80, btn);
            // Nút > ở góc dưới trái? Ta vẽ bên phải cạnh X
            canvas.drawText(">", w-240, h-80, btn);
        }

        // Override để bắt sự kiện nút
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            float w = getWidth(), h = getHeight();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (x > w-240 && x < w-120 && y > h-160 && y < h-60) {
                    // nút >
                    if (onSubmit != null) onSubmit.run();
                    return true;
                } else if (x > w-120 && x < w-20 && y > h-160 && y < h-60) {
                    // nút X
                    if (onDelete != null) onDelete.run();
                    return true;
                }
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
