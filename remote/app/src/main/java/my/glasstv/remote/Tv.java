package my.glasstv.remote;

import android.content.Context;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import dadb.AdbKeyPair;
import dadb.Dadb;

/**
 * One ADB-over-Wi-Fi connection to the TV, with this phone's own key: port 5555 where the TV has it
 * (the first time the TV asks "Allow USB debugging?"; with "Always allow" ticked it never asks again),
 * otherwise Android 11+ Wireless debugging on its own port, over TLS, once this phone is paired.
 */
final class Tv implements Closeable {
    static final int PORT = 5555;
    /** Long enough for someone to walk to the TV and press OK on the first connection. */
    private static final int SOCKET_TIMEOUT_MS = 60_000;
    private static final int CONNECT_TIMEOUT_MS = 3_000;   // home Wi-Fi: an answer comes fast or not at all

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
        } catch (java.net.ConnectException refused) {
            // Nothing on 5555: Android 11+ keeps Wireless debugging on a port of its own, with TLS.
            Tv tv = connectWireless(c, host);
            if (tv != null) return tv;
            throw refused;
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

    /** Null when the TV has no Wireless debugging port open; TlsAdb.NotPaired when it needs pairing. */
    private static Tv connectWireless(Context c, String host) throws IOException {
        int port = WirelessPorts.find(c, host);
        if (port < 0) return null;
        Dadb d = TlsAdb.connect(host, port, identity(c), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS);
        WirelessPorts.remember(c, host, port);
        Tv tv = new Tv(d);
        try {
            tv.shell("echo ok");
        } catch (IOException | RuntimeException e) {
            tv.close();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        return tv;
    }

    /**
     * "Pair device with pairing code" on the TV: its 6-digit code, and the pairing port shown with it
     * (found by itself when the Wi-Fi allows; pass -1 for that).
     */
    static void pair(Context c, String host, int pairingPort, String code) throws IOException {
        int port = pairingPort > 0 ? pairingPort : Mdns.findPort(c, Mdns.PAIRING, host, 4_000);
        if (port <= 0) throw new IOException("PAIRING_PORT");
        AdbPairing.pair(host, port, code, identity(c), new Ekm());
    }

    private static AdbIdentity identity;

    private static synchronized AdbIdentity identity(Context c) throws IOException {
        if (identity == null) {
            key(c);
            identity = AdbIdentity.load(new File(c.getFilesDir(), "adbkey"), new File(c.getFilesDir(), "adbkey.pub"));
        }
        return identity;
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
