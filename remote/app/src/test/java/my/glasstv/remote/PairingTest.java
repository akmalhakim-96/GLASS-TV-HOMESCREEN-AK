package my.glasstv.remote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public class PairingTest {
    private static final byte[] CLIENT = "adb pair client\u0000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER = "adb pair server\u0000".getBytes(StandardCharsets.UTF_8);

    @Test public void curvePointsAreTheReferenceOnes() {
        // The base point and BoringSSL's M and N decode and encode back unchanged, and lie on the curve
        // with the order the protocol expects.
        for (BigInteger[] p : new BigInteger[][]{Spake2.BASE, Spake2.M, Spake2.N}) {
            assertArrayEquals(Spake2.encode(p), Spake2.encode(Spake2.decode(Spake2.encode(p))));
        }
        assertTrue(Spake2.same(Spake2.IDENTITY, Spake2.mul(Spake2.L, Spake2.BASE)));
        assertEquals("5866666666666666666666666666666666666666666666666666666666666666", hex(Spake2.encode(Spake2.BASE)));
        // BASE has x = 15112221349535400772501151409588531511454012693041857206046113283949847762202.
        assertEquals(new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"), Spake2.BASE[0]);
    }

    @Test public void mAndNComeFromTheirSeeds() throws Exception {
        assertEquals(hex(Spake2.encode(Spake2.M)), genpoint("edwards25519 point generation seed (M)"));
        assertEquals(hex(Spake2.encode(Spake2.N)), genpoint("edwards25519 point generation seed (N)"));
    }

    @Test public void passwordScalarIsAMultipleOfEightAndTheSameModL() throws Exception {
        for (int i = 0; i < 50; i++) {
            byte[] h = MessageDigest.getInstance("SHA-512").digest(("code" + i).getBytes(StandardCharsets.UTF_8));
            BigInteger s = Spake2.passwordScalar(h);
            assertEquals(0, s.mod(BigInteger.valueOf(8)).intValue());
            assertEquals(Spake2.le(h).mod(Spake2.L), s.mod(Spake2.L));
            assertTrue(s.bitLength() <= 256);
        }
    }

    @Test public void bothSidesAgreeOnlyWithTheSameCode() {
        byte[] pw = ("482913" + "x".repeat(64)).getBytes(StandardCharsets.UTF_8);
        Spake2 alice = new Spake2(true, CLIENT, SERVER), bob = new Spake2(false, SERVER, CLIENT);
        byte[] a = alice.generateMessage(pw, new SecureRandom()), b = bob.generateMessage(pw, new SecureRandom());
        assertArrayEquals(alice.processMessage(b), bob.processMessage(a));
        assertEquals(64, alice.processMessage(b).length);

        Spake2 alice2 = new Spake2(true, CLIENT, SERVER), bob2 = new Spake2(false, SERVER, CLIENT);
        byte[] a2 = alice2.generateMessage("111111".getBytes(StandardCharsets.UTF_8), new SecureRandom());
        byte[] b2 = bob2.generateMessage("111112".getBytes(StandardCharsets.UTF_8), new SecureRandom());
        assertFalse(Arrays.equals(alice2.processMessage(b2), bob2.processMessage(a2)));
    }

    @Test public void hkdfMatchesRfc5869CaseThree() {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
                hex(PairingCipher.hkdf(ikm, new byte[0], 42)));
    }

    @Test public void cipherCountsEachDirectionAndRejectsAnotherKey() throws Exception {
        byte[] k = new byte[64];
        new SecureRandom().nextBytes(k);
        PairingCipher a = new PairingCipher(k), b = new PairingCipher(k);
        for (int i = 0; i < 3; i++) {
            byte[] msg = ("hello " + i).getBytes(StandardCharsets.UTF_8);
            byte[] sealed = a.encrypt(msg);
            assertEquals(msg.length + 16, sealed.length);
            assertArrayEquals(msg, b.decrypt(sealed));
        }
        assertEquals("010000000000000000000000", hex(PairingCipher.nonce(1)));
        byte[] other = k.clone();
        other[0] ^= 1;
        try {
            new PairingCipher(other).decrypt(new PairingCipher(k).encrypt(new byte[8]));
            fail();
        } catch (java.security.GeneralSecurityException expected) {
        }
    }

    @Test public void packetsAndPeerInfo() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PairingCipher.writePacket(new DataOutputStream(bytes), PairingCipher.SPAKE2_MSG, new byte[32]);
        byte[] raw = bytes.toByteArray();
        assertEquals("010000000020", hex(Arrays.copyOf(raw, 6)));
        assertEquals(32, PairingCipher.readPacket(new DataInputStream(new ByteArrayInputStream(raw)), PairingCipher.SPAKE2_MSG).length);
        try {
            PairingCipher.readPacket(new DataInputStream(new ByteArrayInputStream(raw)), PairingCipher.PEER_INFO);
            fail();
        } catch (IOException expected) {
        }
        byte[] info = PairingCipher.peerInfo("QAAAAabc unknown@unknown\u0000".getBytes(StandardCharsets.UTF_8));
        assertEquals(8192, info.length);
        assertEquals(0, info[0]);
        assertEquals("QAAAAabc unknown@unknown", new String(info, 1, 24, StandardCharsets.UTF_8));
        assertEquals(0, info[25]);
    }

    @Test public void identityFromThePhonesKeyFiles() throws Exception {
        File dir = Files.createTempDirectory("adbkey").toFile();
        File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
        dadb.AdbKeyPair.Companion.generate(priv, pub);
        AdbIdentity id = AdbIdentity.load(priv, pub);
        id.certificate.verify(id.publicKey);
        assertArrayEquals(id.publicKey.getEncoded(), id.certificate.getPublicKey().getEncoded());
        assertArrayEquals(Files.readAllBytes(pub.toPath()), id.publicKeyText);
        assertEquals("TLSv1.3", id.sslContext().getProtocol());
    }

    @Test public void statusAndMessagesForPairing() {
        TlsAdb.NotPaired np = new TlsAdb.NotPaired(new javax.net.ssl.SSLException("closed"));
        assertEquals("Perlu Pair", TvStatus.chip(TvStatus.fromError(np)));
        assertEquals(Messages.PAIR, Messages.forError(np));
        assertEquals(Messages.WRONG_CODE, Messages.forError(new AdbPairing.WrongCode(new java.io.EOFException())));
        // Still refused everywhere: debugging really is off.
        assertEquals("Debug OFF", TvStatus.chip(TvStatus.fromError(new java.net.ConnectException("refused"))));
    }

    /** BoringSSL's genpoint(): hash the seed until the bytes decode as a point. */
    private static String genpoint(String seed) throws Exception {
        byte[] v = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.US_ASCII));
        while (true) {
            try {
                Spake2.decode(v);
                return hex(v);
            } catch (IllegalArgumentException e) {
                v = MessageDigest.getInstance("SHA-256").digest(v);
            }
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }
}
