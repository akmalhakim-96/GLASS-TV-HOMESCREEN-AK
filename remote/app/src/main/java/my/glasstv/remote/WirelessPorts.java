package my.glasstv.remote;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Where each TV's Wireless debugging is listening. It moves whenever the TV restarts or debugging is
 * switched off and on, so: the port that worked last time, else the TV's own announcement on the
 * Wi-Fi, else a quick sweep of the ports Android hands out.
 */
final class WirelessPorts {
    private WirelessPorts() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wireless_ports", Context.MODE_PRIVATE);
    }

    static int find(Context c, String host) {
        int last = prefs(c).getInt(host, -1);
        if (last > 0 && TlsAdb.isTlsAdb(host, last, 1_500)) return last;
        int p = Mdns.findPort(c, Mdns.CONNECT, host, 2_500);
        if (p > 0 && TlsAdb.isTlsAdb(host, p, 1_500)) return p;
        return AdbPorts.scan(host, AdbPorts.FIRST, AdbPorts.LAST, 64, 400);
    }

    static void remember(Context c, String host, int port) {
        prefs(c).edit().putInt(host, port).apply();
    }
}
