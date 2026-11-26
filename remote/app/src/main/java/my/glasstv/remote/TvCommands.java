package my.glasstv.remote;

/** Shell commands sent to the TV; the same paths tools/theme.ps1 uses. */
final class TvCommands {
    static final String PKG = "com.gathin.tvhome";
    static final String DIR = "/sdcard/Android/data/" + PKG + "/files";
    static final String LIST_APPS =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER";

    /** Plays the azan now, with the next prayer's name (Glass TV's AzanReceiver test action). */
    /** -f 32: delivered even if Glass TV was force-stopped and has not run since. */
    static final String AZAN_TEST = "am broadcast -f 32 -n " + PKG + "/.AzanReceiver -a " + PKG + ".AZAN_TEST 2>&1";
    static final String AZAN_NOT_INSTALLED = "Glass TV belum dipasang di TV ini (tekan Pasang di tab TV).";

    /** One TV's line for the azan test: "Siap" when the TV took the broadcast. */
    static String azanTestResult(int glassVersion, String broadcastOutput) {
        if (glassVersion < 0) return AZAN_NOT_INSTALLED;
        if (broadcastOutput != null && broadcastOutput.contains("Broadcast completed")) return "Siap ✓ azan dimainkan";
        String out = broadcastOutput == null ? "" : broadcastOutput.trim();
        return "TV tidak terima arahan azan (" + (out.isEmpty() ? "tiada jawapan" : out) + ")";
    }

    /** What the owner copies when an azan did not show: version, the alarm, permissions and the azan log. */
    static final String AZAN_REPORT = "echo '== masa'; date; "
            + "echo '== versi'; dumpsys package " + PKG + " | grep -m2 -E 'lastUpdateTime|firstInstallTime'; "
            + "echo -n 'skrin azan baharu: '; dumpsys package " + PKG + " | grep -c AzanActivity; "
            + "echo '== keadaan'; dumpsys package " + PKG + " | grep -m1 -o 'stopped=[a-z]*'; "
            + "appops get " + PKG + " SYSTEM_ALERT_WINDOW; "
            + "settings get secure enabled_accessibility_services; "
            + "echo '== peti masuk'; echo -n 'Android '; getprop ro.build.version.release; "
            + "dumpsys package " + PKG + " | grep -m1 -o 'versionCode=[0-9]*'; "
            + "appops get " + PKG + " MANAGE_EXTERNAL_STORAGE; "
            + "ls -la /sdcard/GlassTV /sdcard/GlassTV/.current 2>&1 | head -15; "
            + "logcat -d -v time -s TvHome.Inbox:* | tail -5; "
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

    /** The shared inbox Glass TV empties when it starts (TVs that refuse writes into its folder). */
    static final String INBOX = "/sdcard/GlassTV";
    /** Prints GLASSTV_OK when the TV lets us write into Glass TV's own folder. */
    static final String CAN_WRITE_DIR = "touch " + DIR + "/.glasstv-w 2>/dev/null && rm -f " + DIR + "/.glasstv-w && echo GLASSTV_OK";
    /** Lets Glass TV read the inbox (all-files access on Android 11+, storage on 10 and older). */
    static final String GRANT_FILES = "appops set " + PKG + " MANAGE_EXTERNAL_STORAGE allow; "
            + "pm grant " + PKG + " android.permission.READ_EXTERNAL_STORAGE 2>/dev/null; true";

    /** Only files for Glass TV's own folder go through the inbox; anything else (the APK) is pushed as is. */
    static boolean viaInbox(String remote) {
        return remote.startsWith(DIR + "/");
    }

    /** Where a file meant for Glass TV's folder goes in the inbox; wallpapers.new is simply wallpapers there. */
    static String inboxPath(String remote) {
        String rel = remote.startsWith(DIR + "/") ? remote.substring(DIR.length() + 1) : remote;
        if (rel.startsWith("wallpapers.new/")) rel = "wallpapers/" + rel.substring("wallpapers.new/".length());
        return INBOX + "/" + rel;
    }

    static String readConfig(boolean inbox) {
        return inbox ? "cat " + INBOX + "/.current/config.json 2>/dev/null" : readConfig();
    }

    /** Where a file waits when the TV refuses a push straight into Glass TV's folder (Android 11+). */
    static String tmpPath(String name) { return "/data/local/tmp/glasstv-" + name; }

    /** Copies a waiting file into place from inside the TV; prints GLASSTV_OK when it worked. */
    static String copyInto(String tmp, String dst) {
        return "cp -f '" + tmp + "' '" + dst + "' && rm -f '" + tmp + "' && echo GLASSTV_OK";
    }

    /** Android's own screensaver (Google's) off, or back on, in the TV's settings. */
    static String screensaver(boolean on) {
        return on ? "settings put secure screensaver_enabled 1; settings put secure screensaver_activate_on_sleep 1"
                : "settings put secure screensaver_enabled 0; settings put secure screensaver_activate_on_sleep 0; "
                + "settings put secure screensaver_activate_on_dock 0";
    }

    static String makeDir() { return "mkdir -p " + DIR; }
}
