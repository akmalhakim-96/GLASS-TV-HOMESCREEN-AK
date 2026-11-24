package my.glasstv.remote;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.net.ssl.SSLSocket;

/**
 * "Pair device with pairing code" from Android's Wireless debugging screen: TLS 1.3 to the pairing port,
 * SPAKE2 with the 6-digit code (bound to that TLS session), then each side sends its public key
 * encrypted with the shared key. A wrong code means the TV cannot read ours and hangs up.
 */
final class AdbPairing {
    static final String EXPORT_LABEL = "adb-label\u0000";
    private static final byte[] CLIENT_NAME = "adb pair client\u0000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_NAME = "adb pair server\u0000".getBytes(StandardCharsets.UTF_8);

    /** The code did not match the one on the TV (or the pairing dialog was closed). */
    static final class WrongCode extends IOException {
        WrongCode(Throwable cause) {
            super("wrong pairing code", cause);
        }
    }

    /** The TLS keying material both sides mix into the code; Android's own TLS provides it. */
    interface Exporter {
        byte[] export(SSLSocket socket, String label, int length) throws IOException;
    }

    private AdbPairing() {}

    static boolean validCode(String code) {
        return code != null && code.trim().matches("\\d{6}");
    }

    static void pair(String host, int port, String code, AdbIdentity id, Exporter exporter) throws IOException {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), 5_000);
        raw.setSoTimeout(15_000);
        raw.setTcpNoDelay(true);
        try (SSLSocket tls = (SSLSocket) id.sslContext().getSocketFactory().createSocket(raw, host, port, true)) {
            tls.setUseClientMode(true);
            tls.setEnabledProtocols(new String[]{"TLSv1.3"});
            tls.startHandshake();
            byte[] digits = code.trim().getBytes(StandardCharsets.UTF_8);
            byte[] ekm = exporter.export(tls, EXPORT_LABEL, 64);
            byte[] password = new byte[digits.length + ekm.length];
            System.arraycopy(digits, 0, password, 0, digits.length);
            System.arraycopy(ekm, 0, password, digits.length, ekm.length);

            DataOutputStream out = new DataOutputStream(tls.getOutputStream());
            DataInputStream in = new DataInputStream(tls.getInputStream());
            Spake2 spake = new Spake2(true, CLIENT_NAME, SERVER_NAME);
            PairingCipher.writePacket(out, PairingCipher.SPAKE2_MSG, spake.generateMessage(password, new SecureRandom()));
            byte[] key;
            try {
                key = spake.processMessage(PairingCipher.readPacket(in, PairingCipher.SPAKE2_MSG));
            } catch (IllegalArgumentException e) {
                throw new IOException("pairing: bad message from TV", e);
            }
            PairingCipher cipher = new PairingCipher(key);
            try {
                PairingCipher.writePacket(out, PairingCipher.PEER_INFO, cipher.encrypt(PairingCipher.peerInfo(id.publicKeyText)));
                byte[] theirs = cipher.decrypt(PairingCipher.readPacket(in, PairingCipher.PEER_INFO));
                if (theirs.length != PairingCipher.PEER_INFO_SIZE) throw new IOException("pairing: peer info size " + theirs.length);
            } catch (IOException | GeneralSecurityException e) {
                throw new WrongCode(e);
            }
        }
    }
}
