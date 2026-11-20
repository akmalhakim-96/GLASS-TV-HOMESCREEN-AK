package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.PrayerTimes.Next;
import com.gathin.tvhome.PrayerTimes.Prayer;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.json.JSONObject;
import org.junit.Test;

public class AzanSchedulerTest {

    private static PrayerSettings only(String prayers) throws Exception {
        return PrayerSettings.fromJson(new JSONObject("{\"prayer_zone\":\"SGR01\",\"azan_prayers\":" + prayers + "}"));
    }

    private static ZonedDateTime nov1(int h, int m) {
        return ZonedDateTime.of(2026, 11, 1, h, m, 0, 0, PrayerTimes.MY);
    }

    @Test
    public void skipsDisabledPrayers() throws Exception {
        Next n = AzanScheduler.nextAzan(PrayerTimesTest.load(), only("[\"isyak\"]"), nov1(6, 8));
        assertEquals(Prayer.ISYAK, n.prayer);
        assertEquals(nov1(20, 9), n.time);
    }

    @Test
    public void wrapsToTomorrowWhenTodayDisabled() throws Exception {
        Next n = AzanScheduler.nextAzan(PrayerTimesTest.load(), only("[\"subuh\"]"), nov1(6, 8));
        assertEquals(Prayer.SUBUH, n.prayer);
        assertEquals(LocalDate.of(2026, 11, 2), n.time.toLocalDate());
    }

    @Test
    public void noneBeyondTheData() throws Exception {
        assertNull(AzanScheduler.nextAzan(PrayerTimesTest.load(), only("[\"subuh\"]"),
                ZonedDateTime.of(2026, 11, 30, 21, 0, 0, 0, PrayerTimes.MY)));
    }

    @Test
    public void latePlayIsSkipped() {
        assertTrue(AzanScheduler.shouldPlay(0, 119_000));
        assertFalse(AzanScheduler.shouldPlay(0, 121_000));
        assertFalse(AzanScheduler.shouldPlay(10_000, 0));
    }

    @Test
    public void refreshAfterTheDataEnds() {
        ZonedDateTime at = AzanScheduler.refreshAt(PrayerTimesTest.load(),
                ZonedDateTime.of(2026, 11, 30, 21, 0, 0, 0, PrayerTimes.MY));
        assertEquals(ZonedDateTime.of(2026, 12, 1, 0, 5, 0, 0, PrayerTimes.MY), at);
    }

    @Test
    public void retryInAnHourWithoutData() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 30, 21, 0, 0, 0, PrayerTimes.MY);
        assertEquals(now.plusHours(1), AzanScheduler.refreshAt(null, now));
    }
}
