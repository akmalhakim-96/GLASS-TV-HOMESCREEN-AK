package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * The azan screen: the owner's azan wallpaper (azan_wallpaper.jpg, sent from the phone) or a dark
 * gradient, the words and the glass island. It stays, even after the azan has finished, until OK is
 * pressed; Home and Back wait (HomeKeyService and onBackPressed). OK stops the azan and returns to
 * whatever was on screen before.
 */
public class AzanActivity extends Activity {
    static final String WALLPAPER = "azan_wallpaper.jpg";
    private Bitmap wallpaper;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Config cfg = Config.load(this);
        Intent i = getIntent();
        Prayer prayer = Prayer.fromKey(String.valueOf(i.getStringExtra(AzanScheduler.EXTRA_PRAYER)));
        if (prayer == null) prayer = Prayer.MAGHRIB;
        long at = i.getLongExtra(AzanScheduler.EXTRA_AT, System.currentTimeMillis());
        String time = PrayerBar.clock(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), PrayerTimes.MY).toLocalTime(),
                DateFormat.is24HourFormat(this));

        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1C1A17, 0xFF0B0A09, 0xFF050505});
        root.setBackground(bg);
        wallpaper = decode(new File(Config.dir(this), WALLPAPER));
        if (wallpaper != null) {
            ImageView pic = new ImageView(this);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.setImageBitmap(wallpaper);
            root.addView(pic, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(AzanOverlay.content(this, cfg.prayer, prayer, time), new FrameLayout.LayoutParams(-1, -1));
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(root);
        AzanState.setScreen(true);
        AzanState.setStopper(() -> runOnUiThread(this::finish));
    }

    /** One picture at most screen size, decoded once for this screen and dropped with it. */
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

    /** A new prayer while the last screen is still up: show the new one. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        recreate();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // OK returns to what was on screen; HomeKeyService usually takes it first and does the same.
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Back waits for OK, like Home.
    }

    @Override
    protected void onDestroy() {
        if (isChangingConfigurations()) {   // recreate() for a new prayer: that azan carries on
            super.onDestroy();
            return;
        }
        AzanState.setScreen(false);
        AzanState.setStopper(null);
        AzanState.end();
        startService(new Intent(this, AzanService.class).setAction(AzanService.ACTION_STOP));
        if (wallpaper != null) wallpaper.recycle();
        wallpaper = null;
        super.onDestroy();
    }
}
