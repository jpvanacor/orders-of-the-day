package io.github.jpvanacor.orders;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The wake alarm. The web app hands over the wake times of the next days; one alarm clock is set at a time,
 * and the next one is set each time it fires, after a reboot or after an update.
 * It stops only when the code by the shower is scanned, or the long sentence is typed.
 */
final class WakeAlarm {
    static final String PREFS = "orders_wake";
    static final String CODE = "ORDERS-SHOWER-39FC9E7E";
    static final String SENTENCE = "I am awake and I am going to shower now";
    static final String ACTION_FIRE = "io.github.jpvanacor.orders.WAKE_FIRE";
    static final String ACTION_STOP = "io.github.jpvanacor.orders.WAKE_STOP";
    static final String EXTRA_TEST = "test";
    static final String EXTRA_KIND = "kind";
    static final String KIND_WAKE = "wake";
    /** The work timer's end: it rings like an alarm, and a plain Stop ends it. */
    static final String KIND_WORK = "work";
    static final long WORK_LIMIT_MS = 15L * 60L * 1000L;
    /** It gives up after an hour, so a phone left at home doesn't ring all day. */
    static final long RING_LIMIT_MS = 60L * 60L * 1000L;
    private static final int REQ_FIRE = 7101;
    private static final int REQ_TEST = 7102;
    private static final int REQ_SHOW = 7103;
    private static final int REQ_STOP = 7104;
    private static final int REQ_WORK = 7105;

    private WakeAlarm() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void saveNote(Context c, String note) {
        prefs(c).edit().putString("note", note == null ? "" : note).apply();
    }

    static void save(Context c, boolean enabled, boolean practice, long[] times) {
        StringBuilder sb = new StringBuilder();
        for (long t : times) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        prefs(c).edit().putBoolean("enabled", enabled).putBoolean("practice", practice).putString("times", sb.toString()).apply();
        scheduleNext(c);
    }

    /** Practice mornings: a plain Stop on the alarm screen and on the notification, while the code is new. */
    static boolean isPractice(Context c) {
        return prefs(c).getBoolean("practice", false);
    }

    static PendingIntent stopIntent(Context c) {
        Intent i = new Intent(c, WakeAlarmReceiver.class).setAction(ACTION_STOP);
        return PendingIntent.getBroadcast(c, REQ_STOP, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    /** The next wake time still ahead, or 0. */
    static long next(Context c) {
        if (!enabled(c)) return 0;
        String s = prefs(c).getString("times", "");
        if (s == null || s.isEmpty()) return 0;
        long now = System.currentTimeMillis(), best = 0;
        for (String part : s.split(",")) {
            try {
                long t = Long.parseLong(part.trim());
                if (t > now + 5000 && (best == 0 || t < best)) best = t;
            } catch (NumberFormatException ignored) {
                // skip it
            }
        }
        return best;
    }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent fire = fireIntent(c, false);
        long at = next(c);
        if (at == 0) {
            am.cancel(fire);
            return;
        }
        setClock(c, am, at, fire);
    }

    /** The minute the work timer ends, or 0 when it isn't running. */
    static void setWork(Context c, long at) {
        prefs(c).edit().putLong("workAt", at).apply();
        scheduleWork(c);
    }

    static long workAt(Context c) {
        return prefs(c).getLong("workAt", 0);
    }

    static void scheduleWork(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent fire = workIntent(c);
        long at = workAt(c);
        if (at <= System.currentTimeMillis() + 1000) {
            am.cancel(fire);
            return;
        }
        setClock(c, am, at, fire);
    }

    static PendingIntent workIntent(Context c) {
        Intent i = new Intent(c, WakeAlarmReceiver.class).setAction(ACTION_FIRE).putExtra(EXTRA_KIND, KIND_WORK);
        return PendingIntent.getBroadcast(c, REQ_WORK, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static long scheduleTest(Context c, long delayMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long at = System.currentTimeMillis() + delayMs;
        if (am != null) setClock(c, am, at, fireIntent(c, true));
        return at;
    }

    private static void setClock(Context c, AlarmManager am, long at, PendingIntent fire) {
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(c)), fire);
        } catch (SecurityException e) {
            // No exact alarms allowed: the closest the system still permits.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    static PendingIntent fireIntent(Context c, boolean test) {
        Intent i = new Intent(c, WakeAlarmReceiver.class).setAction(ACTION_FIRE).putExtra(EXTRA_TEST, test);
        return PendingIntent.getBroadcast(c, test ? REQ_TEST : REQ_FIRE, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent showIntent(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, REQ_SHOW, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    static boolean canFullScreen(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.canUseFullScreenIntent();
    }

    static boolean notificationsOn(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    static void markRinging(Context c, boolean ringing, boolean test) {
        markRinging(c, ringing, test, KIND_WAKE);
    }

    static void markRinging(Context c, boolean ringing, boolean test, String kind) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean("ringing", ringing);
        if (ringing) e.putLong("ringStart", System.currentTimeMillis()).putBoolean("ringTest", test).putString("ringKind", kind);
        e.apply();
    }

    static String ringKind(Context c) {
        String k = prefs(c).getString("ringKind", KIND_WAKE);
        return k == null ? KIND_WAKE : k;
    }

    static long limitFor(String kind) {
        return KIND_WORK.equals(kind) ? WORK_LIMIT_MS : RING_LIMIT_MS;
    }

    static boolean isRinging(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("ringing", false) && System.currentTimeMillis() - p.getLong("ringStart", 0) < limitFor(ringKind(c)) + 60000;
    }

    /**
     * How each real wake alarm ended, newest first, for the wake ladder in the app:
     * "day|ringStart|stopAt|how" joined by ';', how = scan, sentence, practice, timeout. Tests are not logged.
     */
    static void logWake(Context c, String how) {
        SharedPreferences p = prefs(c);
        if (KIND_WORK.equals(ringKind(c)) || p.getBoolean("ringTest", false)) return;
        long start = p.getLong("ringStart", 0);
        if (start <= 0) return;
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(start));
        String entry = day + "|" + start + "|" + System.currentTimeMillis() + "|" + how;
        String old = p.getString("wakeLog", "");
        StringBuilder sb = new StringBuilder(entry);
        int kept = 0;
        if (old != null && !old.isEmpty()) {
            for (String e : old.split(";")) {
                if (e.isEmpty() || e.startsWith(day + "|") || kept >= 13) continue;
                sb.append(';').append(e);
                kept++;
            }
        }
        p.edit().putString("wakeLog", sb.toString()).apply();
    }

    static String wakeLog(Context c) {
        String s = prefs(c).getString("wakeLog", "");
        return s == null ? "" : s;
    }

    /** A line from the app for the alarm screen, like how far the next win moves the alarm. */
    static String note(Context c) {
        String s = prefs(c).getString("note", "");
        return s == null ? "" : s;
    }

    /** Scanning by this minute counts as a win: ten minutes after it started ringing. */
    static String deadline(Context c) {
        long start = prefs(c).getLong("ringStart", 0);
        if (start <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(start + 10L * 60L * 1000L));
    }

    static boolean isTest(Context c) {
        return prefs(c).getBoolean("ringTest", false);
    }

    static boolean matches(String scanned) {
        return scanned != null && CODE.equals(scanned.trim());
    }

    static boolean sentenceMatches(String typed) {
        return norm(typed).equals(norm(SENTENCE));
    }

    static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }
}
