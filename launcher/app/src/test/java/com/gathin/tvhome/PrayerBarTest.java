package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.LocalTime;

import org.junit.Test;

public class PrayerBarTest {

    @Test
    public void countdown() {
        assertEquals("7 min lagi", PrayerBar.countdown(Duration.ofMinutes(7)));
        assertEquals("7j 2m lagi", PrayerBar.countdown(Duration.ofMinutes(422)));
        assertEquals("1j lagi", PrayerBar.countdown(Duration.ofMinutes(60)));
        assertEquals("1 min lagi", PrayerBar.countdown(Duration.ofSeconds(20)));
    }

    @Test
    public void clock12and24() {
        assertEquals("1:10 PM", PrayerBar.clock(LocalTime.of(13, 10), false));
        assertEquals("5:48 AM", PrayerBar.clock(LocalTime.of(5, 48), false));
        assertEquals("13:10", PrayerBar.clock(LocalTime.of(13, 10), true));
    }

    @Test
    public void headerCarriesTheHijriDate() {
        assertEquals("WAKTU SOLAT · 11 Jamadilakhir 1448H", PrayerBar.header("11 Jamadilakhir 1448H"));
        assertEquals("WAKTU SOLAT", PrayerBar.header(null));
        assertEquals("WAKTU SOLAT", PrayerBar.header(""));
    }
}
