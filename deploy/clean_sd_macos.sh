#!/bin/bash
# Matze89x in cooperation with DEB and LuKa_dev - free software for the community, must NEVER be sold.
#
# clean_sd_macos.sh [--eject] [SD-Pfad]      (auf dem MAC)
# Entfernt den Muell, den macOS beim Kopieren auf FAT/exFAT-Karten anlegt: "._*"-Begleitdateien (AppleDouble), .DS_Store, .Spotlight-V100, .fseventsd, .Trashes,
# .TemporaryItems, .AppleDouble, .apdisk. Das MMI braucht nichts davon; die Toolbox und unsere Dateien bleiben unberuehrt. Mit --eject wird die Karte danach ausgeworfen.
# Hinweis: macOS legt .fseventsd/.Spotlight-V100 beim Auswerfen manchmal sofort neu an - harmlos fuers MMI; dieses Skript noch einmal laufen lassen, wenn es stoert.
SD="/Volumes/MIBTOOL"; EJECT=0
for a in "$@"; do case "$a" in --eject) EJECT=1 ;; *) SD="$a" ;; esac; done
[ -d "$SD" ] || { echo "FEHLER: $SD gibt es nicht." >&2; exit 1; }
# nie ausserhalb eines unter /Volumes eingehaengten Datentraegers arbeiten
case "$SD" in /Volumes/?*) ;; *) echo "FEHLER: nur Pfade unter /Volumes/<Karte> erlaubt." >&2; exit 1 ;; esac
[ "$SD" != "/Volumes/Macintosh HD" ] || { echo "FEHLER: das ist die Systemplatte." >&2; exit 1; }
count() { find "$SD" \( -name '._*' -o -name '.DS_Store' -o -name '.Spotlight-V100' -o -name '.fseventsd' -o -name '.Trashes' -o -name '.TemporaryItems' -o -name '.AppleDouble' -o -name '.apdisk' \) 2>/dev/null | wc -l | tr -d ' '; }
BEFORE="$(count)"
for pass in 1 2; do
    find "$SD" \( -name '._*' -o -name '.DS_Store' -o -name '.Spotlight-V100' -o -name '.fseventsd' -o -name '.Trashes' -o -name '.TemporaryItems' -o -name '.AppleDouble' -o -name '.apdisk' \) -prune -exec rm -rf {} + 2>/dev/null
    sync; sleep 2
done
LEFT="$(count)"
echo "macOS-Muell: $BEFORE gefunden, $LEFT uebrig."
[ "$LEFT" = 0 ] || find "$SD" \( -name '._*' -o -name '.DS_Store' -o -name '.Spotlight-V100' -o -name '.fseventsd' -o -name '.Trashes' -o -name '.TemporaryItems' -o -name '.AppleDouble' -o -name '.apdisk' \) 2>/dev/null | head -10 | sed 's/^/   uebrig: /'
if [ "$EJECT" = 1 ]; then diskutil eject "$SD" && echo "Karte ausgeworfen."; fi
