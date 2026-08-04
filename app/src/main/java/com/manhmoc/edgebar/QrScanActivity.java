      package com.manhmoc.edgebar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.view.*;
import android.widget.Toast;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

public class QrScanActivity extends Activity {
    private static final int REQ_CAMERA = 8801;
    private CameraDevice camera;
    private ImageReader reader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private volatile boolean done = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
            return; // chờ onRequestPermissionsResult
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
        SurfaceView sv = new SurfaceView(this);
        setContentView(sv);
        bgThread = new HandlerThread("qr-bg"); bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { openCamera(); }
            @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
            @Override public void surfaceDestroyed(SurfaceHolder h){}
        });
    }

    private void openCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            reader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onFrame, bgHandler);
            if (checkSelfPermission(android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) { finish(); return; }
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    camera = c;
                    try {
                        CaptureRequest.Builder req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        req.addTarget(reader.getSurface());
                        c.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override public void onConfigured(CameraCaptureSession s) {
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
        if (done) { Image img = r.acquireLatestImage(); if (img != null) img.close(); return; }
        Image img = r.acquireLatestImage();
        if (img == null) return;
        try {
            byte[] y = new byte[img.getPlanes()[0].getBuffer().remaining()];
            img.getPlanes()[0].getBuffer().get(y);
            LuminanceSource src = new PlanarYUVLuminanceSource(y, img.getWidth(), img.getHeight(),
                0,0, img.getWidth(), img.getHeight(), false);
            Result result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(src)));
            done = true;
            String text = result.getText();
            runOnUiThread(() -> {
                Toast.makeText(this, "QR: " + text, Toast.LENGTH_SHORT).show();
                Intent open = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(text));
                try { startActivity(open); } catch (Exception ignored) {}
                finish();
            });
        } catch (Exception ignored) {
        } finally { img.close(); }
    }

    @Override protected void onDestroy() {
        if (camera != null) camera.close();
        if (bgThread != null) bgThread.quitSafely();
        super.onDestroy();
    }
}
