#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# afx_install.sh - GEM > M.I.B. > ambientfx > "Installieren / Updaten": ambient_fx.jar UND alle Effekt-Plugins (.ambiente) von der SD ins Auto.
#   * nichts installiert        -> installieren
#   * installiert, SD anders    -> aktualisieren (Anzeige alt -> neu, je Datei)
#   * alles gleich              -> "schon aktuell, nichts zu tun" (kein Neustart)
# Immer Jar und ALLE Plugins zusammen (stehende Regel: die Jar enthaelt dieselben Effekt-Klassen, die Plugins duerfen nie auseinanderlaufen).
# Die frueheren Testeffekte momentumgds / momentumgdsselect werden - nach Sicherung - entfernt.
AFX_NAME=install
. "$(dirname "$0")/afx_lib.sh"
afx_banner "Installieren / Updaten (Jar + Effekte)"

# ---- Vorpruefung (nichts wird veraendert) ----
[ -f "$SD_JAR" ] || fail "build/ambient_fx.jar fehlt in mib2q-ambient-fx auf der SD-Karte."
[ -f "$SD_JARSUM" ] || fail "build/ambient_fx.jar.cksum fehlt auf der SD-Karte."
SD_JS=$(cksum2 "$SD_JAR")
read -r e1 e2 rest < "$SD_JARSUM"
[ "$e1 $e2" = "$SD_JS" ] || fail "Pruefsumme der Jar ($SD_JS) weicht von build/ambient_fx.jar.cksum ($e1 $e2) ab - Datei defekt, bitte neu auf die SD kopieren."
sd_plugins_check || fail "Effekt-Plugins auf der SD nicht in Ordnung: $PLUG_ERR."
NPL=$(plug_count)
say "Kopiert wird NUR: ambient_fx.jar + $NPL .ambiente-Plugins (Namen aus plugins.cksum)."
SDX=$(sd_plugin_extras)
[ -z "$SDX" ] || warn "diese Dateien liegen auf der SD im Ordner plugins, werden aber NICHT kopiert: $(echo $SDX)"
[ -d "$JARS" ] || fail "Das Jar-Verzeichnis des Autos ($JARS) gibt es nicht - falsches Geraet?"
say "SD  Jar    : $(ver_label "$SD_JS")  [$SD_JS]"
say "SD  Effekte: $NPL Plugins, alle geprueft"
if [ -f "$JAR" ]; then CAR_JS=$(cksum2 "$JAR"); say "Auto Jar    : $(ver_label "$CAR_JS")  [$CAR_JS]"; else CAR_JS=""; say "Auto Jar    : noch nicht installiert"; fi
say "Auto Effekte: $(car_plugin_count) Plugin-Datei(en) vorhanden"

# ---- Was ist zu tun? ----
JAR_DO=0
if [ -z "$CAR_JS" ]; then JAR_DO=1; say "Jar         : wird NEU installiert"
elif [ "$CAR_JS" = "$SD_JS" ]; then say "Jar         : gleich, nichts zu tun"
else
    if ver_lookup "$CAR_JS"; then CR_=$VER_RANK; else CR_=""; fi
    if ver_lookup "$SD_JS"; then SR_=$VER_RANK; else SR_=""; fi
    if [ -n "$CR_" ] && [ -n "$SR_" ] && [ "$CR_" -gt "$SR_" ] && [ ! -f "$AFX_DIR/ALLOW_DOWNGRADE" ]; then
        fail "Die SD hat einen AELTEREN Jar-Stand als das Auto ($(ver_label "$SD_JS") statt $(ver_label "$CAR_JS")). Nichts veraendert. (Absicht? Leere Datei ALLOW_DOWNGRADE in den Ordner mib2q-ambient-fx legen.)"
    fi
    JAR_DO=1; say "Jar         : wird AKTUALISIERT: $(ver_label "$CAR_JS") -> $(ver_label "$SD_JS")"
fi
PLUG_TODO=""; PLUG_NEW=0; PLUG_UPD=0; PLUG_SAME=0
for n in $(plug_names); do
    want=$(plug_sum "$n")
    if [ ! -f "$EFX/$n" ]; then PLUG_TODO="$PLUG_TODO $n"; PLUG_NEW=$((PLUG_NEW + 1)); say "Effekt      : $n NEU"
    elif [ "$(cksum2 "$EFX/$n")" = "$want" ]; then PLUG_SAME=$((PLUG_SAME + 1))
    else PLUG_TODO="$PLUG_TODO $n"; PLUG_UPD=$((PLUG_UPD + 1)); say "Effekt      : $n AKTUALISIERT [$(cksum2 "$EFX/$n")] -> [$want]"; fi
done
[ "$PLUG_SAME" -gt 0 ] && say "Effekte     : $PLUG_SAME gleich, nichts zu tun"
LEG_TODO=""
for n in $LEGACY_PLUGINS; do [ -f "$EFX/$n" ] && { LEG_TODO="$LEG_TODO $n"; say "Alt-Effekt   : $n wird entfernt (frueherer Testeffekt)"; }; done
if [ "$JAR_DO" = 0 ] && [ -z "$PLUG_TODO" ] && [ -z "$LEG_TODO" ]; then
    say "Alles ist schon aktuell - nichts zu tun. (Nichts veraendert, kein Neustart.)"
    finish 0
