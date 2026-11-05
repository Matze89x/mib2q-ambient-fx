#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - baut eine einzelne Effekt-Quelldatei zu einer ".ambiente"
# Plugin-Datei (siehe EffectPluginLoader.java fuer das Format).
#
# Aufruf:
#   ./build_plugin.sh java_patch/de/mk/ambientfx/effects/MeinEffekt.java "Mein Effekt"
#
# Voraussetzung: ./build_java.sh muss vorher schon mal gelaufen sein
# (build/ambient_fx.jar liefert Effect/Rgb/VehicleData/... als Klassenpfad
# fuers Kompilieren - die muessen NICHT mit in die .ambiente-Datei, die
# findet der Plugin-Classloader zur Laufzeit beim Parent-Classloader).
#
# Ergebnis: build/plugins/<name>.ambiente - von dort auf die SD-Karte nach
# <SD>/mib2q-ambient-fx/effects/ kopieren.
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENDOR_DIR="$SCRIPT_DIR/vendor"

JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"

LSD_JAR="${LSD_JAR:-$VENDOR_DIR/lsd.jar}"
CORE_JAR="$SCRIPT_DIR/build/ambient_fx.jar"

SRC="$1"
LABEL="$2"

if [ -z "$SRC" ] || [ -z "$LABEL" ]; then
    echo "Aufruf: $0 <pfad/zur/EffektKlasse.java> \"<Anzeigename im Menue>\""
    exit 1
fi
if [ ! -f "$SRC" ]; then
    echo "ERROR: $SRC nicht gefunden"
    exit 1
fi
if [ ! -f "$CORE_JAR" ]; then
    echo "ERROR: $CORE_JAR fehlt - erst ./build_java.sh laufen lassen"
    exit 1
fi
if [ ! -f "$LSD_JAR" ]; then
    echo "ERROR: lsd.jar nicht gefunden ($LSD_JAR)"
    exit 1
fi

CLASS_NAME=$(basename "$SRC" .java)
# java_patch/de/mk/ambientfx/effects/MeinEffekt.java -> de.mk.ambientfx.effects.MeinEffekt
FQN=$(echo "$SRC" | sed -E 's#^.*java_patch/##; s#/#.#g; s#\.java$##')

# Vertragsversion direkt aus Effect.java lesen (einzige Quelle der Wahrheit,
# siehe Effect.CONTRACT_VERSION) - nie hier im Skript hart eintragen, sonst
# droht Drift zwischen Java-Konstante und gestempelter Version.
EFFECT_JAVA="$SCRIPT_DIR/java_patch/de/mk/ambientfx/Effect.java"
CONTRACT_VERSION=$(grep -oE 'CONTRACT_VERSION *= *[0-9]+' "$EFFECT_JAVA" | grep -oE '[0-9]+')
if [ -z "$CONTRACT_VERSION" ]; then
    echo "ERROR: CONTRACT_VERSION nicht in $EFFECT_JAVA gefunden"
    exit 1
fi

OUT_DIR=$(mktemp -d)
echo "Kompiliere $SRC ..."
"$JAVAC" -source 1.2 -target 1.2 \
    -cp "$LSD_JAR:$CORE_JAR" \
    -sourcepath "$SCRIPT_DIR/java_patch" \
    -d "$OUT_DIR" \
    -encoding UTF-8 \
    -Xlint:-options \
    "$SRC"

cat > "$OUT_DIR/plugin.properties" <<EOF
class=$FQN
label=$LABEL
version=$CONTRACT_VERSION
EOF

PLUGIN_DIR="$SCRIPT_DIR/build/plugins"
mkdir -p "$PLUGIN_DIR"
OUT_NAME=$(echo "$CLASS_NAME" | tr '[:upper:]' '[:lower:]')
OUT_FILE="$PLUGIN_DIR/$OUT_NAME.ambiente"

( cd "$OUT_DIR" && "$JAR" cf "$OUT_FILE" . )
rm -rf "$OUT_DIR"

echo ""
echo "Plugin gebaut: $OUT_FILE"
echo "class=$FQN"
echo "label=$LABEL"
echo "version=$CONTRACT_VERSION"
echo ""
echo "Naechster Schritt: ./deploy/copy_to_sd.sh /Volumes/<SD-KARTE>"
echo "(build/plugins/ reist automatisch mit, kein eigener Ordner noetig)"
echo "Danach per Telnet von der SD-Karte aufs MIB kopieren - siehe EFFEKTE_HANDBUCH.md Abschnitt 6."
