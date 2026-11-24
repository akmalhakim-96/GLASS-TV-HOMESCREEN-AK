package my.glasstv.remote;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLSocket;

import dadb.AdbConnection;
import dadb.AdbMessage;
import dadb.AdbReader;
import dadb.AdbShellResponse;
import dadb.AdbShellStream;
import dadb.AdbStream;
import dadb.AdbSyncStream;
import dadb.AdbWriter;
import dadb.Dadb;
import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * ADB over Wireless debugging (Android 11+): the TV answers our hello with "STLS", both sides switch the
 * same socket to TLS 1.3, and the TV accepts us only if our key was paired. After that it is ordinary
 * ADB, so the rest of dadb (shell, push, install) runs over it unchanged.
 */
final class TlsAdb {
    static final int CMD_STLS = 0x534c5453, CMD_CNXN = 0x4e584e43;
    private static final int STLS_VERSION = 0x01000000;

    /** The TV has Wireless debugging on but this phone is not paired with it (or was unpaired). */
    static final class NotPaired extends IOException {
        NotPaired(Throwable cause) {
            super("not paired", cause);
        }
    }

    private TlsAdb() {}

    static Dadb connect(String host, int port, AdbIdentity id, int connectMs, int readMs) throws IOException {
        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(host, port), connectMs);
            raw.setSoTimeout(readMs);
            raw.setTcpNoDelay(true);
            AdbWriter w = new AdbWriter(Okio.sink(raw));
            w.writeConnect();
            AdbMessage m = new AdbReader(Okio.source(raw)).readMessage();
            if (m.getCommand() != CMD_STLS) throw new IOException("port " + port + " is not Wireless debugging");
            w.write(CMD_STLS, STLS_VERSION, 0, new byte[0], 0, 0);
        } catch (IOException | RuntimeException e) {
            closeQuietly(raw);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        SSLSocket tls;
        AdbReader reader;
        AdbMessage hello;
        try {
            tls = (SSLSocket) id.sslContext().getSocketFactory().createSocket(raw, host, port, true);
            tls.setUseClientMode(true);
            tls.setEnabledProtocols(new String[]{"TLSv1.3"});
            tls.startHandshake();
            reader = new AdbReader(Okio.source(tls.getInputStream()));
            // With TLS 1.3 the TV checks our certificate after the handshake: a TV that does not know
            // our key just closes here.
            hello = reader.readMessage();
        } catch (IOException | RuntimeException e) {
            closeQuietly(raw);
            throw new NotPaired(e);
        }
        if (hello.getCommand() != CMD_CNXN) {
            closeQuietly(raw);
            throw new IOException("unexpected reply " + Integer.toHexString(hello.getCommand()));
        }
        AdbWriter writer = new AdbWriter(Okio.sink(tls.getOutputStream()));
        Set<String> features = features(new String(hello.getPayload(), StandardCharsets.UTF_8));
        return new Connected(new AdbConnection(reader, writer, tls, features, hello.getArg0(), hello.getArg1()), host + ":" + port);
    }

    /** True when the port is ADB with Wireless debugging (it answers our hello with STLS). */
    static boolean isTlsAdb(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            new AdbWriter(Okio.sink(s)).writeConnect();
            return new AdbReader(Okio.source(s)).readMessage().getCommand() == CMD_STLS;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** "device::ro.product.name=x;...;features=shell_v2,cmd" → {shell_v2, cmd}. */
    static Set<String> features(String banner) {
        Set<String> out = new HashSet<>();
        int at = banner.indexOf("::");
        for (String kv : banner.substring(at < 0 ? 0 : at + 2).split(";")) {
            if (kv.startsWith("features=")) {
                for (String f : kv.substring(9).split(",")) if (!f.trim().isEmpty()) out.add(f.trim());
            }
        }
        return out;
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    /** dadb's commands on top of a connection we opened ourselves. */
    private static final class Connected implements Dadb {
        private final AdbConnection connection;
        private final String name;

        Connected(AdbConnection connection, String name) {
            this.connection = connection;
            this.name = name;
        }

        @Override public AdbStream open(String destination) throws IOException { return connection.open(destination); }
        @Override public boolean supportsFeature(String feature) { return connection.supportsFeature(feature); }
        @Override public void close() { connection.close(); }
        @Override public String toString() { return name; }

        @Override public AdbShellResponse shell(String command) throws IOException { return Dadb.DefaultImpls.shell(this, command); }
        @Override public AdbShellStream openShell(String command) throws IOException { return Dadb.DefaultImpls.openShell(this, command); }
        @Override public void push(File src, String remotePath, int mode, long lastModifiedMs) throws IOException { Dadb.DefaultImpls.push(this, src, remotePath, mode, lastModifiedMs); }
        @Override public void push(Source source, String remotePath, int mode, long lastModifiedMs) throws IOException { Dadb.DefaultImpls.push(this, source, remotePath, mode, lastModifiedMs); }
        @Override public void pull(File dst, String remotePath) throws IOException { Dadb.DefaultImpls.pull(this, dst, remotePath); }
        @Override public void pull(Sink sink, String remotePath) throws IOException { Dadb.DefaultImpls.pull(this, sink, remotePath); }
        @Override public AdbSyncStream openSync() throws IOException { return Dadb.DefaultImpls.openSync(this); }
        @Override public void install(File file, String... options) throws IOException { Dadb.DefaultImpls.install(this, file, options); }
        @Override public void install(Source source, long size, String... options) throws IOException { Dadb.DefaultImpls.install(this, source, size, options); }
        @Override public void installMultiple(List<? extends File> apks, String... options) throws IOException { Dadb.DefaultImpls.installMultiple(this, apks, options); }
        @Override public void uninstall(String packageName) throws IOException { Dadb.DefaultImpls.uninstall(this, packageName); }
        @Override public AdbStream execCmd(String... command) throws IOException { return Dadb.DefaultImpls.execCmd(this, command); }
        @Override public AdbStream abbExec(String... command) throws IOException { return Dadb.DefaultImpls.abbExec(this, command); }
        @Override public void root() throws IOException { Dadb.DefaultImpls.root(this); }
        @Override public void unroot() throws IOException { Dadb.DefaultImpls.unroot(this); }
        @Override public AutoCloseable tcpForward(int hostPort, int targetPort) throws InterruptedException { return Dadb.DefaultImpls.tcpForward(this, hostPort, targetPort); }
    }
}
