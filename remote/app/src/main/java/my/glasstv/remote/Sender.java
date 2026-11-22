package my.glasstv.remote;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** HANTAR KE TV: photos, azan recordings and settings to the TV, then Home restarts with them. */
final class Sender {
    interface Progress {
        void step(String malayText);
    }

    private Sender() {}

    /** Files made once on the phone and then sent to every chosen TV. */
    static final class Prepared {
        final List<File> photos = new ArrayList<>();
        int picked, skipped;
        File azanWallpaper, azan, azanSubuh;
        /** Set when there is nothing sensible to send (all photos unreadable, an MP3 too big). */
        String error;

        void cleanup() {
            for (File f : photos) f.delete();
            for (File f : new File[]{azanWallpaper, azan, azanSubuh}) if (f != null) f.delete();
        }
    }

    /** Prepares photos, the azan picture and MP3s once, whatever the number of TVs. Background thread. */
    static Prepared prepare(Context c, Settings s, List<Uri> photos, boolean darken, boolean fit,
                            Uri azanWallpaper, boolean azanFit, Uri azan, Uri azanSubuh, Progress p) {
        Prepared pr = new Prepared();
        File dir = new File(c.getCacheDir(), "send");
        dir.mkdirs();
        try {
            if (Boolean.FALSE.equals(s.places) && !photos.isEmpty()) {
                pr.picked = photos.size();
                for (int i = 0; i < photos.size() && pr.photos.size() < PhotoMath.MAX_PHOTOS; i++) {
                    p.step("Menyediakan gambar " + (i + 1) + " daripada " + photos.size() + "…");
                    try {
                        File f = PhotoPrep.prepare(c, photos.get(i), darken, fit, PhotoMath.fileName(pr.photos.size()));
                        File kept = new File(dir, f.getName());
                        if (!f.renameTo(kept)) kept = f;
                        pr.photos.add(kept);
                    } catch (Exception | OutOfMemoryError e) {
                        pr.skipped++;   // deleted from the gallery since it was picked, not a picture, or too big
                    }
                }
                pr.error = SendGate.afterPhotos(pr.picked, pr.photos.size());
            }
            if (azanWallpaper != null) {
                p.step("Menyediakan gambar latar azan…");
                File w = PhotoPrep.prepare(c, azanWallpaper, false, azanFit, "azan_wallpaper.jpg");
                pr.azanWallpaper = new File(dir, w.getName());
                if (!w.renameTo(pr.azanWallpaper)) pr.azanWallpaper = w;
            }
            if (azan != null) pr.azan = copyAudio(c, azan, new File(dir, "azan.mp3"));
            if (azanSubuh != null) pr.azanSubuh = copyAudio(c, azanSubuh, new File(dir, "azan_subuh.mp3"));
        } catch (Exception e) {
            pr.error = Messages.forError(e);
        }
        return pr;
    }

    /** Sends the prepared files and this section's settings to one TV; the line for that TV. */
    static String sendTo(Context c, String host, Settings s, Prepared pr, Progress p) {
        if (pr.error != null) return pr.error;
        try (Tv tv = Tv.connect(c, host)) {
            p.step("Bersambung…");
            tv.shell(TvCommands.makeDir());
            boolean inbox = tv.usesInbox();
            if (inbox) tv.shell(TvCommands.GRANT_FILES);
            String existing = tv.readConfig();

            if (!pr.photos.isEmpty()) {
                tv.shell(inbox ? "rm -rf " + TvCommands.INBOX + "/wallpapers" : TvCommands.prepareWallpapers());
                int i = 0;
                for (File f : pr.photos) {
                    p.step("Menghantar gambar " + (++i) + " daripada " + pr.photos.size() + "…");
                    tv.push(f, TvCommands.DIR + "/wallpapers.new/" + f.getName());
                }
                if (!inbox) tv.shell(TvCommands.swapWallpapers());   // in the inbox, Glass TV swaps them itself
            } else if (Boolean.TRUE.equals(s.places)) {
                tv.shell(inbox ? "mkdir -p " + TvCommands.INBOX + "; touch " + TvCommands.INBOX + "/clear-wallpapers"
                        : TvCommands.clearWallpapers());
            }
            if (pr.azanWallpaper != null) {
                p.step("Menghantar gambar latar azan…");
                tv.push(pr.azanWallpaper, TvCommands.azanWallpaperPath());
            }
            if (pr.azan != null) {
                p.step("Menghantar azan.mp3…");
                tv.push(pr.azan, TvCommands.DIR + "/azan.mp3");
            }
            if (pr.azanSubuh != null) {
                p.step("Menghantar azan_subuh.mp3…");
                tv.push(pr.azanSubuh, TvCommands.DIR + "/azan_subuh.mp3");
            }

            p.step("Menyimpan tetapan…");
            File cfg = new File(c.getCacheDir(), "config.json");
            try (FileOutputStream out = new FileOutputStream(cfg)) {
                out.write(ConfigMerge.merge(existing, s.changes()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            tv.push(cfg, TvCommands.DIR + "/config.json");
            cfg.delete();

            tv.shell(HomeSetup.GRANT_LOCATION);   // the Wi-Fi name on Home (installs from older versions lack it)
            // "Tiada screensaver" also turns Android's own screensaver off (and back on when switched off).
            if (s.keepScreenOn != null) tv.shell(TvCommands.screensaver(!s.keepScreenOn));
            p.step("Memulakan semula Home…");
            tv.shell(TvCommands.restartHome());
            // Glass TV's remote-key service (Home on Xiaomi remotes, OK stops the azan): on again if an
            // older version of this app switched it off with a force-stop.
            String services = tv.shell("settings get secure enabled_accessibility_services");
            String wanted = HomeSetup.withHomeKeyService(services);
            if (!wanted.equals(services.trim())) {
                tv.shell("settings put secure enabled_accessibility_services " + wanted);
                tv.shell("settings put secure accessibility_enabled 1");
            }
            String done = "Siap ✓";
            if (!pr.photos.isEmpty()) done += " " + pr.photos.size() + " gambar";
            if (pr.skipped > 0) done += " (" + pr.skipped + " gambar tidak dapat dibaca)";
            return done;
        } catch (Exception e) {
            return Messages.forError(e);
        }
    }

    /** A picked MP3 copied once, refusing more than 5 MB. */
    private static File copyAudio(Context c, Uri uri, File to) throws IOException {
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(to)) {
            if (in == null) throw new IOException(to.getName() + " tidak dapat dibuka");
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > PhotoMath.MAX_MP3) throw new IOException(to.getName() + " melebihi 5 MB");
                out.write(buf, 0, r);
            }
        }
        return to;
    }
}
