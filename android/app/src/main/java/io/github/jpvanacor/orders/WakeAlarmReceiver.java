package io.github.jpvanacor.orders;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The alarm clock firing, plus the moments it has to be set again: a reboot, an update, a clock change. */
public class WakeAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (WakeAlarm.ACTION_STOP.equals(action)) {
            // Only offered on practice mornings.
            if (WakeAlarm.isPractice(context)) {
                context.stopService(new Intent(context, WakeAlarmService.class));
                WakeAlarm.markRinging(context, false, false);
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(WakeAlarmService.NOTIF_ID);
            }
            return;
        }
        if (WakeAlarm.ACTION_FIRE.equals(action)) {
            boolean test = intent.getBooleanExtra(WakeAlarm.EXTRA_TEST, false);
            if (test || WakeAlarm.enabled(context)) WakeAlarmService.start(context, test);
            if (!test) WakeAlarm.scheduleNext(context);
            return;
        }
        WakeAlarm.scheduleNext(context);
    }
}
