#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# afx_uninstall.sh - GEM > M.I.B. > ambientfx > "Deinstallieren": entfernt NUR AmbientFX (ambient_fx.jar, alle .ambiente-Plugins samt leerem Ordner
# ambientfx_effects, den gemerkten Effekt /mnt/persist/ambientfx/ambientfx*.state, den Debug-Marker /mnt/persist/ambientfx/debug samt RAM-Protokoll und den alten Pfad root/hooks/ambientfx.state). Vorher kommt von allem eine
# Kopie auf die SD-Karte (backup/<Zeitstempel>_uninstall/). Danach: Suche nach Resten (ambient* in jars/, lsd/, root/hooks/) und der Nachweis, dass alle
# anderen Jars (mody.jar, fremdmod.jar, carplay ...) unveraendert sind. Andere Mods werden nie angefasst. Ergebnis: das MMI ist wieder im Originalzustand.
AFX_NAME=uninstall
. "$(dirname "$0")/afx_lib.sh"
afx_banner "Deinstallieren (nur AmbientFX)"

# Hilfsfunktionen: unsere gemerkten Zustandsdateien
# (der Debug-Marker /mnt/persist/ambientfx/debug zaehlt dazu: er ueberlebt den Neustart und muss mit weg)
state_files() { for st_f in "$PERSIST"/ambientfx*.state "$PERSIST/debug" "$HOOKS_STATE"; do [ -f "$st_f" ] && echo "$st_f"; done; }

TMPD=${AFX_TEST:+$AFX_TEST}/tmp        # im Auto: /tmp (alte Debug-Dateien frueherer Versionen, ggf. Ausweich-Ort des RAM-Protokolls)
SHMD=${AFX_TEST:+$AFX_TEST}/dev/shmem  # im Auto: /dev/shmem (RAM-Protokoll ambientfx.log)

# gibt es ueberhaupt etwas von uns?
ANY=0
for f in "$JAR" "$JAR.afxnew" "$EFX" $(state_files) "$TMPD/ambientfx.debug" "$TMPD/ambientfx.log" "$SHMD/ambientfx.log"; do [ -e "$f" ] && ANY=1; done
if [ "$ANY" = 0 ]; then
    say "Es ist nichts von mib2q-ambient-fx installiert (keine Jar, keine Effekte, kein gemerkter Effekt)."
    say "(Nichts veraendert, kein Neustart.)"
    finish 0
