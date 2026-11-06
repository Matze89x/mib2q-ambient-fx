#!/usr/bin/env python3
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
#
# DE: Erzeugt aus den ORIGINAL-Effekten (java_patch/de/mk/ambientfx/effects/*.java) die Vorlagen-Klassen des Design Studios (Tpl<Name>.java):
#     jede Konstante wird zu einem Wert, der aus der Design-Datei ueberschrieben werden kann (Standard = Originalwert). Sonst bleibt der Code
#     Zeichen fuer Zeichen derselbe. So ist das Original die einzige Quelle. Ausgabe: build/tpl_src/ + designer/templates_params.json.
# EN: Generates the Design Studio template classes (Tpl<Name>.java) from the ORIGINAL effects: every constant becomes a value that the design
#     file can override (default = original value). The code is otherwise identical. The original is the single source.
#     Output: build/tpl_src/ + designer/templates_params.json.
#
# Aufruf / usage:  python3 -I designer/tools/gen_templates.py
import json
import os
import re
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import tpl_meta as M  # noqa: E402

SRC = os.path.join(ROOT, "java_patch/de/mk/ambientfx/effects")
OUT = os.path.join(ROOT, "build/tpl_src/de/mk/ambientfx/designer")
JSON_OUT = os.path.join(ROOT, "designer/templates_params.json")

CONST = re.compile(r"^(\s*)private static final (float|int|long|boolean) (\w+) = ([^;]+);([^\n]*)$", re.M)


def lit(t, v):
    v = v.strip()
    if t == "boolean":
        return v == "true"
    v = re.sub(r"[fFLl]$", "", v)
    return float(v) if t == "float" else int(v)


def jtype_fn(t):
    return {"float": "f", "long": "l", "int": "i", "boolean": "b"}[t]


def num(x):
    if isinstance(x, float) and x == int(x) and abs(x) < 1e9:
        return str(int(x)) + ".0"
    return repr(x)


def jlit(t, x):
    if x is None:
        return "0"
    if t == "float":
        return num(float(x)) + "f"
    if t == "long":
        return str(int(x)) + "L"
    return str(int(x))


def main():
    os.makedirs(OUT, exist_ok=True)
    all_params = {}
    for cls, key, label in M.EFFECTS:
        src = open(os.path.join(SRC, cls + ".java"), encoding="utf-8").read()
        params = []   # (name, type, default-literal)

        # Konstanten -> Felder
        def repl(m):
            ind, t, name, val, rest = m.groups()
            params.append((name, t, val.strip()))
            return "%sprivate %s %s = %s;%s" % (ind, t, name, val.strip(), rest)
        src = CONST.sub(repl, src)

        # Zahlen mitten im Ablauf herausloesen
        for f, old, new, extra in M.LITERALS:
            if f != cls:
                continue
            if src.count(old) != 1:
                sys.exit("FEHLER %s: Text nicht genau einmal gefunden: %r" % (cls, old[:60]))
            src = src.replace(old, new)
            for name, t, val in extra:
                if not any(p[0] == name for p in params):
                    params.append((name, t, val))
                    # Feld direkt vor dem ersten Zustandsfeld einfuegen (nach der Klassenzeile)
                    src = re.sub(r"(public class %s implements Effect \{\n)" % cls,
                                 lambda mm: mm.group(1) + "    private %s %s = %s;\n" % (t, name, val), src, count=1)

        # Fahrzeugdaten-Anbindung
        for f, old, new in M.HOOKS:
            if f != cls:
                continue
            if src.count(old) != 1:
                sys.exit("FEHLER %s: Hook nicht genau einmal gefunden: %r" % (cls, old[:60]))
            src = src.replace(old, new)

        # static -> Instanz (Methoden greifen jetzt auf die Werte zu)
        src = re.sub(r"private static (?!final)", "private ", src)

        # Takt
        if src.count("return 150;") != 1:
            sys.exit("FEHLER %s: 'return 150;' nicht genau einmal" % cls)
        src = src.replace("return 150;", "return periodMs;")

        # Kopf, Paket, Klassenname
        src = src[src.index("package de.mk.ambientfx.effects;"):]
        src = src.replace("package de.mk.ambientfx.effects;", "package de.mk.ambientfx.designer;")
        tpl = "Tpl" + cls
        src = src.replace("public class %s implements Effect {" % cls, "public class %s implements Effect {" % tpl)
        header = """/*
 * GENERIERT von designer/tools/gen_templates.py aus effects/%(cls)s.java - NICHT von Hand aendern. / GENERATED - do not edit by hand.
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
 *
 * DE: Vorlage "%(label)s" des Design Studios: derselbe Code wie der mitgelieferte Effekt, nur dass jede Konstante aus der Design-Datei
 *     (ambientfx_design.properties, Zeilen "p.NAME=Wert") ueberschrieben werden kann. Ohne Zeilen = exakt das Original.
 * EN: Design Studio template "%(label)s": same code as the bundled effect, but every constant can be overridden from the design file
 *     (lines "p.NAME=value"). No lines = exactly the original.
 */
""" % {"cls": cls, "label": label}
        src = header + src

        # Konstruktor einfuegen: alle Werte laden (mit Grenzen aus tpl_meta)
        names = [p for p in params]
        names.append(("PERIOD_MS", "int", "150"))
        loads = []
        for name, t, val in names:
            if name not in M.META:
                sys.exit("FEHLER %s: %s fehlt in tpl_meta.py" % (cls, name))
            g, lo, hi, st, unit, de, en = M.META[name]
            if name == "PERIOD_MS":
                loads.append('        periodMs = TplEnv.i(p, "PERIOD_MS", periodMs, %d, %d);' % (lo, hi))
            elif t == "boolean":
                loads.append('        %s = TplEnv.b(p, "%s", %s);' % (name, name, name))
            elif t == "float":
                loads.append('        %s = TplEnv.f(p, "%s", %s, %s, %s);' % (name, name, name, jlit("float", lo), jlit("float", hi)))
            elif t == "long":
                loads.append('        %s = TplEnv.l(p, "%s", %s, %dL, %dL);' % (name, name, name, lo, hi))
            else:
                loads.append('        %s = TplEnv.i(p, "%s", %s, %d, %d);' % (name, name, name, lo, hi))
        post = M.POST.get(cls, "")
        ctor = "\n    private int periodMs = 150;\n\n    public %s() {\n        java.util.Properties p = TplEnv.load(\"%s\");\n%s\n%s\n    }\n" % (
            tpl, key, "\n".join(loads), ("        " + post) if post else "")
        src = re.sub(r"(public class %s implements Effect \{\n)" % tpl, lambda mm: mm.group(1) + ctor, src, count=1)
        open(os.path.join(OUT, tpl + ".java"), "w", encoding="utf-8").write(src)

        plist = []
        for name, t, val in names:
            g, lo, hi, st, unit, de, en = M.META[name]
            plist.append({"name": name, "type": t, "def": lit(t, val), "group": g, "min": lo, "max": hi, "step": st,
                          "unit": unit, "de": de, "en": en})
        all_params[key] = {"class": cls, "tpl": tpl, "label": label, "params": plist}
        print("%-15s %2d Werte -> %s.java" % (cls, len(plist), tpl))

    out = {"groups": [{"id": g, "de": d, "en": e} for g, d, e in M.GROUPS], "templates": all_params}
    open(JSON_OUT, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1) + "\n")
    print("geschrieben:", JSON_OUT)


if __name__ == "__main__":
    main()
