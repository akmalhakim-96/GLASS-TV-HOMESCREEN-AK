package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeSetupTest {
    @Test
    public void stockFromHome() throws Exception {
        assertEquals("com.mitv.tvhome", HomeSetup.stockLauncher("com.mitv.tvhome/.MainActivity", ""));
        assertEquals("com.google.android.tvlauncher", HomeSetup.stockLauncher("com.google.android.tvlauncher/.MainActivity\n", ""));
    }

    @Test
    public void alreadyGlassTv() throws Exception {
        assertEquals(HomeSetup.NONE, HomeSetup.stockLauncher("com.gathin.tvhome/.MainActivity", ""));
    }

    @Test
    public void chooserPicksTheOneKnownLauncher() throws Exception {
        assertEquals("com.google.android.tvlauncher", HomeSetup.stockLauncher("android",
                "com.google.android.tvlauncher/.MainActivity\ncom.duokan.factorytest/.Home\n"));
    }

    @Test(expected = HomeSetup.SetupException.class)
    public void chooserWithTwoKnownStops() throws Exception {
        HomeSetup.stockLauncher("android", "com.google.android.tvlauncher/.A\ncom.mitv.tvhome/.B\n");
    }

    @Test(expected = HomeSetup.SetupException.class)
    public void unknownHomeStops() throws Exception {
        HomeSetup.stockLauncher("com.other.launcher/.X", "");
    }

    @Test
    public void sdkGate() {
        assertTrue(HomeSetup.sdkOk("28"));
        assertTrue(HomeSetup.sdkOk(" 26\n"));
        assertFalse(HomeSetup.sdkOk("25"));
        assertFalse(HomeSetup.sdkOk("x"));
    }

    @Test
    public void healthyNeedsSamePidAndFocus() {
        assertTrue(HomeSetup.healthy("123|123|mCurrentFocus=Window{1 u0 com.gathin.tvhome/com.gathin.tvhome.MainActivity}"));
        assertFalse(HomeSetup.healthy("123|456|mCurrentFocus=Window{1 u0 com.gathin.tvhome/x}"));
        assertFalse(HomeSetup.healthy("||mCurrentFocus=Window{1 u0 com.gathin.tvhome/x}"));
        assertFalse(HomeSetup.healthy("123|123|mCurrentFocus=Window{1 u0 com.mitv.tvhome/x}"));
    }

    @Test
    public void accessibilityKeepsOthers() {
        String svc = "com.gathin.tvhome/.HomeKeyService";
        assertEquals(svc, HomeSetup.withHomeKeyService(null));
        assertEquals(svc, HomeSetup.withHomeKeyService("null"));
        assertEquals(svc, HomeSetup.withHomeKeyService(" \n"));
        assertEquals("a/.B:" + svc, HomeSetup.withHomeKeyService("a/.B\n"));
        assertEquals("a/.B:" + svc, HomeSetup.withHomeKeyService("a/.B:" + svc));
    }
}
