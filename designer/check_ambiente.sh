#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
#
# DE: Prueft .ambiente-Dateien (z. B. aus dem Design Studio) am PC, BEVOR sie ins Fahrzeug kommen: laedt sie mit dem Original-Plugin-Lader und
#     zeigt ein paar berechnete Farben. Braucht build/ambient_fx.jar (./build_java.sh) und vendor/lsd.jar.
# EN: Checks .ambiente files (e.g. from the Design Studio) on your computer BEFORE they go into the vehicle: loads them with the original plugin
#     loader and prints a few computed colours. Needs build/ambient_fx.jar (./build_java.sh) and vendor/lsd.jar.
#
# Aufruf / usage:  designer/check_ambiente.sh mein-design.ambiente [weitere.ambiente ...]
#
set -e
cd "$(dirname "$0")/.."
[ "$#" -ge 1 ] || { echo "Aufruf / usage: designer/check_ambiente.sh datei.ambiente [...]"; exit 2; }
JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
LSD_JAR="${LSD_JAR:-vendor/lsd.jar}"
[ -f build/ambient_fx.jar ] || { echo "build/ambient_fx.jar fehlt - erst ./build_java.sh / missing - run ./build_java.sh first"; exit 1; }
[ -f "$LSD_JAR" ] || { echo "lsd.jar nicht gefunden / not found: $LSD_JAR"; exit 1; }
for f in "$@"; do [ -f "$f" ] || { echo "Datei nicht gefunden / file not found: $f"; exit 1; }; done
# Dateien mit absoluten Pfaden uebergeben (wir wechseln ins Projektverzeichnis)
ARGS=()
for f in "$@"; do ARGS+=("$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"); done
W="$(mktemp -d "${TMPDIR:-/tmp}/afxcheck.XXXXXX")"
trap '[ -n "$W" ] && rm -rf "$W"' EXIT
"$JAVA_HOME/bin/javac" -nowarn -cp "build/ambient_fx.jar:$LSD_JAR" -d "$W" designer/tests/LoaderCheck.java 2>/dev/null
"$JAVA_HOME/bin/java" -cp "$W:build/ambient_fx.jar:$LSD_JAR" LoaderCheck "${ARGS[@]}"
