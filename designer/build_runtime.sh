#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Baut die Laufzeit-Klasse des Design Studios (designer/src/.../DesignEffect.java) mit Java-1.2-Regeln zu build/designer/DesignEffect.class
#     und schreibt Pruefsumme + Groesse. Mit  tools/embed_designer.py  kommt die Klasse (Base64) in docs/designer/index.html.
# EN: Builds the Design Studio runtime class (designer/src/.../DesignEffect.java) with Java 1.2 rules into build/designer/DesignEffect.class
#     and prints checksum + size. With  tools/embed_designer.py  the class (Base64) goes into docs/designer/index.html.
#
# Voraussetzung / Requirement: ./build_java.sh ist gelaufen / has been run (build/ambient_fx.jar), vendor/lsd.jar vorhanden.
#
set -e
cd "$(dirname "$0")/.."
JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
LSD_JAR="${LSD_JAR:-vendor/lsd.jar}"
[ -f build/ambient_fx.jar ] || { echo "build/ambient_fx.jar fehlt - erst ./build_java.sh / missing - run ./build_java.sh first"; exit 1; }
[ -f "$LSD_JAR" ] || { echo "lsd.jar nicht gefunden / not found: $LSD_JAR"; exit 1; }
OUT=build/designer
rm -rf "$OUT"; mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" -source 1.2 -target 1.2 -encoding UTF-8 -Xlint:-options -nowarn \
    -cp "$LSD_JAR:build/ambient_fx.jar" -d "$OUT" designer/src/de/mk/ambientfx/designer/DesignEffect.java
F="$OUT/de/mk/ambientfx/designer/DesignEffect.class"
echo "Klasse / class: $F ($(wc -c < "$F" | tr -d ' ') Bytes)"
echo "SHA-256: $(shasum -a 256 "$F" | cut -d' ' -f1)"

# Effekt-Vorlagen (Tpl*) aus den Original-Effekten erzeugen und mit Java-1.2-Regeln bauen / generate effect templates (Tpl*) from the original effects and build with Java 1.2 rules
python3 -I designer/tools/gen_templates.py
TPL=build/tpl_cls
rm -rf "$TPL"; mkdir -p "$TPL"
"$JAVA_HOME/bin/javac" -source 1.2 -target 1.2 -encoding UTF-8 -Xlint:-options -nowarn \
    -cp "$LSD_JAR:build/ambient_fx.jar" -d "$TPL" build/tpl_src/de/mk/ambientfx/designer/*.java designer/src/de/mk/ambientfx/designer/TplEnv.java
for c in "$TPL"/de/mk/ambientfx/designer/*.class; do
    echo "Vorlage / template: $(basename "$c") ($(wc -c < "$c" | tr -d ' ') Bytes) SHA-256 $(shasum -a 256 "$c" | cut -d' ' -f1)"
done
