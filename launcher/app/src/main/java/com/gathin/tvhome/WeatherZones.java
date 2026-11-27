package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Where to ask for the weather: the main town of each JAKIM prayer zone (near enough for weather). */
final class WeatherZones {
    private static final Map<String, double[]> AT = new HashMap<>();

    private static void put(String zone, double lat, double lon) {
        AT.put(zone, new double[]{lat, lon});
    }

    static {
        put("JHR01", 2.45, 104.52);   // Pulau Aur
        put("JHR02", 1.49, 103.74);   // Johor Bahru
        put("JHR03", 2.03, 103.32);   // Kluang
        put("JHR04", 1.85, 102.93);   // Batu Pahat
        put("KDH01", 6.12, 100.37);   // Alor Setar
        put("KDH02", 5.65, 100.49);   // Sungai Petani
        put("KDH03", 6.25, 100.61);   // Kuala Nerang
        put("KDH04", 5.68, 100.92);   // Baling
        put("KDH05", 5.37, 100.56);   // Kulim
        put("KDH06", 6.32, 99.85);    // Langkawi
        put("KDH07", 5.79, 100.43);   // Gunung Jerai
        put("KTN01", 6.13, 102.24);   // Kota Bharu
        put("KTN02", 4.88, 101.97);   // Gua Musang
        put("MLK01", 2.19, 102.25);   // Melaka
        put("NGS01", 2.47, 102.23);   // Tampin
        put("NGS02", 2.74, 102.25);   // Kuala Pilah
        put("NGS03", 2.73, 101.94);   // Seremban
        put("PHG01", 2.82, 104.16);   // Pulau Tioman
        put("PHG02", 3.81, 103.33);   // Kuantan
        put("PHG03", 3.45, 102.42);   // Temerloh
        put("PHG04", 3.79, 101.86);   // Raub
        put("PHG05", 3.35, 101.82);   // Bukit Tinggi
        put("PHG06", 4.47, 101.38);   // Cameron Highlands
        put("PHG07", 2.80, 103.49);   // Rompin
        put("PLS01", 6.44, 100.20);   // Kangar
        put("PNG01", 5.41, 100.33);   // George Town
        put("PRK01", 4.20, 101.26);   // Tapah
        put("PRK02", 4.60, 101.08);   // Ipoh
        put("PRK03", 5.43, 101.13);   // Gerik
        put("PRK04", 5.50, 101.35);   // Temengor, Belum
        put("PRK05", 4.02, 101.02);   // Teluk Intan
        put("PRK06", 4.85, 100.74);   // Taiping
        put("PRK07", 4.86, 100.79);   // Bukit Larut
        put("SBH01", 5.84, 118.12);   // Sandakan
        put("SBH02", 5.89, 117.56);   // Beluran
        put("SBH03", 5.03, 118.33);   // Lahad Datu
        put("SBH04", 4.24, 117.89);   // Tawau
        put("SBH05", 6.88, 116.84);   // Kudat
        put("SBH06", 6.08, 116.56);   // Gunung Kinabalu
        put("SBH07", 5.98, 116.07);   // Kota Kinabalu
        put("SBH08", 5.34, 116.16);   // Keningau
        put("SBH09", 5.35, 115.75);   // Beaufort
        put("SGR01", 3.07, 101.52);   // Shah Alam
        put("SGR02", 3.34, 101.25);   // Kuala Selangor
        put("SGR03", 3.04, 101.45);   // Klang
        put("SWK01", 4.75, 115.01);   // Limbang
        put("SWK02", 4.40, 113.99);   // Miri
        put("SWK03", 3.17, 113.04);   // Bintulu
        put("SWK04", 2.29, 111.83);   // Sibu
        put("SWK05", 2.13, 111.52);   // Sarikei
        put("SWK06", 1.24, 111.46);   // Sri Aman
        put("SWK07", 1.46, 110.49);   // Kota Samarahan
        put("SWK08", 1.55, 110.36);   // Kuching
        put("SWK09", 4.85, 115.40);   // Kampung Patarikan (Lawas)
        put("TRG01", 5.33, 103.14);   // Kuala Terengganu
        put("TRG02", 5.74, 102.49);   // Jerteh
        put("TRG03", 5.07, 103.01);   // Kuala Berang
        put("TRG04", 4.76, 103.42);   // Dungun
        put("WLY01", 3.14, 101.69);   // Kuala Lumpur
        put("WLY02", 5.28, 115.24);   // Labuan
    }

    private WeatherZones() {}

    /** {latitude, longitude}, or null for no zone or one not in the table. */
    static double[] coords(String zone) {
        if (zone == null) return null;
        double[] c = AT.get(zone.trim().toUpperCase(Locale.ROOT));
        return c == null ? null : c.clone();
    }
}
