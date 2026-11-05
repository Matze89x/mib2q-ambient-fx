#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - BACKUP (auf dem MMI im Telnet ausfuehren, VOR der Installation)
# Sichert das komplette jars/-Verzeichnis + lsd.sh auf die SD-Karte nach
#   <SD>/ambientfx_backup/      (bewusst NEBEN dem Projektordner, damit ein
#                                erneutes Rueberziehen des Projekts es nicht loescht)
# Aufruf:  . /net/mmx/fs/sda0/mib2q-ambient-fx/deploy/device/00_backup.sh
# Werkzeuge: die RCC hat kein cut/sync/cmp - wie die MIB-Toolbox holen wir uns
# fehlende Tools von der MMX ueber qnet (gleiche CPU, gleiches QNX).
PATH=$PATH:/net/mmx/bin:/net/mmx/usr/bin:/net/mmx/mnt/app/armle/bin:/net/mmx/mnt/app/armle/usr/bin
export PATH
SD=${SD:-/net/mmx/fs/sda0}
JARS=/net/mmx/mnt/app/eso/hmi/lsd/jars
DST=$SD/ambientfx_backup
echo "== AmbientFX backup -> $DST"
mount -uw $SD 2>/dev/null
mkdir -p $DST/jars || { echo "FEHLER: SD-Karte nicht beschreibbar ($SD)"; return 1 2>/dev/null; exit 1; }
cp -R $JARS/. $DST/jars/ || { echo "FEHLER: cp jars"; return 1 2>/dev/null; exit 1; }
cp /net/mmx/mnt/app/eso/hmi/lsd/lsd.sh $DST/lsd.sh
sync 2>/dev/null
echo "-- Original:"; ls -la $JARS
echo "-- Backup:";   ls -la $DST/jars
echo "-- Byte-Vergleich Original vs Backup (cmp):"
BAD=0
for f in $JARS/*; do
  n=${f##*/}
  if cmp $f $DST/jars/$n >/dev/null 2>&1; then
    echo "  OK   $n"
  else
    echo "  DIFF $n"
    BAD=1
  fi
done
[ $BAD = 0 ] && echo "== Backup fertig - alle Dateien identisch." || echo "== ACHTUNG: Backup unvollstaendig - NICHT installieren!"