fi

# ---- Sicherung auf die SD ----
JARS_BEFORE=$(jars_snapshot)
sd_rw
backup_dir_new || fail "Die SD-Karte ist nicht beschreibbar - ohne Sicherung wird nichts veraendert."
ensure_full_backup || fail "Die einmalige Komplett-Sicherung der Jars (backup/original) ist misslungen."
backup_file "$JAR" ambient_fx.jar || fail "Sicherung der bisherigen Jar misslungen."
backup_car_plugins || fail "Sicherung der bisherigen Effekte misslungen."
backup_stand
if [ -f "$JAR" ] || [ "$(car_plugin_count)" -gt 0 ]; then say "Sicherung: $BKP"; else say "(Es gab vorher nichts zu sichern - Vermerk dazu: $BKP/STAND.txt)"; fi
[ -n "$BKP_SAME" ] && say "  (dieselbe Jar, Nr. $BKP_JARNR, ist schon gesichert in: $BKP_SAME)"

# ---- Installieren ----
car_rw
[ -d "$EFX" ] || { mkdir -p "$EFX" 2>/dev/null || fail "Ordner ambientfx_effects konnte nicht angelegt werden."; say "Ordner ambientfx_effects angelegt."; }
if [ "$JAR_DO" = 1 ]; then
    copy_verify "$SD_JAR" "$JAR" || fail "Jar kopieren/pruefen misslungen (Platz? Rechte?)."
    say "OK  ambient_fx.jar  [$(cksum2 "$JAR")]"
fi
for n in $PLUG_TODO; do
    copy_verify "$SD_PLUG/$n" "$EFX/$n" || fail "$n kopieren/pruefen misslungen (die Jar ist evtl. schon neu - bitte 'Status' ansehen)."
    say "OK  $n  [$(cksum2 "$EFX/$n")]"
done
for n in $LEG_TODO; do
    rm -f "$EFX/$n" 2>/dev/null; [ -f "$EFX/$n" ] && fail "$n nicht entfernbar."
    say "OK  entfernt: $n"
done

# macOS-Muell im Effekte-Ordner (der gehoert uns): entfernen, damit dort wirklich nur die Plugins liegen
clean_junk "$EFX"
[ "$CLEANED" = 0 ] || say "macOS-Muell im Ordner ambientfx_effects entfernt: $CLEANED Datei(en)"

# ---- Pruefung: alles noch einmal aus dem Auto zuruecklesen und mit der SD vergleichen ----
say "PRUEFUNG (aus dem Auto zurueckgelesen):"
[ "$(cksum2 "$JAR")" = "$SD_JS" ] && say "  Jar     : [$SD_JS] = SD" || fail "Pruefung: Jar im Auto weicht von der SD ab."
for n in $(plug_names); do
    [ "$(cksum2 "$EFX/$n")" = "$(plug_sum "$n")" ] || fail "Pruefung: $n im Auto weicht von der SD ab."
done
say "  Effekte : alle $NPL Plugin-Dateien = SD"
for n in $LEGACY_PLUGINS; do [ ! -e "$EFX/$n" ] || fail "Pruefung: alter Testeffekt $n noch da."; done
say "  Alt-Effekte momentumgds/momentumgdsselect: nicht vorhanden"
CNT=$(car_plugin_count)
if [ "$CNT" = "$NPL" ]; then say "  Kontrollpunkt: genau $CNT .ambiente-Dateien im Auto"
else
    warn "$CNT .ambiente-Dateien im Auto, erwartet $NPL - es liegen fremde Plugin-Dateien dabei (nicht angefasst):"
    for f in "$EFX"/*.ambiente; do [ -f "$f" ] || continue; is_ours_plugin "${f##*/}" || say "     ${f##*/}"; done
fi
[ "$(jars_snapshot)" = "$JARS_BEFORE" ] && say "  Andere Jars im Ordner: unveraendert" || fail "Pruefung: andere Dateien im Jar-Ordner haben sich veraendert!"
JUNK_E=$(list_junk_in "$EFX"); JUNK_J=$(list_junk_in "$JARS")
[ -z "$JUNK_E" ] && say "  macOS-Reste im Ordner ambientfx_effects: keine" || fail "Pruefung: macOS-Reste im Ordner ambientfx_effects ($JUNK_E)."
[ -z "$JUNK_J" ] && say "  macOS-Reste im Jar-Ordner: keine" || warn "macOS-Reste im Jar-Ordner (nicht von uns, nicht angefasst): $(echo $JUNK_J)"
for f in "$JAR.afxnew" "$EFX"/*.afxnew; do [ ! -e "$f" ] || fail "Pruefung: Zwischendatei uebrig ($f)."; done
say "--------------------------------------------------------------------"
finish_reboot 15
