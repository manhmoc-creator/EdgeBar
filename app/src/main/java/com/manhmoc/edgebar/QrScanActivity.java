      package com.manhmoc.edgebar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.*;
import android.util.Patterns;
import android.util.Size;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;

/**
 * Quét QR thông minh kiểu Zalo/iPhone Camera + nhận diện QR thanh toán ngân hàng.
 * Tối ưu Pixel 2XL: analysis stream giữ cố định 1280x720 (đủ để giải mã, không
 * tốn thêm CPU/pin so với res cao hơn); preview stream tự chọn size khớp camera
 * thật rồi letterbox đúng tỉ lệ (fix lỗi hình bị bóp méo do trước đây ép SurfaceView
 * full màn hình bất kể tỉ lệ buffer thật).
 */
public class QrScanActivity extends Activity {
    private static final int REQ_CAMERA = 8801;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private volatile boolean paused = false;
    private SurfaceView surfaceView;
    private FrameLayout root;
    private View resultCard;
    private View frame;
    private final MultiFormatReader zxingReader = new MultiFormatReader();

    private String cameraId;
    private int sensorOrientation = 90;
    private Size previewSize;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
            BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
            BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.DATA_MATRIX));
        zxingReader.setHints(hints);

        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        setupScanner();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupScanner();
            } else {
                Toast.makeText(this, "Cần quyền Camera để quét QR!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void setupScanner() {
        if (!queryCameraInfo()) { finish(); return; }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        FrameLayout.LayoutParams svLp = computeLetterboxLayoutParams();
        root.addView(surfaceView, svLp);

        frame = new View(this);
        GradientDrawable fd = new GradientDrawable();
        fd.setStroke(10, Color.parseColor("#00E5FF")); // khung dày hơn (trước đây 4)
        fd.setCornerRadius(32f);
        frame.setBackground(fd);
        int fsize = (int) (getResources().getDisplayMetrics().widthPixels * 0.68f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(fsize, fsize);
        flp.gravity = Gravity.CENTER;
        root.addView(frame, flp);

        Button btnClose = new Button(this);
        btnClose.setText("✕");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(18);
        btnClose.setBackground(makeRounded("#66000000", 100f));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(110, 110);
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.setMargins(0, 60, 30, 0);
        btnClose.setOnClickListener(v -> finish());
        root.addView(btnClose, clp);

        setContentView(root);
        bgThread = new HandlerThread("qr-bg"); bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
        surfaceView.getHolder().setFixedSize(previewSize.getWidth(), previewSize.getHeight());
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { openCamera(h.getSurface()); }
            @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
            @Override public void surfaceDestroyed(SurfaceHolder h){}
        });
    }

    /** Chọn camera sau + đọc sensor orientation + chọn size preview khớp camera thật
     *  (KHÔNG ép cứng theo màn hình) — đây là gốc của lỗi hình bị bóp méo trước đây. */
    private boolean queryCameraInfo() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    Integer so = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = so != null ? so : 90;
                    StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    previewSize = choosePreviewSize(map.getOutputSizes(SurfaceHolder.class));
                    break;
                }
            }
            if (cameraId == null) {
                cameraId = cm.getCameraIdList()[0];
                CameraCharacteristics c = cm.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                previewSize = choosePreviewSize(map.getOutputSizes(SurfaceHolder.class));
            }
            return previewSize != null;
        } catch (Exception e) { return false; }
    }

    /** Ưu tiên size gần tỉ lệ 9:16 (khớp màn dọc), cap chiều dài <=1280 để nhẹ pin/CPU. */
    private Size choosePreviewSize(Size[] sizes) {
        Size best = null;
        double targetRatio = 16.0 / 9.0;
        for (Size s : sizes) {
            if (Math.max(s.getWidth(), s.getHeight()) > 1280) continue;
            double ratio = Math.max(s.getWidth(), s.getHeight()) / (double) Math.min(s.getWidth(), s.getHeight());
            if (best == null) { best = s; continue; }
            double bestRatio = Math.max(best.getWidth(), best.getHeight()) / (double) Math.min(best.getWidth(), best.getHeight());
            boolean closerRatio = Math.abs(ratio - targetRatio) < Math.abs(bestRatio - targetRatio);
            boolean biggerArea = s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight();
            if (closerRatio || (Math.abs(ratio - bestRatio) < 0.05 && biggerArea)) best = s;
        }
        if (best == null && sizes.length > 0) {
            // Không size nào <=1280 -> chọn size nhỏ nhất có sẵn (đỡ tốn pin nhất)
            best = sizes[0];
            for (Size s : sizes) if (s.getWidth()*s.getHeight() < best.getWidth()*best.getHeight()) best = s;
        }
        return best;
    }

    /** Tính khung hiển thị đúng tỉ lệ camera thật (contain/letterbox), không kéo méo. */
    private FrameLayout.LayoutParams computeLetterboxLayoutParams() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        boolean swapped = (sensorOrientation % 180) == 90;
        int dispW = swapped ? previewSize.getHeight() : previewSize.getWidth();
        int dispH = swapped ? previewSize.getWidth() : previewSize.getHeight();
        float scale = Math.max(screenW / (float) dispW, screenH / (float) dispH); // cover, tránh viền đen 2 bên
        int outW = Math.round(dispW * scale);
        int outH = Math.round(dispH * scale);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(outW, outH);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    private GradientDrawable makeRounded(String hex, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(hex));
        g.setCornerRadius(radius);
        return g;
    }

    private void openCamera(android.view.Surface previewSurface) {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onFrame, bgHandler);
            if (checkSelfPermission(android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) { finish(); return; }
            cm.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    camera = c;
                    try {
                        CaptureRequest.Builder req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        req.addTarget(reader.getSurface());
                        req.addTarget(previewSurface);
                        // Autofocus liên tục — QR ở khoảng cách gần (10-20cm) cần lấy nét
                        // chủ động, mặc định TEMPLATE_PREVIEW không lock focus gần.
                        req.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        List<android.view.Surface> targets = Arrays.asList(reader.getSurface(), previewSurface);
                        c.createCaptureSession(targets,
                            new CameraCaptureSession.StateCallback() {
                                @Override public void onConfigured(CameraCaptureSession s) {
                                    session = s;
                                    try { s.setRepeatingRequest(req.build(), null, bgHandler); } catch (Exception ignored) {}
                                }
                                @Override public void onConfigureFailed(CameraCaptureSession s) {}
                            }, bgHandler);
                    } catch (Exception ignored) {}
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); }
                @Override public void onError(CameraDevice c, int e) { c.close(); }
            }, bgHandler);
        } catch (Exception ignored) { finish(); }
    }

    private void onFrame(ImageReader r) {
        if (paused) { Image img = r.acquireLatestImage(); if (img != null) img.close(); return; }
        Image img = r.acquireLatestImage();
        if (img == null) return;
        try {
            byte[] y = new byte[img.getPlanes()[0].getBuffer().remaining()];
            img.getPlanes()[0].getBuffer().get(y);
            LuminanceSource src = new PlanarYUVLuminanceSource(y, img.getWidth(), img.getHeight(),
                0,0, img.getWidth(), img.getHeight(), false);
            Result result = tryDecode(src);
            if (result != null) {
                paused = true;
                runOnUiThread(() -> playDetectAnimThenShow(result.getText(), result));
            }
        } catch (Exception ignored) {
        } finally { img.close(); }
    }

    /** HybridBinarizer trước (nhanh, ánh sáng đều); rớt thì thử GlobalHistogramBinarizer
     *  (chịu ánh sáng không đều/độ tương phản cục bộ thấp — hay gặp ở QR có logo giữa). */
    private Result tryDecode(LuminanceSource src) {
        try {
            Result r = zxingReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
            zxingReader.reset();
            return r;
        } catch (Exception ignored) { zxingReader.reset(); }
        try {
            Result r = zxingReader.decodeWithState(
                new BinaryBitmap(new com.google.zxing.common.GlobalHistogramBinarizer(src)));
            zxingReader.reset();
            return r;
        } catch (Exception ignored) { zxingReader.reset(); }
        return null;
    }
    /** Hiệu ứng: khung đổi xanh lá + thu nhỏ có nảy (bounce) báo hiệu "đã khoá QR",
     *  hoạt động đồng nhất dù bạn cầm máy quét ngang/dọc/nghiêng (zxing tự xoay bất
     *  biến khi tìm 3 góc định vị của QR, không phụ thuộc hướng cầm máy). */
    private void playDetectAnimThenShow(String raw, Result zxingResult) {
        GradientDrawable gd = new GradientDrawable();
        gd.setStroke(10, Color.parseColor("#4CAF50"));
        gd.setCornerRadius(32f);
        frame.setBackground(gd);

        // [MỚI] Tính hệ số scale từ khoảng cách giữa các điểm định vị (finder pattern)
        // mà zxing trả về — QR quét được to (chụp gần) thì khung phóng to hơn, QR nhỏ
        // (chụp xa) thì khung thu nhỏ hơn, thay vì luôn co về 1 tỉ lệ cố định như cũ.
        // Không phụ thuộc hướng cầm máy vì chỉ dựa vào khoảng cách pixel giữa các điểm,
        // không dựa vào toạ độ x/y tuyệt đối.
        float targetScale = computeFrameTargetScale(zxingResult);

        frame.animate()
            .scaleX(targetScale).scaleY(targetScale)
            .rotationBy(360f) // xoay đúng 1 vòng khi "khoá" vào QR — hiệu ứng ôm khít
            .setDuration(360)
            .setInterpolator(new OvershootInterpolator(1.15f))
            .withEndAction(() -> {
                // Trả khung về kích thước/góc xoay gốc trước khi hiện kết quả, tránh
                // khung bị kẹt size lạ nếu người dùng bấm "Quét lại" ở màn kết quả.
                frame.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(180).start();
                showResult(raw);
            }).start();
    }

    /** Khung mặc định = 68% chiều rộng màn hình (xem fsize trong setupScanner()).
     *  Trả về hệ số scale so với size mặc định đó, sao cho khung ôm khít đúng kích
     *  thước thật của QR vừa quét được. Giới hạn trong [0.30, 1.40] để tránh khung
     *  co gần như biến mất hoặc phóng tràn màn hình nếu zxing bắt nhầm điểm định vị. */
    private float computeFrameTargetScale(Result result) {
        try {
            ResultPoint[] pts = result.getResultPoints();
            if (pts == null || pts.length < 2) return 0.72f; // fallback: hành vi cũ

            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (ResultPoint p : pts) {
                if (p == null) continue;
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            }
            float qrPixelSize = Math.max(maxX - minX, maxY - minY);
            if (qrPixelSize <= 0) return 0.72f;

            // Buffer phân tích cố định 1280x720 (xem ImageReader.newInstance trong openCamera()).
            // Dùng cạnh ngắn (720) làm chuẩn quy đổi vì khung vuông lấy 68% CHIỀU RỘNG
            // màn hình (= cạnh ngắn khi cầm máy dọc) làm kích thước gốc.
            float bufferShortSide = Math.min(1280, 720);
            float qrFractionOfBuffer = qrPixelSize / bufferShortSide;
            float rawScale = qrFractionOfBuffer / 0.68f;

            return Math.max(0.30f, Math.min(1.40f, rawScale));
        } catch (Exception e) {
            return 0.72f;
        }
    }
    // ===================== PHÂN LOẠI & HÀNH ĐỘNG =====================
    private void showResult(String raw) {
        if (resultCard != null) root.removeView(resultCard);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRounded("#F0121212", 28f));
        card.setPadding(50, 45, 50, 40);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(30, 0, 30, 60);

        QrParsed parsed = parseContent(raw);

        TextView tvType = new TextView(this);
        tvType.setText(parsed.typeLabel);
        tvType.setTextColor(Color.parseColor("#00E5FF"));
        tvType.setTextSize(13);
        card.addView(tvType);

        TextView tvContent = new TextView(this);
        tvContent.setText(parsed.displayText);
        tvContent.setTextColor(Color.WHITE);
        tvContent.setTextSize(15);
        tvContent.setPadding(0, 10, 0, 20);
        tvContent.setMaxLines(4);
        tvContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(tvContent);

        if (parsed.warning != null) {
            TextView tvWarn = new TextView(this);
            tvWarn.setText("⚠️ " + parsed.warning);
            tvWarn.setTextColor(Color.parseColor("#FFC107"));
            tvWarn.setTextSize(12);
            tvWarn.setPadding(0, 0, 0, 16);
            card.addView(tvWarn);
        }

        if (parsed.isBankQr) {
            card.addView(buildBankAppList());
        }

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnScanAgain = new Button(this);
        btnScanAgain.setText("↻ Quét lại");
        btnScanAgain.setBackground(makeRounded("#333333", 20f));
        btnScanAgain.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -2, 1f);
        sLp.setMargins(0, 0, 10, 0);
        btnScanAgain.setLayoutParams(sLp);
        btnScanAgain.setOnClickListener(v -> {
            root.removeView(resultCard); resultCard = null; paused = false;
            GradientDrawable fd = new GradientDrawable();
            fd.setStroke(10, Color.parseColor("#00E5FF"));
            fd.setCornerRadius(32f);
            frame.setBackground(fd);
        });
        btnRow.addView(btnScanAgain);

        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Sao chép");
        btnCopy.setBackground(makeRounded("#333333", 20f));
        btnCopy.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, -2, 1f);
        cLp.setMargins(10, 0, parsed.primaryAction != null ? 10 : 0, 0);
        btnCopy.setLayoutParams(cLp);
        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText("QR", raw));
            Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(btnCopy);

        if (parsed.primaryAction != null) {
            Button btnAction = new Button(this);
            btnAction.setText(parsed.actionLabel);
            btnAction.setBackground(makeRounded("#00E5FF", 20f));
            btnAction.setTextColor(Color.BLACK);
            btnAction.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            btnAction.setOnClickListener(v -> {
                if (parsed.needsConfirm) confirmThenRun(parsed);
                else runActionSafely(parsed);
            });
            btnRow.addView(btnAction);
        }
        card.addView(btnRow);

        resultCard = card;
        root.addView(card, lp);
    }
    /** [SỬA] Dùng LauncherApps quét qua từng UserHandle (giống getAppListCached()
     *  trong MainActivity) thay vì PackageManager.getPackageInfo() — vì
     *  getPackageInfo() KHÔNG thấy app nằm trong Island/Work profile (app nhân
     *  bản dùng UserHandle riêng, không cùng namespace với profile chính).
     *  Kết quả hiện dạng Icon + Tên app (tên có hậu tố "[Island]" nếu là bản
     *  nhân bản) để phân biệt rõ 2 bản của cùng 1 app. */
    private LinearLayout buildBankAppList() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 4, 0, 20);

        android.content.SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        String userList = prefs.getString("qr_bank_apps", "");
        java.util.List<String> wantedPkgs = new java.util.ArrayList<>();
        for (String p : userList.split(",")) {
            String t = p.trim();
            if (!t.isEmpty() && !wantedPkgs.contains(t)) wantedPkgs.add(t);
        }

        if (wantedPkgs.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Chưa có app ngân hàng nào. Vào Ecosystem > QR NGÂN HÀNG để thêm.");
            tv.setTextColor(Color.parseColor("#9AA0A6"));
            tv.setTextSize(12);
            box.addView(tv);
            return box;
        }

        // {pkg, label, drawable, isIsland} — quét từng package qua mọi profile
        java.util.List<Object[]> found = new java.util.ArrayList<>();
        try {
            android.os.UserManager um = (android.os.UserManager) getSystemService(USER_SERVICE);
            android.content.pm.LauncherApps la = (android.content.pm.LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
            for (String pkg : wantedPkgs) {
                for (android.os.UserHandle profile : um.getUserProfiles()) {
                    boolean isIsland = !profile.equals(android.os.Process.myUserHandle());
                    java.util.List<android.content.pm.LauncherActivityInfo> acts = la.getActivityList(pkg, profile);
                    if (acts != null && !acts.isEmpty()) {
                        android.content.pm.LauncherActivityInfo info = acts.get(0);
                        String label = info.getLabel().toString() + (isIsland ? " [Island]" : "");
                        found.add(new Object[]{pkg, label, info.getBadgedIcon(0), isIsland});
                    }
                }
            }
        } catch (Exception ignored) {}

        if (found.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Không tìm thấy app đã chọn trên máy (có thể đã gỡ cài đặt).");
            tv.setTextColor(Color.parseColor("#9AA0A6"));
            tv.setTextSize(12);
            box.addView(tv);
            return box;
        }

        TextView tvHint = new TextView(this);
        tvHint.setText("Mở bằng:");
        tvHint.setTextColor(Color.parseColor("#9AA0A6"));
        tvHint.setTextSize(12);
        tvHint.setPadding(0, 0, 0, 8);
        box.addView(tvHint);

        LinearLayout row = null; int i = 0;
        for (Object[] item : found) {
            if (i % 3 == 0) { // 3 cột (thay vì 4) vì giờ có thêm dòng tên app bên dưới icon
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            final String fPkg = (String) item[0];
            String label = (String) item[1];
            android.graphics.drawable.Drawable icon = (android.graphics.drawable.Drawable) item[2];
            final boolean fIsIsland = (boolean) item[3];

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.setMargins(6, 0, 6, 10);
            cell.setLayoutParams(clp);

            ImageView iv = new ImageView(this);
            if (icon != null) iv.setImageDrawable(icon);
            iv.setLayoutParams(new LinearLayout.LayoutParams(84, 84));
            cell.addView(iv);

            TextView tvLabel = new TextView(this);
            tvLabel.setText(label);
            tvLabel.setTextColor(Color.WHITE);
            tvLabel.setTextSize(10.5f);
            tvLabel.setMaxLines(1);
            tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setPadding(0, 4, 0, 0);
            cell.addView(tvLabel);

            cell.setOnClickListener(v -> {
                try {
                    if (fIsIsland) {
                        // Mở đúng bản trong Island qua LauncherApps + UserHandle riêng —
                        // getLaunchIntentForPackage() thường của PackageManager chỉ mở
                        // được app ở profile chính, không mở được bản nhân bản.
                        android.os.UserManager um2 = (android.os.UserManager) getSystemService(USER_SERVICE);
                        android.content.pm.LauncherApps la2 = (android.content.pm.LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
                        for (android.os.UserHandle profile : um2.getUserProfiles()) {
                            if (profile.equals(android.os.Process.myUserHandle())) continue;
                            java.util.List<android.content.pm.LauncherActivityInfo> acts = la2.getActivityList(fPkg, profile);
                            if (acts != null && !acts.isEmpty()) {
                                la2.startMainActivity(acts.get(0).getComponentName(), profile, null, null);
                                break;
                            }
                        }
                    } else {
                        Intent li = getPackageManager().getLaunchIntentForPackage(fPkg);
                        if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
                    }
                } catch (Exception ignored) {
                    Toast.makeText(this, "Không thể mở app này", Toast.LENGTH_SHORT).show();
                }
                finish();
            });
            row.addView(cell);
            i++;
        }
        return box;
    }
    private String T(String en, String vi) { return vi; }

    private void confirmThenRun(QrParsed p) {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Mở liên kết ngoài?")
            .setMessage("Bạn sắp truy cập:\n" + p.safeHostPreview
                + "\n\nChỉ mở nếu bạn tin tưởng nguồn này.")
            .setPositiveButton("Mở", (d, w) -> runActionSafely(p))
            .setNegativeButton("Huỷ", null)
            .show();
    }

    private void runActionSafely(QrParsed p) {
        try { p.primaryAction.run(); } catch (Exception e) {
            Toast.makeText(this, "Không thể thực hiện hành động này", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private static class QrParsed {
        String typeLabel, displayText, actionLabel, warning, safeHostPreview;
        boolean needsConfirm = false, isBankQr = false;
        Runnable primaryAction;
    }

    private QrParsed parseContent(String raw) {
        QrParsed p = new QrParsed();
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            Uri uri = Uri.parse(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost() != null ? uri.getHost() : trimmed;
            p.typeLabel = "🔗 Liên kết";
            p.displayText = trimmed;
            p.actionLabel = "Mở liên kết";
            p.safeHostPreview = host;
            if ("http".equalsIgnoreCase(scheme)) {
                p.warning = "Kết nối không mã hoá (http) — cẩn trọng khi nhập thông tin cá nhân.";
            }
            p.needsConfirm = true;
            p.primaryAction = () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            };
            return p;
        }

        if (lower.startsWith("intent://") || lower.startsWith("javascript:") || lower.startsWith("file:")) {
            p.typeLabel = "⛔ Nội dung không an toàn";
            p.displayText = trimmed;
            p.warning = "Mã QR này chứa lệnh có thể gây hại, EdgeBar đã chặn không cho thực thi.";
            return p;
        }

        if (trimmed.startsWith("WIFI:")) {
            String ssid = extractField(trimmed, "S:");
            String pass = extractField(trimmed, "P:");
            p.typeLabel = "📶 Mạng WiFi";
            p.displayText = "SSID: " + (ssid.isEmpty() ? "?" : ssid)
                + (pass.isEmpty() ? "" : "\nMật khẩu: " + pass);
            p.actionLabel = "Mở Cài đặt WiFi";
            p.primaryAction = () -> startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return p;
        }

        if (trimmed.startsWith("BEGIN:VCARD")) {
            String name = extractField(trimmed, "FN:");
            String tel = extractField(trimmed, "TEL:");
            p.typeLabel = "👤 Danh bạ";
            p.displayText = (name.isEmpty() ? "Liên hệ" : name) + (tel.isEmpty() ? "" : "\n" + tel);
            p.actionLabel = "Thêm liên hệ";
            p.primaryAction = () -> {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setType(android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                i.putExtra("data", raw.getBytes());
                try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) {
                    android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Contact", raw));
                }
            };
            return p;
        }

        if (lower.startsWith("tel:")) {
            String num = trimmed.substring(4);
            p.typeLabel = "📞 Số điện thoại";
            p.displayText = num;
            p.actionLabel = "Gọi điện";
            p.primaryAction = () -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return p;
        }

        if (lower.startsWith("smsto:")) {
            String[] parts = trimmed.substring(6).split(":", 2);
            p.typeLabel = "💬 Tin nhắn SMS";
            p.displayText = parts[0] + (parts.length > 1 ? "\n" + parts[1] : "");
            p.actionLabel = "Nhắn tin";
            p.primaryAction = () -> {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + parts[0]));
                if (parts.length > 1) i.putExtra("sms_body", parts[1]);
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            };
            return p;
        }

        if (trimmed.startsWith("MATMSG:") || lower.startsWith("mailto:")
                || Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            String email = trimmed.startsWith("MATMSG:") ? extractField(trimmed, "TO:")
                : lower.startsWith("mailto:") ? trimmed.substring(7) : trimmed;
            p.typeLabel = "✉️ Email";
            p.displayText = email;
            p.actionLabel = "Gửi email";
            p.primaryAction = () -> startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return p;
        }

        if (lower.startsWith("geo:")) {
            p.typeLabel = "📍 Vị trí";
            p.displayText = trimmed;
            p.actionLabel = "Xem bản đồ";
            p.primaryAction = () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return p;
        }

        if (trimmed.startsWith("000201") && trimmed.length() > 20) {
            p.typeLabel = "💳 Mã QR thanh toán (VietQR)";
            p.displayText = trimmed;
            p.isBankQr = true;
            p.warning = "Chọn ngân hàng để thanh toán";
            return p;
        }

        p.typeLabel = "📄 Văn bản";
        p.displayText = trimmed;
        p.actionLabel = "Tìm trên Google";
        p.primaryAction = () -> startActivity(new Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(android.app.SearchManager.QUERY, trimmed)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return p;
    }

    private String extractField(String src, String key) {
        int i = src.indexOf(key);
        if (i < 0) return "";
        int start = i + key.length();
        int end = src.indexOf(';', start);
        if (end < 0) end = src.indexOf('\n', start);
        if (end < 0) end = src.length();
        return src.substring(start, end).trim();
    }

    @Override protected void onDestroy() {
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        if (camera != null) camera.close();
        if (reader != null) reader.close();
        if (bgThread != null) bgThread.quitSafely();
        super.onDestroy();
    }
}
