#!/bin/bash
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# test_ramlog.sh - baut RamLog.java mit Java-1.2-Regeln (-source/-target 1.2) und laesst den Test laufen (Marker, RAM-Datei, Obergrenze).
# Braucht JDK 8 (wie build_java.sh). Aufruf: bash deploy/sd-menu/tests/test_ramlog.sh
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
JH="$(/usr/libexec/java_home -v 1.8 2>/dev/null)" || { echo "JDK 8 nicht gefunden (wird fuer diesen Test gebraucht)"; exit 1; }
W="$(mktemp -d "${TMPDIR:-/tmp}/ramlogtest.XXXXXX")"
trap 'rm -rf "$W"' EXIT
mkdir -p "$W/cls" "$W/work"
"$JH/bin/javac" -nowarn -source 1.2 -target 1.2 -d "$W/cls" "$ROOT/java_patch/de/mk/ambientfx/RamLog.java" 2>"$W/err1" || { cat "$W/err1"; echo "RamLog.java laesst sich mit Java-1.2-Regeln nicht uebersetzen"; exit 1; }
# Log wird mit uebersetzt; was Log sonst noch braucht, findet javac ueber -sourcepath
"$JH/bin/javac" -nowarn -source 1.2 -target 1.2 -d "$W/cls" -cp "$W/cls" -sourcepath "$ROOT/java_patch" "$ROOT/java_patch/de/mk/ambientfx/Log.java" 2>"$W/err2" || { cat "$W/err2"; echo "Log.java nicht uebersetzbar"; exit 1; }
"$JH/bin/javac" -nowarn -d "$W/cls" -cp "$W/cls" "$HERE/RamLogTest.java" 2>"$W/err3" || { cat "$W/err3"; exit 1; }
"$JH/bin/java" -cp "$W/cls" de.mk.ambientfx.RamLogTest "$W/work"
