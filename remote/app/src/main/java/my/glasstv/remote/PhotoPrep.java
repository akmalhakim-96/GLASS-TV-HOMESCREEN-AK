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

    static File prepare(Context c, Uri uri, boolean darken, int index) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("not a picture");
        BitmapFactory.Options o = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= PhotoMath.W && bounds.outHeight / (sample * 2) >= PhotoMath.H) sample *= 2;
        o.inSampleSize = sample;
        Bitmap src;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            src = BitmapFactory.decodeStream(in, null, o);
        }
        if (src == null) throw new IOException("not a picture");
        src = upright(c, uri, src);

        int[] r = PhotoMath.cropRect(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(PhotoMath.W, PhotoMath.H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(src, new Rect(r[0], r[1], r[0] + r[2], r[1] + r[3]), new Rect(0, 0, PhotoMath.W, PhotoMath.H),
                new Paint(Paint.FILTER_BITMAP_FLAG));
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
        File f = new File(c.getCacheDir(), PhotoMath.fileName(index));
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(jpeg);
        }
        return f;
    }

    private static Bitmap upright(Context c, Uri uri, Bitmap b) {
        int degrees = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
        } catch (Exception ignored) {
        }
        if (degrees == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r;
    }
}
