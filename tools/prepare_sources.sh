#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Erzeugt java_patch/de/audi/... = deine decompilierten Stock-Klassen (java_stock/) + die kleinen AmbientFX-Haken aus patches/*.patch.
# EN: Creates java_patch/de/audi/... = your decompiled stock classes (java_stock/) + the small AmbientFX hooks from patches/*.patch.
#
# Danach / Then: ./build_java.sh  ->  ./verify.sh
#
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -d "$ROOT/java_stock/de/audi" ] || { echo "FEHLER/ERROR: java_stock/ fehlt / missing - erst/first: tools/decompile_stock.sh"; exit 1; }
command -v patch >/dev/null || { echo "FEHLER/ERROR: 'patch' nicht installiert / not installed"; exit 1; }

rm -rf "$ROOT/java_patch/de/audi"
mkdir -p "$ROOT/java_patch/de"
cp -R "$ROOT/java_stock/de/audi" "$ROOT/java_patch/de/audi"
for p in "$ROOT"/patches/*.patch; do
    echo "patch: $(basename "$p")"
    patch -p1 -d "$ROOT/java_patch" < "$p" || { echo "FEHLER/ERROR: $(basename "$p") passt nicht / does not apply (andere Firmware oder anderer Decompiler? / other firmware or decompiler?). Siehe docs/BAUEN_BUILD.md."; exit 1; }
done
echo "OK: java_patch/de/audi/ erzeugt / created"
echo "Weiter / Next: ./build_java.sh && ./verify.sh"
