package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Bismillah in champagne gold when the TV turns on, then Home. The calligraphy ships as a 12 KB white
 * mask (res/drawable-nodpi/bismillah_mask.webp); the gold and its shadow are painted here, once per
 * showing, and the bitmaps are dropped as soon as the splash is gone, so Home carries nothing of it.
 */
final class BismillahSplash {
    private static final long FADE_IN_MS = 500, HOLD_MS = 2500, FADE_OUT_MS = 500;
    private static final int[] GOLD = {0xFF8A6A32, 0xFFF6E3B0, 0xFFC99A4A, 0xFFFFF4D6, 0xFFB8893E, 0xFFF3D99A, 0xFF7D5E2A};

    /** On unless a theme says "bismillah": false. */
    static boolean enabled(JSONObject j) {
        Object v = j == null ? null : j.opt("bismillah");
        return !(v instanceof Boolean) || (Boolean) v;
    }

    private final FrameLayout root;
    private final FrameLayout view;
    private final boolean reducedMotion;
    private Bitmap gold;
    private boolean gone;

    private BismillahSplash(FrameLayout root, FrameLayout view, Bitmap gold, boolean reducedMotion) {
        this.root = root;
        this.view = view;
        this.gold = gold;
        this.reducedMotion = reducedMotion;
    }

    static BismillahSplash show(Activity a, FrameLayout root, boolean reducedMotion) {
        float dp = a.getResources().getDisplayMetrics().density;
        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        FrameLayout view = new FrameLayout(a);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1C1A17, 0xFF0B0A09, 0xFF050505});
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientRadius(screenW * 0.75f);
        bg.setGradientCenter(0.5f, 0.45f);
        view.setBackground(bg);
        // A warm light behind the words, the "glow" without blurring anything.
        View glow = new View(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x22E8BE6E, 0x00E8BE6E});
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(screenW * 0.38f);
        glow.setBackground(g);
        view.addView(glow, new FrameLayout.LayoutParams(-1, -1));

        Bitmap gold = goldCalligraphy(a);
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        if (gold != null) {
            int w = Math.min(gold.getWidth(), Math.round(screenW * 0.88f));
            int h = Math.round(w * (float) gold.getHeight() / gold.getWidth());
            column.addView(new Calligraphy(a, gold, 2 * dp), new LinearLayout.LayoutParams(w, h));
        }
        column.addView(ornament(a, dp), new LinearLayout.LayoutParams(Math.round(260 * dp), Math.round(10 * dp)) {{
            topMargin = Math.round(22 * dp);
            bottomMargin = Math.round(14 * dp);
        }});
        Typeface font = a.getResources().getFont(R.font.outfit);
        TextView rumi = text(a, font, "BISMILLAHIR RAHMANIR RAHIM", 21, 0xFFE3C78C);
        rumi.setLetterSpacing(0.33f);
        rumi.setTypeface(Typeface.create(font, Typeface.BOLD));
        column.addView(rumi);
        TextView meaning = text(a, font, "“Dengan nama Allah, Yang Maha Pemurah, lagi Maha Mengasihani.”", 16, 0xB8FFF4DC);
        meaning.getPaint().setTextSkewX(-0.18f);
        column.addView(meaning, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = Math.round(12 * dp); }});
        view.addView(column, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        view.setAlpha(0f);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        BismillahSplash s = new BismillahSplash(root, view, gold, reducedMotion);
        view.animate().alpha(1f).setDuration(reducedMotion ? 100 : FADE_IN_MS).start();
        view.postDelayed(s::dismiss, (reducedMotion ? 100 : FADE_IN_MS) + HOLD_MS);
        return s;
    }

    boolean showing() { return !gone; }

    void dismiss() {
        if (gone) return;
        gone = true;
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(reducedMotion ? 100 : FADE_OUT_MS).withEndAction(() -> {
            root.removeView(view);
            if (gold != null) gold.recycle();
            gold = null;
        }).start();
    }

    /** The white mask filled with the gold gradient; null if the resource cannot be decoded. */
    private static Bitmap goldCalligraphy(Activity a) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap mask = BitmapFactory.decodeResource(a.getResources(), R.drawable.bismillah_mask, o);
        if (mask == null) return null;
        Bitmap out = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint fill = new Paint();
        fill.setShader(new LinearGradient(0, 0, out.getWidth(), out.getHeight() * 0.4f, GOLD, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, out.getWidth(), out.getHeight(), fill);
        Paint cut = new Paint(Paint.FILTER_BITMAP_FLAG);
        cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask, 0, 0, cut);
        mask.recycle();
        return out;
    }

    private static View ornament(Activity a, float dp) {
        return new View(a) {
            private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint gem = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                float w = getWidth(), mid = getHeight() / 2f;
                line.setShader(new LinearGradient(0, 0, w, 0, new int[]{0x00D8B56E, 0xFFD8B56E, 0x00D8B56E}, null, Shader.TileMode.CLAMP));
                line.setStrokeWidth(Math.max(1f, dp));
                c.drawLine(0, mid, w, mid, line);
                gem.setColor(0xFFD8B56E);
                float r = 4 * dp;
                c.save();
                c.rotate(45, w / 2, mid);
                c.drawRect(w / 2 - r, mid - r, w / 2 + r, mid + r, gem);
                c.restore();
            }
        };
    }

    private static TextView text(Activity a, Typeface font, String s, float sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Draws the gold calligraphy over a soft dark shadow. */
    private static final class Calligraphy extends View {
        private final Bitmap gold;
        private final float offset;
        private final Paint shadow = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.RectF dst = new android.graphics.RectF();

        Calligraphy(Activity a, Bitmap gold, float offset) {
            super(a);
            this.gold = gold;
            this.offset = offset;
            shadow.setColorFilter(new PorterDuffColorFilter(0x99000000, PorterDuff.Mode.SRC_IN));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (gold.isRecycled()) return;
            dst.set(0, offset, getWidth(), getHeight() + offset);
            c.drawBitmap(gold, null, dst, shadow);
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(gold, null, dst, paint);
        }
    }
}
