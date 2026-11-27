package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Fetches the weather pill's reading from Open-Meteo (two small requests), at most hourly and only when
 * called (MainActivity calls it while Home is in front, off the main thread). Keeps the last good reading.
 */
final class WeatherStore {
    private static final String TAG = "TvHome.Weather";
    private static final long EVERY_MS = 60 * 60_000L, RETRY_MS = 10 * 60_000L;

    private WeatherStore() {}

    /** Due when the last good reading is an hour old, and the last try at least ten minutes ago. */
    static boolean due(long lastOkMs, long lastTryMs, long nowMs) {
        return nowMs - lastOkMs >= EVERY_MS && nowMs - lastTryMs >= RETRY_MS;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    static boolean dueNow(Context c) {
        SharedPreferences p = prefs(c);
        return due(p.getLong("ok_at", 0), p.getLong("tried_at", 0), System.currentTimeMillis());
    }

    /** The last good reading, or null. */
    static Weather.Reading cached(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("ok_at")) return null;
        return new Weather.Reading(p.getInt("temp", 0), p.getInt("code", 3), p.getBoolean("day", true),
                p.getInt("aqi", -1), p.getLong("ok_at", 0));
    }

    /** Blocking. Saves only a reading that parses; true when one was saved. */
    static boolean refresh(Context c, String zone) {
        double[] at = WeatherZones.coords(zone);
        if (at == null) return false;
        long now = System.currentTimeMillis();
        prefs(c).edit().putLong("tried_at", now).apply();
        String q = String.format(Locale.ROOT, "latitude=%.2f&longitude=%.2f", at[0], at[1]);
        try {
            String forecast = get("https://api.open-meteo.com/v1/forecast?" + q
                    + "&current=temperature_2m,weather_code,is_day&timezone=Asia%2FKuala_Lumpur");
            String air = null;
            try {
                air = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + q + "&current=us_aqi");
            } catch (Exception e) {
                Log.w(TAG, "air: " + e);   // the weather alone is still worth showing
            }
            Weather.Reading r = Weather.parse(forecast, air, now);
            if (r == null) return false;
            prefs(c).edit().putInt("temp", r.tempC).putInt("code", r.code).putBoolean("day", r.day)
                    .putInt("aqi", r.aqi).putLong("ok_at", now).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "weather: " + e);
            return false;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10_000);
        h.setReadTimeout(10_000);
        h.setRequestProperty("Accept", "application/json");
        try {
            if (h.getResponseCode() != 200) throw new IllegalStateException("HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > 64 * 1024) throw new IllegalStateException("reply too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            h.disconnect();
        }
    }
}
