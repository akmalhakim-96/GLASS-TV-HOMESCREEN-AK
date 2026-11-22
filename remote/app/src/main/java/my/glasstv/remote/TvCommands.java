package my.glasstv.remote;

/** Shell commands sent to the TV; the same paths tools/theme.ps1 uses. */
final class TvCommands {
    static final String PKG = "com.gathin.tvhome";
    static final String DIR = "/sdcard/Android/data/" + PKG + "/files";
    static final String LIST_APPS =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER";

    /** Plays the azan now, with the next prayer's name (Glass TV's AzanReceiver test action). */
    static final String AZAN_TEST = "am broadcast -n " + PKG + "/.AzanReceiver -a " + PKG + ".AZAN_TEST";

    /** What the owner copies when an azan did not show: version, the alarm, permissions and the azan log. */
    static final String AZAN_REPORT = "echo '== masa'; date; "
            + "echo '== versi'; dumpsys package " + PKG + " | grep -m2 -E 'lastUpdateTime|firstInstallTime'; "
            + "echo -n 'skrin azan baharu: '; dumpsys package " + PKG + " | grep -c AzanActivity; "
            + "echo '== keadaan'; dumpsys package " + PKG + " | grep -m1 -o 'stopped=[a-z]*'; "
            + "appops get " + PKG + " SYSTEM_ALERT_WINDOW; "
            + "settings get secure enabled_accessibility_services; "
            + "echo '== alarm'; dumpsys alarm | grep -A3 'com.gathin.tvhome' | head -20; "
            + "echo '== log'; logcat -d -v time -s TvHome.Azan:* TvHome.Prayer:* | tail -30; "
            + "logcat -d -v time | grep -i -E 'gathin.tvhome.*(kill|died|crash|force)|AzanActivity|AzanService' | tail -15";

    private TvCommands() {}

    static String prepareWallpapers() { return "rm -rf " + DIR + "/wallpapers.new; mkdir -p " + DIR + "/wallpapers.new"; }

    /** Only after every photo arrived: the old set is replaced in one step. */
    static String swapWallpapers() { return "rm -rf " + DIR + "/wallpapers; mv " + DIR + "/wallpapers.new " + DIR + "/wallpapers"; }

    static String clearWallpapers() { return "rm -rf " + DIR + "/wallpapers " + DIR + "/wallpapers.new"; }

    /** Glass TV reads config.json again. No force-stop: it would switch off the remote-key service. */
    static String restartHome() { return "am broadcast -n " + PKG + "/.AzanReceiver -a " + PKG + ".RELOAD"; }

    static String readConfig() { return "cat " + DIR + "/config.json 2>/dev/null"; }

    /** Prints how many pictures the TV already has in wallpapers/. */
    static String countWallpapers() { return "ls " + DIR + "/wallpapers 2>/dev/null | wc -l"; }

    /** The picture behind the azan screen (Glass TV's AzanActivity). */
    static String azanWallpaperPath() { return DIR + "/azan_wallpaper.jpg"; }

    /** Where a file waits when the TV refuses a push straight into Glass TV's folder (Android 11+). */
    static String tmpPath(String name) { return "/data/local/tmp/glasstv-" + name; }

    /** Copies a waiting file into place from inside the TV; prints GLASSTV_OK when it worked. */
    static String copyInto(String tmp, String dst) {
        return "cp -f '" + tmp + "' '" + dst + "' && rm -f '" + tmp + "' && echo GLASSTV_OK";
    }

    static String makeDir() { return "mkdir -p " + DIR; }
}
