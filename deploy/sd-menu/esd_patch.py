#!/usr/bin/env python3
# Matze89x in cooperation with DEB and LuKa_dev - free software for the community, must NEVER be sold.
"""esd_patch.py - haengt den Menue-Block EINES Mods an die ESD-Menuedateien der M.I.B.-Toolbox an (oder ersetzt/entfernt ihn) - ohne die Bloecke anderer Projekte anzufassen.

  python3 esd_patch.py --name modx --block menu.esd.block  <SD>/esd/Launcher-sda0.esd <SD>/esd/Launcher-MHIG-sda0.esd
  python3 esd_patch.py --name modx --remove                 <ESD-Dateien>
  python3 esd_patch.py --name mody --block menu.esd.block --dry-run <ESD-Dateien>        (zeigt nur, was passieren wuerde)

Regeln (gelten fuer ALLE Mods, damit sie sich auf derselben Karte vertragen):
  * Ein Block steht zwischen den Zeilen "# >>> mib2q-<name> BEGIN ..." und "# <<< mib2q-<name> END" (davor eine Zeile aus 91 "#" als Trenner). <name> ist klein, a-z0-9-.
  * Der eigene Block wird an Ort und Stelle ersetzt, existiert er noch nicht, ans ENDE der Datei gehaengt. Fremde Bloecke und die Toolbox-Datei bleiben Byte fuer Byte gleich.
  * Zeilenenden: wie die Toolbox-Datei (CRLF).
  * Gegenprobe vor dem Schreiben: Original ohne alle mib2q-Bloecke ist unveraendert, jeder fremde Block ist unveraendert, der eigene kommt genau einmal vor. Sonst Abbruch, nichts geschrieben.
  * Geschrieben wird atomar (temporaere Datei + Umbenennen).
"""
import argparse
import os
import re
import sys

RULE = b"#" * 91
NAME_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")


def block_re(name=None):
    n = re.escape(name).encode() if name else rb"([a-z0-9][a-z0-9-]*)"
    # optionaler Trenner davor; Block bis einschliesslich der END-Zeile (+ Zeilenende)
    return re.compile(rb"(?:#{91}\r?\n)?# >>> mib2q-" + n + rb" BEGIN.*?\r?\n# <<< mib2q-" + (rb"\1" if not name else n) + rb" END[^\r\n]*(?:\r?\n|$)", re.S)


def blocks_of(data):
    return {m.group(1).decode(): m.group(0) for m in block_re().finditer(data)}


def strip_all(data):
    return block_re().sub(b"", data)


def normalize_eol(text, eol=b"\r\n"):
    return text.replace(b"\r\n", b"\n").replace(b"\n", eol)


def build_block(block_file, name):
    raw = open(block_file, "rb").read()
    raw = raw.replace(b"\r\n", b"\n")
    if (b"# >>> mib2q-%s BEGIN" % name.encode()) not in raw or (b"# <<< mib2q-%s END" % name.encode()) not in raw:
        sys.exit("FEHLER: %s muss '# >>> mib2q-%s BEGIN' und '# <<< mib2q-%s END' enthalten." % (block_file, name, name))
    body = raw.lstrip(b"\n")
    if body.startswith(RULE):                      # Trenner nicht doppeln
        body = body[len(RULE):].lstrip(b"\n")
    if not body.endswith(b"\n"):
        body += b"\n"
    return RULE + b"\n" + body


def patch(data, name, block, remove=False):
    own = block_re(name)
    base_before, others_before = strip_all(data), {k: v for k, v in blocks_of(data).items() if k != name}
    if remove:
        new = own.sub(b"", data)
    else:
        eol_block = normalize_eol(block, b"\r\n" if b"\r\n" in data else b"\n")
        if own.search(data):
            new = own.sub(lambda m: eol_block, data, count=1)
        else:
            tail = data if data.endswith((b"\n", b"\r\n")) else data + (b"\r\n" if b"\r\n" in data else b"\n")
            new = tail + (b"\r\n" if b"\r\n" in data else b"\n") + eol_block
    # Gegenprobe
    assert strip_all(new) == base_before or strip_all(new).rstrip(b"\r\n") == base_before.rstrip(b"\r\n"), "Toolbox-Teil veraendert"
    nb = blocks_of(new)
    assert {k: v for k, v in nb.items() if k != name} == others_before, "fremder Block veraendert"
    assert len(block_re(name).findall(new)) == (0 if remove else 1), "eigener Block nicht genau einmal"
    return new


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--name", required=True)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--block")
    g.add_argument("--remove", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("files", nargs="+")
    a = ap.parse_args()
    if not NAME_RE.match(a.name):
        sys.exit("FEHLER: --name nur a-z, 0-9, Bindestrich.")
    block = None if a.remove else build_block(a.block, a.name)
    for f in a.files:
        data = open(f, "rb").read()
        try:
            new = patch(data, a.name, block, a.remove)
        except AssertionError as exc:
            sys.exit("FEHLER %s: Gegenprobe fehlgeschlagen (%s) - nichts geschrieben." % (f, exc))
        others = sorted(k for k in blocks_of(new) if k != a.name)
        print("%s: %d -> %d Byte, Block '%s' %s, fremde Bloecke: %s" % (os.path.basename(f), len(data), len(new), a.name, "entfernt" if a.remove else "gesetzt", ", ".join(others) or "keine"))
        if a.dry_run or new == data:
            continue
        tmp = f + ".mibtmp"
        with open(tmp, "wb") as fh:
            fh.write(new)
        os.replace(tmp, f)


if __name__ == "__main__":
    main()
