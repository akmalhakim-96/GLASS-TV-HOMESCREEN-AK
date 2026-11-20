package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.util.Locale;

/**
 * What the azan puts over whatever is on screen: a dark see-through layer, the configured text in the
 * middle and a glass island at the top. The window takes no focus and no touches, so every key still
 * goes to the app in front (HomeKeyService borrows only the first OK). Android 9 cannot blur another
 * app's video, so the layer darkens instead.
 */
final class AzanOverlay {
    private static final String TAG = "TvHome.Azan";
    private static final float[] DIM = {0.45f, 0.62f, 0.80f};
    private static final int GOLD = 0xFFFFCF7A;

    private final WindowManager wm;
    private final FrameLayout root;
    private boolean shown;

    private AzanOverlay(WindowManager wm, FrameLayout root) {
        this.wm = wm;
        this.root = root;
    }

    /** Null when the TV hasn't allowed drawing over other apps; the azan then plays without it. */
    static AzanOverlay show(Context c, PrayerSettings s, Prayer prayer, String time, boolean reducedMotion) {
        if (!Settings.canDrawOverlays(c)) {
            Log.w(TAG, "no SYSTEM_ALERT_WINDOW: azan without the island");
            return null;
        }
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        float dp = c.getResources().getDisplayMetrics().density;
        Typeface font = c.getResources().getFont(R.font.outfit);
        FrameLayout root = new FrameLayout(c);

        View dim = new View(c);
        dim.setBackgroundColor(Math.round(DIM[s.dim] * 255) << 24);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout middle = new LinearLayout(c);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        middle.addView(text(c, font, (prayer.label() + " · " + time).toUpperCase(Locale.ROOT), 15, GOLD, 500, 0.25f));
        TextView main = text(c, font, s.textFor(prayer), 30, 0xFFFFFFFF, 600, 0f);
        main.setGravity(Gravity.CENTER);
        main.setMaxWidth(Math.round(760 * dp));
        main.setLineSpacing(4 * dp, 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Math.round(14 * dp);
        middle.addView(main, lp);
        String sub = s.subtextFor(prayer);
        if (!sub.isEmpty()) {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.topMargin = Math.round(16 * dp);
            middle.addView(text(c, font, sub, 14, 0xB3FFFFFF, 400, 0f), sp);
        }
        root.addView(middle, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        root.addView(island(c, font, dp, s, prayer, time), new FrameLayout.LayoutParams(-2, Math.round(74 * dp),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL) {{ topMargin = Math.round(22 * dp); }});

        WindowManager.LayoutParams wp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try {
            root.setAlpha(0f);
            wm.addView(root, wp);
            root.animate().alpha(1f).setDuration(reducedMotion ? 100 : 300).start();
        } catch (RuntimeException e) {
            Log.w(TAG, "overlay refused: " + e);
            return null;
        }
        AzanOverlay o = new AzanOverlay(wm, root);
        o.shown = true;
        return o;
    }

    private static View island(Context c, Typeface font, float dp, PrayerSettings s, Prayer prayer, String time) {
        LinearLayout island = new LinearLayout(c);
        island.setOrientation(LinearLayout.HORIZONTAL);
        island.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(22 * dp);
        island.setPadding(pad, 0, pad, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB80A0C14);
        bg.setCornerRadius(37 * dp);
        bg.setStroke(Math.max(1, Math.round(dp)), 0x61FFFFFF);
        island.setBackground(bg);

        TextView icon = text(c, font, "🕌", 22, 0xFFFFFFFF, 400, 0f);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{GOLD, 0xFFE89A3C});
        circle.setShape(GradientDrawable.OVAL);
        icon.setBackground(circle);
        island.addView(icon, new LinearLayout.LayoutParams(Math.round(44 * dp), Math.round(44 * dp)));

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(c, font, "Azan " + prayer.label() + " · " + time, 17, 0xFFFFFFFF, 600, 0f));
        words.addView(text(c, font, s.zoneName + " (" + s.zone + ")", 12, 0xBFFFFFFF, 400, 0f));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = Math.round(14 * dp);
        island.addView(words, wl);

        LinearLayout wave = new LinearLayout(c);
        wave.setGravity(Gravity.CENTER_VERTICAL);
        for (int h : new int[]{8, 16, 11, 18, 7}) {
            View bar = new View(c);
            GradientDrawable b = new GradientDrawable();
            b.setColor(GOLD);
            b.setCornerRadius(2 * dp);
            bar.setBackground(b);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Math.round(3 * dp), Math.round(h * dp));
            bl.rightMargin = Math.round(3 * dp);
            wave.addView(bar, bl);
        }
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.leftMargin = Math.round(18 * dp);
        vl.rightMargin = Math.round(28 * dp);
        island.addView(wave, vl);

        TextView ok = text(c, font, c.getString(R.string.azan_continue), 11.5f, 0xE6FFFFFF, 400, 0f);
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(12 * dp);
        chip.setStroke(Math.max(1, Math.round(dp)), 0x80FFFFFF);
        ok.setBackground(chip);
        ok.setPadding(Math.round(11 * dp), Math.round(5 * dp), Math.round(11 * dp), Math.round(5 * dp));
        island.addView(ok);
        return island;
    }

    private static TextView text(Context c, Typeface font, String s, float sp, int color, int weight, float spacing) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
        t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        return t;
    }

    void remove(boolean reducedMotion) {
        if (!shown) return;
        shown = false;
        root.animate().alpha(0f).setDuration(reducedMotion ? 100 : 250).withEndAction(() -> {
            try {
                wm.removeView(root);
            } catch (RuntimeException ignored) {
            }
        }).start();
    }
}
