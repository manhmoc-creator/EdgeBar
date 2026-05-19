package com.manhmoc.edgebar;
import android.app.Activity; import android.content.Intent; import android.os.Bundle; import android.widget.Toast;
public class ScannerHelper extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { Intent i = new Intent("com.google.zxing.client.android.SCAN"); i.setPackage("com.google.android.gms"); startActivity(i); } 
        catch (Exception e) { Toast.makeText(this, "Không tìm thấy trình quét mã vạch Google Play Services!", Toast.LENGTH_SHORT).show(); }
        finish();
    }
}
