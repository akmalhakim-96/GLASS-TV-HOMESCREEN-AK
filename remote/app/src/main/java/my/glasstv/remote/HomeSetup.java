package my.glasstv.remote;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * "Pasang & jadikan Home": installs the Glass TV APK bundled in this app and makes it the TV's Home,
 * the way launcher/install-prebuilt.ps1 does from a computer, with the same safeguard: nothing about
 * the stock launcher changes until Glass TV is seen running, and any failure after the install puts
 * the TV back as it was.
 */
final class HomeSetup {
    static final String NONE = "none";
    private static final String PKG = TvCommands.PKG;
    private static final String COMPONENT = PKG + "/.MainActivity";
    private static final String SERVICE = PKG + "/.HomeKeyService";
    private static final String[] KNOWN_STOCK = {"com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.mitv.tvhome"};
    /**
     * Apps known to claim Home without being a launcher, which leave Android showing its chooser once
     * the stock launcher is off: Xiaomi's factory test app and Google's setup wizard. Only these are
     * ever disabled (never uninstalled), and a rollback turns them back on.
     */
    private static final String[] EXTRA_HOME = {"com.duokan.factorytest", "com.google.android.tungsten.setupwraith"};
    private static final String RESOLVE_HOME =
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME";
    private static final String HOME_CLAIMANTS =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME";
    /**
     * "pid|pid|where": the same Glass TV process seconds apart (not crash-looping), then where the TV
     * is: the focused window, or the resumed activity when no window holds focus for a moment (seen
     * on the Mi TV Stick right after a start). Tries up to five times, a second apart.
     */
    static final String HEALTH = "sleep 4; a=$(pidof " + PKG + "); w=''; for i in 1 2 3 4 5; do "
            + "w=\"$(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus) "
            + "$(dumpsys activity activities 2>/dev/null | grep -m1 mResumedActivity)\"; "
            + "case \"$w\" in *" + PKG + "/*) break;; esac; sleep 1; done; "
            + "b=$(pidof " + PKG + "); echo \"$a|$b|$w\"";

    /** Android 9 shows the Wi-Fi network's name only to apps with location permission. */
    static final String GRANT_LOCATION = "pm grant " + PKG + " android.permission.ACCESS_FINE_LOCATION";

    /** A reason to stop, already in Malay. */
    static final class SetupException extends Exception {
        SetupException(String malay) {
            super(malay);
        }
    }

    /** Filled when the last run was rolled back: what failed, the health line and the TV's error log. */
    static volatile String lastReport;
    private static final String CRASH_LOG = "logcat -d -b crash -t 40; logcat -d -t 60 '*:E'";

    private HomeSetup() {}

    // ---- decisions (pure, tested) ----------------------------------------------------------------

    static boolean sdkOk(String sdk) {
        try {
            return Integer.parseInt(sdk.trim()) >= 26;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The stock launcher to disable, NONE when Glass TV is already Home. */
    static String stockLauncher(String homeResolve, String claimants) throws SetupException {
        String home = lastLine(homeResolve);
        String pkg = home.contains("/") ? home.substring(0, home.indexOf('/')) : home;
        for (String k : KNOWN_STOCK) if (k.equals(pkg)) return k;
        if (pkg.equals(PKG)) return NONE;
        if (pkg.equals("android")) {
            String found = null;
            for (String k : KNOWN_STOCK) {
                if (claimants.contains(k + "/")) {
                    if (found != null) throw new SetupException("TV ada dua Home asal. Pilih satu Home di TV dahulu, kemudian cuba lagi.");
                    found = k;
                }
            }
            if (found != null) return found;
            // No stock launcher left but Glass TV is here: an update, nothing to disable.
            if (claimants.contains(PKG + "/")) return NONE;
        }
        throw new SetupException("Home TV sekarang ialah " + pkg + ", yang tidak dikenali. Tiada apa yang diubah.");
    }

    /** The known non-launcher Home claimants present on this TV, in list order. */
    static java.util.List<String> extraHomeApps(String claimants) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : EXTRA_HOME) if (claimants != null && claimants.contains(k + "/")) out.add(k);
        return out;
    }

    /** "pid|pid|focus": Glass TV is in front and the same process both times (not crashing). */
    static boolean healthy(String line) {
        String[] p = lastLine(line).split("\\|", 3);
        return p.length == 3 && !p[0].trim().isEmpty() && p[0].trim().equals(p[1].trim()) && p[2].contains(PKG + "/");
    }

    /** The enabled accessibility services with HomeKeyService added, keeping every other one. */
    static String withHomeKeyService(String current) {
        String c = current == null ? "" : current.trim();
        if (c.isEmpty() || c.equals("null")) return SERVICE;
        for (String s : c.split(":")) if (s.equals(SERVICE)) return c;
        return c + ":" + SERVICE;
    }

    private static String lastLine(String s) {
        String t = s == null ? "" : s.trim();
        int nl = t.lastIndexOf('\n');
        return (nl < 0 ? t : t.substring(nl + 1)).trim();
    }

    // ---- the run ---------------------------------------------------------------------------------

