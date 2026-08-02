      package com.manhmoc.edgebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.InputStream;

/**
 * MorseLockEngine — phần UI TĨNH của MorseLock (nền đen, bar/corner "morse_",
 * icon khoá, text nhập mật khẩu). Dùng CHUNG cho cả HomescreenService (Homeb,
 * wmType = TYPE_APPLICATION_OVERLAY) và EdgeBarService (Trợ năng, wmType =
 * TYPE_ACCESSIBILITY_OVERLAY) — mục đích là để MorseLock hoạt động được ngay
 * cả khi Trợ năng đang bật, không còn phụ thuộc Homeb phải sống 24/7.
 *
 * Bước này CHỈ chứa phần dựng UI + style. Logic nhập mật khẩu / đếm sai /
 * relock sẽ được thêm ở bước sau (giữ nguyên hành vi cũ trong HomescreenService
 * cho tới lúc đó).
 */
public class MorseLockEngine {

    private final Context ctx;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final int wmType; // TYPE_APPLICATION_OVERLAY (Homeb) hoặc TYPE_ACCESSIBILITY_OVERLAY (Trợ năng)

    public RelativeLayout morseContainer;
    public TextView tvMorseStatus;
    public TextView tvLockIcon;
    private MorseBackgroundView bgView;
    public MorseBarView[] mBars = new MorseBarView[8];
    public View[] mCorners = new View[4];
    private boolean isMorseLockActive = false;
    private String currentMorseAttempt = "";
    private int morseFailCount = 0;
    private String lockedPkg = "";
    private String dismissedPkg = "";
    private String pendingRelockOnWakePkg = "";
    private long dismissedTime = 0; // V19.12.3.6.8: dismissedPkg có thời hạn 3 giây

    private boolean isCountingDown = false;
    private Handler countdownHandler = new Handler();
    private Runnable countdownRunnable = null;
    private ValueAnimator warningAnimator = null;
    private boolean isPreviewMorse = false;
    private boolean isCoveringRecents = false;
    private String currentForegroundPkg = "";
    private boolean isUnlockCooldown = false;
    private boolean isUninstallGuardActive = false; // dùng chung cho cả Uninstall (type=1) và Admin Revoke (type=2)
private int uninstallGuardFailCount = 0;
private int uninstallGuardType = 0; // 0=none, 1=uninstall, 2=admin_revoke
    // V19.12.3.6.6: Throttle SYNC_STATE — chặn broadcast storm từ Zalo/Messenger
    private long lastSyncMs = 0;
    private static final long SYNC_THROTTLE_MS = 150;
    private static final int KBD_HEIGHT_CHANGE_THRESHOLD = 20;
    // HYBRID: cache trạng thái Accessibility, tránh đọc Settings mỗi frame
    private boolean accCacheValue = false;
    private long accCheckTimestamp = 0;
    private WindowManager.LayoutParams currentOverlayParams = null;

    
    private boolean isForceHome = false; // FIX 4: flag tức thì khi nhấn Home

    // HYBRID HOME V2: ContentObserver thay vì polling Settings DB
    // Chi phí = 0 CPU khi không có thay đổi, chỉ fire khi user thực sự toggle Accessibility
    private final Handler suicideHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable suicideRunnable = null;
    // V19.12.3.6.6: Guard chặn loop — scheduleSuicideCheck chỉ được có 1 pending tại 1 thời điểm
    private boolean suicideCheckPending = false;
    private Handler unlockCooldownHandler = new Handler(); 
    // Mỗi app bị khóa riêng — sai 5 lần ở Zalo không ảnh hưởng Messenger
private java.util.Map<String, Long> perPkgLockUntil = new java.util.HashMap<>();

    private int bgType = 0;
    private String bgImagePath = "";
    private Bitmap bgBitmap = null;
    private int cachedBgAlpha = 180;

