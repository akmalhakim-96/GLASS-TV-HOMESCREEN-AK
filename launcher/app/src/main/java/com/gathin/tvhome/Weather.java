package com.gathin.tvhome;

import org.json.JSONObject;

/**
 * The weather pill under the clock: current temperature and conditions from Open-Meteo, and an air-quality
 * number (US AQI) shown with Malaysia's API band words. Not called "API": it is not the official APIMS
 * reading. Pure: parsing and wording only; WeatherStore fetches.
 */
final class Weather {
    /** Shown for up to this long after the last good reading, then hidden. */
    static final long FRESH_MS = 3 * 3_600_000L;

    static final class Reading {
        final int tempC, code, aqi;   // aqi -1 when unknown
        final boolean day;
        final long atMs;

        Reading(int tempC, int code, boolean day, int aqi, long atMs) {
            this.tempC = tempC;
            this.code = code;
            this.day = day;
            this.aqi = aqi;
            this.atMs = atMs;
        }
    }

    private Weather() {}

    /** Null when the forecast reply is not usable; a bad air reply only leaves aqi at -1. */
    static Reading parse(String forecastJson, String airJson, long nowMs) {
        try {
            JSONObject cur = new JSONObject(forecastJson).getJSONObject("current");
            if (!cur.has("temperature_2m") || !cur.has("weather_code")) return null;
            int temp = (int) Math.round(cur.getDouble("temperature_2m"));
            int code = cur.getInt("weather_code");
            boolean day = cur.optInt("is_day", 1) == 1;
            return new Reading(temp, code, day, aqi(airJson), nowMs);
        } catch (Exception e) {
            return null;
        }
    }

    private static int aqi(String airJson) {
        try {
            JSONObject cur = new JSONObject(airJson).getJSONObject("current");
            if (!cur.has("us_aqi") || cur.isNull("us_aqi")) return -1;
            return (int) Math.round(cur.getDouble("us_aqi"));
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean fresh(Reading r, long nowMs) {
        return r != null && nowMs - r.atMs <= FRESH_MS;
    }

    private static boolean en(String lang) {
        return "en".equals(lang);
    }

    private static boolean rain(int code) {
        return (code >= 51 && code <= 67) || (code >= 71 && code <= 86);
    }

    static String words(int code, boolean day, String lang) {
        boolean e = en(lang);
        if (code == 0) return e ? "Clear" : "Cerah";
        if (code == 1 || code == 2) return e ? "Partly cloudy" : "Berawan";
        if (code == 3) return e ? "Cloudy" : "Berawan";
        if (code == 45 || code == 48) return e ? "Fog" : "Berkabus";
        if (rain(code)) return e ? "Rain" : "Hujan";
        if (code >= 95) return e ? "Thunderstorm" : "Ribut petir";
        return e ? "Cloudy" : "Berawan";
    }

    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1 || code == 2) return day ? "⛅" : "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (rain(code)) return "🌧️";
        if (code >= 95) return "⛈️";
        return "☁️";
    }

    static String band(int aqi, String lang) {
        boolean e = en(lang);
        if (aqi <= 50) return e ? "Good" : "Baik";
        if (aqi <= 100) return e ? "Moderate" : "Sederhana";
        if (aqi <= 200) return e ? "Unhealthy" : "Tidak sihat";
        if (aqi <= 300) return e ? "Very unhealthy" : "Sangat tidak sihat";
        return e ? "Hazardous" : "Berbahaya";
    }

    static int bandColor(int aqi) {
        if (aqi <= 50) return 0xFF4ADE80;
        if (aqi <= 100) return 0xFFFACC15;
        if (aqi <= 200) return 0xFFFB923C;
        if (aqi <= 300) return 0xFFF87171;
        return 0xFFC084FC;
    }

    /** "⛅ 29° Berawan" */
    static String weatherText(Reading r, String lang) {
        return icon(r.code, r.day) + " " + r.tempC + "° " + words(r.code, r.day, lang);
    }

    /** "Udara 50 Sederhana"; "" when the air reading is unknown. */
    static String airText(Reading r, String lang) {
        if (r.aqi < 0) return "";
        return (en(lang) ? "Air " : "Udara ") + r.aqi + " " + band(r.aqi, lang);
    }
}
