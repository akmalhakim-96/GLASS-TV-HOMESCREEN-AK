package my.glasstv.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns a gallery photo into a TV wallpaper: upright, centre-cropped to 16:9, 1920×1080, a little
 * darker so the clock and apps stay readable, and a JPEG of at most 300 KB so a 1 GB stick stays quick.
 */
final class PhotoPrep {
    private PhotoPrep() {}

    /**
     * @param fit  true: the whole picture, nothing cut, over a blurred and darkened copy of itself;
     *             false: centre-cropped to fill the screen
     * @param name file name in the cache, e.g. "01.jpg"
     */
    static File prepare(Context c, Uri uri, boolean darken, boolean fit, String name) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("not a picture");
        BitmapFactory.Options o = new BitmapFactory.Options();
        // Sampled on the upright crop, so sideways-stored portraits keep their detail, and capped so a
        // panorama cannot run the phone out of memory.
        o.inSampleSize = PhotoMath.sampleSize(bounds.outWidth, bounds.outHeight, degrees(c, uri));
        Bitmap src;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            src = BitmapFactory.decodeStream(in, null, o);
        }
        if (src == null) throw new IOException("not a picture");
        src = upright(c, uri, src);

        int[] r = PhotoMath.cropRect(src.getWidth(), src.getHeight());
        Rect crop = new Rect(r[0], r[1], r[0] + r[2], r[1] + r[3]);
        Rect screen = new Rect(0, 0, PhotoMath.W, PhotoMath.H);
        Bitmap out = Bitmap.createBitmap(PhotoMath.W, PhotoMath.H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (fit) {
            // Behind: the same picture, cropped to fill, shrunk to a few dozen pixels and stretched back
            // up, which blurs it; darkened so the whole picture in front stands out.
            Bitmap tiny = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888);
            new Canvas(tiny).drawBitmap(src, crop, new Rect(0, 0, 48, 27), smooth);
            canvas.drawBitmap(tiny, null, screen, smooth);
            tiny.recycle();
            canvas.drawColor(0x80000000);
            int[] f = PhotoMath.fitRect(src.getWidth(), src.getHeight());
            canvas.drawBitmap(src, null, new Rect(f[0], f[1], f[0] + f[2], f[1] + f[3]), smooth);
        } else {
            canvas.drawBitmap(src, crop, screen, smooth);
        }
        src.recycle();
        if (darken) canvas.drawColor(0x40000000);

        byte[] jpeg = null;
        for (int q = 90; q >= 40; q -= 5) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG, q, b);
            jpeg = b.toByteArray();
            if (jpeg.length <= PhotoMath.MAX_JPEG) break;
        }
        out.recycle();
        File f = new File(c.getCacheDir(), name);
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(jpeg);
        }
        return f;
    }

    private static int degrees(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static Bitmap upright(Context c, Uri uri, Bitmap b) {
        int degrees = degrees(c, uri);
        if (degrees == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r;
    }
}
