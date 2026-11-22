package com.gathin.tvhome;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A shared inbox for TVs that let nobody else write into Glass TV's own folder (Android 11+): the
 * phone app leaves files in /sdcard/GlassTV/, and Glass TV moves them into its folder when it starts
 * or is asked to reload. It also leaves a copy of its current config.json in /sdcard/GlassTV/.current/
 * so the phone can read the settings it changes only part of. Reading the inbox needs all-files access,
 * granted from the phone (appops MANAGE_EXTERNAL_STORAGE) at install.
 */
final class Inbox {
    private static final String TAG = "TvHome.Inbox";
    static final String NAME = "GlassTV";
    private static final Set<String> FILES = new HashSet<>(Arrays.asList(
            "config.json", "azan.mp3", "azan_subuh.mp3", "azan_wallpaper.jpg", "wallpaper.jpg"));
    /** A marker the phone leaves to say "back to places": the owner's pictures go. */
    static final String CLEAR_WALLPAPERS = "clear-wallpapers";

    private Inbox() {}

    static boolean allowed(String name) {
        return FILES.contains(name);
    }

    static boolean wallpaperAllowed(String name) {
        if (name.contains("/") || name.contains("..")) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png");
    }

    static File dir() {
        return new File(Environment.getExternalStorageDirectory(), NAME);
    }

    /** Moves whatever the phone left into Glass TV's folder, then writes the current config for it. */
    static void take(Context c) {
        File in = dir(), own = Config.dir(c);
        if (own == null) return;
        try {
            File[] files = in.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && allowed(f.getName())) {
                        copy(f, new File(own, f.getName()));
                        f.delete();
                    }
                }
                File marker = new File(in, CLEAR_WALLPAPERS);
                if (marker.exists()) {
                    deleteTree(new File(own, Config.WALLPAPERS_DIR));
                    marker.delete();
                }
                File pics = new File(in, Config.WALLPAPERS_DIR);
                File[] list = pics.listFiles();
                if (list != null) {
                    File target = new File(own, Config.WALLPAPERS_DIR);
                    deleteTree(target);
                    target.mkdirs();
                    for (File p : list) if (p.isFile() && wallpaperAllowed(p.getName())) copy(p, new File(target, p.getName()));
                    deleteTree(pics);
                }
            }
            File cfg = new File(own, Config.FILE_NAME);
            File current = new File(in, ".current");
            if (cfg.isFile() && (current.isDirectory() || current.mkdirs())) copy(cfg, new File(current, Config.FILE_NAME));
        } catch (Exception e) {
            Log.w(TAG, "inbox: " + e);   // no access (older install) or no inbox: nothing to take
        }
    }

    private static void copy(File from, File to) throws Exception {
        File tmp = new File(to.getParentFile(), to.getName() + ".part");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
        if (!tmp.renameTo(to)) throw new IllegalStateException("rename " + to);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
