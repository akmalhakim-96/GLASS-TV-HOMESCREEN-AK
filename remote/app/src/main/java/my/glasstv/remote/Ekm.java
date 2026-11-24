package my.glasstv.remote;

import java.io.IOException;
import java.lang.reflect.Method;

import javax.net.ssl.SSLSocket;

/** TLS keying material from Android's own TLS (Conscrypt): the public API where there is one. */
final class Ekm implements AdbPairing.Exporter {
    @Override
    public byte[] export(SSLSocket socket, String label, int length) throws IOException {
        for (String cls : new String[]{"android.net.ssl.SSLSockets", "com.android.org.conscrypt.Conscrypt"}) {
            try {
                Method m = Class.forName(cls).getMethod("exportKeyingMaterial", SSLSocket.class, String.class, byte[].class, int.class);
                byte[] out = (byte[]) m.invoke(null, socket, label, null, length);
                if (out != null) return out;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        throw new IOException("Telefon ini tidak menyokong pairing (perlu Android 10 ke atas).");
    }
}
