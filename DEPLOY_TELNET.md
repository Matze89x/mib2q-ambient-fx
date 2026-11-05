# AmbientFX – Deploy & Test per Telnet + SD-Karte

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr. Dieses Dokument ist auf Deutsch, der englische Überblick steht in der [README](README.md).

Für: Audi A4 B9, MHI2Q (MU1329), MacBook Pro + E100-Kabel, Terminal-App (Telnet).

Zugang: IP `172.16.250.248` (Standard-Adresse des Geräts am Ethernet-Port), Login `root`. **Die Zugangsdaten und die Einrichtung des Telnet-Zugangs sind nicht Teil dieses Repositorys** – verwende deinen eigenen Zugang.

> **Seit 04.10.2026 gibt es zusätzlich das SD-Menü** (GEM → M.I.B. → ambientfx, ohne Telnet; siehe
> `SD-ANLEITUNG.md`). Dieser Telnet-Weg bleibt unverändert gültig – als Ersatzweg und
> für die Übergabe nach jedem Build.

---

## 1. Kann ich das MMI damit „kaputt“ machen?

Kurz: **Das Steuergerät kann dadurch nicht „nicht mehr starten“.** Der
schlimmste realistische Fall ist eine HMI, die abstürzt oder Menüpunkte
nicht zeigt – und das ist per Telnet in einer Minute rückgängig zu machen.
**Bereits einmal erfolgreich installiert und im Fahrzeug getestet** (v1,
17.09.2026) – dieser Ablauf ist erprobt, nicht mehr nur Theorie.

Warum:

| Was | angefasst? | Konsequenz |
|-----|-----------|------------|
| Bootloader, IFS (`/net/mmx/ifs`), Kernel, `/mnt/system` | **nein** | QNX bootet immer, Netzwerk/Telnet ist immer da |
| `lsd.jxe` (die eigentliche HMI) | **nein** | bleibt original |
| `lsd.sh` (Startskript) | **nein** | lädt von sich aus alle `*.jar` aus `jars/` – genau wie `Cockpit_Mirror.jar` |
| `/mnt/app/eso/hmi/lsd/jars/` | **eine Datei dazu**: `ambient_fx.jar` | löschen = Stock-Zustand |

Der Patch nutzt exakt den Mechanismus, den Lükas `carplay_hook.jar` /
`Cockpit_Mirror.jar` seit Monaten auf deinem Gerät nutzen: eine Jar im
Bootclasspath vor `lsd.jxe`. Es gibt keine Codierung, kein EEPROM, keine
FEC, keine System-Partition.

Was theoretisch schiefgehen kann, und was dann passiert:

1. **Jar defekt kopiert** (z. B. SD-Karte zu früh gezogen) → J9 kann die
   Klassen nicht laden → HMI startet ggf. nicht oder startet in Schleife
   (schwarzer Bildschirm / Audi-Logo-Loop). Telnet geht trotzdem →
   Rollback → Neustart → fertig. Deshalb: **Prüfsumme (`cksum`) vergleichen**,
   bevor du neu startest.
2. **Fehler in meinem Code** → Java-Exception. Alles, was wir aufrufen, ist
   in `try/catch(Throwable)` gekapselt; die drei ersetzten Stock-Klassen sind
   nachweislich bis auf die geänderten Methoden Byte-für-Byte gleich
   (`verify.sh`). Worst case: Menüpunkte fehlen oder ein Effekt läuft nicht –
   Rest der HMI läuft normal.
3. **Fahrzeug reagiert unerwartet auf die RGB-Werte** (Farbe bleibt hängen,
   flackert) → Stock-Profil im Menü wählen, das beendet den Effekt. Das BCM
   selbst kann man mit Farbwerten nicht beschädigen, es sind normale
   CAN-Botschaften, die auch das MMI beim Farbdrehen schickt.

Was die Sicherheit **wirklich** ausmacht:

* Backup vorher (Original-`jars/` sichern, dauert 10 s).
* Nach dem Kopieren **30 s warten**, dann erst neu starten.
* Telnet-Verbindung vorher testen, damit du im Notfall reinkommst.
* Nie Zündung/Strom während des Kopierens abschalten.

---

## 2. Überschneidung mit dem Cockpit-Mirror (Audi A4 B9 MMI/VC Modifikation)?

Geprüft, **keine Überschneidung**:

