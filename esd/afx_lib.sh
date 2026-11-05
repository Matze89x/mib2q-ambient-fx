#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# afx_lib.sh - gemeinsame Funktionen der SD-Menue-Skripte von mib2q-ambient-fx
# (Gruenes Engineering-Menue "GEM" > M.I.B. > ambientfx). Wird von jedem afx_*.sh
# mit "." eingelesen. Nur POSIX/ksh (QNX 6.5 /bin/sh ist pdksh): keine Arrays,
# kein [[ ]], kein "local", im Auto kein awk/sed/tr/cut voraussetzen.
# Zum Testen auf dem Mac laeuft dasselbe mit AFX_TEST=<Ordner> (ein Schein-Auto
# unter <Ordner>/car, keine mount-Aufrufe) - siehe tests/test_scripts.sh.
#
# Was die Skripte aendern (und nichts sonst):
#   /mnt/app/eso/hmi/lsd/jars/ambient_fx.jar
#   /mnt/app/eso/hmi/lsd/ambientfx_effects/*.ambiente   (die Effekt-Plugins)
#   /mnt/persist/ambientfx/ambientfx*.state             (nur beim Deinstallieren)
#   /mnt/app/root/hooks/ambientfx.state                 (alter Pfad, nur beim Deinstallieren)
# Vor JEDER Aenderung wird eine Kopie auf die SD-Karte gelegt
# (mib2q-ambient-fx/backup/<Zeitstempel>_<Menuepunkt>/), jede Kopie wird per
# Pruefsumme (cksum) kontrolliert. Fremde Dateien (andere Jars, andere Mods)
# werden nie angefasst - und das wird nach jeder Aenderung nachgewiesen.
# Auf der SD-Karte wird AUSSCHLIESSLICH in unserem Ordner mib2q-ambient-fx geschrieben (backup/, log/); sonst nirgends.

if [ -z "$AFX_TEST" ]; then
    export PATH=.:/proc/boot:/bin:/usr/bin:/usr/sbin:/sbin:/mnt/app/media/gracenote/bin:/mnt/app/armle/bin:/mnt/app/armle/sbin:/mnt/app/armle/usr/bin:/mnt/app/armle/usr/sbin
    export LD_LIBRARY_PATH=/lib:/mnt/app/root/lib-target:/eso/lib:/mnt/app/usr/lib:/mnt/app/armle/lib:/mnt/app/armle/lib/dll:/mnt/app/armle/usr/lib
    unset LD_PRELOAD
    # Das GEM laeuft auf dem MMX. Wer das Skript von Hand auf dem RCC startet (Telnet Port 123), wird hier an den MMX weitergereicht.
    if [ ! -d /mnt/app/eso/hmi/lsd ] && [ -d /net/mmx/mnt/app/eso/hmi/lsd ] && [ -z "$AFX_FORWARDED" ]; then
        AFX_FORWARDED=1; export AFX_FORWARDED
        exec on -f mmx /bin/sh "$0" "$@"
    fi
fi

