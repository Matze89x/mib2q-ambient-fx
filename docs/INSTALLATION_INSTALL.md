<a id="english"></a>

# Install, uninstall, recover

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **For testing on private property only, not in public road traffic** – see [RECHT_LEGAL.md](RECHT_LEGAL.md). The intervention modifies the HMI software of your head unit. **Use at your own risk.** Make a backup before the first time and make sure you can reset the unit if necessary.

## What gets installed

Exactly these files, **always together**:

| File | Target on the unit |
|---|---|
| `ambient_fx.jar` | `/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar` |
| all `*.ambiente` from `build/plugins/` | `/mnt/app/eso/hmi/lsd/ambientfx_effects/` |

Everything else on the unit stays unchanged. The original HMI (`lsd.jxe`) and the start script (`lsd.sh`) are **not** touched: `lsd.sh` loads every jar from the `jars/` folder in front of the original HMI by itself.

> **Why always the jar and all plugins?** The jar and every plugin contain the same effect class. The plugin loader asks the jar first (parent-first). A newer plugin next to an older jar therefore never takes effect, and vice versa.

## Way 1: SD menu in the Green Engineering Menu (recommended)

Requirement: an **M.I.B. toolbox SD card** (More Incredible Bash) that is **prepared first**: the toolbox (version “Pre-V3.7.1 – MHIG Edition”, see the [release page](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/releases/tag/V3.7.1) and its [wiki/documentation](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/wiki)) has to be on the card, and your unit has to be able to run it **exactly as the toolbox’s own instructions describe**, so that the Green Engineering Menu is reachable. Setting that up is not part of this project. Our script then **adds one more line, `ambientfx`**, to the toolbox menu (GEM → M.I.B. → ambientfx); the installation itself is done through that line, everything else in the toolbox stays unchanged.

**On the computer** (card inserted):

```bash
./build_java.sh && ./verify.sh && tools/build_all_plugins.sh
bash deploy/build_sd_package.sh /Volumes/YOUR-CARD            # dry run: shows what would be written
bash deploy/build_sd_package.sh --apply /Volumes/YOUR-CARD    # writes to the card
```

The script runs all tests first (mock car in four shells, ESD patch), writes **only** to the folder `mib2q-ambient-fx/` on the card and appends its own block to the two toolbox menu files (all other menu entries stay byte-for-byte unchanged; the originals are kept in `mib2q-esd-original/`). macOS junk (`._*`, `.DS_Store`) is removed in its own folder. With `--no-eject` the card stays mounted afterwards.

**In the vehicle** (card in slot 1): Green Engineering Menu → **M.I.B. → ambientfx**:

| Entry | What happens |
|---|---|
| **3 Status** | Read only. Shows SD and vehicle state (jar, each plugin same/different/missing). |
| **1 Install / Update** | Backs up everything affected to the SD card first, copies jar + plugins via a temporary file and checksum, reads everything back from the vehicle (“PRUEFUNG”) and reboots the MMI after 15 seconds on “OK”. |
| **2 Uninstall** | Backs up everything, removes the jar, **all** `.ambiente` plugins including the folder `ambientfx_effects`, the remembered effect, the debug marker including the RAM log and old debug leftovers. Afterwards everything is as before the installation. |

Recommended order: **3 Status → 1 Install/Update → 3 Status**. More details: [SD-ANLEITUNG.md](../SD-ANLEITUNG.md) and [TEST-ABLAUF.md](../TEST-ABLAUF.md).

**Guarantees of the menu** (covered by tests): **only** `ambient_fx.jar` and the files listed in `build/plugins.cksum` that end in `.ambiente` are copied – never macOS junk, never anything unknown. Other jars on the vehicle are never touched, and the menu proves it with checksums before and after the run. If the card is write-protected (LOCK switch) the menu aborts **before** changing anything.

**Backups and logs** are on the card in `mib2q-ambient-fx/`:

