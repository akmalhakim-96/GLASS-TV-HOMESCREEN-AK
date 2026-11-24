package my.glasstv.remote;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SPAKE2 over edwards25519, exactly as BoringSSL's spake25519 does it (which is what Android's
 * Wireless debugging pairing uses), including its adjustment of the password scalar to a multiple of
 * eight. Written plainly with BigInteger: a pairing does a handful of multiplications, once per TV.
 */
final class Spake2 {
    static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    /** The order of the prime-order subgroup. */
    static final BigInteger L = BigInteger.ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
    private static final BigInteger SQRT_M1 = TWO.modPow(P.subtract(BigInteger.ONE).shiftRight(2), P);
    static final BigInteger[] IDENTITY = {BigInteger.ZERO, BigInteger.ONE};
    static final BigInteger[] BASE = decode(hex("5866666666666666666666666666666666666666666666666666666666666666"));
    /** BoringSSL's M and N: the first valid points from SHA-256 of "edwards25519 point generation seed (M|N)". */
    static final BigInteger[] M = decode(hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"));
    static final BigInteger[] N = decode(hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"));

    private final boolean alice;
    private final byte[] myName, theirName;
    private BigInteger privateKey, passwordScalar;
    private byte[] passwordHash, myMsg;

    Spake2(boolean alice, byte[] myName, byte[] theirName) {
        this.alice = alice;
        this.myName = myName.clone();
        this.theirName = theirName.clone();
    }

    /** Our 32-byte message for the other side. */
    byte[] generateMessage(byte[] password, SecureRandom random) {
        byte[] r = new byte[64];
        random.nextBytes(r);
        // Reduced, then times the cofactor so the peer's small-order part is cleared later.
        privateKey = le(r).mod(L).shiftLeft(3);
        BigInteger[] pub = mul(privateKey, BASE);
        passwordHash = sha512(password);
        passwordScalar = passwordScalar(passwordHash);
        myMsg = encode(add(pub, mul(passwordScalar, alice ? M : N)));
        return myMsg.clone();
    }

    /** The shared 64-byte key; throws IllegalArgumentException on a message that is not a point. */
    byte[] processMessage(byte[] theirMsg) {
        if (myMsg == null || theirMsg.length != 32) throw new IllegalArgumentException("spake2 state");
        BigInteger[] q = add(decode(theirMsg), neg(mul(passwordScalar, alice ? N : M)));
        byte[] dh = encode(mul(privateKey, q));
        MessageDigest sha = digest("SHA-512");
        if (alice) {
            prefixed(sha, myName);
            prefixed(sha, theirName);
            prefixed(sha, myMsg);
            prefixed(sha, theirMsg);
        } else {
            prefixed(sha, theirName);
            prefixed(sha, myName);
            prefixed(sha, theirMsg);
            prefixed(sha, myMsg);
        }
        prefixed(sha, dh);
        prefixed(sha, passwordHash);
        return sha.digest();
    }

    /**
     * The password hash reduced mod L, then made a multiple of 8 by adding L, 2L and 4L where needed
     * (BoringSSL's fix for an omitted cofactor shift; peers must agree on it bit for bit).
     */
    static BigInteger passwordScalar(byte[] sha512) {
        BigInteger s = le(sha512).mod(L);
        if (s.testBit(0)) s = s.add(L);
        if (s.testBit(1)) s = s.add(L.shiftLeft(1));
        if (s.testBit(2)) s = s.add(L.shiftLeft(2));
        return s;
    }

    // ---- edwards25519, affine ----------------------------------------------------------------------

    static BigInteger[] add(BigInteger[] a, BigInteger[] b) {
        BigInteger x1 = a[0], y1 = a[1], x2 = b[0], y2 = b[1];
        BigInteger t = D.multiply(x1).multiply(x2).mod(P).multiply(y1).multiply(y2).mod(P);
        BigInteger x3 = x1.multiply(y2).add(y1.multiply(x2)).multiply(BigInteger.ONE.add(t).modInverse(P)).mod(P);
        BigInteger y3 = y1.multiply(y2).add(x1.multiply(x2)).multiply(BigInteger.ONE.subtract(t).mod(P).modInverse(P)).mod(P);
        return new BigInteger[]{x3, y3};
    }

    static BigInteger[] neg(BigInteger[] a) {
        return new BigInteger[]{P.subtract(a[0]).mod(P), a[1]};
    }

    static BigInteger[] mul(BigInteger k, BigInteger[] pt) {
        BigInteger[] r = IDENTITY;
        for (int i = k.bitLength() - 1; i >= 0; i--) {
            r = add(r, r);
            if (k.testBit(i)) r = add(r, pt);
        }
        return r;
    }

    static byte[] encode(BigInteger[] pt) {
        byte[] out = new byte[32];
        byte[] y = pt[1].toByteArray();
        for (int i = 0; i < 32 && i < y.length; i++) out[i] = y[y.length - 1 - i];
        if (pt[0].testBit(0)) out[31] |= (byte) 0x80;
        return out;
    }

    /** As BoringSSL's x25519_ge_frombytes_vartime: the y bits, then x from the curve and the sign bit. */
    static BigInteger[] decode(byte[] s) {
        if (s.length != 32) throw new IllegalArgumentException("point length");
        byte[] c = s.clone();
        boolean negative = (c[31] & 0x80) != 0;
        c[31] &= 0x7f;
        BigInteger y = le(c).mod(P);
        BigInteger yy = y.multiply(y).mod(P);
        BigInteger u = yy.subtract(BigInteger.ONE).mod(P);
        BigInteger v = D.multiply(yy).add(BigInteger.ONE).mod(P);
        BigInteger xx = u.multiply(v.modInverse(P)).mod(P);
        BigInteger x = xx.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (!x.multiply(x).mod(P).equals(xx)) {
            if (!x.multiply(x).add(xx).mod(P).equals(BigInteger.ZERO)) throw new IllegalArgumentException("not a point");
            x = x.multiply(SQRT_M1).mod(P);
        }
        if (x.testBit(0) != negative) x = P.subtract(x).mod(P);
        return new BigInteger[]{x, y};
    }

    // ---- bytes -------------------------------------------------------------------------------------

    static BigInteger le(byte[] b) {
        byte[] be = new byte[b.length];
        for (int i = 0; i < b.length; i++) be[i] = b[b.length - 1 - i];
        return new BigInteger(1, be);
    }

    private static void prefixed(MessageDigest sha, byte[] data) {
        long n = data.length;
        byte[] len = new byte[8];
        for (int i = 0; i < 8; i++) {
            len[i] = (byte) (n & 0xff);
            n >>>= 8;
        }
        sha.update(len);
        sha.update(data);
    }

    private static byte[] sha512(byte[] b) {
        return digest("SHA-512").digest(b);
    }

    private static MessageDigest digest(String name) {
        try {
            return MessageDigest.getInstance(name);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hex(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    static boolean same(BigInteger[] a, BigInteger[] b) {
        return Arrays.equals(new BigInteger[]{a[0].mod(P), a[1].mod(P)}, new BigInteger[]{b[0].mod(P), b[1].mod(P)});
    }
}
