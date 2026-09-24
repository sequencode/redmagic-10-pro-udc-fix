# RedMagic 10 Pro GSI — UDC Selfie Fix

Magisk module for the **RedMagic 10 Pro / 10S Pro (NX789J)**. Built and tested
on PixelOS (Android 16); KernelSU and APatch use the same layout but were not
tested here.

The phone has a camera under the display. Stock Nubia firmware switches the
pixels above the lens off while the front camera captures — otherwise the panel
shines straight into the objective and selfies come out milky. That logic lived
in the Nubia SystemUI and is gone on a GSI: the vendor still reports the camera
(`ro.vendor.feature.camera_under_screen_sensor=true`), but nothing acts on it.

This module puts the behaviour back.

## What it does

- Installs a small system app that watches `CameraManager.AvailabilityCallback`
  to see whether anything opened a front-facing camera. It never holds the
  camera itself and needs no CAMERA permission for this.
- While one is open, a black patch covers the lens. On OLED, black means the
  pixels are off. This covers every camera app, video calls and face unlock.
- Position and size are calibrated in the bundled app *UDC Cutout*, against a
  live preview at fixed exposure with a glare readout. Defaults are measured on
  an NX789J: horizontally centred, 74 px below the top edge, 73 px across.
- The patch is stored as a physical panel coordinate and re-mapped on rotation,
  because the lens does not move with the screen content.

## Requirements

- RedMagic 10 / 10S Pro (NX789J) on a GSI. `customize.sh` warns on other
  devices but installs anyway — the position is calibratable.
- Magisk with root. Nothing else.

## Install

```bash
adb push redmagic-10-pro-udc-fix-v1.1.zip /data/local/tmp/
adb shell su -c 'magisk --install-module /data/local/tmp/redmagic-10-pro-udc-fix-v1.1.zip'
adb reboot
```

After the reboot, open *UDC Cutout* and check the position: the white area at
the top is the worst case for the camera, the preview below shows what the lens
sees. Drag the dot until the glare collapses, nudge it with the arrows
(1/5/20 px steps), set the size, then save. *Dauerhaft: an* pins the patch
permanently and is only meant for measuring.

## The lock screen, and why there is an accessibility service

The keyguard hides ordinary app overlays, so during face unlock — one of the
moments the lens most needs dark pixels — the patch would be invisible.
`FLAG_SHOW_WHEN_LOCKED` does not help; it only applies to activities.

So the module ships an accessibility service that reads nothing and does one
thing: it hosts a window of type `TYPE_ACCESSIBILITY_OVERLAY`, which is drawn
above the lock screen. `service.sh` registers it at boot without displacing
services already enabled. To opt out:

```bash
adb shell su -c 'touch /data/adb/modules/redmagic10pro_udc_fix/disable_a11y'
```

The app then falls back to `SYSTEM_ALERT_WINDOW`, which covers everything
except the lock screen.

## Two Android rules that shaped this

**Opacity.** An app's `TYPE_APPLICATION_OVERLAY` is capped at 0.8 alpha
(`maximum_obscuring_opacity_for_touch`, a tapjacking mitigation), so a single
layer would leak a fifth of the backlight. The accessibility overlay is exempt
and needs one layer; the fallback stacks three, leaving 0.2³ = 0.8 %. Measured
against a 171-grey background, the patch reads 0.

**Force-hiding.** System dialogs set `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`, and a
window can stay stuck at `mIsForceHiddenNonSystemOverlayWindow=true` afterwards.
The overlay is therefore re-created on every show rather than just toggled
visible.

## Updating the module

Use `./update.sh`, not `adb install`. Two traps, both silent:

- The app is `android:persistent`, and Android refuses to update persistent apps
  (`INSTALL_FAILED_INVALID_APK`). Without reading the output, the old version
  simply keeps running.
- A system app is only re-parsed when its `versionCode` rose **and**
  `/data/system/package_cache/` was cleared. Otherwise the APK, the module
  directory and the mount all show the new build while the package manager
  stays on the old one — `dumpsys package` gives it away via `versionCode` and
  `lastUpdateTime`.

So: raise `versionCode` in `app/AndroidManifest.xml` and `module.prop`, then
run `./update.sh`.

## Layout

```
app/          system app sources (plain framework Java, no AndroidX)
build.sh      aapt2 + javac + d8 + apksigner, no Gradle
update.sh     push a rebuilt APK into the installed module and reboot
module.prop, customize.sh, service.sh, system/   the module itself
```

`./build.sh` produces `redmagic-10-pro-udc-fix-<version>.zip`.

## Limits

- The APK is signed with a debug key. That is fine for a system app, but
  changing the signing key later needs a clean reinstall.
- The patch shows up in screenshots. Not in the camera image.
