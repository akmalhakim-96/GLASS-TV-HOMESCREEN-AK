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
    private Bitmap wallpaper;
    private boolean shown;

    private AzanScreen(WindowManager wm, FrameLayout root, Bitmap wallpaper) {
        this.wm = wm;
        this.root = root;
        this.wallpaper = wallpaper;
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
    static AzanScreen show(Context c, PrayerSettings s, Prayer prayer, String time, Runnable onOk) {
        if (!Settings.canDrawOverlays(c)) return null;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        FrameLayout root = new FrameLayout(c);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1C1A17, 0xFF0B0A09, 0xFF050505}));
        Bitmap pic = decode(new File(Config.dir(c), AzanActivity.WALLPAPER));
        if (pic != null) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setImageBitmap(pic);
            root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(AzanOverlay.content(c, s, prayer, time), new FrameLayout.LayoutParams(-1, -1));
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
                PixelFormat.OPAQUE);
        try {
            root.setAlpha(0f);
            wm.addView(root, wp);
            root.requestFocus();
            root.animate().alpha(1f).setDuration(300).start();
        } catch (RuntimeException e) {
            Log.w(TAG, "azan window refused: " + e);
            if (pic != null) pic.recycle();
            return null;
        }
        AzanScreen screen = new AzanScreen(wm, root, pic);
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

    void dismiss() {
        if (!shown) return;
        shown = false;
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            try {
                wm.removeView(root);
            } catch (RuntimeException ignored) {
            }
            if (wallpaper != null) wallpaper.recycle();
            wallpaper = null;
        }).start();
    }
}
