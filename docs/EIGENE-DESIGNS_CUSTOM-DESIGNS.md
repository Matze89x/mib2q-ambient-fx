<a id="english"></a>

# Create your own designs (effects)

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **For testing on private property only, not in public road traffic** – see [RECHT_LEGAL.md](RECHT_LEGAL.md). Your own effects run **unchecked** in the vehicle: you are responsible for making sure they do not dazzle, distract or irritate others.

This repository contains **no colour data or designs from your vehicle**. AmbientFX reads the factory colours from your vehicle at runtime. **You create your own designs yourself**, as described here.

## The principle

A design is an **effect**: a small Java class implementing `de.mk.ambientfx.Effect`. AmbientFX calls it about every `periodMs()` milliseconds and takes the wanted **contour colour** from it. You get the vehicle data (speed, throttle, brake, gear, day/night, drive select profile, outside temperature …) as parameters.

```java
public interface Effect {
    int CONTRACT_VERSION = 1;
    void start(long nowMs);                          // on activation: reset state
    boolean tick(long nowMs, VehicleData vehicle,    // per frame: write the wanted colour into contourOut
                 Rgb[] factoryPalette, boolean daylight, Rgb contourOut);   // true = send a new value
    int periodMs();                                   // send interval in ms
}
```

An effect only drives the **contour**; the surface stays with the normal colour control in the MMI.

## Step by step

1. **Set up the build environment**, once: [BAUEN_BUILD.md](BAUEN_BUILD.md) (build the jar so the compiler finds `Effect`, `Rgb`, `VehicleData`).
2. **Copy the example:**
   ```bash
   cp examples/MeinEffekt.java java_patch/de/mk/ambientfx/effects/MeinEffekt.java
   ```
3. **Adapt it:** class name = file name. The example “breathes” calmly and the hue follows the speed.
4. **Build as a plugin:**
   ```bash
   ./build_plugin.sh java_patch/de/mk/ambientfx/effects/MeinEffekt.java "My effect"
   # -> build/plugins/meineffekt.ambiente
   ```
   The display name (“My effect”) appears in the MMI menu. `build_plugin.sh` stamps the contract version automatically.
5. **Install:** a new plugin goes onto the unit **together with the jar and all other plugins** ([INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)). The SD menu automatically takes **all** `.ambiente` files from `build/plugins/`. If you emptied the folder, rebuild the bundled effects first with `tools/build_all_plugins.sh`.
6. **Reboot.** Plugins are read when the MMI starts. The new entry appears under **Ambient lighting**, after the existing entries.

A broken or incompatible plugin is skipped when read, without a crash. The built-in effects keep running.

## Available data (short)

| Source | Access | Content |
|---|---|---|
| `VehicleData` | parameter `vehicle` | `speedKmh` (mph if `speedUnit == 1`), `acceleratorPct` 0–100, `brakePressure`, `driverBraking`, `currentGear`, `gearTransMode` (4 = D, 5 = S), `engineRpm`, `longAccel`, `latAccel`, `wheelAngle`, `blinkingState`, `kickDown`, `isFresh(nowMs)` |
| Day/night | parameter `daylight` | `true` = day, `false` = night |
| `Rgb[] factoryPalette` | parameter | the factory colour list reported by the vehicle (can be empty) |
| `DriveModeData` | `AmbientFx.getInstance().getDriveMode()` | `profile` (Comfort, Auto, Dynamic, Efficiency, Individual) |
| `OutsideTempData` | `AmbientFx.getInstance().getOutsideTemp()` | `celsius` |
| `ParkingDistanceData` | `AmbientFx.getInstance().getParkingDistance()` | eight sensor values (untested) |

All details, units and pitfalls are in the [effects handbook](../EFFEKTE_HANDBUCH.md). **Check `valid`/`isFresh()` and provide a sensible fallback.**

## Factory colours and brightness

Freely streamed in-between colours can flicker on some LED strips. So the proven pattern is: **compute the ideal colour continuously, snap it to the nearest factory colour (`Rgb.nearest(...)`), and only then multiply in the brightness** (`Rgb.scale(...)`). Example: [`examples/MeinEffekt.java`](../examples/MeinEffekt.java). See how the bundled effects do it in `java_patch/de/mk/ambientfx/effects/`.

## Rules for effects

