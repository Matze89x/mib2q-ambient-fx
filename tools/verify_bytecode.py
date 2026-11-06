#!/usr/bin/env python3
"""
Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)

Vergleicht den Bytecode einer gepatchten Klasse mit dem Original (aus lsd.jar)
METHODENWEISE und listet, welche Methoden sich unterscheiden.

Normalisierung: Constant-Pool-Indizes, Whitespace, nop-Padding und die
ldc/ldc_w-Variante werden ignoriert; Sprungziele werden relativ zur
Instruktionsnummer verglichen, damit verschobene Offsets keinen Diff erzeugen.

Aufruf:
  tools/verify_bytecode.py <original.class> <patched.class>
  tools/verify_bytecode.py --jar <lsd.jar> <patched-classes-dir> [class ...]

Exit-Code 0 wenn nur erwartete Methoden abweichen (via --expect), sonst 1.
"""
import re
import subprocess
import sys
import tempfile
import zipfile
import os

INSN = re.compile(r'^\s*(\d+):\s+(\w+)(.*)$')
METH = re.compile(r'^\s{2}(public|private|protected|static|final|synchronized|abstract|native|[\w.$<>\[\],]+\s)+.*\(.*\).*;$')


def javap(path):
    return subprocess.run(['javap', '-c', '-p', path], capture_output=True, text=True).stdout


def parse(path):
    """-> dict methodSignature -> list of normalized instruction strings"""
    methods = {}
    cur = None
    insns = []
    offsets = []
    for line in javap(path).splitlines():
        m = INSN.match(line)
        if m:
            off = int(m.group(1))
            op = m.group(2)
            rest = m.group(3)
            if op == 'nop':
                continue
            if op == 'ldc_w':
                op = 'ldc'
            rest = re.sub(r'#\d+', '#', rest)
            # javap schreibt Member der eigenen Klasse mal mit, mal ohne Klassen-Prefix
            rest = re.sub(r'(Method|Field|InterfaceMethod) [\w/$]+\.', r'\1 ', rest)
            rest = re.sub(r'\s+', ' ', rest).strip()
            # Sprungziel (absolute Offset) -> als Marker behalten, spaeter aufloesen
            jump = None
            jm = re.match(r'^(\d+)(\s*//.*)?$', rest)
            if jm and op.startswith(('if', 'goto', 'jsr')):
                jump = int(jm.group(1))
                rest = '@JUMP'
            insns.append((off, op, rest, jump))
            continue
        if METH.match(line) and '(' in line and not line.strip().startswith('Code:'):
            if cur is not None:
                methods[cur] = normalize(insns)
            cur = line.strip()
            insns = []
    if cur is not None:
        methods[cur] = normalize(insns)
    return methods


def normalize(insns):
    off2idx = {off: i for i, (off, _, _, _) in enumerate(insns)}
    out = []
    for i, (off, op, rest, jump) in enumerate(insns):
        if jump is not None:
            tgt = off2idx.get(jump, None)
            rest = 'rel%+d' % (tgt - i) if tgt is not None else 'abs%d' % jump
        out.append('%s %s' % (op, rest))
    return out


def extract_from_jar(jar, cls):
    tmp = tempfile.mkdtemp()
    with zipfile.ZipFile(jar) as z:
        z.extract(cls + '.class', tmp)
    return os.path.join(tmp, cls + '.class')


def compare(orig, patched, expect):
    a = parse(orig)
    b = parse(patched)
    ok = True
    print('== %s' % os.path.basename(patched))
    print('   methods: original=%d patched=%d' % (len(a), len(b)))
    for name in sorted(set(a) | set(b)):
        if name not in a:
            print('   + NEW      %s' % name)
            if not any(e in name for e in expect):
                ok = False
        elif name not in b:
            print('   - MISSING  %s' % name)
            ok = False
        elif a[name] != b[name]:
            tag = 'expected' if any(e in name for e in expect) else 'UNEXPECTED'
            print('   ~ DIFF (%s) %s  [%d -> %d insns]' % (tag, name, len(a[name]), len(b[name])))
            if tag == 'UNEXPECTED':
                ok = False
    if ok:
        print('   OK: nur erwartete Abweichungen')
    return ok


def main(argv):
    expect = []
    if '--expect' in argv:
        i = argv.index('--expect')
        expect = argv[i + 1].split(',')
        del argv[i:i + 2]
    if argv[0] == '--jar':
        jar, outdir = argv[1], argv[2]
        classes = argv[3:]
        ok = True
        for cls in classes:
            orig = extract_from_jar(jar, cls)
            patched = os.path.join(outdir, cls + '.class')
            ok &= compare(orig, patched, expect)
        sys.exit(0 if ok else 1)
    else:
        sys.exit(0 if compare(argv[0], argv[1], expect) else 1)


if __name__ == '__main__':
    main(sys.argv[1:])
