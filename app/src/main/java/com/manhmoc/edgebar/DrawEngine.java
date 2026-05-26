package com.manhmoc.edgebar;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class DrawEngine {

    public static void drawEdgeBar(Canvas canvas, Paint paint, int width, int height, float radius, boolean isPixelShiftEnabled) {
        float shiftX = isPixelShiftEnabled ? (float) (Math.random() * 2 - 1) : 0f;
        float shiftY = isPixelShiftEnabled ? (float) (Math.random() * 2 - 1) : 0f;
        RectF barRect = new RectF(shiftX, shiftY, width + shiftX, height + shiftY);
        canvas.drawRoundRect(barRect, radius, radius, paint);
    }

    public static void drawFrameCorner(Canvas canvas, Paint paint, int width, int height, float radius, String position) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND); // Tròn vành 2 đầu nét vẽ
        paint.setStrokeJoin(Paint.Join.ROUND);
        
        Path path = new Path();
        float r = Math.min(radius, Math.min(width, height)); // Chống tràn viền
        
        // Thuật toán ArcTo tạo đường cong toán học chuẩn xác 100%
        switch (position) {
            case "br": 
                path.moveTo(0, height); path.lineTo(width - r, height);
                path.arcTo(new RectF(width - 2*r, height - 2*r, width, height), 90, -90);
                path.lineTo(width, 0); break;
            case "bl": 
                path.moveTo(width, height); path.lineTo(r, height);
                path.arcTo(new RectF(0, height - 2*r, 2*r, height), 90, 90);
                path.lineTo(0, 0); break;
            case "tr": 
                path.moveTo(0, 0); path.lineTo(width - r, 0);
                path.arcTo(new RectF(width - 2*r, 0, width, 2*r), 270, 90);
                path.lineTo(width, height); break;
            case "tl": 
                path.moveTo(width, 0); path.lineTo(r, 0);
                path.arcTo(new RectF(0, 0, 2*r, 2*r), 270, -90);
                path.lineTo(0, height); break;
        }
        canvas.drawPath(path, paint);
    }

    public static void drawCrescentMoon(Canvas canvas, Paint paint, float moonW, float moonH, float thicknessOffset) {
        Path outerOval = new Path(); outerOval.addOval(new RectF(0, 0, moonW, moonH), Path.Direction.CW);
        Path innerOval = new Path(); innerOval.addOval(new RectF(thicknessOffset, thicknessOffset, moonW + thicknessOffset, moonH + thicknessOffset), Path.Direction.CW);
        Path crescentMoon = new Path(); crescentMoon.op(outerOval, innerOval, Path.Op.DIFFERENCE);
        paint.setStyle(Paint.Style.FILL); canvas.drawPath(crescentMoon, paint);
    }
}
