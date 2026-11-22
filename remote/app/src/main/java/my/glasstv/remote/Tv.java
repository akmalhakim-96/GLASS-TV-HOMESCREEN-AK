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
    /** Null until asked: whether this TV refuses writes into Glass TV's folder (then the inbox is used). */
    private Boolean inbox;

    private Tv(Dadb dadb) {
        this.dadb = dadb;
    }

    /** The TV answers on port 5555 but the handshake did not finish: its Allow dialog is waiting. */
    static final class AuthPending extends IOException {
        AuthPending(Throwable cause) {
            super("adb auth pending", cause);
        }
    }

    static Tv connect(Context c, String host) throws IOException {
        // A plain TCP probe first, so "not found" and "waiting for Allow" are told apart.
        try (java.net.Socket probe = new java.net.Socket()) {
            probe.connect(new java.net.InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
        }
        Dadb d = Dadb.create(host, PORT, key(c), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS);
        Tv tv = new Tv(d);
        try {
            tv.shell("echo ok");   // connect and authenticate now, not halfway through sending
        } catch (IOException | RuntimeException e) {
            try {
                d.close();
            } catch (Exception ignored) {
            }
            throw new AuthPending(e);
        }
        return tv;
    }

    private static synchronized AdbKeyPair key(Context c) {
        File priv = new File(c.getFilesDir(), "adbkey");
        File pub = new File(c.getFilesDir(), "adbkey.pub");
        if (!priv.isFile() || !pub.isFile()) AdbKeyPair.Companion.generate(priv, pub);
        return AdbKeyPair.read(priv, pub);
    }

    /** Whether files go through the shared inbox (/sdcard/GlassTV) on this TV. */
    boolean usesInbox() throws IOException {
        if (inbox == null) {
            shell(TvCommands.makeDir() + " 2>/dev/null");
            inbox = !shell(TvCommands.CAN_WRITE_DIR).contains("GLASSTV_OK");
        }
        return inbox;
    }

    String readConfig() throws IOException {
        return shell(TvCommands.readConfig(usesInbox()));
    }

    /** The command's standard output. */
    String shell(String cmd) throws IOException {
        return dadb.shell(cmd).getOutput();
    }

    /**
     * Straight into place where the TV allows it; otherwise (Android 11+ refuses pushes into another
     * app's folder) into /data/local/tmp and copied into place by the TV's own shell.
     */
    void push(File f, String remotePath) throws IOException {
        if (TvCommands.viaInbox(remotePath) && usesInbox()) {
            String in = TvCommands.inboxPath(remotePath);
            shell("mkdir -p '" + in.substring(0, in.lastIndexOf('/')) + "'");
            remotePath = in;
        }
        try {
            dadb.push(f, remotePath, 0644, System.currentTimeMillis());
            return;
        } catch (IOException direct) {
            String tmp = TvCommands.tmpPath(f.getName());
            dadb.push(f, tmp, 0644, System.currentTimeMillis());
            String out = shell("(" + TvCommands.copyInto(tmp, remotePath) + ") 2>&1");
            if (!out.contains("GLASSTV_OK")) {
                shell("rm -f '" + tmp + "'");
                throw new IOException("TV tidak benarkan fail disimpan (" + out.trim() + ")");
            }
        }
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
