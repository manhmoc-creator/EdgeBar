package com.manhmoc.edgebar;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class DrawEngine {

    // Thuật toán vẽ Thanh Bar (Hỗ trợ Pixel Shifting chống burn-in)
    public static void drawEdgeBar(Canvas canvas, Paint paint, int width, int height, float radius, boolean isPixelShiftEnabled) {
        float shiftX = 0f;
        float shiftY = 0f;
        
        if (isPixelShiftEnabled) {
            shiftX = (float) (Math.random() * 2 - 1);
            shiftY = (float) (Math.random() * 2 - 1);
        }

        RectF barRect = new RectF(
            shiftX, 
            shiftY, 
            width + shiftX, 
            height + shiftY
        );

        canvas.drawRoundRect(barRect, radius, radius, paint);
    }

    // Thuật toán vẽ Góc Khung Bezier
    public static void drawFrameCorner(Canvas canvas, Paint paint, int width, int height, float cornerRadius, String position) {
        Path cornerPath = new Path();
        
        switch (position) {
            case "br": // Đáy Phải
                cornerPath.moveTo(0, height);
                cornerPath.lineTo(width - cornerRadius, height);
                cornerPath.quadTo(width, height, width, height - cornerRadius);
                cornerPath.lineTo(width, 0);
                break;
            case "tl": // Đỉnh Trái
                cornerPath.moveTo(width, 0);
                cornerPath.lineTo(cornerRadius, 0);
                cornerPath.quadTo(0, 0, 0, cornerRadius);
                cornerPath.lineTo(0, height);
                break;
            case "tr": // Đỉnh Phải
                cornerPath.moveTo(0, 0);
                cornerPath.lineTo(width - cornerRadius, 0);
                cornerPath.quadTo(width, 0, width, cornerRadius);
                cornerPath.lineTo(width, height);
                break;
            case "bl": // Đáy Trái
                cornerPath.moveTo(width, height);
                cornerPath.lineTo(cornerRadius, height);
                cornerPath.quadTo(0, height, 0, height - cornerRadius);
                cornerPath.lineTo(0, 0);
                break;
        }
        
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(cornerPath, paint);
    }

    // Thuật toán Trăng Khuyết (Op.DIFFERENCE)
    public static void drawCrescentMoon(Canvas canvas, Paint paint, float moonW, float moonH, float thicknessOffset) {
        Path outerOval = new Path();
        outerOval.addOval(new RectF(0, 0, moonW, moonH), Path.Direction.CW);

        Path innerOval = new Path();
        innerOval.addOval(new RectF(
            thicknessOffset, 
            thicknessOffset, 
            moonW + thicknessOffset, 
            moonH + thicknessOffset
        ), Path.Direction.CW);

        Path crescentMoon = new Path();
        crescentMoon.op(outerOval, innerOval, Path.Op.DIFFERENCE);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(crescentMoon, paint);
    }
}
