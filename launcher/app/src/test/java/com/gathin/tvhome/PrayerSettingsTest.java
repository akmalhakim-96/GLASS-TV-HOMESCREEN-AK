package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.time.LocalDate;
import java.util.EnumSet;

import org.json.JSONObject;
import org.junit.Test;

public class PrayerSettingsTest {

    private static final long DAY = 24L * 3600 * 1000;
    private static final long NOW = 1_000L * DAY;

    private static PrayerSettings s(String json) throws Exception {
        return PrayerSettings.fromJson(new JSONObject(json));
    }

    private static LocalDate nov(int d) { return LocalDate.of(2026, 11, d); }

    @Test
    public void defaults() throws Exception {
        PrayerSettings s = s("{\"prayer_zone\":\"SGR01\"}");
        assertTrue(s.enabled());
        assertEquals("SGR01", s.zoneName);
        assertEquals(EnumSet.allOf(Prayer.class), s.azan);
        assertEquals(1, s.dim);
        assertEquals("Telah masuk waktu solat fardu Maghrib bagi kawasan SGR01 dan sekitarnya", s.textFor(Prayer.MAGHRIB));
        assertEquals("Marilah menunaikan solat", s.subtextFor(Prayer.MAGHRIB));
    }

    @Test
    public void offWithoutZone() throws Exception {
        assertFalse(PrayerSettings.fromJson(null).enabled());
        assertFalse(s("{\"prayer_zone\":\"  \"}").enabled());
    }

    @Test
    public void customTextAndSubset() throws Exception {
        PrayerSettings s = s("{\"prayer_zone\":\"sgr01\",\"prayer_zone_name\":\"Shah Alam\","
                + "\"azan_prayers\":[\"maghrib\",\"isyak\",\"syuruk\"],\"azan_text\":\"{waktu} – {zon}\",\"azan_dim\":2}");
        assertEquals("SGR01", s.zone);
        assertEquals("Maghrib – Shah Alam", s.textFor(Prayer.MAGHRIB));
        assertEquals(EnumSet.of(Prayer.MAGHRIB, Prayer.ISYAK), s.azan);
        assertEquals(2, s.dim);
    }

    @Test
    public void badTypesFallBack() throws Exception {
        PrayerSettings s = s("{\"prayer_zone\":\"SGR01\",\"azan_dim\":\"gelap\",\"azan_prayers\":\"maghrib\"}");
        assertEquals(1, s.dim);
        assertEquals(EnumSet.allOf(Prayer.class), s.azan);
        assertEquals(1, s("{\"prayer_zone\":\"SGR01\",\"azan_dim\":7}").dim);
        assertEquals(EnumSet.allOf(Prayer.class), s("{\"prayer_zone\":\"SGR01\",\"azan_prayers\":[]}").azan);
    }

    @Test
    public void dueWhenNothingCached() {
        assertTrue(PrayerStore.due(null, "", "SGR01", 0, NOW, nov(1)));
    }

    @Test
    public void dueWhenZoneChanged() {
        assertTrue(PrayerStore.due(PrayerTimesTest.load(), "WLY01", "SGR01", NOW, NOW, nov(1)));
        assertFalse(PrayerStore.due(PrayerTimesTest.load(), "SGR01", "SGR01", NOW, NOW, nov(1)));
    }

    @Test
    public void dueWhenOlderThan30Days() {
        assertTrue(PrayerStore.due(PrayerTimesTest.load(), "SGR01", "SGR01", NOW - 31 * DAY, NOW, nov(1)));
        assertFalse(PrayerStore.due(PrayerTimesTest.load(), "SGR01", "SGR01", NOW - DAY, NOW, nov(1)));
    }

    @Test
    public void dueWhenFewDaysLeft() {
        assertTrue(PrayerStore.due(PrayerTimesTest.load(), "SGR01", "SGR01", NOW, NOW, nov(24)));
        assertFalse(PrayerStore.due(PrayerTimesTest.load(), "SGR01", "SGR01", NOW, NOW, nov(20)));
    }

    private static PrayerTimes untilDec31() throws Exception {
        String day = "\"hijri\":\"1448-07-01\",\"fajr\":\"05:50:00\",\"syuruk\":\"07:00:00\",\"dhuhr\":\"13:10:00\","
                + "\"asr\":\"16:30:00\",\"maghrib\":\"19:10:00\",\"isha\":\"20:20:00\"";
        return PrayerTimes.parse("{\"status\":\"OK!\",\"prayerTime\":[{\"date\":\"30-Dis-2026\"," + day
                + "},{\"date\":\"31-Dis-2026\"," + day + "}]}");
    }

    @Test
    public void wholeYearIsNotRefetchedInLateDecember() throws Exception {
        assertFalse(PrayerStore.due(untilDec31(), "SGR01", "SGR01", NOW, NOW, LocalDate.of(2026, 12, 26)));
        assertFalse(PrayerStore.due(untilDec31(), "SGR01", "SGR01", NOW, NOW, LocalDate.of(2026, 12, 31)));
    }

    @Test
    public void newYearIsDue() throws Exception {
        assertTrue(PrayerStore.due(untilDec31(), "SGR01", "SGR01", NOW, NOW, LocalDate.of(2027, 1, 1)));
    }

    @Test(expected = java.io.IOException.class)
    public void slowDownloadStopsAtTheDeadline() throws Exception {
        java.io.InputStream slow = new java.io.ByteArrayInputStream(new byte[100_000]);
        PrayerStore.readAll(slow, 2 << 20, System.currentTimeMillis() - 1);
    }
}
