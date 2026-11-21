package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * Whether an azan is playing, shared by AzanService (which plays it) and HomeKeyService (which sees
 * every remote key). While it plays, the first OK press stops it and goes no further; every other key
 * reaches the app in front as usual.
 */
final class AzanState {
    private static boolean active;
    /** An OK press was swallowed on its way down; swallow its release too. */
    private static boolean pendingUp;
    private static Runnable stopper;
    /** The azan screen is showing (AzanActivity), so Home can bring it back to the front. */
    private static boolean screen;

    private AzanState() {}

    static synchronized boolean active() { return active; }

    /** False when an azan is already playing, so a second alarm never starts a second player. */
    static synchronized boolean begin() {
        if (active) return false;
        active = true;
        return true;
    }

    static synchronized void end() {
        active = false;
        pendingUp = false;   // never leave a stale press that would eat a later OK
    }

    static synchronized void setScreen(boolean showing) {
        screen = showing;
    }

    static synchronized boolean screenShowing() { return screen; }

    static synchronized void setStopper(Runnable r) {
        stopper = r;
    }

    /** While the azan screen shows, Home and Back wait for OK: the screen stays until it is pressed. */
    static synchronized boolean swallowNav(int keyCode) {
        if (!active) return false;
        return keyCode == KeyEvent.KEYCODE_ALL_APPS || keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BACK;
    }

    static boolean swallowOk(int keyCode, int action) {
        Runnable stop = null;
        synchronized (AzanState.class) {
            boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (!ok) return false;
            if (action == KeyEvent.ACTION_DOWN) {
                if (!active && !pendingUp) return false;
                pendingUp = true;
                return true;
            }
            if (action != KeyEvent.ACTION_UP || !pendingUp) return false;
            pendingUp = false;
            if (active) stop = stopper;
        }
        if (stop != null) stop.run();
        return true;
    }
}
