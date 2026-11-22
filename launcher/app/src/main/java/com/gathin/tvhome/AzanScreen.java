package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.io.File;

/**
 * The azan screen as a full-screen window floating over whatever is playing: the owner's azan
 * wallpaper, the words and the glass island. The app behind (Netflix…) is never closed or sent to the
 * back, so OK simply lifts the window and the app is exactly where it was. The window takes the
 * remote's keys so OK does not pause or skip the film; only the volume keys pass through.
 */
final class AzanScreen {
    private static final String TAG = "TvHome.Azan";
    static final int PASS = 0, CONSUME = 1, DISMISS = 2;

    private final WindowManager wm;
    private final FrameLayout root;
    private final View back, dim, middle, island;
    private final boolean calm;
    private final float dp;
    private Bitmap wallpaper;
    private boolean shown;

    private AzanScreen(WindowManager wm, FrameLayout root, View back, FrameLayout content, Bitmap wallpaper,
                       boolean calm, float dp) {
        this.wm = wm;
        this.root = root;
        this.back = back;
        // AzanOverlay.content: the darkening layer, the words, the island, in that order.
        this.dim = content.getChildAt(0);
        this.middle = content.getChildAt(1);
        this.island = content.getChildAt(2);
        this.wallpaper = wallpaper;
        this.calm = calm;
        this.dp = dp;
    }

    /** OK released lifts the screen; volume keys work as usual; every other key stays here. */
    static int keyAction(int keyCode, int action) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) return PASS;
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        return ok && action == KeyEvent.ACTION_UP ? DISMISS : CONSUME;
    }

    /** Null when the TV has not allowed drawing over apps; the caller then uses AzanActivity. */
    static AzanScreen show(Context c, PrayerSettings s, Prayer prayer, String time, boolean reducedMotion, Runnable onOk) {
        if (!Settings.canDrawOverlays(c)) return null;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        FrameLayout root = new FrameLayout(c);
        // The picture on its own layer, so it can fade in over the film and settle.
        FrameLayout back = new FrameLayout(c);
        back.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1C1A17, 0xFF0B0A09, 0xFF050505}));
        Bitmap pic = decode(new File(Config.dir(c), AzanActivity.WALLPAPER));
        if (pic != null) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setImageBitmap(pic);
            back.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout content = AzanOverlay.content(c, s, prayer, time);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((v, keyCode, event) -> {
            int a = keyAction(keyCode, event.getAction());
            if (a == DISMISS) onOk.run();
            return a != PASS;
        });

        WindowManager.LayoutParams wp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);   // see-through while it fades in over the film
        AzanScreen screen = new AzanScreen(wm, root, back, content, pic, reducedMotion,
                c.getResources().getDisplayMetrics().density);
        try {
            screen.prepareEnter();
            wm.addView(root, wp);
            root.requestFocus();
            screen.enter();
        } catch (RuntimeException e) {
            Log.w(TAG, "azan window refused: " + e);
            if (pic != null) pic.recycle();
            return null;
        }
        screen.shown = true;
        return screen;
    }

    /** One picture at most screen size, decoded for this screen and dropped with it. */
    private static Bitmap decode(File f) {
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) s *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
    }

    // ---- entrance and exit ------------------------------------------------------------------------

    private static final android.view.animation.Interpolator EASE =
            new android.view.animation.DecelerateInterpolator(2f);

    private void prepareEnter() {
        back.setAlpha(0f);
        dim.setAlpha(0f);
        middle.setAlpha(0f);
        island.setAlpha(0f);
        if (calm) return;
        back.setScaleX(1.06f);
        back.setScaleY(1.06f);
        middle.setTranslationY(24 * dp);
        island.setTranslationY(-36 * dp);
    }

    /**
     * The picture fades in over the film and settles from slightly larger, the darkening follows, the
     * glass island glides down into place and the words rise in. Reduced motion: the same, fades only.
     */
    private void enter() {
        if (calm) {
            // Reduced motion: the same order, fades only (no scaling or sliding), still unhurried.
            back.animate().alpha(1f).setDuration(500).setInterpolator(EASE).start();
            dim.animate().alpha(1f).setStartDelay(100).setDuration(400).start();
            island.animate().alpha(1f).setStartDelay(250).setDuration(400).start();
            middle.animate().alpha(1f).setStartDelay(400).setDuration(450).start();
            return;
        }
        back.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(EASE).start();
        dim.animate().alpha(1f).setStartDelay(150).setDuration(700).setInterpolator(EASE).start();
        island.animate().alpha(1f).translationY(0f).setStartDelay(350).setDuration(600).setInterpolator(EASE).start();
        middle.animate().alpha(1f).translationY(0f).setStartDelay(550).setDuration(700).setInterpolator(EASE).start();
    }

    void dismiss() {
        if (!shown) return;
        shown = false;
        for (View v : new View[]{back, dim, middle, island}) v.animate().cancel();
        long words = calm ? 200 : 250, picture = calm ? 350 : 450;
        middle.animate().alpha(0f).setStartDelay(0).setDuration(words).start();
        island.animate().alpha(0f).translationY(calm ? 0f : -24 * dp).setStartDelay(0).setDuration(words).start();
        dim.animate().alpha(0f).setStartDelay(words / 2).setDuration(picture).start();
        back.animate().alpha(0f).setStartDelay(words / 2).setDuration(picture).withEndAction(() -> {
            try {
                wm.removeView(root);
            } catch (RuntimeException ignored) {
            }
            if (wallpaper != null) wallpaper.recycle();
            wallpaper = null;
        }).start();
    }
}
