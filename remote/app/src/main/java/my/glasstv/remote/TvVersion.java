package my.glasstv.remote;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which Glass TV the TV runs, compared with the one bundled in this app (assets/glass-tv.apk). */
final class TvVersion {
    /** versionCode of assets/glass-tv.apk; raise it whenever a new Glass TV is bundled. */
    static final int BUNDLED = 9;
    static final String QUERY = "dumpsys package " + TvCommands.PKG + " | grep -m1 versionCode";
    private static final Pattern CODE = Pattern.compile("versionCode=(\\d+)");

    private TvVersion() {}

    /** The versionCode in `dumpsys package` output; -1 when Glass TV is not installed. */
    static int parse(String dumpsys) {
        Matcher m = CODE.matcher(dumpsys == null ? "" : dumpsys);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    static boolean needsUpdate(int onTv) {
        return onTv >= 0 && onTv < BUNDLED;
    }
}
