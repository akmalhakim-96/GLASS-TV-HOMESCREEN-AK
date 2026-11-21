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
    static String send(Context c, String host, Settings s, List<Uri> photos, boolean darken,
                       Uri azan, Uri azanSubuh, Progress p) {
        try (Tv tv = Tv.connect(c, host)) {
            p.step("Bersambung ke TV…");
            tv.shell(TvCommands.makeDir());
            String existing = tv.shell(TvCommands.readConfig());

            int sent = 0, skipped = 0;
            if (Boolean.FALSE.equals(s.places) && !photos.isEmpty()) {
                tv.shell(TvCommands.prepareWallpapers());
                for (int i = 0; i < photos.size() && sent < PhotoMath.MAX_PHOTOS; i++) {
                    p.step("Menyediakan gambar " + (i + 1) + " daripada " + photos.size() + "…");
                    File f;
                    try {
                        f = PhotoPrep.prepare(c, photos.get(i), darken, sent);
                    } catch (Exception e) {
                        skipped++;   // deleted from the gallery since it was picked, or not a picture
                        continue;
                    }
                    tv.push(f, TvCommands.DIR + "/wallpapers.new/" + f.getName());
                    f.delete();
                    sent++;
                }
                if (sent > 0) tv.shell(TvCommands.swapWallpapers());
            } else if (Boolean.TRUE.equals(s.places)) {
                tv.shell(TvCommands.clearWallpapers());
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

            p.step("Memulakan semula Home…");
            tv.shell(TvCommands.restartHome());
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
