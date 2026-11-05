# Testablauf im Auto – Status, dann alles entfernen, dann sauber neu installieren (Stand 2026-10-04)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](docs/RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr. Dieses Dokument ist auf Deutsch, der englische Überblick steht in der [README](README.md).

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
