package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SettingsTest {
    @Test
    public void onlySetFieldsAreChanges() {
        Settings s = new Settings();
        s.columns = 5;
        s.zone = "SGR01";
        assertEquals(Map.of("columns", 5, "prayer_zone", "SGR01"), s.changes());
    }

    @Test
    public void ownPicturesTurnPlacesOff() {
        Settings s = new Settings();
        s.places = false;
        s.wallpaperSeconds = 30;
        Map<String, Object> c = s.changes();
        assertEquals(false, c.get("places"));
        assertEquals(30, c.get("wallpaper_seconds"));
    }

    @Test
    public void placesClearsTheInterval() {
        Settings s = new Settings();
        s.places = true;
        s.wallpaperSeconds = 30;
        assertTrue(s.changes().containsKey("wallpaper_seconds"));
        assertNull(s.changes().get("wallpaper_seconds"));
    }

    @Test
    public void everyKeyHasItsConfigName() {
        Settings s = new Settings();
        s.dockApps = List.of("com.netflix.ninja");
        s.networkPill = false;
        s.reducedMotion = true;
        s.bismillah = true;
        s.backgroundColor = "#000000";
        s.zoneName = "Shah Alam";
        s.azanPrayers = List.of("maghrib");
        s.azanText = "a";
        s.azanSubtext = "b";
        s.azanDim = 2;
        Map<String, Object> c = s.changes();
        for (String k : new String[]{"dock_apps", "show_network_pill", "reduced_motion", "bismillah", "background_color",
                "prayer_zone_name", "azan_prayers", "azan_text", "azan_subtext", "azan_dim"}) {
            assertTrue(k, c.containsKey(k));
        }
        assertEquals(10, c.size());
    }
}
