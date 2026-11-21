package my.glasstv.remote;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Finds TVs on the phone's Wi-Fi: addresses on its /24 that accept a connection on port 5555. */
final class Discovery {
    private static final int TIMEOUT_MS = 400;

    private Discovery() {}

    /** The phone's Wi-Fi IPv4 address, or null when it is not on Wi-Fi. */
    static String phoneIp(Context c) {
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wm == null ? null : Subnet.ipFromWifiInt(wm.getConnectionInfo().getIpAddress());
    }

    static List<String> scan(Context c) {
        List<String> hosts = Subnet.hosts24(phoneIp(c));
        if (hosts.isEmpty()) return Collections.emptyList();
        ExecutorService pool = Executors.newFixedThreadPool(64);
        List<Future<String>> results = new ArrayList<>();
        for (String h : hosts) {
            results.add(pool.submit(() -> {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(h, Tv.PORT), TIMEOUT_MS);
                    return h;
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        List<String> found = new ArrayList<>();
        for (Future<String> f : results) {
            try {
                String h = f.get();
                if (h != null) found.add(h);
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        try {
            pool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        found.sort((a, b) -> Integer.compare(last(a), last(b)));
        return found;
    }

    private static int last(String ip) {
        return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1));
    }
}