- `backup/original/` – one-time full backup of all jars (and `lsd.sh`) on first install
- `backup/<no>_<entry>_<jar checksum>/` – backup before every change (running number instead of a timestamp, because the clock in the Green Engineering Menu shows 1970), with `STAND.txt`
- `log/` – logs of the runs (`install.txt` = last run, `<no>_install.txt` = every run with a backup)

## Way 2: Telnet

If you have set up telnet access to the unit, [DEPLOY_TELNET.md](../DEPLOY_TELNET.md) has the step-by-step guide (backup, `mount -uw` for **both** partitions and the SD card, copy, checksums, rollback). Credentials are **not** part of this repository.

## Choose what gets installed

You are **free** in what ends up in the vehicle. The menu entries come from the `.ambiente` files that are listed in `build/plugins/` when you build the SD package:

- **Add your own designs:** export them from the [Design Studio](../designer/README.md), put the `.ambiente` files into `build/plugins/` and run `deploy/build_sd_package.sh` again.
- **Remove bundled effects:** delete the bundled `.ambiente` files you do not want from `build/plugins/` (at least one plugin has to stay) and build again. You can keep some of the five, or use **only your own designs**. The jar is always needed.
- **Take a bundled effect out of the vehicle:** the entry **1 Install / Update** only copies what is in the list and **never deletes** plugins that are already in the vehicle (it reports them as foreign plugin files). So first run **2 Uninstall**, then **1 Install / Update** with the shortened list – or delete the file via telnet (see [DEPLOY_TELNET.md](../DEPLOY_TELNET.md)).
- The menu texts still say “5 effects”; that is only cosmetic.

## Recover

1. **Menu:** entry **2 Uninstall**. Afterwards the ambient lighting is back to stock.
2. **By hand:** delete `ambient_fx.jar` from `/mnt/app/eso/hmi/lsd/jars/` and the folder `/mnt/app/eso/hmi/lsd/ambientfx_effects/` (first `mount -uw /net/mmx/mnt/app`), then reboot.
3. **Back to an older version:** copy the files from `backup/<no>_…/` or `backup/original/` back. The menu prevents accidentally going back to an **older** jar (an empty file `ALLOW_DOWNGRADE` in the folder `mib2q-ambient-fx/` lifts that).

After an uninstall the vehicle may still be on the “individual” profile with the last effect colour. Just pick a profile in the menu.

## If something does not work

- **Menu entry missing:** re-insert the card; `bash deploy/build_sd_package.sh` without `--apply` shows whether the block is in the menu files.
- **Menu aborts:** the last line gives the reason (“nothing was changed” or where the backup is). The log is in `log/`.
- **Effect entries missing after reboot:** run 3 Status. Often a plugin is missing or the jar is older than the plugins.
- **MMI does not start normally:** uninstalling via the menu is then not possible. Remove `ambient_fx.jar` via telnet or restore the original backup `backup/original/`.

---

<a id="deutsch"></a>

# Installieren, Deinstallieren, Wiederherstellen

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** – siehe [RECHT_LEGAL.md](RECHT_LEGAL.md). Der Eingriff verändert die HMI-Software deines Steuergeräts. **Nutzung auf eigene Gefahr.** Mach vor dem ersten Mal ein Backup und stell sicher, dass du das Gerät notfalls wieder zurücksetzen kannst.

## Was installiert wird

Genau diese Dateien, **immer zusammen**:

| Datei | Ziel im Gerät |
|---|---|
| `ambient_fx.jar` | `/mnt/app/eso/hmi/lsd/jars/ambient_fx.jar` |
| alle `*.ambiente` aus `build/plugins/` | `/mnt/app/eso/hmi/lsd/ambientfx_effects/` |

Alles andere im Gerät bleibt unverändert. Die Original-HMI (`lsd.jxe`) und das Startskript (`lsd.sh`) werden **nicht** angefasst: `lsd.sh` lädt von sich aus jede Jar aus dem Ordner `jars/` vor der Original-HMI.

> **Warum immer Jar und alle Plugins?** Die Jar und jedes Plugin enthalten dieselbe Effekt-Klasse. Der Plugin-Lader fragt zuerst die Jar (Parent-first). Ein neueres Plugin neben einer älteren Jar wird daher nie wirksam und umgekehrt.

