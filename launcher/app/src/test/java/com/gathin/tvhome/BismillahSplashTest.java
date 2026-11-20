package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class BismillahSplashTest {

    @Test
    public void onWithoutATheme() {
        assertTrue(BismillahSplash.enabled(null));
    }

    @Test
    public void themeCanTurnItOff() throws Exception {
        assertFalse(BismillahSplash.enabled(new JSONObject("{\"bismillah\":false}")));
        assertTrue(BismillahSplash.enabled(new JSONObject("{\"bismillah\":true}")));
    }

    @Test
    public void wrongTypeKeepsItOn() throws Exception {
        assertTrue(BismillahSplash.enabled(new JSONObject("{\"bismillah\":\"tidak\"}")));
    }
}
