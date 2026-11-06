#!/usr/bin/env python3
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
#
# embed_designer.py
#   DE: Baut docs/designer/index.html aus designer/index.src.html: setzt die Schriften (designer/fonts.css.part), die Laufzeit-Klasse
#       (build/designer/.../DesignEffect.class als Base64 + SHA-256) und die Vertragsversion (aus Effect.java) ein.
#   EN: Builds docs/designer/index.html from designer/index.src.html: inlines the fonts (designer/fonts.css.part), the runtime class
#       (build/designer/.../DesignEffect.class as Base64 + SHA-256) and the contract version (from Effect.java).
#
# Aufruf / usage:   ./designer/build_runtime.sh && python3 tools/embed_designer.py
import base64
import hashlib
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read_text(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def read_bytes(rel):
    with open(os.path.join(ROOT, rel), "rb") as f:
        return f.read()


src = read_text("designer/index.src.html")
fonts = read_text("designer/fonts.css.part")
cls = read_bytes("build/designer/de/mk/ambientfx/designer/DesignEffect.class")
m = re.search(r"CONTRACT_VERSION\s*=\s*(\d+)", read_text("java_patch/de/mk/ambientfx/Effect.java"))
if not m:
    sys.exit("CONTRACT_VERSION nicht in Effect.java gefunden / not found in Effect.java")
for ph in ("/*@FONTS@*/", "/*@RUNTIME_B64@*/", "/*@RUNTIME_SHA@*/", "/*@TPL_ENGINE@*/"):
    if ph not in src:
        sys.exit("Platzhalter fehlt / placeholder missing: " + ph)

out = src.replace("/*@FONTS@*/", fonts.strip())
out = out.replace("/*@RUNTIME_B64@*/", base64.b64encode(cls).decode())
out = out.replace("/*@RUNTIME_SHA@*/", hashlib.sha256(cls).hexdigest())
out = re.sub(r"/\*@CONTRACT@\*/\d+/\*@END@\*/", m.group(1), out)

# ---- Effekt-Vorlagen (Version 1.1) / effect templates (version 1.1) ----
import json
import zipfile
meta = read_text("designer/templates_params.json")
json.loads(meta)
tpl_classes = {"env": base64.b64encode(read_bytes("build/tpl_cls/de/mk/ambientfx/designer/TplEnv.class")).decode()}
tpl_orig = {}
for key, info in json.loads(meta)["templates"].items():
    tpl_classes[key] = base64.b64encode(read_bytes("build/tpl_cls/de/mk/ambientfx/designer/%s.class" % info["tpl"])).decode()
    with zipfile.ZipFile(os.path.join(ROOT, "build/plugins/%s.ambiente" % key)) as z:
        data = z.read("de/mk/ambientfx/effects/%s.class" % info["class"])
    tpl_orig[key] = {"cls": "de.mk.ambientfx.effects." + info["class"], "sha": hashlib.sha256(data).hexdigest()}
for name, val in (("TPL_META", meta.strip()), ("TPL_CLASSES", json.dumps(tpl_classes)), ("TPL_ORIG", json.dumps(tpl_orig))):
    out, n = re.subn(r"/\*@%s@\*/null/\*@END@\*/" % name, lambda m, v=val: v, out)
    if n != 1:
        sys.exit("Platzhalter fehlt / placeholder missing: " + name)
out = out.replace("/*@TPL_ENGINE@*/", read_text("designer/tpl_engine.js").replace("if (typeof module !== 'undefined') module.exports = TplEngine;", ""))

dst = os.path.join(ROOT, "docs", "designer", "index.html")
os.makedirs(os.path.dirname(dst), exist_ok=True)
with open(dst, "w", encoding="utf-8") as f:
    f.write(out)
print("geschrieben / written:", dst, len(out), "Bytes; Klasse / class", len(cls), "Bytes, SHA-256", hashlib.sha256(cls).hexdigest())
