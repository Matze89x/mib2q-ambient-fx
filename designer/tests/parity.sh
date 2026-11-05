#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Vergleicht die JavaScript-Engine des Design Studios mit der Java-Klasse DesignEffect: gleiche Designs, gleiche Eingaben, Takt fuer Takt.
#     Ablauf: 1) ./build_java.sh && ./designer/build_runtime.sh   2) Design Studio oeffnen, designer/tests/parity_snippet.js in die Browser-Konsole
#     einfuegen (laedt parity_js.json herunter)   3) designer/tests/parity.sh parity_js.json
# EN: Compares the Design Studio's JavaScript engine with the Java class DesignEffect: same designs, same inputs, tick by tick.
#     Steps: 1) ./build_java.sh && ./designer/build_runtime.sh   2) open the Design Studio, paste designer/tests/parity_snippet.js into the browser
#     console (downloads parity_js.json)   3) designer/tests/parity.sh parity_js.json
set -e
cd "$(dirname "$0")/../.."
[ "$#" -eq 1 ] && [ -f "$1" ] || { echo "Aufruf / usage: designer/tests/parity.sh parity_js.json"; exit 2; }
JSON="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
LSD_JAR="${LSD_JAR:-vendor/lsd.jar}"
[ -f build/designer/de/mk/ambientfx/designer/DesignEffect.class ] || { echo "erst ./designer/build_runtime.sh / run ./designer/build_runtime.sh first"; exit 1; }
CP="build/designer:build/ambient_fx.jar:$LSD_JAR"
W="$(mktemp -d "${TMPDIR:-/tmp}/afxparity.XXXXXX")"
trap '[ -n "$W" ] && rm -rf "$W"' EXIT
mkdir -p "$W/par" "$W/cls"
python3 -I - "$JSON" "$W/par" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1]))
for c in d["cases"]:
    open("%s/%s.properties" % (sys.argv[2], c["name"]), "w").write(c["props"])
    if c["usePal"]:
        open("%s/%s.pal" % (sys.argv[2], c["name"]), "w").write("1")
print(len(d["cases"]), "Faelle / cases")
PYEOF
"$JAVA_HOME/bin/javac" -nowarn -cp "$CP" -d "$W/cls" designer/tests/ParityTest.java 2>/dev/null
"$JAVA_HOME/bin/java" -cp "$W/cls:$CP" ParityTest "$W/par" > "$W/java.txt"
python3 -I - "$JSON" "$W/java.txt" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1]))
java = {}
for line in open(sys.argv[2]):
    n, i, r, g, b = line.split()
    java.setdefault(n, {})[int(i)] = (int(r), int(g), int(b))
bad = 0
ticks = 0
for c in d["cases"]:
    mx = 0
    for i, row in enumerate(c["rows"]):
        j = java[c["name"]][i]
        dd = max(abs(row[k] - j[k]) for k in range(3))
        mx = max(mx, dd)
        ticks += 1
        if dd > 1:
            bad += 1
    print("%-18s max. Abweichung / max deviation: %d" % (c["name"], mx))
print("Takte / ticks: %d, Abweichung > 1: %d" % (ticks, bad))
sys.exit(1 if bad else 0)
PYEOF
