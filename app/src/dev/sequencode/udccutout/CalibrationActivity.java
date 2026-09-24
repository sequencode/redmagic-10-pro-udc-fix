package dev.sequencode.udccutout;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

/**
 * Calibration: the lens sits somewhere under the top of the panel and nothing
 * on the device says where. The top half of this screen is lit white, which is
 * the worst case for the camera, and the bottom half is that camera's live
 * view. Drag the black dot until the glare in the preview collapses - that is
 * the spot.
 */
public class CalibrationActivity extends Activity {

    private static final int REQ_CAM = 7;
    /** Fraction of the screen kept white above the panel, for the lens to see. */
    private static final float LIT = 0.45f;

    private Prefs prefs;
    private TargetView target;
    private TextView status;
    private TextureView preview;
    private Button alwaysBtn, shapeBtn, aeBtn;

    private int cx, cy, diameter, step = 5;
    private int natH;
    private boolean square, autoExposure;

    private CameraManager cm;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread camThread;
    private Handler camHandler;
    private double luma = -1, best = Double.MAX_VALUE;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        cm = getSystemService(CameraManager.class);

        Point nat = OverlayController.naturalSize(getWindowManager().getDefaultDisplay());
        natH = nat.y;
        cx = prefs.cx() >= 0 ? prefs.cx() : nat.x / 2;
        cy = prefs.cy() >= 0 ? prefs.cy() : Math.round(nat.y * 0.035f);
        diameter = prefs.diameter();
        square = prefs.square();

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        setContentView(buildUi());
        // Only valid once the decor view exists.
        WindowInsetsController ic = getWindow().getInsetsController();
        if (ic != null) {
            ic.hide(WindowInsets.Type.systemBars());
            ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM);
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        target = new TargetView(this);
        root.addView(target, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF101010);
        panel.setPadding(24, 16, 24, 24);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        panel.addView(status);

