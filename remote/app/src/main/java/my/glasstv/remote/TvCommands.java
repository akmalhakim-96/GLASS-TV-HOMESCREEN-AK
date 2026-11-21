package my.glasstv.remote;

/** Shell commands sent to the TV; the same paths tools/theme.ps1 uses. */
final class TvCommands {
    static final String PKG = "com.gathin.tvhome";
    static final String DIR = "/sdcard/Android/data/" + PKG + "/files";
    static final String LIST_APPS =
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER";

    private TvCommands() {}

    static String prepareWallpapers() { return "rm -rf " + DIR + "/wallpapers.new; mkdir -p " + DIR + "/wallpapers.new"; }

    /** Only after every photo arrived: the old set is replaced in one step. */
    static String swapWallpapers() { return "rm -rf " + DIR + "/wallpapers; mv " + DIR + "/wallpapers.new " + DIR + "/wallpapers"; }

    static String clearWallpapers() { return "rm -rf " + DIR + "/wallpapers " + DIR + "/wallpapers.new"; }

    static String restartHome() { return "am force-stop " + PKG + "; am start -n " + PKG + "/.MainActivity"; }

    static String readConfig() { return "cat " + DIR + "/config.json 2>/dev/null"; }

    /** Prints how many pictures the TV already has in wallpapers/. */
    static String countWallpapers() { return "ls " + DIR + "/wallpapers 2>/dev/null | wc -l"; }

    static String makeDir() { return "mkdir -p " + DIR; }
}
