package my.glasstv.remote;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** HANTAR KE TV: photos, azan recordings and settings to the TV, then Home restarts with them. */
final class Sender {
    interface Progress {
        void step(String malayText);
    }

    private Sender() {}

    /** Runs on a background thread; returns the line shown under the button. */
    static String send(Context c, String host, Settings s, List<Uri> photos, boolean darken, boolean fit,
                       Uri azanWallpaper, boolean azanFit, Uri azan, Uri azanSubuh, Progress p) {
        try (Tv tv = Tv.connect(c, host)) {
            p.step("Bersambung ke TV…");
            tv.shell(TvCommands.makeDir());
            boolean inbox = tv.usesInbox();
            if (inbox) tv.shell(TvCommands.GRANT_FILES);
            String existing = tv.readConfig();

            int sent = 0, skipped = 0;
            if (Boolean.FALSE.equals(s.places) && !photos.isEmpty()) {
                tv.shell(inbox ? "rm -rf " + TvCommands.INBOX + "/wallpapers" : TvCommands.prepareWallpapers());
                for (int i = 0; i < photos.size() && sent < PhotoMath.MAX_PHOTOS; i++) {
                    p.step("Menyediakan gambar " + (i + 1) + " daripada " + photos.size() + "…");
                    File f;
                    try {
                        f = PhotoPrep.prepare(c, photos.get(i), darken, fit, PhotoMath.fileName(sent));
                    } catch (Exception | OutOfMemoryError e) {
                        skipped++;   // deleted from the gallery since it was picked, not a picture, or too big
                        continue;
                    }
                    tv.push(f, TvCommands.DIR + "/wallpapers.new/" + f.getName());
                    f.delete();
                    sent++;
                }
                String none = SendGate.afterPhotos(photos.size(), sent);
                if (none != null) return none;   // keep the TV as it is rather than a blank background
                if (!inbox) tv.shell(TvCommands.swapWallpapers());   // in the inbox, Glass TV swaps them itself
            } else if (Boolean.TRUE.equals(s.places)) {
                tv.shell(inbox ? "mkdir -p " + TvCommands.INBOX + "; touch " + TvCommands.INBOX + "/clear-wallpapers"
                        : TvCommands.clearWallpapers());
            }

            if (azanWallpaper != null) {
                p.step("Menyediakan gambar latar azan…");
                File w = PhotoPrep.prepare(c, azanWallpaper, false, azanFit, "azan_wallpaper.jpg");
                tv.push(w, TvCommands.azanWallpaperPath());
                w.delete();
            }
            if (azan != null) pushAudio(c, tv, azan, "azan.mp3", p);
            if (azanSubuh != null) pushAudio(c, tv, azanSubuh, "azan_subuh.mp3", p);

            p.step("Menyimpan tetapan…");
            File cfg = new File(c.getCacheDir(), "config.json");
            try (FileOutputStream out = new FileOutputStream(cfg)) {
                out.write(ConfigMerge.merge(existing, s.changes()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            tv.push(cfg, TvCommands.DIR + "/config.json");
            cfg.delete();

            tv.shell(HomeSetup.GRANT_LOCATION);   // the Wi-Fi name on Home (installs from older versions lack it)
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
            if (sent > 0) done += " " + sent + " gambar dihantar";
            if (skipped > 0) done += " (" + skipped + " gambar tidak dapat dibaca)";
            return done;
        } catch (Exception e) {
            return Messages.forError(e);
        }
    }

    private static void pushAudio(Context c, Tv tv, Uri uri, String name, Progress p) throws IOException {
        p.step("Menghantar " + name + "…");
        File f = new File(c.getCacheDir(), name);
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new IOException(name + " tidak dapat dibuka");
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > PhotoMath.MAX_MP3) throw new IOException(name + " melebihi 5 MB");
                out.write(buf, 0, r);
            }
        }
        tv.push(f, TvCommands.DIR + "/" + name);
        f.delete();
    }
}
