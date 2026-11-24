package my.glasstv.remote;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asks the Wi-Fi which port a TV's Wireless debugging is on: Android announces "_adb-tls-connect" (and,
 * while the pairing dialog is open, "_adb-tls-pairing") with its address and port. Blocking; call it
 * off the main thread. -1 when nothing from that address answers in time.
 */
final class Mdns {
    static final String CONNECT = "_adb-tls-connect._tcp", PAIRING = "_adb-tls-pairing._tcp";

    private Mdns() {}

    static int findPort(Context c, String type, String host, long timeoutMs) {
        NsdManager nsd = (NsdManager) c.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return -1;
        LinkedBlockingQueue<NsdServiceInfo> found = new LinkedBlockingQueue<>();
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo s) { found.add(s); }
            @Override public void onStartDiscoveryFailed(String t, int e) {}
            @Override public void onStopDiscoveryFailed(String t, int e) {}
            @Override public void onDiscoveryStarted(String t) {}
            @Override public void onDiscoveryStopped(String t) {}
            @Override public void onServiceLost(NsdServiceInfo s) {}
        };
        int[] port = {-1};
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener);
            long end = System.currentTimeMillis() + timeoutMs;
            while (port[0] < 0) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                NsdServiceInfo s = found.poll(left, TimeUnit.MILLISECONDS);
                if (s == null) break;
                CountDownLatch resolved = new CountDownLatch(1);
                nsd.resolveService(s, new NsdManager.ResolveListener() {
                    @Override public void onServiceResolved(NsdServiceInfo r) {
                        if (r.getHost() != null && host.equals(r.getHost().getHostAddress())) port[0] = r.getPort();
                        resolved.countDown();
                    }
                    @Override public void onResolveFailed(NsdServiceInfo r, int e) { resolved.countDown(); }
                });
                resolved.await(Math.max(1, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // discovery refused (busy, or Wi-Fi off): fall back to the other ways
        } finally {
            try {
                nsd.stopServiceDiscovery(listener);
            } catch (RuntimeException ignored) {
            }
        }
        return port[0];
    }
}
