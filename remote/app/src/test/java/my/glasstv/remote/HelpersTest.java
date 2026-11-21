package my.glasstv.remote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Subnet, TvApps, PhotoMath, TvCommands and Messages: the plain helpers. */
public class HelpersTest {
    @Test
    public void wifiIntToIp() {
        assertEquals("192.168.1.23", Subnet.ipFromWifiInt(0x1701A8C0));
        assertNull(Subnet.ipFromWifiInt(0));
    }

    @Test
    public void hostsSkipSelf() {
        List<String> h = Subnet.hosts24("192.168.1.23");
        assertEquals(253, h.size());
        assertFalse(h.contains("192.168.1.23"));
        assertTrue(h.contains("192.168.1.1"));
        assertFalse(h.contains("192.168.1.0"));
        assertFalse(h.contains("192.168.1.255"));
    }

    @Test
    public void noWifiNoHosts() {
        assertTrue(Subnet.hosts24(null).isEmpty());
        assertTrue(Subnet.hosts24("not-an-ip").isEmpty());
    }

    @Test
    public void parsesTvApps() {
        assertEquals(List.of("com.netflix.ninja", "com.google.android.youtube.tv"),
                TvApps.parse("com.netflix.ninja/.MainActivity\ncom.gathin.tvhome/.MainActivity\n"
                        + "com.google.android.youtube.tv/x.Shell\ncom.netflix.ninja/.Other\n\n"));
    }

    @Test
    public void friendlyNames() {
        assertEquals("Netflix", TvApps.label("com.netflix.ninja"));
        assertEquals("Astro GO", TvApps.label("com.astro.astro"));
        assertEquals("org.foo", TvApps.label("org.foo"));
    }

    @Test
    public void landscapeCropsSides() {
        assertArrayEquals(new int[]{240, 0, 3840, 2160}, PhotoMath.cropRect(4320, 2160));
    }

    @Test
    public void portraitCropsMiddle() {
        assertArrayEquals(new int[]{0, 1312, 3000, 1687}, PhotoMath.cropRect(3000, 4312));
    }

    @Test
    public void fileNames() {
        assertEquals("01.jpg", PhotoMath.fileName(0));
        assertEquals("30.jpg", PhotoMath.fileName(29));
    }

    @Test
    public void swapIsAtomicPair() {
        String d = TvCommands.DIR;
        assertEquals("rm -rf " + d + "/wallpapers; mv " + d + "/wallpapers.new " + d + "/wallpapers", TvCommands.swapWallpapers());
        assertEquals("am force-stop com.gathin.tvhome; am start -n com.gathin.tvhome/.MainActivity", TvCommands.restartHome());
    }

    @Test
    public void tvMessageForRefused() {
        assertTrue(Messages.forError(new java.net.ConnectException("refused")).startsWith("TV tidak dijumpai"));
        assertTrue(Messages.forError(new java.net.SocketTimeoutException("t")).startsWith("TV tidak dijumpai"));
        assertTrue(Messages.forError(new RuntimeException("boom")).startsWith("Gagal: "));
    }

    @Test
    public void portraitFitsInsideWithoutCutting() {
        // 3000×4000 upright: full height 1080, width 810, centred.
        assertArrayEquals(new int[]{555, 0, 810, 1080}, PhotoMath.fitRect(3000, 4000));
    }

    @Test
    public void wideFitsInsideWithoutCutting() {
        // 4000×1000: full width 1920, height 480, centred.
        assertArrayEquals(new int[]{0, 300, 1920, 480}, PhotoMath.fitRect(4000, 1000));
    }

    @Test
    public void azanWallpaperPath() {
        assertEquals(TvCommands.DIR + "/azan_wallpaper.jpg", TvCommands.azanWallpaperPath());
    }
}
