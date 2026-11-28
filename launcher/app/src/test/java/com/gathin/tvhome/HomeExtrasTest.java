package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class HomeExtrasTest {
    @Test public void defaults() throws Exception {
        HomeExtras h = HomeExtras.fromJson(new JSONObject("{}"));
        assertTrue(h.verse);
        assertEquals("ms", h.lang);
        assertTrue(h.weatherOn("SGR01"));
        assertFalse(h.weatherOn(""));
        assertFalse(h.weatherOn("XYZ99"));
        assertTrue(HomeExtras.fromJson(null).verse);
    }

    @Test public void explicitOff() throws Exception {
        HomeExtras h = HomeExtras.fromJson(new JSONObject("{\"weather\":false,\"daily_verse\":false,\"display_language\":\"en\"}"));
        assertFalse(h.verse);
        assertFalse(h.weatherOn("SGR01"));
        assertEquals("en", h.lang);
        assertEquals("ms", HomeExtras.fromJson(new JSONObject("{\"display_language\":\"fr\"}")).lang);
    }
}
