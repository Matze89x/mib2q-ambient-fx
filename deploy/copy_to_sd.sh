#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - Projektordner auf die SD-Karte kopieren (auf dem Mac ausfuehren)
#
# Macht dasselbe wie "Ordner im Finder rueberziehen", nur sauber:
#   - Ziel: <SD>/mib2q-ambient-fx/   (Root der Karte, NEBEN Toolbox/)
#   - laesst build/classes, java_stock und macOS-Muell (.DS_Store, ._*) weg
#   - loescht NICHTS ausserhalb von <SD>/mib2q-ambient-fx/
#   - innerhalb davon bleibt alles, was nur auf der Karte entsteht, unangetastet und
#     wird nie geloescht/ueberschrieben: backup/ und log/ (vom SD-Menue geschrieben),
#     MANIFEST.txt, NO_AUTO_REBOOT, ALLOW_DOWNGRADE (siehe deploy/build_sd_package.sh)
#   - .ambiente-Plugin-Dateien reisen ganz normal mit ueber build/plugins/
#     (wie ambient_fx.jar auch) - KEIN eigener effects/-Ordner auf der Karte,
#     die Karte ist nur Transport, der echte Ablageort ist immer das MIB
#     selbst (siehe EFFEKTE_HANDBUCH.md)
#   - prueft danach die Pruefsumme der Jar auf der Karte
#
# Aufruf:  deploy/copy_to_sd.sh [--dry-run] /Volumes/<SD-KARTE>
#          --dry-run: zeigt nur, was rsync tun wuerde (schreibt nichts)
#
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJ="$(cd "$SCRIPT_DIR/.." && pwd)"
DRY=0; SD=""
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; *) SD="$a" ;; esac; done

if [ -z "$SD" ] || [ ! -d "$SD" ]; then
    echo "Aufruf: $0 [--dry-run] /Volumes/<SD-KARTE>"
    echo "Gefundene Volumes:"; ls /Volumes
    exit 1
fi
if [ ! -f "$PROJ/build/ambient_fx.jar" ] || [ ! -f "$PROJ/build/ambient_fx.jar.cksum" ]; then
    echo "build/ambient_fx.jar(.cksum) fehlt - erst ./build_java.sh && ./verify.sh"
    exit 1
fi

DST="$SD/mib2q-ambient-fx"
RSYNC_OPTS=(-a --delete
    --exclude '.DS_Store' --exclude '._*' --exclude '.AppleDouble' --exclude '.apdisk' --exclude '.TemporaryItems' --exclude '.Trashes' --exclude '.Spotlight-V100' --exclude '.fseventsd' --exclude '.git'
    --exclude 'build/classes' --exclude 'build/designer' --exclude 'java_stock' --exclude 'vendor'
   
    --exclude '/backup/' --exclude '/log/' --exclude '/MANIFEST.txt' --exclude '/NO_AUTO_REBOOT' --exclude '/ALLOW_DOWNGRADE')
if [ "$DRY" = 1 ]; then
    echo "== Trockenlauf (nichts wird geschrieben): $PROJ/ -> $DST/"
    rsync -n --stats --itemize-changes "${RSYNC_OPTS[@]}" "$PROJ/" "$DST/" 2>&1 | grep -E "^(\*deleting|Number of (regular )?files|Number of created files|Number of deleted files|Total file size)" | sed 's/^/   /'
    exit 0
fi
mkdir -p "$DST"
rsync "${RSYNC_OPTS[@]}" "$PROJ/" "$DST/"
find "$DST" -name '._*' -delete 2>/dev/null || true
sync

SUM_MAC=$(cat "$PROJ/build/ambient_fx.jar.cksum")
SUM_SD=$(cksum "$DST/build/ambient_fx.jar" | awk '{print $1, $2}')
echo "Kopiert nach: $DST"
echo "cksum Mac: $SUM_MAC"
echo "cksum SD:  $SUM_SD"
if [ "$SUM_MAC" != "$SUM_SD" ]; then
    echo "FEHLER: Pruefsummen ungleich - Karte pruefen"
    exit 1
fi
echo "OK. Jetzt SD-Karte im Finder auswerfen und ins MMI (Schacht 1) stecken."