    /** Runs on a background thread; returns the Malay line for the status text. */
    static String run(Context c, String host, Sender.Progress p) {
        lastReport = null;
        String[] health = {""};
        java.util.List<String> disabledExtras = new java.util.ArrayList<>();
        try (Tv tv = Tv.connect(c, host)) {
            p.step("Memeriksa TV…");
            if (!sdkOk(tv.shell("getprop ro.build.version.sdk"))) {
                return "TV ini guna Android lama (bawah 8). Glass TV tidak boleh jadi Home. Tiada apa yang diubah.";
            }
            if (tv.shell("getprop ro.product.manufacturer").trim().equalsIgnoreCase("Amazon")) {
                return "Ini Fire TV: Fire OS akan ambil semula Home. Tiada apa yang diubah.";
            }
            String origHome = lastLine(tv.shell(RESOLVE_HOME));
            String stock = stockLauncher(origHome, tv.shell(HOME_CLAIMANTS));

            p.step("Memasang Glass TV…");
            File apk = new File(c.getCacheDir(), "glass-tv.apk");
            try (InputStream in = c.getAssets().open("glass-tv.apk"); FileOutputStream out = new FileOutputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            tv.push(apk, "/data/local/tmp/tvhome.apk");
            apk.delete();
            String installed = tv.shell("pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk");
            if (!installed.contains("Success")) {
                return "Pemasangan gagal (" + lastLine(installed) + "). Home asal tidak diubah.";
            }
            try {
                tv.shell("appops set " + PKG + " SYSTEM_ALERT_WINDOW allow");
                tv.shell(GRANT_LOCATION);
                tv.shell("am start -W -n " + COMPONENT);
                p.step("Memastikan Glass TV berjalan…");
                health[0] = tv.shell(HEALTH);
                if (!healthy(health[0])) throw new SetupException("Glass TV tidak berjalan dengan baik pada TV ini.");
                if (!stock.equals(NONE)) tv.shell("pm disable-user --user 0 " + stock);
                tv.shell("cmd package set-home-activity --user 0 " + COMPONENT);
                String now = lastLine(tv.shell(RESOLVE_HOME));
                if (!now.startsWith(PKG + "/")) {
                    // Android's chooser: another app also claims Home. Turn off the known ones and try again.
                    String claimants = tv.shell(HOME_CLAIMANTS);
                    for (String extra : extraHomeApps(claimants)) {
                        p.step("Mematikan " + extra + " (turut mengaku sebagai Home)…");
                        tv.shell("pm disable-user --user 0 " + extra);
                        disabledExtras.add(extra);
                    }
                    tv.shell("cmd package set-home-activity --user 0 " + COMPONENT);
                    now = lastLine(tv.shell(RESOLVE_HOME));
                    if (!now.startsWith(PKG + "/")) {
                        health[0] = "Home claimants:\n" + claimants.trim();
                        throw new SetupException("Home masih ke " + now + ".");
                    }
                }
                tv.shell("input keyevent KEYCODE_HOME");
                health[0] = tv.shell(HEALTH);
                if (!healthy(health[0])) throw new SetupException("Butang Home tidak membuka Glass TV.");
            } catch (Exception e) {
                String why = e instanceof SetupException ? e.getMessage() : Messages.forError(e);
                p.step("Mengumpul butiran…");
                String log = "";
                try {
                    log = tv.shell(CRASH_LOG);
                } catch (Exception ignored) {
                }
                lastReport = Diagnostics.report(why, health[0], log);
                p.step("Mengembalikan Home asal…");
                for (String extra : disabledExtras) {
                    try {
                        tv.shell("pm enable --user 0 " + extra);
                    } catch (Exception ignored) {
                    }
                }
                restore(tv, stock, origHome);
                return why + " Home asal TV sudah dikembalikan.";
            }

            p.step("Menghidupkan butang Home dan OK remote…");
            tv.shell("settings put secure enabled_accessibility_services "
                    + withHomeKeyService(tv.shell("settings get secure enabled_accessibility_services")));
            tv.shell("settings put secure accessibility_enabled 1");
            if (tv.shell("settings get secure high_text_contrast_enabled").trim().equals("1")) {
                tv.shell("settings put secure high_text_contrast_enabled 0");
            }
            return "Siap ✓ Glass TV kini Home. Sekarang tekan HANTAR KE TV untuk hantar zon solat dan azan.";
        } catch (SetupException e) {
            return e.getMessage();
        } catch (Exception e) {
            return Messages.forError(e);
        }
    }

    /** Puts the TV back: Glass TV out, the stock launcher on and Home where it was. Best effort. */
    private static void restore(Tv tv, String stock, String origHome) {
        try {
            // Glass TV was already Home before (an update): removing it would leave the TV without one.
            if (!stock.equals(NONE)) tv.shell("pm uninstall " + PKG);
            if (!stock.equals(NONE)) tv.shell("pm enable --user 0 " + stock);
            if (origHome.contains("/") && !origHome.startsWith(PKG + "/")) {
                tv.shell("cmd package set-home-activity --user 0 " + origHome);
            }
            tv.shell("input keyevent KEYCODE_HOME");
        } catch (Exception ignored) {
        }
    }
}
