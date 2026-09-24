## v1.2

- Fixed a race that could leave the patch off: the camera callback ran on its
  own thread and read `calibrating` before the main thread's write was visible,
  so leaving the calibration screen while a camera closed switched the overlay
  off until the next event. All state now lives on the main thread.

## v1.1

- Accessibility service added, so the patch also appears on the lock screen and
  covers face unlock. The keyguard hides ordinary app overlays; a
  `TYPE_ACCESSIBILITY_OVERLAY` sits above it and is exempt from the 0.8 opacity
  cap — one layer instead of three.
- `update.sh` for deploying rebuilt APKs into the installed module.

## v1.0

- First version: a black patch over the under-display camera while a front
  camera is open, with a calibration app offering a live preview and a glare
  readout.