## Weg 1: SD-Menü im Green Engineering Menu (empfohlen)

Voraussetzung: eine **M.I.B.-Toolbox-SD-Karte** (More Incredible Bash), die **vorher eingerichtet** ist: Die Toolbox (Version „Pre-V3.7.1 – MHIG Edition“, siehe [Release-Seite](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/releases/tag/V3.7.1) und ihr [Wiki/Dokumentation](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash/wiki)) muss auf der Karte liegen, und dein Gerät muss sie **genau nach der eigenen Anleitung der Toolbox** ausführen können, sodass das Green Engineering Menu erreichbar ist. Das Einrichten ist nicht Teil dieses Projekts. Unser Skript **fügt dann eine weitere Zeile `ambientfx`** zum Menü der Toolbox hinzu (GEM → M.I.B. → ambientfx); die Installation selbst läuft über diese Zeile, alles Übrige der Toolbox bleibt unverändert.

**Am Computer** (Karte eingesteckt):

```bash
./build_java.sh && ./verify.sh && tools/build_all_plugins.sh
bash deploy/build_sd_package.sh /Volumes/DEINE-KARTE            # Trockenlauf: zeigt, was geschrieben würde
bash deploy/build_sd_package.sh --apply /Volumes/DEINE-KARTE    # schreibt auf die Karte
```

Das Skript lässt vorher alle Tests laufen (Schein-Auto in vier Shells, ESD-Patch), schreibt **nur** in den Ordner `mib2q-ambient-fx/` auf der Karte und hängt einen eigenen Block an die beiden Menü-Dateien der Toolbox an (alle anderen Menü-Einträge bleiben Byte für Byte unverändert, die Originale werden in `mib2q-esd-original/` aufgehoben). macOS-Müll (`._*`, `.DS_Store`) wird im eigenen Ordner entfernt. Mit `--no-eject` bleibt die Karte danach eingehängt.

**Im Fahrzeug** (Karte in Slot 1): Green Engineering Menu → **M.I.B. → ambientfx**:

| Punkt | Was passiert |
|---|---|
| **3 Status** | Nur lesen. Zeigt SD- und Fahrzeug-Stand (Jar, jedes Plugin gleich/abweichend/fehlt). |
| **1 Installieren / Updaten** | Sichert vorher alles Betroffene auf die SD, kopiert Jar + Plugins über Zwischendatei und Prüfsumme, liest alles aus dem Fahrzeug zurück („PRUEFUNG“) und startet bei „OK“ das MMI nach 15 Sekunden neu. |
| **2 Deinstallieren** | Sichert alles, entfernt Jar, **alle** `.ambiente`-Plugins samt Ordner `ambientfx_effects`, den gemerkten Effekt, den Debug-Marker samt RAM-Protokoll und alte Debug-Reste. Danach ist alles wie vor der Installation. |

Empfohlener Ablauf: **3 Status → 1 Installieren/Updaten → 3 Status**. Mehr Details: [SD-ANLEITUNG.md](../SD-ANLEITUNG.md) und [TEST-ABLAUF.md](../TEST-ABLAUF.md).

**Zusicherungen des Menüs** (durch Tests abgesichert): Kopiert werden **nur** `ambient_fx.jar` und die Dateien, die in `build/plugins.cksum` stehen und auf `.ambiente` enden – nie macOS-Müll, nie etwas Unbekanntes. Andere Jars im Fahrzeug werden nie angefasst, und das Menü beweist das mit Prüfsummen vor und nach dem Lauf. Ist die Karte schreibgeschützt (LOCK-Schalter), bricht das Menü ab, **bevor** es etwas verändert.

**Sicherungen und Protokolle** liegen auf der Karte im Ordner `mib2q-ambient-fx/`:

