      package com.manhmoc.edgebar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.*;
import android.util.Patterns;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;

/**
 * Quét QR thông minh kiểu Zalo/iPhone Camera:
 * - Nhận diện loại nội dung (Link, WiFi, Danh bạ, SĐT, Email, SMS, Vị trí, Văn bản thường,
 *   mã QR thanh toán VietQR/EMVCo) và đưa ra hành động tương ứng thay vì chỉ hiện text cứng.
 * - Cảnh báo an toàn trước khi mở link (hiện rõ domain thật, chặn scheme nguy hiểm
 *   như javascript:/intent:/file: để chống link giả mạo/khai thác lỗ hổng).
 * - Fix quét được cả QR có logo giữa (Messenger...): tăng độ phân giải khung hình,
 *   bật autofocus liên tục, bật TRY_HARDER cho zxing.
 * Zero-RAM overhead: không giữ Bitmap/Thread nào sau khi Activity đóng.
 */
public class QrScanActivity extends Activity {
    private static final int REQ_CAMERA = 8801;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private volatile boolean paused = false; // true khi đang hiện dialog kết quả
    private android.view.Surface previewSurface;
    private FrameLayout root;
    private View resultCard;
    private final MultiFormatReader zxingReader = new MultiFormatReader();

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
        root = new FrameLayout(this);
        SurfaceView sv = new SurfaceView(this);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        // Khung ngắm ở giữa — chỉ trang trí, không ảnh hưởng vùng giải mã thật
        View frame = new View(this);
        GradientDrawable fd = new GradientDrawable();
        fd.setStroke(4, Color.parseColor("#00E5FF"));
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
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { previewSurface = h.getSurface(); openCamera(); }
            @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
            @Override public void surfaceDestroyed(SurfaceHolder h){}
        });
    }

    private GradientDrawable makeRounded(String hex, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(hex));
        g.setCornerRadius(radius);
        return g;
    }

    private void openCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            // Tăng độ phân giải từ 1280x720 lên 1920x1080 — QR có logo giữa (Messenger...)
            // cần mật độ pixel cao hơn mới đủ chi tiết để zxing giải mã được các module nhỏ.
            reader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onFrame, bgHandler);
            if (checkSelfPermission(android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) { finish(); return; }
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    camera = c;
                    try {
                        CaptureRequest.Builder req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        req.addTarget(reader.getSurface());
                        // Autofocus liên tục — mặc định TEMPLATE_PREVIEW không lock focus gần,
                        // khiến QR ở khoảng cách gần (10-20cm, phổ biến khi quét) bị mờ và
                        // không giải mã nổi dù ảnh đã lên khung hình.
                        req.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        List<android.view.Surface> targets = new ArrayList<>();
                        targets.add(reader.getSurface());
                        if (previewSurface != null) {
                            req.addTarget(previewSurface);
                            targets.add(previewSurface);
                        }
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
            String text = tryDecode(src);
            if (text != null) {
                paused = true;
                runOnUiThread(() -> showResult(text));
            }
        } catch (Exception ignored) {
        } finally { img.close(); }
    }

    /** Giải mã với HybridBinarizer trước (nhanh, tốt cho ánh sáng đều); nếu thất bại
     *  thử lại với GlobalHistogramBinarizer (chịu được ánh sáng không đều / QR có logo
     *  làm giảm độ tương phản cục bộ — đúng trường hợp Messenger). */
    private String tryDecode(LuminanceSource src) {
        try {
            Result r = zxingReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
            zxingReader.reset();
            return r.getText();
        } catch (Exception ignored) { zxingReader.reset(); }
        try {
            Result r = zxingReader.decodeWithState(
                new BinaryBitmap(new com.google.zxing.common.GlobalHistogramBinarizer(src)));
            zxingReader.reset();
            return r.getText();
        } catch (Exception ignored) { zxingReader.reset(); }
        return null;
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

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnScanAgain = new Button(this);
        btnScanAgain.setText("↻ Quét lại");
        btnScanAgain.setBackground(makeRounded("#333333", 20f));
        btnScanAgain.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -2, 1f);
        sLp.setMargins(0, 0, 10, 0);
        btnScanAgain.setLayoutParams(sLp);
        btnScanAgain.setOnClickListener(v -> { root.removeView(resultCard); resultCard = null; paused = false; });
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
        boolean needsConfirm = false;
        Runnable primaryAction;
    }

    private QrParsed parseContent(String raw) {
        QrParsed p = new QrParsed();
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // --- Link web ---
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

        // --- Chặn scheme nguy hiểm (chống khai thác lỗ hổng qua intent://, javascript:, file:) ---
        if (lower.startsWith("intent://") || lower.startsWith("javascript:") || lower.startsWith("file:")) {
            p.typeLabel = "⛔ Nội dung không an toàn";
            p.displayText = trimmed;
            p.warning = "Mã QR này chứa lệnh có thể gây hại, EdgeBar đã chặn không cho thực thi.";
            p.actionLabel = null; p.primaryAction = null;
            return p;
        }

        // --- WiFi ---
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

        // --- Danh bạ (vCard) ---
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

        // --- Số điện thoại ---
        if (lower.startsWith("tel:")) {
            String num = trimmed.substring(4);
            p.typeLabel = "📞 Số điện thoại";
            p.displayText = num;
            p.actionLabel = "Gọi điện";
            p.primaryAction = () -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return p;
        }

        // --- SMS ---
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

        // --- Email ---
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

        // --- Vị trí ---
        if (lower.startsWith("geo:")) {
            p.typeLabel = "📍 Vị trí";
            p.displayText = trimmed;
            p.actionLabel = "Xem bản đồ";
            p.primaryAction = () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return p;
        }

        // --- Mã QR thanh toán VietQR/EMVCo (thường bắt đầu bằng "000201") ---
        if (trimmed.startsWith("000201") && trimmed.length() > 20) {
            p.typeLabel = "💳 Mã QR thanh toán (VietQR)";
            p.displayText = trimmed;
            p.warning = "EdgeBar không tự mở app ngân hàng vì mỗi ngân hàng dùng liên kết riêng "
                + "— hãy mở app ngân hàng/ví của bạn và dùng chức năng quét QR trong app đó để đảm bảo an toàn.";
            p.actionLabel = null; p.primaryAction = null;
            return p;
        }

        // --- Còn lại: văn bản thường (mã lô hàng, số series...) ---
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
