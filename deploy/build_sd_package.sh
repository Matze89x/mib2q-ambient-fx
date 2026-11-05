#!/bin/bash
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# build_sd_package.sh [--apply] [--no-eject] [--skip-tests] [SD-Pfad]      (auf dem MAC)
#
# Bringt AmbientFX auf die M.I.B.-Toolbox-SD ("Pre-V3.7.1 - MHIG Edition") - mit SD-Menue im Gruenen Engineering-Menue (GEM): M.I.B. -> ambientfx.
# EIN Ordner auf der Karte: <SD>/mib2q-ambient-fx/ - darin liegt alles (und nur dort wird geschrieben):
#     esd/afx_*.sh            die Menue-Skripte (+ known_versions.txt, VERSION)
#     build/ambient_fx.jar    die Jar (+ .cksum), build/plugins/*.ambiente die 5 Effekte (+ build/plugins.cksum) - dieselben Dateien wie bei der Telnet-Uebergabe
#     SD-ANLEITUNG.md, TEST-ABLAUF.md, MANIFEST.txt, deploy/, DEPLOY_TELNET.md ... (der Projektordner, wie bisher via copy_to_sd.sh)
#     backup/ und log/        schreibt das Menue selbst (Sicherungen vor jeder Aenderung, Protokolle); hier NIE geloescht
# Ausserhalb dieses Ordners wird auf der Karte NUR eines angefasst: an esd/Launcher-sda0.esd und esd/Launcher-MHIG-sda0.esd wird der Block "mib2q-ambientfx" ANGEHAENGT
# (unterhalb der Bloecke anderer Mods). esd_patch.py ersetzt nur den eigenen Block; die Toolbox-Datei und fremde Bloecke bleiben Byte fuer Byte gleich.
# Vorher laufen die Tests (Schein-Auto in sh/bash/ksh/dash + ESD-Patch gegen die echten Menue-Dateien); bei Fehlern Abbruch.
# Ohne --apply passiert NICHTS auf der SD: es wird nur gezeigt, was geschehen wuerde (Trockenlauf). --apply schreibt nur unter /Volumes/<Karte>.
# Aufraeumen (macOS-Muell: ._*, .DS_Store ...) nur in unserem Ordner, nicht auf der ganzen Karte. Mit --apply wird die Karte am Ende AUTOMATISCH ausgeworfen
# (diskutil eject; --no-eject laesst sie gemountet). Klappt das Auswerfen nicht (Volume belegt), steht eine Meldung da - dann im Finder auswerfen.
#
# DAUERREGEL: nach JEDEM Build/Update: Tests -> Trockenlauf -> --apply (erst nach OK) -> danach wie gewohnt die volle Telnet-Uebergabe (Adresse/Login, mount app+system+sda0, cp, cksum, Tests).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"            # .../mib2q-ambient-fx/deploy
PROJ="$(cd "$HERE/.." && pwd)"
MENU="$HERE/sd-menu"
APPLY=0; SKIPTESTS=0; NOEJECT=0; SD=""
while [ $# -gt 0 ]; do
    case "$1" in
        --apply) APPLY=1 ;;
        --skip-tests) SKIPTESTS=1 ;;
        --no-eject) NOEJECT=1 ;;
        -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
        *) SD="$1" ;;
    esac
    shift
done
SD="${SD:-/Volumes/MIBTOOL}"
AFD="$SD/mib2q-ambient-fx"
ck() { cksum < "$1" | awk '{print $1, $2}'; }
say() { echo "$@"; }
die() { echo "FEHLER: $*" >&2; exit 1; }
ESD_FILES="Launcher-sda0.esd Launcher-MHIG-sda0.esd"
SCRIPTS="afx_lib.sh afx_install.sh afx_uninstall.sh afx_status.sh"

say "== Vorpruefung"
[ -d "$SD" ] || die "SD-Pfad $SD gibt es nicht (Volumes: $(ls /Volumes | tr '\n' ' '))."
if [ "$APPLY" = 1 ]; then
    case "$SD" in /Volumes/?*) ;; *) die "--apply schreibt nur unter /Volumes/<Karte> (angegeben: $SD)." ;; esac
    [ "$SD" != "/Volumes/Macintosh HD" ] || die "das ist die Systemplatte."
