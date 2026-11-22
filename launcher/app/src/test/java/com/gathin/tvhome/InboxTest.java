package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Only Glass TV's own files are taken from the shared inbox. */
public class InboxTest {
    @Test
    public void knownFilesOnly() {
        for (String ok : new String[]{"config.json", "azan.mp3", "azan_subuh.mp3", "azan_wallpaper.jpg", "wallpaper.jpg"}) {
            assertTrue(ok, Inbox.allowed(ok));
        }
        for (String no : new String[]{"../config.json", "evil.apk", "wallpapers", ".current", "config.json.tmp", ""}) {
            assertFalse(no, Inbox.allowed(no));
        }
    }

    @Test
    public void wallpaperPicturesOnly() {
        assertTrue(Inbox.wallpaperAllowed("01.jpg"));
        assertTrue(Inbox.wallpaperAllowed("12.png"));
        assertFalse(Inbox.wallpaperAllowed("../01.jpg"));
        assertFalse(Inbox.wallpaperAllowed("x.mp3"));
    }
}