# ---- Pfade --------------------------------------------------------------------------------------------------------------------------------
AFX_ESD_DIR=${AFX_ESD_DIR:-$(cd "$(dirname "$0")" && pwd)}         # .../mib2q-ambient-fx/esd
AFX_DIR=${AFX_DIR:-${AFX_ESD_DIR%/*}}                               # .../mib2q-ambient-fx  = UNSER EINZIGER Ordner auf der SD (Jar, Plugins, Skripte, backup/, log/)
SDROOT=${SDROOT:-${AFX_DIR%/*}}                                     # Einhaengepunkt der SD-Karte (z. B. /net/mmx/fs/sda0)
CAR=${AFX_TEST:+$AFX_TEST/car}                                      # leer = das echte Auto
LSD=$CAR/mnt/app/eso/hmi/lsd
JARS=$LSD/jars
JAR=$JARS/ambient_fx.jar
EFX=$LSD/ambientfx_effects
HOOKS=$CAR/mnt/app/root/hooks
HOOKS_STATE=$HOOKS/ambientfx.state                                  # alter Pfad der ersten Version
PERSIST=$CAR/mnt/persist/ambientfx                                  # heutiger Ablageort des gemerkten Effekts (ambientfx.state, ambientfx_key<N>.state)
SD_JAR=$AFX_DIR/build/ambient_fx.jar                                # Jar + Plugins liegen "wie bisher" unter build/ (dieselben Dateien wie bei der Telnet-Uebergabe)
SD_JARSUM=$AFX_DIR/build/ambient_fx.jar.cksum
SD_PLUG=$AFX_DIR/build/plugins
SD_PLUGSUM=$AFX_DIR/build/plugins.cksum                             # Zeilen "<cksum> <groesse> <name>.ambiente" (vom Builder erzeugt)
SD_VERSIONS=$AFX_ESD_DIR/known_versions.txt
SD_FULLBACKUP=$AFX_DIR/backup/original                              # einmalige Komplett-Sicherung von jars/ + lsd.sh (wie deploy/device/00_backup.sh) - IN unserem Ordner
LEGACY_PLUGINS="momentumgds.ambiente momentumgdsselect.ambiente"   # fruehere Testeffekte (bis 30.09.2026), werden beim Installieren entfernt

# ---- Ausgabe + Protokoll ------------------------------------------------------------------------------------------------------------------
AFX_NAME=${AFX_NAME:-afx}
AFX_LOG=${AFX_TEST:-/tmp}/afx_$AFX_NAME.log
: > "$AFX_LOG" 2>/dev/null
AFX_RC=0
say()  { echo "$*"; echo "$*" >> "$AFX_LOG" 2>/dev/null; }
warn() { say "HINWEIS: $*"; }
afx_banner() {
    say "===================================================================="
    say "mib2q-ambient-fx: $1"
    say "===================================================================="
}

# ---- Einhaengen ---------------------------------------------------------------------------------------------------------------------------
# Jedes Dateisystem wird nur dann beschreibbar eingehaengt, wenn es das nicht schon ist - und am Ende nur das wieder schreibgeschuetzt,
# was wir selbst geaendert haben.
MNT_CHANGED=""
mnt_rw() { # <Einhaengepunkt>
    [ -n "$AFX_TEST" ] && return 0
    if touch "$1/.afx_wtest" 2>/dev/null; then rm -f "$1/.afx_wtest" 2>/dev/null; return 0; fi
    mount -uw "$1" 2>/dev/null && MNT_CHANGED="$MNT_CHANGED $1"
    return 0
}
sd_rw()      { mnt_rw "$SDROOT"; }
car_rw()     { mnt_rw /mnt/app; }
persist_rw() { mnt_rw /mnt/persist; }
mnt_restore() { # <alles|auto>: "auto" laesst die SD beschreibbar (die Toolbox-Neustartfunktion verlangt eine beschreibbare SD)
    [ -n "$AFX_TEST" ] && return 0
    sync
    for mr_m in $MNT_CHANGED; do
        if [ "$1" = auto ] && [ "$mr_m" = "$SDROOT" ]; then continue; fi
        mount -ur "$mr_m" 2>/dev/null
    done
    [ "$1" = auto ] || MNT_CHANGED=""
    return 0
}
# save_log: Protokoll auf die SD. log/<Punkt>.txt = immer der LETZTE Lauf; gab es in diesem Lauf eine Sicherung (BKP_NR), liegt dasselbe Protokoll zusaetzlich als
# log/<NNN>_<Punkt>.txt (gleiche Nummer wie der Sicherungsordner) - so geht kein frueherer Lauf verloren und nichts wird ueberschrieben.
save_log() {
    mkdir -p "$AFX_DIR/log" 2>/dev/null || return 0
    cp "$AFX_LOG" "$AFX_DIR/log/$AFX_NAME.txt" 2>/dev/null
    if [ -n "$BKP_NR" ]; then cp "$AFX_LOG" "$AFX_DIR/log/${BKP_NR}_$AFX_NAME.txt" 2>/dev/null; fi
    return 0
}

# Ende ohne Neustart: Protokoll auf die SD kopieren (nur zur Ablage, Fehler egal), Einhaengepunkte zurueck, mit Rueckgabewert beenden
finish() {
    AFX_RC=${1:-$AFX_RC}
    case $AFX_RC in 0) say "ERGEBNIS: OK" ;; 1) say "ERGEBNIS: ABGEBROCHEN" ;; *) say "ERGEBNIS: MIT HINWEISEN (Code $AFX_RC)" ;; esac
    save_log
    mnt_restore alles
    case $AFX_RC in 0) say "--- fertig ---" ;; 1) say "--- ABGEBROCHEN (Details oben) ---" ;; *) say "--- fertig, aber mit Hinweisen oben (Code $AFX_RC) ---" ;; esac
    exit "$AFX_RC"
}
fail() {
    say "FEHLER: $*"
    if [ -n "$BKP" ]; then say "Sicherung des vorherigen Stands: $BKP (danach bitte 'Status' ansehen)"; else say "Es wurde nichts veraendert."; fi
    finish 1
}

# Ende MIT Neustart (nur nach erfolgreicher Pruefung): nach <Sekunden> startet das MMI von selbst neu (Toolbox: apps/reboot -t <Sek>). Abschaltbar durch die Datei
# mib2q-ambient-fx/NO_AUTO_REBOOT auf der SD. Die SD bleibt bis zum Neustart beschreibbar (apps/reboot bricht sonst mit "SD write protected" ab).
finish_reboot() {
    RB_SEC=${1:-15}
    say "ERGEBNIS: OK - alle Pruefsummen stimmen"
    if [ -f "$AFX_DIR/NO_AUTO_REBOOT" ]; then
        say "Automatischer Neustart ist AUS (Datei NO_AUTO_REBOOT auf der SD)."
        say "Bitte das MMI selbst neu starten (Power-Taste ca. 10 s halten)."
        save_log; mnt_restore alles; say "--- fertig ---"; exit 0
    fi
    say "Das MMI startet in $RB_SEC Sekunden von selbst NEU."
    say "Nicht abbrechen, Karte stecken lassen, Neustart dauert ca. 1 Minute."
    save_log
    mnt_restore auto
    if [ -n "$AFX_TEST" ]; then echo "reboot $RB_SEC" > "$AFX_TEST/reboot_called"; exit 0; fi
    for rb_f in flash backup rccd svm vim ambient; do
        if [ -f "/net/rcc/dev/shmem/$rb_f.mib" ]; then say "Ein anderer Vorgang laeuft noch ($rb_f) - kein Neustart. Bitte spaeter selbst neu starten."; exit 0; fi
    done
    if [ ! -f "$SDROOT/apps/reboot" ]; then say "apps/reboot der Toolbox fehlt - bitte das MMI selbst neu starten (Power-Taste ca. 10 s)."; exit 0; fi
    sync; sync; sync
    GEM=1; export GEM
    "$SDROOT/apps/reboot" -t "$RB_SEC"
    exit 0
}

# ---- Pruefsummen, Dateien -----------------------------------------------------------------------------------------------------------------
# cksum2 <Datei>: "Pruefsumme Groesse" (ohne Dateinamen, ohne awk - gibt es auf dem MMI nicht ueberall)
cksum2() {
    [ -f "$1" ] || { echo ""; return 1; }
    set -- $(cksum "$1" 2>/dev/null)
    echo "$1 $2"
}

# copy_verify <Quelle> <Ziel>: ueber eine Zwischendatei kopieren, Pruefsumme vergleichen, erst dann an den Zielort schieben. Rueckgabe 0 = Ziel ist identisch.
# Die Zwischendatei endet auf ".afxnew" (nie ".ambiente" - das Plugin-Verzeichnis darf keine halbfertige Datei als Effekt sehen).
copy_verify() {
    cv_src=$1; cv_dst=$2
    cv_tmp="$cv_dst.afxnew"
    rm -f "$cv_tmp" 2>/dev/null
    cp "$cv_src" "$cv_tmp" 2>/dev/null || { rm -f "$cv_tmp" 2>/dev/null; return 1; }
    cv_a=$(cksum2 "$cv_src"); cv_b=$(cksum2 "$cv_tmp")
    if [ -z "$cv_a" ] || [ "$cv_a" != "$cv_b" ]; then rm -f "$cv_tmp" 2>/dev/null; return 1; fi
    chmod 644 "$cv_tmp" 2>/dev/null
    mv -f "$cv_tmp" "$cv_dst" 2>/dev/null || { rm -f "$cv_tmp" 2>/dev/null; return 1; }
    [ "$(cksum2 "$cv_dst")" = "$cv_a" ]
}

# jars_snapshot: "Name Pruefsumme" aller Dateien im Jar-Ordner AUSSER unserer (Beweis, dass andere Mods unberuehrt bleiben).
# Log-Dateien (*.log, *.log.*) sind bewusst ausgenommen: andere Mods (z. B. fremdmod.jar -> fremdmod.log) schreiben im Betrieb in den Jar-Ordner,
# die Datei waechst zwischen "vorher" und "nachher" - das waere sonst ein FALSCHER Fehlalarm. Fremde Log-Dateien werden nie angefasst/geloescht/als Rest gewertet.
jars_snapshot() {
    [ -d "$JARS" ] || return 0
    for js_f in "$JARS"/*; do
        [ -f "$js_f" ] || continue
        js_b=${js_f##*/}
        case $js_b in ambient_fx.jar|*.afxnew|*.log|*.log.*) continue ;; esac
        echo "$js_b $(cksum2 "$js_f")"
    done
}

