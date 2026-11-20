package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.gathin.tvhome.PrayerTimes.Next;

import java.time.ZonedDateTime;

/**
 * The azan alarm, plus the system events after which the alarm must be set again (boot, clock or time
 * zone change, app update). AZAN_TEST plays the azan now with the next prayer's name, for checking
 * the setup: am broadcast -n com.gathin.tvhome/.AzanReceiver -a com.gathin.tvhome.AZAN_TEST
 */
public class AzanReceiver extends BroadcastReceiver {
    private static final String TAG = "TvHome.Azan";
    static final String ACTION_TEST = "com.gathin.tvhome.AZAN_TEST";
    /** AzanService is started by name so this receiver stays independent of it. */
    static final String SERVICE = "com.gathin.tvhome.AzanService";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (AzanScheduler.ACTION_AZAN.equals(action)) {
            long at = intent.getLongExtra(AzanScheduler.EXTRA_AT, 0);
            String prayer = intent.getStringExtra(AzanScheduler.EXTRA_PRAYER);
            if (prayer != null && AzanScheduler.shouldPlay(at, System.currentTimeMillis())) play(c, prayer, at);
            else Log.i(TAG, "skipped late or empty alarm for " + prayer);
        } else if (ACTION_TEST.equals(action)) {
            PrayerSettings s = Config.load(c).prayer;
            PrayerTimes t = s.enabled() ? PrayerStore.load(c, s.zone) : null;
            Next n = t == null ? null : t.next(ZonedDateTime.now(PrayerTimes.MY));
            play(c, n == null ? PrayerTimes.Prayer.MAGHRIB.key() : n.prayer.key(),
                    n == null ? System.currentTimeMillis() : n.time.toInstant().toEpochMilli());
            return;
        }
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AzanScheduler.schedule(c);
                String zone = Config.load(c).prayer.zone;
                if (PrayerStore.dueNow(c, zone) && PrayerStore.refresh(c, zone)) AzanScheduler.schedule(c);
            } finally {
                result.finish();
            }
        }, "azan-schedule").start();
    }

    private static void play(Context c, String prayer, long at) {
        Intent i = new Intent().setClassName(c, SERVICE)
                .putExtra(AzanScheduler.EXTRA_PRAYER, prayer).putExtra(AzanScheduler.EXTRA_AT, at);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not start the azan: " + e);
        }
    }
}
