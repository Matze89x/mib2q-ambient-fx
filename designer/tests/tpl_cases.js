/* Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev - Lizenz: siehe LICENSE.md / License: see LICENSE.md
 * DE: Erzeugt Testfaelle (Eingaben + Werte-Aenderungen) und rechnet sie mit tpl_engine.js. Erwartet TplEngine und TPL_META. Liefert ein Objekt.
 * EN: Generates test cases (inputs + value changes) and computes them with tpl_engine.js. Expects TplEngine and TPL_META. Returns an object. */
(function () {
  let seed = 987654321;
  const rnd = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
  const ri = n => Math.floor(rnd() * n);
  const q = x => Math.round(x * 4) / 4;
  const N = 3000;
  const profiles = [-1, 1, 2, 3, 5, 7];
  const inputs = [];
  let spd = 0, thr = 0, wheel = 0, gear = 4, brake = false, profIdx = 2, day = true;
  for (let i = 0; i < N; i++) {
    const dt = (i % 7 === 0) ? 60 : 150;
    if (i % 23 === 0) spd = ri(5) === 0 ? 0 : ri(190);
    spd += (rnd() - 0.5) * 6; if (spd < 0) spd = 0;
    if (i % 11 === 0) thr = ri(3) === 0 ? 0 : ri(101);
    if (i % 17 === 0) wheel = ri(700) - 350;
    if (i % 41 === 0) brake = ri(4) === 0;
    if (i % 53 === 0) gear = ri(3) === 0 ? 4 : (ri(2) === 0 ? 5 : 0);
    if (i % 211 === 0) profIdx = ri(profiles.length);
    if (i % 97 === 0) day = ri(3) !== 0;
    inputs.push({
      dt, vvalid: (i % 400) < 380, stale: (i % 61) === 0, unit: (Math.floor(i / 800) % 3) === 2 ? 1 : 0, speed: q(spd), thr, wheel, gear, brake,
      profValid: !(profiles[profIdx] === -1 && i % 2 === 0), profile: profiles[profIdx],
      tempValid: (Math.floor(i / 300) % 4) !== 3, temp: -15 + (Math.floor(i / 5) % 56), day
    });
  }
  const b = x => x ? '1' : '0';
  const inputsText = inputs.map(t => [t.dt, b(t.vvalid), b(t.stale), t.unit, t.speed, t.thr, t.wheel, t.gear, b(t.brake), b(t.profValid), t.profile, b(t.tempValid), t.temp, b(t.day)].join(' ')).join('\n') + '\n';

  const keys = Object.keys(TPL_META.templates);
  const cases = [];
  keys.forEach(key => {
    const params = TPL_META.templates[key].params;
    cases.push({ name: key + '_default_pal', key, over: {}, pal: true });
    cases.push({ name: key + '_default_nopal', key, over: {}, pal: false });
    for (let k = 0; k < 10; k++) {
      const over = {};
      params.forEach(p => {
        if (rnd() > 0.4) return;
        if (p.type === 'boolean') { over[p.name] = rnd() < 0.5; return; }
        const span = p.max - p.min, step = p.step || 1;
        let v;
        const r = rnd();
        if (r < 0.08) v = p.min - step * 3; else if (r < 0.16) v = p.max + span * 0.5;      /* ausserhalb: Begrenzung pruefen */
        else v = p.min + Math.round(rnd() * span / step) * step;
        over[p.name] = Number(v.toFixed(6));
      });
      cases.push({ name: key + '_var' + k + (k % 2 ? '_pal' : '_nopal'), key, over, pal: k % 2 === 1 });
    }
  });

  const PAL = TplEngine.palette();
  const outLines = [];
  const files = [];
  cases.forEach(cs => {
    let props = 'format=2\ntemplate=' + cs.key + '\n';
    Object.keys(cs.over).forEach(n => { props += 'p.' + n + '=' + String(cs.over[n]) + '\n'; });
    files.push({ name: cs.name, props, pal: cs.pal });
    const eff = TplEngine.make(cs.key, cs.over);
    let now = 100000; eff.start(now);
    const out = new TplEngine.Rgb();
    inputs.forEach((t, i) => {
      now += t.dt;
      const v = { valid: t.vvalid, lastUpdateMs: t.stale ? now - 5000 : now, speedUnit: t.unit, speedKmh: t.speed, acceleratorPct: t.thr, wheelAngle: t.wheel, gearTransMode: t.gear, driverBraking: t.brake };
      eff.tick(now, v, cs.pal ? PAL : null, t.day, out, { profile: t.profValid ? t.profile : -1, tempC: t.tempValid ? t.temp : null });
      outLines.push(cs.name + ' ' + i + ' ' + out.r + ' ' + out.g + ' ' + out.b);
    });
  });
  return { inputsText, files, outText: outLines.join('\n') + '\n' };
})()
