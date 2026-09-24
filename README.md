# UDC Selfie Cutout

Magisk-Modul für das **RedMagic 10/10S Pro (NX789J)** unter einem GSI (hier
PixelOS, Android 16).

Das Gerät hat eine Kamera unter dem Display. Die Nubia-Firmware schaltet die
Pixel über der Linse ab, sobald die Frontkamera läuft — sonst leuchtet das
Panel direkt in das Objektiv und das Selfie wird milchig. Diese Logik steckte
in der Nubia-SystemUI und ist mit dem GSI verschwunden; der Vendor meldet die
UDC zwar noch (`ro.vendor.feature.camera_under_screen_sensor=true`), aber
niemand wertet das aus.

Das Modul holt genau dieses Verhalten zurück.

## Was es tut

Eine kleine System-App beobachtet über `CameraManager.AvailabilityCallback`,
ob irgendeine App eine frontseitige Kamera geöffnet hat — ohne die Kamera
selbst zu belegen und ohne CAMERA-Berechtigung. Solange das der Fall ist,
liegt ein schwarzer Punkt über der Linse. Auf OLED heißt schwarz: Pixel aus.

Das greift für jede Kamera-App, für Videochats und für Face Unlock.

## Installation

```bash
adb push udc-selfie-cutout-v1.0.zip /data/local/tmp/
adb shell su -c 'magisk --install-module /data/local/tmp/udc-selfie-cutout-v1.0.zip'
adb reboot
```

War die App vorher per `adb install` installiert, vorher entfernen —
sonst beschattet die Kopie in `/data` die System-App:

```bash
adb uninstall dev.sequencode.udccutout
```

## Sperrbildschirm und der Bedienungshilfen-Dienst

Der Keyguard blendet gewöhnliche App-Overlays aus — bei Face Unlock, also
genau dann, wenn die Kamera durch ein leuchtendes Panel schaut, wäre der Punkt
unsichtbar. `FLAG_SHOW_WHEN_LOCKED` ändert daran nichts, das Flag gilt nur für
Activities.

Darum bringt das Modul einen Bedienungshilfen-Dienst mit, der nichts ausliest
und nur eines tut: Er erlaubt ein Fenster vom Typ
`TYPE_ACCESSIBILITY_OVERLAY`. Das liegt über dem Sperrbildschirm und ist von
der Deckkraft-Grenze ausgenommen — **ein** Layer statt drei.

`service.sh` trägt den Dienst beim Booten ein, ohne vorhandene Dienste zu
verdrängen. Wer ihn nicht will:

```bash
adb shell su -c 'touch /data/adb/modules/udc_selfie_cutout/disable_a11y'
```

Dann bleibt der Fallback über `SYSTEM_ALERT_WINDOW` — deckt alles ab außer den
Sperrbildschirm.

## Kalibrierung

Die Vorgabewerte sind auf diesem Gerät gemessen: horizontal mittig, **74 px**
unter der Displaykante, **⌀ 73 px**. Ändern lässt sich das in der App
*UDC Cutout*:

- Oben ist die Fläche weiß — der ungünstigste Fall für die Kamera.
- Unten läuft die Frontkamera-Vorschau mit **fester Belichtung**, damit der
  angezeigte Streulicht-Wert über die Zeit vergleichbar bleibt.
- Punkt antippen/schieben, mit den Pfeilen feinjustieren (Schrittweite
  1/5/20 px), Größe über den Slider, dann *Speichern*.
- *Dauerhaft: an* lässt den Punkt permanent stehen — nur zum Ausmessen
  gedacht.

Die Position wird als physische Panel-Koordinate in der natürlichen
Orientierung gespeichert und bei Drehung umgerechnet: Die Linse wandert nicht
mit dem Bildschirminhalt.

## Zwei Eigenheiten von Android, die hier zählen

**Deckkraft.** Android deckelt den Alpha-Wert eines `TYPE_APPLICATION_OVERLAY`
einer normalen App auf 0,8 (`maximum_obscuring_opacity_for_touch`,
Tapjacking-Schutz). Ein einzelner Layer ließe also 20 % des Backlights durch.
Das Modul legt deshalb **drei** Fenster übereinander: 0,2³ = 0,8 % Restlicht.
Gemessen auf hellem Grund (171): im Punkt exakt 0.

**Zwangsverstecken.** Systemdialoge (z. B. Berechtigungsabfragen) setzen
`HIDE_NON_SYSTEM_OVERLAY_WINDOWS`; ein Fenster kann danach auf
`mIsForceHiddenNonSystemOverlayWindow=true` hängenbleiben. Darum wird das
Overlay bei jedem Einblenden neu erzeugt statt nur sichtbar geschaltet.

## Änderungen einspielen

`./update.sh` — nicht `adb install`. Zwei Fallen, beide still:

- Die App ist `android:persistent`; Android lehnt Updates persistenter Apps ab
  (`INSTALL_FAILED_INVALID_APK`). Ohne Blick auf die Ausgabe läuft einfach
  weiter die alte Version.
- Eine System-App wird nur neu eingelesen, wenn der `versionCode` gestiegen
  ist **und** `/data/system/package_cache/` geleert wurde. Sonst zeigen APK,
  Modulverzeichnis und Mount alle den neuen Stand, während der
  PackageManager beim alten bleibt — `dumpsys package` verrät es am
  `versionCode` und an `lastUpdateTime`.

Also: `versionCode` im Manifest erhöhen, dann `./update.sh`.

## Aufbau

```
app/          Quellen der System-App (reines Framework-Java, kein AndroidX)
build.sh      aapt2 + javac + d8 + apksigner, ohne Gradle
update.sh     gebaute APK ins installierte Modul schieben und neu starten
module/       Magisk-Modul; build.sh legt die APK nach system/app/ ab
```

`./build.sh` erzeugt `udc-selfie-cutout-<version>.zip`.

## Grenzen

- Die App ist mit dem Debug-Key signiert. Für ein System-App-Modul reicht das;
  ein Wechsel des Signaturschlüssels später erfordert aber eine Neuinstallation.
- Der Punkt ist im Screenshot sichtbar, im Kamerabild nicht.
- Bei einem ROM-Update muss das Modul neu aktiviert, nicht neu gebaut werden.