fi
[ -f "$SD/VERSION" ] && say "   Toolbox : $(head -1 "$SD/VERSION")" || die "$SD/VERSION fehlt - ist das die M.I.B.-Toolbox-SD?"
grep -q "MHIG Edition" "$SD/VERSION" || say "   HINWEIS: VERSION nennt nicht 'MHIG Edition' - das Menue funktioniert trotzdem (gleiche Menue-Dateien), bitte pruefen."
for f in $ESD_FILES; do [ -f "$SD/esd/$f" ] || die "$SD/esd/$f fehlt - Toolbox unvollstaendig?"; done
[ -f "$SD/apps/reboot" ] && say "   apps/reboot der Toolbox: vorhanden (fuer den automatischen Neustart)" || say "   HINWEIS: $SD/apps/reboot fehlt - das Menue koennte nicht neu starten (Meldung im Menue, kein Schaden)."
[ -f "$PROJ/build/ambient_fx.jar" ] && [ -f "$PROJ/build/ambient_fx.jar.cksum" ] || die "build/ambient_fx.jar(.cksum) fehlt - erst ./build_java.sh && ./verify.sh."
JAR_CK="$(ck "$PROJ/build/ambient_fx.jar")"
[ "$JAR_CK" = "$(awk '{print $1, $2}' "$PROJ/build/ambient_fx.jar.cksum")" ] || die "Pruefsumme von build/ambient_fx.jar passt nicht zu ambient_fx.jar.cksum."
BUILD_ID="$(sed -n 's/.*BUILD_ID = "\(.*\)";.*/\1/p' "$PROJ/java_patch/de/mk/ambientfx/AmbientFx.java")"
[ -n "$BUILD_ID" ] || die "BUILD_ID im Quelltext (AmbientFx.java) nicht gefunden."
BUILD_NAME="$(printf '%s' "$BUILD_ID" | sed 's/ /_/g; s/_+_/+/g')"
say "   Jar     : $BUILD_ID  [$JAR_CK]"
PLUGINS="$(cd "$PROJ/build/plugins" && ls *.ambiente 2>/dev/null)"
[ -n "$PLUGINS" ] || die "build/plugins/*.ambiente fehlt - erst ./build_plugin.sh fuer jeden Effekt."
for p in $PLUGINS; do case "$p" in .*|*[!A-Za-z0-9._-]*) die "ungueltiger Plugin-Dateiname '$p' (erlaubt: Name.ambiente, nie mit Punkt, keine Leerzeichen).";; esac; done
LOCALX="$(cd "$PROJ/build/plugins" && ls -A | grep -vxF "$PLUGINS" || true)"
[ -z "$LOCALX" ] || say "   HINWEIS: in build/plugins liegen weitere Dateien, die NICHT in die Plugin-Liste kommen und nie ins Auto gelangen: $(echo $LOCALX)"
for legacy in momentumgds.ambiente momentumgdsselect.ambiente; do echo "$PLUGINS" | grep -qx "$legacy" && die "build/plugins/$legacy ist ein alter Testeffekt - bitte entfernen (siehe README, GDS ist in Momentum aufgegangen)."; done
NPL="$(echo "$PLUGINS" | wc -l | tr -d ' ')"
[ "$NPL" = 5 ] || say "   HINWEIS: $NPL statt 5 Plugins - Anleitung/Menuebeschriftung ('5 Effekte') bitte angleichen."
say "   Plugins : $NPL  ($(echo $PLUGINS | sed 's/\.ambiente//g'))"
for f in $SCRIPTS; do for sh in bash sh dash ksh; do command -v $sh >/dev/null 2>&1 && { $sh -n "$PROJ/esd/$f" || die "Syntaxfehler in esd/$f ($sh)"; }; done; done
say "   Skripte : Syntax ok (bash, sh, dash, ksh)"
for f in SD-ANLEITUNG.md TEST-ABLAUF.md esd/known_versions.txt esd/VERSION; do [ -f "$PROJ/$f" ] || die "$f fehlt im Projektordner."; done

# bekannte Versionen: Tabelle + aktuelles Jar (gleiche Pruefsumme -> bleibt, sonst naechster Rang)
VERS_TMP="$(mktemp)"; PLUGSUM_TMP="$(mktemp)"; trap 'rm -f "$VERS_TMP" "$PLUGSUM_TMP" 2>/dev/null' EXIT
cp "$PROJ/esd/known_versions.txt" "$VERS_TMP"
if ! grep -v '^#' "$VERS_TMP" | awk -v c="$JAR_CK" '{ if ($3" "$4 == c) f=1 } END { exit f?0:1 }'; then
    RANK=$(( $(grep -v '^#' "$VERS_TMP" | awk 'BEGIN{m=0} {if ($1+0>m) m=$1+0} END{print m}') + 1 ))
    printf '%s %s %s\n' "$RANK" "$BUILD_NAME" "$JAR_CK" >> "$VERS_TMP"
    say "   Versionstabelle: neuer Eintrag Rang $RANK ($BUILD_NAME)"
