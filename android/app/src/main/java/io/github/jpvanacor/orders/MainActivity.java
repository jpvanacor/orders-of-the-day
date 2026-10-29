package io.github.jpvanacor.orders;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(OrdersWidgetPlugin.class);
        registerPlugin(WakeAlarmPlugin.class);
        registerPlugin(ScreenPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
