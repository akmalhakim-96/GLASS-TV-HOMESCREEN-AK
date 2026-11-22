package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.WifiList.Net;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class WifiListTest {
    private static Net n(String ssid, int level, String caps) {
        return new Net(ssid, level, caps);
    }

    @Test
    public void connectedFirstThenStrongestOnceEach() {
        List<Net> rows = WifiList.rows(Arrays.asList(
                n("Jiran", -70, "[WPA2-PSK-CCMP][ESS]"),
                n("Rumah", -60, "[WPA2-PSK-CCMP][ESS]"),
                n("Kedai", -40, "[ESS]"),
                n("Rumah", -50, "[WPA2-PSK-CCMP][ESS]"),   // the same network on another band
                n("", -30, "[ESS]")),                        // hidden
                "Rumah");
        assertEquals(Arrays.asList("Rumah", "Kedai", "Jiran"), WifiList.names(rows));
        assertEquals(-50, rows.get(0).level);
    }

    @Test
    public void atMostEight() {
        Net[] many = new Net[12];
        for (int i = 0; i < many.length; i++) many[i] = n("N" + i, -40 - i, "[ESS]");
        assertEquals(8, WifiList.rows(Arrays.asList(many), null).size());
    }

    @Test
    public void security() {
        assertTrue(n("a", 0, "[WPA2-PSK-CCMP][ESS]").secured());
        assertTrue(n("a", 0, "[WPA-PSK-TKIP]").secured());
        assertFalse(n("a", 0, "[ESS]").secured());
        assertTrue(n("a", 0, "[WPA2-PSK-CCMP]").directJoin());
        assertTrue(n("a", 0, "[ESS]").directJoin());
        assertFalse(n("a", 0, "[WPA2-EAP-CCMP]").directJoin());   // office Wi-Fi: settings
        assertFalse(n("a", 0, "[WEP]").directJoin());
        assertFalse(n("a", 0, "[SAE]").directJoin());            // WPA3: settings
    }

    @Test
    public void quotedForAndroid() {
        assertEquals("\"Rumah Kami\"", WifiList.quote("Rumah Kami"));
        assertEquals("\"pa\"ss\"", WifiList.quote("pa\"ss"));   // Android takes the text as is between the quotes
    }
}
