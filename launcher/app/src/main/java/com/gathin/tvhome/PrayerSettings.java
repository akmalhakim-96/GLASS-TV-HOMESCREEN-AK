package com.gathin.tvhome;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.util.EnumSet;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/** The prayer-time keys of config.json. No prayer_zone means the feature is off. */
final class PrayerSettings {

    static final String DEFAULT_TEXT = "Telah masuk waktu solat fardu {waktu} bagi kawasan {zon} dan sekitarnya";
    static final String DEFAULT_SUBTEXT = "Marilah menunaikan solat";

    /** JAKIM zone code such as SGR01; "" when off. */
    final String zone;
    final String zoneName;
    /** Prayers that sound the azan. */
    final EnumSet<Prayer> azan;
    final String text;
    final String subtext;
    /** How dark the screen goes during the azan: 0 light, 1 medium, 2 dark. */
    final int dim;

    private PrayerSettings(String zone, String zoneName, EnumSet<Prayer> azan, String text, String subtext, int dim) {
        this.zone = zone;
        this.zoneName = zoneName;
        this.azan = azan;
        this.text = text;
        this.subtext = subtext;
        this.dim = dim;
    }

    boolean enabled() { return !zone.isEmpty(); }

    static PrayerSettings fromJson(JSONObject j) {
        String zone = j == null ? "" : str(j, "prayer_zone", "").toUpperCase(Locale.ROOT);
        String name = j == null ? "" : str(j, "prayer_zone_name", "");
        EnumSet<Prayer> azan = EnumSet.noneOf(Prayer.class);
        Object list = j == null ? null : j.opt("azan_prayers");
        if (list instanceof JSONArray) {
            JSONArray a = (JSONArray) list;
            for (int i = 0; i < a.length(); i++) {
                Object v = a.opt(i);
                Prayer p = v instanceof String ? Prayer.fromKey(((String) v).trim()) : null;
                if (p != null) azan.add(p);
            }
        }
        if (azan.isEmpty()) azan = EnumSet.allOf(Prayer.class);
        int dim = 1;
        Object d = j == null ? null : j.opt("azan_dim");
        if (d instanceof Number && ((Number) d).intValue() >= 0 && ((Number) d).intValue() <= 2) dim = ((Number) d).intValue();
        return new PrayerSettings(zone, name.isEmpty() ? zone : name, azan,
                j == null ? DEFAULT_TEXT : str(j, "azan_text", DEFAULT_TEXT),
                j == null ? DEFAULT_SUBTEXT : str(j, "azan_subtext", DEFAULT_SUBTEXT), dim);
    }

    String textFor(Prayer p) { return fill(text, p); }

    String subtextFor(Prayer p) { return fill(subtext, p); }

    private String fill(String s, Prayer p) {
        return s.replace("{waktu}", p.label()).replace("{zon}", zoneName);
    }

    /** A trimmed string value, or the fallback for a missing or non-string one. An empty string is kept. */
    private static String str(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }
}
