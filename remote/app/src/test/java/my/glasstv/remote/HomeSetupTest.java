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

    @Test
    public void extraHomeAppsAreOnlyKnownSafeOnes() {
        String claimants = "com.gathin.tvhome/.MainActivity\ncom.mitv.tvhome/.Main\n"
                + "com.duokan.factorytest/.FactoryHome\ncom.google.android.tungsten.setupwraith/.RecoveryActivity\n"
                + "com.android.tv.settings/.HomeX\n";
        assertEquals(java.util.List.of("com.duokan.factorytest", "com.google.android.tungsten.setupwraith"),
                HomeSetup.extraHomeApps(claimants));
    }

    @Test
    public void noExtraHomeApps() {
        assertTrue(HomeSetup.extraHomeApps("com.gathin.tvhome/.MainActivity\n").isEmpty());
    }

    @Test
    public void resumedActivityCountsWhenFocusIsNull() {
        assertTrue(HomeSetup.healthy("18077|18077|  mCurrentFocus=null "
                + "mResumedActivity: ActivityRecord{1 u0 com.gathin.tvhome/.MainActivity t12}"));
        assertFalse(HomeSetup.healthy("18077|18077|  mCurrentFocus=null mResumedActivity: ActivityRecord{1 u0 com.mitv.tvhome/.Main t3}"));
    }

    @Test
    public void healthCheckLooksAtTheResumedActivityAndRetries() {
        assertTrue(HomeSetup.HEALTH.contains("mResumedActivity"));
        assertTrue(HomeSetup.HEALTH.contains("for i in 1 2 3 4 5"));
    }
}
