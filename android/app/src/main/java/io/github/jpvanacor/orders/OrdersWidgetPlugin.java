package io.github.jpvanacor.orders;

import android.content.Context;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Lets the web app hand the widget the next days of blocks. */
@CapacitorPlugin(name = "OrdersWidget")
public class OrdersWidgetPlugin extends Plugin {
    @PluginMethod
    public void update(PluginCall call) {
        Context context = getContext();
        context.getSharedPreferences(OrdersWidget.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(OrdersWidget.KEY, call.getString("blocks", ""))
            .apply();
        OrdersWidget.refreshAll(context);
        call.resolve();
    }
}