else say "   Versionstabelle: Jar schon bekannt"; fi
{ echo "# mib2q-ambient-fx - Pruefsummen der Effekt-Plugins ($BUILD_ID): <cksum> <Groesse> <Name>"; for p in $PLUGINS; do echo "$(ck "$PROJ/build/plugins/$p") $p"; done; } > "$PLUGSUM_TMP"

# Tests (Schein-Auto + ESD-Patch) - schreiben nie auf die SD
if [ "$SKIPTESTS" = 1 ]; then say "   Tests   : UEBERSPRUNGEN (--skip-tests)"
else
    say "== Tests"
    T1="$(bash "$MENU/tests/test_scripts.sh" 2>&1)" || { echo "$T1" | tail -15; die "Skript-Tests fehlgeschlagen."; }
    say "   Skripte (sh bash ksh dash, Schein-Auto): $(echo "$T1" | tail -1)"
    T2="$(python3 "$MENU/tests/test_esd_patch.py" "$SD/esd" 2>&1)" || { echo "$T2" | tail -15; die "ESD-Patch-Test fehlgeschlagen."; }
    say "   ESD-Patch gegen die echten Menue-Dateien der Karte: $(echo "$T2" | tail -1)"
    T3="$(bash "$MENU/tests/test_ramlog.sh" 2>&1)" || { echo "$T3" | tail -15; die "RAM-Log-Test fehlgeschlagen."; }
    say "   RAM-Log (Marker, Obergrenze, Zuruecksetzen): $(echo "$T3" | tail -1)"
fi

# Plan
ESD_ORIG="$SD/mib2q-esd-original"        # gemeinsamer Ort fuer die Original-Menuedateien aller Mods (gehoert nicht uns)
say "== Plan"
say "   EINZIGER Ordner, den wir anlegen/beschreiben: $AFD"
say "     (backup/, log/, MANIFEST.txt, NO_AUTO_REBOOT, ALLOW_DOWNGRADE darin werden nie geloescht)"
say "   schreibe    : mib2q-ambient-fx/esd/  $SCRIPTS known_versions.txt VERSION"
say "   schreibe    : mib2q-ambient-fx/build/ambient_fx.jar (+ .cksum)  [$JAR_CK]"
for p in $PLUGINS; do say "   schreibe    : mib2q-ambient-fx/build/plugins/$p  [$(ck "$PROJ/build/plugins/$p")]"; done
say "   schreibe    : mib2q-ambient-fx/build/plugins.cksum, SD-ANLEITUNG.md, TEST-ABLAUF.md, MANIFEST.txt"
say "   ins AUTO (durch das Menue) kommt spaeter NUR: ambient_fx.jar + diese $NPL .ambiente-Dateien (Namen aus build/plugins.cksum) - nie macOS-Muell, nie Ungelistetes"
say "   Projektordner (wie bisher, Doku/Telnet-Anleitung/Quellen):"
bash "$HERE/copy_to_sd.sh" --dry-run "$SD" | sed 's/^/     /'
for e in $ESD_FILES; do
    say "   Menue       : esd/$e: $(python3 "$MENU/esd_patch.py" --name ambientfx --block "$MENU/menu.esd.block" --dry-run "$SD/esd/$e" | sed 's/^[^:]*: //')"
done
say "                 (Block wird ANS ENDE gehaengt = unterhalb der vorhandenen Bloecke; sonst bleibt die Datei Byte fuer Byte gleich)"
if [ -f "$ESD_ORIG/Launcher-sda0.esd" ] && [ -f "$ESD_ORIG/Launcher-MHIG-sda0.esd" ]; then say "   Originale   : mib2q-esd-original/ ist schon da - wird nicht angefasst"
else say "   Originale   : mib2q-esd-original/ fehlt - wird (gemeinsames Protokoll) angelegt, solange die Menuedateien noch ohne mib2q-Block sind"; fi
[ -d "$AFD/backup/original/jars" ] && say "   Komplett-Sicherung der Auto-Jars: $AFD/backup/original vorhanden (bleibt)" || say "   Komplett-Sicherung der Auto-Jars: legt das Menue beim ERSTEN Installieren in mib2q-ambient-fx/backup/original/ an"
say "   unberuehrt  : alles andere auf der Karte (Toolbox, andere Mods ...); es wird nichts geloescht ausser veralteten Dateien IN unserem Ordner (rsync --delete)"
if [ "$APPLY" != 1 ]; then say "== Trockenlauf - nichts geschrieben. Zum Ausfuehren: $0 --apply \"$SD\""; exit 0; fi