* `Cockpit_Mirror.jar` enthält Klassen aus `com/luka/carplay/*`,
  `de/audi/app/terminalmode/*`, `de/audi/app/combi/*`,
  `de/audi/tghu/navi/app/cluster/*`, `org/dsi/ifc/carplay/*`.
* `ambient_fx.jar` enthält `de/audi/tghu/car/hmi/evohigh/CarScreenBag5`,
  `de/audi/app/car/evo/light/*`, `de/audi/app/car/core/light/*`, `de/mk/ambientfx/*`.
* Schnittmenge: **0 Klassen**.
* Die Cockpit-Mirror-Skripte (CHECK / START / AutoStart) suchen nur nach
  `Cockpit_Mirror.jar` und `carplay_hook.jar`; eine `ambient_fx.jar` daneben
  ignorieren sie.
* Andere Daten: Cockpit-Mirror = Kombi/VC-Videokanal, BAP, DisplayManager.
  AmbientFX = `DSICarLight` (Innenlicht) + lesend `DSICarVehicleStates`
  (Drehzahl/Speed/Abblendlicht). Verschiedene Dienste, verschiedene Bussignale.

---

## 3. SD-Karten-Struktur

Der komplette Projektordner `mib2q-ambient-fx` liegt im **Root** der
Toolbox-SD-Karte (FAT32) – neben `Toolbox/`, nicht darin (per Finder rüberziehen
reicht). Backup und Logs kommen **daneben**, damit ein erneutes Rüberziehen
des Projektordners sie nie überschreibt:

```
<SD>/
├── Toolbox/                    ← Cockpit-Mirror / MIB-Toolbox (unverändert lassen)
├── Custom/, Modifications/     ← Cockpit-Mirror (unverändert lassen)
├── mib2q-ambient-fx/           ← der Projektordner (Finder oder deploy/copy_to_sd.sh)
│   └── build/ambient_fx.jar          der Patch (+ .cksum daneben)
├── ambientfx_backup/           ← Original jars/ + lsd.sh (siehe Schritt 4.3)
└── ambientfx_logs/             ← alte Logs (bis 02.10.2026)
```

Auf die Karte bringen (Mac, SD gesteckt, Volume-Name mit `ls /Volumes`):

```bash
cd <dein-Projektordner> && ./build_java.sh && ./verify.sh && deploy/copy_to_sd.sh /Volumes/<SD-KARTE>
```

`copy_to_sd.sh` ist das „Rüberziehen“ in sauber: es aktualisiert nur
`<SD>/mib2q-ambient-fx/`, lässt `build/classes`, `java_stock` und macOS-Müll
weg und vergleicht danach die Prüfsumme der Jar auf der Karte. Manuelles
Rüberziehen im Finder geht genauso gut.

Danach SD-Karte **sauber auswerfen** (Finder → Auswerfen), sonst fehlen Daten.

---

## 4. Telnet-Ablauf – von Hand, Schritt für Schritt

