package my.glasstv.remote;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The encrypted part of ADB pairing: AES-128-GCM with a key from HKDF-SHA256 of the SPAKE2 key, and a
 * 12-byte nonce that is a little-endian counter, one counter for each direction. Also the packet
 * framing: version 1, type, big-endian payload length.
 */
final class PairingCipher {
    static final byte SPAKE2_MSG = 0, PEER_INFO = 1;
    static final int PEER_INFO_SIZE = 8192;
    private static final byte[] INFO = "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private long encCounter, decCounter;

    PairingCipher(byte[] spakeKey) {
        key = new SecretKeySpec(hkdf(spakeKey, INFO, 16), "AES");
    }

    byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce(encCounter++)));
        return c.doFinal(plain);
    }

    /** Throws (AEADBadTagException) when the other side used another code. */
    byte[] decrypt(byte[] sealed) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce(decCounter++)));
        return c.doFinal(sealed);
    }

    static byte[] nonce(long n) {
        byte[] out = new byte[12];
        for (int i = 0; i < 8; i++) out[i] = (byte) (n >>> (8 * i));
        return out;
    }

    /** RFC 5869 HKDF-SHA256 with no salt (a block of zeros). */
    static byte[] hkdf(byte[] ikm, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] out = new byte[length], t = new byte[0];
            for (int done = 0, i = 1; done < length; i++) {
                mac.update(t);
                mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int n = Math.min(t.length, length - done);
                System.arraycopy(t, 0, out, done, n);
                done += n;
            }
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Our public key as the TV wants it: type 0, then "base64 name", zero-padded to 8192 bytes. */
    static byte[] peerInfo(byte[] publicKeyText) {
        byte[] out = new byte[PEER_INFO_SIZE];
        int n = publicKeyText.length;
        while (n > 0 && publicKeyText[n - 1] == 0) n--;
        System.arraycopy(publicKeyText, 0, out, 1, Math.min(n, PEER_INFO_SIZE - 2));
        return out;
    }

    static void writePacket(DataOutputStream out, byte type, byte[] payload) throws IOException {
        out.writeByte(1);
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    static byte[] readPacket(DataInputStream in, byte expectedType) throws IOException {
        int version = in.readUnsignedByte();
        int type = in.readUnsignedByte();
        int size = in.readInt();
        if (version != 1 || type != expectedType || size <= 0 || size > 2 * PEER_INFO_SIZE) {
            throw new IOException("pairing packet v" + version + " type " + type + " size " + size);
        }
        byte[] payload = new byte[size];
        in.readFully(payload);
        return payload;
    }
}
