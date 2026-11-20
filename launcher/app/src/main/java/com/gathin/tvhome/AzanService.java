package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Plays one azan: asks the app in front to lower its sound, plays the owner's MP3 (azan_subuh.mp3 for
 * Subuh when there is one, else azan.mp3), shows the overlay, and undoes all of it when the MP3 ends or
 * OK is pressed. Without an MP3 the overlay stays for a minute, silent.
 */
public class AzanService extends Service {
    private static final String TAG = "TvHome.Azan";
    static final String ACTION_STOP = "com.gathin.tvhome.AZAN_STOP";
    private static final String CHANNEL = "azan";
    private static final long SILENT_MS = 60_000;
    private static final long MAX_MS = 10 * 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private AzanOverlay overlay;
    private boolean reducedMotion;
    private boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foreground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;   // this one is already playing
        if (!AzanState.begin()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        running = true;
        AzanState.setStopper(() -> handler.post(this::finish));
        handler.postDelayed(this::finish, MAX_MS);

        Config cfg = Config.load(this);
        reducedMotion = cfg.reducedMotion;
        Prayer prayer = intent == null ? null : Prayer.fromKey(String.valueOf(intent.getStringExtra(AzanScheduler.EXTRA_PRAYER)));
        if (prayer == null) prayer = Prayer.MAGHRIB;
        long at = intent == null ? System.currentTimeMillis() : intent.getLongExtra(AzanScheduler.EXTRA_AT, System.currentTimeMillis());
        java.time.LocalTime t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), PrayerTimes.MY).toLocalTime();
        String time = java.time.format.DateTimeFormatter.ofPattern(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm a", java.util.Locale.US).format(t);
        overlay = AzanOverlay.show(this, cfg.prayer, prayer, time, reducedMotion);
        play(prayer);
        return START_NOT_STICKY;
    }

    private void play(Prayer prayer) {
        File dir = Config.dir(this);
        File subuh = dir == null ? null : new File(dir, "azan_subuh.mp3");
        File any = dir == null ? null : new File(dir, "azan.mp3");
        File mp3 = prayer == Prayer.SUBUH && subuh != null && subuh.isFile() ? subuh : any;
        if (mp3 == null || !mp3.isFile()) {
            Log.i(TAG, "no azan.mp3: showing the reminder silently");
            handler.postDelayed(this::finish, SILENT_MS);
            return;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        if (audio != null) audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(mp3.getAbsolutePath());
            player.setOnCompletionListener(mp -> finish());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "player error " + what + "/" + extra);
                finish();
                return true;
            });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "cannot play " + mp3 + ": " + e);
            handler.postDelayed(this::finish, SILENT_MS);
        }
    }

    private void finish() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        focus = null;
        if (overlay != null) overlay.remove(reducedMotion);
        overlay = null;
        if (running) {
            running = false;
            AzanState.setStopper(null);
            AzanState.end();
        }
        stopForeground(true);
        stopSelf();
    }

    private void foreground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.azan_channel),
                    NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.azan_channel))
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public void onDestroy() {
        if (running) finish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
