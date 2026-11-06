<a id="english"></a>

# mib2q-ambient-fx – SD menu (AmbientFX, state 2026-10-04)

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **For testing on private property only, not in public road traffic** (see [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Intervention in the HMI software, use at your own risk.

Matze89x in cooperation with DEB and LuKa_dev. Attribution required, **no commercial use/selling** (see LICENSE.md in the project).

With this SD card you install, update and remove AmbientFX (the ambient lighting effects) **without telnet**: in the Green Engineering Menu (GEM) under **M.I.B. → ambientfx**. The project always consists of **the jar and all five effect plugins together** (Atmosphere, Drive, Momentum, Momentum select, Scroll) – the menu treats them that way, too. **Note:** the menu texts on the car’s screen are in German; this document quotes them as they appear and adds the English meaning in brackets.

**Prerequisite:** the card must already carry the **M.I.B. toolbox** (“Pre-V3.7.1 – MHIG Edition”, see the [release page](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/releases/tag/V3.7.1) and its [wiki/documentation](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/wiki)), and your unit must be able to run it **as the toolbox’s own instructions describe**. Our package only adds **one more line, `ambientfx`**, under M.I.B. in the Green Engineering Menu; the rest of the toolbox stays unchanged.

## 1. What is on the card

**One single folder `mib2q-ambient-fx/`** – everything is in there, and the menu only writes there (backups, logs). Outside of it the only thing that changes on the card are the two menu files of the toolbox (an appended block `ambientfx`).

```
<SD root>                         M.I.B. toolbox "Pre-V3.7.1 – MHIG Edition" (unchanged, except the two menu files below)
  mib2q-esd-original/             the original menu files of the toolbox (shared, never overwritten)
  esd/Launcher-sda0.esd           menu file of the toolbox + appended blocks of other mods (…, here additionally "ambientfx" – at the very bottom)
  esd/Launcher-MHIG-sda0.esd      same (for MHIG units)
  mib2q-ambient-fx/               OUR folder (the project folder, as before via deploy/copy_to_sd.sh)
    esd/afx_*.sh                  the 3 menu scripts + afx_lib.sh, known_versions.txt, VERSION
    build/ambient_fx.jar (+ .cksum)   the jar
    build/plugins/*.ambiente      the 5 effect plugins, build/plugins.cksum = their checksums
    backup/<no>_<entry>_<jar no>/ backups: BEFORE every change in the car a copy goes here (no = running number, jar no = checksum of the backed-up jar)
    backup/original/              one-time full backup of the car’s jars (+ lsd.sh); the menu creates it itself on first install
    log/                          logs: install.txt / uninstall.txt / status.txt = last run, <no>_install.txt etc. = every run with a backup (same no. as the backup folder), status_bericht.txt
    MANIFEST.txt                  list of the files that matter for the menu with size and checksum
    SD-ANLEITUNG.md, TEST-ABLAUF.md, DEPLOY_TELNET.md, deploy/, java_patch/ …   guides and sources (the telnet route stays valid)
```

## 2. Build it cleanly (anew) – on the Mac

```
bash deploy/build_sd_package.sh "/Volumes/<card>"            # dry run: only shows what would happen
bash deploy/build_sd_package.sh --apply "/Volumes/<card>"    # really writes
```
The script first runs all tests (mock car in sh/bash/ksh/dash + ESD patch against the real menu files), checks every file by checksum and only **appends** a block to the toolbox menu files (blocks of other projects stay byte for byte the same). At the end it cleans up macOS junk only in our folder and **ejects the card automatically** (`--no-eject` leaves it mounted). If ejecting does not work, a message says so – then eject in the Finder.

## 3. Start (always the same)

1. Card in **SD slot 1**, ignition on, MMI booted (after a cold start the GEM can take up to 5 minutes).
2. On the **home screen (HOME)** your known key combination for the Green Engineering Menu (A4 8W – the combination from the M.I.B. wiki “hidden menus – key combinations”).
3. In the GEM: **==>>M.I.B.<<==** → **ambientfx**.
4. Tap an entry, never abort the run (neither “Back” nor pulling the card) – until “--- fertig ---” (“--- done ---”) appears.

## 4. The menu entries

| No. | Entry | What happens |
|---|---|---|
| 1 | **Install / Update** | Nothing installed → install everything. Installed, SD state different → update (display **old → new**, per file). Everything the same → “schon aktuell, nichts zu tun” (“already up to date, nothing to do”; no reboot). Always the jar **and** all 5 plugins; the old test effects `momentumgds`/`momentumgdsselect` are removed (after a backup). |
| 2 | **Uninstall** | Removes only AmbientFX: jar, all `.ambiente` plugins (and the empty folder `ambientfx_effects`), the remembered effect (`/mnt/persist/ambientfx`) – backup to the SD first, then a search for leftovers and proof that all other jars are unchanged. |
| 3 | **Status** | Read only. Shows SD and car: jar state, each plugin (same/different/missing), old test effects, foreign plugins, remembered effect, debug marker (set or not). Writes `log/status_bericht.txt`. Recommended **first**. |

Every entry that changes something does: **pre-check without changes** (files on the SD present, jar checksum against the `.cksum`, each plugin against `plugins.cksum` – otherwise abort) → **backup to the SD** (`backup/<no>_<entry>_<jar no>/`; SD not writable = nothing is changed) → **copy via temporary file + checksum** → **read back from the car (“PRUEFUNG”, i.e. “CHECK”)** and proof that other files in the jar folder are unchanged → on **“ERGEBNIS: OK” (“RESULT: OK”) the MMI reboots by itself after 15 seconds** (no reboot on abort, notes, leftovers or “nothing to do”). To switch this off: an empty file `NO_AUTO_REBOOT` in `mib2q-ambient-fx`. The GEM cannot hide entries – so the scripts check the “only if” conditions themselves.

Special points:
* **Downgrade protection:** if the SD has an *older* jar state than the car (according to `known_versions.txt`), “Install” aborts without changing anything. Intended? Create an empty file `ALLOW_DOWNGRADE` in `mib2q-ambient-fx`.
* **Foreign plugins** in the folder `ambientfx_effects` (not from this SD) are never touched, only reported; “Uninstall” backs up and removes all `.ambiente` files (without the jar they are useless).
* **Log files of other mods** in the jar folder (e.g. `somemod.log`) grow during operation: they do not count for the “other jars unchanged” comparison and are never touched, deleted or counted as leftovers.
* **On the card only `mib2q-ambient-fx/` is written** (backup/, log/); foreign folders (toolbox, other mods …) and foreign menu blocks stay untouched.
* **Debug:** a log in RAM only is available via the marker file `/mnt/persist/ambientfx/debug` (switch on and read via telnet, see [docs/DEBUG.md](docs/DEBUG.md)). The menu has no entry of its own for it: **Status** shows whether the marker is set, and **Uninstall** also deletes the marker and the RAM log.
* **Full backup** (`mib2q-ambient-fx/backup/original/`: all jars + `lsd.sh`, with byte comparison): “Install” creates it once if it is missing. It is cleanest when AmbientFX is **not** installed at that time (e.g. after “Uninstall”); otherwise it also contains the jar installed back then.

## 5. Backup and way back

Before every change a copy is in `backup/<no>_<entry>_<jar no>/` on the SD (old jar, all plugins, remembered effect, plus `STAND.txt` with checksums). **Why a number instead of a time:** the car’s clock shows 01.01.1970 in the GEM and starts over after every reboot – timestamps would be neither unique nor sortable. `no` counts up from 001 (highest existing + 1), `jar no` is the checksum of the jar being backed up (`ohne-jar` = nothing installed); same `jar no` = same jar state, and the menu points that out when backing up (“dieselbe Jar … schon gesichert in …”, “same jar … already backed up in …”). The matching log is `log/<no>_<entry>.txt`. Older folders with a date in the name stay untouched. Restoring = “Install” with the jar/plugins from the SD (a different state: put the files from `backup/…` into `mib2q-ambient-fx` and keep `plugins.cksum`/`.cksum` matching – or simply copy via telnet, see 6). Stock state = menu entry 2 (Uninstall). The original menu files of the toolbox are in `mib2q-esd-original/`.

## 6. Telnet (fallback if the menu does not appear) – stays valid

```
telnet 172.16.250.248 123          (login root, your own password)
mount -uw /net/mmx/mnt/app
mount -uw /net/mmx/mnt/system      (the scripts write nothing there – but it belongs to your routine)
mount -uw /net/mmx/fs/sda0
sh /net/mmx/fs/sda0/mib2q-ambient-fx/esd/afx_status.sh          (every menu entry also works this way: afx_install.sh, afx_uninstall.sh)
```
Pure manual work without the scripts is in `mib2q-ambient-fx/DEPLOY_TELNET.md` (jar + all plugins, checksums, rollback). Once the menu runs you do not need telnet any more.

---

<a id="deutsch"></a>

# mib2q-ambient-fx – SD-Menü (AmbientFX, Stand 2026-10-04)

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr.

Matze89x in cooperation with DEB and LuKa_dev. Namensnennung Pflicht, **keine kommerzielle Nutzung/Verkauf** (siehe LICENSE.md im Projekt).

Mit dieser SD-Karte installierst, aktualisierst und entfernst du AmbientFX (die Ambientebeleuchtungs-Effekte) **ohne Telnet**: im Grünen Engineering-Menü (GEM) unter **M.I.B. → ambientfx**. Zum Projekt gehören immer **die Jar und alle fünf Effekt-Plugins zusammen** (Atmosphere, Drive, Momentum, Momentum select, Scroll) – das Menü behandelt sie auch so.

**Voraussetzung:** Auf der Karte muss schon die **M.I.B.-Toolbox** liegen („Pre-V3.7.1 – MHIG Edition“, siehe [Release-Seite](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/releases/tag/V3.7.1) und ihr [Wiki/Dokumentation](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/wiki)), und dein Gerät muss sie **nach der eigenen Anleitung der Toolbox** ausführen können. Unser Paket fügt nur **eine weitere Zeile `ambientfx`** unter M.I.B. im Green Engineering Menu hinzu; alles Übrige der Toolbox bleibt unverändert.

## 1. Was auf der Karte liegt

**Ein einziger Ordner `mib2q-ambient-fx/`** – darin liegt alles, und nur dort schreibt das Menü (Sicherungen, Protokolle). Außerhalb davon ändert sich auf der Karte nur an den zwei Menü-Dateien der Toolbox etwas (angehängter Block `ambientfx`).

```
<SD-Wurzel>                       M.I.B.-Toolbox "Pre-V3.7.1 – MHIG Edition" (unverändert, bis auf die zwei Menü-Dateien unten)
  mib2q-esd-original/             die Original-Menü-Dateien der Toolbox (gemeinsam genutzt, nie überschrieben)
  esd/Launcher-sda0.esd           Menü-Datei der Toolbox + angehängte Blöcke anderer Mods (…, hier zusätzlich "ambientfx" – ganz unten)
  esd/Launcher-MHIG-sda0.esd      dito (für MHIG-Geräte)
  mib2q-ambient-fx/               UNSER Ordner (der Projektordner, wie bisher via deploy/copy_to_sd.sh)
    esd/afx_*.sh                  die 3 Menü-Skripte + afx_lib.sh, known_versions.txt, VERSION
    build/ambient_fx.jar (+ .cksum)   die Jar
    build/plugins/*.ambiente      die 5 Effekt-Plugins, build/plugins.cksum = ihre Prüfsummen
    backup/<Nr>_<Punkt>_<Jar-Nr>/ Sicherungen: vor JEDER Änderung im Auto kommt eine Kopie hierher (Nr = laufende Nummer, Jar-Nr = Prüfsumme der gesicherten Jar)
    backup/original/              einmalige Komplett-Sicherung der Auto-Jars (+ lsd.sh); legt das Menü beim ersten Installieren selbst an
    log/                          Protokolle: install.txt / uninstall.txt / status.txt = letzter Lauf, <Nr>_install.txt usw. = jeder Lauf mit Sicherung (gleiche Nr. wie der Sicherungsordner), status_bericht.txt
    MANIFEST.txt                  Liste der für das Menü wichtigen Dateien mit Größe und Prüfsumme
    SD-ANLEITUNG.md, TEST-ABLAUF.md, DEPLOY_TELNET.md, deploy/, java_patch/ …   Anleitungen und Quellen (Telnet-Weg bleibt gültig)
```

## 2. Sauber (neu) aufbauen – auf dem Mac

```
bash deploy/build_sd_package.sh "/Volumes/<Karte>"            # Trockenlauf: zeigt nur, was geschehen würde
bash deploy/build_sd_package.sh --apply "/Volumes/<Karte>"    # schreibt wirklich
```
Das Skript lässt zuerst alle Tests laufen (Schein-Auto in sh/bash/ksh/dash + ESD-Patch gegen die echten Menü-Dateien), prüft jede Datei per Prüfsumme und ändert an den Toolbox-Menüdateien nur das **Anhängen** eines Blocks (Blöcke anderer Projekte bleiben Byte für Byte gleich). Am Ende räumt es den macOS-Müll nur in unserem Ordner auf und **wirft die Karte automatisch aus** (`--no-eject` lässt sie gemountet). Klappt das Auswerfen nicht, steht eine Meldung da – dann im Finder auswerfen.

## 3. Starten (immer gleich)

1. Karte in **SD-Slot 1**, Zündung an, MMI hochgefahren (nach einem Kaltstart kann das GEM bis zu 5 Minuten brauchen).
2. Auf der **Startseite (HOME)** deine bekannte Tastenkombination für das Grüne Engineering-Menü (A4 8W – die Kombination aus dem M.I.B.-Wiki „hidden menus – key combinations“).
3. Im GEM: **==>>M.I.B.<<==** → **ambientfx**.
4. Einen Punkt antippen, den Lauf nie abbrechen (weder „Zurück“ noch Karte ziehen) – bis „--- fertig ---“ dasteht.

## 4. Die Menüpunkte

| Nr. | Punkt | Was passiert |
|---|---|---|
| 1 | **Installieren / Updaten** | Nichts installiert → alles installieren. Installiert, SD-Stand anders → aktualisieren (Anzeige **alt → neu**, je Datei). Alles gleich → „schon aktuell, nichts zu tun“ (kein Neustart). Immer Jar **und** alle 5 Plugins; die alten Testeffekte `momentumgds`/`momentumgdsselect` werden (nach Sicherung) entfernt. |
| 2 | **Deinstallieren** | Entfernt nur AmbientFX: Jar, alle `.ambiente`-Plugins (und den leeren Ordner `ambientfx_effects`), den gemerkten Effekt (`/mnt/persist/ambientfx`) – vorher Sicherung auf die SD, danach Rest-Suche und Nachweis, dass alle anderen Jars unverändert sind. |
| 3 | **Status** | Nur lesen. Zeigt SD und Auto: Jar-Stand, jedes Plugin (gleich/abweichend/fehlt), alte Testeffekte, fremde Plugins, gemerkter Effekt, Debug-Marker (gesetzt oder nicht). Schreibt `log/status_bericht.txt`. Empfohlen **zuerst**. |

Jeder Änderungs-Punkt macht: **Vorprüfung ohne Änderung** (Dateien auf der SD da, Jar-Prüfsumme gegen die `.cksum`, jedes Plugin gegen `plugins.cksum` – sonst Abbruch) → **Sicherung auf die SD** (`backup/<Nr>_<Punkt>_<Jar-Nr>/`; SD nicht beschreibbar = nichts wird geändert) → **Kopie über Zwischendatei + Prüfsumme** → **Rücklesen aus dem Auto („PRUEFUNG“)** und Nachweis, dass andere Dateien im Jar-Ordner unverändert sind → bei **„ERGEBNIS: OK“ startet das MMI nach 15 Sekunden von selbst neu** (bei Abbruch, Hinweisen, Resten oder „nichts zu tun“ kein Neustart). Abschalten: leere Datei `NO_AUTO_REBOOT` in `mib2q-ambient-fx`. Das GEM kann Einträge nicht ausblenden – die „nur wenn“-Bedingungen prüfen deshalb die Skripte selbst.

Besonderheiten:
* **Downgrade-Schutz:** hat die SD einen *älteren* Jar-Stand als das Auto (laut `known_versions.txt`), bricht „Installieren“ ab, ohne etwas zu ändern. Absicht? Leere Datei `ALLOW_DOWNGRADE` in `mib2q-ambient-fx` anlegen.
* **Fremde Plugins** im Ordner `ambientfx_effects` (nicht von dieser SD) werden nie angefasst, nur gemeldet; „Deinstallieren“ sichert und entfernt alle `.ambiente`-Dateien (ohne die Jar sind sie wirkungslos).
* **Log-Dateien anderer Mods** im Jar-Ordner (z. B. `irgendeinmod.log`) wachsen im Betrieb: sie zählen nicht zum Vergleich „andere Jars unverändert“ und werden nie angefasst, gelöscht oder als Rest gewertet.
* **Auf der Karte wird nur in `mib2q-ambient-fx/` geschrieben** (backup/, log/); fremde Ordner (Toolbox, andere Mods …) und fremde Menü-Blöcke bleiben unberührt.
* **Debug:** Ein Protokoll nur im RAM gibt es per Marker-Datei `/mnt/persist/ambientfx/debug` (Einschalten und Auslesen per Telnet, siehe [docs/DEBUG.md](docs/DEBUG.md)). Im Menü gibt es dazu keinen eigenen Punkt: **Status** zeigt, ob der Marker gesetzt ist, und **Deinstallieren** löscht Marker und RAM-Protokoll mit.
* **Komplett-Sicherung** (`mib2q-ambient-fx/backup/original/`: alle Jars + `lsd.sh`, mit Byte-Vergleich): legt „Installieren“ einmalig an, falls sie fehlt. Am saubersten ist sie, wenn AmbientFX zu dem Zeitpunkt **nicht** installiert ist (z. B. nach „Deinstallieren“); sonst enthält sie die damals installierte Jar mit.

## 5. Sicherung und Rückweg

Vor jeder Änderung liegt eine Kopie in `backup/<Nr>_<Punkt>_<Jar-Nr>/` auf der SD (alte Jar, alle Plugins, gemerkter Effekt, dazu `STAND.txt` mit Prüfsummen). **Warum Nummer statt Uhrzeit:** die Auto-Uhr steht im GEM auf 01.01.1970 und beginnt nach jedem Neustart von vorn – Zeitstempel wären nicht eindeutig und nicht sortierbar. `Nr` zählt von 001 aufwärts (höchste vorhandene + 1), `Jar-Nr` ist die Prüfsumme der Jar, die gesichert wird (`ohne-jar` = nichts installiert); gleiche `Jar-Nr` = gleicher Jar-Stand, das Menü weist beim Sichern darauf hin („dieselbe Jar … schon gesichert in …“). Das zugehörige Protokoll liegt als `log/<Nr>_<Punkt>.txt`. Ältere Ordner mit Datum im Namen bleiben unverändert liegen. Zurückspielen = „Installieren“ mit der Jar/den Plugins von der SD (anderer Stand: Dateien aus `backup/…` in `mib2q-ambient-fx` legen und `plugins.cksum`/`.cksum` passend halten – oder einfach per Telnet kopieren, siehe 6). Stock-Zustand = Menüpunkt 2 (Deinstallieren). Die Original-Menüdateien der Toolbox liegen in `mib2q-esd-original/`.

## 6. Telnet (Ersatzweg, falls das Menü nicht erscheint) – bleibt gültig

```
telnet 172.16.250.248 123          (Login root, dein eigenes Passwort)
mount -uw /net/mmx/mnt/app
mount -uw /net/mmx/mnt/system      (die Skripte schreiben dort nichts – gehört aber zu deiner Routine)
mount -uw /net/mmx/fs/sda0
sh /net/mmx/fs/sda0/mib2q-ambient-fx/esd/afx_status.sh          (jeder Menü-Punkt geht auch so: afx_install.sh, afx_uninstall.sh)
```
Reine Handarbeit ohne die Skripte steht in `mib2q-ambient-fx/DEPLOY_TELNET.md` (Jar + alle Plugins, Prüfsummen, Rollback). Läuft das Menü erst einmal, brauchst du Telnet nicht mehr.
