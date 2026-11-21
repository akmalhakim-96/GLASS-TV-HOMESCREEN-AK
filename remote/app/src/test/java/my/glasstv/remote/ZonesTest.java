package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ZonesTest {
    @Test
    public void sixtyZones() {
        assertEquals(60, Zones.all().size());
    }

    @Test
    public void shahAlamIsSgr01() {
        assertTrue(Zones.find("SGR01").areas.contains("S.Alam"));
        assertEquals("Selangor", Zones.find("SGR01").state);
        assertNull(Zones.find("XXX99"));
    }

    @Test
    public void shortNameIsTheFirstArea() {
        assertEquals("Gombak", Zones.shortName(Zones.find("SGR01")));
        assertEquals("SGR01 · Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, S.Alam", Zones.find("SGR01").label());
    }
}
