package io.github.jpvanacor.orders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home screen widget: the current block with a live countdown, and the next one. */
public class OrdersWidget extends AppWidgetProvider {
    static final String PREFS = "orders_widget";
    static final String KEY = "blocks";
    static final String ACTION_TICK = "io.github.jpvanacor.orders.WIDGET_TICK";
    private static final int TICK_REQUEST = 7001;
    private static final int OPEN_REQUEST = 7002;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, manager, ids);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            refreshAll(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        cancelTick(context);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, OrdersWidget.class));
        if (ids != null && ids.length > 0) render(context, manager, ids);
        else cancelTick(context);
    }

    static void render(Context context, AppWidgetManager manager, int[] ids) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<WidgetSchedule.Block> blocks = WidgetSchedule.parse(prefs.getString(KEY, null));
        long now = System.currentTimeMillis();
        WidgetSchedule.Now view = WidgetSchedule.at(blocks, now);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.orders_widget);
            rv.setOnClickPendingIntent(R.id.widget_root, openApp(context));
            if (view.current == null) {
                rv.setTextViewText(R.id.w_label, "ORDERS");
                rv.setTextViewText(R.id.w_title, "Open the app");
                rv.setTextViewText(R.id.w_range, "to load your plan");
                rv.setViewVisibility(R.id.w_left, View.GONE);
                rv.setViewVisibility(R.id.w_next, View.GONE);
                rv.setInt(R.id.w_bar, "setBackgroundColor", Color.parseColor("#56636E"));
            } else {
                rv.setTextViewText(R.id.w_label, "NOW");
                rv.setTextViewText(R.id.w_title, view.current.title);
                rv.setTextViewText(R.id.w_range, hhmm(view.current.start) + "–" + hhmm(view.current.end));
                rv.setInt(R.id.w_bar, "setBackgroundColor", color(view.current.color));
                rv.setViewVisibility(R.id.w_left, View.VISIBLE);
                rv.setChronometer(R.id.w_left, SystemClock.elapsedRealtime() + (view.current.end - now), null, true);
                rv.setChronometerCountDown(R.id.w_left, true);
                if (view.next != null) {
                    rv.setViewVisibility(R.id.w_next, View.VISIBLE);
                    rv.setTextViewText(R.id.w_next, "Next  " + hhmm(view.next.start) + "  " + view.next.title);
                } else {
                    rv.setViewVisibility(R.id.w_next, View.GONE);
                }
            }
            manager.updateAppWidget(id, rv);
        }
        scheduleTick(context, WidgetSchedule.nextChange(blocks, now));
    }

    private static String hhmm(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private static int color(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return Color.parseColor("#8AAEFF");
        }
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, OPEN_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, OrdersWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, TICK_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleTick(Context context, long when) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pi = tickIntent(context);
        alarms.cancel(pi);
        if (when <= 0) return;
        long at = when + 1000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        }
    }

    private static void cancelTick(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(tickIntent(context));
    }
}
