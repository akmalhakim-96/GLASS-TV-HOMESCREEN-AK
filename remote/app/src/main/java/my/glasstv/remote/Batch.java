package my.glasstv.remote;

import java.util.ArrayList;
import java.util.List;

/** Results of one action over several TVs: "3 berjaya, 1 gagal" and a line per TV. */
final class Batch {
    private final List<String> lines = new ArrayList<>();
    private int ok, failed;

    void add(String tvName, String result) {
        boolean good = result.startsWith("Siap");
        if (good) ok++;
        else failed++;
        lines.add(good ? "✓ " + tvName : "✗ " + tvName + " — " + result);
    }

    String headline() {
        return ok + " berjaya, " + failed + " gagal";
    }

    String details() {
        return String.join("\n", lines);
    }

    boolean allOk() {
        return failed == 0;
    }
}
