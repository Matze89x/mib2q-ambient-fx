#!/bin/bash
#
# Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
# Lizenz: siehe LICENSE.md / License: see LICENSE.md
#
# DE: Wie parity.sh, aber ohne Browser: rechnet die Faelle des Design-Studios mit der JavaScript-Engine der gebauten Seite (docs/designer/index.html)
#     ueber node bzw. macOS-JavaScriptCore (osascript) und vergleicht sie mit der Java-Klasse DesignEffect.
#     Voraussetzung: ./build_java.sh, ./designer/build_runtime.sh, python3 -I tools/embed_designer.py
# EN: Like parity.sh but without a browser: computes the Design Studio cases with the JavaScript engine of the built page (docs/designer/index.html)
#     via node or macOS JavaScriptCore (osascript) and compares them with the Java class DesignEffect.
set -e
cd "$(dirname "$0")/../.."
W="$(mktemp -d "${TMPDIR:-/tmp}/afxpar.XXXXXX")"
trap '[ -n "$W" ] && rm -rf "$W"' EXIT
python3 -I - "$W" <<'PYEOF'
import re, sys
W = sys.argv[1]
page = open("docs/designer/index.html", encoding="utf-8").read()
scripts = re.findall(r"<script>(.*?)</script>", page, re.S)
script1 = scripts[0].replace("'use strict';", "")
examples = re.search(r"const EXAMPLES = \{.*?\n\};\n", scripts[1], re.S).group(0).replace("const EXAMPLES", "var EXAMPLES")
snippet = open("designer/tests/parity_snippet.js", encoding="utf-8").read()
body = snippet[snippet.index("(function () {"):]
body = body.replace("const A = window.__afx;", "const A = {Engine, EXAMPLES, serialize};")
body = body[:body.index("const a = document.createElement('a');")] + "return JSON.stringify({pal: PAL, cases: out});\n})()"
js = "var self={crypto:{getRandomValues:function(a){for(var i=0;i<a.length;i++)a[i]=Math.floor(Math.random()*256);return a;}}};\nvar window=self;\n"
js += script1 + "\n" + examples + "\nvar RESULT = " + body + ";\nRESULT;\n"
open(W + "/eval.js", "w", encoding="utf-8").write(js)
open(W + "/run_jxa.js", "w", encoding="utf-8").write("""
ObjC.import('Foundation');
var code = $.NSString.stringWithContentsOfFileEncodingError('%s/eval.js', $.NSUTF8StringEncoding, null).js;
var res = (0,eval)(code);
$(res).writeToFileAtomicallyEncodingError('%s/parity_js.json', true, $.NSUTF8StringEncoding, null);
""" % (W, W))
PYEOF
if command -v node >/dev/null 2>&1; then
    node -e "const fs=require('fs');const r=eval(fs.readFileSync('$W/eval.js','utf8'));fs.writeFileSync('$W/parity_js.json',r)"
else
    osascript -l JavaScript "$W/run_jxa.js" >/dev/null
fi
bash designer/tests/parity.sh "$W/parity_js.json"
