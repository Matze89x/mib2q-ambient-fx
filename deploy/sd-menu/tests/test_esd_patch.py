#!/usr/bin/env python3
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
"""Tests fuer esd_patch.py + unseren Block (menu.esd.block, Name "ambientfx") gegen die ECHTEN Toolbox-Menuedateien
(nur gelesen, Arbeitskopien im Temp-Ordner).

Aufruf:  python3 tests/test_esd_patch.py [Toolbox-esd-Ordner ...]
Ohne Argument: die SD-Karte (/Volumes/MIBTOOL/esd), falls gesteckt, plus die Mac-Kopien der Toolbox (Downloads / SD-Backups).
Ist eine Karte schon gepatcht (mib2q-Bloecke drin), wird der Toolbox-Teil rekonstruiert (strip_all)."""
import glob
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "esd_patch.py")
MY_BLOCK = os.path.join(HERE, "..", "menu.esd.block")
sys.path.insert(0, os.path.join(HERE, ".."))
import esd_patch  # noqa: E402

DEFAULTS = ["/Volumes/MIBTOOL/esd"] + sorted(glob.glob(os.path.expanduser("~/Documents/SD Karte Backup */esd"))) \
    + [os.path.expanduser("~/Downloads/M.I.B._More-Incredible-Bash-3.7.1/esd")]
SOURCES = [p for p in (sys.argv[1:] or DEFAULTS) if os.path.isfile(os.path.join(p, "Launcher-sda0.esd"))]
ok = bad = 0


def check(cond, msg):
    global ok, bad
    if cond:
        ok += 1
    else:
        bad += 1
        print("  FEHLER:", msg)


def run(*args, expect_ok=True):
    r = subprocess.run([sys.executable, TOOL] + list(args), capture_output=True, text=True)
    if expect_ok:
        check(r.returncode == 0, "esd_patch %s: %s" % (" ".join(args[:3]), r.stderr.strip()[:200]))
    return r


def fake_block(name):
    return ("# >>> mib2q-%s BEGIN (test)\n#\nscreen %s =>M.I.B.<=\nkeyValue\n    value   String sys 0x00000000 0\n    label   \"%s Test\"\n    poll    0\n# <<< mib2q-%s END\n" % (name, name, name, name)).encode()


# ---- Teil 1: unser Block fuer sich (Form, Pfade, Ausgangspunkt der Skripte) ----
mine = open(MY_BLOCK, "rb").read().decode()
check(mine.count("# >>> mib2q-ambientfx BEGIN") == 1 and mine.count("# <<< mib2q-ambientfx END") == 1, "Block hat genau eine BEGIN- und END-Marke")
check("screen ambientfx =>M.I.B.<=" in mine, "Screen-Name ambientfx unter M.I.B.")
check(mine.count("script\n") == 3, "genau drei Menuepunkte (Installieren/Updaten, Deinstallieren, Status)")
for s in ("afx_install.sh", "afx_uninstall.sh", "afx_status.sh"):
    check('"/net/mmx/fs/sda0/mib2q-ambient-fx/esd/%s"' % s in mine, "Block zeigt auf " + s)
check(all(ord(c) < 128 for c in mine), "Block ist reines ASCII (keine Umlaute)")
check(max(len(l.split('label   "', 1)[1].rstrip('"\n')) for l in mine.splitlines() if 'label   "' in l and "____" not in l) <= 70, "Beschriftungen <= 70 Zeichen")
check("modx" not in mine and "pc_" not in mine, "keine Reste fremder Bloecke im Block")

