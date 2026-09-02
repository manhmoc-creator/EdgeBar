      package com.manhmoc.edgebar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Patterns;
import android.util.Size;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
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

    // [MỚI] Timeout tự tắt cam nếu quét quá lâu không ra kết quả — chống nóng máy
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private static final long SCAN_TIMEOUT_MS = 15000;
    private boolean cameraClosed = false;

    // [MỚI] Quét từ ảnh + Tạo QR
    private static final int REQ_PICK_IMAGE = 8802;
    private LinearLayout bottomBar;
    private View createQrOverlay;
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
        fd.setStroke(10, Color.parseColor("#8AB4F8")); // khung dày hơn (trước đây 4)
        fd.setCornerRadius(32f);
        frame.setBackground(fd);
        int fsize = (int) (getResources().getDisplayMetrics().widthPixels * 0.68f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(fsize, fsize);
        flp.gravity = Gravity.CENTER;
        root.addView(frame, flp);

        View btnClose = buildCloseXView();
        btnClose.setBackground(makeRounded("#66000000", 100f));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(110, 110);
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.setMargins(0, 60, 30, 0);
        btnClose.setOnClickListener(v -> finish());
        root.addView(btnClose, clp);

        addBottomToolsBar(); // [MỚI] Quét từ ảnh + Tạo QR

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

    // ===================== [MỚI] THANH CÔNG CỤ DƯỚI: TỪ ẢNH + TẠO QR =====================
    private void addBottomToolsBar() {
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(-1, -2);
        barLp.gravity = Gravity.BOTTOM;
        barLp.setMargins(30, 0, 30, 50);
        bottomBar.setLayoutParams(barLp);

        Button btnGallery = new Button(this);
        btnGallery.setText("🖼️ Từ ảnh");
        btnGallery.setBackground(makeRounded("#CC202124", 24f));
        btnGallery.setTextColor(Color.WHITE);
        btnGallery.setTextSize(13.5f);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, -2, 1f);
        glp.setMargins(0, 0, 10, 0);
        btnGallery.setLayoutParams(glp);
        btnGallery.setOnClickListener(v -> pickImageFromGallery());

        Button btnCreate = new Button(this);
        btnCreate.setText("✨ Tạo mã QR");
        btnCreate.setBackground(makeRounded("#CC00E5FF", 24f));
        btnCreate.setTextColor(Color.BLACK);
        btnCreate.setTextSize(13.5f);
        btnCreate.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        btnCreate.setOnClickListener(v -> showCreateQrOverlay());

        bottomBar.addView(btnGallery);
        bottomBar.addView(btnCreate);
        root.addView(bottomBar);
    }

    // ===================== [MỚI] QUÉT TỪ ẢNH TRONG THƯ VIỆN =====================
    private void pickImageFromGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        try { startActivityForResult(i, REQ_PICK_IMAGE); }
        catch (Exception e) { Toast.makeText(this, "Không tìm thấy ứng dụng chọn ảnh", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            decodeImageFromUri(data.getData());
        }
    }

    /** Decode ảnh trên background thread — tránh đứng UI với ảnh lớn, tự giới hạn
     *  1600px để đỡ tốn RAM khi quét (đủ độ nét để zxing đọc được QR thông thường). */
    private void decodeImageFromUri(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bmp;
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
                    bmp = ImageDecoder.decodeBitmap(src, (decoder, info, s) ->
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
                } else {
                    bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                }
                if (bmp == null) throw new Exception("null bitmap");
                int maxDim = 1600;
                if (bmp.getWidth() > maxDim || bmp.getHeight() > maxDim) {
                    float scale = Math.min(maxDim / (float) bmp.getWidth(), maxDim / (float) bmp.getHeight());
                    bmp = Bitmap.createScaledBitmap(bmp,
                        Math.round(bmp.getWidth()*scale), Math.round(bmp.getHeight()*scale), true);
                }
                int w = bmp.getWidth(), h = bmp.getHeight();
                int[] pixels = new int[w*h];
                bmp.getPixels(pixels, 0, w, 0, 0, w, h);
                LuminanceSource src2 = new RGBLuminanceSource(w, h, pixels);
                Result result = tryDecode(src2);
                runOnUiThread(() -> {
                    if (result != null) {
                        fireDetectFeedback();
                        stopCameraHardware();
                        playDetectAnimThenShow(result.getText(), result);
                    } else {
                        Toast.makeText(this, "Không tìm thấy mã QR trong ảnh này", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Không đọc được ảnh này", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ===================== [MỚI] RUNG + BEEP KHI PHÁT HIỆN QR =====================
    private void fireDetectFeedback() {
        try {
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                else vib.vibrate(40);
            }
        } catch (Exception ignored) {}
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            new Handler(Looper.getMainLooper()).postDelayed(tg::release, 200);
        } catch (Exception ignored) {}
    }

    // ===================== [MỚI] TẠO MÃ QR TỪ VĂN BẢN =====================
    private void showCreateQrOverlay() {
        if (createQrOverlay != null) return;
        stopCameraHardware(); // dừng camera khi đang ở màn tạo QR, đỡ hao pin
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRounded("#F0121212", 0f));
        card.setPadding(50, 100, 50, 40);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);

        TextView title = new TextView(this);
        title.setText("✨ Tạo mã QR");
        title.setTextColor(Color.parseColor("#8AB4F8"));
        title.setTextSize(17);
        title.setPadding(0, 0, 0, 20);
        card.addView(title);

        EditText etInput = new EditText(this);
        etInput.setHint("Nhập link, văn bản, số điện thoại...");
        etInput.setHintTextColor(Color.GRAY);
        etInput.setTextColor(Color.WHITE);
        etInput.setBackground(makeRounded("#2C2C2C", 20f));
        etInput.setPadding(30, 26, 30, 26);
        card.addView(etInput);

        ImageView ivQr = new ImageView(this);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        ivLp.setMargins(0, 30, 0, 30);
        ivQr.setLayoutParams(ivLp);
        ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(ivQr);

        Button btnGen = new Button(this);
        btnGen.setText("Tạo mã");
        btnGen.setBackground(makeRounded("#8AB4F8", 20f));
        btnGen.setTextColor(Color.BLACK);
        card.addView(btnGen);

        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        btnRow2.setPadding(0, 16, 0, 0);
        Button btnSave = new Button(this);
        btnSave.setText("💾 Lưu ảnh");
        btnSave.setBackground(makeRounded("#4CAF50", 18f));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setEnabled(false);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -2, 1f);
        sLp.setMargins(0, 0, 10, 0);
        btnSave.setLayoutParams(sLp);

        Button btnShare = new Button(this);
        btnShare.setText("🔗 Chia sẻ");
        btnShare.setBackground(makeRounded("#7C4DFF", 18f));
        btnShare.setTextColor(Color.WHITE);
        btnShare.setEnabled(false);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(0, -2, 1f);
        shLp.setMargins(10, 0, 0, 0);
        btnShare.setLayoutParams(shLp);

        btnRow2.addView(btnSave); btnRow2.addView(btnShare);
        card.addView(btnRow2);

        Button btnClose2 = new Button(this);
        btnClose2.setText("Đóng");
        btnClose2.setBackground(makeRounded("#333333", 18f));
        btnClose2.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(-1, -2);
        clp2.setMargins(0, 16, 0, 0);
        btnClose2.setLayoutParams(clp2);
        card.addView(btnClose2);

        final Bitmap[] genBmp = {null};

        btnGen.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) { Toast.makeText(this, "Nhập nội dung trước đã", Toast.LENGTH_SHORT).show(); return; }
            Bitmap qr = generateQrBitmap(text, 720);
            if (qr == null) { Toast.makeText(this, "Không tạo được mã QR", Toast.LENGTH_SHORT).show(); return; }
            genBmp[0] = qr;
            ivQr.setImageBitmap(qr);
            btnSave.setEnabled(true);
            btnShare.setEnabled(true);
        });

        btnSave.setOnClickListener(v -> {
            if (genBmp[0] == null) return;
            Uri saved = saveQrToGallery(genBmp[0]);
            Toast.makeText(this, saved != null ? "Đã lưu vào Photos" : "Lưu thất bại", Toast.LENGTH_SHORT).show();
        });

        btnShare.setOnClickListener(v -> {
            if (genBmp[0] == null) return;
            Uri saved = saveQrToGallery(genBmp[0]);
            if (saved == null) { Toast.makeText(this, "Lưu thất bại", Toast.LENGTH_SHORT).show(); return; }
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, saved);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(share, "Chia sẻ mã QR"));
        });

        btnClose2.setOnClickListener(v -> hideCreateQrOverlay());

        createQrOverlay = card;
        root.addView(card, lp);
    }

    private void hideCreateQrOverlay() {
        if (createQrOverlay == null) return;
        root.removeView(createQrOverlay);
        createQrOverlay = null;
        restartScanning();
    }

    private Bitmap generateQrBitmap(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) { return null; }
    }

    private Uri saveQrToGallery(Bitmap bmp) {
        try {
            String name = "EdgeBar_QR_" + System.currentTimeMillis() + ".png";
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29)
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EdgeBar");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            return uri;
        } catch (Exception e) { return null; }
    }
    /** [MỚI] Vẽ tay dấu X bằng 2 đường chéo — không phụ thuộc font,
     *  luôn hiển thị đúng hình X trên mọi máy/mọi bộ font hệ thống. */
    private View buildCloseXView() {
        return new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            { p.setColor(Color.WHITE); p.setStrokeWidth(6f); p.setStrokeCap(Paint.Cap.ROUND); }
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float pad = getWidth() * 0.30f;
                canvas.drawLine(pad, pad, getWidth() - pad, getHeight() - pad, p);
                canvas.drawLine(getWidth() - pad, pad, pad, getHeight() - pad, p);
            }
        };
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
                                    runOnUiThread(() -> scheduleScanTimeout()); // [MỚI]
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
                // [MỚI] Tắt hẳn camera ngay khi vừa quét được — chống nóng máy/hao pin,
                // preview sẽ đứng hình ở khung cuối cùng (đủ để làm nền cho bảng kết quả).
                stopCameraHardware();
                runOnUiThread(() -> {
                    fireDetectFeedback(); // [MỚI] rung + tiếng "tít" báo hiệu quét thành công
                    playDetectAnimThenShow(result.getText(), result);
                });
            }
        } catch (Exception ignored) {
        } finally { img.close(); }
    }

    /** [MỚI] Đóng session + camera + reader, hủy timeout — camera thực sự ngừng hoạt động. */
    private void stopCameraHardware() {
        if (cameraClosed) return;
        cameraClosed = true;
        cancelScanTimeout();
        try { if (session != null) { session.stopRepeating(); session.close(); } } catch (Exception ignored) {}
        session = null;
        try { if (camera != null) camera.close(); } catch (Exception ignored) {}
        camera = null;
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        reader = null;
    }

    private void scheduleScanTimeout() {
        cancelScanTimeout();
        timeoutRunnable = () -> {
            if (!paused) {
                stopCameraHardware();
                showTimeoutOverlay();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS);
    }

    private void cancelScanTimeout() {
        if (timeoutRunnable != null) timeoutHandler.removeCallbacks(timeoutRunnable);
        timeoutRunnable = null;
    }

    /** [MỚI] Hết giờ mà chưa quét được QR -> tắt cam, hiện bảng "Không tìm thấy mã QR". */
    private void showTimeoutOverlay() {
        if (resultCard != null) root.removeView(resultCard);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE); // [MỚI]
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(makeRounded("#F0121212", 28f));
        card.setPadding(50, 45, 50, 40);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(30, 0, 30, 60);

        TextView tv = new TextView(this);
        tv.setText("⏱️ Không tìm thấy mã QR");
        tv.setTextColor(Color.parseColor("#FFC107"));
        tv.setTextSize(15);
        tv.setPadding(0, 0, 0, 20);
        card.addView(tv);

        Button btnRetry = new Button(this);
        btnRetry.setText("Quét lại");
        btnRetry.setBackground(makeRounded("#8AB4F8", 20f));
        btnRetry.setTextColor(Color.BLACK);
        btnRetry.setOnClickListener(v -> {
            root.removeView(resultCard); resultCard = null;
            restartScanning();
        });
        card.addView(btnRetry);

        resultCard = card;
        root.addView(card, lp);
    }

    /** [MỚI] Mở lại camera + reset trạng thái quét, dùng chung cho nút "Quét lại" ở cả 2 nơi. */
    private void restartScanning() {
        paused = false;
        cameraClosed = false;
        if (bottomBar != null) bottomBar.setVisibility(View.VISIBLE); // [MỚI]
        GradientDrawable fd = new GradientDrawable();
        fd.setStroke(10, Color.parseColor("#8AB4F8"));
        fd.setCornerRadius(32f);
        frame.setBackground(fd);
        if (surfaceView.getHolder().getSurface() != null && surfaceView.getHolder().getSurface().isValid()) {
            openCamera(surfaceView.getHolder().getSurface());
        }
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
        if (bottomBar != null) bottomBar.setVisibility(View.GONE); // [MỚI]
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
        tvType.setTextColor(Color.parseColor("#8AB4F8"));
        tvType.setTextSize(13);
        card.addView(tvType);

        // [SỬA] Thay TextView đa dòng bị cắt "..." bằng thanh cuộn ngang 1 dòng —
        // xem trọn nội dung dài (VietQR, vCard, WiFi...) bằng vuốt trái/phải, kèm
        // nút Sao chép ngay cạnh bên. Zero-alloc thêm ngoài lúc mở kết quả (dùng
        // lại View đã có, không Thread/Timer nào chạy nền).
        LinearLayout contentRow = new LinearLayout(this);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams contentRowLp = new LinearLayout.LayoutParams(-1, -2);
        contentRowLp.setMargins(0, 10, 0, 20);
        contentRow.setLayoutParams(contentRowLp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackground(makeRounded("#1A1A1A", 18f));
        hsv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        hsv.setPadding(24, 16, 24, 16);
        hsv.setClipToPadding(false);

        TextView tvContent = new TextView(this);
        tvContent.setText(parsed.displayText.replace("\n", "   |   "));
        tvContent.setTextColor(Color.WHITE);
        tvContent.setTextSize(15);
        tvContent.setSingleLine(true);
        tvContent.setEllipsize(null); // không cắt "..." — cuộn để xem trọn
        hsv.addView(tvContent);
        contentRow.addView(hsv);

        Button btnCopyInline = new Button(this);
        btnCopyInline.setText("📋");
        btnCopyInline.setTextSize(16);
        btnCopyInline.setBackground(makeRounded("#333333", 18f));
        btnCopyInline.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(90, 90);
        copyLp.setMargins(10, 0, 0, 0);
        btnCopyInline.setLayoutParams(copyLp);
        btnCopyInline.setOnClickListener(v -> {
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText("QR", raw));
            Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show();
        });
        contentRow.addView(btnCopyInline);

        card.addView(contentRow);
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
        sLp.setMargins(0, 0, parsed.primaryAction != null ? 10 : 0, 0);
        btnScanAgain.setLayoutParams(sLp);
        btnScanAgain.setOnClickListener(v -> {
            root.removeView(resultCard); resultCard = null;
            restartScanning();
        });
        btnRow.addView(btnScanAgain);

        if (parsed.primaryAction != null) {
            Button btnAction = new Button(this);
            btnAction.setText(parsed.actionLabel);
            btnAction.setBackground(makeRounded("#8AB4F8", 20f));
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
            // [MỚI] Best-effort: tag 59 (Merchant Name) trong chuẩn EMV/VietQR đôi khi
            // chính là tên chủ tài khoản — không phải ngân hàng nào cũng điền, nên đây
            // chỉ mang tính tham khảo, KHÔNG thay thế bước xác nhận thật trong app ngân hàng.
            String holderName = extractEmvField(trimmed, "59");
            p.warning = !holderName.isEmpty()
                ? "Chủ tài khoản (tham khảo): " + holderName + " — luôn xác nhận lại trong app ngân hàng!"
                : "Chọn ngân hàng để thanh toán — nhớ xác nhận tên chủ tài khoản trước khi chuyển!";
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

    /** [MỚI] Parser EMV QR TLV đơn giản: mỗi field = 2 số ID + 2 số độ dài + nội dung.
     *  Chỉ đọc field ở cấp NGOÀI CÙNG — đủ dùng cho tag "59" (Merchant Name) của VietQR. */
    private String extractEmvField(String data, String tag) {
        try {
            int i = 0;
            while (i + 4 <= data.length()) {
                String id = data.substring(i, i + 2);
                int len = Integer.parseInt(data.substring(i + 2, i + 4));
                int valStart = i + 4, valEnd = valStart + len;
                if (valEnd > data.length()) break;
                String val = data.substring(valStart, valEnd);
                if (id.equals(tag)) return val.trim();
                i = valEnd;
            }
        } catch (Exception ignored) {}
        return "";
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
        cancelScanTimeout();
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        if (camera != null) camera.close();
        if (reader != null) reader.close();
        if (bgThread != null) bgThread.quitSafely();
        super.onDestroy();
    }
}
