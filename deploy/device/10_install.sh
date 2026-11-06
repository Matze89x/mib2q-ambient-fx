#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - INSTALL (auf dem MMI im Telnet ausfuehren)
# Kopiert build/ambient_fx.jar aus dem Projektordner auf der SD-Karte nach
# /mnt/app/eso/hmi/lsd/jars/ und vergleicht die Pruefsummen.
# Aufruf:  . /net/mmx/fs/sda0/mib2q-ambient-fx/deploy/device/10_install.sh
# Werkzeuge: die RCC hat kein cut/sync/cmp - wie die MIB-Toolbox holen wir uns
# fehlende Tools von der MMX ueber qnet (gleiche CPU, gleiches QNX).
PATH=$PATH:/net/mmx/bin:/net/mmx/usr/bin:/net/mmx/mnt/app/armle/bin:/net/mmx/mnt/app/armle/usr/bin
export PATH
SD=${SD:-/net/mmx/fs/sda0}
BASE=$SD/mib2q-ambient-fx
SRC=$BASE/build/ambient_fx.jar
JARS=/net/mmx/mnt/app/eso/hmi/lsd/jars
echo "== AmbientFX install"
[ -f $SRC ] || { echo "FEHLER: $SRC nicht gefunden (Projektordner auf der SD?)"; return 1 2>/dev/null; exit 1; }
[ -d $SD/ambientfx_backup/jars ] || { echo "FEHLER: kein Backup unter $SD/ambientfx_backup - erst 00_backup.sh"; return 1 2>/dev/null; exit 1; }
set -- `cksum $SRC`; SUM_SD="$1 $2"
if [ -f $BASE/build/ambient_fx.jar.cksum ]; then
  read SUM_EXP < $BASE/build/ambient_fx.jar.cksum
else
  SUM_EXP="(keine .cksum-Datei - mit der Zahl vom Mac vergleichen)"
fi
echo "-- Pruefsumme SD:       $SUM_SD"
echo "-- Pruefsumme erwartet: $SUM_EXP"
if [ -f $BASE/build/ambient_fx.jar.cksum ] && [ "$SUM_SD" != "$SUM_EXP" ]; then
  echo "FEHLER: Jar auf der SD ist beschaedigt/unvollstaendig - NICHT installieren"
  return 1 2>/dev/null; exit 1
fi
mount -uw /net/mmx/mnt/app || { echo "FEHLER: mount -uw /net/mmx/mnt/app"; return 1 2>/dev/null; exit 1; }
cp $SRC $JARS/ambient_fx.jar || { echo "FEHLER: cp"; mount -ur /net/mmx/mnt/app; return 1 2>/dev/null; exit 1; }
chmod 755 $JARS/ambient_fx.jar
sync 2>/dev/null
set -- `cksum $JARS/ambient_fx.jar`; SUM_DST="$1 $2"
echo "-- Pruefsumme Ziel:     $SUM_DST"
ls -la $JARS/ambient_fx.jar
mount -ur /net/mmx/mnt/app
sync 2>/dev/null
if [ "$SUM_SD" != "$SUM_DST" ]; then
  echo "FEHLER: Ziel-Pruefsumme weicht ab - 90_rollback.sh ausfuehren, NICHT neu starten"
  return 1 2>/dev/null; exit 1
fi
echo "== Installiert (Pruefsummen gleich). Jetzt 30 s warten, dann KOMPLETTEN MMI-Neustart."
