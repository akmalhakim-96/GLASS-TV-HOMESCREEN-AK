package my.glasstv.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.SocketTimeoutException;

import org.junit.Test;

/** The review's important findings, pinned. */
public class FixesTest {
    // 1. HANTAR before the form was filled from the TV.
    @Test
    public void sendRefusedUntilFilledFromTv() {
        assertEquals(SendGate.NOT_FILLED, SendGate.check(false, false, 0, false));
        assertNull(SendGate.check(true, false, 0, false));
        // One section's own button sends what that page shows: no need to read the TV first.
        assertNull(SendGate.check(Settings.WALLPAPER, false, true, 1, false));
        assertEquals(SendGate.PICK_PHOTOS, SendGate.check(Settings.WALLPAPER, false, true, 0, false));
        assertEquals(SendGate.NOT_FILLED, SendGate.check(Settings.ALL, false, false, 0, false));
    }

    // 2. The TV answered but nobody pressed Allow yet.
    @Test
    public void handshakeTimeoutAsksForAllow() {
        assertEquals(Messages.ALLOW, Messages.forError(new Tv.AuthPending(new SocketTimeoutException("read timed out"))));
    }

    // 3. Sample size from the oriented crop, with a memory cap.
    @Test
    public void portraitStoredSidewaysKeepsFullDetail() {
        assertEquals(1, PhotoMath.sampleSize(4000, 3000, 90));
    }

    @Test
    public void bigLandscapeIsSampledDownButStaysSharp() {
        assertEquals(4, PhotoMath.sampleSize(8000, 4500, 0));   // 2000×1125 still covers 1920×1080
    }

    @Test
    public void panoramaIsCappedInMemory() {
        int s = PhotoMath.sampleSize(20000, 2000, 0);
        assertTrue("sample " + s, (20000L / s) * (2000L / s) <= PhotoMath.MAX_DECODE_PIXELS);
    }

    // 5. "Gambar saya" without photos.
    @Test
    public void ownWithoutPhotosNeedsPhotosOnTv() {
        assertEquals(SendGate.PICK_PHOTOS, SendGate.check(true, true, 0, false));
        assertNull(SendGate.check(true, true, 0, true));
        assertNull(SendGate.check(true, true, 3, false));
    }

    @Test
    public void allPhotosFailedIsAFailure() {
        assertEquals(SendGate.NO_PHOTO_SENT, SendGate.afterPhotos(3, 0));
        assertNull(SendGate.afterPhotos(3, 1));
        assertNull(SendGate.afterPhotos(0, 0));
    }
}
