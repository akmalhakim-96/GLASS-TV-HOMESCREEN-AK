package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.TextView;

/** Text with a dark outline under the fill, readable over any picture (the azan screen's words). */
final class OutlineTextView extends TextView {
    private final float strokeWidth;
    private final int strokeColor;
    private boolean drawingStroke;

    OutlineTextView(Context c, float strokeWidth, int strokeColor) {
        super(c);
        this.strokeWidth = strokeWidth;
        this.strokeColor = strokeColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int fill = getCurrentTextColor();
        Paint p = getPaint();
        // The outline first, then the letters on top of it.
        drawingStroke = true;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(strokeWidth);
        setTextColor(strokeColor);
        super.onDraw(canvas);
        p.setStyle(Paint.Style.FILL);
        setTextColor(fill);
        drawingStroke = false;
        super.onDraw(canvas);
    }

    @Override
    public void invalidate() {
        if (!drawingStroke) super.invalidate();   // setTextColor while drawing must not loop
    }
}
