// ... (GIỮ NGUYÊN PHẦN TRƯỚC, CHỈ THAY ĐỔI HÀM onDraw CỦA CORNERVIEW) ...
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); 
            float w = getWidth(), h = getHeight(), thick = pStroke.getStrokeWidth(); float pad = thick/2;
            float radSlider = (type < 2) ? prefs.getInt("lock_corner_bot_rad", 80) : prefs.getInt("lock_corner_top_rad", 80);
            float mw = prefs.getInt("lock_corner_"+CORNERS[type]+"_moon_w", 100); float mh = prefs.getInt("lock_corner_"+CORNERS[type]+"_moon_h", 100);
            
            // LỚP LÕI (MOON): Vẽ độc lập không bị cắt bởi viền
            Path moonPath = new Path();
            if(type==0) { moonPath.moveTo(w-pad, pad); moonPath.lineTo(w-mw, pad); moonPath.lineTo(w-pad, mh); moonPath.close(); }
            else if(type==1) { moonPath.moveTo(pad, pad); moonPath.lineTo(mw, pad); moonPath.lineTo(pad, mh); moonPath.close(); }
            else if(type==2) { moonPath.moveTo(w-pad, h-pad); moonPath.lineTo(w-mw, h-pad); moonPath.lineTo(w-pad, h-mh); moonPath.close(); }
            else if(type==3) { moonPath.moveTo(pad, h-pad); moonPath.lineTo(mw, h-pad); moonPath.lineTo(pad, h-mh); moonPath.close(); }
            canvas.drawPath(moonPath, pFill);

            // LỚP VỎ (STROKE): Vẽ độc lập
            Path strokePath = new Path();
            // ... (Giữ nguyên logic strokePath cũ từ 19.12) ...
            canvas.drawPath(strokePath, pStroke); 
        }
// ...
