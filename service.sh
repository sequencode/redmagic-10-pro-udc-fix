#!/system/bin/sh
# late_start service: the package manager has scanned our system app by now.
PKG=dev.sequencode.udccutout

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done
sleep 10

# Drawing over other apps, and the camera preview used by the calibration screen.
cmd appops set "$PKG" SYSTEM_ALERT_WINDOW allow
pm grant "$PKG" android.permission.CAMERA

# The keyguard hides ordinary app overlays, so face unlock would get no patch.
# An accessibility overlay is drawn above the lock screen. Create
# $MODDIR/disable_a11y to keep this module from re-enabling the service.
MODDIR=${0%/*}
COMP="$PKG/$PKG.CutoutAccessibilityService"
if [ ! -f "$MODDIR/disable_a11y" ]; then
    CUR=$(settings get secure enabled_accessibility_services)
    case "$CUR" in
        *"$COMP"*) ;;
        null|"") settings put secure enabled_accessibility_services "$COMP" ;;
        *) settings put secure enabled_accessibility_services "$CUR:$COMP" ;;
    esac
    settings put secure accessibility_enabled 1
fi

# Make sure the process is up even if android:persistent did not take.
am broadcast -a dev.sequencode.udccutout.PING -n "$PKG/.StartReceiver" --user 0
