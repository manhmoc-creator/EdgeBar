package com.manhmoc.edgebar;
import android.app.Activity; import android.content.Intent; import android.os.Bundle;
public class ScannerHelper extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { Intent i = new Intent("com.google.zxing.client.android.SCAN"); i.setPackage("com.google.android.gms"); startActivity(i); } 
        catch (Exception e) { finish(); }
        finish();
    }
}
