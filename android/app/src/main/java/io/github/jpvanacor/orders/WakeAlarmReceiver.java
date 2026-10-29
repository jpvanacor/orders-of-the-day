package io.github.jpvanacor.orders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The alarm clock firing, plus the moments it has to be set again: a reboot, an update, a clock change. */
public class WakeAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (WakeAlarm.ACTION_FIRE.equals(action)) {
            boolean test = intent.getBooleanExtra(WakeAlarm.EXTRA_TEST, false);
            if (test || WakeAlarm.enabled(context)) WakeAlarmService.start(context, test);
            if (!test) WakeAlarm.scheduleNext(context);
            return;
        }
        WakeAlarm.scheduleNext(context);
    }
}