# ---- ab hier wird geschrieben ----
touch "$SD/.afx_wtest" 2>/dev/null && rm -f "$SD/.afx_wtest" || die "$SD ist nicht beschreibbar."
# lokal erzeugte Dateien (gehoeren zum Projektordner und reisen mit)
cp "$PLUGSUM_TMP" "$PROJ/build/plugins.cksum"
cp "$VERS_TMP" "$PROJ/esd/known_versions.txt"
say "== Projektordner auf die Karte (deploy/copy_to_sd.sh)"
bash "$HERE/copy_to_sd.sh" "$SD" | sed 's/^/   /' || die "copy_to_sd.sh fehlgeschlagen."
for f in $SCRIPTS; do chmod +x "$AFD/esd/$f" 2>/dev/null || true; done
bash "$HERE/clean_sd_macos.sh" "$AFD" | sed 's/^/   /' || true
# Originale der Toolbox-Menuedateien einmal sichern - nur, solange sie fehlen und noch kein mib2q-Block drinsteht (gemeinsames Protokoll)
for e in $ESD_FILES; do
    if [ ! -f "$ESD_ORIG/$e" ] && ! grep -aq '# >>> mib2q-' "$SD/esd/$e"; then mkdir -p "$ESD_ORIG"; cp "$SD/esd/$e" "$ESD_ORIG/$e"; fi
done
python3 "$MENU/esd_patch.py" --name ambientfx --block "$MENU/menu.esd.block" "$SD/esd/Launcher-sda0.esd" "$SD/esd/Launcher-MHIG-sda0.esd" || die "Menue-Dateien konnten nicht angepasst werden (nichts geschrieben)."
sync
echo "== Pruefung (Pruefsummen SD <-> Quelle)"
BAD=0
chk() { A="$(ck "$1")"; B="$(ck "$2")"; if [ "$A" = "$B" ]; then echo "   OK   $3  [$A]"; else echo "   FEHLER $3: $A <> $B"; BAD=1; fi; }
for f in $SCRIPTS known_versions.txt VERSION; do chk "$PROJ/esd/$f" "$AFD/esd/$f" "esd/$f"; done
chk "$PROJ/build/ambient_fx.jar" "$AFD/build/ambient_fx.jar" build/ambient_fx.jar
chk "$PROJ/build/ambient_fx.jar.cksum" "$AFD/build/ambient_fx.jar.cksum" build/ambient_fx.jar.cksum
for p in $PLUGINS; do chk "$PROJ/build/plugins/$p" "$AFD/build/plugins/$p" "build/plugins/$p"; done
chk "$PROJ/build/plugins.cksum" "$AFD/build/plugins.cksum" build/plugins.cksum
# exakte Inhaltsliste der Ordner, aus denen das Menue ins Auto kopiert (auch versteckte Dateien): nichts Fremdes, kein macOS-Muell
EXP_PL="$(echo "$PLUGINS" | LC_ALL=C sort)"; HAVE_PL="$(cd "$AFD/build/plugins" && ls -A | LC_ALL=C sort)"
if [ "$EXP_PL" = "$HAVE_PL" ]; then echo "   OK   build/plugins auf der Karte enthaelt EXAKT die $NPL .ambiente-Dateien (kein ._*, kein .DS_Store)"; else echo "   FEHLER build/plugins auf der Karte enthaelt Anderes: $(echo $HAVE_PL)"; BAD=1; fi
EXP_B="$(printf 'ambient_fx.jar\nambient_fx.jar.cksum\nplugins\nplugins.cksum\n' | LC_ALL=C sort)"; HAVE_B="$(cd "$AFD/build" && ls -A | LC_ALL=C sort)"
if [ "$EXP_B" = "$HAVE_B" ]; then echo "   OK   build/ auf der Karte: nur Jar, .cksum, plugins/, plugins.cksum"; else echo "   FEHLER build/ auf der Karte enthaelt Anderes: $(echo $HAVE_B)"; BAD=1; fi
JUNK_N="$(find "$AFD" \( -name '._*' -o -name '.DS_Store' -o -name '.AppleDouble' \) | wc -l | tr -d ' ')"
[ "$JUNK_N" = 0 ] && echo "   OK   kein macOS-Muell in mib2q-ambient-fx" || { echo "   FEHLER $JUNK_N macOS-Muell-Dateien in mib2q-ambient-fx"; BAD=1; }
for d in SD-ANLEITUNG.md TEST-ABLAUF.md; do chk "$PROJ/$d" "$AFD/$d" "$d"; done
for e in $ESD_FILES; do
    grep -aq "screen ambientfx =>M.I.B.<=" "$SD/esd/$e" && echo "   OK   Menue in esd/$e (Block ambientfx: $(grep -ac '# >>> mib2q-ambientfx BEGIN' "$SD/esd/$e")x, andere Bloecke: $(grep -a '# >>> mib2q-' "$SD/esd/$e" | grep -av ambientfx | wc -l | tr -d ' '))" || { echo "   FEHLER Menue in esd/$e"; BAD=1; }
