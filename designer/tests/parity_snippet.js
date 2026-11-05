/*
 * DE: In die Browser-Konsole der geoeffneten Design-Studio-Seite einfuegen. Rechnet 17 Faelle x 90 Takte mit der JavaScript-Engine der Seite und
 *     laedt "parity_js.json" herunter. Danach: designer/tests/parity.sh parity_js.json  (vergleicht mit der Java-Klasse DesignEffect).
 * EN: Paste into the browser console of the opened Design Studio page. Computes 17 cases x 90 ticks with the page's JavaScript engine and
 *     downloads "parity_js.json". Then: designer/tests/parity.sh parity_js.json  (compares with the Java class DesignEffect).
 */
(function () {
  const A = window.__afx;
  const PAL = [[255,255,255],[255,0,0],[0,255,0],[0,0,255],[255,160,0],[170,0,255],[0,220,220]];
  const cases = [];
  const mk = (name, cfg, usePal) => cases.push({name, cfg, usePal});
  Object.keys(A.EXAMPLES).forEach(k => { const c = A.EXAMPLES[k](); mk('ex_' + k, c, false); mk('ex_' + k + '_pal', c, true); });
  { const c = A.EXAMPLES.tempo(); c.brakeOn = true; c.brakeColor = '#FF0000'; c.brakeBright = 80; c.snap = false; mk('brake_nosnap', c, false); }
  { const c = A.EXAMPLES.tempo(); c.brakeOn = true; c.brakeColor = '#FF2030'; c.brakeBright = 70; c.snap = true; mk('brake_snap', c, true); }
  { const c = A.EXAMPLES.gas(); c.smoothMs = 0; mk('nosmooth', c, false); }
  { const c = A.EXAMPLES.curve(); c.smooth = false; c.brSource = 'speed'; c.brSrcMin = 0; c.brSrcMax = 120; c.brInvert = true; c.brMin = 20; c.brMax = 90; mk('brspeed_inv', c, false); }
  { const c = A.EXAMPLES.rainbow(); c.cycSat = 70; c.cycBase = -30; c.smoothMs = 800; mk('cycle2', c, true); }
  const out = [];
  for (const cs of cases) {
    const e = new A.Engine(cs.cfg); e.start(1000);
    const rows = [];
    for (let i = 0; i < 90; i++) {
      const now = 1000 + i * 100;
      const inp = { speed: (i*3)%160, throttle: (i*7)%101, steer: ((i*13)%361)-180, rpm: 800 + i*50,
                    accel: (((i*3)%13)-6)/2, lat: (((i*5)%13)-6)/2, temp: 15, braking: (i%9===0) };
      const o = e.tick(now, inp, (i%20) < 15, cs.usePal ? PAL : null);
      rows.push([o.r, o.g, o.b]);
    }
    out.push({name: cs.name, props: A.serialize(cs.cfg), usePal: cs.usePal, rows});
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([JSON.stringify({pal: PAL, cases: out})], {type: 'application/json'}));
  a.download = 'parity_js.json';
  document.body.appendChild(a); a.click(); a.remove();
})();
