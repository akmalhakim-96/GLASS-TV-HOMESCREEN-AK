package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.gathin.tvhome.PrayerTimes.Next;

import java.time.ZonedDateTime;

/**
 * Keeps exactly one alarm set: the next prayer whose azan is on. Called when Home starts, after every
 * azan, after a reboot, when the clock or time zone changes, and after the prayer times are refetched.
 */
final class AzanScheduler {
    private static final String TAG = "TvHome.Azan";
    static final String ACTION_AZAN = "com.gathin.tvhome.AZAN";
    static final String ACTION_REFRESH = "com.gathin.tvhome.PRAYER_REFRESH";
    static final String EXTRA_PRAYER = "prayer";
    static final String EXTRA_AT = "at";
    /** An alarm later than this after its prayer (the TV was asleep or off) is not played. */
    static final long LATE_MS = 120_000;

    private AzanScheduler() {}

    static Next nextAzan(PrayerTimes t, PrayerSettings s, ZonedDateTime now) {
        Next n = t.next(now);
        for (int i = 0; n != null && i < 12; i++) {
            if (s.azan.contains(n.prayer)) return n;
            n = t.next(n.time);
        }
        return null;
    }

    /**
     * When to try again for prayer times once there is no azan left to set: 00:05 the day after the
     * cached data ends, or in an hour when there is no data at all (no internet yet, JAKIM down).
     */
    static ZonedDateTime refreshAt(PrayerTimes t, ZonedDateTime now) {
        if (t == null) return now.plusHours(1);
        return t.lastDay().plusDays(1).atTime(0, 5).atZone(PrayerTimes.MY);
    }

    static boolean shouldPlay(long prayerAtMs, long nowMs) {
        long late = nowMs - prayerAtMs;
        return late >= 0 && late <= LATE_MS;
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PrayerSettings s = Config.load(c).prayer;
        PrayerTimes t = s.enabled() ? PrayerStore.load(c, s.zone) : null;
        Next n = t == null ? null : nextAzan(t, s, ZonedDateTime.now(PrayerTimes.MY));
        if (n == null) {
            am.cancel(pending(c, null));
            PendingIntent retry = PendingIntent.getBroadcast(c, 1,
                    new Intent(c, AzanReceiver.class).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (s.enabled()) {
                // Out of data (year end) or none yet: wake up to fetch again, then set the azan.
                long when = refreshAt(t, ZonedDateTime.now(PrayerTimes.MY)).toInstant().toEpochMilli();
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, retry);
                Log.i(TAG, "no azan in the data; refetch at " + when);
            } else {
                am.cancel(retry);
                Log.i(TAG, "prayer times off");
            }
            return;
        }
        long at = n.time.toInstant().toEpochMilli();
        PendingIntent pi = pending(c, n);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
        Log.i(TAG, "next azan: " + n.prayer + " at " + n.time);
    }

    private static PendingIntent pending(Context c, Next n) {
        Intent i = new Intent(c, AzanReceiver.class).setAction(ACTION_AZAN);
        if (n != null) i.putExtra(EXTRA_PRAYER, n.prayer.key()).putExtra(EXTRA_AT, n.time.toInstant().toEpochMilli());
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
