package com.manhmoc.edgebar;
import android.app.Activity; import android.os.Bundle; import android.graphics.Color; import android.widget.*;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this); tv.setText("Edge Bar V19.12.3.0 - Đã hồi sinh"); tv.setTextColor(Color.WHITE);
        setContentView(tv);
    }
}
