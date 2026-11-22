package com.gathin.tvhome;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_MUTE;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** What each remote key does while the azan screen floats over the app. */
public class AzanScreenTest {
    @Test
    public void okReleaseDismisses() {
        assertEquals(AzanScreen.DISMISS, AzanScreen.keyAction(KEYCODE_DPAD_CENTER, ACTION_UP));
        assertEquals(AzanScreen.DISMISS, AzanScreen.keyAction(KEYCODE_ENTER, ACTION_UP));
        assertEquals(AzanScreen.CONSUME, AzanScreen.keyAction(KEYCODE_DPAD_CENTER, ACTION_DOWN));
    }

    @Test
    public void volumeStillWorks() {
        assertEquals(AzanScreen.PASS, AzanScreen.keyAction(KEYCODE_VOLUME_UP, ACTION_DOWN));
        assertEquals(AzanScreen.PASS, AzanScreen.keyAction(KEYCODE_VOLUME_DOWN, ACTION_DOWN));
        assertEquals(AzanScreen.PASS, AzanScreen.keyAction(KEYCODE_VOLUME_MUTE, ACTION_DOWN));
    }

    @Test
    public void otherKeysDoNotReachTheAppBehind() {
        assertEquals(AzanScreen.CONSUME, AzanScreen.keyAction(KEYCODE_BACK, ACTION_UP));
        assertEquals(AzanScreen.CONSUME, AzanScreen.keyAction(KEYCODE_DPAD_LEFT, ACTION_DOWN));
    }
}
