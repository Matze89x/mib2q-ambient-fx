#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - Sicherheitscheck nach dem Build.
#
# Die drei ersetzten Stock-Klassen werden methodenweise gegen das Original in
# lsd.jar verglichen. Nur die hier aufgelisteten Methoden duerfen abweichen.
# Schlaegt der Check an, NICHT deployen - dann hat sich das Decompilat oder
# eine unbeabsichtigte Aenderung eingeschlichen.
#
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LSD_JAR="${LSD_JAR:-$SCRIPT_DIR/vendor/lsd.jar}"

python3 "$SCRIPT_DIR/tools/verify_bytecode.py" --jar "$LSD_JAR" "$SCRIPT_DIR/build/classes" \
    de/audi/tghu/car/hmi/evohigh/CarScreenBag5 \
    de/audi/app/car/evo/light/IntLightComponentEvo \
    de/audi/app/car/core/light/IntLightRange2DBusiness \
    --expect "cARCARSETTINGSINTLIGHTMAIN,initBusiness,initModels,updateIntLightActiveProfile,updateIntLightViewOptions,updateIntLightRGBColorListTotalNumberOfElements,responseIntLightRGBColorListRA0,deinit,processKeyTyped"

echo ""
echo "Class-Versionen (muessen 46 = Java 1.2 sein):"
bad=0
while IFS= read -r -d '' f; do
    v=$(javap -v "$f" | grep 'major version' | awk '{print $3}')
    if [ "$v" != "46" ]; then
        echo "  FALSCH: $f -> $v"
        bad=1
    fi
done < <(find "$SCRIPT_DIR/build/classes" -name '*.class' -print0)
[ $bad = 0 ] && echo "  alle ok"
exit $bad
