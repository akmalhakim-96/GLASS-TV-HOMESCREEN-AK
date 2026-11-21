package my.glasstv.remote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the form says. Null fields are left alone on the TV; set ones become config.json keys. */
final class Settings {
    Boolean places;
    Integer wallpaperSeconds;
    List<String> dockApps;
    Integer columns;
    Boolean networkPill, reducedMotion, bismillah;
    String backgroundColor;
    String zone, zoneName;
    List<String> azanPrayers;
    String azanText, azanSubtext;
    Integer azanDim;

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
