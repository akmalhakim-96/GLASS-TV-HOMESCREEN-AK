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

    static synchronized void setStopper(Runnable r) {
        stopper = r;
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