# ---- Effekt-Plugins (.ambiente) -----------------------------------------------------------------------------------------------------------
# plug_names: Namen der Plugins aus plugins.cksum (3. Feld), eine pro Zeile
plug_names() {
    [ -f "$SD_PLUGSUM" ] || return 0
    while read -r pn_a pn_b pn_n pn_rest; do
        case $pn_a in "#"*|"") continue ;; esac
        echo "$pn_n"
    done < "$SD_PLUGSUM"
}
# plug_sum <Name>: "Pruefsumme Groesse" laut plugins.cksum (Rueckgabe 1, wenn nicht aufgefuehrt)
plug_sum() {
    [ -f "$SD_PLUGSUM" ] || return 1
    while read -r ps_a ps_b ps_n ps_rest; do
        if [ "$ps_n" = "$1" ]; then echo "$ps_a $ps_b"; return 0; fi
    done < "$SD_PLUGSUM"
    return 1
}
plug_count() { pcn=0; for pcx in $(plug_names); do pcn=$((pcn + 1)); done; echo "$pcn"; }
# is_ours_plugin <Name>: 0 = steht in plugins.cksum
is_ours_plugin() { for iop in $(plug_names); do [ "$iop" = "$1" ] && return 0; done; return 1; }
is_legacy_plugin() { for ilp in $LEGACY_PLUGINS; do [ "$ilp" = "$1" ] && return 0; done; return 1; }
# car_plugin_count: Zahl der *.ambiente im Auto
car_plugin_count() {
    cpn=0
    for cpf in "$EFX"/*.ambiente; do [ -f "$cpf" ] && cpn=$((cpn + 1)); done
    echo "$cpn"
}
# plug_name_ok <Name>: 0 = erlaubter Plugin-Dateiname: nur a-z A-Z 0-9 . _ -, endet auf ".ambiente", beginnt NICHT mit einem Punkt (also nie ._*-Muell), kein Pfad
plug_name_ok() {
    case $1 in ""|.*|*/*|*[!A-Za-z0-9._-]*) return 1 ;; esac
    case $1 in *.ambiente) return 0 ;; esac
    return 1
}
# macOS-Muell (AppleDouble ._*, .DS_Store ...): is_junk_name <Name> / list_junk_in <Ordner> / clean_junk <Ordner> (loescht dort NUR solchen Muell, setzt CLEANED)
is_junk_name() { case $1 in ._*|.DS_Store|.AppleDouble|.apdisk|.TemporaryItems|.Trashes|.Spotlight-V100|.fseventsd) return 0 ;; esac; return 1; }
list_junk_in() {
    for lj_f in "$1"/.[!.]* "$1"/..?*; do
        [ -e "$lj_f" ] || continue
        is_junk_name "${lj_f##*/}" && echo "${lj_f##*/}"
    done
}
clean_junk() {
    CLEANED=0
    for cj_f in "$1"/.[!.]* "$1"/..?*; do
        [ -e "$cj_f" ] || continue
        if is_junk_name "${cj_f##*/}"; then rm -rf "$cj_f" 2>/dev/null; [ -e "$cj_f" ] || CLEANED=$((CLEANED + 1)); fi
    done
}
# sd_plugin_extras: alles im Ordner build/plugins der SD (auch versteckte Dateien), was NICHT in plugins.cksum steht - wird nie kopiert
sd_plugin_extras() {
    for se_f in "$SD_PLUG"/* "$SD_PLUG"/.[!.]* "$SD_PLUG"/..?*; do
        [ -e "$se_f" ] || continue
        is_ours_plugin "${se_f##*/}" || echo "${se_f##*/}"
    done
}
# sd_plugins_check: 0 = alle Plugins laut plugins.cksum liegen auf der SD und stimmen; sonst PLUG_ERR
sd_plugins_check() {
    PLUG_ERR=""
    [ -f "$SD_PLUGSUM" ] || { PLUG_ERR="plugins.cksum fehlt auf der SD"; return 1; }
    [ "$(plug_count)" -gt 0 ] || { PLUG_ERR="plugins.cksum ist leer"; return 1; }
    for sp_n in $(plug_names); do
        plug_name_ok "$sp_n" || { PLUG_ERR="ungueltiger Plugin-Name '$sp_n' in plugins.cksum (erlaubt: nur Name.ambiente, nie mit Punkt)"; return 1; }
        [ -f "$SD_PLUG/$sp_n" ] || { PLUG_ERR="$sp_n fehlt im Ordner plugins auf der SD"; return 1; }
        sp_want=$(plug_sum "$sp_n"); sp_have=$(cksum2 "$SD_PLUG/$sp_n")
        [ "$sp_want" = "$sp_have" ] || { PLUG_ERR="$sp_n ist defekt (Pruefsumme $sp_have statt $sp_want)"; return 1; }
    done
    return 0
}

