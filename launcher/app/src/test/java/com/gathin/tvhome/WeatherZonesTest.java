package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherZonesTest {
    @Test public void shahAlam() {
        double[] c = WeatherZones.coords("SGR01");
        assertEquals(3.07, c[0], 0.2);
        assertEquals(101.52, c[1], 0.2);
        assertNotNull(WeatherZones.coords("sgr01"));
    }

    @Test public void unknownZone() {
        assertNull(WeatherZones.coords("XYZ99"));
        assertNull(WeatherZones.coords(""));
        assertNull(WeatherZones.coords(null));
    }

    @Test public void everyJakimZoneIsInMalaysia() {
        String[] all = ("JHR01 JHR02 JHR03 JHR04 KDH01 KDH02 KDH03 KDH04 KDH05 KDH06 KDH07 KTN01 KTN02 MLK01 NGS01 NGS02 NGS03 "
                + "PHG01 PHG02 PHG03 PHG04 PHG05 PHG06 PHG07 PLS01 PNG01 PRK01 PRK02 PRK03 PRK04 PRK05 PRK06 PRK07 SBH01 SBH02 "
                + "SBH03 SBH04 SBH05 SBH06 SBH07 SBH08 SBH09 SGR01 SGR02 SGR03 SWK01 SWK02 SWK03 SWK04 SWK05 SWK06 SWK07 SWK08 "
                + "SWK09 TRG01 TRG02 TRG03 TRG04 WLY01 WLY02").split(" ");
        for (String z : all) {
            double[] c = WeatherZones.coords(z);
            assertNotNull(z, c);
            if (c[0] < 0.8 || c[0] > 7.5 || c[1] < 99.5 || c[1] > 119.5) throw new AssertionError(z + " outside Malaysia");
        }
    }
}
