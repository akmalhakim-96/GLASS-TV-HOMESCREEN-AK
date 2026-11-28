package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;

public class VersesTest {
    static final String TWO = "[{\"ref\":\"94:5\",\"surah\":\"Ash-Sharh\",\"ar\":\"a\",\"ms\":\"m\",\"en\":\"e\"},"
            + "{\"ref\":\"65:3\",\"surah\":\"At-Talaq\",\"ar\":\"b\",\"ms\":\"n\",\"en\":\"f\"}]";

    @Test public void pickIsStable() {
        Verses v = Verses.parse(TWO);
        LocalDate d = LocalDate.of(2026, 11, 28);
        assertEquals(v.pick(d).ref, v.pick(d).ref);
        assertEquals("94:5", v.pick(LocalDate.of(2026, 1, 1)).ref);
        assertEquals("65:3", v.pick(LocalDate.of(2026, 1, 2)).ref);
    }

    @Test public void indexAcrossYearEnd() {
        Verses v = Verses.parse(TWO);
        for (LocalDate d : new LocalDate[]{LocalDate.of(2026, 12, 31), LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 12, 31), LocalDate.of(2027, 1, 1)}) {
            assertFalse(v.pick(d).ref.isEmpty());
        }
    }

    @Test public void headingUppercase() {
        Verses.Verse x = Verses.parse(TWO).pick(LocalDate.of(2026, 1, 2));
        assertEquals("AT-TALAQ 65:3", Verses.heading(x));
        assertEquals("n", x.translation("ms"));
        assertEquals("f", x.translation("en"));
        assertEquals("n", x.translation("xx"));
    }

    @Test public void badDataIsEmptyNotACrash() {
        assertTrue(Verses.parse("not json").isEmpty());
        assertTrue(Verses.parse("[]").isEmpty());
        assertEquals(null, Verses.parse("[]").pick(LocalDate.of(2026, 1, 1)));
    }

    @Test public void bundledFileIsComplete() throws Exception {
        String json = new String(Files.readAllBytes(new File("src/main/res/raw/ayat.json").toPath()), StandardCharsets.UTF_8);
        Verses v = Verses.parse(json);
        assertEquals(60, v.size());
        for (int i = 0; i < v.size(); i++) {
            Verses.Verse x = v.get(i);
            assertTrue(x.ref, x.ref.matches("\\d{1,3}:\\d{1,3}"));
            assertFalse(x.ref, x.surah.isEmpty() || x.ar.isEmpty() || x.ms.isEmpty() || x.en.isEmpty());
            assertTrue(x.ref, x.ar.length() <= 140 && x.ms.length() <= 300 && x.en.length() <= 300);
        }
    }
}
