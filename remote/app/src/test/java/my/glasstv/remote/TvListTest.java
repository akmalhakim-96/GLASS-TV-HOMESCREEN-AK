package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TvListTest {
    @Test
    public void foundTvsAreAddedOnceWithDefaultNames() {
        TvList l = new TvList();
        l.addFound(Arrays.asList("192.168.0.12", "192.168.0.7"));
        l.addFound(Arrays.asList("192.168.0.7"));
        assertEquals(2, l.all().size());
        assertEquals("192.168.0.7", l.all().get(0).ip);          // ordered by address
        assertEquals("TV .7", l.all().get(0).name);
        assertTrue(l.all().get(0).selected);                       // new TVs start ticked
    }

    @Test
    public void renameRemoveAndSelect() {
        TvList l = new TvList();
        l.addFound(Arrays.asList("192.168.0.7", "192.168.0.12"));
        l.rename("192.168.0.7", "Ruang Tamu");
        l.setSelected("192.168.0.12", false);
        assertEquals("Ruang Tamu", l.find("192.168.0.7").name);
        assertEquals(List.of("192.168.0.7"), l.selectedIps());
        l.remove("192.168.0.7");
        assertEquals(1, l.all().size());
        l.selectAll(true);
        assertEquals(List.of("192.168.0.12"), l.selectedIps());
    }

    @Test
    public void savesAndLoads() {
        TvList l = new TvList();
        l.addFound(Arrays.asList("192.168.0.7"));
        l.rename("192.168.0.7", "Bilik \"1\"");
        l.setSelected("192.168.0.7", false);
        TvList back = TvList.fromJson(l.toJson());
        assertEquals("Bilik \"1\"", back.find("192.168.0.7").name);
        assertFalse(back.find("192.168.0.7").selected);
        assertEquals(0, TvList.fromJson("rosak").all().size());
        assertEquals(0, TvList.fromJson(null).all().size());
    }

    @Test
    public void statusWords() {
        assertEquals("✅ Glass TV 17 (terkini)", TvStatus.label(TvStatus.of(17, 17)));
        assertEquals("⚠️ Glass TV 12 – perlu kemas kini", TvStatus.label(TvStatus.of(12, 17)));
        assertEquals("⭕ Glass TV belum dipasang", TvStatus.label(TvStatus.of(-1, 17)));
        assertEquals("❌ Tidak dijumpai", TvStatus.label(TvStatus.unreachable()));
        assertEquals("🔒 Tekan Always allow di TV", TvStatus.label(TvStatus.notAllowed()));
    }

    @Test
    public void batchSummary() {
        Batch b = new Batch();
        b.add("Ruang Tamu", "Siap ✓");
        b.add("Bilik 1", "TV tidak dijumpai. Pastikan TV hidup…");
        assertEquals("1 berjaya, 1 gagal", b.headline());
        assertEquals("✓ Ruang Tamu\n✗ Bilik 1 — TV tidak dijumpai. Pastikan TV hidup…", b.details());
    }

    @Test
    public void chipWords() {
        assertEquals("Terkini", TvStatus.chip(TvStatus.of(17, 17)));
        assertEquals("Kemas kini", TvStatus.chip(TvStatus.of(12, 17)));
        assertEquals("Belum pasang", TvStatus.chip(TvStatus.of(-1, 17)));
        assertEquals("Allow di TV", TvStatus.chip(TvStatus.notAllowed()));
        assertEquals("Tiada", TvStatus.chip(TvStatus.unreachable()));
    }
}
