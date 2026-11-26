package my.glasstv.remote;

/** Whether HANTAR may go ahead, and whether the photos that went make it a success. */
final class SendGate {
    static final String NOT_FILLED = "Membaca tetapan TV dahulu… Semak borang, kemudian tekan HANTAR sekali lagi.";
    static final String PICK_PHOTOS = "Pilih foto dahulu untuk \"Gambar saya\".";
    static final String NO_PHOTO_SENT = "Tiada foto dapat dibaca. Tetapan TV tidak diubah.";

    private SendGate() {}

    /**
     * @param filled      the form shows what the TV has (read on a successful connect)
     * @param own         "Gambar saya" is selected
     * @param photos      photos picked on the phone
     * @param tvHasPhotos the TV already has its own wallpapers/ to keep showing
     */
    static String check(boolean filled, boolean own, int photos, boolean tvHasPhotos) {
        if (!filled) return NOT_FILLED;
        if (own && photos == 0 && !tvHasPhotos) return PICK_PHOTOS;
        return null;
    }

    /**
     * A section's own button (Gambar, Home, Solat, Bismillah) sends exactly what that page shows, and the
     * rest of the TV stays as it is, so it need not wait for the TV's settings to be read. Only "Hantar
     * semua" does, since it sends every page, including ones the owner has not looked at.
     */
    static String check(int section, boolean filled, boolean own, int photos, boolean tvHasPhotos) {
        return check(filled || section != Settings.ALL, own, photos, tvHasPhotos);
    }

    /** Photos were picked but none arrived: don't switch the TV away from what it shows now. */
    static String afterPhotos(int picked, int sent) {
        return picked > 0 && sent == 0 ? NO_PHOTO_SENT : null;
    }
}
