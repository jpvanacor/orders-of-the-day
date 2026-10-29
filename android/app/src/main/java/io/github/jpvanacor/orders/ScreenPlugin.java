package io.github.jpvanacor.orders;

import android.app.Activity;
import android.view.WindowManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Keeps the screen on while Orders is open (the web Wake Lock API isn't available inside the app). */
@CapacitorPlugin(name = "Screen")
public class ScreenPlugin extends Plugin {
    @PluginMethod
    public void keepOn(final PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("The app window isn't ready");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (on) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                JSObject r = new JSObject();
                r.put("on", on);
                call.resolve(r);
            }
        });
    }
}
