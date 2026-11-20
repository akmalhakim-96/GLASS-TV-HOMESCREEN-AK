package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A zone's prayer times from JAKIM e-solat (esolatApi/TakwimSolat), one entry per day. All times are
 * Malaysian time, whatever zone the TV is set to.
 */
final class PrayerTimes {

    static final ZoneId MY = ZoneId.of("Asia/Kuala_Lumpur");

    enum Prayer {
        SUBUH("Subuh", "subuh", "fajr"),
        ZOHOR("Zohor", "zohor", "dhuhr"),
        ASAR("Asar", "asar", "asr"),
        MAGHRIB("Maghrib", "maghrib", "maghrib"),
        ISYAK("Isyak", "isyak", "isha");

        private final String label, key, jakim;

        Prayer(String label, String key, String jakim) {
            this.label = label;
            this.key = key;
            this.jakim = jakim;
        }

        String label() { return label; }
        String key() { return key; }

        static Prayer fromKey(String key) {
            for (Prayer p : values()) if (p.key.equalsIgnoreCase(key)) return p;
            return null;
        }
    }

    static final class Next {
        final Prayer prayer;
        final ZonedDateTime time;

        Next(Prayer prayer, ZonedDateTime time) {
            this.prayer = prayer;
            this.time = time;
        }
    }

    private static final String[] MONTHS_MS = {"jan", "feb", "mac", "apr", "mei", "jun", "jul", "ogo", "sep", "okt", "nov", "dis"};
    private static final String[] MONTHS_EN = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final String[] HIJRI = {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
            "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"};

    private final TreeMap<LocalDate, LocalTime[]> days;
    private final Map<LocalDate, String> hijri;

    private PrayerTimes(TreeMap<LocalDate, LocalTime[]> days, Map<LocalDate, String> hijri) {
        this.days = days;
        this.hijri = hijri;
    }

    /** Throws IllegalArgumentException when JAKIM answers without data (unknown zone, no record). */
    static PrayerTimes parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"OK!".equals(root.optString("status"))) throw new IllegalArgumentException("JAKIM: " + root.optString("status"));
        JSONArray list = root.optJSONArray("prayerTime");
        if (list == null || list.length() == 0) throw new IllegalArgumentException("JAKIM: no prayer times");
        TreeMap<LocalDate, LocalTime[]> days = new TreeMap<>();
        Map<LocalDate, String> hijri = new HashMap<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.getJSONObject(i);
            LocalDate date = date(d.getString("date"));
            LocalTime[] t = new LocalTime[Prayer.values().length];
            for (Prayer p : Prayer.values()) t[p.ordinal()] = LocalTime.parse(d.getString(p.jakim));
            days.put(date, t);
            String h = hijri(d.optString("hijri"));
            if (h != null) hijri.put(date, h);
        }
        return new PrayerTimes(days, hijri);
    }

    /** "01-Nov-2026", with Malay (Mac, Mei, Ogo, Okt, Dis) or English month abbreviations. */
    private static LocalDate date(String s) {
        String[] p = s.split("-");
        if (p.length != 3) throw new IllegalArgumentException("JAKIM date: " + s);
        String m = p[1].trim().toLowerCase(Locale.ROOT);
        if (m.length() > 3) m = m.substring(0, 3);
        int month = -1;
        for (int i = 0; i < 12; i++) if (m.equals(MONTHS_MS[i]) || m.equals(MONTHS_EN[i])) month = i + 1;
        if (month < 0) throw new IllegalArgumentException("JAKIM month: " + s);
        return LocalDate.of(Integer.parseInt(p[2].trim()), month, Integer.parseInt(p[0].trim()));
    }

    /** "1448-05-21" → "21 Jamadilawal 1448H"; null when it doesn't parse. */
    private static String hijri(String s) {
        String[] p = s.split("-");
        if (p.length != 3) return null;
        try {
            int month = Integer.parseInt(p[1]);
            if (month < 1 || month > 12) return null;
            return Integer.parseInt(p[2]) + " " + HIJRI[month - 1] + " " + Integer.parseInt(p[0]) + "H";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    boolean has(LocalDate day) { return days.containsKey(day); }

    LocalDate lastDay() { return days.lastKey(); }

    ZonedDateTime at(LocalDate day, Prayer p) {
        LocalTime[] t = days.get(day);
        return t == null ? null : ZonedDateTime.of(day, t[p.ordinal()], MY);
    }

    String hijri(LocalDate day) { return hijri.get(day); }

    /** The first prayer strictly after now, today or tomorrow; null when the data doesn't reach it. */
    Next next(ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(MY);
        for (LocalDate day = local.toLocalDate(); !day.isAfter(local.toLocalDate().plusDays(1)); day = day.plusDays(1)) {
            if (!has(day)) continue;
            for (Prayer p : Prayer.values()) {
                ZonedDateTime t = at(day, p);
                if (t.isAfter(local)) return new Next(p, t);
            }
        }
        return null;
    }
}
