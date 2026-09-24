#!/usr/bin/env bash
# Builds the app with the bare SDK tools - no Gradle, no AndroidX.
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/java/jdk-17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
OUT=build
APK_NAME=UdcCutout.apk
KS="$HOME/.android/debug.keystore"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" \
    --manifest app/AndroidManifest.xml "$OUT/res.zip" \
    --min-sdk-version 33 --target-sdk-version 36

javac --release 8 -cp "$JAR" -nowarn \
    -d "$OUT/classes" app/src/dev/sequencode/udccutout/*.java

"$BT/d8" --min-api 33 --lib "$JAR" --output "$OUT" "$OUT"/classes/dev/sequencode/udccutout/*.class

(cd "$OUT" && zip -q -j unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --ks-key-alias androiddebugkey \
    --key-pass pass:android --out "$OUT/$APK_NAME" "$OUT/aligned.apk"

mkdir -p system/app/UdcCutout
cp "$OUT/$APK_NAME" system/app/UdcCutout/$APK_NAME

VER=$(grep '^version=' module.prop | cut -d= -f2)
ZIP="redmagic-10-pro-udc-fix-$VER.zip"
rm -f "$ZIP"
zip -q -r "$ZIP" module.prop customize.sh service.sh system \
    README.md LICENSE CHANGELOG.md update.json
echo "APK  : $OUT/$APK_NAME"
echo "Modul: $ZIP"
