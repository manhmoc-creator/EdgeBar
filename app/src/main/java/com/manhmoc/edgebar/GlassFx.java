cat << 'EOF' > app/src/main/java/com/manhmoc/edgebar/GlassFx.java
package com.manhmoc.edgebar;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

public final class GlassFx {
    private GlassFx() {}
    public static final String[] HEX = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F"};
    public static boolean on(SharedPreferences p) { return p.getBoolean("glass_on", false); }
    public static int tint(SharedPreferences p) {
        int i = Math.max(0, Math.min(HEX.length - 1, p.getInt("glass_color_idx", 0)));
        return Color.parseColor(HEX[i]) & 0xFFFFFF;
    }
    public static int rgb(SharedPreferences p) { return on(p) ? tint(p) : 0x607D8B; }

    private static final int[] SHEEN_C = {0xFFFFFFFF, 0x00FFFFFF, 0x00000000, 0xFF000000};
    private static final float[] SHEEN_P = {0f, 0.40f, 0.60f, 1f};

    /** Lớp bóng sáng-trên/tối-dưới cho vùng bất kỳ (Corner clip theo moonPath). Gradient chỉ dựng lại khi đổi kích thước. */
    public static final class Sheen {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient g; private float cw, ch;
        public void draw(Canvas c, float w, float h, float k) {
            if (k <= 0.004f || w < 2 || h < 2) return;
            if (g == null || cw != w || ch != h) {
                g = new LinearGradient(0, 0, 0, h, SHEEN_C, SHEEN_P, Shader.TileMode.CLAMP);
                cw = w; ch = h; p.setShader(g);
            }
            p.setAlpha((int) (120 * k));
            c.drawRect(0, 0, w, h, p);
        }
    }

    /** Nền dùng chung: glass tắt = bo góc đặc như cũ; glass bật = thân màu + bóng + viền phản quang.
     *  setColor() chỉ lấy ALPHA: kéo độ mờ về 0 thì thân/bóng/viền cùng về 0 (vẫn tàng hình). */
    public static final class Surface extends android.graphics.drawable.Drawable {
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), sheenP = new Paint(Paint.ANTI_ALIAS_FLAG),
            rimP = new Paint(Paint.ANTI_ALIAS_FLAG), strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF(), in = new RectF();
        private LinearGradient sheenG, edgeG;
        private int alpha = 255, flat, tint = 0x607D8B, strokeColor; private float radius, strokeW;
        private boolean glass, oval;
        public Surface() { this(0x607D8B); }
        public Surface(int flatRgb) {
            flat = flatRgb & 0xFFFFFF;
            rimP.setStyle(Paint.Style.STROKE); rimP.setStrokeWidth(4f);
            strokeP.setStyle(Paint.Style.STROKE);
        }
        public void refresh(SharedPreferences p) { glass = on(p); tint = tint(p); invalidateSelf(); }
        public void setGlassRgb(int rgb) { tint = rgb & 0xFFFFFF; }
        public void setColor(int argb) { alpha = Color.alpha(argb); invalidateSelf(); }
        public void setCornerRadius(float rad) { radius = rad; invalidateSelf(); }
        public void setOval() { oval = true; }
        public void setStroke(int w, int color) { strokeW = w; strokeColor = color; }
        @Override protected void onBoundsChange(Rect b) {
            if (b.width() < 2 || b.height() < 2) { sheenG = null; edgeG = null; return; }
            sheenG = new LinearGradient(b.left, b.top, b.left, b.bottom, SHEEN_C, SHEEN_P, Shader.TileMode.CLAMP);
            edgeG = new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0xCCFFFFFF}, new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas c) {
            int a = alpha;
            if (a <= 0) return;
            r.set(getBounds());
            float rad = oval ? Math.min(r.width(), r.height()) / 2f : radius;
            if (!glass) {
                body.setColor((a << 24) | flat);
                c.drawRoundRect(r, rad, rad, body);
                if (strokeW > 0) {
                    strokeP.setColor(strokeColor); strokeP.setStrokeWidth(strokeW);
                    in.set(r.left + strokeW / 2f, r.top + strokeW / 2f, r.right - strokeW / 2f, r.bottom - strokeW / 2f);
                    c.drawRoundRect(in, Math.max(0, rad - strokeW / 2f), Math.max(0, rad - strokeW / 2f), strokeP);
                }
                return;
            }
            body.setColor((Math.round(a * 0.78f) << 24) | tint);
            c.drawRoundRect(r, rad, rad, body);
            if (sheenG == null) return;
            float k = (float) Math.sqrt(a / 255f);   // sqrt để alpha thấp (Bar mặc định 50) vẫn thấy ánh glass, nhưng về 0 là mất hẳn
            sheenP.setShader(sheenG); sheenP.setAlpha((int) (120 * k));
            c.drawRoundRect(r, rad, rad, sheenP);
            float h = rimP.getStrokeWidth() / 2f;
            in.set(r.left + h, r.top + h, r.right - h, r.bottom - h);
            rimP.setShader(edgeG); rimP.setAlpha((int) (255 * k));
            c.drawRoundRect(in, Math.max(0, rad - h), Math.max(0, rad - h), rimP);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
