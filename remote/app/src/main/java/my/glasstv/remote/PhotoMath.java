package my.glasstv.remote;

import java.util.Locale;

/** Sizes and names for the TV's wallpapers. */
final class PhotoMath {
    static final int W = 1920, H = 1080;
    static final int MAX_PHOTOS = 30;
    static final int MAX_JPEG = 300 * 1024;
    static final long MAX_MP3 = 5L << 20;

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

    /** 0 → "01.jpg": the launcher rotates through wallpapers in file-name order. */
    static String fileName(int index) {
        return String.format(Locale.ROOT, "%02d.jpg", index + 1);
    }
}