# ---- Versionen (known_versions.txt: "Rang Name Pruefsumme Groesse") ------------------------------------------------------------------------
# ver_lookup "<Pruefsumme> <Groesse>": 0 = bekannt, setzt VER_RANK/VER_NAME
ver_lookup() {
    VER_RANK=""; VER_NAME=""
    [ -f "$SD_VERSIONS" ] || return 1
    while read -r vl_r vl_n vl_c vl_s vl_rest; do
        case $vl_r in "#"*|"") continue ;; esac
        if [ "$vl_c $vl_s" = "$1" ]; then VER_RANK=$vl_r; VER_NAME=$vl_n; return 0; fi
    done < "$SD_VERSIONS"
    return 1
}
ver_label() { # "<Pruefsumme> <Groesse>" -> "Name" oder "unbekannt"
    if ver_lookup "$1"; then echo "$VER_NAME"; else echo "unbekannt"; fi
}

# ---- Sicherung auf der SD-Karte -----------------------------------------------------------------------------------------------------------
# backup_dir_new: legt <SD>/mib2q-ambient-fx/backup/<NNN>_<Menuepunkt>_<Jar-Nr> an und setzt BKP (leer, wenn die SD nicht beschreibbar ist).
# Die Auto-Uhr steht im GEM auf 1970 und beginnt nach jedem Neustart von vorn - deshalb steht KEINE Uhrzeit im Namen:
#   NNN    = laufende Nummer (hoechste vorhandene + 1, dreistellig) - ordnet die Sicherungen ueber alle Neustarts hinweg, wiederholt sich nie
#   Jar-Nr = Pruefsumme der Jar im Auto, die gerade gesichert wird ("ohne-jar" = keine installiert) - gleiche Nr. = gleicher Jar-Stand
# Alte Ordner (<Datum>-<Zeit>_<Punkt>) werden beim Zaehlen ignoriert und nie angefasst.
backup_dir_new() {
    BKP=""; BKP_NR=""; BKP_JARNR=""; BKP_SAME=""
    bd_max=0
    for bd_e in "$AFX_DIR"/backup/*; do
        [ -d "$bd_e" ] || continue
        bd_b=${bd_e##*/}
        case $bd_b in
            [0-9][0-9][0-9]_*)
                bd_v=${bd_b%%_*}; bd_v=${bd_v#0}; bd_v=${bd_v#0}      # ohne fuehrende Nullen (sonst gilt "008" in manchen Shells als ungueltige Oktalzahl)
                [ "$bd_v" -gt "$bd_max" ] && bd_max=$bd_v ;;
        esac
    done
    bd_new=$((bd_max + 1))
    case $bd_new in ?) bd_nr=00$bd_new ;; ??) bd_nr=0$bd_new ;; *) bd_nr=$bd_new ;; esac
    bd_jn=$(cksum2 "$JAR"); bd_jn=${bd_jn%% *}; [ -n "$bd_jn" ] || bd_jn=ohne-jar
    # gleiche Jar-Pruefsumme schon einmal gesichert? (nur Hinweis - jeder Lauf bekommt trotzdem seinen eigenen Ordner)
    if [ "$bd_jn" != ohne-jar ]; then
        for bd_e in "$AFX_DIR"/backup/*_"$bd_jn"; do [ -d "$bd_e" ] && BKP_SAME=${bd_e##*/}; done
    fi
    bd_base="$AFX_DIR/backup/${bd_nr}_${AFX_NAME}_$bd_jn"
    bd_n=0; bd_dir=$bd_base
    while [ -e "$bd_dir" ]; do bd_n=$((bd_n + 1)); bd_dir="${bd_base}-$bd_n"; done
    mkdir -p "$bd_dir" 2>/dev/null || return 1
    BKP=$bd_dir; BKP_NR=$bd_nr; BKP_JARNR=$bd_jn
    return 0
}
# backup_stand: schreibt BKP/STAND.txt - was im Auto VOR der Aenderung war (auch wenn nichts zu sichern war, ist der Ordner so nie "leer ohne Erklaerung")
backup_stand() {
    [ -n "$BKP" ] || return 0
    {
        echo "mib2q-ambient-fx Sicherung $BKP_NR ($AFX_NAME) - Stand im Auto VOR der Aenderung"
        if [ -f "$JAR" ]; then echo "Jar im Auto   : $(cksum2 "$JAR")  = $(ver_label "$(cksum2 "$JAR")")"; else echo "Jar im Auto   : keine (ambient_fx.jar war nicht installiert)"; fi
        bs_n=0
        for bs_f in "$EFX"/*.ambiente; do
            [ -f "$bs_f" ] || continue
            [ "$bs_n" = 0 ] && echo "Effekte im Auto (Pruefsumme Groesse):"
            bs_n=$((bs_n + 1)); echo "  ${bs_f##*/}  $(cksum2 "$bs_f")"
        done
        [ "$bs_n" = 0 ] && echo "Effekte im Auto: keine"
        [ -n "$BKP_SAME" ] && echo "Dieselbe Jar (Nr. $BKP_JARNR) ist schon gesichert in: $BKP_SAME"
        bs_y=$(date +%Y 2>/dev/null)
        if [ "${bs_y:-0}" -ge 2024 ] 2>/dev/null; then echo "Datum         : $(date 2>/dev/null)"; else echo "Datum         : unbekannt (Auto-Uhr nicht gestellt: $(date 2>/dev/null)) - deshalb zaehlt die laufende Nummer, nicht die Uhrzeit"; fi
    } > "$BKP/STAND.txt" 2>/dev/null
    return 0
}
# backup_file <Datei> <Pfad im Backup>: kopiert (falls vorhanden) in BKP und prueft; 0 = gesichert oder nichts zu sichern, 1 = Sicherung misslungen
backup_file() {
    [ -f "$1" ] || return 0
    [ -n "$BKP" ] || return 1
    bf_d="$BKP/$2"; bf_p=${bf_d%/*}
    mkdir -p "$bf_p" 2>/dev/null || return 1
    cp "$1" "$bf_d" 2>/dev/null || return 1
    [ "$(cksum2 "$1")" = "$(cksum2 "$bf_d")" ]
}
# backup_car_plugins: alle *.ambiente im Auto (auch fremde und die alten Testeffekte) nach BKP/ambientfx_effects/ - 0 = ok
backup_car_plugins() {
    for bc_f in "$EFX"/*.ambiente; do
        [ -f "$bc_f" ] || continue
        backup_file "$bc_f" "ambientfx_effects/${bc_f##*/}" || return 1
    done
    return 0
}

