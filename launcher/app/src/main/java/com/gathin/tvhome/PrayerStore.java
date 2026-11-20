package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * A year of JAKIM prayer times for one zone, kept in the app's own storage so Home and the azan work
 * offline. Fetched again when the zone changes, every 30 days, or when fewer than 7 days are left.
 */
final class PrayerStore {
    private static final String TAG = "TvHome.Prayer";
    private static final String FILE = "prayer.json";
    private static final long DAY_MS = 24L * 3600 * 1000;
    private static final long RETRY_MS = 3600 * 1000L;
    private static final String URL = "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone=";

    private PrayerStore() {}

    /** The cached times when they belong to this zone; null otherwise. */
    static PrayerTimes load(Context c, String zone) {
        if (zone.isEmpty() || !zone.equals(prefs(c).getString("prayer_zone", ""))) return null;
        File f = new File(c.getFilesDir(), FILE);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return PrayerTimes.parse(new String(readAll(in, 2 << 20), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "cache unreadable: " + e);
            return null;
        }
    }

    static boolean due(PrayerTimes cached, String cachedZone, String zone, long fetchedAtMs, long nowMs, LocalDate today) {
        if (cached == null || !zone.equals(cachedZone)) return true;
        if (nowMs - fetchedAtMs >= 30 * DAY_MS) return true;
        return cached.lastDay().isBefore(today.plusDays(7));
    }

    /** Whether a fetch is due now, with a one-hour pause after a failed one. */
    static boolean dueNow(Context c, String zone) {
        return dueNow(c, zone, zone.isEmpty() ? null : load(c, zone));
    }

    /** As above, with times already loaded for this zone (or null), so Home need not parse them again. */
    static boolean dueNow(Context c, String zone, PrayerTimes cached) {
        if (zone.isEmpty()) return false;
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (now - p.getLong("prayer_failed_at", 0) < RETRY_MS) return false;
        return due(cached, p.getString("prayer_zone", ""), zone, p.getLong("prayer_fetched_at", 0), now,
                LocalDate.now(PrayerTimes.MY));
    }

    /** Fetches the year for the zone and replaces the cache only with data that parses. Blocking. */
    static boolean refresh(Context c, String zone) {
        SharedPreferences p = prefs(c);
        try {
            byte[] body = get(URL + zone);
            PrayerTimes.parse(new String(body, StandardCharsets.UTF_8));   // never cache a bad answer
            File tmp = new File(c.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            if (!tmp.renameTo(new File(c.getFilesDir(), FILE))) throw new IllegalStateException("rename failed");
            p.edit().putString("prayer_zone", zone).putLong("prayer_fetched_at", System.currentTimeMillis())
                    .remove("prayer_failed_at").apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "fetch failed for " + zone + ": " + e);
            p.edit().putLong("prayer_failed_at", System.currentTimeMillis()).apply();
            return false;
        }
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return readAll(in, 2 << 20);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
            if (out.size() > max) throw new IllegalStateException("too large");
        }
        return out.toByteArray();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prayer", Context.MODE_PRIVATE);
    }
}
