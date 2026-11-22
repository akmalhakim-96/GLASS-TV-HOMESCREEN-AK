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
import android.view.KeyEvent;

import com.gathin.tvhome.PrayerTimes.Prayer;

import java.io.File;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Plays one azan: asks the app in front to lower its sound, plays the owner's MP3 (azan_subuh.mp3 for
 * Subuh when there is one, else azan.mp3) while the app in front pauses, and opens the azan screen
 * (AzanActivity), which stays until OK. Without an MP3 the screen still shows, silent.
 */
public class AzanService extends Service {
    private static final String TAG = "TvHome.Azan";
    static final String ACTION_STOP = "com.gathin.tvhome.AZAN_STOP";
    private static final String CHANNEL = "azan";
    private static final long SILENT_MS = 60_000;
    private static final long MAX_MS = 10 * 60_000;
    private static final long SCREEN_WAIT_MS = 15_000;
    private static final long AFTER_AZAN_MS = 1_500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean running;
    private AzanScreen screen;
    /** Something was playing when the azan began: it got a Pause, and gets Play back after OK. */
    private boolean pausedOthers;
    /** The floating azan screen is up (Home must not raise AzanActivity over it). */
    static volatile boolean overlayUp;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foreground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (screen != null) okPressed();
            else finish();
            return START_NOT_STICKY;
        }
        if (running) {
            if (screen == null || player != null) return START_NOT_STICKY;   // this one is still playing
            clearScreen();   // the last azan's screen waited for an OK that never came: this prayer takes over
        }
        if (!AzanState.begin()) {
            // The last azan's screen is still up (nobody pressed OK): this prayer takes it over.
            AzanState.end();
            AzanState.begin();
        }
        running = true;
        handler.postDelayed(this::okPressed, MAX_MS);

        Prayer prayer = intent == null ? null : Prayer.fromKey(String.valueOf(intent.getStringExtra(AzanScheduler.EXTRA_PRAYER)));
        if (prayer == null) prayer = Prayer.MAGHRIB;
        long at = intent == null ? System.currentTimeMillis() : intent.getLongExtra(AzanScheduler.EXTRA_AT, System.currentTimeMillis());
        // Over whatever is playing, which stays where it is: OK lifts the window and the film is there.
        String time = PrayerBar.clock(java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), PrayerTimes.MY)
                .toLocalTime(), android.text.format.DateFormat.is24HourFormat(this));
        Config cfg = Config.load(this);
        screen = AzanScreen.show(this, cfg.prayer, prayer, time, cfg.reducedMotion, () -> handler.post(this::okPressed));
        if (screen != null) {
            overlayUp = true;
            AzanState.setScreen(true);
            AzanState.setStopper(() -> handler.post(this::okPressed));
        } else {
            // No permission to float over apps: the azan screen as its own activity instead.
            try {
                startActivity(new Intent(this, AzanActivity.class)
                        .putExtra(AzanScheduler.EXTRA_PRAYER, prayer.key())
                        .putExtra(AzanScheduler.EXTRA_AT, at)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (RuntimeException e) {
                Log.w(TAG, "azan screen refused: " + e);
            }
        }
        // A screen that never came up must not keep Home and Back held.
        // Its own handler: stopping the sound must not cancel this check.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!AzanState.screenShowing()) AzanState.end();
        }, SCREEN_WAIT_MS);
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
            handler.postDelayed(this::okPressed, SILENT_MS);
            return;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        // Some apps ignore losing audio focus and keep playing under the azan: press Pause for them,
        // as the remote's ⏸ would. Only when something is playing, so OK can press Play back.
        if (audio != null && audio.isMusicActive()) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            pausedOthers = true;
        }
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        if (audio != null) audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(mp3.getAbsolutePath());
            // Azan over and nobody pressed OK: a moment's pause, then the screen goes and the film carries on.
            player.setOnCompletionListener(mp -> handler.postDelayed(this::okPressed, AFTER_AZAN_MS));
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "player error " + what + "/" + extra);
                handler.postDelayed(this::okPressed, SILENT_MS);
                return true;
            });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "cannot play " + mp3 + ": " + e);
            handler.postDelayed(this::okPressed, SILENT_MS);
        }
    }

    /** OK on the floating screen: lift it, stop the sound, end the azan. */
    private void okPressed() {
        clearScreen();
        finish();
        if (pausedOthers) {
            pausedOthers = false;
            // After the azan has let go of the sound, so the film's player takes it back.
            new Handler(Looper.getMainLooper()).postDelayed(() -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY), 400);
        }
    }

    private void mediaKey(int code) {
        AudioManager am = audio != null ? audio : (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private void clearScreen() {
        if (screen != null) screen.dismiss();
        screen = null;
        overlayUp = false;
        AzanState.setScreen(false);
        AzanState.setStopper(null);
        AzanState.end();
    }

    /**
     * Stops the sound. The service ends too unless the floating screen is still up: that waits for OK,
     * even after the azan has finished.
     */
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
        if (screen != null) return;   // keep the screen (and this service) until OK
        running = false;
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
