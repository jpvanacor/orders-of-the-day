package io.github.jpvanacor.orders;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Rings at alarm volume, in a loop, with vibration, until the alarm screen stops it (or an hour passes). */
public class WakeAlarmService extends Service {
    static final String CHANNEL = "wake_alarm";
    static final String CHANNEL_FALLBACK = "wake_alarm_fallback";
    static final int NOTIF_ID = 7201;
    private static final int NOTIF_MISSED = 7203;
    private static final int REQ_SCREEN = 7202;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int savedVolume = -1;
    private boolean ringing;
    private String kind = WakeAlarm.KIND_WAKE;

    private final Runnable limit = new Runnable() {
        @Override
        public void run() {
            boolean wake = !WakeAlarm.KIND_WORK.equals(kind);
            stopRinging();
            if (wake) postMissed(WakeAlarmService.this);
            stopSelf();
        }
    };

    static void start(Context c, String kind, boolean test) {
        Intent svc = new Intent(c, WakeAlarmService.class).putExtra(WakeAlarm.EXTRA_TEST, test).putExtra(WakeAlarm.EXTRA_KIND, kind);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        } catch (RuntimeException e) {
            // The system refused a foreground service: ring through a plain notification instead.
            WakeAlarm.markRinging(c, true, test, kind);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                Notification n = build(c, kind, test, true);
                n.flags |= Notification.FLAG_INSISTENT;
                nm.notify(NOTIF_ID, n);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean test = intent != null && intent.getBooleanExtra(WakeAlarm.EXTRA_TEST, false);
        String k = intent != null ? intent.getStringExtra(WakeAlarm.EXTRA_KIND) : null;
        if (!ringing) kind = WakeAlarm.KIND_WORK.equals(k) ? WakeAlarm.KIND_WORK : WakeAlarm.KIND_WAKE;
        WakeAlarm.markRinging(this, true, test, kind);
        Notification n = build(this, kind, test, false);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);
        startRinging(test);
        // With the phone in use, Android shows the full-screen alarm as a banner; open the screen directly when allowed.
        try {
            startActivity(new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // the notification opens it
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRinging();
        super.onDestroy();
    }

    private void startRinging(boolean test) {
        if (ringing) return;
        ringing = true;
        WakeAlarm.markRinging(this, true, test, kind);
        boolean wake = !WakeAlarm.KIND_WORK.equals(kind);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orders:wake");
            wakeLock.acquire(WakeAlarm.limitFor(kind) + 60000);
        }
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // Only the wake alarm turns the alarm volume all the way up.
        if (audio != null && wake) {
            try {
                savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            } catch (RuntimeException e) {
                savedVolume = -1;
            }
        }
        player = play();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = { 0, 900, 700 };
            if (Build.VERSION.SDK_INT >= 26) {
                AudioAttributes va = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), va);
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
        handler.postDelayed(limit, WakeAlarm.limitFor(kind));
    }

    private MediaPlayer play() {
        Uri own = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.wake_alarm);
        Uri[] sounds = {
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            own
        };
        for (Uri u : sounds) {
            if (u == null) continue;
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                );
                mp.setDataSource(this, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                return mp;
            } catch (Exception e) {
                mp.release();
            }
        }
        return null;
    }

    private void stopRinging() {
        handler.removeCallbacks(limit);
        if (player != null) {
            try {
                player.stop();
            } catch (RuntimeException ignored) {
                // already stopped
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (savedVolume >= 0) {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            try {
                if (audio != null) audio.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0);
            } catch (RuntimeException ignored) {
                // leave it
            }
            savedVolume = -1;
        }
        if (wakeLock != null) {
            if (wakeLock.isHeld()) wakeLock.release();
            wakeLock = null;
        }
        if (ringing) WakeAlarm.markRinging(this, false, false);
        ringing = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Wake alarm", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("The morning alarm. It stops at the code by the shower.");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        NotificationChannel fb = new NotificationChannel(CHANNEL_FALLBACK, "Wake alarm (backup)", NotificationManager.IMPORTANCE_HIGH);
        fb.setDescription("Used only if the alarm can't start its own sound.");
        fb.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        );
        fb.enableVibration(true);
        fb.setVibrationPattern(new long[] { 0, 900, 700, 900, 700, 900 });
        fb.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(fb);
    }

    static PendingIntent screenIntent(Context c) {
        Intent i = new Intent(c, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return PendingIntent.getActivity(c, REQ_SCREEN, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification build(Context c, String kind, boolean test, boolean fallback) {
        channels(c);
        boolean work = WakeAlarm.KIND_WORK.equals(kind);
        PendingIntent screen = screenIntent(c);
        Notification.Builder b = builder(c, fallback ? CHANNEL_FALLBACK : CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_orders)
            .setColor(0xFF1E5BD0)
            .setContentTitle(work ? "Work time done" : test ? "Test alarm: shower first" : "Wake up. Shower first.")
            .setContentText(work ? "Stop the alarm, write tomorrow's top 3, close everything." : "Scan the code by the shower to stop it.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (work || WakeAlarm.isPractice(c)) {
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_stat_orders), work ? "Stop" : "Stop (practice)", WakeAlarm.stopIntent(c)).build());
        }
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_MAX);
            if (fallback) b.setDefaults(Notification.DEFAULT_ALL);
        }
        return b.build();
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context c, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(c, channel);
        return new Notification.Builder(c);
    }

    static void postMissed(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.cancel(NOTIF_ID);
        channels(c);
        Notification n = builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_orders)
            .setColor(0xFF1E5BD0)
            .setContentTitle("The alarm rang for an hour")
            .setContentText("No code was scanned, so it stopped on its own.")
            .setAutoCancel(true)
            .setContentIntent(WakeAlarm.showIntent(c))
            .build();
        nm.notify(NOTIF_MISSED, n);
    }

}