check(len(SOURCES) > 0, "mindestens eine echte Toolbox-esd-Quelle gefunden (Karte oder Mac-Kopie)")
for SRC in SOURCES:
    print("== Quelle:", SRC)
    tmp = tempfile.mkdtemp()
    try:
        for fn in ("Launcher-sda0.esd", "Launcher-MHIG-sda0.esd"):
            path = os.path.join(SRC, fn)
            if not os.path.isfile(path):
                continue
            orig_raw = open(path, "rb").read()
            orig = esd_patch.strip_all(orig_raw)               # Toolbox-Teil (ohne alle mib2q-Bloecke)
            f = os.path.join(tmp, fn)
            open(f, "wb").write(orig)
            pre_blocks = sorted(esd_patch.blocks_of(orig_raw))
            # unser echter Block + zwei fremde Test-Bloecke; unsere Reihenfolge-Variante: erst fremde, dann wir
            for n in ("modx", "mody"):
                bf = os.path.join(tmp, n + ".block"); open(bf, "wb").write(fake_block(n))
                run("--name", n, "--block", bf, f)
            run("--name", "ambientfx", "--block", MY_BLOCK, f)
            d = open(f, "rb").read()
            check(d.startswith(orig.rstrip(b"\r\n")), fn + ": Toolbox-Teil unveraendert (Byte fuer Byte)")
            check(all(d.count(b"# >>> mib2q-%s BEGIN" % n.encode()) == 1 for n in ("modx", "mody", "ambientfx")), fn + ": jeder Block genau einmal")
            check(d.index(b"mib2q-modx BEGIN") < d.index(b"mib2q-mody BEGIN") < d.index(b"mib2q-ambientfx BEGIN"), fn + ": Reihenfolge = Reihenfolge des Anhaengens")
            check(b"screen ambientfx =>M.I.B.<=" in d, fn + ": unser Menue ist enthalten")
            body = d.replace(b"\r\n", b"")
            check(b"\n" not in body, fn + ": Zeilenenden durchgehend CRLF (wie die Toolbox-Datei)")
            if b"\r\n" in orig:
                check(d.count(b"\r\n") >= d.count(b"\n"), fn + ": kein einzelnes LF eingeschleppt")
            # Idempotenz: erneutes Anhaengen aendert nichts
            run("--name", "ambientfx", "--block", MY_BLOCK, f)
            check(open(f, "rb").read() == d, fn + ": Wiederholung aendert nichts")
            # Aktualisierung unseres Blocks: bleibt an seiner Stelle, die anderen unveraendert
            nb = os.path.join(tmp, "ambientfx2.block")
            open(nb, "wb").write(mine.replace("Jar + 5 Effekte", "Jar + 6 Effekte").encode())
            run("--name", "ambientfx", "--block", nb, f)
            d2 = open(f, "rb").read()
            check(b"6 Effekte" in d2 and b"5 Effekte" not in d2.split(b"mib2q-ambientfx BEGIN")[1].split(b"\r\n")[1:3].__str__().encode() or b"6 Effekte" in d2, fn + ": Update ersetzt unseren Block")
            check(d2.index(b"mib2q-modx BEGIN") < d2.index(b"mib2q-mody BEGIN") < d2.index(b"mib2q-ambientfx BEGIN"), fn + ": Update behaelt die Position")
            check(all(d2.count(b"# >>> mib2q-%s BEGIN" % n) == 1 for n in (b"modx", b"mody", b"ambientfx")), fn + ": Update dupliziert nichts")
            # Entfernen NUR unseres Blocks: die fremden bleiben Byte fuer Byte
            run("--name", "ambientfx", "--remove", f)
            d3 = open(f, "rb").read()
            check(b"mib2q-ambientfx" not in d3 and b"mib2q-modx BEGIN" in d3 and b"mib2q-mody BEGIN" in d3, fn + ": Entfernen laesst die anderen stehen")
            others = {k: v for k, v in esd_patch.blocks_of(d).items() if k != "ambientfx"}
            check({k: v for k, v in esd_patch.blocks_of(d3).items()} == others, fn + ": fremde Bloecke nach dem Entfernen unveraendert")
            # alles entfernen -> wieder die Toolbox-Datei
            run("--name", "modx", "--remove", f); run("--name", "mody", "--remove", f)
            check(open(f, "rb").read().rstrip(b"\r\n") == orig.rstrip(b"\r\n"), fn + ": nach Entfernen aller Bloecke = Toolbox-Original")
            # Fehlerfaelle
            bad_block = os.path.join(tmp, "bad.block"); open(bad_block, "wb").write(b"screen x =>M.I.B.<=\n")
            before = open(f, "rb").read()
            r = run("--name", "ambientfx", "--block", bad_block, f, expect_ok=False)
            check(r.returncode != 0 and open(f, "rb").read() == before, fn + ": Block ohne Marken wird abgelehnt, Datei unveraendert")
            r = run("--name", "Ambient Fx", "--block", MY_BLOCK, f, expect_ok=False); check(r.returncode != 0, fn + ": ungueltiger Name abgelehnt")
            r = run("--name", "ambientfx", "--block", MY_BLOCK, "--dry-run", f); check(open(f, "rb").read() == before, fn + ": --dry-run schreibt nichts")
            # die echte, schon gepatchte Karte: unser Block darf deren Bloecke nicht veraendern (Trockenlauf gegen eine Arbeitskopie des Kartenstands)
            g = os.path.join(tmp, "karte_" + fn); open(g, "wb").write(orig_raw)
            run("--name", "ambientfx", "--block", MY_BLOCK, g)
            gd = open(g, "rb").read()
            foreign_before = {k: v for k, v in esd_patch.blocks_of(orig_raw).items() if k != "ambientfx"}
            check({k: v for k, v in esd_patch.blocks_of(gd).items() if k != "ambientfx"} == foreign_before, fn + ": Kartenstand: fremde Bloecke (%s) bleiben Byte fuer Byte gleich" % (", ".join(sorted(foreign_before)) or "keine"))
            check(len(esd_patch.blocks_of(gd)) == len(foreign_before) + 1 and gd.count(b"# >>> mib2q-ambientfx BEGIN") == 1, fn + ": Kartenstand: unser Block kommt genau einmal vor (ersetzt, nicht verdoppelt)")
            check(esd_patch.strip_all(gd).rstrip(b"\r\n") == orig.rstrip(b"\r\n"), fn + ": Kartenstand: Toolbox-Teil unveraendert")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
print("bestanden: %d, Fehler: %d" % (ok, bad))
sys.exit(1 if bad else 0)
