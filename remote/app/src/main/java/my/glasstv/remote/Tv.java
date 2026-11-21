package my.glasstv.remote;

import android.content.Context;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import dadb.AdbKeyPair;
import dadb.Dadb;

/**
 * One ADB-over-Wi-Fi connection to the TV (port 5555), with this phone's own key. The first time, the
 * TV asks "Allow USB debugging?"; with "Always allow" ticked it never asks this phone again.
 */
final class Tv implements Closeable {
    static final int PORT = 5555;
    /** Long enough for someone to walk to the TV and press OK on the first connection. */
    private static final int SOCKET_TIMEOUT_MS = 60_000;
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private final Dadb dadb;

    private Tv(Dadb dadb) {
        this.dadb = dadb;
    }

    static Tv connect(Context c, String host) throws IOException {
        Dadb d = Dadb.create(host, PORT, key(c), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS);
        Tv tv = new Tv(d);
        tv.shell("echo ok");   // connect and authenticate now, not halfway through sending
        return tv;
    }

    private static synchronized AdbKeyPair key(Context c) {
        File priv = new File(c.getFilesDir(), "adbkey");
        File pub = new File(c.getFilesDir(), "adbkey.pub");
        if (!priv.isFile() || !pub.isFile()) AdbKeyPair.Companion.generate(priv, pub);
        return AdbKeyPair.read(priv, pub);
    }

    /** The command's standard output. */
    String shell(String cmd) throws IOException {
        return dadb.shell(cmd).getOutput();
    }

    void push(File f, String remotePath) throws IOException {
        dadb.push(f, remotePath, 0644, System.currentTimeMillis());
    }

    @Override
    public void close() throws IOException {
        try {
            dadb.close();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
