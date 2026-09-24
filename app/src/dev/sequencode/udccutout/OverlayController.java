package dev.sequencode.udccutout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The black patch that sits over the under-display camera.
 *
 * On an OLED panel a black pixel is an unlit pixel, which is exactly what the
 * stock Nubia software does while the UDC is capturing: it stops the layer
 * above the lens from shining into it.
 */
final class OverlayController implements DisplayManager.DisplayListener {

    private final Context ctx;
    private WindowManager wm;
    private final DisplayManager dm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Prefs prefs;

    /**
     * Android caps the alpha of an app's TYPE_APPLICATION_OVERLAY at
     * maximum_obscuring_opacity_for_touch (0.8) as a tapjacking mitigation, so a
     * single layer would still let a fifth of the backlight through. Stacking
     * three of them leaves 0.8 % - black enough for the lens.
     */
    private static final int LAYERS = 3;

    private final List<View> views = new ArrayList<>();
    /** Accessibility service context when one is connected; it may draw over
     *  the keyguard and at full opacity. */
    private Context host;
    private WindowManager.LayoutParams lp;
    private boolean wanted;

    OverlayController(Context ctx) {
        this.ctx = ctx;
        this.prefs = Prefs.get(ctx);
        this.wm = ctx.getSystemService(WindowManager.class);
        this.dm = ctx.getSystemService(DisplayManager.class);
        dm.registerDisplayListener(this, main);
    }

    /** Switch between the accessibility overlay and the plain app overlay. */
    void setHost(Context newHost) {
        main.post(() -> {
            host = newHost;
            wm = (newHost != null ? newHost : ctx).getSystemService(WindowManager.class);
            detach();
            apply();
        });
    }

    void setWanted(boolean b) {
        main.post(() -> {
            wanted = b;
            apply();
        });
    }

    /** Re-read the calibration and re-apply; called when settings change. */
    void refresh() {
        main.post(() -> {
            detach();
            apply();
        });
    }

    private void apply() {
        boolean show = wanted && prefs.enabled();
        if (show) {
            // Always re-create: a window that was force-hidden while a system
            // dialog owned the screen can stay hidden otherwise.
            detach();
            attach();
        } else {
            detach();
        }
    }

    private void attach() {
        boolean trusted = host != null;
        lp = new WindowManager.LayoutParams(
                trusted ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("UdcCutout");
        position();
        int layers = trusted ? 1 : LAYERS;
        for (int i = 0; i < layers; i++) {
            View v = newPatch();
            try {
                wm.addView(v, lp);
                views.add(v);
            } catch (Exception e) {
                Log.e(UdcApp.TAG, "addView failed - SYSTEM_ALERT_WINDOW not granted?", e);
                return;
            }
        }
    }

    private View newPatch() {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF000000);
        View v = new View(ctx) {
            @Override protected void onDraw(Canvas c) {
                int w = getWidth(), h = getHeight();
                if (prefs.square()) c.drawRect(0, 0, w, h, paint);
                else c.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, paint);
            }
        };
        v.setWillNotDraw(false);
        return v;
    }

    private void detach() {
        for (View v : views) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
        views.clear();
    }

    /**
     * Map the calibrated physical point into the current, possibly rotated,
     * display coordinate space. The lens does not move when the screen does.
     */
    private void position() {
        if (lp == null) return;
        Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Point nat = naturalSize(d);
        int w = nat.x, h = nat.y;

        int cx = prefs.cx(), cy = prefs.cy();
        if (cx < 0) cx = w / 2;          // horizontally centred is the safe default
        if (cy < 0) cy = Math.round(h * 0.035f);

        int size = Math.max(8, prefs.diameter());
        int r = size / 2;
        int x, y;
        switch (d.getRotation()) {
            case Surface.ROTATION_90:  x = cy;         y = w - cx;    break;
            case Surface.ROTATION_180: x = w - cx;     y = h - cy;    break;
            case Surface.ROTATION_270: x = h - cy;     y = cx;        break;
            default:                   x = cx;         y = cy;        break;
        }
        lp.x = x - r;
        lp.y = y - r;
        lp.width = size;
        lp.height = size;
        for (View v : views) {
            try {
                wm.updateViewLayout(v, lp);
            } catch (Exception ignored) {
            }
        }
    }

    /** Panel size in its natural orientation, independent of current rotation. */
    static Point naturalSize(Display d) {
        Point p = new Point();
        d.getRealSize(p);
        int rot = d.getRotation();
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            return new Point(p.y, p.x);
        }
        return p;
    }

    @Override public void onDisplayChanged(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) main.post(this::position);
    }
    @Override public void onDisplayAdded(int displayId) { }
    @Override public void onDisplayRemoved(int displayId) { }
}