- **Java 1.2:** no generics, enums, lambdas, no `StringBuilder`.
- **Every effect file is self-contained**, no references to other effect classes.
- **Dim at night** (`daylight == false`) and **no fast flashes** or hard bright/dark changes.
- **No indicator imitation** and nothing that could look like a warning lamp or a brake light.
- Expect **any** vehicle and firmware variant that reacts differently from what you expect.

## Checklist

1. Your own file under `java_patch/de/mk/ambientfx/effects/`.
2. Implement `Effect`, include the factory-colour snap, use `daylight` deliberately.
3. `./build_java.sh && ./verify.sh` (must report “only expected differences” and “all ok”).
4. `./build_plugin.sh …` and test on **private property**.
5. Install the jar and **all** plugins together.

## Without programming: the Design Studio

For simple designs (colour ramp over speed, throttle, steering angle or temperature, rainbow, brightness, breathing) you do not need a single line of Java: the **[Design Studio](../designer/README.md)** is an editor in your browser (one HTML file, works offline) that can **create, import, edit and export as `.ambiente`**. Since **version 1.1** it can also open the five bundled effects as **original templates** and change all of their settings, including gear-lever flash and drive-profile colour ranges. It is **experimental** and tested on a computer so far. For completely new logic you still write an effect in Java as above.

---

<a id="deutsch"></a>

# Eigene Designs (Effekte) erstellen

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** – siehe [RECHT_LEGAL.md](RECHT_LEGAL.md). Eigene Effekte laufen im Fahrzeug **ungeprüft**: Du bist selbst dafür verantwortlich, dass sie nicht blenden, ablenken oder andere irritieren.

In diesem Repository sind **keine Farbdaten oder Designs deines Fahrzeugs** hinterlegt. Die Werksfarben liest AmbientFX zur Laufzeit aus deinem Fahrzeug. **Eigene Designs erstellst du selbst**, so wie hier beschrieben.

## Das Prinzip

Ein Design ist ein **Effekt**: eine kleine Java-Klasse, die `de.mk.ambientfx.Effect` implementiert. AmbientFX ruft sie etwa alle `periodMs()` Millisekunden auf und bekommt die gewünschte **Kontur-Farbe** zurück. Die Daten des Fahrzeugs (Tempo, Gas, Bremse, Gang, Tag/Nacht, Drive-Select-Profil, Außentemperatur …) bekommst du als Parameter.

```java
public interface Effect {
    int CONTRACT_VERSION = 1;
    void start(long nowMs);                          // beim Aktivieren: Zustand zurücksetzen
    boolean tick(long nowMs, VehicleData vehicle,    // pro Frame: Wunschfarbe in contourOut schreiben
                 Rgb[] factoryPalette, boolean daylight, Rgb contourOut);   // true = neuen Wert senden
    int periodMs();                                   // Sende-Intervall in ms
}
```

Ein Effekt steuert **nur die Kontur**, die Fläche bleibt beim normalen Farbregler im MMI.

## Schritt für Schritt

1. **Bauumgebung einrichten**, einmalig: [BAUEN_BUILD.md](BAUEN_BUILD.md) (Jar bauen, damit der Compiler `Effect`, `Rgb`, `VehicleData` findet).
2. **Beispiel kopieren:**
   ```bash
   cp examples/MeinEffekt.java java_patch/de/mk/ambientfx/effects/MeinEffekt.java
   ```
3. **Anpassen:** Klassenname = Dateiname. Das Beispiel „atmet“ ruhig, der Farbton folgt dem Tempo.
4. **Als Plugin bauen:**
   ```bash
   ./build_plugin.sh java_patch/de/mk/ambientfx/effects/MeinEffekt.java "Mein Effekt"
   # -> build/plugins/meineffekt.ambiente
   ```
   Der Anzeigename („Mein Effekt“) erscheint im MMI-Menü. `build_plugin.sh` stempelt die Vertragsversion automatisch ein.
5. **Installieren:** Ein neues Plugin gehört **zusammen mit der Jar und allen anderen Plugins** auf das Gerät ([INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)). Das SD-Menü nimmt automatisch **alle** `.ambiente`-Dateien aus `build/plugins/` mit. Baue vorher mit `tools/build_all_plugins.sh` die mitgelieferten Effekte neu, wenn du das Verzeichnis geleert hast.
6. **Neu starten.** Plugins werden beim Start des MMI eingelesen. Das neue Menü ist unter **Ambientebeleuchtung** hinter den bisherigen Einträgen.

