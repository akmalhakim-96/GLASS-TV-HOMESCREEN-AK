package com.gathin.tvhome;

import org.json.JSONObject;

/**
 * The Home extras in config.json: "weather" (the pill under the clock), "daily_verse" and
 * "display_language" ("ms" or "en", for the verse translation and the weather words). A config without
 * them shows the verse, in Malay, and the weather when the prayer zone is known.
 */
final class HomeExtras {
    final boolean weather, verse;
    final String lang;

    private HomeExtras(boolean weather, boolean verse, String lang) {
        this.weather = weather;
        this.verse = verse;
        this.lang = lang;
    }

    static HomeExtras fromJson(JSONObject j) {
        if (j == null) return new HomeExtras(true, true, "ms");
        String lang = "en".equals(j.optString("display_language", "ms")) ? "en" : "ms";
        return new HomeExtras(j.optBoolean("weather", true), j.optBoolean("daily_verse", true), lang);
    }

    /** Weather needs the setting and a zone whose town we know. */
    boolean weatherOn(String zone) {
        return weather && WeatherZones.coords(zone) != null;
    }
}
