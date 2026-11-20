package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.PrayerTimes.Next;
import com.gathin.tvhome.PrayerTimes.Prayer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.Test;

/** JAKIM e-solat data for SGR01, November 2026. */
public class PrayerTimesTest {

    static PrayerTimes load() {
        try (InputStream in = PrayerTimesTest.class.getResourceAsStream("/sgr01-month.json")) {
            return PrayerTimes.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ZonedDateTime my(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, PrayerTimes.MY);
    }

    @Test
    public void parsesMalayMonthsAndTimes() {
        PrayerTimes t = load();
        assertEquals(my(2026, 11, 1, 5, 48), t.at(LocalDate.of(2026, 11, 1), Prayer.SUBUH));
        assertEquals(my(2026, 11, 30, 20, 16), t.at(LocalDate.of(2026, 11, 30), Prayer.ISYAK));
        assertEquals(LocalDate.of(2026, 11, 30), t.lastDay());
    }

    @Test
    public void parsesDisAndOgo() throws Exception {
        String day = "\"hijri\":\"1448-06-21\",\"imsak\":\"05:40:00\",\"fajr\":\"05:50:00\",\"syuruk\":\"07:00:00\","
                + "\"dhuhr\":\"13:10:00\",\"asr\":\"16:30:00\",\"maghrib\":\"19:10:00\",\"isha\":\"20:20:00\"";
        PrayerTimes t = PrayerTimes.parse("{\"status\":\"OK!\",\"prayerTime\":[{\"date\":\"31-Dis-2026\"," + day
                + "},{\"date\":\"15-Ogo-2026\"," + day + "}]}");
        assertTrue(t.has(LocalDate.of(2026, 12, 31)));
        assertTrue(t.has(LocalDate.of(2026, 8, 15)));
    }

    @Test
    public void nextSkipsSyurukAndPicksZohor() {
        Next n = load().next(my(2026, 11, 1, 6, 8));
        assertEquals(Prayer.ZOHOR, n.prayer);
        assertEquals(my(2026, 11, 1, 13, 0), n.time);
    }

    @Test
    public void nextAfterIsyakIsTomorrowsSubuh() {
        Next n = load().next(my(2026, 11, 1, 21, 0));
        assertEquals(Prayer.SUBUH, n.prayer);
        assertEquals(LocalDate.of(2026, 11, 2), n.time.toLocalDate());
    }

    @Test
    public void nextAtExactTimeIsTheFollowingOne() {
        assertEquals(Prayer.ASAR, load().next(my(2026, 11, 1, 13, 0)).prayer);
    }

    @Test
    public void nextIsNullPastLastDay() {
        assertNull(load().next(my(2026, 11, 30, 21, 0)));
    }

    @Test
    public void hijriInMalay() {
        assertEquals("21 Jamadilawal 1448H", load().hijri(LocalDate.of(2026, 11, 1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRecordIsRejected() throws Exception {
        PrayerTimes.parse("{\"prayerTime\":{\"data\":[\"No data\"]},\"status\":\"NO_RECORD!\"}");
    }
}
