#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
#
# DE: Vergleicht die JavaScript-Vorlagen (designer/tpl_engine.js) mit den erzeugten Java-Vorlagen (Tpl*), Takt fuer Takt, fuer zufaellige Werte-Aenderungen.
#     Voraussetzung: ./build_java.sh, python3 -I designer/tools/gen_templates.py, ./designer/build_runtime.sh (baut auch die Tpl-Klassen). macOS (osascript/JavaScriptCore) oder node.
# EN: Compares the JavaScript templates (designer/tpl_engine.js) with the generated Java templates (Tpl*), tick by tick, for random value changes.
#     Requires: ./build_java.sh, python3 -I designer/tools/gen_templates.py, ./designer/build_runtime.sh (also builds the Tpl classes). macOS (osascript/JavaScriptCore) or node.
set -e
cd "$(dirname "$0")/../.."
JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
LSD_JAR="${LSD_JAR:-vendor/lsd.jar}"
TPL=build/tpl_cls
[ -f "$TPL/de/mk/ambientfx/designer/TplMomentum.class" ] || { echo "erst ./designer/build_runtime.sh / run ./designer/build_runtime.sh first"; exit 1; }
W="$(mktemp -d "${TMPDIR:-/tmp}/afxtpl.XXXXXX")"
trap '[ -n "$W" ] && rm -rf "$W"' EXIT
mkdir -p "$W/cases" "$W/cls"
# 1) Faelle + JS-Ergebnis
python3 -I - "$W" <<'PYEOF'
import json, sys
W = sys.argv[1]
meta = open("designer/templates_params.json", encoding="utf-8").read()
engine = open("designer/tpl_engine.js", encoding="utf-8").read()
cases = open("designer/tests/tpl_cases.js", encoding="utf-8").read()
open(W + "/run.js", "w", encoding="utf-8").write("var TPL_META = " + meta + ";\n" + engine + "\nvar RESULT = JSON.stringify(" + cases.strip().rstrip(";") + ");\nRESULT;\n")
open(W + "/run_jxa.js", "w", encoding="utf-8").write("""
ObjC.import('Foundation');
var code = $.NSString.stringWithContentsOfFileEncodingError('%s/run.js', $.NSUTF8StringEncoding, null).js;
var res = (0,eval)(code);
$(res).writeToFileAtomicallyEncodingError('%s/result.json', true, $.NSUTF8StringEncoding, null);
'ok';
""" % (W, W))
PYEOF
if command -v node >/dev/null 2>&1; then
    node -e "const fs=require('fs');const r=eval(fs.readFileSync('$W/run.js','utf8'));fs.writeFileSync('$W/result.json',r)"
else
    osascript -l JavaScript "$W/run_jxa.js" >/dev/null
fi
python3 -I - "$W" <<'PYEOF'
import json, sys
W = sys.argv[1]
r = json.load(open(W + "/result.json", encoding="utf-8"))
open(W + "/cases/inputs.txt", "w").write(r["inputsText"])
for f in r["files"]:
    open("%s/cases/%s.properties" % (W, f["name"]), "w").write(f["props"])
    if f["pal"]:
        open("%s/cases/%s.pal" % (W, f["name"]), "w").write("1")
open(W + "/js.txt", "w").write(r["outText"])
print(len(r["files"]), "Faelle / cases")
PYEOF
# 2) Java-Ergebnis
CP="$LSD_JAR:build/ambient_fx.jar"
"$JAVA_HOME/bin/javac" -nowarn -cp "$CP" -d "$W/cls" designer/tests/TplEquivTest.java designer/tests/TplParityTest.java 2>/dev/null
"$JAVA_HOME/bin/java" -cp "$W/cls:$CP" TplParityTest "$TPL" "$W/cases" > "$W/java.txt"
# 3) Vergleich
python3 -I - "$W" <<'PYEOF'
import sys
W = sys.argv[1]
js = open(W + "/js.txt").read().split("\n")
jv = open(W + "/java.txt").read().split("\n")
js = [l for l in js if l]; jv = [l for l in jv if l]
if len(js) != len(jv):
    print("FEHLER: Zeilenzahl JS %d, Java %d" % (len(js), len(jv))); sys.exit(1)
worst = {}; bad = 0; big = 0
key = lambda l: (l.split()[0], int(l.split()[1]))
js.sort(key=key); jv.sort(key=key)
for a, b in zip(js, jv):
    na, ia, *ca = a.split(); nb, ib, *cb = b.split()
    if (na, ia) != (nb, ib):
        print("FEHLER: Reihenfolge", a, b); sys.exit(1)
    d = max(abs(int(x) - int(y)) for x, y in zip(ca, cb))
    if d: bad += 1
    if d > 1:
        big += 1
        if big <= 8: print("  Abweichung", na, "Takt", ia, "JS", ca, "Java", cb)
    worst[na] = max(worst.get(na, 0), d)
by = {}
for n, d in worst.items():
    k = n.split("_")[0]; by[k] = max(by.get(k, 0), d)
for k in sorted(by): print("%-15s max. Abweichung / max deviation: %d" % (k, by[k]))
cols = {}
for l in jv:
    n, i, r, g, b = l.split(); cols.setdefault(n, []).append((r, g, b))
distinct = {n: len(set(v)) for n, v in cols.items()}
diffvar = 0; nvar = 0
for n, v in cols.items():
    if "_var" in n:
        nvar += 1
        base = cols[n.split("_var")[0] + ("_default_pal" if n.endswith("_pal") else "_default_nopal")]
        if v != base: diffvar += 1
print("Pruefung gegen leeren Test: kleinste Zahl verschiedener Farben je Fall: %d; Varianten, die vom Standard abweichen: %d von %d" % (min(distinct.values()), diffvar, nvar))
print("Takte / ticks: %d, Abweichung > 0: %d, Abweichung > 1: %d" % (len(js), bad, big))
sys.exit(1 if big else 0)
PYEOF
