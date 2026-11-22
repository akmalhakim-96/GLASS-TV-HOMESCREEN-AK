package my.glasstv.remote;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/** Each section's own send button changes only that section's keys. */
public class SectionsTest {
    private static Settings everything() {
        Settings s = new Settings();
        s.places = false;
        s.wallpaperSeconds = 30;
        s.dockApps = List.of("com.netflix.ninja");
        s.columns = 5;
        s.networkPill = true;
        s.reducedMotion = false;
        s.keepScreenOn = true;
        s.bismillah = true;
        s.zone = "SGR01";
        s.zoneName = "Shah Alam";
        s.azanPrayers = List.of("maghrib");
        s.azanText = "a";
        s.azanSubtext = "b";
        s.azanDim = 1;
        return s;
    }

    @Test
    public void wallpaperOnly() {
        assertEquals(Set.of("places", "wallpaper_seconds"), Settings.only(Settings.WALLPAPER, everything()).changes().keySet());
    }

    @Test
    public void homeOnly() {
        assertEquals(Set.of("dock_apps", "columns", "show_network_pill", "reduced_motion", "keep_screen_on"),
                Settings.only(Settings.HOME, everything()).changes().keySet());
    }

    @Test
    public void prayerOnly() {
        assertEquals(Set.of("prayer_zone", "prayer_zone_name", "azan_prayers", "azan_text", "azan_subtext", "azan_dim"),
                Settings.only(Settings.PRAYER, everything()).changes().keySet());
    }

    @Test
    public void bismillahOnly() {
        assertEquals(Set.of("bismillah"), Settings.only(Settings.BISMILLAH, everything()).changes().keySet());
    }
}
