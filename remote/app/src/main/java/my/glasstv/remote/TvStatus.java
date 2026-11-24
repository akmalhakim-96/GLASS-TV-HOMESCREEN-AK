package my.glasstv.remote;

/** One TV's state in the list, in words. */
final class TvStatus {
    enum Kind { LATEST, OUTDATED, NOT_INSTALLED, UNREACHABLE, NOT_ALLOWED, DEBUG_OFF, NEEDS_PAIRING }

    final Kind kind;
    final int version;

    private TvStatus(Kind kind, int version) {
        this.kind = kind;
        this.version = version;
    }

    static TvStatus of(int onTv, int bundled) {
        if (onTv < 0) return new TvStatus(Kind.NOT_INSTALLED, -1);
        return new TvStatus(onTv < bundled ? Kind.OUTDATED : Kind.LATEST, onTv);
    }

    static TvStatus unreachable() { return new TvStatus(Kind.UNREACHABLE, -1); }

    static TvStatus notAllowed() { return new TvStatus(Kind.NOT_ALLOWED, -1); }

    /**
     * Why a TV could not be reached: waiting for Allow, refusing on port 5555 (it is there, debugging is
     * off), or no answer at all (switched off, another network, or a Wi-Fi that keeps devices apart).
     */
    static TvStatus fromError(Throwable t) {
        if (t instanceof Tv.AuthPending) return notAllowed();
        if (t instanceof TlsAdb.NotPaired) return new TvStatus(Kind.NEEDS_PAIRING, -1);
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.ConnectException) return new TvStatus(Kind.DEBUG_OFF, -1);
        }
        return unreachable();
    }

    /** The short coloured label in the TV list. */
    static String chip(TvStatus s) {
        switch (s.kind) {
            case LATEST: return "Terkini";
            case OUTDATED: return "Kemas kini";
            case NOT_INSTALLED: return "Belum pasang";
            case NOT_ALLOWED: return "Allow di TV";
            case DEBUG_OFF: return "Debug OFF";
            case NEEDS_PAIRING: return "Perlu Pair";
            default: return "Tiada";
        }
    }

    /** Text and background colours for that label. */
    static int[] chipColors(TvStatus s) {
        switch (s.kind) {
            case LATEST: return new int[]{0xFF0F9D6B, 0xFFE3F8EF};
            case OUTDATED: return new int[]{0xFFE8711E, 0xFFFFF1E6};
            case NOT_INSTALLED: return new int[]{0xFF7A5CFF, 0xFFF0EBFF};
            case NOT_ALLOWED: return new int[]{0xFF4F6BFF, 0xFFEEF1FF};
            case DEBUG_OFF: return new int[]{0xFFD9480F, 0xFFFFE8DD};
            case NEEDS_PAIRING: return new int[]{0xFF7A5CFF, 0xFFF0EBFF};
            default: return new int[]{0xFFE0335F, 0xFFFDE7EE};
        }
    }

    static String label(TvStatus s) {
        switch (s.kind) {
            case LATEST: return "✅ Glass TV " + s.version + " (terkini)";
            case OUTDATED: return "⚠️ Glass TV " + s.version + " – perlu kemas kini";
            case NOT_INSTALLED: return "⭕ Glass TV belum dipasang";
            case NOT_ALLOWED: return "🔒 Tekan Always allow di TV";
            case DEBUG_OFF: return "🔌 TV dijumpai, debugging OFF";
            case NEEDS_PAIRING: return "🔑 Tekan lama TV → Pair";
            default: return "❌ Tidak dijumpai";
        }
    }
}
