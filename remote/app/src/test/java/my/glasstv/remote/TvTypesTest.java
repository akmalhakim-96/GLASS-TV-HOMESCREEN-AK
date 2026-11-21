package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TvTypesTest {
    @Test
    public void sixChoicesInOrder() {
        assertEquals(6, TvTypes.ALL.length);
        assertEquals("Android TV", TvTypes.ALL[0].label);
        assertEquals("LG (webOS)", TvTypes.ALL[5].label);
    }

    @Test
    public void samsungAndLgCannotRunGlassTv() {
        assertFalse(TvTypes.find("samsung").supported);
        assertFalse(TvTypes.find("lg").supported);
        assertTrue(TvTypes.find("google").supported);
        assertTrue(TvTypes.find("samsung").note.contains("Google TV"));
    }

    @Test
    public void detectsFromTheTv() {
        assertEquals("mi", TvTypes.detect("Xiaomi", "MiTV-AESP0", "").key);
        assertEquals("skyworth", TvTypes.detect("skyworth", "SWTV-21AE", "").key);
        assertEquals("google", TvTypes.detect("Google", "Chromecast", "com.google.android.apps.tv.launcherx/.Home").key);
        assertEquals("google", TvTypes.detect("TCL", "G10", "x\ncom.google.android.apps.tv.launcherx/.Home").key);
        assertEquals("android", TvTypes.detect("Sony", "BRAVIA 4K", "com.google.android.tvlauncher/.Main").key);
    }

    @Test
    public void unknownKeyIsNull() {
        assertNull(TvTypes.find("roku"));
    }
}
