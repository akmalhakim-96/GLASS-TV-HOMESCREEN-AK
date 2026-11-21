package my.glasstv.remote;

import java.util.Locale;

/** Sizes and names for the TV's wallpapers. */
final class PhotoMath {
    static final int W = 1920, H = 1080;
    static final int MAX_PHOTOS = 30;
    static final int MAX_JPEG = 300 * 1024;
    static final long MAX_MP3 = 5L << 20;
    /** Most pixels decoded at once: ~96 MB as ARGB_8888, safe on ordinary phones. */
    static final long MAX_DECODE_PIXELS = 24_000_000L;

    private PhotoMath() {}

    /** The centred 16:9 part of a w×h picture: {left, top, width, height}. */
    static int[] cropRect(int w, int h) {
        if ((long) w * 9 > (long) h * 16) {
            int cw = h * 16 / 9;
            return new int[]{(w - cw) / 2, 0, cw, h};
        }
        int ch = w * 9 / 16;
        return new int[]{0, (h - ch) / 2, w, ch};
    }

    /**
     * BitmapFactory's inSampleSize for a w×h file shown upright after rotating by degrees: the largest
     * power of two that keeps the 16:9 crop at least 1920×1080, raised further only to stay under
     * MAX_DECODE_PIXELS (a very wide panorama).
     */
    static int sampleSize(int w, int h, int degrees) {
        int ow = degrees == 90 || degrees == 270 ? h : w;
        int oh = degrees == 90 || degrees == 270 ? w : h;
        int[] crop = cropRect(ow, oh);
        int s = 1;
        while (crop[2] / (s * 2) >= W && crop[3] / (s * 2) >= H) s *= 2;
        while ((long) (w / s) * (h / s) > MAX_DECODE_PIXELS) s *= 2;
        return s;
    }

    /** 0 → "01.jpg": the launcher rotates through wallpapers in file-name order. */
    static String fileName(int index) {
        return String.format(Locale.ROOT, "%02d.jpg", index + 1);
    }
}
