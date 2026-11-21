package my.glasstv.remote;

import java.util.Locale;

/**
 * The TV kinds the owner can pick. Glass TV runs on Android TV and Google TV 8+ (on the TV or on a
 * stick or box); Samsung (Tizen) and LG (webOS) cannot run Android apps or change their Home.
 * After connecting, the kind is detected from the TV itself.
 */
final class TvTypes {
    static final class Type {
        final String key, label, note;
        final boolean supported;

        Type(String key, String label, boolean supported, String note) {
            this.key = key;
            this.label = label;
            this.supported = supported;
            this.note = note;
        }
    }

    private static final String BOX = "Pasang stick Google TV (contoh Chromecast with Google TV) atau Mi TV Stick "
            + "di port HDMI TV, hidupkan HDMI-CEC pada TV, kemudian pilih jenis stick itu di sini.";

    static final Type[] ALL = {
            new Type("android", "Android TV", true, "Android TV 8 ke atas: Sony, TCL, Sharp, Philips, Hisense (Android), box Android."),
            new Type("google", "Google TV", true, "Google TV: Chromecast with Google TV dan TV Google TV."),
            new Type("skyworth", "Skyworth", true, "Skyworth Android TV / Google TV, termasuk unifi Plus Box."),
            new Type("mi", "Mi TV Stick / Mi Box", true, "Xiaomi Mi TV Stick dan Mi Box."),
            new Type("samsung", "Samsung (Tizen)", false, "TV Samsung guna Tizen: tidak boleh pasang app Android atau tukar Home. " + BOX),
            new Type("lg", "LG (webOS)", false, "TV LG guna webOS: tidak boleh pasang app Android atau tukar Home. " + BOX),
    };

    private TvTypes() {}

    static Type find(String key) {
        for (Type t : ALL) if (t.key.equals(key)) return t;
        return null;
    }

    /** From getprop ro.product.manufacturer / ro.product.model and the HOME claimants. */
    static Type detect(String manufacturer, String model, String homeClaimants) {
        String m = (manufacturer == null ? "" : manufacturer).trim().toLowerCase(Locale.ROOT);
        String mo = (model == null ? "" : model).trim().toLowerCase(Locale.ROOT);
        if (m.contains("xiaomi") || mo.startsWith("mitv") || mo.startsWith("mibox")) return find("mi");
        if (m.contains("skyworth")) return find("skyworth");
        if (homeClaimants != null && homeClaimants.contains("com.google.android.apps.tv.launcherx/")) return find("google");
        return find("android");
    }
}
