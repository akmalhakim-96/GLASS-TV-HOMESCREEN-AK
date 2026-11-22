package my.glasstv.remote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** The owner's TVs: address, a name they choose and whether the next send goes to it. */
final class TvList {
    static final class Entry {
        final String ip;
        String name;
        boolean selected;

        Entry(String ip, String name, boolean selected) {
            this.ip = ip;
            this.name = name;
            this.selected = selected;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    Entry find(String ip) {
        for (Entry e : entries) if (e.ip.equals(ip)) return e;
        return null;
    }

    /** New addresses join ticked, named after their last number ("TV .7"); known ones are left alone. */
    void addFound(Collection<String> ips) {
        for (String ip : ips) {
            String t = ip.trim();
            if (t.isEmpty() || find(t) != null) continue;
            entries.add(new Entry(t, "TV ." + t.substring(t.lastIndexOf('.') + 1), true));
        }
        entries.sort((a, b) -> Long.compare(order(a.ip), order(b.ip)));
    }

    private static long order(String ip) {
        long v = 0;
        for (String p : ip.split("\\.")) {
            try {
                v = v * 256 + Integer.parseInt(p);
            } catch (NumberFormatException e) {
                v = v * 256;
            }
        }
        return v;
    }

    void rename(String ip, String name) {
        Entry e = find(ip);
        if (e != null && !name.trim().isEmpty()) e.name = name.trim();
    }

    void remove(String ip) {
        entries.removeIf(e -> e.ip.equals(ip));
    }

    void setSelected(String ip, boolean on) {
        Entry e = find(ip);
        if (e != null) e.selected = on;
    }

    void selectAll(boolean on) {
        for (Entry e : entries) e.selected = on;
    }

    List<String> selectedIps() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) if (e.selected) out.add(e.ip);
        return out;
    }

    String toJson() {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : entries) a.put(new JSONObject().put("ip", e.ip).put("name", e.name).put("on", e.selected));
        } catch (Exception ignored) {
        }
        return a.toString();
    }

    static TvList fromJson(String json) {
        TvList l = new TvList();
        if (json == null) return l;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                l.entries.add(new Entry(o.getString("ip"), o.optString("name", o.getString("ip")), o.optBoolean("on", true)));
            }
        } catch (Exception e) {
            return new TvList();
        }
        return l;
    }
}