    private final String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
    private final int[] M_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
            Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL,
            Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.CENTER};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
            Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    public MorseLockEngine(Context ctx, WindowManager wm, SharedPreferences prefs, int wmType) {
        this.ctx = ctx; this.wm = wm; this.prefs = prefs; this.wmType = wmType;
    }

    /** Dựng toàn bộ View và addView vào WindowManager — gọi 1 lần lúc service onCreate(). */
    public void build() {
        morseContainer = new RelativeLayout(ctx);
        morseContainer.setBackgroundColor(Color.TRANSPARENT);
        morseContainer.setVisibility(View.GONE);

        bgView = new MorseBackgroundView(ctx);
        morseContainer.addView(bgView, new RelativeLayout.LayoutParams(-1, -1));

        tvMorseStatus = new TextView(ctx);
        tvMorseStatus.setId(View.generateViewId());
        tvMorseStatus.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(-1, -2);
        tLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        tvMorseStatus.setTextSize(prefs.getInt("morse_text_size", 30));
        morseContainer.addView(tvMorseStatus, tLp);

        tvLockIcon = new TextView(ctx);
        tvLockIcon.setText("🔒");
        tvLockIcon.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams iconLp = new RelativeLayout.LayoutParams(-2, -2);
        iconLp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        morseContainer.addView(tvLockIcon, iconLp);
        tvLockIcon.setTextSize(prefs.getInt("morse_lock_icon_size", 48));

        WindowManager.LayoutParams bgP = new WindowManager.LayoutParams(-1, -1, wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT);
        try { wm.addView(morseContainer, bgP); } catch (Exception ignored) {}

        for (int i = 0; i < 8; i++) {
            mBars[i] = new MorseBarView(ctx, "morse_map_" + M_BARS[i]);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, wmType, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mBars[i], p); } catch (Exception ignored) {}
        }
        for (int i = 0; i < 4; i++) {
            mCorners[i] = new CornerView(ctx, i);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, wmType, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mCorners[i], p); } catch (Exception ignored) {}
        }

        cachedBgAlpha = prefs.getInt("morse_bg_alpha", 180);
        bgType = prefs.getInt("morse_bg_type", 0);
        bgImagePath = prefs.getString("morse_bg_image", "");
        reloadBackground();
    }

    /** Gỡ toàn bộ View khỏi WindowManager — gọi lúc service onDestroy(). */
    public void destroy() {
        try { if (morseContainer != null) wm.removeView(morseContainer); } catch (Exception ignored) {}
        for (int i = 0; i < 8; i++) try { if (mBars[i] != null) wm.removeView(mBars[i]); } catch (Exception ignored) {}
        for (int i = 0; i < 4; i++) try { if (mCorners[i] != null) wm.removeView(mCorners[i]); } catch (Exception ignored) {}
        if (bgBitmap != null && !bgBitmap.isRecycled()) { bgBitmap.recycle(); bgBitmap = null; }
    }

    public void reloadBackground() {
        bgType = prefs.getInt("morse_bg_type", 0);
        bgImagePath = prefs.getString("morse_bg_image", "");
        cachedBgAlpha = prefs.getInt("morse_bg_alpha", 180);
        if (bgType == 1 && !bgImagePath.isEmpty()) {
            try {
                InputStream is = ctx.getContentResolver().openInputStream(Uri.parse(bgImagePath));
                bgBitmap = BitmapFactory.decodeStream(is);
                if (bgView != null) bgView.invalidate();
            } catch (Exception e) { bgBitmap = null; }
        } else if (bgView != null) bgView.invalidate();
    }

    public void setBgAlphaLive(int alpha) {
        cachedBgAlpha = alpha;
        if (bgView != null) bgView.invalidate();
    }

    public void applyMorseTextStyle() {
        if (tvMorseStatus == null) return;
        String theme = prefs.getString("anim_color", "NEON");
        int textColor, glowColor;
        switch (theme) {
            case "OCEAN":    textColor = Color.parseColor("#00BFFF"); glowColor = Color.parseColor("#1E90FF"); break;
            case "AURORA":   textColor = Color.parseColor("#B388FF"); glowColor = Color.parseColor("#00E5FF"); break;
            case "ABYSS":    textColor = Color.parseColor("#1DE9B6"); glowColor = Color.parseColor("#00E5FF"); break;
            case "MIDNIGHT": textColor = Color.parseColor("#03A9F4"); glowColor = Color.parseColor("#7B1FA2"); break;
            case "CANDY":    textColor = Color.parseColor("#F06292"); glowColor = Color.parseColor("#4DD0E1"); break;
            default:         textColor = Color.WHITE; glowColor = Color.parseColor("#00E5FF"); break;
        }
        int blurRadius = prefs.getInt("morse_text_blur", 20);
        boolean neonOn = prefs.getBoolean("morse_text_neon", true);
        tvMorseStatus.setTextColor(textColor);
        tvMorseStatus.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        if (neonOn) tvMorseStatus.setShadowLayer(blurRadius, 0, 0, glowColor);
        else tvMorseStatus.setShadowLayer(4, 2, 2, Color.BLACK);
    }

    public void applyLockIconStyle() {
        if (tvLockIcon == null) return;
        String theme = prefs.getString("anim_color", "NEON");
        int glowColor;
        switch (theme) {
            case "OCEAN":    glowColor = Color.parseColor("#00BFFF"); break;
            case "AURORA":   glowColor = Color.parseColor("#B388FF"); break;
            case "ABYSS":    glowColor = Color.parseColor("#1DE9B6"); break;
            case "MIDNIGHT": glowColor = Color.parseColor("#03A9F4"); break;
            case "CANDY":    glowColor = Color.parseColor("#F06292"); break;
            default:         glowColor = Color.parseColor("#00E5FF"); break;
        }
        int blur = prefs.getInt("morse_text_blur", 20);
        boolean neonOn = prefs.getBoolean("morse_text_neon", true);
        if (neonOn) tvLockIcon.setShadowLayer(blur * 1.5f, 0, 0, glowColor);
        else tvLockIcon.setShadowLayer(6, 2, 2, Color.BLACK);
    }

    public void updateLockIconPosition() {
        if (tvLockIcon == null) return;
        int yVal = prefs.getInt("morse_lock_icon_y", 600);
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float yRatio = Math.max(0.1f, Math.min(0.85f, yVal / 3000f));
        int yPx = (int) (yRatio * dm.heightPixels);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) tvLockIcon.getLayoutParams();
        if (lp == null) return;
        lp.topMargin = yPx;
        lp.removeRule(RelativeLayout.CENTER_IN_PARENT);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        tvLockIcon.setLayoutParams(lp);
        tvLockIcon.setTextSize(prefs.getInt("morse_lock_icon_size", 48));
    }

    /** Đọc key số/ký tự đã gán qua Map Keys — dùng chung cho bar và corner. */
    public String mapComponentToNumber(String comp) {
        String key = "morse_map_" + comp.substring(6);
        String val = prefs.getString(key, "*");
        if (val.equals("X") || val.equals(">")) return val;
        if (val.matches("\\d")) return val;
        return "*";
    }
    private void doMorseVibrate() {
        if (prefs.getBoolean("morse_vib_en", true)) {
            int dur = prefs.getInt("morse_vib_dur", 30);
            if (dur <= 0) return;
            try {
                if (Build.VERSION.SDK_INT >= 26)
                    vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
                else vibrator.vibrate(dur);
            } catch (Exception e) {}
        }
    }
    private void startFailCountdown(int failCount, Runnable onFinished) {
        if (isCountingDown) return;
        boolean isHome = currentForegroundPkg.isEmpty()
                || currentForegroundPkg.contains("launcher")
                || currentForegroundPkg.contains("nexuslauncher");
        if (isHome) { onFinished.run(); return; }

        isCountingDown = true;
        String prefKey = (failCount == 3) ? "morse_lock3_seconds" : "morse_lock4_seconds";
        int totalSeconds = prefs.getInt(prefKey, (failCount == 3) ? 10 : 30);

        if (warningAnimator != null) warningAnimator.cancel();
        warningAnimator = ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f);
        warningAnimator.setDuration(1200);
        warningAnimator.addUpdateListener(anim -> {
            float val = (float) anim.getAnimatedValue();
            int red = (int)(180 * val);
            if (bgView != null) {
                bgView.setBackgroundColor(Color.argb((int)(80 * val), 255, 20, 60));
                bgView.invalidate();
            }
        });
        warningAnimator.start();

        final int[] remaining = {totalSeconds};
        if (tvMorseStatus != null)
            tvMorseStatus.setText("⏳ Chờ " + remaining[0] + "s");

        // [FIX-BUG-5] Bổ sung điều kiện kiểm tra vòng lặp: Nếu đã thoát ra HOME, lập tức hủy bộ đếm hoạt cảnh
        countdownRunnable = new Runnable() {
            @Override public void run() {
                boolean isCurrentHome = currentForegroundPkg.isEmpty() 
                        || currentForegroundPkg.contains("launcher") 
                        || currentForegroundPkg.contains("nexuslauncher")
                        || currentForegroundPkg.contains("quickstep");

                if (isCurrentHome || !isMorseLockActive) {
                    // Nếu kẻ trộm thoát ra Home, lập tức xóa sạch hoạt cảnh nháy đỏ đếm ngược
                    isCountingDown = false;
                    if (tvMorseStatus != null) tvMorseStatus.setText("");
                    if (bgView != null) {
                        bgView.setBackgroundColor(Color.TRANSPARENT);
                        bgView.invalidate();
                    }
                    countdownHandler.removeCallbacks(this);
                    return;
                }

                remaining[0]--;
                if (remaining[0] > 0) {
                    if (tvMorseStatus != null)
                        tvMorseStatus.setText("⏳ Chờ " + remaining[0] + "s");
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    isCountingDown = false;
                    if (tvMorseStatus != null) tvMorseStatus.setText("");
                    if (bgView != null) {
                        bgView.setBackgroundColor(Color.TRANSPARENT);
                        bgView.invalidate();
                    }
                    onFinished.run();
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }
    public void handleMorseTap(String comp, View v, boolean isLongPress) {
        if (isUninstallGuardActive) {
        doMorseVibrate();
        if (v != null) {
            if (v instanceof CornerView) ((CornerView) v).triggerFlash();
            else if (v instanceof MorseBarView) ((MorseBarView) v).triggerFlash();
        }
        String mappedKey = mapComponentToNumber(comp);
        String masterPass = prefs.getString("morse_master_pass", "");

        if (mappedKey.equals("X")) {
            if (!currentMorseAttempt.isEmpty())
                currentMorseAttempt = currentMorseAttempt.substring(0, currentMorseAttempt.length() - 1);
            tvMorseStatus.setText(currentMorseAttempt.isEmpty() ? "🔒 XÁC NHẬN GỠ CÀI ĐẶT" : currentMorseAttempt);
            return;
        }
        if (mappedKey.equals(">")) {
    if (!masterPass.isEmpty() && currentMorseAttempt.equals(masterPass)) {
        int doneType = uninstallGuardType;
        try {
            android.app.admin.DevicePolicyManager dpm =
    (android.app.admin.DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName adminComp =
    new android.content.ComponentName(ctx, EdgeAdminReceiver.class);
            if (dpm.isAdminActive(adminComp)) dpm.removeActiveAdmin(adminComp);
        } catch (Exception ignored) {}
        isUninstallGuardActive = false;
        uninstallGuardType = 0;
        currentMorseAttempt = "";
        morseContainer.setVisibility(View.GONE);
        morseContainer.setOnTouchListener(null);
        if (tvLockIcon != null) tvLockIcon.setOnTouchListener(null);
        if (doneType == 1) {
            new Handler().postDelayed(() -> {
                try {
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + ctx.getPackageName()));
                    uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(uninstallIntent);
                } catch (Exception e) {
                    Toast.makeText(ctx, "Đã gỡ quyền Admin. Vui lòng gỡ cài đặt lại.", Toast.LENGTH_LONG).show();
                }
            }, 400);
        } else {
            Toast.makeText(ctx, "Đã xác nhận. Quyền Admin đã được tắt.", Toast.LENGTH_SHORT).show();
        }
        updateVisibility();
    } else {
        uninstallGuardFailCount++;
        currentMorseAttempt = "";
        if (uninstallGuardFailCount >= 5) {
            tvMorseStatus.setText("Sai quá nhiều lần, thử lại sau");
            new Handler().postDelayed(() -> {
                isUninstallGuardActive = false;
                uninstallGuardType = 0;
                uninstallGuardFailCount = 0;
                morseContainer.setVisibility(View.GONE);
                morseContainer.setOnTouchListener(null);
                if (tvLockIcon != null) tvLockIcon.setOnTouchListener(null);
                Intent home = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                home.putExtra("act", "HOME");
                ctx.ctx.sendBroadcast(home);
            }, 2500);
        } else {
            tvMorseStatus.setText("Sai! Còn " + (5 - uninstallGuardFailCount) + " lần");
        }
    }
    return;
}
        if (mappedKey.matches("\\d") && currentMorseAttempt.length() < 20) {
            currentMorseAttempt += mappedKey;
            tvMorseStatus.setText(currentMorseAttempt);
        }
        return;
    }
    // ... phần code MorseLock app thường giữ nguyên như cũ ...
        String mappedKey = mapComponentToNumber(comp);
        String masterPass = prefs.getString("morse_master_pass", "");
        int realMaxLen = masterPass.length();

        if (mappedKey.equals("X")) {
    if (isLongPress) {
        // Long press X = xóa toàn bộ
        // V19.12.3.6.8: Cancel runnable TRƯỚC khi reset tránh race condition
        if (hideNumberRunnable != null) {
            numberDisplayHandler.removeCallbacks(hideNumberRunnable);
            hideNumberRunnable = null;
        }
        currentMorseAttempt = "";
        tvMorseStatus.setText("");
    } else {
        if (currentMorseAttempt.length() > 0) {
            // V19.12.3.6.8 THE ETERNAL EGO — Fix Bug 3: xóa ký tự không hiện lại số
            // Cancel runnable CŨ trước khi cắt attempt
            // Tránh race: runnable cũ chạy SAU khi đã cắt → setText sai nội dung
            if (hideNumberRunnable != null) {
                numberDisplayHandler.removeCallbacks(hideNumberRunnable);
                hideNumberRunnable = null;
            }
            currentMorseAttempt = currentMorseAttempt.substring(
                0, currentMorseAttempt.length() - 1);
            if (currentMorseAttempt.isEmpty()) {
                tvMorseStatus.setText("");
            } else {
                // SAU KHI XÓA: rebuild dot-string hoàn toàn
                // TẤT CẢ ký tự còn lại = dấu chấm, KHÔNG có số nào
                // Lý do: ký tự "mới nhất" sau khi xóa đã từng bị hide thành chấm
                // → hiện lại dưới dạng số là SAI về mặt UX
                // KHÔNG schedule hideNumberRunnable mới — không có số nào để hide
                StringBuilder dots = new StringBuilder();
                for (int di = 0; di < currentMorseAttempt.length(); di++) {
                    dots.append("• ");
                }
                tvMorseStatus.setText(dots.toString().trim());
            }
        }
    }
    return;
}
        else if (mappedKey.equals(">")) {
            if (currentMorseAttempt.isEmpty() || masterPass.isEmpty()) return;
            if (currentMorseAttempt.equals(masterPass)) {
    isUnlockCooldown = true;
    unlockCooldownHandler.removeCallbacksAndMessages(null);
    unlockCooldownHandler.postDelayed(() -> {
        isUnlockCooldown = false;
    }, 1000);

    unlockedApps.add(lockedPkg);
    lastUnlockedTime = System.currentTimeMillis();
    isMorseLockActive = false;
    morseFailCount = 0;
    perPkgLockUntil.remove(lockedPkg); // Unlock xong → xóa phạt của app đó
    currentMorseAttempt = "";
    morseContainer.setVisibility(View.GONE);

    Intent success = new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
    success.putExtra("pkg", lockedPkg);
    ctx.sendBroadcast(success);
    updateVisibility();
} else {
                morseFailCount++;
                doMorseVibrate();
                String insult = prefs.getString("morse_insult_" + Math.min(morseFailCount, 5), "Saiii!");
                currentMorseAttempt = "";

                // [FIX-5] Điều hướng kích hoạt bộ đếm thời gian nháy đỏ tại app bị khóa tùy biến theo số lần sai
                if (morseFailCount == 3 || morseFailCount == 4) {
                    tvMorseStatus.setText(insult);
                    startFailCountdown(morseFailCount, () -> {
                        if (isMorseLockActive && tvMorseStatus != null) tvMorseStatus.setText("");
                    });
               } else if (morseFailCount >= 5) {
    tvMorseStatus.setText(insult);
    int lockMinutes = prefs.getInt("morse_lock_minutes", 30);
    // Chỉ phạt ĐÚNG app vừa nhập sai — Zalo phạt thì Messenger vẫn hỏi mật khẩu bình thường
String punishedPkg = lockedPkg;
long lockUntil = System.currentTimeMillis() + lockMinutes * 60 * 1000L;
perPkgLockUntil.put(punishedPkg, lockUntil);

isMorseLockActive = false;
lockedPkg = "";
morseFailCount = 0;
currentMorseAttempt = "";
Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
kick.putExtra("act", "HOME");
ctx.sendBroadcast(kick);
new Handler().postDelayed(() -> updateVisibility(), 500);
} else {
                    tvMorseStatus.setText(insult);
                }
            }
            return;
        }

        if (currentMorseAttempt.length() >= realMaxLen && realMaxLen > 0) {
            morseFailCount++;
            doMorseVibrate();
            String insult = prefs.getString("morse_insult_" + Math.min(morseFailCount,5), "Quá dài!");
            tvMorseStatus.setText(insult);
            currentMorseAttempt = "";
            if (morseFailCount >= 5) {
    int lockMinutes = prefs.getInt("morse_lock_minutes", 30);
    String punishedPkg = lockedPkg;
long lockUntil = System.currentTimeMillis() + lockMinutes * 60 * 1000L;
perPkgLockUntil.put(punishedPkg, lockUntil);
    isMorseLockActive = false;
    lockedPkg = "";
    morseFailCount = 0;
    currentMorseAttempt = "";
    Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
    kick.putExtra("act", "HOME");
    ctx.sendBroadcast(kick);
}
            return;
        }

        currentMorseAttempt += mappedKey;
// FIX-DOT-2: Hiện dạng "••• 5" — các số cũ đã thành dấu chấm,
// chỉ số mới nhất hiện rõ, không bao giờ hiện lại số cũ
if (hideNumberRunnable != null) numberDisplayHandler.removeCallbacks(hideNumberRunnable);
int showNumberMs = prefs.getInt("morse_show_number_ms", 800);
// Tạo chuỗi hiển thị: dấu chấm cho các ký tự cũ + số mới nhất
StringBuilder displayStr = new StringBuilder();
for (int i = 0; i < currentMorseAttempt.length() - 1; i++) displayStr.append("• ");
displayStr.append(mappedKey); // chỉ số mới nhất hiện dạng số
tvMorseStatus.setText(displayStr.toString());
// Sau showNumberMs: đổi số mới nhất thành dấu chấm
hideNumberRunnable = () -> {
    StringBuilder dots = new StringBuilder();
    for (int i = 0; i < currentMorseAttempt.length(); i++) dots.append("• ");
    tvMorseStatus.setText(dots.toString());
};
numberDisplayHandler.postDelayed(hideNumberRunnable, showNumberMs);
    }



    // ==================== INNER VIEWS ====================

    private class MorseBackgroundView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        MorseBackgroundView(Context c) { super(c); setWillNotDraw(false); setLayerType(LAYER_TYPE_SOFTWARE, null); }
        @Override public void setVisibility(int v) {
            super.setVisibility(v);
            if (v == View.VISIBLE) { setWillNotDraw(false); post(this::invalidate); }
            else setWillNotDraw(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (bgType == 1 && bgBitmap != null && !bgBitmap.isRecycled()) {
                paint.setAlpha(cachedBgAlpha);
                canvas.drawBitmap(bgBitmap, null, new Rect(0, 0, getWidth(), getHeight()), paint);
            } else {
                canvas.drawColor(Color.argb(cachedBgAlpha, 0, 0, 0));
            }
        }
    }

    public class MorseBarView extends View {
        private Handler autoHideHandler = new Handler();
        private int visMode = 0; private boolean isAutoHiding = false; private int hideDelay = 2500;
        private GradientDrawable gd; private String labelKey; private Paint labelPaint;
        MorseBarView(Context c, String labelKey) {
            super(c); this.labelKey = labelKey;
            gd = new GradientDrawable(); gd.setCornerRadius(24f); setBackground(gd);
            labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(Color.WHITE); labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setShadowLayer(6f, 0, 0, Color.BLACK);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            String v = prefs.getString(labelKey, "*");
            if (v.equals("*") || v.isEmpty()) return;
            labelPaint.setTextSize(prefs.getInt("morse_map_label_size", 20));
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            canvas.drawText(v, getWidth()/2f, getHeight()/2f - (fm.ascent+fm.descent)/2, labelPaint);
        }
        public void updateProps(int alpha, int mode, int delay) {
            this.visMode = mode; this.hideDelay = delay;
            if (mode == 0) { setAlpha(alpha/255f); autoHideHandler.removeCallbacksAndMessages(null); isAutoHiding = false; }
            else if (mode == 1) { setAlpha(0f); isAutoHiding = true; }
            else { setAlpha(0f); isAutoHiding = false; }
            gd.setColor(Color.argb(alpha, 96, 125, 139));
        }
        public void triggerFlash() {
            if (visMode != 1 || !isAutoHiding) return;
            autoHideHandler.removeCallbacksAndMessages(null);
            setAlpha(1f);
            autoHideHandler.postDelayed(() -> { if (visMode == 1) setAlpha(0f); }, hideDelay);
        }
    }

    public class CornerView extends View {
        private Paint pFill, pStroke; private int type;
        private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false;
        private int baseMoonAlpha, baseStrokeAlpha, hideDelay; private boolean isInv = false;
        private Paint labelPaint;
        CornerView(Context c, int type) {
            super(c); this.type = type;
            pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true);
            pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        }
        public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) {
            pStroke.setStrokeWidth(thick);
            baseMoonAlpha = moonAlpha; baseStrokeAlpha = strokeAlpha; isAutoHiding = autoHide; hideDelay = delay; isInv = inv;
            if (inv) { pFill.setAlpha(0); pStroke.setAlpha(0); }
            else if (!autoHide) { pFill.setColor(Color.argb(moonAlpha, 96, 125, 139)); pStroke.setAlpha(strokeAlpha); }
            else { pFill.setColor(Color.argb(0, 96, 125, 139)); pStroke.setAlpha(0); }
            invalidate();
        }
        public void triggerFlash() {
            if (!isAutoHiding || isInv) return;
            autoHideHandler.removeCallbacksAndMessages(null);
            pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha+50), 96,125,139));
            pStroke.setAlpha(Math.min(255, baseStrokeAlpha+50)); invalidate();
            autoHideHandler.postDelayed(() -> {
                android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(1f, 0f);
                a.setDuration(1500);
                a.addUpdateListener(anim -> {
                    float val = (float) anim.getAnimatedValue();
                    pFill.setColor(Color.argb((int)(baseMoonAlpha*val), 96,125,139));
                    pStroke.setAlpha((int)(baseStrokeAlpha*val)); invalidate();
                });
                a.start();
            }, hideDelay);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(), pad = thick/2;
            String ck = "morse_corner_" + CORNERS[type] + "_";
            int shapeMode = prefs.getInt(ck+"shape", 0);
            float sRad = prefs.getInt(ck+"rad", 80) / 1000f, mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
            float sw = prefs.getInt(ck+"w", 100), sh = prefs.getInt(ck+"h", 100);
            float mw = prefs.getInt(ck+"moon_w", 100), mh = prefs.getInt(ck+"moon_h", 100);
            Path moonPath = new Path(), strokePath = new Path();
            float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
            float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;
            if (type==0) { sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f); }
            else if (type==1) { sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f); }
            else if (type==2) { sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f); }
            else { sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=0; mTipX=mw; mTipY=mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f); }
            if (shapeMode==1) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sTipX, sRootY); }
            else if (shapeMode==2) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sRootX, sTipY); }
            else { strokePath.moveTo(sRootX, sTipY); strokePath.quadTo(sCtrlX, sCtrlY, sTipX, sRootY); }
            if (type==0||type==1) { moonPath.moveTo(mRootX, mTipY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mTipX, mRootY); moonPath.quadTo(mCtrlX, mCtrlY, mRootX, mTipY); }
            else { moonPath.moveTo(mTipX, mRootY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mRootX, mTipY); moonPath.quadTo(mCtrlX, mCtrlY, mTipX, mRootY); }
            moonPath.close();
            canvas.drawPath(strokePath, pStroke);
            float mx = prefs.getInt(ck+"moon_x", 1250) - 1250, my = prefs.getInt(ck+"moon_y", 1250) - 1250;
            canvas.save(); canvas.translate(mx, my); canvas.drawPath(moonPath, pFill); canvas.restore();
            String v = prefs.getString("morse_map_corner_" + CORNERS[type], "*");
            if (!v.equals("*") && !v.isEmpty()) {
                if (labelPaint == null) {
                    labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    labelPaint.setColor(Color.WHITE); labelPaint.setTextAlign(Paint.Align.CENTER);
                    labelPaint.setShadowLayer(6f, 0, 0, Color.BLACK);
                }
                labelPaint.setTextSize(prefs.getInt("morse_map_label_size", 20));
                Paint.FontMetrics fm = labelPaint.getFontMetrics();
                canvas.drawText(v, tw/2f, th/2f - (fm.ascent+fm.descent)/2, labelPaint);
            }
        }
    }
    public void engage(String pkg) {
        isMorseLockActive = true;
        lockedPkg = pkg;
        morseFailCount = 0;
        currentMorseAttempt = "";
        if (tvMorseStatus != null) tvMorseStatus.setText("");
        applyMorseTextStyle();
        applyLockIconStyle();
        updateLockIconPosition();
        morseContainer.setVisibility(View.VISIBLE);
    }
    public void dismiss() {
        isMorseLockActive = false;
        morseFailCount = 0;
        currentMorseAttempt = "";
        lockedPkg = "";
        morseContainer.setVisibility(View.GONE);
    }
    public boolean isActive() { return isMorseLockActive; }
}
