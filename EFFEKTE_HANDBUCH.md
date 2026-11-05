# AmbientFX – Handbuch für eigene Effekte

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr. Dieses Dokument ist auf Deutsch, der englische Überblick steht in der [README](README.md).

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
