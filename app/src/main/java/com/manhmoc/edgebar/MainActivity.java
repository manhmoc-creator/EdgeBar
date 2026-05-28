package com.manhmoc.edgebar;
import android.app.Activity; import android.app.AlertDialog; import android.content.Intent; import android.content.SharedPreferences;
import android.graphics.Color; import android.graphics.drawable.GradientDrawable;
import android.os.Bundle; import android.provider.Settings; import android.net.Uri;
import android.view.Gravity; import android.view.View; import android.widget.*;
import org.json.JSONObject;
import java.util.Iterator;
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        // Giao diện tối giản, chỉ để kiểm tra build – bạn có thể thay bằng MainActivity đầy đủ từ bản trước
        TextView tv = new TextView(this);
        tv.setText("Edge Bar V19.12.3.5\nBuild thành công!");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(24);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(40,40,40,40);
        setContentView(tv);
        // (Nếu muốn giao diện đầy đủ, hãy copy MainActivity.java từ commit 19.12.3.4 vào đây)
    }
}
