package my.glasstv.remote;

/** A short, copyable report of why the install was rolled back, for the owner to paste to support. */
final class Diagnostics {
    static final int LOG_LINES = 40;

    private Diagnostics() {}

    static String report(String reason, String health, String log) {
        String h = health == null || health.trim().isEmpty() ? "(tiada)" : health.trim();
        String l = log == null || log.trim().isEmpty() ? "(tiada)" : tail(log.trim(), LOG_LINES);
        return "Sebab: " + reason + "\nSemakan: " + h + "\nLog:\n" + l;
    }

    private static String tail(String s, int n) {
        String[] lines = s.split("\n");
        if (lines.length <= n) return s;
        StringBuilder b = new StringBuilder();
        for (int i = lines.length - n; i < lines.length; i++) {
            if (b.length() > 0) b.append('\n');
            b.append(lines[i]);
        }
        return b.toString();
    }
}
