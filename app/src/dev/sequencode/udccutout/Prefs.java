package dev.sequencode.udccutout;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings, stored in device-protected storage so the overlay works before the
 * user unlocks the phone (face unlock uses the front camera on the lock screen).
 *
 * Coordinates are always physical panel pixels in the natural (portrait)
 * orientation: origin top-left, 1216 x 2688 on the RedMagic 10 Pro.
 */
final class Prefs {
    static final String PKG = "dev.sequencode.udccutout";
    private static final String FILE = "udc";

    static final String K_ENABLED = "enabled";
    static final String K_CX = "cx";
    static final String K_CY = "cy";
    static final String K_DIAMETER = "diameter";
    static final String K_SQUARE = "square";
    static final String K_ALWAYS = "always";

    private final SharedPreferences sp;

    private Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Prefs get(Context ctx) {
        Context safe = ctx.isDeviceProtectedStorage() ? ctx : ctx.createDeviceProtectedStorageContext();
        return new Prefs(safe);
    }

    boolean enabled() { return sp.getBoolean(K_ENABLED, true); }
    /** Cover the lens permanently instead of only while the front camera is open. */
    boolean always() { return sp.getBoolean(K_ALWAYS, false); }
    boolean square() { return sp.getBoolean(K_SQUARE, false); }
    int cx() { return sp.getInt(K_CX, -1); }
    /** Measured on a RedMagic 10S Pro (NX789J): 74 px below the top edge. */
    int cy() { return sp.getInt(K_CY, 74); }
    int diameter() { return sp.getInt(K_DIAMETER, 73); }

    void set(String key, int v) { sp.edit().putInt(key, v).apply(); }
    void set(String key, boolean v) { sp.edit().putBoolean(key, v).apply(); }

    void register(SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp.registerOnSharedPreferenceChangeListener(l);
    }
}
