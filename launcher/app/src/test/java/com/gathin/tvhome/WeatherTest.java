package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {
    static final String FORECAST = "{\"current\":{\"time\":\"2026-11-27T14:00\",\"interval\":900,"
            + "\"temperature_2m\":29.4,\"weather_code\":2,\"is_day\":1}}";
    static final String AIR = "{\"current\":{\"time\":\"2026-11-27T14:00\",\"us_aqi\":50}}";

    @Test public void parseBoth() {
        Weather.Reading r = Weather.parse(FORECAST, AIR, 1000L);
        assertEquals(29, r.tempC);
        assertEquals(2, r.code);
        assertTrue(r.day);
        assertEquals(50, r.aqi);
        assertEquals(1000L, r.atMs);
        assertEquals("⛅ 29° Berawan", Weather.weatherText(r, "ms"));
        assertEquals("⛅ 29° Partly cloudy", Weather.weatherText(r, "en"));
        assertEquals("Udara 50 Baik", Weather.airText(r, "ms"));
        assertEquals("Air 40 Good", Weather.airText(Weather.parse(FORECAST, AIR.replace("50", "40"), 0), "en"));
        assertNull(Weather.parse("<html>", AIR, 0));
        assertNull(Weather.parse("{\"current\":{}}", AIR, 0));
    }

    @Test public void pillWithoutAir() {
        for (String bad : new String[]{null, "", "oops", "{\"current\":{}}"}) {
            Weather.Reading r = Weather.parse(FORECAST, bad, 0);
            assertEquals(-1, r.aqi);
            assertEquals("", Weather.airText(r, "ms"));
        }
    }

    @Test public void wordsAndIcons() {
        assertEquals("Cerah", Weather.words(0, true, "ms"));
        assertEquals("Clear", Weather.words(0, false, "en"));
        assertEquals("☀️", Weather.icon(0, true));
        assertEquals("🌙", Weather.icon(0, false));
        assertEquals("Cloudy", Weather.words(3, true, "en"));
        assertEquals("Berawan", Weather.words(3, true, "ms"));
        assertEquals("Berkabus", Weather.words(45, true, "ms"));
        assertEquals("Hujan", Weather.words(61, true, "ms"));
        assertEquals("Rain", Weather.words(81, true, "en"));
        assertEquals("🌧️", Weather.icon(53, true));
        assertEquals("Ribut petir", Weather.words(95, true, "ms"));
        assertEquals("⛈️", Weather.icon(99, false));
    }

    @Test public void bands() {
        assertEquals("Baik", Weather.band(0, "ms"));
        assertEquals("Baik", Weather.band(50, "ms"));
        assertEquals("Sederhana", Weather.band(51, "ms"));
        assertEquals("Moderate", Weather.band(100, "en"));
        assertEquals("Tidak sihat", Weather.band(101, "ms"));
        assertEquals("Sangat tidak sihat", Weather.band(201, "ms"));
        assertEquals("Hazardous", Weather.band(301, "en"));
        assertEquals(0xFF4ADE80, Weather.bandColor(50));
        assertEquals(0xFFFACC15, Weather.bandColor(51));
        assertEquals(0xFFFB923C, Weather.bandColor(150));
        assertEquals(0xFFF87171, Weather.bandColor(250));
        assertEquals(0xFFC084FC, Weather.bandColor(400));
    }

    @Test public void staleAfterThreeHours() {
        Weather.Reading r = Weather.parse(FORECAST, AIR, 0);
        assertTrue(Weather.fresh(r, 3 * 3_600_000L));
        assertFalse(Weather.fresh(r, 3 * 3_600_000L + 1));
        assertFalse(Weather.fresh(null, 0));
    }

    @Test public void dueAfterAnHour() {
        long h = 3_600_000L, m = 60_000L;
        assertTrue(WeatherStore.due(0, 0, h));
        assertFalse(WeatherStore.due(h, h, h + 59 * m));
        assertTrue(WeatherStore.due(h, h, 2 * h));
        // A failed try waits 10 minutes before the next.
        assertFalse(WeatherStore.due(0, 2 * h, 2 * h + 9 * m));
        assertTrue(WeatherStore.due(0, 2 * h, 2 * h + 10 * m));
    }
}