Kein Skript nötig – jeder Befehl einzeln, damit du siehst, was passiert.
(Die Helfer-Skripte in `deploy/device/` tun exakt dasselbe und liegen als
Abkürzung bereit, falls gewünscht – siehe [Abschnitt 7](#7-merkzettel).)

Mac: E100 am Laptop, Zündung an, MMI hochgefahren (≥ 1 Min warten), SD-Karte
im MMI-Slot.

### 4.1 Verbinden

```
telnet 172.16.250.248 123
```
Login `root` + dein Passwort (nicht Teil dieses Repositorys).

Port 123 = **RCC** (unsere Standard-Route für Dateizugriff, Pfade unter
`/net/mmx/...`). Port 23 wäre die MMX direkt – nur für Prozess-Checks
(`pidin`) nötig, da Port 123 dort immer "nicht gefunden" zeigt.

### 4.2 Partitionen beschreibbar mounten

```sh
mount -uw /net/mmx/mnt/app
mount -uw /net/mmx/mnt/system
mount -uw /net/mmx/fs/sda0
```
Wir schreiben nur nach `/mnt/app`; `/mnt/system` wird gemountet, aber nicht
verändert (feste Routine für diese Kiste). **Die SD-Karte (`sda0`) selbst
ist standardmäßig ebenfalls read-only gemountet** – ohne dieses `mount -uw`
schlägt Schritt 4.4 (Backup, schreibt nach `sda0`) mit "Read-only file
system" fehl. Leicht zu vergessen, weil `/mnt/app`/`/mnt/system` schon so
vertraut sind – deshalb hier immer alle drei zusammen mounten.

### 4.3 Umgebung prüfen (nur lesen)

```sh
ls /net/mmx/fs/
ls /net/mmx/fs/sda0/mib2q-ambient-fx/build/
ls -la /net/mmx/mnt/app/eso/hmi/lsd/jars/
```
Erwartet: `sda0` (ggf. `sdb0` – dann in allen Befehlen unten `sda0` ersetzen);
`ambient_fx.jar` + `.cksum` auf der Karte; in `jars/` deine bisherigen Dateien
(`carplay_hook.jar` o. ä.), noch **keine** `ambient_fx.jar`.

### 4.4 Backup (Pflicht, vor jeder Installation/Aktualisierung)

```sh
mkdir -p /net/mmx/fs/sda0/ambientfx_backup/jars
cp -R /net/mmx/mnt/app/eso/hmi/lsd/jars/. /net/mmx/fs/sda0/ambientfx_backup/jars/
cp /net/mmx/mnt/app/eso/hmi/lsd/lsd.sh /net/mmx/fs/sda0/ambientfx_backup/lsd.sh
ls -la /net/mmx/fs/sda0/ambientfx_backup/jars
```
Byte-Vergleich (RCC hat kein `cut`/`cmp`, wir leihen `cmp` von der MMX):
```sh
cd /net/mmx/mnt/app/eso/hmi/lsd/jars
for f in *; do /net/mmx/mnt/app/armle/usr/bin/cmp $f /net/mmx/fs/sda0/ambientfx_backup/jars/$f && echo "OK $f"; done
cd /
```
**Kontrollpunkt:** eine `OK`-Zeile pro Datei. Erst weiter, wenn alle da sind.

### 4.5 Installieren

```sh
cksum /net/mmx/fs/sda0/mib2q-ambient-fx/build/ambient_fx.jar
cat /net/mmx/fs/sda0/mib2q-ambient-fx/build/ambient_fx.jar.cksum
```
**Kontrollpunkt:** beide Zahlen gleich (die Datei enthält, was `./build_java.sh`
am Mac zuletzt ausgegeben hat). Ungleich → Karte erneut vorbereiten, nicht
installieren.

```sh
cp /net/mmx/fs/sda0/mib2q-ambient-fx/build/ambient_fx.jar /net/mmx/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar
chmod 755 /net/mmx/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar
cksum /net/mmx/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar
ls -la /net/mmx/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar
```
**Kontrollpunkt:** dieselbe Prüfsumme wie eben, `ls` zeigt die Datei mit
korrekter Größe. Bei Abweichung: `rm` und nochmal kopieren.

### 4.5b Effekt-Plugins (`.ambiente`) – IMMER zusammen mit der Jar

Die Effekte liegen als Plugins in `ambientfx_effects/` **auf dem MMI**
(nicht auf der SD). Die Jar enthält dieselben Effekt-Klassen nochmal, und
der Plugin-Classloader fragt nach Java-Regel zuerst den Parent (= die Jar) –
deshalb gilt: **Jar und Plugins immer gemeinsam aktualisieren, nie nur eines
von beiden.** Am einfachsten: alle Plugins kopieren, das ist idempotent und
nimmt jede Unsicherheit, was auf dem Auto schon aktuell ist.

```sh
mkdir -p /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects
cp /net/mmx/fs/sda0/mib2q-ambient-fx/build/plugins/*.ambiente /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/
cd /net/mmx/fs/sda0/mib2q-ambient-fx/build/plugins
for f in *.ambiente; do cksum $f; cksum /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/$f; done
cd /
ls -la /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/
```
**Kontrollpunkt:** je Plugin zwei Zeilen mit denselben zwei Zahlen (Prüfsumme,
Größe). Aktuell fünf Plugins: `atmosphere`, `drive`, `momentum`,
`momentumselect`, `scroll`. Die Reihenfolge im Menü ist alphabetisch nach
Dateiname.

**Einmalig nach dem GDS-Merge (30.09.2026): alte Testeffekte entfernen.** Das
`cp *.ambiente` überschreibt nur, es löscht nichts – die beiden früheren
Testeffekte müssen von Hand weg, sonst bleiben sie im Menü:
```sh
rm /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/momentumgds.ambiente
rm /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/momentumgdsselect.ambiente
ls -la /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/
```
**Kontrollpunkt:** `ls` zeigt genau fünf `.ambiente`-Dateien.

### 4.6 Zurück auf read-only, warten, Neustart

```sh
mount -ur /net/mmx/mnt/app
mount -ur /net/mmx/mnt/system
mount -ur /net/mmx/fs/sda0
```
**30 Sekunden warten** (Flash-Flush, die RCC hat kein `sync`), `exit`, dann
**kompletter MMI-Neustart** (Zündung aus, Auto abschließen, warten bis das
MMI wirklich aus ist – oder deine Reset-Tastenkombination). Ein reiner
Zündungs-Aus/An ohne Busruhe reicht oft nicht, weil die Java-VM dann
weiterläuft.

### 4.7 Rollback (wenn nötig)

```sh
mount -uw /net/mmx/mnt/app
rm /net/mmx/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar
mount -ur /net/mmx/mnt/app
```
→ 30 s warten → kompletter Neustart → Stock.

Falls das MMI nach dem Patch **gar kein Bild** zeigt: Telnet geht trotzdem
(RCC/MMX booten unabhängig von der HMI) → Rollback wie oben. Falls Telnet
ausnahmsweise nicht erreichbar ist: Zündung aus, 5 Min warten, erneut.

---

## 5. Nach dem Neustart: testen

AmbientFX schreibt **keine eigene Log-Datei**. Du prüfst das Ergebnis am Verhalten der Beleuchtung und mit dem Menüpunkt *Status* des SD-Menüs.

### Testprotokoll

> **Stand-Hinweis (29.09.2026):** Die Tabelle unten beschreibt den
> ursprünglichen Ein-Effekt-Stand (nur Momentum) und ist als Grundcheck
> weiterhin gültig. Es gibt inzwischen fünf Effekte – effektspezifische
> Tests stehen im [README](README.md) (Abschnitte je Effekt, u. a. „Gangwahlhebel
> D → S“ mit dem D→S-Test) und im jeweiligen Handoff im
> Chat.

**v3 (nach zwei Runden Live-Test-Feedback korrigiert): nur noch ein Effekt,
„Momentum“.** Er steuert **nur die Kontur**; die Fläche stellst du ganz
normal über den Farbregler im „individuell“-Profil ein – daran ändert der
Effekt nichts. Farbe = voller Regenbogen über 0–100 km/h, gesnappt auf
echte Audi-Werksfarben (wegen des Retrofit-LED-Streifens vorne). Tag/Nacht
steckt direkt in Momentum selbst: **tagsüber immer 100 % hell** (der erste
Test zeigte, dass „nur bei kräftigem Gas hell" tagsüber praktisch
unsichtbar wirkt), **nachts** 25–100 % je nach Gaspedal (war erst 50–100 %,
im Test aber kaum spürbar).

| # | Aktion | Erwartung im MMI | Erwartung im Log | wenn nicht |
|---|--------|------------------|------------------|------------|
| 1 | MMI hochfahren | normal, keine Fehlermeldung | – (Log erst nach `touch` + Menü öffnen) | schwarzer Bildschirm > 3 Min → Rollback |
| 2 | Fahrzeug → Fahrzeugeinstellungen → Ambientebeleuchtung | unter „Audi drive select“ steht ein neuer Eintrag **Momentum** | `FxMenu: created 1 fx menu items`, `range2D X limit extended to 9`, `bound (dsi=true, range2d=true)` | Eintrag fehlt → Log auf `createMenuItems failed` prüfen |
| 3 | **Momentum** wählen, Fahrzeug steht (0 km/h), **bei Tageslicht** | Kontur = **durchgehend volles Rot**, nicht nur kurz beim Antippen des Gases | `activate widgetId=9 (Momentum)` | Licht bleibt aus/blitzt nur kurz auf → genau der jetzt behobene Fehler, Log + Firmwarestand schicken |
| 4 | Langsam losfahren, bei Tageslicht auf ~20/40/70/100 km/h beschleunigen, dabei **wenig** Gas geben (normales Fahren) | Kontur ist die **ganze Zeit voll hell** und durchläuft den vollen Regenbogen (Rot → Orange → Gelb → Grün → Blau → Lila, ab 100 km/h wieder Rot) – unabhängig vom Gaspedal; die gezeigten Farben sind **echte Audi-Werksfarben**, keine frei gestreamten Zwischenwerte | `alive: conSent=… con=… veh[rpm=… v=…]` mit echten Tempowerten, `lowBeam=false` | `v=0.0` beim Fahren → `vehicleSpeed`/`speedUnit` prüfen; Farben flackern trotzdem → Log + genaue Beschreibung, dann evtl. „factory color list complete“ im Log fehlt |
| 5 | Bei Dunkelheit/mit Abblendlicht: bei ~0–100 % Gas fahren | Kontur ist bei losgelassenem Gas schon **spürbar gedimmt** (25 %), wird mit mehr Gas **deutlich** heller, bei Vollgas **voll hell** – nie ganz dunkel | `acc=` im Log steigt mit dem Pedalweg, `lowBeam=true` | Kein wahrnehmbarer Unterschied beim Gasgeben → Log schicken (prüfen ob `acc=` im Log überhaupt mit dem Pedal variiert) |
| 6 | Bremsen (aus jedem Tempo/Gaszustand, Tag oder Nacht) | Kontur springt **sofort** auf volles, helles **Rot**, unabhängig von Tempo/Gas/Tageszeit | `brake=true` im Log | verzögert oder bleibt Tempofarbe → Log schicken |
| 7 | Bremse loslassen | Kontur springt zurück auf die zum aktuellen Tempo/Tageszeit passende Farbe/Helligkeit | `brake=false` | – |
| 8 | Fläche: im „individuell“-Profil manuell eine Farbe/Helligkeit einstellen, dann Momentum an-/abwählen | Fläche bleibt exakt bei der manuell gewählten Farbe/Helligkeit, egal was die Kontur macht | – | Fläche ändert sich von selbst → sofort melden, das darf nicht mehr passieren |
| 9 | Auf den **aktiven** Effekt nochmal drücken | Helligkeits-Screen öffnet (Titel „individuell“) | – | kosmetisch, nur notieren |
| 10 | Zündung aus/an | Momentum ist automatisch wieder aktiv (Auto-Restore, siehe README) | `state loaded: widgetId=9 …`, `restoring saved effect widgetId=9` | Restore bleibt aus → `/mnt/persist`-Beschreibbarkeit prüfen (siehe docs/TECHNIK.md) |
| 11 | Andere Menüs / Cockpit-Mirror / CarPlay kurz durchklicken | alles wie vorher | – | Auffälligkeiten notieren |

Es liegt nichts auf dem Auto, das nach dem Test gesichert oder gelöscht werden müsste. Notiere, welche Zeile der Tabelle nicht gepasst hat.

---

## 6. Was du sonst noch im Telnet sehen kannst

* Läuft die HMI? Auf **Port 23** (MMX direkt) einloggen und `pidin | grep j9`
  → eine Zeile mit `j9`. Über Port 123 zeigt `pidin` immer "nicht gefunden",
  weil es dort den RCC-Prozessraum listet, nicht den der MMX.
* AmbientFX-Debug-Protokoll (nur RAM, nur mit Marker): `cat /dev/shmem/ambientfx.log`
  auf Port 23, auf Port 123 mit `/net/mmx` davor. Einschalten und Grenzen: [docs/DEBUG.md](docs/DEBUG.md).
* Ist `/mnt/app` wieder read-only? `on -f mmx mount` zeigt alle MMX-Mounts.
* MMX-Partitionen insgesamt: `on -f mmx mount` (zeigt u. a. `/mnt/app`,
  `/mnt/system`, `/mnt/persist`, `/ramdisk` – siehe docs/TECHNIK.md).

---

## 7. Merkzettel

```
telnet 172.16.250.248 123                                   # login root (dein eigenes Passwort)
mount -uw /net/mmx/mnt/app
mount -uw /net/mmx/mnt/system
mount -uw /net/mmx/fs/sda0                                  # NICHT vergessen - sonst schlaegt 4.4 (Backup) fehl
# Backup + cmp-Check (4.4), Prüfsumme + cp + chmod (4.5), ALLE Plugins kopieren + Prüfsummen (4.5b)
mount -ur /net/mmx/mnt/app
mount -ur /net/mmx/mnt/system
mount -ur /net/mmx/fs/sda0
# 30 s warten, kompletter MMI-Neustart
# ... testen (AmbientFX schreibt keine Log-Datei)
```

Abkürzung über die Helfer-Skripte (tun dasselbe, ohne alles einzeln zu tippen):
```
. /net/mmx/fs/sda0/mib2q-ambient-fx/deploy/device/00_backup.sh
. /net/mmx/fs/sda0/mib2q-ambient-fx/deploy/device/10_install.sh
. /net/mmx/fs/sda0/mib2q-ambient-fx/deploy/device/90_rollback.sh
```
