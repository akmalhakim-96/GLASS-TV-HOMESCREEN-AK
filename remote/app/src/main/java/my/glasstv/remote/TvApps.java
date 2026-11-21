package my.glasstv.remote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The TV's apps for the dock picker, from `cmd package query-activities … LEANBACK_LAUNCHER`. */
final class TvApps {
    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        NAMES.put("com.google.android.youtube.tv", "YouTube");
        NAMES.put("com.netflix.ninja", "Netflix");
        NAMES.put("com.disney.disneyplus", "Disney+");
        NAMES.put("com.amazon.amazonvideo.livingroom", "Prime Video");
        NAMES.put("com.vuclip.viu", "Viu");
        NAMES.put("com.iqiyi.i18n.tv", "iQIYI");
        NAMES.put("com.spotify.tv.android", "Spotify");
        NAMES.put("com.apple.atve.androidtv.appletv", "Apple TV");
        NAMES.put("com.astro.astro", "Astro GO");
        NAMES.put("com.tm.unifiplay", "unifi TV");
        NAMES.put("com.android.vending", "Play Store");
        NAMES.put("my.com.tonton", "Tonton");
    }

    private TvApps() {}

    /** Unique packages in the order listed, without Glass TV itself. */
    static List<String> parse(String output) {
        Set<String> pkgs = new LinkedHashSet<>();
        for (String line : output.split("\n")) {
            String l = line.trim();
            int slash = l.indexOf('/');
            if (slash <= 0) continue;
            String pkg = l.substring(0, slash);
            if (!pkg.equals("com.gathin.tvhome")) pkgs.add(pkg);
        }
        return new ArrayList<>(pkgs);
    }

    static String label(String pkg) {
        String n = NAMES.get(pkg);
        return n != null ? n : pkg;
    }
}
