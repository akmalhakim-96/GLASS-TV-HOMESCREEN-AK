package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/** "keep_screen_on": Home holds the screen on, so neither the screensaver nor sleep starts over it. */
public class KeepScreenTest {
    @Test
    public void offUnlessAsked() throws Exception {
        assertFalse(Config.keepScreenOn(null));
        assertFalse(Config.keepScreenOn(new JSONObject("{}")));
        assertFalse(Config.keepScreenOn(new JSONObject("{\"keep_screen_on\":\"ya\"}")));
        assertTrue(Config.keepScreenOn(new JSONObject("{\"keep_screen_on\":true}")));
    }
}
