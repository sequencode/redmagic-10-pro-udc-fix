#!/usr/bin/env bash
# Deploy a rebuilt APK into the installed module and reboot.
#
# adb install does not work here: the app declares android:persistent, and
# Android refuses to update persistent apps ("Persistent apps are not
# updateable"). And a system app is only re-parsed when its versionCode rose
# AND the package parse cache is gone - otherwise the old APK keeps running
# while every file on disk already shows the new one.
set -euo pipefail
cd "$(dirname "$0")"

MOD=/data/adb/modules/redmagic10pro_udc_fix
./build.sh

adb push build/UdcCutout.apk /data/local/tmp/UdcCutout.apk
adb push service.sh /data/local/tmp/service.sh
adb shell su -c "cp /data/local/tmp/UdcCutout.apk $MOD/system/app/UdcCutout/UdcCutout.apk \
    && chmod 644 $MOD/system/app/UdcCutout/UdcCutout.apk \
    && chown 0:0 $MOD/system/app/UdcCutout/UdcCutout.apk \
    && cp /data/local/tmp/service.sh $MOD/service.sh \
    && chmod 755 $MOD/service.sh \
    && rm -rf /data/system/package_cache/*"
echo "- Neustart"
adb reboot
