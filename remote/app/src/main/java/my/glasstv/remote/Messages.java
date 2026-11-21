package my.glasstv.remote;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** What went wrong, in Malay, for the line under the send button. */
final class Messages {
    static final String NOT_FOUND = "TV tidak dijumpai. Pastikan TV hidup, sama Wi-Fi, dan USB debugging ON.";
    static final String ALLOW = "Di TV, tanda Always allow dan tekan OK, kemudian cuba lagi.";
    static final String DROPPED = "Sambungan ke TV terputus. Cuba lagi.";
    static final String NO_WIFI = "Sambung telefon ke Wi-Fi rumah dahulu.";

    private Messages() {}

    static String forError(Throwable t) {
        if (t instanceof Tv.AuthPending) return ALLOW;   // the TV answered; its Allow dialog is waiting
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ConnectException || c instanceof SocketTimeoutException
                    || c instanceof NoRouteToHostException || c instanceof UnknownHostException) return NOT_FOUND;
            String m = c.getMessage();
            if (m != null && m.toLowerCase(java.util.Locale.ROOT).contains("auth")) return ALLOW;
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.SocketException || c instanceof java.io.EOFException) return DROPPED;
        }
        return "Gagal: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
    }
}
