package com.manhmoc.edgebar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.Random;

public class ScratchView extends View {
    private Paint paint = new Paint();
    private Random random = new Random();
    private long lastUpdate = 0;
    private static final int UPDATE_INTERVAL_MS = 150; // 6-7 fps, tiết kiệm CPU

    public ScratchView(Context context) {
        super(context);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        if (now - lastUpdate >= UPDATE_INTERVAL_MS) {
            lastUpdate = now;
            paint.setAlpha(60 + random.nextInt(80));
            for (int i = 0; i < 8; i++) {
                int x1 = random.nextInt(getWidth());
                int y1 = random.nextInt(getHeight());
                int x2 = x1 + random.nextInt(200) - 100;
                int y2 = y1 + random.nextInt(200) - 100;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
            paint.setStrokeWidth(2);
            paint.setAlpha(80);
            for (int i = 0; i < 12; i++) {
                int x1 = random.nextInt(getWidth());
                int y1 = random.nextInt(getHeight());
                int x2 = x1 + random.nextInt(120) - 60;
                int y2 = y1 + random.nextInt(120) - 60;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }
        postInvalidateDelayed(UPDATE_INTERVAL_MS);
    }
}
