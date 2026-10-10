// THAY TOÀN BỘ file bằng:
package com.manhmoc.edgebar;
import android.app.Service; import android.content.Intent; import android.os.IBinder;
public class SidePanelService extends Service {
    public static boolean isRunning = false;
    @Override public int onStartCommand(Intent i, int f, int id) { stopSelf(); return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
}
