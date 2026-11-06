#!/bin/bash
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# test_scripts.sh [Shell] - testet die SD-Menue-Skripte (afx_*.sh) auf dem Mac gegen ein Schein-Auto und eine Schein-SD
# (alles in einem Temp-Ordner, nichts Echtes wird angefasst).
# Aufruf:  bash test_scripts.sh            (testet mit sh, bash, ksh, dash - soweit vorhanden)      bash test_scripts.sh ksh   (nur eine Shell)
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../../esd"
FAILS=0; PASS=0
ok()   { PASS=$((PASS + 1)); }
bad()  { FAILS=$((FAILS + 1)); echo "  FEHLER: $*"; }
check(){ if eval "$1"; then ok; else bad "$2   [$1]"; fi; }

run_all() {
    SH="$1"
    echo "== Shell: $SH"
    T="$(mktemp -d)"
    SD="$T/sd"; AFD="$SD/mib2q-ambient-fx"; LSD="$T/car/mnt/app/eso/hmi/lsd"; CARJ="$LSD/jars"; EFX="$LSD/ambientfx_effects"
    PERS="$T/car/mnt/persist/ambientfx"; HK="$T/car/mnt/app/root/hooks"; TMPD="$T/tmp"
    mkdir -p "$AFD/esd" "$AFD/build/plugins" "$CARJ" "$PERS" "$HK" "$TMPD"
    cp "$SRC"/afx_*.sh "$AFD/esd/"
    echo "mib2q-ambient-fx SD-Menue (Test)" > "$AFD/esd/VERSION"
    ck() { set -- $(cksum < "$1"); echo "$1 $2"; }
    head -c 6000 /dev/urandom > "$T/jar_new"; head -c 5000 /dev/urandom > "$T/jar_old"; head -c 4000 /dev/urandom > "$T/jar_unknown"
    PLUGS="atmosphere drive momentum momentumselect scroll"
    for p in $PLUGS; do head -c 2000 /dev/urandom > "$T/p_$p"; cp "$T/p_$p" "$AFD/build/plugins/$p.ambiente"; done
    mk_sd() { # SD-Seite (Jar + Plugins + Listen) aus $T/jar_new bzw. dem uebergebenen Jar neu schreiben
        cp "${1:-$T/jar_new}" "$AFD/build/ambient_fx.jar"; cksum < "${1:-$T/jar_new}" > "$AFD/build/ambient_fx.jar.cksum"
        { echo "# test"; for p in $PLUGS; do echo "$(ck "$AFD/build/plugins/$p.ambiente") $p.ambiente"; done; } > "$AFD/build/plugins.cksum"
    }
    mk_sd
    { echo "# Rang Name Pruefsumme Groesse"; echo "1 alt $(ck "$T/jar_old")"; echo "2 neu $(ck "$T/jar_new")"; } > "$AFD/esd/known_versions.txt"
    echo "other" > "$CARJ/mody.jar"; echo "pc" > "$CARJ/fremdmod.jar"; echo "carplay" > "$CARJ/carplay_hook.jar"
    echo "#!/bin/sh stock" > "$LSD/lsd.sh"
    snap() { find "$T/car" -type f | LC_ALL=C sort | while IFS= read -r f; do echo "$f $(cksum < "$f")"; done; }
    run() { # <Skript> -> setzt OUT, RC
        rm -f "$T/reboot_called"
        OUT="$(AFX_TEST="$T" PATH="${FAKEBIN:-/nonexistent}:$PATH" "$SH" "$AFD/esd/$1" 2>&1)"; RC=$?
    }
    carjar_is() { cmp -s "$1" "$CARJ/ambient_fx.jar"; }
    all_plugs_same() { for p in $PLUGS; do cmp -s "$T/p_$p" "$EFX/$p.ambiente" || return 1; done; }
    others_same() { [ "$(cat "$CARJ/mody.jar")" = other ] && [ "$(cat "$CARJ/fremdmod.jar")" = pc ] && [ "$(cat "$CARJ/carplay_hook.jar")" = carplay ]; }

    # --- Status, leeres Auto (und: Status veraendert NICHTS)
    S0="$(snap)"
    run afx_status.sh
    check '[ $RC = 0 ]' "status rc"; check 'echo "$OUT" | grep -q "Auto Jar    : nicht installiert"' "status: kein Jar"; check 'echo "$OUT" | grep -q "Ordner ambientfx_effects nicht vorhanden"' "status: kein Effekte-Ordner"
    check 'echo "$OUT" | grep -q "SD  Effekte: 5 Plugins, alle geprueft"' "status: SD-Plugins geprueft"
    check '[ -f "$AFD/log/status_bericht.txt" ]' "Bericht geschrieben"; check '[ "$(snap)" = "$S0" ]' "status veraendert nichts im Auto"; check '[ ! -f "$T/reboot_called" ]' "status: kein Neustart"
    check 'echo "$OUT" | grep -q "NICHT identisch"' "status: Auto und SD nicht identisch"
    check 'echo "$OUT" | grep -q "Debug-Marker: nicht gesetzt"' "status: Debug-Marker nicht gesetzt"
    mkdir -p "$PERS"; : > "$PERS/debug"; run afx_status.sh
    check 'echo "$OUT" | grep -q "Debug-Marker: GESETZT" && echo "$OUT" | grep -q "cat /dev/shmem/ambientfx.log"' "status: Debug-Marker gesetzt wird gezeigt (mit Telnet-Hinweis)"
    check '[ -f "$PERS/debug" ]' "status: laesst den Marker unberuehrt"
    rm -f "$PERS/debug"; rmdir "$PERS" 2>/dev/null; mkdir -p "$PERS"

    # --- Installieren: Vorpruefung bricht ab OHNE Aenderung
    echo "kaputt" > "$AFD/build/ambient_fx.jar.cksum"; run afx_install.sh
    check '[ $RC = 1 ] && echo "$OUT" | grep -q "defekt" && [ ! -f "$T/reboot_called" ]' "falsche Jar-Pruefsumme bricht ab, KEIN Neustart"; check '[ ! -f "$CARJ/ambient_fx.jar" ] && [ ! -d "$EFX" ]' "nichts installiert (Pruefsumme)"
    check '[ ! -d "$AFD/backup/original" ]' "auch keine Komplett-Sicherung angelegt (Vorpruefung)"
    mk_sd
    rm -f "$AFD/build/ambient_fx.jar.cksum"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "cksum fehlt"' "fehlende .cksum bricht ab"; mk_sd
    mv "$AFD/build/plugins/drive.ambiente" "$T/drive_weg"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "drive.ambiente fehlt" && [ ! -f "$CARJ/ambient_fx.jar" ]' "fehlendes Plugin auf der SD bricht ab"; mv "$T/drive_weg" "$AFD/build/plugins/drive.ambiente"
    echo x >> "$AFD/build/plugins/scroll.ambiente"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "scroll.ambiente ist defekt" && [ ! -d "$EFX" ]' "defektes Plugin auf der SD bricht ab"; cp "$T/p_scroll" "$AFD/build/plugins/scroll.ambiente"
    rm -f "$AFD/build/plugins.cksum"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "plugins.cksum fehlt"' "fehlende plugins.cksum bricht ab"; mk_sd
    check '[ "$(snap)" = "$S0" ]' "alle Abbrueche: Auto komplett unveraendert"

    # --- Installieren: frisch
    run afx_install.sh
    check '[ $RC = 0 ] && [ "$(cat "$T/reboot_called")" = "reboot 15" ]' "install ok, Neustart nach 15 s ausgeloest"
    check 'echo "$OUT" | grep -q "PRUEFUNG" && echo "$OUT" | grep -q "Andere Jars im Ordner: unveraendert" && echo "$OUT" | grep -q "genau 5 .ambiente-Dateien"' "install: Rueckleser-Pruefung + genau fuenf"
    check 'carjar_is "$T/jar_new"' "Jar im Auto identisch"; check 'all_plugs_same' "alle 5 Plugins im Auto identisch"; check 'others_same' "andere Jars unberuehrt"
    check '[ -f "$AFD/backup/original/jars/mody.jar" ] && [ -f "$AFD/backup/original/lsd.sh" ]' "einmalige Komplett-Sicherung angelegt (jars + lsd.sh)"
    check 'ls "$AFD/backup" | grep -q _install' "Sicherungsordner auf der SD angelegt"
    check 'ls "$CARJ" "$EFX" | grep -q afxnew; [ $? -ne 0 ]' "keine Zwischendatei uebrig"; check '[ "$(cat "$LSD/lsd.sh")" = "#!/bin/sh stock" ]' "lsd.sh unberuehrt"
    # zweiter Lauf: alles aktuell
    run afx_install.sh; check '[ $RC = 0 ] && echo "$OUT" | grep -q "schon aktuell" && [ ! -f "$T/reboot_called" ]' "zweites Installieren: schon aktuell, kein Neustart"
    run afx_status.sh; check 'echo "$OUT" | grep -q "Auto Jar    : neu" && echo "$OUT" | grep -q "= wie SD" && echo "$OUT" | grep -q "5 gleich wie SD" && echo "$OUT" | grep -q "alles auf dem Stand der SD"' "status nach install: alles gleich"

    # --- Updaten: alte Jar + ein altes Plugin im Auto
    cp "$T/jar_old" "$CARJ/ambient_fx.jar"; echo "altes plugin" > "$EFX/momentum.ambiente"; BEFORE_B=$(ls "$AFD/backup/original/jars" | wc -l)
    run afx_install.sh
    check '[ $RC = 0 ] && carjar_is "$T/jar_new" && all_plugs_same && [ -f "$T/reboot_called" ]' "update: Jar + Plugin ersetzt, Neustart"
    check 'echo "$OUT" | grep -q "AKTUALISIERT: alt -> neu"' "update: Anzeige alt -> neu (Jar)"; check 'echo "$OUT" | grep -q "momentum.ambiente AKTUALISIERT"' "update: nur das geaenderte Plugin wird genannt"
    check 'echo "$OUT" | grep -q "gleich, nichts zu tun"' "update: unveraenderte Dateien werden nicht angefasst"
    LASTB="$(ls -d "$AFD"/backup/*_install* | tail -1)"; check 'cmp -s "$T/jar_old" "$LASTB/ambient_fx.jar" && grep -q "altes plugin" "$LASTB/ambientfx_effects/momentum.ambiente"' "update: alte Jar + altes Plugin gesichert"
    check 'echo "$OUT" | grep -q "Komplett-Sicherung der Jars: schon vorhanden"' "update: Komplett-Sicherung wird nicht neu angelegt"; check '[ "$(ls "$AFD/backup/original/jars" | wc -l)" = "$BEFORE_B" ]' "Komplett-Sicherung unveraendert"

    # --- Alte Testeffekte (GDS) werden nach Sicherung entfernt
    echo gds1 > "$EFX/momentumgds.ambiente"; echo gds2 > "$EFX/momentumgdsselect.ambiente"
    run afx_install.sh
    check '[ $RC = 0 ] && [ ! -e "$EFX/momentumgds.ambiente" ] && [ ! -e "$EFX/momentumgdsselect.ambiente" ] && [ -f "$T/reboot_called" ]' "GDS-Testeffekte entfernt, Neustart"
    LASTB="$(ls -d "$AFD"/backup/*_install* | tail -1)"; check '[ "$(cat "$LASTB/ambientfx_effects/momentumgds.ambiente")" = gds1 ] && [ "$(cat "$LASTB/ambientfx_effects/momentumgdsselect.ambiente")" = gds2 ]' "GDS-Testeffekte vorher gesichert"
    check 'echo "$OUT" | grep -q "genau 5 .ambiente-Dateien"' "danach genau fuenf"

    # --- Fremdes Plugin im Effekte-Ordner: nicht angefasst, Hinweis
    echo "eigener effekt" > "$EFX/meinEffekt.ambiente"; echo "x" > "$EFX/momentum.ambiente"
    run afx_install.sh
    check '[ $RC = 0 ] && [ "$(cat "$EFX/meinEffekt.ambiente")" = "eigener effekt" ] && echo "$OUT" | grep -q "fremde Plugin-Dateien" && echo "$OUT" | grep -q "meinEffekt.ambiente"' "fremdes Plugin: nicht angefasst, Hinweis"
    rm -f "$EFX/meinEffekt.ambiente"

    # --- Unbekannte Jar im Auto wird ersetzt (mit Sicherung)
    cp "$T/jar_unknown" "$CARJ/ambient_fx.jar"; run afx_install.sh
    check '[ $RC = 0 ] && carjar_is "$T/jar_new" && echo "$OUT" | grep -q "unbekannt -> neu"' "unbekannte Jar im Auto: ersetzt, Anzeige 'unbekannt -> neu'"
    LASTB="$(ls -d "$AFD"/backup/*_install* | tail -1)"; check 'cmp -s "$T/jar_unknown" "$LASTB/ambient_fx.jar"' "unbekannte Jar gesichert"

    # --- macOS-Muell und Ungelistetes: ins Auto kommt NUR ambient_fx.jar + die gelisteten .ambiente
    run afx_uninstall.sh; check '[ ! -e "$CARJ/ambient_fx.jar" ] && [ ! -e "$EFX" ]' "Ausgangslage fuer den Muell-Test: Auto ohne AmbientFX"
    echo junk > "$AFD/build/plugins/._momentum.ambiente"; echo junk > "$AFD/build/plugins/.DS_Store"; echo n > "$AFD/build/plugins/notizen.txt"
    echo x > "$AFD/build/plugins/extra.ambiente"; echo junk > "$AFD/build/._ambient_fx.jar"; mkdir -p "$AFD/build/plugins/unterordner"; echo u > "$AFD/build/plugins/unterordner/x.ambiente"
    mkdir -p "$EFX"; echo j > "$EFX/._momentum.ambiente"; echo j > "$EFX/.DS_Store"; echo j > "$CARJ/._fremdmod.jar"
    JLIST="$(ls -A "$CARJ" | LC_ALL=C sort)"
    run afx_install.sh
    check '[ $RC = 0 ] && carjar_is "$T/jar_new" && all_plugs_same && [ -f "$T/reboot_called" ]' "Muell-Test: Installation klappt trotz Muell auf der SD"
    check '[ "$(ls -A "$EFX" | LC_ALL=C sort)" = "$(for p in $PLUGS; do echo $p.ambiente; done | LC_ALL=C sort)" ]' "Effekte-Ordner im Auto enthaelt EXAKT die 5 Plugins (kein ._*, kein .DS_Store, nichts Ungelistetes)"
    check '[ "$(ls -A "$CARJ" | LC_ALL=C sort)" = "$( { echo "$JLIST"; echo ambient_fx.jar; } | LC_ALL=C sort)" ]' "Jar-Ordner im Auto: nur ambient_fx.jar ist dazugekommen"
    check 'echo "$OUT" | grep -q "NICHT kopiert" && echo "$OUT" | grep -q "extra.ambiente" && echo "$OUT" | grep -q "notizen.txt" && echo "$OUT" | grep -q "._momentum.ambiente"' "Hinweis: Ungelistetes/Muell auf der SD wird NICHT kopiert (und benannt)"
    check '[ ! -e "$EFX/extra.ambiente" ] && [ ! -e "$EFX/notizen.txt" ] && [ ! -e "$EFX/unterordner" ] && [ ! -e "$CARJ/._ambient_fx.jar" ]' "nichts Ungelistetes im Auto gelandet"
    check 'echo "$OUT" | grep -q "macOS-Muell im Ordner ambientfx_effects entfernt: 2" && echo "$OUT" | grep -q "macOS-Reste im Ordner ambientfx_effects: keine"' "vorhandener macOS-Muell im Effekte-Ordner wird entfernt und gemeldet"
    check '[ -f "$CARJ/._fremdmod.jar" ] && echo "$OUT" | grep -q "macOS-Reste im Jar-Ordner (nicht von uns"' "fremder Muell im Jar-Ordner: nur gemeldet, nicht angefasst"
    check 'echo "$OUT" | grep -q "Kopiert wird NUR: ambient_fx.jar + 5 .ambiente-Plugins"' "Ausgabe nennt, was ausschliesslich kopiert wird"
    run afx_status.sh; check 'echo "$OUT" | grep -q "NICHT kopiert werden" && echo "$OUT" | grep -q "macOS-Reste im Auto: ._fremdmod.jar"' "status zeigt Ungelistetes auf der SD und Muell im Auto"
    # Uninstall raeumt Muell im Effekte-Ordner mit weg (Ordner verschwindet), fremden Muell im Jar-Ordner nicht
    echo j > "$EFX/._x"; echo j > "$EFX/.DS_Store"; run afx_uninstall.sh
    check '[ $RC = 0 ] && [ ! -e "$EFX" ] && [ -f "$CARJ/._fremdmod.jar" ] && echo "$OUT" | grep -q "macOS-Muell im Ordner ambientfx_effects entfernt: 2"' "uninstall: Muell im Effekte-Ordner mit entfernt, Ordner weg, fremder Muell bleibt"
    # ungueltige Namen in plugins.cksum werden abgelehnt (kein ._*, kein Pfad), Auto bleibt unveraendert
    rm -f "$AFD/build/plugins/._momentum.ambiente" "$AFD/build/plugins/.DS_Store" "$AFD/build/plugins/notizen.txt" "$AFD/build/plugins/extra.ambiente" "$AFD/build/._ambient_fx.jar"; rm -rf "$AFD/build/plugins/unterordner"; rm -f "$CARJ/._fremdmod.jar"
    S2="$(snap)"; cp "$AFD/build/plugins.cksum" "$T/plugsum_ok"
    echo "123 45 ._evil.ambiente" >> "$AFD/build/plugins.cksum"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "ungueltiger Plugin-Name" && [ "$(snap)" = "$S2" ]' "Plugin-Name mit Punkt am Anfang wird abgelehnt, Auto unveraendert"
    cp "$T/plugsum_ok" "$AFD/build/plugins.cksum"; echo "123 45 ../x.ambiente" >> "$AFD/build/plugins.cksum"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "ungueltiger Plugin-Name" && [ "$(snap)" = "$S2" ]' "Plugin-Name mit Pfad wird abgelehnt, Auto unveraendert"
    cp "$T/plugsum_ok" "$AFD/build/plugins.cksum"; echo "123 45 notizen.txt" >> "$AFD/build/plugins.cksum"; run afx_install.sh; check '[ $RC = 1 ] && echo "$OUT" | grep -q "ungueltiger Plugin-Name" && [ "$(snap)" = "$S2" ]' "Eintrag ohne .ambiente-Endung wird abgelehnt, Auto unveraendert"
    cp "$T/plugsum_ok" "$AFD/build/plugins.cksum"
    run afx_install.sh; check '[ $RC = 0 ] && carjar_is "$T/jar_new" && all_plugs_same' "zurueck zum sauberen Stand: installiert"

    # --- Downgrade-Schutz: SD hat aelteren Stand als das Auto
    mk_sd "$T/jar_old"; run afx_install.sh
    check '[ $RC = 1 ] && echo "$OUT" | grep -q "AELTEREN" && carjar_is "$T/jar_new" && [ ! -f "$T/reboot_called" ]' "Downgrade wird abgelehnt, Auto unveraendert, kein Neustart"
    touch "$AFD/ALLOW_DOWNGRADE"; run afx_install.sh; check '[ $RC = 0 ] && carjar_is "$T/jar_old"' "ALLOW_DOWNGRADE erlaubt es bewusst"; rm -f "$AFD/ALLOW_DOWNGRADE"
    mk_sd "$T/jar_new"; run afx_install.sh; check '[ $RC = 0 ] && carjar_is "$T/jar_new"' "zurueck auf den neuen Stand"

    # --- Abschaltbarer Neustart
    cp "$T/jar_old" "$CARJ/ambient_fx.jar"; touch "$AFD/NO_AUTO_REBOOT"; run afx_install.sh
    check '[ $RC = 0 ] && [ ! -f "$T/reboot_called" ] && echo "$OUT" | grep -q "Automatischer Neustart ist AUS" && carjar_is "$T/jar_new"' "NO_AUTO_REBOOT: kein Neustart, Hinweis"; rm -f "$AFD/NO_AUTO_REBOOT"

    # --- SD nicht beschreibbar: nichts wird veraendert
    cp "$T/jar_old" "$CARJ/ambient_fx.jar"; rm -rf "$AFD/backup"; chmod 555 "$AFD"; S1="$(snap)"; run afx_install.sh
    chmod 755 "$AFD"
    if [ "$(id -u)" = 0 ]; then ok; ok; else
        check '[ $RC = 1 ] && echo "$OUT" | grep -q "nicht beschreibbar" && [ "$(snap)" = "$S1" ] && [ ! -f "$T/reboot_called" ]' "SD nicht beschreibbar: Abbruch, Auto unveraendert, kein Neustart"
        ok
    fi
    run afx_install.sh

    # --- fremdmod.log (Log-Datei eines ANDEREN Mods im Jar-Ordner) waechst waehrend des Laufs: kein Fehlalarm, wird nie angefasst
    grow_start() { # <Datei>...: haengt im Hintergrund laufend Zeilen an die Dateien (Betrieb des anderen Mods)
        GROW_FILES="$*"
        ( while :; do for gf in $GROW_FILES; do echo "betrieb $(date +%s)" >> "$gf"; done; sleep 0.01; done ) &
        GROWPID=$!
    }
    grow_stop() { { kill "$GROWPID"; wait "$GROWPID"; } 2>/dev/null; return 0; }
    printf 'zeile1\nzeile2\nzeile3\n' > "$CARJ/fremdmod.log"; printf 'alt1\nalt2\n' > "$CARJ/fremdmod.log.1"
    LOG0="$(cat "$CARJ/fremdmod.log")"; LOG1="$(cat "$CARJ/fremdmod.log.1")"
    # a) Aktualisieren mitten im Lauf
    cp "$T/jar_old" "$CARJ/ambient_fx.jar"; SZ0=$(wc -c < "$CARJ/fremdmod.log")
    grow_start "$CARJ/fremdmod.log" "$CARJ/fremdmod.log.1"; run afx_install.sh; grow_stop
    check '[ $RC = 0 ] && echo "$OUT" | grep -q "ERGEBNIS: OK" && [ "$(cat "$T/reboot_called")" = "reboot 15" ] && carjar_is "$T/jar_new" && all_plugs_same' "wachsende fremdmod.log: Update endet mit ERGEBNIS: OK + Neustart (kein Fehlalarm)"
    check 'echo "$OUT" | grep -q "Andere Jars im Ordner: unveraendert"' "wachsende fremdmod.log: Pruefung 'andere Jars unveraendert' besteht"
    check '[ "$(wc -c < "$CARJ/fremdmod.log")" -gt "$SZ0" ]' "(Testgueltigkeit) die Log-Datei ist WAEHREND des Laufs tatsaechlich gewachsen"
    check '[ "$(head -c ${#LOG0} "$CARJ/fremdmod.log")" = "$LOG0" ] && [ "$(head -c ${#LOG1} "$CARJ/fremdmod.log.1")" = "$LOG1" ]' "Log-Dateien nicht angefasst: noch da, bisheriger Inhalt unveraendert"
    # b) erste Installation MIT Komplett-Sicherung (cp -R + Byte-Vergleich der Jars) waehrend die Log waechst
    rm -rf "$AFD/backup/original"; cp "$T/jar_old" "$CARJ/ambient_fx.jar"; SZ0=$(wc -c < "$CARJ/fremdmod.log")
    grow_start "$CARJ/fremdmod.log" "$CARJ/fremdmod.log.1"; run afx_install.sh; grow_stop
    check '[ $RC = 0 ] && echo "$OUT" | grep -q "Komplett-Sicherung (einmalig)" && echo "$OUT" | grep -q "alle Jars + lsd.sh identisch gesichert" && [ -d "$AFD/backup/original/jars" ] && [ -f "$T/reboot_called" ]' "wachsende Log: auch die einmalige Komplett-Sicherung (Byte-Vergleich) schlaegt nicht falsch an"
    check '[ "$(wc -c < "$CARJ/fremdmod.log")" -gt "$SZ0" ] && [ "$(head -c ${#LOG0} "$CARJ/fremdmod.log")" = "$LOG0" ]' "Log-Datei dabei weiter unberuehrt (gewachsen, Anfang gleich)"
    # c) Deinstallieren mitten im Lauf: Log wird weder geloescht noch als REST gewertet
    SZ0=$(wc -c < "$CARJ/fremdmod.log"); grow_start "$CARJ/fremdmod.log" "$CARJ/fremdmod.log.1"; run afx_uninstall.sh; grow_stop
    check '[ $RC = 0 ] && echo "$OUT" | grep -q "ERGEBNIS: OK" && [ "$(cat "$T/reboot_called")" = "reboot 15" ] && [ ! -e "$CARJ/ambient_fx.jar" ] && ! echo "$OUT" | grep -q "REST gefunden"' "wachsende fremdmod.log: Deinstallieren endet mit ERGEBNIS: OK + Neustart, Log kein REST"
    check '[ -f "$CARJ/fremdmod.log" ] && [ -f "$CARJ/fremdmod.log.1" ] && [ "$(wc -c < "$CARJ/fremdmod.log")" -gt "$SZ0" ] && [ "$(head -c ${#LOG0} "$CARJ/fremdmod.log")" = "$LOG0" ]' "Deinstallieren: fremde Log-Dateien weiter vorhanden und unberuehrt"
    run afx_status.sh; check 'echo "$OUT" | grep -q "Andere Jars:" && ! echo "$OUT" | grep -q "fremdmod.log"' "status zaehlt Log-Dateien nicht als Jars"
    # d) Gegenprobe: eine Nicht-Log-Datei eines anderen Mods, die sich waehrend des Laufs aendert, MUSS weiter auffallen
    run afx_install.sh; cp "$T/jar_old" "$CARJ/ambient_fx.jar"
    grow_start "$CARJ/mody.jar"; run afx_install.sh; grow_stop
    check '[ $RC = 1 ] && echo "$OUT" | grep -q "andere Dateien im Jar-Ordner haben sich veraendert" && [ ! -f "$T/reboot_called" ]' "Gegenprobe: geaenderte fremde .jar wird weiter erkannt (Abbruch, kein Neustart)"
    echo "other" > "$CARJ/mody.jar"; rm -f "$CARJ/fremdmod.log" "$CARJ/fremdmod.log.1"
    run afx_install.sh; check '[ $RC = 0 ] && carjar_is "$T/jar_new" && all_plugs_same && others_same' "zurueck zum sauberen Stand: installiert, andere Jars unberuehrt"

    # --- Deinstallieren: alles weg, Fremdes bleibt, gemerkter Effekt weg
    mkdir -p "$PERS" "$HK"; echo "k2" > "$PERS/ambientfx_key2.state"; echo "g" > "$PERS/ambientfx.state"; echo "alt" > "$HK/ambientfx.state"; echo "fremd" > "$HK/anderer_mod.txt"
    echo "d" > "$TMPD/ambientfx.debug"; echo "l" > "$TMPD/ambientfx.log"
    : > "$PERS/debug"; mkdir -p "$T/dev/shmem"; echo "r" > "$T/dev/shmem/ambientfx.log"
    run afx_uninstall.sh
    check '[ $RC = 0 ] && [ ! -e "$CARJ/ambient_fx.jar" ] && [ ! -e "$EFX" ] && [ ! -e "$PERS" ] && [ ! -e "$HK/ambientfx.state" ] && [ "$(cat "$T/reboot_called")" = "reboot 15" ]' "uninstall: Jar, Effekte (samt Ordner), gemerkter Effekt weg + Neustart nach 15 s"
    check 'echo "$OUT" | grep -q "PRUEFUNG" && echo "$OUT" | grep -q "Andere Jars    : unveraendert" && echo "$OUT" | grep -q "ERGEBNIS: OK" && echo "$OUT" | grep -q "Reste: keine"' "uninstall: Pruefung zeigt alles sauber"
    check 'others_same && [ "$(cat "$HK/anderer_mod.txt")" = fremd ] && [ "$(cat "$LSD/lsd.sh")" = "#!/bin/sh stock" ]' "uninstall: fremde Jars/Dateien unberuehrt"
    check 'echo "$OUT" | grep -q "mody.jar \[" && echo "$OUT" | grep -q "fremdmod.jar \["' "uninstall: Nachweis nennt die anderen Jars mit Pruefsumme"
    check '[ ! -e "$TMPD/ambientfx.debug" ] && [ ! -e "$TMPD/ambientfx.log" ]' "uninstall: alte Debug-Dateien weg"
    check '[ ! -e "$PERS/debug" ] && [ ! -e "$T/dev/shmem/ambientfx.log" ]' "uninstall: Debug-Marker (/mnt/persist) und RAM-Protokoll (/dev/shmem) weg"
    UB="$(ls -d "$AFD"/backup/*_uninstall* | tail -1)"
    check 'cmp -s "$T/jar_new" "$UB/ambient_fx.jar" && ls "$UB"/ambientfx_effects/*.ambiente | wc -l | grep -q "^ *5$" && [ "$(cat "$UB/state/persist_ambientfx_key2.state")" = k2 ] && [ "$(cat "$UB/state/hooks_ambientfx.state")" = alt ] && [ -f "$UB/state/persist_debug" ]' "uninstall: Jar, 5 Effekte, gemerkter Effekt und Debug-Marker vorher auf der SD gesichert"
    run afx_status.sh; check 'echo "$OUT" | grep -q "Auto Jar    : nicht installiert" && echo "$OUT" | grep -q "nicht vorhanden"' "status nach uninstall sauber"
    run afx_uninstall.sh; check '[ $RC = 0 ] && echo "$OUT" | grep -q "nichts von mib2q-ambient-fx installiert" && [ ! -f "$T/reboot_called" ]' "zweite Deinstallation: nichts zu tun, kein Neustart"
    # sauber neu installieren (Wiederaufbau)
    run afx_install.sh; check '[ $RC = 0 ] && carjar_is "$T/jar_new" && all_plugs_same && [ -f "$T/reboot_called" ]' "nach Deinstallation: Neuinstallation klappt"

    # --- Deinstallieren: Ordner mit fremder Datei bleibt stehen
    echo "fremd" > "$EFX/notizen.txt"; run afx_uninstall.sh
    check '[ $RC = 0 ] && [ -d "$EFX" ] && [ "$(cat "$EFX/notizen.txt")" = fremd ] && ! ls "$EFX" | grep -q ambiente && echo "$OUT" | grep -q "bleibt (enthaelt noch andere Dateien)"' "uninstall: Ordner mit fremder Datei bleibt, Plugins weg"
    rm -rf "$EFX"
    # --- Reste und Handsicherung
    run afx_install.sh; echo "x" > "$CARJ/ambient_fx.jar.alt"; run afx_uninstall.sh
    check '[ $RC = 2 ] && echo "$OUT" | grep -q "REST gefunden" && echo "$OUT" | grep -q "ambient_fx.jar.alt" && [ ! -f "$T/reboot_called" ]' "uninstall: meldet Reste, KEIN Neustart"
    rm -f "$CARJ/ambient_fx.jar.alt"
    run afx_install.sh; mkdir -p "$LSD/ambientfx_backup_vor_fern_debug"; echo "hand" > "$LSD/ambientfx_backup_vor_fern_debug/ambient_fx.jar.bak"; run afx_uninstall.sh
    check '[ $RC = 0 ] && echo "$OUT" | grep -q "Handsicherung bleibt" && [ -f "$LSD/ambientfx_backup_vor_fern_debug/ambient_fx.jar.bak" ] && [ -f "$T/reboot_called" ]' "uninstall: eigene Handsicherung gilt nicht als Rest und bleibt"
    # Auto-Neustart abschaltbar auch beim Deinstallieren
    rm -rf "$LSD/ambientfx_backup_vor_fern_debug"; run afx_install.sh; touch "$AFD/NO_AUTO_REBOOT"; run afx_uninstall.sh
    check '[ $RC = 0 ] && [ ! -f "$T/reboot_called" ] && [ ! -e "$CARJ/ambient_fx.jar" ]' "uninstall mit NO_AUTO_REBOOT: kein Neustart"; rm -f "$AFD/NO_AUTO_REBOOT"
    # Protokoll
    check '[ -f "$AFD/log/install.txt" ] && [ -f "$AFD/log/uninstall.txt" ] && [ -f "$AFD/log/status.txt" ]' "Protokolle auf der SD abgelegt"
    check 'others_same' "am Ende: andere Jars immer noch unberuehrt"

    # --- Sicherungs-Namen OHNE Uhr (Auto-Uhr steht im GEM auf 1970 und beginnt nach jedem Neustart von vorn): laufende Nummer + Jar-Pruefsumme
    mkdir -p "$T/fakebin1970" "$T/fakebin2026"
    printf '%s\n' '#!/bin/sh' 'case "$*" in *%Y%m%d*) echo 19700101-000053 ;; *%Y*) echo 1970 ;; *) echo "Thu Jan 01 00:00:53 GMT 1970" ;; esac' > "$T/fakebin1970/date"
    printf '%s\n' '#!/bin/sh' 'case "$*" in *%Y%m%d*) echo 20261004-153000 ;; *%Y*) echo 2026 ;; *) echo "Sun Oct 04 15:30:00 CEST 2026" ;; esac' > "$T/fakebin2026/date"
    chmod +x "$T/fakebin1970/date" "$T/fakebin2026/date"
    rm -rf "$AFD/backup" "$AFD/log" "$EFX"; rm -f "$CARJ/ambient_fx.jar"; mkdir -p "$AFD/backup/19700101-000034_install"   # alter Ordner (Altlast der ersten Version) darf nicht stoeren
    JN_OLD=$(ck "$T/jar_old"); JN_OLD=${JN_OLD%% *}; JN_NEW=$(ck "$T/jar_new"); JN_NEW=${JN_NEW%% *}
    FAKEBIN="$T/fakebin1970"
    run afx_install.sh
    check '[ $RC = 0 ] && [ -d "$AFD/backup/001_install_ohne-jar" ]' "Name ohne Uhr: erste Sicherung = 001_install_ohne-jar (alter Datums-Ordner wird ignoriert)"
    check '[ -f "$AFD/backup/001_install_ohne-jar/STAND.txt" ] && grep -q "keine (ambient_fx.jar war nicht installiert)" "$AFD/backup/001_install_ohne-jar/STAND.txt" && grep -q "Auto-Uhr nicht gestellt" "$AFD/backup/001_install_ohne-jar/STAND.txt"' "nichts zu sichern: Ordner ist NICHT leer, STAND.txt erklaert es (inkl. Hinweis Uhr)"
    check 'echo "$OUT" | grep -q "STAND.txt"' "Anzeige nennt den Vermerk STAND.txt"
    check '[ -f "$AFD/log/001_install.txt" ] && [ -f "$AFD/log/install.txt" ] && cmp -s "$AFD/log/001_install.txt" "$AFD/log/install.txt"' "Protokoll als log/001_install.txt UND log/install.txt (letzter Lauf)"
    check '[ -z "$(ls "$AFD/backup" | grep -E "^[0-9]{3}_" | grep 1970)" ]' "kein neuer Ordnername enthaelt eine Uhrzeit/1970"
    cp "$T/jar_old" "$CARJ/ambient_fx.jar"; run afx_install.sh
    check '[ $RC = 0 ] && [ -d "$AFD/backup/002_install_$JN_OLD" ] && cmp -s "$T/jar_old" "$AFD/backup/002_install_$JN_OLD/ambient_fx.jar"' "Update: 002_install_<Pruefsumme der gesicherten Jar>, Jar liegt darin"
    check 'grep -q "$JN_OLD" "$AFD/backup/002_install_$JN_OLD/STAND.txt" && grep -q "= alt" "$AFD/backup/002_install_$JN_OLD/STAND.txt" && grep -q "momentum.ambiente" "$AFD/backup/002_install_$JN_OLD/STAND.txt"' "STAND.txt nennt Jar-Pruefsumme, Versionsname und Effekte"
    check '! echo "$OUT" | grep -q "schon gesichert in"' "erste Sicherung dieser Jar: kein Hinweis auf Doppelte"
    cp "$T/jar_old" "$CARJ/ambient_fx.jar"; run afx_install.sh
    check '[ $RC = 0 ] && [ -d "$AFD/backup/003_install_$JN_OLD" ] && echo "$OUT" | grep -q "schon gesichert in: 002_install_$JN_OLD"' "gleiche Jar-Pruefsumme nochmal: eigener Ordner 003, Hinweis auf 002"
    check '[ -f "$AFD/log/002_install.txt" ] && [ -f "$AFD/log/003_install.txt" ] && ! cmp -s "$AFD/log/002_install.txt" "$AFD/log/003_install.txt" && ! grep -q "schon gesichert in" "$AFD/log/002_install.txt"' "aelteres Protokoll 002 bleibt unveraendert (nichts ueberschrieben)"
    # Zaehler: fuehrende Nullen (008/009 sind keine Oktalzahlen) und fremde Ordner (original) stoeren nicht
    mkdir -p "$AFD/backup/008_x" "$AFD/backup/009_x"; run afx_uninstall.sh
    check '[ $RC = 0 ] && [ -d "$AFD/backup/010_uninstall_$JN_NEW" ] && [ -d "$AFD/backup/original" ]' "Zaehler nach 009 -> 010 (kein Oktal-Fehler), Ordner 'original' zaehlt nicht"
    check '[ -f "$AFD/log/010_uninstall.txt" ] && [ -f "$AFD/backup/010_uninstall_$JN_NEW/STAND.txt" ] && ls "$AFD/backup/010_uninstall_$JN_NEW/ambientfx_effects"/*.ambiente | wc -l | grep -q "^ *5$"' "Deinstallation: gleiche Nummer fuer Sicherung + Protokoll, 5 Effekte gesichert"
    # Uhr ist gestellt: Datum steht im Vermerk, die Namen bleiben trotzdem Nummer-basiert
    FAKEBIN="$T/fakebin2026"; run afx_install.sh
    check '[ $RC = 0 ] && ls -d "$AFD"/backup/011_install_* >/dev/null 2>&1 && grep -q "Datum         : Sun Oct 04 15:30:00 CEST 2026" "$AFD"/backup/011_install_*/STAND.txt' "gestellte Uhr: Datum im Vermerk, Name bleibt Nummer-basiert (011)"
    FAKEBIN=""
    check 'others_same' "Sicherungs-Namen-Tests: andere Jars unberuehrt"

    rm -rf "$T"
}

SHELLS="${1:-sh bash ksh dash}"
for s in $SHELLS; do
    command -v "$s" >/dev/null 2>&1 || { echo "== Shell $s nicht vorhanden - uebersprungen"; continue; }
    run_all "$(command -v "$s")"
done
echo "-----"; echo "bestanden: $PASS, Fehler: $FAILS"
[ "$FAILS" = 0 ]
