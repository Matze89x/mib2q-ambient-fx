#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# afx_status.sh - GEM > M.I.B. > ambientfx > "Status": NUR LESEN. Zeigt, was auf der SD-Karte und im Auto liegt und ob beides zusammenpasst; schreibt zusaetzlich
# einen ausfuehrlichen Bericht nach <SD>/mib2q-ambient-fx/log/status_bericht.txt (fuer die Fehlersuche, ohne Telnet). Aendert nichts im Auto.
AFX_NAME=status
. "$(dirname "$0")/afx_lib.sh"
afx_banner "Status (nur lesen)"
sd_rw
REPORT=$AFX_DIR/log/status_bericht.txt
mkdir -p "$AFX_DIR/log" 2>/dev/null

[ -f "$AFX_ESD_DIR/VERSION" ] && { read -r PKG < "$AFX_ESD_DIR/VERSION"; say "SD-Paket : $PKG"; }

# ---- SD-Karte ----
if [ -f "$SD_JAR" ]; then
    SD_JS=$(cksum2 "$SD_JAR"); SD_JV=$(ver_label "$SD_JS")
    OKI=""; [ -f "$SD_JARSUM" ] && { read -r e1 e2 rest < "$SD_JARSUM"; [ "$e1 $e2" = "$SD_JS" ] || OKI=" (WEICHT von build/ambient_fx.jar.cksum ab!)"; }
    say "SD  Jar    : $SD_JV  [$SD_JS]$OKI"
else
    SD_JS=""; say "SD  Jar    : fehlt (build/ambient_fx.jar im Ordner mib2q-ambient-fx)"
fi
if sd_plugins_check; then say "SD  Effekte: $(plug_count) Plugins, alle geprueft"; else say "SD  Effekte: PROBLEM - $PLUG_ERR"; fi
SDX=$(sd_plugin_extras); [ -z "$SDX" ] || say "SD  Hinweis : im Ordner plugins liegen Dateien, die NICHT kopiert werden: $(echo $SDX)"
[ -d "$SD_FULLBACKUP/jars" ] && say "SD  Komplett-Sicherung der Jars (backup/original): vorhanden" || say "SD  Komplett-Sicherung der Jars: noch nicht angelegt (kommt beim ersten Installieren)"

# ---- Auto ----
if [ -f "$JAR" ]; then
    CAR_JS=$(cksum2 "$JAR"); CAR_JV=$(ver_label "$CAR_JS")
    REL=""
    if [ -n "$SD_JS" ]; then
        if [ "$CAR_JS" = "$SD_JS" ]; then REL="= wie SD"
        else
            ver_lookup "$CAR_JS"; cr=$VER_RANK; ver_lookup "$SD_JS"; sr=$VER_RANK
            if [ -n "$cr" ] && [ -n "$sr" ]; then
                if [ "$sr" -gt "$cr" ]; then REL="aelter als SD -> Update moeglich"; else REL="NEUER als SD (kein Update)"; fi
            else REL="Vergleich nicht moeglich"; fi
        fi
    fi
    say "Auto Jar    : $CAR_JV  [$CAR_JS] $REL"
else
    CAR_JS=""; say "Auto Jar    : nicht installiert"
fi
if [ -d "$EFX" ]; then
    NSAME=0; NDIFF=0; NMISS=0
    if sd_plugins_check; then
        for n in $(plug_names); do
            if [ ! -f "$EFX/$n" ]; then NMISS=$((NMISS + 1)); say "Auto Effekt : $n FEHLT"
            elif [ "$(cksum2 "$EFX/$n")" = "$(plug_sum "$n")" ]; then NSAME=$((NSAME + 1))
            else NDIFF=$((NDIFF + 1)); say "Auto Effekt : $n WEICHT von der SD ab"; fi
        done
        say "Auto Effekte: $NSAME gleich wie SD, $NDIFF abweichend, $NMISS fehlend (von $(plug_count))"
    else
        say "Auto Effekte: $(car_plugin_count) Plugin-Datei(en) (SD-Vergleich nicht moeglich)"
    fi
    for n in $LEGACY_PLUGINS; do [ -f "$EFX/$n" ] && say "Auto Alt-Effekt: $n liegt noch (wird beim Installieren entfernt)"; done
    for f in "$EFX"/*.ambiente; do [ -f "$f" ] || continue; b=${f##*/}; is_ours_plugin "$b" || is_legacy_plugin "$b" || say "Auto Fremd   : $b (nicht von dieser SD)"; done
else
    say "Auto Effekte: Ordner ambientfx_effects nicht vorhanden"
fi
JE=$(list_junk_in "$EFX"); JJ=$(list_junk_in "$JARS")
[ -z "$JE$JJ" ] && say "macOS-Reste im Auto: keine" || say "macOS-Reste im Auto: $(echo $JE $JJ) (Installieren/Deinstallieren raeumt im Ordner ambientfx_effects auf)"
NST=0; for f in "$PERSIST"/ambientfx*.state "$HOOKS_STATE"; do [ -f "$f" ] && NST=$((NST + 1)); done
say "Gemerkter Effekt: $NST state-Datei(en) (/mnt/persist/ambientfx)"
if [ -f "$PERSIST/debug" ]; then say "Debug-Marker: GESETZT - AmbientFX protokolliert im RAM (Telnet: cat /dev/shmem/ambientfx.log; ausschalten: rm /mnt/persist/ambientfx/debug)"
else say "Debug-Marker: nicht gesetzt (kein Protokoll; einschalten per Telnet: touch /mnt/persist/ambientfx/debug)"; fi
NOJ=0; for oj in $(jars_snapshot | while read -r jn jc js; do echo "$jn"; done); do NOJ=$((NOJ + 1)); done
say "Andere Jars: $NOJ Datei(en) im Jar-Ordner (werden nie angefasst)"
say "--------------------------------------------------------------------"
if [ -n "$CAR_JS" ] && [ "$CAR_JS" = "$SD_JS" ] && [ "${NMISS:-1}" = 0 ] && [ "${NDIFF:-1}" = 0 ]; then say "Ergebnis: alles auf dem Stand der SD."
else say "Ergebnis: Auto und SD sind NICHT identisch -> Punkt 1 'Installieren / Updaten'."; fi

# ---- Bericht (ausfuehrlich, nur auf die SD) ----
{
    echo "mib2q-ambient-fx Status-Bericht"; date 2>/dev/null; uname -a 2>/dev/null
    echo "== Jars im Auto =="; ls -la "$JARS" 2>&1
    echo "== ambientfx_effects =="; ls -la "$EFX" 2>&1
    echo "== gemerkter Effekt =="; ls -la "$PERSIST" 2>&1; ls -la "$HOOKS" 2>&1
    echo "== Einhaengepunkte =="; mount 2>&1 | grep -i -E "/mnt/app|/mnt/persist|sda0|system"
    echo "== Platz =="; df /mnt/app /mnt/persist 2>&1
    echo "== SD-Ordner =="; ls -l "$AFX_DIR" 2>&1          # ohne -a: "." und ".." scheitern am MMX-Netzpfad mit "Filename too long"
    echo "== SD-Sicherungen =="; ls -l "$AFX_DIR/backup" "$SD_FULLBACKUP" 2>&1
} > "$REPORT" 2>&1
say "Bericht: $REPORT"
finish 0
