package dev.sequencode.udccutout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Exists only to make the system spin up our process, which is what runs
 * UdcApp.onCreate. Reached on boot, and from the module's service.sh.
 */
public class StartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        UdcApp app = UdcApp.get();
        if (app != null) app.update();
    }
}
