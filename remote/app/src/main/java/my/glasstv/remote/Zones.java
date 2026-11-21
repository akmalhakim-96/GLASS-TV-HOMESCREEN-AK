package my.glasstv.remote;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;

/** JAKIM's 60 prayer-time zones (zones.json, taken from e-solat.gov.my): state, code and areas. */
final class Zones {

    static final class Zone {
        final String state, code, areas;

        Zone(String state, String code, String areas) {
            this.state = state;
            this.code = code;
            this.areas = areas;
        }

        /** "SGR01 · Gombak, Petaling, …" for the zone picker. */
        String label() { return code + " · " + areas; }
    }

    private static List<Zone> all;

    private Zones() {}

    static synchronized List<Zone> all() {
        if (all != null) return all;
        List<Zone> out = new ArrayList<>();
        try (InputStream in = Zones.class.getResourceAsStream("/zones.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
            JSONArray a = new JSONArray(new String(b.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONArray z = a.getJSONArray(i);
                out.add(new Zone(z.getString(0), z.getString(1), z.getString(2)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("zones.json unreadable", e);
        }
        all = Collections.unmodifiableList(out);
        return all;
    }

    static Zone find(String code) {
        for (Zone z : all()) if (z.code.equalsIgnoreCase(code)) return z;
        return null;
    }

    /** The first area of the zone, for the TV's display name ("Gombak"). */
    static String shortName(Zone z) {
        int comma = z.areas.indexOf(',');
        return (comma < 0 ? z.areas : z.areas.substring(0, comma)).trim();
    }
}
