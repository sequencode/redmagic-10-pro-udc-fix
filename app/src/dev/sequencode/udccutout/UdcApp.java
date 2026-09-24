package dev.sequencode.udccutout;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs for the whole uptime (the app is installed as a system app and declares
 * android:persistent) and watches whether anything is using a front-facing
 * camera. CameraManager.AvailabilityCallback reports that without holding the
 * camera itself and without the CAMERA permission.
 */
public class UdcApp extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {

    static final String TAG = "UdcCutout";

    private static UdcApp instance;

    private OverlayController overlay;
    private Prefs prefs;
    private CameraManager cm;
    /**
     * Everything that decides whether the patch is up runs here. The camera
     * callback used to run on its own thread and raced setCalibrating(): it
     * read a stale value, computed the opposite answer and, posting last, won.
     */
    private final Handler main = new Handler(Looper.getMainLooper());
    private Handler bg;
    private final Set<String> frontIds = new HashSet<>();
    private final Set<String> busy = new HashSet<>();
    /** Suppressed while the calibration screen draws its own target. */
    private boolean calibrating;

    static UdcApp get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        Context safe = createDeviceProtectedStorageContext();
        prefs = Prefs.get(safe);
        prefs.register(this);
        overlay = new OverlayController(safe);

        HandlerThread t = new HandlerThread("udc");
        t.start();
        bg = new Handler(t.getLooper());
        cm = getSystemService(CameraManager.class);
        bg.post(this::startWatching);
        Log.i(TAG, "started");
    }

    private void startWatching() {
        if (!findFrontCameras()) {
            // Early in boot the camera service may not be up yet.
            bg.postDelayed(this::startWatching, 5000);
            return;
        }
        // Posting hands frontIds over safely and puts the callback on main.
        main.post(() -> {
            cm.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
                @Override public void onCameraAvailable(String id) {
                    if (busy.remove(id)) update();
                }
                @Override public void onCameraUnavailable(String id) {
                    if (frontIds.contains(id) && busy.add(id)) update();
                }
            }, main);
            update();
        });
    }

    private boolean findFrontCameras() {
        try {
            frontIds.clear();
            for (String id : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) frontIds.add(id);
            }
        } catch (Throwable e) {
            Log.w(TAG, "camera list not ready: " + e);
            return false;
        }
        if (frontIds.isEmpty()) return false;
        Log.i(TAG, "front cameras: " + frontIds);
        return true;
    }

    /** Front camera id for the calibration preview, or null. */
    String frontCameraId() {
        if (frontIds.isEmpty()) findFrontCameras();
        return frontIds.isEmpty() ? null : frontIds.iterator().next();
    }

    /** Called by the accessibility service as it connects and disconnects. */
    void setOverlayHost(Context host) {
        overlay.setHost(host);
    }

    void setCalibrating(boolean b) {
        calibrating = b;
        update();
    }

    void update() {
        boolean want = !calibrating && (prefs.always() || !busy.isEmpty());
        Log.i(TAG, "update want=" + want + " busy=" + busy + " front=" + frontIds
                + " calibrating=" + calibrating + " enabled=" + prefs.enabled()
                + " always=" + prefs.always());
        overlay.setWanted(want);
    }

    /** Preview of a not-yet-saved calibration, driven by the calibration screen. */
    OverlayController overlay() { return overlay; }

    @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        overlay.refresh();
        update();
    }
}
