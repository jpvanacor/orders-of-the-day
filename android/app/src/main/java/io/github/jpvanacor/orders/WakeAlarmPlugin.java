package io.github.jpvanacor.orders;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Lets the web app set the wake alarm, test it, and see what Android still has to allow. */
@CapacitorPlugin(name = "WakeAlarm", permissions = { @Permission(alias = "camera", strings = { Manifest.permission.CAMERA }) })
public class WakeAlarmPlugin extends Plugin {
    @PluginMethod
    public void setSchedule(PluginCall call) {
        boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        boolean practice = Boolean.TRUE.equals(call.getBoolean("practice", false));
        JSArray arr = call.getArray("times");
        int n = arr == null ? 0 : arr.length();
        long[] times = new long[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            long t = arr.optLong(i, 0);
            if (t > 0) times[k++] = t;
        }
        long[] kept = new long[k];
        System.arraycopy(times, 0, kept, 0, k);
        WakeAlarm.save(getContext(), enabled, practice, kept);
        call.resolve(status());
    }

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void test(PluginCall call) {
        long at = WakeAlarm.scheduleTest(getContext(), 10000);
        JSObject r = status();
        r.put("testAt", at);
        call.resolve(r);
    }

    @PluginMethod
    public void requestCamera(PluginCall call) {
        if (hasCamera()) {
            call.resolve(status());
            return;
        }
        requestPermissionForAlias("camera", call, "cameraResult");
    }

    @PermissionCallback
    private void cameraResult(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        String what = call.getString("what", "app");
        Context c = getContext();
        Uri pkg = Uri.parse("package:" + c.getPackageName());
        Intent i;
        if ("fullscreen".equals(what) && Build.VERSION.SDK_INT >= 34) i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg);
        else if ("exact".equals(what) && Build.VERSION.SDK_INT >= 31) i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg);
        else i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            call.resolve();
        } catch (RuntimeException e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                call.resolve();
            } catch (RuntimeException e2) {
                call.reject("Could not open Android's settings");
            }
        }
    }

    private boolean hasCamera() {
        return getContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private JSObject status() {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("enabled", WakeAlarm.enabled(c));
        r.put("practice", WakeAlarm.isPractice(c));
        r.put("next", WakeAlarm.next(c));
        r.put("ringing", WakeAlarm.isRinging(c));
        r.put("exact", WakeAlarm.canExact(c));
        r.put("fullScreen", WakeAlarm.canFullScreen(c));
        r.put("notifications", WakeAlarm.notificationsOn(c));
        r.put("camera", hasCamera());
        return r;
    }
}