Ein defektes oder inkompatibles Plugin wird beim Einlesen übersprungen, ohne Absturz. Die eingebauten Effekte laufen weiter.

## Verfügbare Daten (Kurzfassung)

| Quelle | Zugriff | Inhalt |
|---|---|---|
| `VehicleData` | Parameter `vehicle` | `speedKmh` (bei `speedUnit == 1` mph), `acceleratorPct` 0–100, `brakePressure`, `driverBraking`, `currentGear`, `gearTransMode` (4 = D, 5 = S), `engineRpm`, `longAccel`, `latAccel`, `wheelAngle`, `blinkingState`, `kickDown`, `isFresh(nowMs)` |
| Tag/Nacht | Parameter `daylight` | `true` = Tag, `false` = Nacht |
| `Rgb[] factoryPalette` | Parameter | Die vom Fahrzeug gemeldete Werksfarbliste (kann leer sein) |
| `DriveModeData` | `AmbientFx.getInstance().getDriveMode()` | `profile` (Comfort, Auto, Dynamic, Efficiency, Individual) |
| `OutsideTempData` | `AmbientFx.getInstance().getOutsideTemp()` | `celsius` |
| `ParkingDistanceData` | `AmbientFx.getInstance().getParkingDistance()` | acht Sensorwerte (ungetestet) |

Alle Einzelheiten, Einheiten und Stolperfallen stehen im [Effekte-Handbuch](../EFFEKTE_HANDBUCH.md). **Prüfe `valid`/`isFresh()` und baue einen sinnvollen Fallback ein.**

## Werksfarben und Helligkeit

Frei gestreamte Zwischenfarben können auf manchen LED-Streifen flackern. Deshalb gilt das bewährte Muster: **Ideale Farbe stufenlos berechnen, auf die nächste Werksfarbe snappen (`Rgb.nearest(...)`), erst danach die Helligkeit aufmultiplizieren** (`Rgb.scale(...)`). Beispiel: [`examples/MeinEffekt.java`](../examples/MeinEffekt.java). Wie die mitgelieferten Effekte das machen, siehst du in `java_patch/de/mk/ambientfx/effects/`.

## Regeln für Effekte

- **Java 1.2:** keine Generics, Enums, Lambdas, kein `StringBuilder`.
- **Jede Effekt-Datei ist eigenständig**, keine Verweise auf andere Effekt-Klassen.
- **Nachts dimmen** (`daylight == false`) und **keine schnellen Blitze** oder harten Hell-Dunkel-Wechsel.
- **Kein Blinker-Nachbau** und nichts, was wie eine Warnleuchte oder ein Bremslicht wirken könnte.
- Rechne mit **jeder** Fahrzeug- und Firmware-Variante, die anders reagiert, als du erwartest.

## Checkliste

1. Eigene Datei unter `java_patch/de/mk/ambientfx/effects/`.
2. `Effect` implementieren, Werksfarben-Snap einbauen, `daylight` bewusst nutzen.
3. `./build_java.sh && ./verify.sh` (muss „nur erwartete Abweichungen“ und „alle ok“ liefern).
4. `./build_plugin.sh …` und auf einem **privaten Grundstück** testen.
5. Jar + **alle** Plugins gemeinsam installieren.

## Ohne Programmieren: das Design Studio

Für einfache Designs (Farbverlauf über Tempo, Gas, Lenkwinkel oder Temperatur, Regenbogen, Helligkeit, Atmen) brauchst du keine Zeile Java: das **[Design Studio](../designer/README.md)** ist ein Editor im Browser (eine HTML-Datei, läuft offline), der **neu erstellen, importieren, bearbeiten und als `.ambiente` exportieren** kann. Seit **Version 1.1** öffnet es auch die fünf mitgelieferten Effekte als **Original-Vorlagen** und lässt alle ihre Einstellwerte ändern, einschließlich Gangwahlhebel-Blitz und Farbwelten je Fahrprofil. Es ist **experimentell** und bisher nur am PC getestet. Für ganz neue Logik schreibst du weiterhin einen Effekt in Java wie oben.