fi
JARS_BEFORE=$(jars_snapshot)
sd_rw
backup_dir_new || fail "Die SD-Karte ist nicht beschreibbar - ohne Sicherung wird nichts entfernt."
backup_file "$JAR" ambient_fx.jar || fail "Sicherung der Jar misslungen."
backup_car_plugins || fail "Sicherung der Effekte misslungen."
for f in $(state_files); do
    case $f in "$HOOKS_STATE") bn=hooks_ambientfx.state ;; *) bn=persist_${f##*/} ;; esac
    backup_file "$f" "state/$bn" || fail "Sicherung des gemerkten Effekts misslungen ($f)."
done
backup_stand
say "Sicherung (alles Betroffene): $BKP"
[ -n "$BKP_SAME" ] && say "  (dieselbe Jar, Nr. $BKP_JARNR, ist schon gesichert in: $BKP_SAME)"
car_rw
persist_rw

# 1. Jar
for f in "$JAR" "$JAR.afxnew"; do
    if [ -f "$f" ]; then rm -f "$f" 2>/dev/null; [ -f "$f" ] && fail "$f nicht entfernbar."; say "1. entfernt: ${f##*/}"; fi
done
# 2. Effekt-Plugins (alle .ambiente samt Zwischendateien); der Ordner nur, wenn danach nichts anderes mehr darin liegt
K=0
for f in "$EFX"/*.ambiente "$EFX"/*.afxnew; do
    if [ -f "$f" ]; then rm -f "$f" 2>/dev/null; [ -f "$f" ] && fail "$f nicht entfernbar."; K=$((K + 1)); fi
done
say "2. Effekt-Plugins entfernt: $K Datei(en)"
clean_junk "$EFX"
[ "$CLEANED" = 0 ] || say "   macOS-Muell im Ordner ambientfx_effects entfernt: $CLEANED Datei(en)"
if [ -d "$EFX" ]; then
    if rmdir "$EFX" 2>/dev/null; then say "   Ordner ambientfx_effects entfernt (war leer)"; else say "   Ordner ambientfx_effects bleibt (enthaelt noch andere Dateien)"; fi
fi
# 3. gemerkter Effekt (nur unsere Dateien; der Ordner nur, wenn leer)
K=0
for f in $(state_files); do rm -f "$f" 2>/dev/null; [ -f "$f" ] && fail "$f nicht entfernbar."; K=$((K + 1)); done
say "3. gemerkter Effekt und Debug-Marker entfernt: $K Datei(en)"
[ -d "$PERSIST" ] && rmdir "$PERSIST" 2>/dev/null
# 4. Debug-Dateien: RAM-Protokoll (/dev/shmem, ggf. /tmp) und alte Dateien frueherer Versionen
for f in "$TMPD/ambientfx.debug" "$TMPD/ambientfx.log" "$SHMD/ambientfx.log"; do [ -e "$f" ] && { rm -f "$f" 2>/dev/null; say "4. Debug-Datei entfernt: $f"; }; done
# 5. Reste suchen (eigene Handsicherungen "ambientfx_backup*" gelten nicht als Rest, werden aber genannt)
REST=""; NOTE=""
for d in "$JARS" "$LSD" "$HOOKS"; do
    [ -d "$d" ] || continue
    for f in "$d"/ambient*; do
        [ -e "$f" ] || continue
        case ${f##*/} in
            ambientfx_backup*) NOTE="$NOTE $f" ;;
            ambientfx_effects)  # der Ordner selbst ist nur dann ein Rest, wenn noch Plugins von uns darin liegen (Fremdes darin bleibt bewusst stehen)
                if [ "$f" = "$EFX" ] && [ "$(car_plugin_count)" = 0 ] && [ ! -e "$EFX"/*.afxnew ]; then NOTE="$NOTE $f"; else REST="$REST $f"; fi ;;
            *) REST="$REST $f" ;;
        esac
    done
done
for f in "$PERSIST"/*; do [ -e "$f" ] && REST="$REST $f"; done
if [ -n "$REST" ]; then say "5. REST gefunden (bitte pruefen):"; for r in $REST; do say "     $r"; done; AFX_RC=2
else say "5. Reste: keine."; fi
for r in $NOTE; do say "   (Handsicherung bleibt: $r)"; done

# ---- Pruefung: alles noch einmal aus dem Auto lesen ----
say "PRUEFUNG (aus dem Auto gelesen):"
BADV=0
[ ! -e "$JAR" ] && say "  Jar            : nicht mehr vorhanden" || { say "  Jar            : NOCH DA"; BADV=1; }
if [ -d "$EFX" ]; then
    if [ "$(car_plugin_count)" = 0 ]; then say "  Effekte        : keine .ambiente mehr (Ordner enthaelt Fremdes)"; else say "  Effekte        : NOCH DA"; BADV=1; fi
else say "  Effekte        : nicht mehr vorhanden"; fi
[ -z "$(state_files)" ] && say "  Gemerkter Effekt: nicht mehr vorhanden" || { say "  Gemerkter Effekt: NOCH DA"; BADV=1; }
if [ "$(jars_snapshot)" = "$JARS_BEFORE" ]; then
    say "  Andere Jars    : unveraendert (Pruefsummen gleich):"
    jars_snapshot | while read -r jn jc js; do say "     $jn [$jc $js]"; done
else say "  Andere Jars    : VERAENDERT"; BADV=1; fi
[ "$BADV" = 0 ] || fail "Pruefung nach dem Entfernen nicht sauber (siehe oben) - kein Neustart."
say "--------------------------------------------------------------------"
if [ "${AFX_RC:-0}" = 0 ]; then
    say "Danach laeuft alles wie vor der Installation (Audi original)."
    finish_reboot 15
fi
say "Es wurden Reste gefunden - KEIN automatischer Neustart. Bitte die Liste ansehen."
finish ${AFX_RC:-0}
