package my.glasstv.remote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the form says. Null fields are left alone on the TV; set ones become config.json keys. */
final class Settings {
    static final int ALL = 0, WALLPAPER = 1, HOME = 2, PRAYER = 3, BISMILLAH = 4;

    Boolean places;
    Integer wallpaperSeconds;
    List<String> dockApps;
    Integer columns;
    Boolean networkPill, reducedMotion, bismillah, keepScreenOn;
    String backgroundColor;
    String zone, zoneName;
    List<String> azanPrayers;
    String azanText, azanSubtext;
    Integer azanDim;

    /** A copy holding only one section's fields, so its send button leaves the rest of the TV alone. */
    static Settings only(int section, Settings from) {
        if (section == ALL) return from;
        Settings s = new Settings();
        if (section == WALLPAPER) {
            s.places = from.places;
            s.wallpaperSeconds = from.wallpaperSeconds;
        } else if (section == HOME) {
            s.dockApps = from.dockApps;
            s.columns = from.columns;
            s.networkPill = from.networkPill;
            s.reducedMotion = from.reducedMotion;
            s.keepScreenOn = from.keepScreenOn;
            s.backgroundColor = from.backgroundColor;
        } else if (section == PRAYER) {
            s.zone = from.zone;
            s.zoneName = from.zoneName;
            s.azanPrayers = from.azanPrayers;
            s.azanText = from.azanText;
            s.azanSubtext = from.azanSubtext;
            s.azanDim = from.azanDim;
        } else if (section == BISMILLAH) {
            s.bismillah = from.bismillah;
        }
        return s;
    }

    Map<String, Object> changes() {
        Map<String, Object> c = new LinkedHashMap<>();
        if (places != null) {
            c.put("places", places);
            // Places pick their own rhythm; an interval only means something for the owner's pictures.
            c.put("wallpaper_seconds", places ? null : wallpaperSeconds);
            if (!places && wallpaperSeconds == null) c.remove("wallpaper_seconds");
        } else if (wallpaperSeconds != null) {
            c.put("wallpaper_seconds", wallpaperSeconds);
        }
        put(c, "dock_apps", dockApps);
        put(c, "columns", columns);
        put(c, "show_network_pill", networkPill);
        put(c, "reduced_motion", reducedMotion);
        put(c, "keep_screen_on", keepScreenOn);
        put(c, "bismillah", bismillah);
        put(c, "background_color", backgroundColor);
        put(c, "prayer_zone", zone);
        put(c, "prayer_zone_name", zoneName);
        put(c, "azan_prayers", azanPrayers);
        put(c, "azan_text", azanText);
        put(c, "azan_subtext", azanSubtext);
        put(c, "azan_dim", azanDim);
        return c;
    }

    private static void put(Map<String, Object> c, String key, Object v) {
        if (v != null) c.put(key, v);
    }
}
