package com.gathin.tvhome;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Wi-Fi networks for the network pill's glass menu: the connected one first, then the rest by
 * signal, at most eight, each name once. Joining works directly on Android 9 and older (open and
 * WPA/WPA2 personal networks); newer Android lets only Settings join, so the menu opens it there.
 */
final class WifiList {
    static final int MAX = 8;

    static final class Net {
        final String ssid;
        final int level;
        final String caps;

        Net(String ssid, int level, String caps) {
            this.ssid = ssid;
            this.level = level;
            this.caps = caps == null ? "" : caps;
        }

        boolean secured() {
            String c = caps.toUpperCase(Locale.ROOT);
            return c.contains("WPA") || c.contains("WEP") || c.contains("SAE") || c.contains("EAP") || c.contains("PSK");
        }

        /** Open, or WPA/WPA2 with a password: what the menu can join by itself. */
        boolean directJoin() {
            String c = caps.toUpperCase(Locale.ROOT);
            if (c.contains("EAP") || c.contains("WEP") || c.contains("SAE") || c.contains("OWE")) return false;
            return !secured() || c.contains("PSK");
        }
    }

    private WifiList() {}

    static List<Net> rows(List<Net> scans, String connected) {
        Map<String, Net> best = new LinkedHashMap<>();
        for (Net n : scans) {
            if (n.ssid == null || n.ssid.trim().isEmpty()) continue;
            Net had = best.get(n.ssid);
            if (had == null || n.level > had.level) best.put(n.ssid, n);
        }
        List<Net> out = new ArrayList<>(best.values());
        out.sort((a, b) -> {
            boolean ca = a.ssid.equals(connected), cb = b.ssid.equals(connected);
            if (ca != cb) return ca ? -1 : 1;
            return Integer.compare(b.level, a.level);
        });
        return out.size() > MAX ? new ArrayList<>(out.subList(0, MAX)) : out;
    }

    static List<String> names(List<Net> rows) {
        List<String> out = new ArrayList<>();
        for (Net n : rows) out.add(n.ssid);
        return out;
    }

    /** WifiConfiguration wants the name and the password between double quotes. */
    static String quote(String s) {
        return "\"" + s + "\"";
    }

    // ---- Android ---------------------------------------------------------------------------------

    static List<Net> scan(Context c) {
        List<Net> out = new ArrayList<>();
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return out;
        try {
            wm.startScan();   // results come in over a few seconds; the last ones are shown now
            for (ScanResult r : wm.getScanResults()) out.add(new Net(r.SSID, r.level, r.capabilities));
        } catch (SecurityException ignored) {
        }
        return out;
    }

    /** Whether the menu can join by itself here (Android 9 and older). */
    static boolean canJoin() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    /** A network saved on the TV before (no password needed), or -1. */
    @SuppressWarnings("deprecation")
    static int savedId(Context c, String ssid) {
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return -1;
        try {
            List<WifiConfiguration> saved = wm.getConfiguredNetworks();
            if (saved != null) for (WifiConfiguration w : saved) if (quote(ssid).equals(w.SSID)) return w.networkId;
        } catch (SecurityException ignored) {
        }
        return -1;
    }

    /** Joins a saved network, or adds one with the password (null for open). False if Android refused. */
    @SuppressWarnings("deprecation")
    static boolean join(Context c, Net n, String password) {
        if (!canJoin()) return false;
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return false;
        int id = savedId(c, n.ssid);
        if (id < 0 || password != null) {
            WifiConfiguration w = new WifiConfiguration();
            w.SSID = quote(n.ssid);
            if (n.secured()) w.preSharedKey = quote(password == null ? "" : password);
            else w.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            if (id >= 0) wm.removeNetwork(id);
            id = wm.addNetwork(w);
            if (id < 0) return false;
            wm.saveConfiguration();
        }
        boolean ok = wm.enableNetwork(id, true);
        wm.reconnect();
        return ok;
    }
}
