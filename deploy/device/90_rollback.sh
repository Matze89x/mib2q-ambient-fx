#!/bin/sh
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
#
# AmbientFX - ROLLBACK (auf dem MMI im Telnet ausfuehren)
# Entfernt ambient_fx.jar wieder. Danach kompletter MMI-Neustart -> Stock-Zustand.
# Aufruf:  . /net/mmx/fs/sda0/mib2q-ambient-fx/deploy/device/90_rollback.sh
# Werkzeuge: die RCC hat kein cut/sync/cmp - wie die MIB-Toolbox holen wir uns
# fehlende Tools von der MMX ueber qnet (gleiche CPU, gleiches QNX).
PATH=$PATH:/net/mmx/bin:/net/mmx/usr/bin:/net/mmx/mnt/app/armle/bin:/net/mmx/mnt/app/armle/usr/bin
export PATH
JARS=/net/mmx/mnt/app/eso/hmi/lsd/jars
echo "== AmbientFX rollback"
mount -uw /net/mmx/mnt/app || { echo "FEHLER: mount -uw"; return 1 2>/dev/null; exit 1; }
rm -f $JARS/ambient_fx.jar
rm -f /net/mmx/mnt/app/root/hooks/ambientfx.state
sync 2>/dev/null
ls -la $JARS
mount -ur /net/mmx/mnt/app
sync 2>/dev/null
echo "== Entfernt. 30 s warten, dann KOMPLETTEN MMI-Neustart."
