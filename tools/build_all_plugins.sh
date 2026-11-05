#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Baut alle mitgelieferten Effekte als .ambiente-Plugins nach build/plugins/ (Jar + ALLE Plugins immer zusammen installieren!).
# EN: Builds all bundled effects as .ambiente plugins into build/plugins/ (always install the jar AND ALL plugins together!).
#
# Voraussetzung / Requirement: ./build_java.sh ist gelaufen / has been run.
#
set -e
cd "$(dirname "$0")/.."
rm -f build/plugins/*.ambiente
E=java_patch/de/mk/ambientfx/effects
./build_plugin.sh $E/Atmosphere.java     "Atmosphere"
./build_plugin.sh $E/Drive.java          "Drive"
./build_plugin.sh $E/Momentum.java       "Momentum"
./build_plugin.sh $E/MomentumSelect.java "Momentum select"
./build_plugin.sh $E/Scroll.java         "Scroll"
echo
echo "Fertig / Done: $(ls build/plugins/*.ambiente | wc -l | tr -d ' ') Plugins in build/plugins/"
