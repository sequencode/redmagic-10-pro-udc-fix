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

./build.sh

# A script on the device, rather than a long su -c line: quoting a chained
# command through adb shell su -c silently drops the root context partway.
cat > /tmp/udc_deploy.sh <<'INNER'
#!/system/bin/sh
MOD=/data/adb/modules/redmagic10pro_udc_fix
set -e
cp /data/local/tmp/UdcCutout.apk "$MOD/system/app/UdcCutout/UdcCutout.apk"
chmod 644 "$MOD/system/app/UdcCutout/UdcCutout.apk"
chown 0:0 "$MOD/system/app/UdcCutout/UdcCutout.apk"
cp /data/local/tmp/service.sh "$MOD/service.sh"
chmod 755 "$MOD/service.sh"
rm -rf /data/system/package_cache/*
echo "- deployed: $(md5sum "$MOD/system/app/UdcCutout/UdcCutout.apk" | cut -c1-32)"
INNER

adb push build/UdcCutout.apk /data/local/tmp/UdcCutout.apk
adb push service.sh /data/local/tmp/service.sh
adb push /tmp/udc_deploy.sh /data/local/tmp/udc_deploy.sh
adb shell su -c 'sh /data/local/tmp/udc_deploy.sh'
echo "- Neustart"
adb reboot
