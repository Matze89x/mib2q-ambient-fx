#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Beweis "exakt wie das Original": laesst jede Effekt-Vorlage (Tpl*) gegen die ECHTE mitgelieferte Effekt-Klasse laufen (TplEquivTest).
#     Voraussetzung: ./build_java.sh und ./designer/build_runtime.sh.
# EN: Proof "exactly like the original": runs every effect template (Tpl*) against the REAL bundled effect class (TplEquivTest).
#     Requires ./build_java.sh and ./designer/build_runtime.sh.
set -e
cd "$(dirname "$0")/../.."
JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
LSD_JAR="${LSD_JAR:-vendor/lsd.jar}"
[ -f build/tpl_cls/de/mk/ambientfx/designer/TplMomentum.class ] || { echo "erst ./designer/build_runtime.sh / run ./designer/build_runtime.sh first"; exit 1; }
W="$(mktemp -d "${TMPDIR:-/tmp}/afxeq.XXXXXX")"
trap '[ -n "$W" ] && rm -rf "$W"' EXIT
CP="build/ambient_fx.jar:$LSD_JAR"
"$JAVA_HOME/bin/javac" -nowarn -cp "$CP" -d "$W" designer/tests/TplEquivTest.java 2>/dev/null
"$JAVA_HOME/bin/java" -cp "$W:$CP" TplEquivTest build/tpl_cls
