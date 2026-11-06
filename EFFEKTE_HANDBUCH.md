<a id="english"></a>

# AmbientFX – Handbook for your own effects

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **For testing on private property only, not in public road traffic** (see [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Intervention in the HMI software, use at your own risk.

This document is meant for anyone who wants to write **their own effect** for
AmbientFX – whether as a contribution to this project or as a
private variant of their own. It describes the contract (the `Effect` interface),
all available vehicle data, and what is and is not technically possible.

For the architecture/installation see `README.md`, `docs/TECHNIK.md` and `docs/INSTALLATION_INSTALL.md` – this
is only about **writing effects**.

---

## 1. The contract: the `Effect` interface

```java
package de.mk.ambientfx;

public interface Effect {
    void start(long nowMs);
    boolean tick(long nowMs, VehicleData vehicle, Rgb[] factoryPalette, boolean daylight, Rgb contourOut);
    int periodMs();
}
```

* **`start(nowMs)`** – called once on activation, to reset the internal
  state (smoothing variables, timestamps …).
* **`tick(...)`** – called every `periodMs()` (at least 50 ms, see
  `AmbientFx.MIN_PERIOD_MS`). Write the target colour of the
  **contour** (r/g/b, 0–255) into `contourOut` and return `true` if a
  new value should be sent. `vehicle` is never `null` but can be
  “empty/invalid” (check `vehicle.isFresh(nowMs)`, see below).
* **`periodMs()`** – how smoothly your effect should be sent. 150 ms
  is a good default (all three effects so far use it).

**Since 25.09.2026 there is only one way left: plugin.** Every effect –
including Momentum, Momentum select and Atmosphere themselves – is delivered as
a `.ambiente` file (see section 6); no effect is hard-wired into the jar any
more. Adding/removing/replacing an effect therefore **never again** needs a
rebuild/redeploy of `ambient_fx.jar` –
just add or delete a `.ambiente` file on the MIB and reboot.

The built-in way (`FxCatalog.addBuiltin(...)`) still exists in the code
as a mechanism, in case an effect should ever be fixed/guaranteed available
independent of the plugin folder – but it is currently used by nobody:
```java
addBuiltin("Your effect", new EffectFactory() {
    public Effect create() { return new YourEffect(); }
});
```

In both cases the menu, the Range2D limit (how many entries the screen
shows) and the radio-button selection follow automatically – you do not
have to touch anything else for that.

**Important restrictions** (technically caused, not optional):
* There is **only one contour colour for the whole vehicle** –
  `setIntLightContourLightColor` knows no zones (no left/right, no
  front/rear controllable separately). Every effect can deliver only ONE colour per
  frame.
* The **surface is never touched** – it always stays with the manual
  colour control in the MIB. Effects act exclusively on the
  contour/line.
* **Java 1.2** – no generics, no enums, no lambdas, no
  `switch` on strings. Every interface method has to be implemented
  completely (no default methods).
* The engine suppresses sends below `AmbientFx.MIN_DELTA` (14,
  sum of the RGB channel differences) – an old retrofit LED strip in
  this vehicle reacts to very fine intermediate steps with dropouts.
  Normally this does not concern you directly, but it explains why tiny
  brightness steps sometimes do not arrive.

---

## 2. Factory colour snapping (please note if you have LEDs in the vehicle)

A retrofit LED strip in this vehicle only shows real
**Audi factory colours** cleanly – freely streamed in-between values flicker.
That is why all three effects so far compute their ideal colour continuously
but snap it **before every send** to the nearest colour from
`factoryPalette` (comes live from the vehicle, see below) – only **afterwards**
is the brightness multiplied in separately (otherwise every dimmed
colour would jump back to the full factory colour). Recommendation: copy
this pattern (see `Momentum.setDisplay()` or `Atmosphere.setDisplay()`
as a template), unless you do not have a sensitive strip and want to
deliberately test free streaming (see `MomentumSelect.TRY_BLENDED_COLORS`
for an experiment in that direction – so far unclear whether that stays
flicker-free everywhere).

```java
static Rgb nearest(Rgb target, Rgb[] palette) {
    Rgb best = palette[0]; int bestDist = Integer.MAX_VALUE;
    for (int i = 0; i < palette.length; i++) {
        int d = target.distance(palette[i]);
        if (d < bestDist) { bestDist = d; best = palette[i]; }
    }
    return best;
}
```

---

## 3. Available data – overview

Every source is its own `AbstractCarComponent` that initialises itself lazily
on the first effect start (`AmbientFx.ensureVehicleData()`).
You get `vehicle` directly as a parameter in `tick()`, everything else via
`AmbientFx.getInstance().getXxx()` (can be `null` before the first
effect was ever activated).

| Source | Access | Confirmed live in the vehicle? |
|---|---|---|
| `VehicleData` | parameter `vehicle` in `tick()` | ✅ yes (speed/throttle/brake/indicators) |
| `DaylightData` | parameter `daylight` in `tick()` | ✅ yes (light switch test) |
| `DriveModeData` | `AmbientFx.getInstance().getDriveMode()` | ⚠️ attribute confirmed, effect (Momentum select) not driven yet |
| `OutsideTempData` | `AmbientFx.getInstance().getOutsideTemp()` | ❌ only by decompiling, never seen live |
| `ParkingDistanceData` | `AmbientFx.getInstance().getParkingDistance()` | ❌ only by decompiling, never seen live |

“Only by decompiling” means: the interface demonstrably exists in the
vehicle firmware (confirmed with `javap` against your own `lsd.jar`), but whether
it actually delivers updates on **this** vehicle is still open.
Build defensively (always check `valid`, have a sensible fallback).

**Lazy loading (since 25.09.2026):** `DriveModeData`/`OutsideTempData`/
`ParkingDistanceData` only register with the vehicle on the **first** `getXxx()` call –
if your effect never calls `getParkingDistance()`, no DSI registration is
ever triggered for it. `VehicleData`/`DaylightData`
always stay active immediately (every effect gets them directly as
`tick()` parameters).

### 3.1 `VehicleData` (engine/chassis – high frequency)

```java
boolean valid; long lastUpdateMs;
int engineRpm;
float speedKmh; int speedUnit;       // 0=km/h, 1=mph (convert yourself, see Momentum.tick())
float longAccel, latAccel;           // m/s², longAccel positive=accelerating; latAccel=lateral acceleration (cornering!)
int acceleratorPct;                  // 0..100, accelerator pedal position
int powermeter;                      // power meter raw value
int shiftLight;                      // 0=off, >0=shift recommendation/flash
float brakePressure;
int currentGear, recommendedGear;
boolean driverBraking;                // driver is braking right now
boolean kickDown;                     // kickdown (full throttle/downshift request)
int gearTransMode;                    // P/R/N/D/S ...
int blinkingState;                    // 0=none, 1=left, 2=right, 3=both (hazard lights)
int activeKey;                        // which vehicle key is active, -1 = unknown (since 25.09.2026, untested)
boolean isFresh(long nowMs);          // true if data arrived within the last 3 s
```
`latAccel` (lateral acceleration when cornering) and `powermeter` are
used by no effect so far – both would be interesting candidates for
new ideas (e.g. the contour “leans” in colour in corners, or the
power meter instead of the throttle as a brightness source).

**Beware of freshness:** only `updateDynamicVehicleInfoHighFrequent` sets
`valid`/`lastUpdateMs` – `blinkingState`/`driverBraking`/`currentGear` etc.
come from a separate, rarer message and are NOT marked
“fresh” on their own. That is why all effects so far check
`vehicle.isFresh(nowMs)` once and then read all fields together
(the exact timing of the secondary values is somewhat less precise but unproblematic
in practice – Momentum does it the same way).

### 3.2 `DaylightData` (day/night)

```java
boolean valid;
boolean night;   // true = night
```
Source: the same DSI channel that CarPlay’s own day/night switching
uses (found by decompiling `NightDayModeHandler`). Light switch test
confirmed: it flips in real time.

### 3.3 `DriveModeData` (Audi Drive Select)

```java
boolean valid;
int profile;   // see constants below
```
```java
DriveModeData.PROFILE_COMFORT      = 1;
DriveModeData.PROFILE_AUTO_NORMAL  = 2;
DriveModeData.PROFILE_DYNAMIC      = 3;
DriveModeData.PROFILE_EFFICIENCY   = 5;   // "Eco" in the menu
DriveModeData.PROFILE_INDIVIDUAL   = 7;
DriveModeData.PROFILE_UNKNOWN      = -1;  // no update received yet
```

### 3.4 `OutsideTempData` (outside temperature)

```java
boolean valid;
float celsius;   // already converted, no matter whether the vehicle reports C or F internally
```
The same number as the thermometer icon in the instrument cluster display.

### 3.5 `ParkingDistanceData` (parking aid)

```java
boolean valid; long lastUpdateMs;
int frontLeft, frontLeftMiddle, frontRightMiddle, frontRight;
int rearLeft, rearLeftMiddle, rearRightMiddle, rearRight;
boolean isFresh(long nowMs);   // true if a message arrived within the last 3 s (PDC active)
int minDistance();             // smallest value across all 8 sensors
```
Unit presumably centimetres, but **unconfirmed** – cross-check before
productive use in the vehicle (let the vehicle slowly approach an
obstacle and watch the values).

---

## 4. Checked but not (yet) connected

So nobody does the same research twice:

* **Rain sensor/wipers** (`org.dsi.ifc.carcomfort.DSICarComfort`,
  `updateWiperRainSensorOnOff` etc.) – exists, but looks like a
  settings query (“is the rain sensor automatic enabled?”) rather than
  a live “it is raining right now” message. No clear
  rain intensity found. Not connected, meaning uncertain.
* **Cruise control/ACC status** – no matching DSI interface with such a
  name found. It is possible that it sits under a different service name
  or is not exposed at HMI level at all.
* **Fuel level/range, tilt angles (roll/pitch)** – the
  corresponding fields (`BCTankLevel`, `TADCurrentRollAngle`/`PitchAngle`)
  technically already run along, because `OutsideTempData` implements the complete
  `DSICarKombiListener` interface (a Java 1.2 requirement) – but the
  methods are currently empty stubs. If you need it: simply fill in the
  matching `update...` method in `OutsideTempData.java` (analogous
  to `updateBCOutsideTemperature`), no new DSI service needed.

---

## 6. Effects as a plugin (`.ambiente` file, without rebuilding the main jar)

Since 25.09.2026 there is a second way to make an effect available
without rebuilding `ambient_fx.jar` and reinstalling it completely:
a single **`.ambiente` file**. It lives ON THE MIB ITSELF (app
partition), not on the SD card – the card only serves as transport
when installing, exactly as with `ambient_fx.jar`. Reason: the card
can log off/re-enumerate in the middle of a session (see `docs/TECHNIK.md`), but effects should be there permanently, independent of
whether/which card is inserted.

**Format:** technically a perfectly normal jar with exactly two things in it –
the compiled `.class` file of your `Effect` implementation, plus a
text file `plugin.properties`:
```
class=de.mk.ambientfx.effects.YourEffect
label=Your effect
version=1
```
The `Effect`/`Rgb`/`VehicleData`/… classes themselves do NOT have to go in –
the plugin loader finds them at runtime automatically in the already running
`ambient_fx.jar` (normal classloader delegation principle).

`version` is stamped by `build_plugin.sh` automatically from `Effect.CONTRACT_VERSION`
(never enter it by hand) and has to match the currently valid number
**exactly** – a plugin with a wrong/missing version is rejected on loading
(log: “Versions-Mismatch”, i.e. version mismatch) instead of failing at runtime
with a possibly incompatible `tick()` call. If the
`Effect` contract changes (e.g. a new `tick()` parameter),
`CONTRACT_VERSION` in `Effect.java` is counted up – all `.ambiente` files built
with the old version then have to be rebuilt with the current
`build_plugin.sh`.

**Build:**
```sh
./build_java.sh                                              # if not done yet
./build_plugin.sh java_patch/de/mk/ambientfx/effects/YourEffect.java "Your effect"
# -> build/plugins/youreffect.ambiente
```
`build/plugins/` is a perfectly normal part of the project folder – it ends up on the card
automatically with the next `deploy/copy_to_sd.sh`, **no**
separate `effects/` folder on the SD card needed (the card is only
transport, as with `ambient_fx.jar` – no permanent place for
effects on the card, only on the MIB itself).

**Install:** in two steps, exactly as with `ambient_fx.jar` – first
`deploy/copy_to_sd.sh` (brings `build/plugins/*.ambiente` onto the
card), then via telnet copy from the transported location onto the MIB:
```sh
mount -uw /net/mmx/mnt/app
mkdir -p /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects
cp /net/mmx/fs/sda0/mib2q-ambient-fx/build/plugins/youreffect.ambiente /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/
mount -ur /net/mmx/mnt/app
```
Then wait for the complete MMI reboot – the folder is read in
automatically at start. Removing a `.ambiente` file again works the same way
(`mount -uw`, `rm`, `mount -ur`, reboot).

**Why no raw `.java`?** The MIB has no compiler on board (no
`javac` on the QNX box) – compiling is always done on the Mac, only
finished bytecode is loaded.

**What happens on an error:** a broken/incompatible plugin (e.g.
wrong class name in `plugin.properties`, class does not implement `Effect`,
or similar) is skipped during the scan and logged (`Log` output of
`EffectPluginLoader`) – **no crash**; the three built-in effects
(Momentum, Momentum select, Atmosphere) always keep running independent of it.
Even a completely missing/empty plugin folder (fresh
installation, no `.ambiente` copied there yet) is uncritical – then
there are simply no additional plugin entries, everything else stays as
usual.

**Limits/open points:**
* The scan runs once at MMI start (more precisely: as soon as `FxCatalog` is
  used for the first time) – a plugin copied onto the
  MIB during operation only appears in the menu after the next complete MMI reboot.
* The mechanism (`java.net.URLClassLoader`) is confirmed working by a local
  Mac test (see commit history) – **not yet tested in the vehicle itself**.
  Should it not work on the real QNX runtime environment for
  any reason, the three built-in effects remain
  completely unaffected (the plugin scan is encapsulated in its own try/catch in
  `FxCatalog`’s initialiser).

---

## 7. Checklist for a new effect

1. Your own file under `java_patch/de/mk/ambientfx/effects/YourEffect.java`,
   **no reference to other effect files** (every effect stays
   independent – see `Momentum`/`MomentumSelect`/`Atmosphere` as a model).
2. Implement `Effect`, include the factory-colour snap (section 2).
3. Use `daylight` sensibly or ignore it deliberately (like `Atmosphere`).
4. `./build_java.sh && ./verify.sh` – must give 3× “OK”.
5. `./build_plugin.sh java_patch/de/mk/ambientfx/effects/YourEffect.java "Label"`
   – builds the `.ambiente` file (section 6).
6. Test in the vehicle on private property and observe the behaviour.

---

<a id="deutsch"></a>

# AmbientFX – Handbuch für eigene Effekte

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr.

Dieses Dokument ist für jeden gedacht, der einen **eigenen Effekt** für
AmbientFX schreiben will – egal ob als Beitrag zu diesem Projekt oder als
eigene, private Variante. Es beschreibt den Vertrag (`Effect`-Interface),
alle verfügbaren Fahrzeugdaten, und was technisch geht bzw. nicht geht.

Für die Architektur/Installation siehe `README.md`, `docs/TECHNIK.md` und `docs/INSTALLATION_INSTALL.md` – hier
geht's nur ums **Effekte schreiben**.

---

## 1. Der Vertrag: `Effect`-Interface

```java
package de.mk.ambientfx;

public interface Effect {
    void start(long nowMs);
    boolean tick(long nowMs, VehicleData vehicle, Rgb[] factoryPalette, boolean daylight, Rgb contourOut);
    int periodMs();
}
```

* **`start(nowMs)`** – einmal beim Aktivieren aufgerufen, zum internen
  Zustand zurücksetzen (Glättungsvariablen, Zeitstempel …).
* **`tick(...)`** – wird alle `periodMs()` (mind. 50 ms, siehe
  `AmbientFx.MIN_PERIOD_MS`) aufgerufen. Schreibe die Zielfarbe der
  **Kontur** (r/g/b, 0–255) in `contourOut` und gib `true` zurück, wenn ein
  neuer Wert gesendet werden soll. `vehicle` ist nie `null`, kann aber
  „leer/ungültig" sein (`vehicle.isFresh(nowMs)` prüfen, siehe unten).
* **`periodMs()`** – wie flüssig dein Effekt gesendet werden soll. 150 ms
  ist ein guter Standardwert (alle drei bisherigen Effekte nutzen das).

**Seit 25.09.2026 gibt es nur noch einen Weg: Plugin.** Jeder Effekt –
auch Momentum, Momentum select und Atmosphere selbst – wird als
`.ambiente`-Datei ausgeliefert (siehe Abschnitt 6), kein Effekt ist mehr
fest im Jar verdrahtet. Ein Effekt hinzufügen/entfernen/austauschen
braucht dadurch **nie mehr** einen Neubau/Redeploy von `ambient_fx.jar` –
nur eine `.ambiente`-Datei auf dem MIB hinzufügen oder löschen und neu
starten.

Der eingebaute Weg (`FxCatalog.addBuiltin(...)`) existiert im Code
weiterhin als Mechanismus, falls mal ein Effekt fest/garantiert verfügbar
sein soll, unabhängig vom Plugin-Ordner – wird aktuell aber von niemandem
genutzt:
```java
addBuiltin("Dein Effekt", new EffectFactory() {
    public Effect create() { return new DeinEffekt(); }
});
```

In beiden Fällen ziehen Menü, Range2D-Limit (wie viele Einträge der Screen
anzeigt) und die Radio-Button-Auswahl automatisch nach – dafür musst du
sonst nichts anfassen.

**Wichtige Einschränkungen** (technisch bedingt, nicht optional):
* Es gibt **nur eine Kontur-Farbe fürs ganze Fahrzeug** –
  `setIntLightContourLightColor` kennt keine Zonen (kein Links/Rechts, kein
  Vorne/Hinten getrennt ansteuerbar). Jeder Effekt kann nur EINE Farbe pro
  Frame liefern.
* Die **Fläche wird nie angefasst** – bleibt immer dem manuellen
  Farbregler im MIB überlassen. Effekte wirken ausschließlich auf die
  Kontur/Linie.
* **Java 1.2** – keine Generics, keine Enums, keine Lambdas, keine
  `switch` auf Strings. Jede Interface-Methode muss vollständig
  implementiert werden (keine Default-Methoden).
* Die Engine unterdrückt Sendungen unterhalb `AmbientFx.MIN_DELTA` (14,
  Summe der RGB-Kanal-Differenzen) – ein alter Retrofit-LED-Streifen in
  diesem Fahrzeug reagiert auf sehr feine Zwischenschritte mit Aussetzern.
  Das betrifft dich normalerweise nicht direkt, erklärt aber, warum winzige
  Helligkeitsschritte manchmal nicht ankommen.

---

## 2. Werksfarben-Snapping (bitte beachten, wenn du LEDs am Fahrzeug hast)

Ein Retrofit-LED-Streifen in diesem Fahrzeug stellt nur echte
**Audi-Werksfarben** sauber dar – frei gestreamte Zwischenwerte flackern.
Deshalb rechnen alle drei bisherigen Effekte ihre ideale Farbe stufenlos
aus, snappen sie aber **vor jedem Senden** auf die nächstliegende Farbe aus
`factoryPalette` (kommt live vom Fahrzeug, siehe unten) – **danach** erst
wird die Helligkeit separat aufmultipliziert (sonst würde jede gedimmte
Farbe wieder auf die volle Werksfarbe zurückspringen). Empfehlung: kopiere
dieses Muster (siehe `Momentum.setDisplay()` oder `Atmosphere.setDisplay()`
als Vorlage), außer du hast keinen empfindlichen Streifen und willst
bewusst freies Streaming testen (siehe `MomentumSelect.TRY_BLENDED_COLORS`
für ein Experiment in die Richtung – bisher unklar, ob das überall
flackerfrei bleibt).

```java
static Rgb nearest(Rgb target, Rgb[] palette) {
    Rgb best = palette[0]; int bestDist = Integer.MAX_VALUE;
    for (int i = 0; i < palette.length; i++) {
        int d = target.distance(palette[i]);
        if (d < bestDist) { bestDist = d; best = palette[i]; }
    }
    return best;
}
```

---

## 3. Verfügbare Daten – Übersicht

Jede Quelle ist eine eigene `AbstractCarComponent`, die sich beim ersten
Effekt-Start selbst lazy initialisiert (`AmbientFx.ensureVehicleData()`).
`vehicle` bekommst du direkt als Parameter in `tick()`, alles andere über
`AmbientFx.getInstance().getXxx()` (kann `null` sein, bevor der erste
Effekt je aktiviert wurde).

| Quelle | Zugriff | Live im Fahrzeug bestätigt? |
|---|---|---|
| `VehicleData` | Parameter `vehicle` in `tick()` | ✅ ja (Tempo/Gas/Bremse/Blinker) |
| `DaylightData` | Parameter `daylight` in `tick()` | ✅ ja (Lichtschalter-Test) |
| `DriveModeData` | `AmbientFx.getInstance().getDriveMode()` | ⚠️ Attribut bestätigt, Effekt (Momentum select) noch nicht gefahren |
| `OutsideTempData` | `AmbientFx.getInstance().getOutsideTemp()` | ❌ nur per Decompile, noch nie live gesehen |
| `ParkingDistanceData` | `AmbientFx.getInstance().getParkingDistance()` | ❌ nur per Decompile, noch nie live gesehen |

„Nur per Decompile" heißt: die Schnittstelle existiert nachweislich in der
Fahrzeug-Firmware (per `javap` gegen die eigene `lsd.jar` bestätigt), aber ob
sie auf **diesem** Fahrzeug tatsächlich Updates liefert, ist noch offen.
Baue defensiv (immer `valid` prüfen, sinnvollen Fallback haben).

**Lazy-Loading (seit 25.09.2026):** `DriveModeData`/`OutsideTempData`/
`ParkingDistanceData` melden sich erst beim **ersten** `getXxx()`-Aufruf
beim Fahrzeug an – ruft dein Effekt `getParkingDistance()` nie auf, wird
dafür auch nie eine DSI-Anmeldung ausgelöst. `VehicleData`/`DaylightData`
bleiben immer sofort aktiv (jeder Effekt bekommt sie direkt als
`tick()`-Parameter).

### 3.1 `VehicleData` (Motor/Fahrwerk – hochfrequent)

```java
boolean valid; long lastUpdateMs;
int engineRpm;
float speedKmh; int speedUnit;       // 0=km/h, 1=mph (selbst umrechnen, siehe Momentum.tick())
float longAccel, latAccel;           // m/s², longAccel positiv=beschleunigen; latAccel=Querbeschleunigung (Kurvenfahrt!)
int acceleratorPct;                  // 0..100, Gaspedalstellung
int powermeter;                      // Leistungsmesser-Rohwert
int shiftLight;                      // 0=aus, >0=Schaltempfehlung/-blitz
float brakePressure;
int currentGear, recommendedGear;
boolean driverBraking;                // Fahrer bremst gerade
boolean kickDown;                     // Kickdown (Vollgas/Downshift-Anforderung)
int gearTransMode;                    // P/R/N/D/S ...
int blinkingState;                    // 0=keine, 1=links, 2=rechts, 3=beide (Warnblinker)
int activeKey;                        // welcher Fahrzeugschluessel aktiv ist, -1 = unbekannt (seit 25.09.2026, ungetestet)
boolean isFresh(long nowMs);          // true wenn in den letzten 3s Daten kamen
```
`latAccel` (Querbeschleunigung beim Kurvenfahren) und `powermeter` werden
bisher von keinem Effekt genutzt – beide wären interessante Kandidaten für
neue Ideen (z. B. Kontur "lehnt" sich farblich in Kurven, oder
Leistungsmesser statt Gaspedal als Helligkeitsquelle).

**Achtung Freshness:** nur `updateDynamicVehicleInfoHighFrequent` setzt
`valid`/`lastUpdateMs` – `blinkingState`/`driverBraking`/`currentGear` etc.
kommen aus einer separaten, selteneren Meldung und werden NICHT eigenständig
als "frisch" markiert. Deshalb prüfen alle bisherigen Effekte
`vehicle.isFresh(nowMs)` einmal und lesen dann alle Felder zusammen
(exaktes Timing der Nebenwerte ist etwas ungenauer, aber in der Praxis
unproblematisch – so macht's auch Momentum).

### 3.2 `DaylightData` (Tag/Nacht)

```java
boolean valid;
boolean night;   // true = Nacht
```
Quelle: derselbe DSI-Kanal, den auch CarPlays eigene Tag/Nacht-Umschaltung
nutzt (per Decompile von `NightDayModeHandler` gefunden). Lichtschalter-Test
bestätigt: kippt in Echtzeit.

### 3.3 `DriveModeData` (Audi Drive Select)

```java
boolean valid;
int profile;   // siehe Konstanten unten
```
```java
DriveModeData.PROFILE_COMFORT      = 1;
DriveModeData.PROFILE_AUTO_NORMAL  = 2;
DriveModeData.PROFILE_DYNAMIC      = 3;
DriveModeData.PROFILE_EFFICIENCY   = 5;   // "Eco" im Menü
DriveModeData.PROFILE_INDIVIDUAL   = 7;
DriveModeData.PROFILE_UNKNOWN      = -1;  // noch kein Update erhalten
```

### 3.4 `OutsideTempData` (Außentemperatur)

```java
boolean valid;
float celsius;   // bereits umgerechnet, egal ob Fahrzeug intern C oder F meldet
```
Dieselbe Zahl wie das Thermometer-Icon im Kombi-Display.

### 3.5 `ParkingDistanceData` (Einparkhilfe)

```java
boolean valid; long lastUpdateMs;
int frontLeft, frontLeftMiddle, frontRightMiddle, frontRight;
int rearLeft, rearLeftMiddle, rearRightMiddle, rearRight;
boolean isFresh(long nowMs);   // true wenn in den letzten 3s eine Meldung kam (PDC aktiv)
int minDistance();             // kleinster Wert ueber alle 8 Sensoren
```
Einheit vermutlich Zentimeter, aber **unbestätigt** – vor produktivem
Gebrauch im Fahrzeug gegenprüfen (Fahrzeug langsam an ein
Hindernis heranfahren lassen und Werte beobachten).

---

## 4. Geprüft, aber (noch) nicht angebunden

Damit niemand dieselbe Recherche doppelt macht:

* **Regensensor/Wischer** (`org.dsi.ifc.carcomfort.DSICarComfort`,
  `updateWiperRainSensorOnOff` etc.) – existiert, sieht aber nach einer
  Einstellungs-Abfrage ("ist die Regensensor-Automatik aktiviert?") statt
  einer Live-"es regnet gerade gerade"-Meldung aus. Keine klare
  Regenintensität gefunden. Nicht angebunden, Bedeutung unsicher.
* **Tempomat/ACC-Status** – keine passende DSI-Schnittstelle mit diesem
  Namen gefunden. Möglich, dass es unter einem anderen Servicenamen steckt
  oder auf HMI-Ebene gar nicht exponiert wird.
* **Tankfüllstand/Reichweite, Neigungswinkel (Rollen/Nicken)** – die
  zugehörigen Felder (`BCTankLevel`, `TADCurrentRollAngle`/`PitchAngle`)
  laufen technisch schon mit, weil `OutsideTempData` das komplette
  `DSICarKombiListener`-Interface implementiert (Java-1.2-Pflicht) – die
  Methoden sind aber aktuell leere Stubs. Wer das braucht: einfach die
  passende `update...`-Methode in `OutsideTempData.java` ausfüllen (analog
  zu `updateBCOutsideTemperature`), kein neuer DSI-Service nötig.

---

## 6. Effekte als Plugin (`.ambiente`-Datei, ohne Neubau des Haupt-Jars)

Seit 25.09.2026 gibt es einen zweiten Weg, einen Effekt verfügbar zu
machen, ohne `ambient_fx.jar` neu zu bauen und komplett neu zu installieren:
eine einzelne **`.ambiente`-Datei**. Die liegt AUF DEM MIB SELBST (App-
Partition), nicht auf der SD-Karte – die Karte dient nur als Transport
beim Installieren, genau wie bei `ambient_fx.jar` auch. Grund: die Karte
kann sich mitten in der Session abmelden/neu enumerieren (siehe `docs/TECHNIK.md`), Effekte sollen aber dauerhaft da sein, unabhängig davon,
ob/welche Karte gerade steckt.

**Format:** technisch ein ganz normales Jar mit genau zwei Dingen drin –
der kompilierten `.class`-Datei deiner `Effect`-Implementierung, plus einer
Text-Datei `plugin.properties`:
```
class=de.mk.ambientfx.effects.DeinEffekt
label=Dein Effekt
version=1
```
Die `Effect`/`Rgb`/`VehicleData`/…-Klassen selbst müssen NICHT mit rein –
die findet der Plugin-Lader zur Laufzeit automatisch im schon laufenden
`ambient_fx.jar` (normales Classloader-Delegationsprinzip).

`version` wird von `build_plugin.sh` automatisch aus `Effect.CONTRACT_VERSION`
gestempelt (nie von Hand eintragen) und muss **exakt** zur aktuell gültigen
Zahl passen – ein Plugin mit falscher/fehlender Version wird beim Laden
abgelehnt (Log: „Versions-Mismatch"), statt mit einem möglicherweise
inkompatiblen `tick()`-Aufruf zur Laufzeit zu scheitern. Ändert sich der
`Effect`-Vertrag (z. B. ein neuer `tick()`-Parameter), wird
`CONTRACT_VERSION` in `Effect.java` hochgezählt – alle mit der alten
Version gebauten `.ambiente`-Dateien müssen dann mit dem aktuellen
`build_plugin.sh` neu gebaut werden.

**Bauen:**
```sh
./build_java.sh                                              # falls noch nicht geschehen
./build_plugin.sh java_patch/de/mk/ambientfx/effects/DeinEffekt.java "Dein Effekt"
# -> build/plugins/deineffekt.ambiente
```
`build/plugins/` ist ganz normaler Teil des Projektordners – landet beim
nächsten `deploy/copy_to_sd.sh` automatisch mit auf der Karte, **kein**
eigener `effects/`-Ordner auf der SD-Karte nötig (die Karte ist nur
Transport, wie bei `ambient_fx.jar` auch – kein dauerhafter Platz für
Effekte auf der Karte, nur auf dem MIB selbst).

**Installieren:** zweistufig, genau wie bei `ambient_fx.jar` auch – erst
`deploy/copy_to_sd.sh` (bringt `build/plugins/*.ambiente` mit auf die
Karte), dann per Telnet vom transportierten Ort auf das MIB kopieren:
```sh
mount -uw /net/mmx/mnt/app
mkdir -p /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects
cp /net/mmx/fs/sda0/mib2q-ambient-fx/build/plugins/deineffekt.ambiente /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/
mount -ur /net/mmx/mnt/app
```
Danach kompletten MMI-Neustart abwarten – der Ordner wird beim Start
automatisch eingelesen. Eine `.ambiente`-Datei wieder entfernen geht genauso
(`mount -uw`, `rm`, `mount -ur`, Neustart).

**Warum kein rohes `.java`?** Das MIB hat keinen Compiler an Bord (kein
`javac` auf der QNX-Box) – kompiliert wird immer am Mac, geladen wird nur
fertiger Bytecode.

**Was bei einem Fehler passiert:** ein defektes/inkompatibles Plugin (z. B.
falscher Klassenname in `plugin.properties`, Klasse implementiert `Effect`
nicht, o. ä.) wird beim Scan übersprungen und geloggt (`Log`-Ausgabe
`EffectPluginLoader`) – **kein Absturz**, die eingebauten drei Effekte
(Momentum, Momentum select, Atmosphere) laufen immer unabhängig davon
weiter. Auch ein komplett fehlender/leerer Plugin-Ordner (frische
Installation, noch keine `.ambiente` draufkopiert) ist unkritisch – dann
gibt's einfach keine zusätzlichen Plugin-Einträge, alles andere bleibt wie
gewohnt.

**Grenzen/offene Punkte:**
* Der Scan läuft einmal beim MMI-Start (genauer: sobald `FxCatalog` zum
  ersten Mal benutzt wird) – ein Plugin, das während des Betriebs auf das
  MIB kopiert wird, taucht erst nach dem nächsten kompletten MMI-Neustart
  im Menü auf.
* Der Mechanismus (`java.net.URLClassLoader`) ist per lokalem Mac-Test
  bestätigt funktionsfähig (siehe Commit-Historie) – **im Fahrzeug selbst
  noch nicht getestet**. Sollte das auf der echten QNX-Laufzeitumgebung aus
  irgendeinem Grund nicht gehen, bleiben die drei eingebauten Effekte davon
  komplett unberührt (der Plugin-Scan ist in einem eigenen try/catch in
  `FxCatalog`s Initialisierer gekapselt).

---

## 7. Checkliste für einen neuen Effekt

1. Eigene Datei unter `java_patch/de/mk/ambientfx/effects/DeinEffekt.java`,
   **keine Referenz auf andere Effekt-Dateien** (jeder Effekt bleibt
   unabhängig – siehe `Momentum`/`MomentumSelect`/`Atmosphere` als Vorbild).
2. `Effect` implementieren, Werksfarben-Snap einbauen (Abschnitt 2).
3. `daylight` sinnvoll nutzen oder bewusst ignorieren (wie `Atmosphere`).
4. `./build_java.sh && ./verify.sh` – muss 3× "OK" liefern.
5. `./build_plugin.sh java_patch/de/mk/ambientfx/effects/DeinEffekt.java "Label"`
   – baut die `.ambiente`-Datei (Abschnitt 6).
6. Auf privatem Grundstück im Fahrzeug testen und das Verhalten beobachten.
