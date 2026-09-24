package dev.sequencode.udccutout;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Not used to observe anything - it exists because the keyguard hides ordinary
 * app overlays, so the patch would be invisible during face unlock, which is
 * one of the moments the lens needs the pixels above it dark. A window of type
 * TYPE_ACCESSIBILITY_OVERLAY is drawn above the lock screen, and is exempt from
 * the 0.8 opacity cap, so one layer is enough.
 *
 * Optional: without this service enabled the app falls back to a stack of
 * SYSTEM_ALERT_WINDOW layers, which covers everything except the lock screen.
 */
public class CutoutAccessibilityService extends AccessibilityService {

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(UdcApp.TAG, "accessibility overlay host connected");
        UdcApp app = UdcApp.get();
        if (app != null) app.setOverlayHost(this);
    }

    @Override public boolean onUnbind(Intent intent) {
        UdcApp app = UdcApp.get();
        if (app != null) app.setOverlayHost(null);
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
