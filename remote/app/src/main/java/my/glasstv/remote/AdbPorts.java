package my.glasstv.remote;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finding Wireless debugging's port when the phone's mDNS search finds nothing (school Wi-Fi often
 * drops multicast): Android gives adbd a random port from the ephemeral range each time it starts, and
 * on the local network a closed port answers "refused" at once, so trying them all takes seconds. Every
 * open port found is then asked whether it speaks ADB with TLS.
 */
final class AdbPorts {
    static final int FIRST = 30000, LAST = 61000;   // Linux hands out 32768–60999

    private AdbPorts() {}

    static int scan(String host, int first, int last, int threads, int connectMs) {
        List<Integer> open = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger next = new AtomicInteger(first);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                for (int p = next.getAndIncrement(); p <= last; p = next.getAndIncrement()) {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(host, p), connectMs);
                        open.add(p);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<Integer> found;
        synchronized (open) {
            found = new ArrayList<>(open);
        }
        Collections.sort(found);
        for (int p : found) if (TlsAdb.isTlsAdb(host, p, 1_500)) return p;
        return -1;
    }
}
