package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The daily verse on Home: 60 short verses in res/raw/ayat.json, made by tools/ayat/fetch.py from
 * Quran.com (never typed by hand). One a day by day of the year, the same on every TV.
 */
final class Verses {
    static final class Verse {
        final String ref, surah, ar, ms, en;

        Verse(String ref, String surah, String ar, String ms, String en) {
            this.ref = ref;
            this.surah = surah;
            this.ar = ar;
            this.ms = ms;
            this.en = en;
        }

        String translation(String lang) {
            return "en".equals(lang) ? en : ms;
        }
    }

    private final List<Verse> all;

    private Verses(List<Verse> all) {
        this.all = Collections.unmodifiableList(all);
    }

    /** Empty (never null) when the data is unusable. */
    static Verses parse(String json) {
        List<Verse> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Verse(o.getString("ref"), o.getString("surah"), o.getString("ar"), o.getString("ms"), o.getString("en")));
            }
        } catch (Exception e) {
            out.clear();
        }
        return new Verses(out);
    }

    /** Reads the bundled file; empty when it cannot be read. Call off the main thread. */
    static Verses load(Context c) {
        try (InputStream in = c.getResources().openRawResource(R.raw.ayat)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
            return parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return parse("[]");
        }
    }

    boolean isEmpty() { return all.isEmpty(); }

    int size() { return all.size(); }

    Verse get(int i) { return all.get(i); }

    /** The verse for that day (Malaysia date), or null when there are none. */
    Verse pick(LocalDate day) {
        if (all.isEmpty()) return null;
        return all.get((day.getDayOfYear() - 1) % all.size());
    }

    /** "AT-TALAQ 65:3" */
    static String heading(Verse v) {
        return v.surah.toUpperCase(Locale.ROOT) + " " + v.ref;
    }
}
