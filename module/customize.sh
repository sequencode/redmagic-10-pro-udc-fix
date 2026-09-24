#!/system/bin/sh
SKIPUNZIP=0

MODEL=$(getprop ro.build.product)
ui_print "- Geraet: $MODEL"
if [ "$MODEL" != "NX789J" ]; then
    ui_print "! Nicht als RedMagic 10/10S Pro (NX789J) erkannt."
    ui_print "! Modul laeuft trotzdem - Position muss kalibriert werden."
fi
if [ "$(getprop ro.vendor.feature.camera_under_screen_sensor)" = "true" ]; then
    ui_print "- Under-Display-Kamera vom Vendor bestaetigt"
fi

set_perm_recursive "$MODPATH/system" 0 0 0755 0644
ui_print "- Nach dem Neustart: App \"UDC Cutout\" oeffnen und kalibrieren."
