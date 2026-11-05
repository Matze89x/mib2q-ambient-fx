# mib2q-ambient-fx – SD-Menü (AmbientFX, Stand 2026-10-04)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr. Dieses Dokument ist auf Deutsch, der englische Überblick steht in der [README](README.md).

Matze89x in cooperation with DEB and LuKa_dev. Namensnennung Pflicht, **keine kommerzielle Nutzung/Verkauf** (siehe LICENSE.md im Projekt).

Mit dieser SD-Karte installierst, aktualisierst und entfernst du AmbientFX (die Ambientebeleuchtungs-Effekte) **ohne Telnet**: im Grünen Engineering-Menü (GEM) unter **M.I.B. → ambientfx**. Zum Projekt gehören immer **die Jar und alle fünf Effekt-Plugins zusammen** (Atmosphere, Drive, Momentum, Momentum select, Scroll) – das Menü behandelt sie auch so.

## 1. Was auf der Karte liegt

**Ein einziger Ordner `mib2q-ambient-fx/`** – darin liegt alles, und nur dort schreibt das Menü (Sicherungen, Protokolle). Außerhalb davon ändert sich auf der Karte nur an den zwei Menü-Dateien der Toolbox etwas (angehängter Block `ambientfx`).

```
<SD-Wurzel>                       M.I.B.-Toolbox "Pre-V3.7.1 – MHIG Edition" (unverändert, bis auf die zwei Menü-Dateien unten)
  mib2q-esd-original/             die Original-Menü-Dateien der Toolbox (gemeinsam für alle Mods, nie überschrieben)
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
