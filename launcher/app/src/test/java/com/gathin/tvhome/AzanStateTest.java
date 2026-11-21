package com.gathin.tvhome;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_HOME;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class AzanStateTest {

    @After
    public void reset() {
        AzanState.setStopper(null);
        AzanState.end();
    }

    @Test
    public void beginTwiceIsRefused() {
        assertTrue(AzanState.begin());
        assertFalse(AzanState.begin());
        AzanState.end();
        assertTrue(AzanState.begin());
    }

    @Test
    public void okIsSwallowedOnlyWhileActive() {
        assertFalse(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN));
        AzanState.begin();
        assertTrue(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN));
        assertTrue(AzanState.swallowOk(KEYCODE_ENTER, ACTION_DOWN));
        assertFalse(AzanState.swallowOk(KEYCODE_VOLUME_UP, ACTION_DOWN));
        assertFalse(AzanState.swallowOk(KEYCODE_HOME, ACTION_DOWN));
        assertFalse(AzanState.swallowOk(KEYCODE_BACK, ACTION_DOWN));
    }

    @Test
    public void okUpRunsStopperOnce() {
        AtomicInteger stops = new AtomicInteger();
        AzanState.setStopper(stops::incrementAndGet);
        AzanState.begin();
        assertTrue(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN));
        assertTrue(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_UP));
        assertEquals(1, stops.get());
        AzanState.end();
        assertFalse(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN));
        assertEquals(1, stops.get());
    }

    @Test
    public void endClearsAPendingPress() {
        AzanState.begin();
        assertTrue(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN));
        AzanState.end();   // the azan finished before OK was released
        assertFalse(AzanState.swallowOk(KEYCODE_DPAD_CENTER, ACTION_DOWN));
    }

    @Test
    public void homeAndBackWaitForOkWhileAzanShows() {
        assertFalse(AzanState.swallowNav(android.view.KeyEvent.KEYCODE_ALL_APPS));
        AzanState.begin();
        assertTrue(AzanState.swallowNav(android.view.KeyEvent.KEYCODE_ALL_APPS));
        assertTrue(AzanState.swallowNav(KEYCODE_HOME));
        assertTrue(AzanState.swallowNav(KEYCODE_BACK));
        assertFalse(AzanState.swallowNav(KEYCODE_VOLUME_UP));
        AzanState.end();
        assertFalse(AzanState.swallowNav(KEYCODE_BACK));
    }
}
