package my.glasstv.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * This phone's ADB key (the same files the 5555 connection uses) in the forms Wireless debugging
 * needs: a self-signed certificate for TLS, and the "base64 name" text the TV stores when paired.
 * The TV checks only that the certificate's key is one it paired with; the TV's own certificate is
 * self-signed too, so any is accepted (the pairing code is what proves who is who).
 */
final class AdbIdentity {
    final PrivateKey privateKey;
    final RSAPublicKey publicKey;
    final X509Certificate certificate;
    final byte[] publicKeyText;
    private SSLContext ssl;

    private AdbIdentity(PrivateKey privateKey, RSAPublicKey publicKey, X509Certificate certificate, byte[] text) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.certificate = certificate;
        this.publicKeyText = text;
    }

    static AdbIdentity load(File privatePem, File publicText) throws IOException {
        try {
            String pem = new String(Files.readAllBytes(privatePem.toPath()), StandardCharsets.US_ASCII);
            String b64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(b64)));
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) priv;
            RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return new AdbIdentity(priv, pub, selfSigned(priv, pub), Files.readAllBytes(publicText.toPath()));
        } catch (GeneralSecurityException | ClassCastException | IllegalArgumentException e) {
            throw new IOException("kunci ADB telefon rosak: " + e, e);
        }
    }

    /** TLS 1.3 with this certificate as ours, and any TV certificate accepted. */
    synchronized SSLContext sslContext() throws IOException {
        if (ssl != null) return ssl;
        try {
            SSLContext c = SSLContext.getInstance("TLSv1.3");
            c.init(new KeyManager[]{keyManager()}, new TrustManager[]{trustAll()}, new SecureRandom());
            return ssl = c;
        } catch (GeneralSecurityException e) {
            throw new IOException("TLS 1.3 tiada pada telefon ini (perlu Android 10 ke atas)", e);
        }
    }

    private KeyManager keyManager() {
        return new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String keyType, Principal[] issuers) { return new String[]{"adb"}; }
            @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) { return "adb"; }
            @Override public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, javax.net.ssl.SSLEngine e) { return "adb"; }
            @Override public String[] getServerAliases(String keyType, Principal[] issuers) { return null; }
            @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) { return null; }
            @Override public X509Certificate[] getCertificateChain(String alias) { return new X509Certificate[]{certificate}; }
            @Override public PrivateKey getPrivateKey(String alias) { return privateKey; }
        };
    }

    private static X509TrustManager trustAll() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    // ---- a minimal self-signed X.509 v1 certificate, built by hand (Android has no API for it) -------

    private static final byte[] SHA256_RSA = {0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b};
    private static final byte[] COMMON_NAME = {0x06, 0x03, 0x55, 0x04, 0x03};

    static X509Certificate selfSigned(PrivateKey priv, RSAPublicKey pub) throws GeneralSecurityException {
        byte[] alg = der(0x30, SHA256_RSA, new byte[]{0x05, 0x00});
        byte[] name = der(0x30, der(0x31, der(0x30, COMMON_NAME, der(0x0c, "adb".getBytes(StandardCharsets.US_ASCII)))));
        // Fixed dates: TV clocks are often wrong, and the TV does not check them.
        byte[] validity = der(0x30, der(0x17, "200101000000Z".getBytes(StandardCharsets.US_ASCII)),
                der(0x17, "491231235959Z".getBytes(StandardCharsets.US_ASCII)));
        byte[] tbs = der(0x30, new byte[]{0x02, 0x01, 0x01}, alg, name, validity, name, pub.getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(priv);
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        byte[] cert = der(0x30, tbs, alg, der(0x03, bits));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert));
    }

    static byte[] der(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p, 0, p.length);
        int n = body.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (n < 0x80) {
            out.write(n);
        } else if (n < 0x100) {
            out.write(0x81);
            out.write(n);
        } else {
            out.write(0x82);
            out.write(n >> 8);
            out.write(n & 0xff);
        }
        byte[] b = body.toByteArray();
        out.write(b, 0, b.length);
        return out.toByteArray();
    }
}
