package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

public class ScreenshotPermissionActivity extends Activity {
    public static final int REQ_CODE = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE) {
            Intent i = new Intent("com.manhmoc.edgebar.ACTION_SCREENSHOT_GRANTED");
            i.putExtra("resultCode", resultCode);
            i.putExtra("data", data);
            sendBroadcast(i);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
