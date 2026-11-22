package my.glasstv.remote;

/** One TV's state in the list, in words. */
final class TvStatus {
    enum Kind { LATEST, OUTDATED, NOT_INSTALLED, UNREACHABLE, NOT_ALLOWED }

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

    /** The short coloured label in the TV list. */
    static String chip(TvStatus s) {
        switch (s.kind) {
            case LATEST: return "Terkini";
            case OUTDATED: return "Kemas kini";
            case NOT_INSTALLED: return "Belum pasang";
            case NOT_ALLOWED: return "Allow di TV";
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
            default: return new int[]{0xFFE0335F, 0xFFFDE7EE};
        }
    }

    static String label(TvStatus s) {
        switch (s.kind) {
            case LATEST: return "✅ Glass TV " + s.version + " (terkini)";
            case OUTDATED: return "⚠️ Glass TV " + s.version + " – perlu kemas kini";
            case NOT_INSTALLED: return "⭕ Glass TV belum dipasang";
            case NOT_ALLOWED: return "🔒 Tekan Always allow di TV";
            default: return "❌ Tidak dijumpai";
        }
    }
}
