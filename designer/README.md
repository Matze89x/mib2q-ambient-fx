# AmbientFX Design Studio

🇩🇪 **Deutsch** · 🇬🇧 [English below](#english)

> [!CAUTION]
> **Experimentell: bisher am PC getestet, im Fahrzeug noch nicht.** Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr – siehe [../docs/RECHT_LEGAL.md](../docs/RECHT_LEGAL.md). Eigene Designs laufen im Fahrzeug ungeprüft: nicht blenden, nicht ablenken, kein Blinker- oder Bremslicht-Nachbau. Nutzung auf eigene Gefahr.

Das **Design Studio** ist ein Editor im Browser, mit dem du eigene AmbientFX-Effekte **ohne Programmieren** baust: Designs **neu erstellen**, **importieren**, **bearbeiten** und als **`.ambiente`** **exportieren**. Dunkles Design, deutsch und englisch (Schalter oben rechts), mit Live-Vorschau und Fahrsimulator.

## Öffnen

- **Online (GitHub Pages):** `https://matze89x.github.io/mib2q-ambient-fx/designer/` (sobald GitHub Pages für `main` / `docs` aktiv ist).
- **Offline:** [`../docs/designer/index.html`](../docs/designer/index.html) herunterladen und im Browser öffnen. Es ist **eine einzige Datei** ohne externe Aufrufe: keine Server, kein Tracking, die Schriften sind eingebettet. Nichts wird hochgeladen.

## Ablauf

1. **Design bauen:** Farbe (Verlauf über Tempo, Gas, Lenkwinkel, Drehzahl, Beschleunigung oder Außentemperatur, Regenbogen oder einfarbig), Helligkeit, optional „Atmen“, Bremsen-Umschaltung, Nacht-Dimmung, Glättung. Die Vorschau rechnet **mit demselben Verfahren wie das Fahrzeug**.
2. **Namen vergeben** (Buchstaben, Ziffern, Leerzeichen, `. _ -`, höchstens 24 Zeichen, **eindeutig**, nicht wie ein eingebauter Effekt).
3. **Exportieren:** du bekommst `name.ambiente`.
4. Optional am PC prüfen: `designer/check_ambiente.sh name.ambiente` lädt die Datei mit dem **Original-Plugin-Lader** und zeigt ein paar Farben.
5. Datei nach `build/plugins/` legen und mit dem SD-Menü **Installieren/Updaten** (oder per Telnet, siehe [../docs/INSTALLATION_INSTALL.md](../docs/INSTALLATION_INSTALL.md)). Jar und **alle** Plugins gehören immer zusammen aufs Gerät.
6. MMI neu starten: dein Design steht unter **Ambientebeleuchtung** hinter den bisherigen Einträgen.

## Import

Importieren kannst du **Designs, die das Design Studio erzeugt hat** (`.ambiente`), und den **Design-Text** (Karte „Design als Text“) zum Teilen im Chat. Fertig kompilierte Effekte (Momentum, Atmosphere …) lassen sich hier **nicht** bearbeiten, denn sie enthalten nur Bytecode, kein Design.

## Die ID

Jedes Design hat eine **zufällige, feste ID** (`AFX-XXXX-XXXX`, zum Beispiel `AFX-95A8-F895`). Sie wird bei **Neu** und bei jedem **Beispiel** neu erzeugt und bei **Import und Export beibehalten**. Mit „Neue ID“ machst du aus einem fremden Design deine eigene Kopie. Älteren, längeren IDs (`AFX-` plus 16 Zeichen) bleiben beim Import erhalten. Das Studio erklärt die ID auch direkt auf der Seite („Was ist die ID?“). Wichtig zu wissen:

- Das Fahrzeug merkt sich den zuletzt gewählten Effekt über den **Namen** (Menü-Text). Fehlt dieser Effekt später (Plugin entfernt oder umbenannt), schaltet AmbientFX beim Start auf das Standard-Profil „Kontur-Weiß“ zurück. Das ist ein Rückfall, kein Fehler.
- Die ID steht in `plugin.properties` und in der Design-Datei und erscheint im Debug-Protokoll ([../docs/DEBUG.md](../docs/DEBUG.md)), damit man sieht, welches Design läuft.

## Was wird gesteuert?

Die Live-Vorschau zeigt den Innenraum von oben. **Aktuell ist im System erst die Kontur gebaut** (die LED-Streifen): Tür-Kontur an den vorderen Türen, je nach Ausstattung auch an den hinteren, und, nur bei Nachrüstung, der Streifen am Armaturenbrett. Die **passive Fläche** (Türtaschen, Lautsprecher, Fußraum, Getränkehalter, Mittelkonsole seitlich) bleibt manuell im MMI und kommt später dazu. Im MMI sind das zwei getrennte Farbwahlen. Das Studio erklärt das auch direkt auf der Seite („Was wird gesteuert?“).

## Was ein Design kann (und was nicht)

| Baustein | Möglichkeiten |
|---|---|
| Farbe | **Verlauf** (bis 16 Farbstopps, weich oder linear) über eine Quelle · **Regenbogen** (dreht endlos, schneller mit dem Tempo) · **einfarbig** |
| Quellen | Tempo (km/h), Gaspedal (%), Lenkwinkel (Betrag, °), Bremse (0/100), Drehzahl, Längs- und Querbeschleunigung (m/s²), Außentemperatur (°C) |
| Helligkeit | konstant oder über eine Quelle (auch umgekehrt) · „Atmen“ (mind. 2 s je Zyklus, keine schnellen Blitze) · Nacht-Dimmung |
| Bremsen | optional sofortiger Wechsel auf eine Farbe und Helligkeit (bitte [Recht & Haftung](../docs/RECHT_LEGAL.md) beachten) |
| Feinschliff | Glättung (ms), Sende-Intervall (50 bis 500 ms), Runden auf Werksfarben des Fahrzeugs |

Ein Design steuert **nur die Kontur**, wie alle AmbientFX-Effekte. Es gibt **keine Programmlogik** (zum Beispiel „beim Schalten blitzen“ oder Gang-abhängige Regeln): dafür schreibst du einen Effekt in Java ([../docs/EIGENE-DESIGNS_CUSTOM-DESIGNS.md](../docs/EIGENE-DESIGNS_CUSTOM-DESIGNS.md)). **Keine Werksfarben des Fahrzeugs** stecken im Studio: die Vorschau zeigt die Wunschfarbe, das Fahrzeug rundet sie bei „Runden“ auf seine eigene Werksfarbliste und dimmt erst danach.

## Wie es technisch funktioniert

Ein Browser kann kein Java kompilieren. Deshalb steckt in **jeder** `.ambiente`, die das Studio erzeugt, **dieselbe fertige Laufzeit-Klasse** `de.mk.ambientfx.designer.DesignEffect` (Quelle: [`src/de/mk/ambientfx/designer/DesignEffect.java`](src/de/mk/ambientfx/designer/DesignEffect.java), Java 1.2). Sie liest ihr Design aus der Textdatei `ambientfx_design.properties` im selben Archiv. Der Editor erzeugt also nur Text und packt ein Archiv:

```
name.ambiente  (ein Jar/Zip)
├── META-INF/MANIFEST.MF
├── plugin.properties                 class=…DesignEffect · label=<Name> · id=<ID> · version=<Vertragsversion>
├── ambientfx_design.properties       das Design (Text, siehe unten)
└── de/mk/ambientfx/designer/DesignEffect.class
```

- `ambient_fx.jar` bleibt **unverändert**: die Klasse liegt nur in den Plugins, jedes Plugin lädt seine eigene Kopie über seinen eigenen Classloader. Mehrere Designs nebeneinander sind kein Problem.
- Ein defektes Design (unlesbare Zahl, falsche Farbe …) lässt den Konstruktor scheitern: der Plugin-Lader überspringt das Plugin, es gibt keinen Absturz.
- **Rechenweg** wie bei den mitgelieferten Effekten: ideale Farbe → glätten → auf Werksfarbe runden → erst dann Helligkeit. Die JavaScript-Engine der Vorschau und die Java-Klasse rechnen **gleich** (geprüft: 17 Designs × 90 Takte, Abweichung 0, siehe unten).

### Design-Datei (Format 1)

Textdatei im `java.util.Properties`-Format (`schlüssel=wert`, `#` beginnt eine Kommentarzeile). Unbekannte Schlüssel werden ignoriert. Zahlen werden auf die angegebenen Grenzen begrenzt.

| Schlüssel | Bedeutung | Bereich / Werte |
|---|---|---|
| `format` | Formatversion | `1` |
| `design.id` | ID (nur zur Wiedererkennung) | `A-Z a-z 0-9 -`, 4 bis 40 Zeichen |
| `period` | Sende-Intervall in ms | 50 bis 500 |
| `color.mode` | Farbmodus | `ramp`, `cycle`, `solid` |
| `color.source` | Quelle für `ramp` | `speed`, `throttle`, `steer`, `brake`, `rpm`, `accel`, `lat`, `temp` |
| `color.min`, `color.max` | Bereich der Quelle | Zahlen |
| `color.smooth` | weiche Übergänge (smoothstep) | `0`/`1` |
| `color.stops` | `Position:#RRGGBB,…` (Position in Prozent, aufsteigend, 1 bis 16) | |
| `cycle.base`, `cycle.speed`, `cycle.sat` | Drehung °/s im Stand, Zusatz bei 130 km/h, Sättigung % | −720..720, −720..720, 0..100 |
| `bright.source` | Quelle der Helligkeit | wie oben oder `none` |
| `bright.srcMin`, `bright.srcMax`, `bright.invert` | Bereich, Umkehren | |
| `bright.min`, `bright.max` | Helligkeit in % (bei „Von“/„Bis“) | 0..100 |
| `bright.breath`, `bright.breathSec`, `bright.breathDepth` | „Atmen“, Zyklus in s (**min. 2**), Tiefe % | |
| `night.factor` | Nacht-Helligkeit in % | 0..100 |
| `brake.on`, `brake.color`, `brake.bright` | Bremsen-Umschaltung | |
| `smooth.ms` | Glättung (exponentiell) | 0..5000 |
| `snap` | auf Werksfarben runden | `0`/`1` |

Einheiten: Tempo immer in **km/h** (mph wird umgerechnet), Lenkwinkel als **Betrag**, Bremse als 0 oder 100.

## Selbst bauen und prüfen

```bash
./build_java.sh                       # braucht dein vendor/lsd.jar, siehe docs/BAUEN_BUILD.md
./designer/build_runtime.sh           # baut DesignEffect.class (Java-1.2-Regeln), zeigt die SHA-256
python3 tools/embed_designer.py       # setzt Klasse + Schriften + Vertragsversion in docs/designer/index.html ein
designer/check_ambiente.sh mein.ambiente        # lädt ein exportiertes Design mit dem Original-Plugin-Lader
designer/tests/parity.sh parity_js.json         # JavaScript-Engine gegen die Java-Klasse (parity_snippet.js erzeugt die Datei)
```

Die **SHA-256** der eingebetteten Klasse steht im Studio unter „So kommt dein Design ins Auto“. Wenn du `build_runtime.sh` mit demselben JDK 8 ausführst, sollte sie übereinstimmen. Du musst dem Binärteil nicht trauen: der Quelltext liegt hier und das Ergebnis lässt sich nachbauen.

## Schriften und Lizenz

Schriften: **Space Grotesk** © The Space Grotesk Project Authors und **Space Mono** © The Space Mono Project Authors, beide unter der [SIL Open Font License 1.1](https://openfontlicense.org), eingebettet in `docs/designer/index.html`. Der Rest steht unter der [Projekt-Lizenz](../LICENSE.md) (© 2026 Matze89x in cooperation with [DEB](https://www.digital-eliteboard.com/) and [LuKa_dev](https://github.com/luka-dev)).

---

<a id="english"></a>

# AmbientFX Design Studio

🇩🇪 [Deutsch oben](#ambientfx-design-studio) · 🇬🇧 **English** (English version of the German text above)

> [!CAUTION]
> **Experimental: tested on a computer so far, not yet in a vehicle.** For testing on private property only, not in public road traffic – see [../docs/RECHT_LEGAL.md](../docs/RECHT_LEGAL.md). Your own designs run unchecked in the vehicle: do not dazzle, do not distract, no indicator or brake-light imitation. Use at your own risk.

The **Design Studio** is an editor in your browser to build your own AmbientFX effects **without programming**: **create** designs, **import**, **edit** and **export** them as **`.ambiente`**. Dark design, German and English (switch at the top right), with live preview and a driving simulator.

## Open

- **Online (GitHub Pages):** `https://matze89x.github.io/mib2q-ambient-fx/designer/` (once GitHub Pages is enabled for `main` / `docs`).
- **Offline:** download [`../docs/designer/index.html`](../docs/designer/index.html) and open it in a browser. It is **one single file** with no external requests: no server, no tracking, the fonts are embedded. Nothing is uploaded.

## Workflow

1. **Build the design:** colour (ramp over speed, throttle, steering angle, engine speed, acceleration or outside temperature; rainbow; or solid), brightness, optional “breathing”, brake switch, night dimming, smoothing. The preview computes **the same way as the vehicle**.
2. **Give it a name** (letters, digits, spaces, `. _ -`, at most 24 characters, **unique**, not the same as a built-in effect).
3. **Export:** you get `name.ambiente`.
4. Optionally check on your computer: `designer/check_ambiente.sh name.ambiente` loads the file with the **original plugin loader** and prints a few colours.
5. Put the file into `build/plugins/` and use the SD menu **Install/Update** (or telnet, see [../docs/INSTALLATION_INSTALL.md](../docs/INSTALLATION_INSTALL.md)). The jar and **all** plugins always belong on the unit together.
6. Reboot the MMI: your design appears under **Ambient lighting**, after the existing entries.

## Import

You can import **designs created by the Design Studio** (`.ambiente`) and the **design text** (card “Design as text”) for sharing in a chat. Precompiled effects (Momentum, Atmosphere …) cannot be edited here because they contain only bytecode, no design.

## The ID

Every design has a **random, fixed ID** (`AFX-XXXX-XXXX`, for example `AFX-95A8-F895`). It is generated anew on **New** and on every **example**, and **kept on import and export**. “New ID” turns someone else’s design into your own copy. Good to know:

- The vehicle remembers the last chosen effect by its **name** (menu text). If that effect is missing later (plugin removed or renamed), AmbientFX falls back to the standard profile “contour white” at start. That is a fallback, not an error.
- The ID is in `plugin.properties` and in the design file and appears in the debug log ([../docs/DEBUG.md](../docs/DEBUG.md)) so you can see which design is running.

## What is controlled?

The live preview shows the interior from above. **For now only the contour is built into the system** (the LED strips): door contour on the front doors, depending on equipment also on the rear doors, and, only if retrofitted, the strip on the instrument panel. The **passive surface** (door pockets, speakers, footwell, cupholders, centre console sides) stays manual in the MMI and will be added later. In the MMI these are two separate colour choices. The Studio also explains this on the page (“What is controlled?”).

## What a design can do (and what not)

| Building block | Options |
|---|---|
| Colour | **Ramp** (up to 16 colour stops, smooth or linear) over a source · **rainbow** (rotates endlessly, faster with speed) · **solid** |
| Sources | speed (km/h), throttle (%), steering angle (absolute, °), brake (0/100), engine speed, longitudinal and lateral acceleration (m/s²), outside temperature (°C) |
| Brightness | constant or over a source (also inverted) · “breathing” (at least 2 s per cycle, no fast flashes) · night dimming |
| Braking | optional instant switch to a colour and brightness (please mind [law & liability](../docs/RECHT_LEGAL.md)) |
| Fine tuning | smoothing (ms), send interval (50 to 500 ms), rounding to the vehicle’s factory colours |

A design drives **only the contour**, like all AmbientFX effects. There is **no program logic** (for example “flash on shifting” or gear-dependent rules): for that you write an effect in Java ([../docs/EIGENE-DESIGNS_CUSTOM-DESIGNS.md](../docs/EIGENE-DESIGNS_CUSTOM-DESIGNS.md)). **No factory colours of the vehicle** are inside the Studio: the preview shows the wanted colour; with “round” the vehicle rounds it to its own factory colour list and only then dims.

## How it works technically

A browser cannot compile Java. So **every** `.ambiente` the Studio produces contains **the same prebuilt runtime class** `de.mk.ambientfx.designer.DesignEffect` (source: [`src/de/mk/ambientfx/designer/DesignEffect.java`](src/de/mk/ambientfx/designer/DesignEffect.java), Java 1.2). It reads its design from the text file `ambientfx_design.properties` in the same archive. The editor only produces text and packs an archive (see the tree in the German part above).

- `ambient_fx.jar` stays **unchanged**: the class lives only in the plugins; each plugin loads its own copy through its own classloader. Several designs side by side are no problem.
- A broken design (unreadable number, wrong colour …) makes the constructor fail: the plugin loader skips the plugin, no crash.
- **Calculation order** as in the bundled effects: ideal colour → smooth → round to factory colour → only then brightness. The preview’s JavaScript engine and the Java class compute **identically** (verified: 17 designs × 90 ticks, deviation 0, see below).

### Design file (format 1)

See the key table in the German part above (identical). Plain text in `java.util.Properties` format (`key=value`, `#` starts a comment line). Unknown keys are ignored. Numbers are clamped to the listed limits. Units: speed always in **km/h** (mph is converted), steering angle as an **absolute value**, brake as 0 or 100.

## Build and verify yourself

See the commands in the German part above (identical). The **SHA-256** of the embedded class is shown in the Studio under “How your design gets into the car”. If you run `build_runtime.sh` with the same JDK 8 it should match. You do not have to trust the binary part: the source is here and the result can be rebuilt.

## Fonts and licence

Fonts: **Space Grotesk** © The Space Grotesk Project Authors and **Space Mono** © The Space Mono Project Authors, both under the [SIL Open Font License 1.1](https://openfontlicense.org), embedded in `docs/designer/index.html`. Everything else is under the [project licence](../LICENSE.md) (© 2026 Matze89x in cooperation with [DEB](https://www.digital-eliteboard.com/) and [LuKa_dev](https://github.com/luka-dev)).
