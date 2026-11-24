package my.glasstv.remote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dadb.AdbReader;
import dadb.AdbWriter;
import dadb.Dadb;
import okio.Okio;

/** A pretend TV on localhost, with real TLS 1.3, standing in for Wireless debugging. */
public class TlsAdbTest {

    private static AdbIdentity newIdentity() throws Exception {
        File dir = Files.createTempDirectory("adbkey").toFile();
        File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
        dadb.AdbKeyPair.Companion.generate(priv, pub);
        return AdbIdentity.load(priv, pub);
    }

    /** The TV's side: its own certificate, and only the given phone key let in (null: any). */
    private static SSLContext tvContext(AdbIdentity tv, AdbIdentity paired) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("tv", tv.privateKey, "x".toCharArray(), new Certificate[]{tv.certificate});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "x".toCharArray());
        X509TrustManager tm = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (paired != null && !Arrays.equals(chain[0].getPublicKey().getEncoded(), paired.publicKey.getEncoded())) {
                    throw new CertificateException("unknown key");
                }
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext c = SSLContext.getInstance("TLSv1.3");
        c.init(kmf.getKeyManagers(), new TrustManager[]{tm}, new SecureRandom());
        return c;
    }

    private static SSLSocket serverTls(SSLContext c, Socket s) throws Exception {
        SSLSocket t = (SSLSocket) c.getSocketFactory().createSocket(s, null, s.getPort(), true);
        t.setUseClientMode(false);
        t.setNeedClientAuth(true);
        t.setEnabledProtocols(new String[]{"TLSv1.3"});
        return t;
    }

    /** Wireless debugging's connect port: hello → STLS → TLS → the TV's hello. */
    private static ServerSocket fakeTv(SSLContext tvTls) throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    serve(tvTls, s);
                } catch (Exception ignored) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }

    private static void serve(SSLContext tvTls, Socket s) throws Exception {
        {
            {
                AdbReader r = new AdbReader(Okio.source(s));
                AdbWriter w = new AdbWriter(Okio.sink(s));
                if (r.readMessage().getCommand() != TlsAdb.CMD_CNXN) return;
                w.write(TlsAdb.CMD_STLS, 0x01000000, 0, new byte[0], 0, 0);
                if (r.readMessage().getCommand() != TlsAdb.CMD_STLS) return;
                SSLSocket tls = serverTls(tvTls, s);
                tls.startHandshake();
                byte[] banner = "device::ro.product.name=tv;features=shell_v2,cmd".getBytes(StandardCharsets.UTF_8);
                new AdbWriter(Okio.sink(tls.getOutputStream())).write(TlsAdb.CMD_CNXN, 0x01000001, 256 * 1024, banner, 0, banner.length);
                Thread.sleep(500);
            }
        }
    }

    @Test public void pairedPhoneConnects() throws Exception {
        AdbIdentity phone = newIdentity(), tv = newIdentity();
        try (ServerSocket server = fakeTv(tvContext(tv, phone));
             Dadb d = TlsAdb.connect("127.0.0.1", server.getLocalPort(), phone, 3_000, 5_000)) {
            assertTrue(d.supportsFeature("cmd"));
            assertTrue(d.supportsFeature("shell_v2"));
        }
    }

    @Test public void unpairedPhoneIsToldToPair() throws Exception {
        AdbIdentity phone = newIdentity(), other = newIdentity(), tv = newIdentity();
        try (ServerSocket server = fakeTv(tvContext(tv, other))) {
            TlsAdb.connect("127.0.0.1", server.getLocalPort(), phone, 3_000, 5_000);
            fail();
        } catch (TlsAdb.NotPaired expected) {
        }
    }

    @Test public void findsTheWirelessDebuggingPort() throws Exception {
        AdbIdentity tv = newIdentity();
        try (ServerSocket adb = fakeTv(tvContext(tv, null)); ServerSocket other = new ServerSocket(0)) {
            assertFalse(TlsAdb.isTlsAdb("127.0.0.1", other.getLocalPort(), 500));
            int p = adb.getLocalPort();
            assertEquals(p, AdbPorts.scan("127.0.0.1", p - 20, p + 20, 8, 200));
        }
    }

    @Test public void featuresFromTheBanner() {
        assertEquals(new java.util.HashSet<>(Arrays.asList("shell_v2", "cmd")),
                TlsAdb.features("device::ro.product.name=x;ro.product.model=y;features=shell_v2,cmd"));
        assertTrue(TlsAdb.features("device::").isEmpty());
    }

    // ---- pairing ------------------------------------------------------------------------------------

    private static final byte[] EKM = new byte[64];
    static {
        Arrays.fill(EKM, (byte) 7);
    }

    /** Wireless debugging's pairing port, run by a pretend TV that shows the given code. */
    private static ServerSocket fakePairingTv(SSLContext tvTls, String code, AtomicReference<String> gotKey) throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try (Socket s = server.accept(); SSLSocket tls = serverTls(tvTls, s)) {
                tls.startHandshake();
                DataInputStream in = new DataInputStream(tls.getInputStream());
                DataOutputStream out = new DataOutputStream(tls.getOutputStream());
                byte[] pw = new byte[6 + 64];
                System.arraycopy(code.getBytes(StandardCharsets.UTF_8), 0, pw, 0, 6);
                System.arraycopy(EKM, 0, pw, 6, 64);
                Spake2 bob = new Spake2(false, "adb pair server\u0000".getBytes(StandardCharsets.UTF_8),
                        "adb pair client\u0000".getBytes(StandardCharsets.UTF_8));
                byte[] theirMsg = PairingCipher.readPacket(in, PairingCipher.SPAKE2_MSG);
                PairingCipher.writePacket(out, PairingCipher.SPAKE2_MSG, bob.generateMessage(pw, new SecureRandom()));
                PairingCipher cipher = new PairingCipher(bob.processMessage(theirMsg));
                byte[] info = cipher.decrypt(PairingCipher.readPacket(in, PairingCipher.PEER_INFO));   // wrong code: throws, TV hangs up
                int n = 1;
                while (info[n] != 0) n++;
                gotKey.set(new String(info, 1, n - 1, StandardCharsets.UTF_8));
                byte[] guid = new byte[PairingCipher.PEER_INFO_SIZE];
                guid[0] = 1;
                PairingCipher.writePacket(out, PairingCipher.PEER_INFO, cipher.encrypt(guid));
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }

    @Test public void pairsWithTheRightCode() throws Exception {
        AdbIdentity phone = newIdentity(), tv = newIdentity();
        AtomicReference<String> got = new AtomicReference<>();
        try (ServerSocket server = fakePairingTv(tvContext(tv, null), "482913", got)) {
            AdbPairing.pair("127.0.0.1", server.getLocalPort(), "482913", phone, (s, label, len) -> {
                assertEquals("adb-label\u0000", label);
                return EKM.clone();
            });
        }
        assertArrayEquals(phone.publicKeyText, got.get().getBytes(StandardCharsets.UTF_8));
    }

    @Test public void wrongCodeIsReportedAsSuch() throws Exception {
        AdbIdentity phone = newIdentity(), tv = newIdentity();
        try (ServerSocket server = fakePairingTv(tvContext(tv, null), "482913", new AtomicReference<>())) {
            AdbPairing.pair("127.0.0.1", server.getLocalPort(), "111111", phone, (s, label, len) -> EKM.clone());
            fail();
        } catch (AdbPairing.WrongCode expected) {
        }
    }

    @Test public void codeMustBeSixDigits() {
        assertTrue(AdbPairing.validCode("482913"));
        assertTrue(AdbPairing.validCode(" 482913 "));
        assertFalse(AdbPairing.validCode("48291"));
        assertFalse(AdbPairing.validCode("48a913"));
        assertFalse(AdbPairing.validCode(null));
    }
}
