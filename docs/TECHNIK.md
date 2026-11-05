# AmbientFX – Technische Dokumentation

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr. Dieses Dokument ist auf Deutsch, der englische Überblick steht in der [README](../README.md).

Java-Patch für das Audi MHI2Q (MU1329), der das Menü **Fahrzeugeinstellungen → Ambientebeleuchtung** um animierte und fahrzeugreaktive Effekte erweitert. Dieses Dokument beschreibt Aufbau, Funktionsweise und die Analyse des Stock-HMI.

Überblick und Schnellstart: [README](../README.md) · Bauen: [BAUEN_BUILD.md](BAUEN_BUILD.md) · Installieren: [INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md) · Eigene Effekte: [EIGENE-DESIGNS_CUSTOM-DESIGNS.md](EIGENE-DESIGNS_CUSTOM-DESIGNS.md) · Lizenz: [LICENSE.md](../LICENSE.md) (gilt für den eigenen Code, **nicht** für Audi-/Drittanbieter-Code; die ersetzten Audi-Klassen werden hier nicht mitgeliefert).

---

## Was der Patch macht

Stock-Menü (Screen 600028):

```
Kontur-Weiß        (Profil 3)
Maritim-Blau       (Profil 4)
Impuls-Rot         (Profil 6)
individuell        (Profil 1)
Audi drive select  (Profil 2)
```

