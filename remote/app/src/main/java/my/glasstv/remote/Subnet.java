package my.glasstv.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The phone's Wi-Fi address and the other addresses on its /24, where the TV is looked for. */
final class Subnet {
    private Subnet() {}

    /** WifiInfo.getIpAddress() is little-endian: 0x1701A8C0 → "192.168.1.23". Null for 0 (no Wi-Fi). */
    static String ipFromWifiInt(int ip) {
        if (ip == 0) return null;
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >>> 24) & 0xFF);
    }

    /** 1–254 of the phone's /24 except the phone itself; empty without a valid IPv4 address. */
    static List<String> hosts24(String self) {
        if (self == null) return Collections.emptyList();
        String[] p = self.split("\\.");
        if (p.length != 4) return Collections.emptyList();
        try {
            for (String s : p) {
                int v = Integer.parseInt(s);
                if (v < 0 || v > 255) return Collections.emptyList();
            }
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
        String prefix = p[0] + "." + p[1] + "." + p[2] + ".";
        List<String> out = new ArrayList<>(253);
        for (int i = 1; i <= 254; i++) {
            String h = prefix + i;
            if (!h.equals(self)) out.add(h);
        }
        return out;
    }
}