- `backup/original/` – einmalige Komplett-Sicherung aller Jars (und `lsd.sh`) beim ersten Installieren
- `backup/<Nr>_<Punkt>_<Jar-Prüfsumme>/` – Sicherung vor jeder Änderung (laufende Nummer statt Uhrzeit, weil die Uhr im Green Engineering Menu auf 1970 steht), mit `STAND.txt`
- `log/` – Protokolle der Läufe (`install.txt` = letzter Lauf, `<Nr>_install.txt` = jeder Lauf mit Sicherung)

## Weg 2: Telnet

Wer einen Telnet-Zugang zum Gerät eingerichtet hat, findet in [DEPLOY_TELNET.md](../DEPLOY_TELNET.md) die Schritt-für-Schritt-Anleitung (Backup, `mount -uw` für **beide** Partitionen und die SD, Kopieren, Prüfsummen, Rollback). Die Zugangsdaten sind **nicht** Teil dieses Repositorys.

## Auswählen, was installiert wird

Du bist **frei**, was im Fahrzeug landet. Die Menü-Einträge stammen aus den `.ambiente`-Dateien, die beim Bauen des SD-Pakets in `build/plugins/` liegen:

- **Eigene Designs dazulegen:** Exportiere sie aus dem [Design Studio](../designer/README.md), lege die `.ambiente`-Dateien nach `build/plugins/` und führe `deploy/build_sd_package.sh` erneut aus.
- **Mitgelieferte Effekte entfernen:** Lösche die mitgelieferten `.ambiente`-Dateien, die du nicht willst, aus `build/plugins/` (mindestens ein Plugin muss bleiben) und baue neu. Du kannst einige der fünf behalten oder **nur eigene Designs** verwenden. Die Jar wird immer gebraucht.
- **Einen mitgelieferten Effekt aus dem Fahrzeug nehmen:** Der Punkt **1 Installieren / Updaten** kopiert nur, was in der Liste steht, und **löscht nie** Plugins, die schon im Fahrzeug liegen (er meldet sie als fremde Plugin-Dateien). Mach deshalb erst **2 Deinstallieren** und dann **1 Installieren / Updaten** mit der gekürzten Liste – oder lösche die Datei per Telnet (siehe [DEPLOY_TELNET.md](../DEPLOY_TELNET.md)).
- Die Menütexte sprechen weiter von „5 Effekten“; das ist nur Kosmetik.

## Wiederherstellen

1. **Menü:** Punkt **2 Deinstallieren**. Danach ist die Ambientebeleuchtung wieder im Originalzustand.
2. **Von Hand:** `ambient_fx.jar` aus `/mnt/app/eso/hmi/lsd/jars/` und den Ordner `/mnt/app/eso/hmi/lsd/ambientfx_effects/` löschen (zuvor `mount -uw /net/mmx/mnt/app`), danach neu starten.
3. **Zurück auf einen älteren Stand:** Die Dateien aus `backup/<Nr>_…/` oder `backup/original/` zurückkopieren. Das Menü verhindert ein versehentliches Zurücksetzen auf eine **ältere** Jar (Datei `ALLOW_DOWNGRADE` im Ordner `mib2q-ambient-fx/` hebt das auf).

Nach einer Deinstallation kann das Fahrzeug noch auf dem Profil „individuell“ mit der letzten Effektfarbe stehen. Wähle im Menü einfach ein Profil.

## Wenn etwas nicht geht

- **Menü-Eintrag fehlt:** Karte neu einstecken, `bash deploy/build_sd_package.sh` ohne `--apply` zeigt, ob der Block in den Menü-Dateien steht.
- **Menü bricht ab:** Die letzte Zeile nennt den Grund („Es wurde nichts verändert“ oder wo die Sicherung liegt). Das Protokoll liegt in `log/`.
- **Effekt-Einträge fehlen nach dem Neustart:** Punkt 3 Status zeigen. Häufig fehlt ein Plugin oder die Jar ist älter als die Plugins.
- **MMI startet nicht normal:** Deinstallieren ist per Menü nicht mehr möglich. Dann per Telnet `ambient_fx.jar` entfernen oder die Original-Sicherung `backup/original/` zurückspielen.