Mit Patch (Jar) + den fünf mitgelieferten Plugin-Dateien (siehe
„Plugin-System" weiter unten – seit 25.09.2026 ist **kein** Effekt mehr
fest im Jar verdrahtet, alle werden als `.ambiente`-Dateien separat
installiert) kommen darunter fünf Einträge dazu (Reihenfolge im Menü ist
seit 26.09.2026 alphabetisch nach Dateiname, siehe „Plugin-System"):

| Eintrag  | Kontur (Linie)                                                  | Fläche |
|----------|------------------------------------------------------------------|--------|
| Atmosphere | **Eigenständige, bewusst nicht-reaktive** Stimmung (siehe „Atmosphere" unten) – Farbton nach Außentemperatur, Helligkeit als langsames „Atmen", keine Tempo-/Gas-/Brems-Reaktion. | unangetastet, wie Momentum |
| Drive | **Gaspedal + Lenkwinkel** steuern Farbton/Sättigung/Helligkeit (siehe „Drive" unten, Konzept/Formeln von LuKa_dev übernommen) – Ruhe = blasses Violett, Vollgas schwenkt Richtung warmes Ember, Lenkeinschlag spreizt den Farbton zusätzlich auf. Bremse = sofort voll Rot wie bei den anderen. | unangetastet, wie Momentum |
| Momentum | Farbe = Durchlauf durch **alle 28 farbigen Audi-Werksfarben** über 0–130 km/h (Rot → Orange → Gelb → Grün → Cyan → Blau → Violett → Rosa-Rot → wieder **Rot bei 130 km/h**), stufenlos zwischen benachbarten Werksfarben überblendet – siehe „Farbtabelle Momentum / Momentum select“ unten. Helligkeit **tagsüber immer 100 %** (unabhängig vom Gaspedal), **nachts** 5 % bei 0 % Gas, steigt bis 50 % Gas auf 100 % an und bleibt darüber bei 100 % (asymmetrisch geglättet, schneller heller als dunkler). Bremse = sofort voll Rot, voll hell, unabhängig von allem anderen. **Gangwahlhebel D → S** (außer im Dynamic-Profil): einmalig sofort Weiß bei 100 % (Tag und Nacht), 1 s gehalten, dann 2 s weiches Überblenden zurück zur normalen Farbe (siehe „Gangwahlhebel D → S“ unten). | **unangetastet** – bleibt der manuelle Farb-/Helligkeitsregler im MIB (eigener, vom Fahrzeug selbst gemerkter Wert) |
| Momentum select | **Eigenständige Kopie von Momentum** (siehe „Momentum Select" unten) – gleiche Farbtabelle (alle Werksfarben, 0–130 km/h) und Brems-Prinzip, aber die Farbwelt, Helligkeits- und Reaktionskurve hängt zusätzlich vom **Audi-Drive-Select-Modus** ab (Comfort/Auto/Dynamic/Eco/Individual). Dieselbe D→S-Weißsequenz wie Momentum (siehe „Gangwahlhebel D → S“ unten). | unangetastet, wie Momentum |
| Scroll | **Reines Regenbogen-Showelement** (siehe „Scroll" unten, Idee/Formel von LuKa_dev übernommen) – Farbton dreht sich mit einer zum Tempo proportionalen Geschwindigkeit endlos über das Farbrad, im Stand friert die Farbe ein. Kein Gas-/Tag-Nacht-Breathing, nur ein fester Nacht-Dimmwert. Bremse = sofort voll Rot wie bei den anderen. | unangetastet, wie Momentum |

### Farbtabelle Momentum / Momentum select (seit 03.10.2026)

Anlass: die Farben wechselten zu hart (Snap auf nur **eine** nächste
Werksfarbe, 25 verschiedene Farben im ganzen Tempobereich, Sprünge bis 240
RGB-Stufen pro km/h) und viele Grün-/Gelbtöne der Fahrzeugliste wurden nie
angezeigt. Nach den Live-Tests mit Scroll (freies Streamen, **kein Flackern**
auf dem Retrofit-Streifen) wurde das Snapping in Momentum und Momentum select
durch einen **Farbring aus der Werksliste** ersetzt:

* Die Liste (30 Einträge, zur Laufzeit vom Fahrzeug: 28 farbig + 2 Weiß) wird
  in `buildRing()` gefiltert (Sättigung ≥ 0,35 → Weiß fällt heraus) und **nach
  Farbton ab Rot sortiert**. Die Reihenfolge der Liste im Fahrzeug spielt keine
  Rolle, die Tabelle passt sich an die gemeldete Liste an.
* Geschwindigkeit → Position auf dem **geschlossenen** Ring: 0 km/h = erstes Rot,
  130 km/h = wieder dasselbe Rot (13 Schritte à 10 km/h, `RAINBOW_SPAN_KMH=130`,
  `SPEED_STEP_KMH=10`). Zwischen zwei Werksfarben wird stufenlos überblendet
  (`SMOOTH_COLORS=true`); alle 10 km/h laufen gut zwei Werksfarben durch.
  Das letzte Stück (Violett → Rosa-Rot → Rot) schließt den Kreis, ohne dass
  die Liste eine eigene Magenta-Farbe bräuchte.
* **Rückfall bei Flackern:** `SMOOTH_COLORS=false` → nur noch echte Werksfarben
  in harten 10-km/h-Stufen (14 Farben, Stufe = Rundung auf 10 km/h).
* Ohne Werksliste (noch nicht eingetroffen): reiner Farbton wie früher.
* **Bremse unverändert:** sofort Werks-Rot, voll hell. **Helligkeit unverändert**
  (Tag 100 %, Nacht 5 % bei 0 % Gas → 100 % bei 50 % Gas; Comfort/Eco und
  Dynamic wie gehabt). **D→S-Weißsequenz unverändert.**
* Momentum select behält seine **Farbwelten je Profil**, jetzt aus echten
  Werksfarben: Auto/Individual = ganzer Ring (0 km/h Rot … 130 km/h Rot);
  Dynamic = Abschnitt Rot → Gelb über 0–130 km/h (Gas zieht weiter Richtung
  Rot); Comfort/Eco = Abschnitt Grün → Blau über 0–130 km/h.
* Messung (Simulation mit Foto-Näherung der Werksfarben, 1-km/h-Raster,
  0–140 km/h): Momentum **25 → 130 verschiedene Farben**, größter Sprung pro
  km/h **240 → 52**. 
  Die echten 30 RGB-Werte stehen bei gesetztem Debug-Marker als Zeile
  „factory color list values: …“ im Log (kommen vom Fahrzeug, nicht aus dem
  MMI-Dump).
* Backup vor dem Umbau: `backups/2026-10-03-vor-farbtabelle/`.

### Werksfarben-Snapping (bis 02.10.2026, wegen Retrofit-LED-Streifen)

> **Historisch:** gilt für Momentum/Momentum select nur noch für die **Bremse**
> und den Fallback ohne Werksliste; Atmosphere/Drive/Scroll siehe jeweils dort.
> Die Annahme „freie Zwischenwerte flackern“ hat sich mit dem Scroll-Test
> (80–130 km/h, kein Flackern) nicht bestätigt – siehe „Farbtabelle“ oben.

Der nachgerüstete LED-Streifen im Fahrzeug stellt nur die Farben aus der
**Audi-eigenen „individuell“-Farbliste** zuverlässig dar – frei gestreamte
Zwischenwerte flackern. Deshalb rechnet Momentum den Regenbogen-Farbton
intern zwar stufenlos aus, **snappt ihn aber vor jedem Senden auf die
nächstliegende echte Werksfarbe** (`Rgb.nearest(...)`, Palette kommt passiv
per DSI vom Fahrzeug, siehe `AmbientFx.onColorListChunk`). Das ist bewusst
keine optionale Einstellung mehr (das frühere Marker-Datei-Experiment ist
raus) – auf diesem Fahrzeug mit dieser LED ist es die einzige Konfiguration,
die sauber läuft. Wichtig: die Helligkeit wird **danach separat**
aufmultipliziert, nicht mitgesnappt – sonst würde jede gedimmte Farbe wieder
auf die volle Werksfarbe zurückspringen und die Gas-Helligkeit wirkungslos
machen. Bis die Werksfarbliste vom Fahrzeug eingetroffen ist (sollte
automatisch beim Start passieren, siehe Log-Zeile „factory color list
complete: N entries“), zeigt Momentum den unverfälschten Regenbogen-Farbton.

**Wichtig, anders als in v2: die Engine sendet gar keine Fläche mehr.**
`Effect.tick()` liefert nur noch die Kontur-Farbe; die Fläche bekommst du
weiterhin ganz normal über den Farbregler im MIB selbst eingestellt, und das
Fahrzeug merkt sich diesen Wert wie bei jedem anderen Profil auch – unser
Patch mischt sich da nicht ein.

Der Effekt läuft technisch auf dem Fahrzeug-Profil **„individuell“ (1)**
(nötig, damit die Kontur frei bestimmbar ist): die Engine schaltet das Profil
um und streamt dann nur die Kontur-Farbe per DSI ans Fahrzeug. Der
Radio-Button im Menü bleibt auf „Momentum“.

### Tag/Nacht-Verhalten

**Korrigiert nach dem ersten Live-Test:** die erste Fassung hatte bei
normalem, moderatem Gas (< 50 %) tagsüber quasi durchgehend 0 % Helligkeit –
dadurch war die Kontur bei normaler Tagfahrt praktisch unsichtbar, sichtbar
wurde nur ein kurzes Aufblitzen bei kräftigem Antritt (Gas > 50 %). Das war
ein Fehler in der Helligkeitslogik, keine Tag/Nacht-Abschaltung.

Die Tag/Nacht-Erkennung sitzt bewusst **im Effekt selbst** (nicht als
pauschale Regel auf Engine-Ebene – die gab es in v3 vorher, wurde aber
entfernt, weil sie Momentums Kurve wieder die Spitze gekappt hätte). Quelle
ist **`DaylightData`** (`de/mk/ambientfx/DaylightData.java`), **live im
Fahrzeug bestätigt**: Lichtschalter umlegen kippt das Signal in Echtzeit.
`VehicleData.lowBeamOn` (das erste, naheliegende Signal) blieb dagegen in
mehreren Tests durchgehend `false` – benutzt wird stattdessen derselbe
DSI-Dienst und dasselbe Attribut (`DSIGeneralVehicleStates`, Attribut 16,
`updateDisplayDayNightDesign`), das nachweislich (per Decompile von
`de.audi.app.terminalmode.NightDayModeHandler`) auch CarPlays eigene
Tag/Nacht-Umschaltung speist:

* **Tag** – Kontur immer **100 % hell**, unabhängig vom Gaspedal. Tagsüber
  blendet nichts, und „meistens unsichtbar" war schlechter als „immer
  sichtbar".
* **Nacht** – Helligkeit folgt dem Gaspedal. Verlauf mehrfach nachjustiert:
  50–100 % (zu knapp) → 25–100 % linear über den ganzen Gasbereich → seit
  26.09.2026 (zweite Kurve): **5 % bei 0 % Gas** (nie ganz aus), steigt bis
  **50 % Gas** linear auf **100 %** und bleibt darüber gedeckelt – löst den
  alltagsrelevanten unteren/mittleren Gasbereich feiner auf, statt erst bei
  Vollgas die volle Helligkeit zu erreichen. Seit 26.09.2026 zusätzlich
  **asymmetrisch** geglättet (Idee aus `luka-collection`, siehe
  `Momentum.java`): schneller heller beim Gasgeben (120 ms), spürbar
  langsamer wieder dunkler beim Loslassen (350 ms).
* **Bremse** überschreibt beides sofort mit vollem Rot bei voller Helligkeit.

### Momentum Select (Drive-Select-reaktive Variante)

**Bewusst eine komplette, unabhängige Kopie** von `Momentum.java` –
`effects/MomentumSelect.java` importiert/erweitert/referenziert Momentum an
keiner Stelle, damit beide Effekte sich nicht gegenseitig beeinflussen
können: wird eine `.ambiente`-Datei vom MIB gelöscht, bleibt der andere
Effekt unverändert lauffähig. Gleiches Grundprinzip (Regenbogen über Tempo, Werksfarben-Snap,
Sofort-Rot bei Bremse), aber die Helligkeits-/Reaktionskurve hängt
zusätzlich vom **Audi-Drive-Select-Modus** ab, Quelle ist die neue
`DriveModeData` (`org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristics`,
Attribut 13, `updateCharismaActiveProfile`) – dasselbe Attribut, das schon
länger in der Wunschliste stand, hier zum ersten Mal angebunden. Abfrage
über `AmbientFx.getInstance().getDriveMode()`, bewusst **nicht** als
zusätzlicher Parameter in `Effect.tick()`, damit Momentum unverändert
bleibt.

| Profil | Helligkeit | Reaktionsgeschwindigkeit (Hue / Helligkeit hoch·runter) |
|---|---|---|
| **Dynamic** | immer 100 % (Tag *und* Nacht, unabhängig vom Gaspedal) | viel schneller, **symmetrisch** (150 / 100·100 ms) – bewusst kein Attack/Release, passt zum knackigen Charakter |
| **Auto, Individual** | exakt wie Momentum (Tag 100 %, Nacht 5–100 % nach Gas, gedeckelt ab 50 % Gas) | exakt wie Momentum (600 / 120·350 ms, asymmetrisch seit 26.09.2026) |
| **Comfort, Eco (Efficiency)** | folgt **immer** dem Gaspedal: 3 % bei 0 % Gas bis 100 % bei Vollgas (unabhängig Tag/Nacht) | deutlich langsamer, asymmetrisch (1000 / 250·700 ms) |
| unbekannt / kein Signal | wie Auto/Individual | wie Auto/Individual |

„Ohne Überlauf" bei Dynamic: es ist nur ein einfacher Tiefpass
(Exponential-Glättung wie bei Momentum), kein Feder-/PID-Modell – der kann
grundsätzlich nicht überschwingen, unabhängig von der Zeitkonstante.

**Experiment „mehr Zwischenfarben"** (auf Wunsch aktiv, `TRY_BLENDED_COLORS
= true` in `MomentumSelect.java`): statt hart auf die eine nächste
Werksfarbe zu snappen, mischt Momentum Select gewichtet zwischen den
**zwei** nächsten Werksfarben – mehr wahrnehmbare Zwischentöne, aber die
Mischwerte sind keine reinen Werksfarben mehr und könnten auf dem
Retrofit-Streifen denselben Flacker-Effekt zeigen wie freies Streaming. Bei
Flackern: `TRY_BLENDED_COLORS` auf `false` – dann exakt derselbe harte Snap
wie bei Momentum, nur in dieser separaten Datei. Beide Varianten bewusst im
Code erhalten (ein einziger Schalter), nicht als zwei Dateien.

### Gangwahlhebel D → S (in Momentum und Momentum select)

Seit 30.09.2026 direkt in `effects/Momentum.java` und
`effects/MomentumSelect.java`. Entstanden am 27.–29.09.2026 als zwei
getrennte Testeffekte „Momentum GDS“ / „Momentum GDS select“ (auf Nutzerwunsch
bewusst separat, damit die Originale unangetastet blieben); nach dem
erfolgreichen Live-Test übernommen, die beiden Testeffekte sind entfernt
(Kopien: `backups/2026-09-30-vor-gds-merge/`). Auf dem MIB müssen die alten
Dateien `momentumgds.ambiente` und `momentumgdsselect.ambiente` gelöscht
werden, sonst tauchen sie weiter im Menü auf (siehe DEPLOY_TELNET.md 4.5b).

Quelle: `VehicleData.gearTransMode` (`DynamicVehicleInfoMidFrequent`,
`automaticGearShiftTransMode`). Steht der Hebel auf **S** (Sport) **und** ist
Audi drive select **nicht** auf Dynamic (`DriveModeData.PROFILE_DYNAMIC`),
spielt beim **Wechsel** auf S (nicht dauerhaft, nur einmalig pro Umschaltung)
eine weiße Eintrittssequenz – „wie Bremsen, nur in Weiß“:

1. **Sofort** Weiß bei 100 % Helligkeit (Tag und Nacht gleich, ohne Rampe),
   1 s lang gehalten (`FLASH_HOLD_MS`).
2. Danach 2 s weiches Überblenden (`FLASH_FADE_MS`) von Weiß zu dem, was der
   Effekt gerade laufend für Farbe/Helligkeit berechnet (live mitverfolgt,
   kein statisches Ziel; nachts blendet es also auf die dann gültige, dunklere
   Nachthelligkeit herunter).
3. Danach 1:1 normales Momentum/Momentum select, keine weitere
   Sonderbehandlung, bis erneut auf D und wieder auf S geschaltet wird.

Zurück auf **D**: sofort normales Verhalten, kein Übergang. **Dynamic bewusst
ausgenommen** (Nutzer-Vorgabe): dort steht das Getriebe ohnehin dauerhaft auf
S, das Signal wäre bedeutungslos. **Bremse hat weiterhin Vorrang** vor allem,
auch vor der Sequenz; die Sequenz läuft nach Uhrzeit (nicht nach Tick-Zahl)
und ist deshalb nach kurzem Bremsen mittendrin entsprechend weiter. Wird
gebremst, *während* der Hebel auf S geschaltet wird, gewinnt das Rot, und die
Weißsequenz startet erst beim Loslassen der Bremse.

Die frühere Zwischenstufe „1 s Weiß bei 20 %“ vor dem 100-%-Sprung entfiel
am 30.09.2026 auf Nutzerwunsch. Der allererste Versuch (28.09., dauerhafter
Farbzug Richtung Rot, solange S aktiv ist) wirkte im Live-Test unruhig, weil
der Tempo-Regenbogen parallel dagegen ankämpfte.

Per `javap` gegen die eigene `lsd.jar` (MU1329) bestätigt:
`GEARTRANSMISSIONMODE_POSITION_D=4`, `_S=5` – identisch auf diesem
Fahrzeug vorhanden.

### Atmosphere (ruhige, nicht-reaktive Variante)

**Ebenfalls eine komplett unabhängige Datei** – `effects/Atmosphere.java`
importiert/erweitert/referenziert weder Momentum noch Momentum Select.
Bewusst das Gegenteil der beiden anderen: die sind *reaktiv* (springen mit
Tempo/Gas/Bremse), Atmosphere ist *ruhig* – kein Bezug zu Tempo, Gas oder
Bremse, gedacht als Stimmung für alle im Auto statt als Signal für den
Fahrer.

* **Farbton**: hängt von der **Außentemperatur** ab – Quelle ist die neue
  `OutsideTempData` (`org.dsi.ifc.carkombi.DSICarKombi`, Attribut 65,
  `updateBCOutsideTemperature`, dieselbe Zahl wie das Thermometer-Icon im
  Kombi-Display). Kalt (≤ −5 °C) → kühles Blau (Hue 210°), warm (≥ 30 °C)
  → warmes Amber (Hue 30°), dazwischen linear interpoliert. **Noch nicht
  zweifelsfrei live bestätigt** (anders als `DaylightData`/`DriveModeData`)
  – ob wirklich ein echter Wert ankommt oder nur der neutrale Platzhalter
  (15 °C) läuft, prüft seit 26.09.2026 das Alive-Log direkt (`temp[valid=…
  c=…]`, siehe `AmbientFx.loop()`). Zusätzlich prüft `OutsideTempData` seit
  26.09.2026 auch `CarBCTemperature.state == NORMAL_OPERATION`, nicht nur
  das grobe `validFlag`. Die Temperatur wird zusätzlich sehr träge
  nachgezogen (~8 s Zeitkonstante) – hier soll nichts hinterherspringen.
* **Helligkeit**: kein Bezug zu Gas/Tempo. Stattdessen ein langsames
  „Atmen" – ein Sinus zwischen einem Min- und Max-Wert über ~5 s, tagsüber
  etwas präsenter (50–85 %), nachts deutlich gedämpfter (15–35 %).
* **Bremse**: wird **bewusst nicht ausgewertet** – kein hartes Rot wie bei
  Momentum/Momentum Select. Das würde den ruhigen Charakter sofort
  kaputtmachen.
* **Blinker**: seit 26.09.2026 **keine Reaktion mehr** (frühere Versionen
  ließen die Kontur beim Blinken amber pulsieren – wegen des
  Sendediscipline-Problems entfernt). Aus
  Fahrverhalten reagiert Atmosphere jetzt auf **gar nichts** mehr.
* **Werksfarben-Snap**: wie Momentum – harter Snap auf die eine nächste
  Werksfarbe (`Rgb.nearest`-Prinzip), nicht die Zwischenfarben-Mischung aus
  Momentum Select. Laut Live-Beobachtung im Fahrzeug traten damit bisher
  keinerlei Farbprobleme auf dem Retrofit-Streifen auf – das ist also die
  bewährte Basis.

### Scroll (Regenbogen-Showelement)

**Vierter Effekt, komplett unabhängige Datei** – `effects/Scroll.java`.
Ursprung: `luka-collection/AmbientSpeedComponent.java`, Modus „Scroll" –
dort dreht sich der Farbton mit einer zum Tempo proportionalen
Geschwindigkeit endlos über das komplette Farbrad (0,36° pro (km/h·s),
Formel/Wert 1:1 von Luka übernommen). Anders als Momentum (Tempo → feste
Hue-**Position**, bleibt ab 100 km/h bei Rot stehen) ist Scroll eine
fortlaufende Hue-**Rotation**: im Stand friert die Farbe ein, je schneller
gefahren wird, desto schneller dreht sie sich weiter – kein Anschlag, läuft
unbegrenzt im Kreis.

**Bewusst NICHT 1:1 von Luka übernommen** (siehe Klassenkommentar in
`Scroll.java` für die volle Begründung):
* Luka streamt rohe HSV-Werte über das komplette Farbrad ohne
  Werksfarben-Snap – funktioniert auf seinem Fahrzeug, hätte auf unserem
  Retrofit-Streifen aber vermutlich dasselbe Flackern reproduziert, das wir
  bei Momentum extra gelöst haben. Scroll snappt deshalb genau wie Momentum
  hart auf die nächste Werksfarbe.
* Lukas Version steuert Fläche und Kontur gleichzeitig – bei uns bauartbedingt
  (Effect-Schnittstelle/Engine) nur die Kontur, wie überall in diesem
  Projekt.
* Bremse = sofort volles Rot – eigene Ergänzung für Konsistenz mit
  Momentum/Momentum Select, bei Luka nicht vorhanden.

Helligkeit bewusst simpel: Tag 100 %, Nacht ein fester Dimmwert (40 %) ohne
Gas-Bezug und ohne Breathing – Scroll ist ein reines Showelement, kein
Fahr-Feedback wie Momentum oder eine Stimmung wie Atmosphere.

### Drive (Gaspedal + Lenkwinkel)

**Fünfter Effekt, komplett unabhängige Datei** – `effects/Drive.java`.
Ursprung: `luka-collection/AmbientSpeedComponent.java`, Modus „Drive" –
dort als „Flaggschiff" bezeichnet. Das Gaspedal besitzt die Farbton-Achse
(Ruhe = blasses Violett, Vollgas schwenkt über Rot Richtung warmes Ember),
Lenkeinschlag spreizt den Farbton zusätzlich auf und hebt die Sättigung an
– je mehr Last/Kurve, desto satter die Farbe, in Ruhe blass/silbrig.
Lenkwinkel-Quelle: neues Feld `VehicleData.wheelAngle`
(`DynamicVehicleInfoHighFrequent`, Attribut 14) – seit 26.09.2026 zum
ersten Mal angebunden, per `javap` gegen die eigene `lsd.jar`
bestätigt identisch zu Lukas Fund vorhanden.

**Bewusst NICHT 1:1 von Luka übernommen** (siehe Klassenkommentar in
`Drive.java` für die volle Begründung):
* Werksfarben-Snap statt rohem HSV-Streaming – wie bei Scroll/Momentum.
* Nur die Kontur – Lukas eigene Kontur-Formel (`hc`/`cv`, Lenkeinschlag
  spreizt sie weiter vom Ruhewert weg) wird direkt als **einziger** Ausgang
  benutzt, da wir ohnehin nur einen Kanal haben.
* **Bremse = sofort volles Rot**, nicht Lukas Azurblau – Konsistenz mit der
  bereits etablierten Bremse-Konvention der anderen vier Effekte war hier
  wichtiger als Werktreue zum Original.

Die Sicherheits-Klammer `DRIVE_HUE_CAP` (bei Luka ausdrücklich als „SAFETY
INVARIANT, not a taste knob" markiert – verhindert, dass Vollgas +
Vollausschlag den Farbton ungewollt Richtung Grün kippen lässt) wurde 1:1
übernommen. Tag/Nacht ist eine eigene, einfache Ergänzung (bei Luka gar
nicht vorhanden): nachts wird die berechnete Helligkeit pauschal halbiert.

### Plugin-System (seit 25.09.2026, im Fahrzeug bestätigt)

Kein Effekt ist mehr fest im Jar verdrahtet – auch Momentum, Momentum
select, Atmosphere, Scroll und Drive
nicht. Alle fünf werden als eigenständige **`.ambiente`-Dateien**
ausgeliefert und beim Start automatisch aus
`/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/` (auf dem MIB selbst,
nicht der SD-Karte – die dient nur als Transport) geladen
(`EffectPluginLoader`, `java.net.URLClassLoader`). Eine `.ambiente`-Datei
ist technisch ein kleines Jar (kompilierte Effekt-Klasse + `plugin.properties`
mit Klassenname/Label) – Momentum select nicht dabei haben wollen? Einfach
die Datei vom MIB löschen, kein Neubau von `ambient_fx.jar` nötig. Ein
defektes/fehlendes Plugin wird beim Scan übersprungen und geloggt, nie ein
Absturz – im schlimmsten Fall zeigt das Menü einfach keine Zusatz-Einträge.

Vollständige Anleitung zum Schreiben und Ausliefern eigener Effekte:
[EFFEKTE_HANDBUCH.md](../EFFEKTE_HANDBUCH.md).

### Sicherheit: nur die Kontur reagiert

Alles Reaktive (Tempofarbe, Gas-Helligkeit, Brems-Rot) läuft **ausschließlich
auf der Kontur** (schmale Linie), nie auf der Fläche. Ein großflächiges
Reagieren/Blitzen im peripheren Sichtfeld (Fußraum, große Türpappen) wäre
nachts eine echte Blend- und Irritationsgefahr – die Fläche bleibt deshalb
grundsätzlich außen vor.

### Debug

Die Debug-Fassade `Log.java` (`Log.i`, `Log.w`, `Log.e`, `Log.value`, `Log.enabled`) schreibt über `RamLog.java` in ein **Protokoll nur im RAM** (`/dev/shmem/ambientfx.log`, Ausweich `/tmp`). Eingeschaltet wird sie ausschließlich durch die Marker-Datei `/mnt/persist/ambientfx/debug` (überlebt den Neustart; `RamLog.on()` prüft sie höchstens alle 2 s). Ohne Marker entsteht keine Datei. Die Datei hat eine **harte Obergrenze** (`MAX_BYTES` = 256 KB pro Start): danach folgt genau eine Schlusszeile, dann wird nichts mehr geschrieben, es wird nichts rotiert und nichts gelöscht. Wird die Datei gelöscht oder gekürzt, schreibt `RamLog` wieder. Kein Netz, kein eigener Thread, jeder Schreibfehler wird verschluckt. Messwerte (`Log.value`) werden nur bei Änderung geschrieben (höchstens 64 verschiedene Namen). Bedienung und Grenzen: [DEBUG.md](DEBUG.md).

Auto-Restore des letzten Effekts nach Neustart ist aktiv
(`AmbientFx.RESTORE_ON_BOOT = true`), Ablage unter `/mnt/persist/ambientfx/`
auf der MMX. **Live bestätigt (26.09.2026):** `/mnt/persist` ist ohne
vorheriges `mount -uw` beschreibbar, das Log zeigte korrekte
Speichervorgänge je Fahrzeugschlüssel (`ambientfx_key2.state`). Der
Restore-Pfad nach einem echten Neustart ist noch nicht separat getestet,
sollte aber symmetrisch funktionieren. Schlägt das Schreiben/Lesen doch
mal fehl, bleibt alles Übrige unberührt (kein Absturz, nur kein Restore),
siehe Kommentar über `AmbientFx.saveState()`.

---

## Wie es im Stock-HMI funktioniert (Analyse)

Alles aus der `lsd.jxe` (MU1329) decompiliert (`tools/decompile_stock.sh`, Ergebnis lokal in `java_stock/`, nicht im Repository).

* **Screen** `600028` = `CarScreenBag5.cARCARSETTINGSINTLIGHTMAIN(...)`
  (`de.audi.tghu.car.hmi.evohigh`). Generierter Widget-Baum: 8 statische
  `MenuItemController` vom Typ 256 (Radio-Button), alle am Range2D-Modell
  **601073** (X = aktives Profil, Y = Helligkeit), Event **600040**.
  `widgetID` = Profilnummer: 1 individuell, 2 Audi drive select, 3 Kontur-Weiß,
  4 Maritim-Blau, 5 Solar-Orange, 6 Impuls-Rot, 7 Vision-Weiß, 8 Sport-Rot.
  Sichtbarkeit kommt vom Fahrzeug (`IntLightViewOptions`, Modelle 601079…601085,
  602368) – Lükas „Sport red“ ist schlicht Profil 8 sichtbar.
* **Texte** kommen aus `lang_data/resources/HMICarEvoHigh/strings_<lang>.data`
  (Text-IDs 601979 „individuell“, 601980 „Audi drive select“, 601030 …).
  Für unsere Einträge setzt `FxMenuItemController` den Text direkt als
  String-Modell auf das Label – keine Sprachtabelle nötig.
* **Auswahl**: Fokus → `IntLightMenuEventBusiness.processItemFocused(widgetID)`
  → `IntLightCurrentFocus`. Druck → `IntLightRange2DBusiness.processKeyTyped`
  → `dsi.setIntLightActiveProfile(focus)`.
* **Fahrzeug-Rückmeldung**: `AbstractIntLightComponent.updateIntLightActiveProfile`
  setzt X des Range2D-Modells auf das aktive Profil.
* **DSI** `org.dsi.ifc.carlight.DSICarLight` – vollständig durchsucht, das
  sind **alle** Setter, die es gibt:
  `setIntLightActiveProfile(int)`, `setIntLightColour(int)`,
  `setIntLightIlluminationSet(setNr, helligkeit)` (Helligkeit **pro Zone**,
  keine Farbe), `setIntLightAmbientLightColor(IntLightRGBValues)`,
  `setIntLightContourLightColor(IntLightRGBValues)`,
  `setIntLightIlluminationProfile(profil, helligkeit)`. **Keine weiteren
  Farb-Setter** – siehe [Was nicht geht](#was-nicht-geht-recherche-runde-2)
  unten. Alles fire-and-forget (`remoteCallMethod`).
* **Fahrzeugdaten** `org.dsi.ifc.carvehiclestates.DSICarVehicleStates`,
  Attribut 14 `DynamicVehicleInfoHighFrequent` (engineSpeed, vehicleSpeed,
  longitudinalAcceleration, acceleratorPosition, shiftLight, powermeter …),
  Attribut 15 `DynamicVehicleInfoMidFrequent` (currentGear, recommendedGear,
  driverIsBraking, acceleratorKickDown, automaticGearShiftTransMode,
  blinkingState, lowBeamHeadlightIsActive – **Vorsicht:** Letzteres blieb im
  Live-Test durchgehend `false`, siehe Tag/Nacht-Verhalten oben).
* **Tag/Nacht** `org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStates`,
  Attribut 16, `updateDisplayDayNightDesign(boolean,int)` – die tatsächlich
  funktionierende Quelle (siehe `DaylightData.java`), extrahiert aus
  `de.audi.app.terminalmode.NightDayModeHandler` (steuert dort CarPlays
  eigene Tag/Nacht-Umschaltung).
* **Drive-Select-Modus** (für einen späteren „Drive“-Effekt):
  `DSICarDrivingCharacteristics` Attribut 13 `CHARISMAACTIVEPROFILE`.

### Was nicht geht (Recherche Runde 2)

Zwei Wünsche aus der Wunschliste sind an harten Grenzen der DSI-Schicht
geprüft und beantwortet worden – nicht als Vermutung, sondern anhand des
vollständig decompilierten Interfaces:

* **Einzelne LEDs/Zonen unabhängig ansteuern** (Fußraum ≠ Türtasche ≠
  Becherhalter, wie im VW/Audi-Servicehandbuch-Schaltplan zu Interior/
  Background Lighting mit LIN-Bus an J519 gezeigt): **nicht möglich.**
  `IntLightMembersMultiColor` / `IntLightMembersIlluminationSet` sind reine
  Konfigurations-Flags („ist Zone X verbaut“), keine Farbwerte. Es gibt nur
  die zwei Kanäle Fläche/Kontur; welche physischen LEDs an welchem Kanal
  hängen, entscheidet die Werksverkabelung/Codierung (Ambiente-Paket QQ2),
  nicht die Java-Schicht.
* **Musik-Equalizer für reaktives Licht**: **nicht möglich mit dem, worauf
  wir Zugriff haben.** `org.dsi.ifc.audio.DSISound`/`DSISoundListener`
  komplett durchsucht – es gibt nur Klangregler (Bass/Mitten/Höhen/Fader als
  feste Sollwerte), keinen Echtzeit-Pegelmesser/Spektrum der laufenden Wiedergabe.
  Musikreaktives Licht bräuchte einen Tap auf die rohe Audio-Pipeline (eigenes,
  deutlich größeres Projekt, nicht Teil dieses Patches).

---

## Architektur des Patches

```
java_patch/
├── de/audi/tghu/car/hmi/evohigh/CarScreenBag5.java     ERSATZ – nur cARCARSETTINGSINTLIGHTMAIN geändert
├── de/audi/app/car/evo/light/IntLightComponentEvo.java ERSATZ – Engine binden, X-Limit, Auswahl halten
├── de/audi/app/car/core/light/IntLightRange2DBusiness.java ERSATZ – Effekt-Einträge beim Drücken
└── de/mk/ambientfx/
    ├── AmbientFx.java              Engine (Singleton, Worker-Thread, sendet nur die Kontur per DSI)
    ├── FxCatalog.java              Effekt-Liste, zur Laufzeit befüllt (siehe EffectPluginLoader)
    ├── EffectPluginLoader.java     laedt *.ambiente-Dateien aus ambientfx_effects/ auf dem MIB
    ├── EffectFactory.java          kleine Fabrik-Schnittstelle fürs Plugin-System
    ├── FxMenu.java                 baut die Menü-Einträge für den Screen
    ├── FxMenuItemController.java   MenuItem mit freiem Text
    ├── VehicleData.java            DSICarVehicleStates-Listener (rpm, speed, gas, bremse, …)
    ├── DaylightData.java           DSIGeneralVehicleStates-Listener (Tag/Nacht, Attribut 16)
    ├── DriveModeData.java          DSICarDrivingCharacteristics-Listener (Drive Select, Attribut 13)
    ├── OutsideTempData.java        DSICarKombi-Listener (Außentemperatur, Attribut 65 - noch ungetestet)
    ├── ParkingDistanceData.java    DSICarParkingSystem-Listener (Einparkhilfe, Attribute 10/11)
    ├── Effect.java / Rgb.java      Vertrag (nur Kontur) + Farb-Helfer (HSV, Palette, Lerp)
    ├── Log.java / RamLog.java      Debug-Fassade + RAM-Protokoll (nur mit Marker, Obergrenze 256 KB)
    └── effects/                    Momentum, Momentum Select, Atmosphere - Quelltext liegt hier,
                                     ausgeliefert werden sie aber als .ambiente-Plugins (siehe unten),
                                     NICHT mehr fest in FxCatalog verdrahtet
```

Klassen in `/mnt/app/eso/hmi/lsd/jars/*.jar` stehen im Bootclasspath **vor**
`lsd.jxe` (siehe `lsd.sh`), deshalb ersetzen gleichnamige Klassen die
Stock-Klassen. Die drei ersetzten Klassen sind aus dem Decompilat neu
kompiliert – `verify.sh` beweist, dass sich nur die beabsichtigten Methoden
vom Original unterscheiden (alle anderen Methoden sind instruktionsgleich).
Alles Effekt-bezogene lebt in `de.mk.ambientfx` – die drei Stock-Ersatz-
Klassen mussten für v2 (Fläche/Kontur-Trennung, Tag/Nacht, 9. Effekt)
**kein einziges Mal** angefasst werden, das bestätigt `verify.sh`.

### Ablauf bei Auswahl „Momentum“

1. Menü-Fokus → `IntLightCurrentFocus = 9`
2. Drücken → `IntLightRange2DBusiness.processKeyTyped`: `9 >= FIRST_WIDGET_ID`
   → `AmbientFx.activate(9)`
3. Engine: Range2D-X := 9 (Radio-Button springt), `setIntLightActiveProfile(1)`,
   Worker-Thread startet
4. Fahrzeug meldet `updateIntLightActiveProfile(1)` → Stock-Code setzt X = 1 →
   `IntLightComponentEvo` ruft `AmbientFx.restoreSelection()` → X = 9
5. Worker: alle `periodMs` → `effect.tick()` liefert die fertige Kontur-Farbe
   (Tag/Nacht ist darin bereits eingerechnet, siehe oben) → bei Änderung
   ≥ 14 (RGB-Summe) `setIntLightContourLightColor`. Die Fläche wird nie
   angefasst.
6. Stock-Profil wählen → `deactivate()` + `setIntLightActiveProfile(n)`

### Neuen Effekt hinzufügen

Seit 25.09.2026 ausschließlich über das **Plugin-System** – kein `FxCatalog`-
Eintrag, kein Neubau des Haupt-Jars mehr nötig. Kurzfassung (Details,
inkl. aller verfügbaren Fahrzeugdaten, in `EFFEKTE_HANDBUCH.md`):

1. Klasse in `de/mk/ambientfx/effects/` (Interface `Effect`, liefert nur die
   Kontur-Farbe; Tag/Nacht kommt als fertiger `daylight`-Parameter rein,
   siehe `Momentum.tick()`)
2. `./build_java.sh && ./verify.sh`
3. `./build_plugin.sh java_patch/de/mk/ambientfx/effects/DeinEffekt.java "Label"`
   → `build/plugins/deineffekt.ambiente`
4. `deploy/copy_to_sd.sh` (bringt `build/plugins/` automatisch mit) → per
   Telnet nach `/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/` kopieren
   → kompletter MMI-Neustart

Menü, Range2D-Limit und Auswahl ziehen automatisch nach.

---

## Build

Siehe **[BAUEN_BUILD.md](BAUEN_BUILD.md)**: `vendor/` mit der eigenen `lsd.jar` anlegen, `tools/decompile_stock.sh`, `tools/prepare_sources.sh`, `./build_java.sh`, `./verify.sh`, `tools/build_all_plugins.sh`.

---

## Deploy

Siehe **[INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)** (SD-Menü im Green Engineering Menu oder Telnet, immer Jar + alle Plugins zusammen), [SD-ANLEITUNG.md](../SD-ANLEITUNG.md) und [DEPLOY_TELNET.md](../DEPLOY_TELNET.md).

Verträgt sich mit anderen Jars im Ordner `jars/` (zum Beispiel `carplay_hook.jar`) – keine Klassen-Überschneidung.

**Rückbau:** Menüpunkt *Deinstallieren* oder Jar und Plugin-Ordner löschen, Neustart. Das Fahrzeug steht danach ggf. noch auf dem Profil „individuell“ mit der letzten Effektfarbe – im Menü einfach ein Profil wählen.

---

## Nächste Schritte / Ideen

* Momentum in der Praxis feinschleifen (Farbgrenzen, Gas-Schwelle, Brems-Verhalten)
  bevor weitere Effekte zurückkommen
* `/mnt/persist`-Beschreibbarkeit per Telnet verifizieren (siehe README-Abschnitt
  „Debug-Log“ oben und Kommentar über `AmbientFx.saveState()`)
* Später: die entfernten v2-Effekte (siehe Git-Historie) selektiv als
  `.ambiente`-Plugins wiederbeleben (siehe „Plugin-System“ oben)
* **Drive**-Effekt: Farbe nach Drive-Select-Modus (`DSICarDrivingCharacteristics`
  Attr. 13) – comfort blau, dynamic rot, efficiency grün …
* **Blinker-Effekt** (`blinkingState` 1/2 → Seite kurz aufleuchten lassen)
* Lükas „* am Lenkrad = comfort ⇄ dynamic“: eigener Patch über
  `DSICarDrivingCharacteristics.setCharismaActiveProfile` + Lenkrad-Taste

---

## Referenzen

* Inspiration (kein Build-Bestandteil): [LuKa_devs](https://github.com/luka-dev) Custom-Patch für das MHI2Q (aufmerksam geworden durch [mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi)); seine Ideen zu **Drive** und **Scroll** und der Bootclasspath-Mechanismus (`jars/*.jar` vor `lsd.jxe`) sind übernommen bzw. angelehnt, Abweichungen stehen in den Klassenkommentaren.
* Die Audi-Klassen (`CarScreenBag5`, `IntLightComponentEvo`, `IntLightRange2DBusiness`) gehören Audi/e.solutions und werden hier **nicht** mitgeliefert; `patches/` enthält nur unsere Änderungen daran.
