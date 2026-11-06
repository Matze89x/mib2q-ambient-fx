#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - Build-Skript
#
# Kompiliert java_patch/ gegen das originale lsd.jar (aus jxe2jar) und baut
# build/ambient_fx.jar. Ziel: Java 1.2 Bytecode fuer die J9-VM des MHI2Q.
#
# Danach: tools/verify_bytecode.py vergleicht die ersetzten Stock-Klassen
# (ohne unsere Aenderungen) gegen das Original - siehe README.
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENDOR_DIR="$SCRIPT_DIR/vendor"

JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"

LSD_JAR="${LSD_JAR:-$VENDOR_DIR/lsd.jar}"
OSGI_LIBS="$VENDOR_DIR/osgi"
OSGI_CP="$OSGI_LIBS/org.osgi.framework-1.10.0.jar:$OSGI_LIBS/org.osgi.util.tracker-1.5.4.jar"

SRC_DIR="$SCRIPT_DIR/java_patch"
BUILD_DIR="$SCRIPT_DIR/build"
OUTPUT_DIR="$BUILD_DIR/classes"
OUTPUT_JAR="$BUILD_DIR/ambient_fx.jar"

echo "=== AmbientFX Java Builder ==="

if [ ! -x "$JAVAC" ]; then
    echo "ERROR: javac not found at $JAVAC (JDK 8 needed)"
    exit 1
fi
if [ ! -f "$LSD_JAR" ]; then
    echo "ERROR: lsd.jar not found at $LSD_JAR (put it in vendor/lsd.jar or set LSD_JAR=...)"
    exit 1
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR" "$BUILD_DIR"

# Quellen einsammeln (Pfade mit Leerzeichen -> @argfile mit Anfuehrungszeichen)
SOURCES_LIST=$(mktemp)
find "$SRC_DIR" -name '*.java' -type f -print0 | xargs -0 -I{} printf '"%s"\n' {} > "$SOURCES_LIST"
FILE_COUNT=$(wc -l < "$SOURCES_LIST" | tr -d ' ')
echo "Compiling $FILE_COUNT files (target 1.2)..."

"$JAVAC" -source 1.2 -target 1.2 \
    -cp "$LSD_JAR:$OSGI_CP" \
    -sourcepath "$SRC_DIR" \
    -d "$OUTPUT_DIR" \
    -encoding UTF-8 \
    -Xlint:-options \
    @"$SOURCES_LIST"
rm -f "$SOURCES_LIST"

cd "$OUTPUT_DIR"
"$JAR" cf "$OUTPUT_JAR" .
cd "$SCRIPT_DIR"

# POSIX-cksum daneben ablegen: 10_install.sh vergleicht damit auf dem Geraet
cksum "$OUTPUT_JAR" | awk '{print $1, $2}' > "$OUTPUT_JAR.cksum"

echo ""
echo "Output: $OUTPUT_JAR"
echo "cksum:  $(cat "$OUTPUT_JAR.cksum")"
echo "Classes:"
find "$OUTPUT_DIR" -name '*.class' | sed "s|$OUTPUT_DIR/||" | sort
