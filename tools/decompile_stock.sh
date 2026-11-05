#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Decompiliert die drei Audi-Klassen, die AmbientFX ersetzt, aus DEINER eigenen lsd.jar nach java_stock/.
#     Diese Dateien sind Audi-/e.solutions-Code und werden NICHT mitgeliefert und NICHT ins Git eingecheckt (.gitignore).
# EN: Decompiles the three Audi classes that AmbientFX replaces from YOUR OWN lsd.jar into java_stock/.
#     These files are Audi / e.solutions code: they are NOT shipped and NOT committed (.gitignore).
#
# Aufruf / Usage:
#   LSD_JAR=/pfad/zu/lsd.jar VINEFLOWER=/pfad/zu/vineflower-1.12.0.jar tools/decompile_stock.sh
#
# Optional: VF_JAVA=/pfad/zu/java (Vineflower braucht Java 17+ / needs Java 17+; Standard: "java")
#
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LSD_JAR="${LSD_JAR:-$ROOT/vendor/lsd.jar}"
VINEFLOWER="${VINEFLOWER:-$ROOT/vendor/decompilers/vineflower-1.12.0.jar}"
VF_JAVA="${VF_JAVA:-java}"

[ -f "$LSD_JAR" ]    || { echo "FEHLER/ERROR: lsd.jar nicht gefunden / not found: $LSD_JAR (siehe docs/BAUEN_BUILD.md)"; exit 1; }
[ -f "$VINEFLOWER" ] || { echo "FEHLER/ERROR: Vineflower nicht gefunden / not found: $VINEFLOWER (siehe docs/BAUEN_BUILD.md)"; exit 1; }

CLASSES="
de/audi/tghu/car/hmi/evohigh/CarScreenBag5
de/audi/app/car/evo/light/IntLightComponentEvo
de/audi/app/car/core/light/IntLightRange2DBusiness
"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/out"
for c in $CLASSES; do
    unzip -q -o "$LSD_JAR" "$c.class" "$c\$*.class" -d "$TMP/classes" 2>/dev/null || unzip -q -o "$LSD_JAR" "$c.class" -d "$TMP/classes" \
        || { echo "FEHLER/ERROR: $c.class nicht in lsd.jar / not in lsd.jar (andere Firmware? / other firmware?)"; exit 1; }
done

"$VF_JAVA" -jar "$VINEFLOWER" -dgs=1 -hdc=0 --add-external="$LSD_JAR" "$TMP/classes" "$TMP/out" >/dev/null

rm -rf "$ROOT/java_stock"; mkdir -p "$ROOT/java_stock"
cp -R "$TMP/out/de" "$ROOT/java_stock/de"
# Vineflower schreibt die verschachtelte Klasse als "IntLightSetEvaluator.SingleZoneIdentity"; in der aus dem JXE konvertierten lsd.jar heisst sie
# aber "IntLightSetEvaluator$SingleZoneIdentity" (ein Klassenname). Sonst kompiliert die Datei nicht.
# Vineflower writes the nested class as "IntLightSetEvaluator.SingleZoneIdentity"; in the JXE-converted lsd.jar it is the single class name
# "IntLightSetEvaluator$SingleZoneIdentity". Without this fix the file does not compile.
perl -pi -e 's/IntLightSetEvaluator\.SingleZoneIdentity/IntLightSetEvaluator\$SingleZoneIdentity/g; s/(?<![\w\$.])SingleZoneIdentity(?=\s+\w)/IntLightSetEvaluator\$SingleZoneIdentity/g' \
    "$ROOT/java_stock/de/audi/app/car/evo/light/IntLightComponentEvo.java"
for c in $CLASSES; do [ -f "$ROOT/java_stock/$c.java" ] || { echo "FEHLER/ERROR: $c.java wurde nicht erzeugt / was not produced"; exit 1; }; done
echo "OK: java_stock/ erzeugt / created ($(echo $CLASSES | wc -w | tr -d ' ') Klassen/classes)"
echo "Weiter / Next: tools/prepare_sources.sh"