done
[ "$BAD" = 0 ] || die "Pruefung fehlgeschlagen."
# Liste der fuer das Menue wichtigen Dateien (Pruefsumme, Groesse, Pfad) in UNSEREN Ordner. Die zwei Toolbox-Menuedateien stehen NICHT mit einer Dateipruefsumme drin:
# sie enthalten Bloecke mehrerer Mods und aendern sich, sobald ein anderes Projekt seinen Block anhaengt. Stabil ist nur UNSER Block - dessen Pruefsumme steht dort.
blk_ck() { python3 -c 'import sys; sys.path.insert(0, sys.argv[2]); import esd_patch; sys.stdout.buffer.write(esd_patch.blocks_of(open(sys.argv[1], "rb").read()).get("ambientfx", b""))' "$1" "$MENU" | cksum | awk '{print $1, $2}'; }
( cd "$SD" && { echo "# mib2q-ambient-fx - Dateien, die dieses Paket auf die SD-Karte gebracht hat (cksum = POSIX-Pruefsumme, Groesse in Byte)"; \
  echo "# Stand beim Bauen: $BUILD_ID. Der Projektordner enthaelt ausserdem Quelltexte/Doku (siehe deploy/copy_to_sd.sh); hier die fuer das Menue wichtigen Dateien:"; \
  { find mib2q-ambient-fx/esd mib2q-ambient-fx/build -type f ! -name '._*'; for f in SD-ANLEITUNG.md TEST-ABLAUF.md DEPLOY_TELNET.md; do echo "mib2q-ambient-fx/$f"; done; } | LC_ALL=C sort | while IFS= read -r f; do printf '%s  %s\n' "$(cksum < "$f" | awk '{print $1" "$2}')" "$f"; done; \
  echo "#"; \
  echo "# Toolbox-Menuedateien esd/Launcher-sda0.esd und esd/Launcher-MHIG-sda0.esd: hier bewusst KEINE Dateipruefsumme - Stand beim Bauen, aendert sich, wenn ein anderes"; \
  echo "# Mod seinen Block anhaengt oder aktualisiert. Stabil ist nur unser Block (zwischen '# >>> mib2q-ambientfx BEGIN' und"; \
  echo "# '# <<< mib2q-ambientfx END'); seine Pruefsumme (Stand beim Bauen):"; \
  for e in $ESD_FILES; do printf '%s  Block ambientfx in esd/%s\n' "$(blk_ck "$SD/esd/$e")" "$e"; done; \
  echo "# Originale der Toolbox-Menuedateien: mib2q-esd-original/ (gemeinsam fuer alle Mods)"; } > mib2q-ambient-fx/MANIFEST.txt )
echo "   Liste der hinzugefuegten Dateien: mib2q-ambient-fx/MANIFEST.txt"
echo "== macOS-Muell nur in unserem Ordner entfernen (._*, .DS_Store ...)"
bash "$HERE/clean_sd_macos.sh" "$AFD" || true
for e in $ESD_FILES; do rm -f "$SD/esd/._$e" 2>/dev/null || true; done
echo "== Fertig. Karte in SD-Slot 1 stecken, im GEM: M.I.B. > ambientfx."
if [ "$NOEJECT" = 1 ]; then echo "   (--no-eject: Karte bleibt gemountet - bitte selbst auswerfen)"
else
    sync; sleep 1
    EJ=0; for try in 1 2 3; do if diskutil eject "$SD" >/dev/null 2>&1; then EJ=1; break; fi; sleep 3; done
    if [ "$EJ" = 1 ]; then echo "== Karte AUSGEWORFEN - kann abgezogen und ins MMI (Slot 1) gesteckt werden."
    else echo "HINWEIS: Auswerfen hat nicht geklappt (Volume belegt?) - bitte im Finder auswerfen, erst dann abziehen."; fi
fi