# ensure_full_backup: einmalige Komplett-Sicherung von jars/ + lsd.sh nach <SD>/mib2q-ambient-fx/backup/original/ (wie deploy/device/00_backup.sh, mit Byte-Vergleich),
# nur wenn sie noch fehlt. 0 = vorhanden oder jetzt angelegt und geprueft, 1 = misslungen (dann wird NICHTS installiert).
ensure_full_backup() {
    if [ -d "$SD_FULLBACKUP/jars" ]; then
        fb_n=0
        for fb_f in "$SD_FULLBACKUP"/jars/*; do [ -e "$fb_f" ] && fb_n=$((fb_n + 1)); done
        if [ "$fb_n" -gt 0 ]; then say "Komplett-Sicherung der Jars: schon vorhanden ($fb_n Dateien)"; return 0; fi
    fi
    say "Komplett-Sicherung (einmalig): jars/ + lsd.sh -> backup/original/"
    mkdir -p "$SD_FULLBACKUP/jars" 2>/dev/null || return 1
    cp -R "$JARS/." "$SD_FULLBACKUP/jars/" 2>/dev/null || return 1
    [ -f "$LSD/lsd.sh" ] && { cp "$LSD/lsd.sh" "$SD_FULLBACKUP/lsd.sh" 2>/dev/null || return 1; }
    fb_bad=0
    for fb_f in "$JARS"/*; do
        [ -f "$fb_f" ] || continue
        fb_b=${fb_f##*/}
        case $fb_b in *.afxnew|*.log|*.log.*) continue ;; esac     # wachsende Log-Dateien anderer Mods nicht vergleichen (siehe jars_snapshot)
        if type cmp >/dev/null 2>&1; then
            cmp "$fb_f" "$SD_FULLBACKUP/jars/$fb_b" >/dev/null 2>&1 || fb_bad=1
        else
            [ "$(cksum2 "$fb_f")" = "$(cksum2 "$SD_FULLBACKUP/jars/$fb_b")" ] || fb_bad=1
        fi
    done
    [ -f "$LSD/lsd.sh" ] && { [ "$(cksum2 "$LSD/lsd.sh")" = "$(cksum2 "$SD_FULLBACKUP/lsd.sh")" ] || fb_bad=1; }
    [ "$fb_bad" = 0 ] || return 1
    say "  ok - alle Jars + lsd.sh identisch gesichert"
    return 0
}