        preview = new TextureView(this);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
        });
        LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        pv.topMargin = 12;
        pv.bottomMargin = 12;
        panel.addView(preview, pv);

        // Nudge pad: fingers are not precise enough for a 5 px lens.
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.HORIZONTAL);
        pad.addView(btn("←", v -> nudge(-step, 0)));
        pad.addView(btn("→", v -> nudge(step, 0)));
        pad.addView(btn("↑", v -> nudge(0, -step)));
        pad.addView(btn("↓", v -> nudge(0, step)));
        Button stepBtn = btn("", null);
        stepBtn.setOnClickListener(v -> {
            step = step == 1 ? 5 : step == 5 ? 20 : 1;
            stepBtn.setText(step + " px");
            redraw();
        });
        stepBtn.setText(step + " px");
        pad.addView(stepBtn);
        panel.addView(pad);

        SeekBar size = new SeekBar(this);
        size.setMin(40);
        size.setMax(420);
        size.setProgress(diameter);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { diameter = p; redraw(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        panel.addView(size);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        shapeBtn = btn(square ? "Quadrat" : "Kreis", v -> {
            square = !square;
            shapeBtn.setText(square ? "Quadrat" : "Kreis");
            redraw();
        });
        row.addView(shapeBtn);
        alwaysBtn = btn("", v -> {
            prefs.set(Prefs.K_ALWAYS, !prefs.always());
            updateAlwaysLabel();
        });
        updateAlwaysLabel();
        row.addView(alwaysBtn);
        aeBtn = btn("Belichtung: fix", v -> {
            autoExposure = !autoExposure;
            aeBtn.setText(autoExposure ? "Belichtung: auto" : "Belichtung: fix");
            startPreviewRequest();
        });
        row.addView(aeBtn);
        row.addView(btn("Speichern", v -> save()));
        panel.addView(row);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(natH * (1f - LIT)), Gravity.BOTTOM);
        root.addView(panel, plp);
        return root;
    }

    private void updateAlwaysLabel() {
        alwaysBtn.setText(prefs.always() ? "Dauerhaft: an" : "Dauerhaft: aus");
    }

    private Button btn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private void nudge(int dx, int dy) {
        cx += dx;
        cy += dy;
        redraw();
    }

    private void redraw() {
        if (target != null) target.invalidate();
        if (status != null) {
            String lumaTxt = luma < 0 ? "-" : String.format(Locale.US, "%.1f", luma);
            String bestTxt = best == Double.MAX_VALUE ? "-" : String.format(Locale.US, "%.1f", best);
            status.setText("Mitte " + cx + " / " + cy + "   ⌀ " + diameter
                    + "   Streulicht " + lumaTxt + " (min " + bestTxt + ")"
                    + "\nSchwarzen Punkt so schieben, dass das Vorschaubild am klarsten wird.");
        }
    }

    private void save() {
        prefs.set(Prefs.K_CX, cx);
        prefs.set(Prefs.K_CY, cy);
        prefs.set(Prefs.K_DIAMETER, diameter);
        prefs.set(Prefs.K_SQUARE, square);
        prefs.set(Prefs.K_ENABLED, true);
        status.setText("Gespeichert: " + cx + " / " + cy + ", ⌀ " + diameter);
    }

    /** White above, so the panel over the lens is at its brightest. */
    private class TargetView extends View {
        private final Paint white = new Paint();
        private final Paint black = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);

        TargetView(android.content.Context c) {
            super(c);
            white.setColor(Color.WHITE);
            black.setColor(Color.BLACK);
            guide.setColor(0x40000000);
            guide.setStrokeWidth(2);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth();
            int lit = (int) (getHeight() * LIT);
            c.drawRect(0, 0, w, lit, white);
            float r = diameter / 2f;
            c.drawLine(0, cy, cx - r - 40, cy, guide);
            c.drawLine(cx + r + 40, cy, w, cy, guide);
            c.drawLine(cx, 0, cx, cy - r - 40, guide);
            c.drawLine(cx, cy + r + 40, cx, lit, guide);
            if (square) c.drawRect(cx - r, cy - r, cx + r, cy + r, black);
            else c.drawCircle(cx, cy, r, black);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getY() > getHeight() * LIT) return false;
            cx = Math.round(e.getX());
            cy = Math.round(e.getY());
            redraw();
            return true;
        }
    }

    // ---- camera preview -------------------------------------------------

    private void openCamera() {
        if (camera != null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Kamera-Berechtigung fehlt - ohne Vorschau kalibrieren, oder erlauben.");
            return;
        }
        UdcApp app = UdcApp.get();
        String id = app != null ? app.frontCameraId() : null;
        if (id == null) {
            status.setText("Keine Frontkamera gefunden.");
            return;
        }
        camThread = new HandlerThread("cal-cam");
        camThread.start();
        camHandler = new Handler(camThread.getLooper());
        try {
            CameraCharacteristics cc = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size ySize = smallest(map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888));
            reader = ImageReader.newInstance(ySize.getWidth(), ySize.getHeight(),
                    android.graphics.ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onFrame, camHandler);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    camera = d;
                    createSession();
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); camera = null; }
                @Override public void onError(CameraDevice d, int err) {
                    d.close();
                    camera = null;
                    runOnUiThread(() -> status.setText("Kamera-Fehler " + err));
                }
            }, camHandler);
        } catch (Throwable t) {
            status.setText("Kamera: " + t);
        }
    }

    private static Size smallest(Size[] sizes) {
        Size best = sizes[0];
        for (Size s : sizes) {
            if ((long) s.getWidth() * s.getHeight() < (long) best.getWidth() * best.getHeight()
                    && s.getWidth() >= 160) best = s;
        }
        return best;
    }

    private void createSession() {
        try {
            SurfaceTexture st = preview.getSurfaceTexture();
            st.setDefaultBufferSize(1280, 960);
            Surface ps = new Surface(st);
            camera.createCaptureSession(Arrays.asList(ps, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            startPreviewRequest();
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            runOnUiThread(() -> status.setText("Session fehlgeschlagen"));
                        }
                    }, camHandler);
        } catch (Throwable t) {
            status.setText("Session: " + t);
        }
    }

    /**
     * Fixed exposure by default: with auto exposure the camera would hide the
     * very glare we are trying to minimise, and the readings would not compare.
     */
    private void startPreviewRequest() {
        if (session == null || camera == null) return;
        try {
            CaptureRequest.Builder rb = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            rb.addTarget(new Surface(preview.getSurfaceTexture()));
            rb.addTarget(reader.getSurface());
            if (!autoExposure) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(camera.getId());
                Range<Long> exp = cc.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                Range<Integer> iso = cc.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                if (exp != null && iso != null) {
                    rb.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    rb.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp.clamp(20_000_000L));
                    rb.set(CaptureRequest.SENSOR_SENSITIVITY, iso.clamp(800));
                }
            }
            best = Double.MAX_VALUE;
            session.setRepeatingRequest(rb.build(), null, camHandler);
        } catch (Throwable t) {
            status.setText("Request: " + t);
        }
    }

    private void onFrame(ImageReader r) {
        Image img = r.acquireLatestImage();
        if (img == null) return;
        try {
            Image.Plane y = img.getPlanes()[0];
            ByteBuffer buf = y.getBuffer();
            int stride = y.getRowStride();
            int w = img.getWidth(), h = img.getHeight();
            long sum = 0;
            int n = 0;
            for (int row = 0; row < h; row += 4) {
                int base = row * stride;
                for (int col = 0; col < w; col += 4) {
                    sum += buf.get(base + col) & 0xFF;
                    n++;
                }
            }
            final double avg = n == 0 ? 0 : (double) sum / n;
            runOnUiThread(() -> {
                luma = avg;
                if (avg < best) best = avg;
                redraw();
            });
        } catch (Throwable ignored) {
        } finally {
            img.close();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        UdcApp app = UdcApp.get();
        if (app != null) app.setCalibrating(true);
        if (preview != null && preview.isAvailable()) openCamera();
    }

    /** Hand the camera back - holding it in the background would lock out the
     *  camera app and keep the overlay switched on. */
    @Override protected void onStop() {
        super.onStop();
        closeCamera();
        UdcApp app = UdcApp.get();
        if (app != null) app.setCalibrating(false);
    }

    private void closeCamera() {
        try { if (session != null) session.close(); } catch (Throwable ignored) { }
        try { if (camera != null) camera.close(); } catch (Throwable ignored) { }
        try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
        session = null;
        camera = null;
        reader = null;
        if (camThread != null) {
            camThread.quitSafely();
            camThread = null;
            camHandler = null;
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ_CAM && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) openCamera();
        else redraw();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        closeCamera();
    }
}
