<a id="english"></a>

# Test procedure in the car – status, then remove everything, then reinstall cleanly (state 2026-10-04)

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **For testing on private property only, not in public road traffic** (see [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Intervention in the HMI software, use at your own risk.

Matze89x in cooperation with DEB and LuKa_dev. Attribution required, no commercial use/selling. **Note:** the menu texts on the car’s screen are in German; this document quotes them as they appear and adds the English meaning in brackets.

**Before:** ignition on, MMI booted, SD card in **slot 1**, vehicle on a charger or engine running (never lose power in the middle of a run). Allow about 10 minutes; every reboot takes about 1 minute.
**Open:** on the home screen your known key combination for the Green Engineering Menu (GEM) → **==>>M.I.B.<<==** → **ambientfx**. You select an entry as always in the GEM. Never abort runs (neither “Back” nor pulling the card).

Every entry that changes something does the same: **backup to the SD → change → read back and compare checksums (“PRUEFUNG”, i.e. “CHECK”) → on “ERGEBNIS: OK” (“RESULT: OK”) the MMI reboots by itself after 15 seconds.** If something is not clean, you see “ERGEBNIS: ABGEBROCHEN” (“RESULT: ABORTED”) or “MIT HINWEISEN” (“WITH NOTES”) and there is **no** reboot. Please take a **photo of the screen** at every step.

## 0. Look at the starting point
**3. Show status** → photo. Expected depending on the state:
* `SD  Jar` with state and checksum, `SD  Effekte: 5 Plugins, alle geprueft` (“SD effects: 5 plugins, all checked”).
* `Auto Jar` / `Auto Effekte` (“car jar” / “car effects”) show what is in the car (if already installed: `= wie SD` and `5 gleich wie SD`, i.e. “same as SD”; otherwise “nicht installiert” (“not installed”) or different).

## A. Remove everything
**2. Uninstall** (only if AmbientFX is installed; otherwise “nichts von mib2q-ambient-fx installiert” (“nothing of mib2q-ambient-fx installed”), no reboot – then go straight to C).
In order: backup of all affected files to the SD (`backup/<no>_uninstall_<jar no>/`) → jar removed → all effect plugins removed (folder `ambientfx_effects` removed if empty) → remembered effect removed → search for leftovers → **PRUEFUNG**:

| Line | must say |
|---|---|
| Jar | “nicht mehr vorhanden” (no longer present) |
| Effekte (effects) | “nicht mehr vorhanden” |
| Gemerkter Effekt (remembered effect) | “nicht mehr vorhanden” |
| Andere Jars (other jars) | “unverändert” (checksums the same) – including all foreign jars … they are named one by one with a checksum |
| 5. Reste (leftovers) | “keine” (none) |

Then: **“ERGEBNIS: OK – alle Prüfsummen stimmen”** (“RESULT: OK – all checksums match”) and **“Das MMI startet in 15 Sekunden von selbst NEU”** (“the MMI restarts by itself in 15 seconds”). (If there are leftovers, “REST gefunden” (“leftover found”) appears and **no** reboot – please take a photo.)
After the reboot: the MMI is in its original state, there is no AmbientFX menu under “Audi drive select” any more.

## B. Check that everything is really gone
GEM → ambientfx → **3. Status** → photo. Expected: `Auto Jar    : nicht installiert` (not installed) · `Ordner ambientfx_effects nicht vorhanden` (folder does not exist) · `Gemerkter Effekt: 0 state-Datei(en)` (remembered effect: 0 state files).

## C. Reinstall
**1. Install / Update**
Checks everything on the SD first (jar checksum against `ambient_fx.jar.cksum`, each plugin against `plugins.cksum` – otherwise **abort without changes**), creates – if not there yet – **the full backup once** of the jars (`backup/original/` in the folder `mib2q-ambient-fx`, with byte comparison; directly after step A it contains the pure original state), backs up, copies the jar and all plugins, reads everything back from the car:

| PRUEFUNG line | must say |
|---|---|
| Jar | `[<checksum>] = SD` (the number is also shown in the status and in `plugins.cksum`/`ambient_fx.jar.cksum`) |
| Effekte (effects) | `alle 5 Plugin-Dateien = SD` (all 5 plugin files = SD) |
| Old effects momentumgds/momentumgdsselect | not present |
| Kontrollpunkt (check point) | `genau 5 .ambiente-Dateien im Auto` (exactly 5 .ambiente files in the car) |
| Other jars in the folder | unchanged |

Then “ERGEBNIS: OK” and a reboot after 15 seconds.

## D. Check
1. GEM → ambientfx → **3. Status**: `Auto Jar … = wie SD`, `Auto Effekte: 5 gleich wie SD, 0 abweichend, 0 fehlend` (5 same as SD, 0 different, 0 missing), `Ergebnis: alles auf dem Stand der SD.` (result: everything at the SD’s state).
2. **1. Install / Update** once more → must say **“Alles ist schon aktuell – nichts zu tun”** (“Everything is already up to date – nothing to do”) and **not** reboot.
3. In the car: Vehicle → Vehicle settings → Ambient lighting → under “Audi drive select” there are **Atmosphere, Drive, Momentum, Momentum select, Scroll** (alphabetical, exactly five).

## If something does not work
* The menu shows “ABGEBROCHEN” (“ABORTED”): read the message (photo), nothing else was changed; backups are under `mib2q-ambient-fx/backup/` on the SD, logs under `log/`.
* “Die SD-Karte ist nicht beschreibbar” (“The SD card is not writable”): check the small slide switch (LOCK) at the side of the card (must point towards the contacts), re-insert the card.
* “Die SD hat einen AELTEREN Jar-Stand” (“The SD has an OLDER jar state”): intended, see downgrade protection in SD-ANLEITUNG.
* Switch off the automatic reboot: put an empty file `NO_AUTO_REBOOT` into the folder `mib2q-ambient-fx`.
* Fallback telnet (only if the menu does not appear at all):
```
telnet 172.16.250.248 123          (login root, your own password)
mount -uw /net/mmx/mnt/app
mount -uw /net/mmx/mnt/system
mount -uw /net/mmx/fs/sda0
sh /net/mmx/fs/sda0/mib2q-ambient-fx/esd/afx_status.sh         (or afx_uninstall.sh / afx_install.sh – the same entries as in the menu, incl. reboot)
```

---

<a id="deutsch"></a>

# Testablauf im Auto – Status, dann alles entfernen, dann sauber neu installieren (Stand 2026-10-04)

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr.

Matze89x in cooperation with DEB and LuKa_dev. Namensnennung Pflicht, keine kommerzielle Nutzung/Verkauf.

**Vorher:** Zündung an, MMI ist hochgefahren, SD-Karte in **Slot 1**, Fahrzeug an einem Ladegerät oder Motor an (nie mitten im Lauf Strom verlieren). Rechne mit ca. 10 Minuten; jeder Neustart dauert etwa 1 Minute.
**Öffnen:** auf der Startseite deine bekannte Tastenkombination fürs Grüne Engineering-Menü (GEM) → **==>>M.I.B.<<==** → **ambientfx**. Einen Eintrag wählst du wie immer im GEM. Läufe nie abbrechen (weder „Zurück“ noch Karte ziehen).

Jeder Punkt, der etwas ändert, macht dasselbe: **Sicherung auf die SD → Änderung → Rücklesen und Prüfsummen vergleichen („PRUEFUNG“) → bei „ERGEBNIS: OK“ startet das MMI nach 15 Sekunden von selbst neu.** Ist etwas nicht sauber, steht „ERGEBNIS: ABGEBROCHEN“ oder „MIT HINWEISEN“, und es gibt **keinen** Neustart. Bei jedem Schritt bitte ein **Foto vom Bildschirm**.

## 0. Ausgangslage ansehen
**3. Status anzeigen** → Foto. Je nach Stand erwartet:
* `SD  Jar` mit Stand und Prüfsumme, `SD  Effekte: 5 Plugins, alle geprueft`.
* `Auto Jar` / `Auto Effekte` zeigen, was im Auto liegt (wenn schon installiert: `= wie SD` und `5 gleich wie SD`; sonst „nicht installiert“ bzw. abweichend).

## A. Alles entfernen
**2. Deinstallieren** (nur wenn AmbientFX installiert ist; sonst „nichts von mib2q-ambient-fx installiert“, kein Neustart – dann direkt weiter mit C).
Es passiert der Reihe nach: Sicherung aller betroffenen Dateien auf die SD (`backup/<Nr>_uninstall_<Jar-Nr>/`) → Jar weg → alle Effekt-Plugins weg (Ordner `ambientfx_effects` weg, wenn leer) → gemerkter Effekt weg → Suche nach Resten → **PRUEFUNG**:

| Zeile | muss lauten |
|---|---|
| Jar | nicht mehr vorhanden |
| Effekte | nicht mehr vorhanden |
| Gemerkter Effekt | nicht mehr vorhanden |
| Andere Jars | unverändert (Prüfsummen gleich) – darunter alle fremden Jars … werden einzeln mit Prüfsumme genannt |
| 5. Reste | keine |

Danach: **„ERGEBNIS: OK – alle Prüfsummen stimmen“** und **„Das MMI startet in 15 Sekunden von selbst NEU“**. (Gibt es Reste, kommt „REST gefunden“ und **kein** Neustart – bitte Foto.)
Nach dem Neustart: das MMI ist im Originalzustand, es gibt kein AmbientFX-Menü mehr unter „Audi drive select“.

## B. Prüfen, dass wirklich alles weg ist
GEM → ambientfx → **3. Status** → Foto. Erwartet: `Auto Jar    : nicht installiert` · `Ordner ambientfx_effects nicht vorhanden` · `Gemerkter Effekt: 0 state-Datei(en)`.

## C. Neu installieren
**1. Installieren / Updaten**
Prüft vorher alles auf der SD (Jar-Prüfsumme gegen `ambient_fx.jar.cksum`, jedes Plugin gegen `plugins.cksum` – sonst **Abbruch ohne Änderung**), legt – falls noch nicht da – **einmalig die Komplett-Sicherung** der Jars an (`backup/original/` im Ordner `mib2q-ambient-fx`, mit Byte-Vergleich; direkt nach Schritt A enthält sie den reinen Originalzustand), sichert, kopiert Jar und alle Plugins, liest alles aus dem Auto zurück:

| PRUEFUNG-Zeile | muss lauten |
|---|---|
| Jar | `[<Prüfsumme>] = SD` (die Zahl steht auch im Status und in `plugins.cksum`/`ambient_fx.jar.cksum`) |
| Effekte | `alle 5 Plugin-Dateien = SD` |
| Alt-Effekte momentumgds/momentumgdsselect | nicht vorhanden |
| Kontrollpunkt | `genau 5 .ambiente-Dateien im Auto` |
| Andere Jars im Ordner | unverändert |

Danach „ERGEBNIS: OK“ und Neustart nach 15 Sekunden.

## D. Prüfen
1. GEM → ambientfx → **3. Status**: `Auto Jar … = wie SD`, `Auto Effekte: 5 gleich wie SD, 0 abweichend, 0 fehlend`, `Ergebnis: alles auf dem Stand der SD.`
2. **1. Installieren / Updaten** noch einmal → muss **„Alles ist schon aktuell – nichts zu tun“** sagen und **nicht** neu starten.
3. Im Auto: Fahrzeug → Fahrzeugeinstellungen → Ambientebeleuchtung → unter „Audi drive select“ stehen **Atmosphere, Drive, Momentum, Momentum select, Scroll** (alphabetisch, genau fünf).

## Wenn etwas nicht klappt
* Das Menü zeigt „ABGEBROCHEN“: Meldung lesen (Foto), es wurde nichts weiter verändert; Sicherungen liegen unter `mib2q-ambient-fx/backup/` auf der SD, Protokolle unter `log/`.
* „Die SD-Karte ist nicht beschreibbar“: den kleinen Schiebeschalter (LOCK) an der Seite der Karte prüfen (muss zu den Kontakten hin stehen), Karte neu einstecken.
* „Die SD hat einen AELTEREN Jar-Stand“: gewollt, siehe Downgrade-Schutz in der SD-ANLEITUNG.
* Automatischen Neustart abschalten: leere Datei `NO_AUTO_REBOOT` in den Ordner `mib2q-ambient-fx` legen.
* Ersatzweg Telnet (nur wenn das Menü gar nicht erscheint):
```
telnet 172.16.250.248 123          (Login root, dein eigenes Passwort)
mount -uw /net/mmx/mnt/app
mount -uw /net/mmx/mnt/system
mount -uw /net/mmx/fs/sda0
sh /net/mmx/fs/sda0/mib2q-ambient-fx/esd/afx_status.sh         (oder afx_uninstall.sh / afx_install.sh – dieselben Punkte wie im Menü, inkl. Neustart)
```
