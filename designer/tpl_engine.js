/* Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md / License: see LICENSE.md
 *
 * DE: JavaScript-Nachbau der Effekt-Vorlagen (Momentum, Momentum select, Atmosphere, Scroll, Drive) fuer die Vorschau im Design Studio. Zeile fuer Zeile
 *     nach den Java-Originalen (java_patch/.../effects/*.java), mit Float-Genauigkeit wie in Java (Math.fround). Geprueft per Takt-Vergleich gegen die
 *     erzeugten Java-Vorlagen (designer/tests/tpl_parity.sh), diese wiederum gegen die echten Effekte (TplEquivTest).
 *     Erwartet die globale Variable TPL_META (= designer/templates_params.json).
 * EN: JavaScript replica of the effect templates for the Design Studio preview. Line by line after the Java originals, with Java float precision
 *     (Math.fround). Checked tick by tick against the generated Java templates, which in turn are checked against the real effects.
 *     Expects the global variable TPL_META (= designer/templates_params.json).
 */
const TplEngine = (function () {
  'use strict';
  const F = Math.fround;
  const jround = x => Math.floor(x + 0.5);                       /* Java Math.round(float) */
  const c255 = v => v < 0 ? 0 : (v > 255 ? 255 : v);
  const PROFILE_UNKNOWN = -1, PROFILE_COMFORT = 1, PROFILE_DYNAMIC = 3, PROFILE_EFFICIENCY = 5;
  const GEAR_S = 5;

  class Rgb {
    constructor(r, g, b) { this.r = 0; this.g = 0; this.b = 0; if (r !== undefined) this.set(r, g, b); }
    set(r, g, b) { if (r instanceof Rgb) { this.r = r.r; this.g = r.g; this.b = r.b; } else { this.r = c255(r); this.g = c255(g); this.b = c255(b); } }
    distance(o) { return Math.abs(this.r - o.r) + Math.abs(this.g - o.g) + Math.abs(this.b - o.b); }
    scale(f) { this.set(jround(F(this.r * f)), jround(F(this.g * f)), jround(F(this.b * f))); }
    setHsv(h, s, v) {
      h = F(h % 360); if (h < 0) h = F(h + 360);
      const c = F(v * s), hp = F(h / 60);
      const x = F(c * F(1 - Math.abs(F(F(hp % 2) - 1))));
      let r1 = 0, g1 = 0, b1 = 0;
      if (hp < 1) { r1 = c; g1 = x; } else if (hp < 2) { r1 = x; g1 = c; } else if (hp < 3) { g1 = c; b1 = x; }
      else if (hp < 4) { g1 = x; b1 = c; } else if (hp < 5) { r1 = x; b1 = c; } else { r1 = c; b1 = x; }
      const m = F(v - c);
      this.set(jround(F(F(r1 + m) * 255)), jround(F(F(g1 + m) * 255)), jround(F(F(b1 + m) * 255)));
    }
    lerp(a, b, t) {
      if (t < 0) t = 0; if (t > 1) t = 1;
      this.set(jround(F(a.r + F((b.r - a.r) * t))), jround(F(a.g + F((b.g - a.g) * t))), jround(F(a.b + F((b.b - a.b) * t))));
    }
  }
  function nearest(target, pal) {
    let best = pal[0], bd = 2147483647;
    for (let i = 0; i < pal.length; i++) { const d = target.distance(pal[i]); if (d < bd) { bd = d; best = pal[i]; } }
    return best;
  }

  /* ---- Werte laden (Begrenzung wie TplEnv) / load values (clamping like TplEnv) ---- */
  function loadParams(key, over) {
    const t = TPL_META.templates[key]; const P = {};
    t.params.forEach(p => {
      let v = over && Object.prototype.hasOwnProperty.call(over, p.name) ? over[p.name] : undefined;
      if (p.type === 'boolean') { P[p.name] = (v === undefined || v === null) ? p.def : (v === true || v === 1 || v === 'true' || v === '1') ? true : (v === false || v === 0 || v === 'false' || v === '0') ? false : p.def; return; }
      if (v === undefined || v === null || v === '' || !isFinite(Number(v))) v = p.def; else v = Number(v);
      if (p.type === 'float') { v = F(v); v = v < p.min ? F(p.min) : (v > p.max ? F(p.max) : v); }
      else { v = Math.trunc(v); v = v < p.min ? p.min : (v > p.max ? p.max : v); }
      P[p.name] = v;
    });
    if (key === 'atmosphere' && F(P.TEMP_WARM_C - P.TEMP_COLD_C) < 1) P.TEMP_WARM_C = F(P.TEMP_COLD_C + 1);
    return P;
  }
  const fresh = (v, now) => !!v && v.valid && (now - v.lastUpdateMs) < 3000;

  /* ---- gemeinsamer Teil von Momentum / Momentum select / Farbring / shared ring code ---- */
  function hueOf(p) {
    const max = Math.max(p.r, Math.max(p.g, p.b)), min = Math.min(p.r, Math.min(p.g, p.b));
    const d = F(max - min);
    if (d === 0) return 0;
    let h;
    if (max === p.r) { h = F(F(p.g - p.b) / d); if (h < 0) h = F(h + 6); }
    else if (max === p.g) h = F(2 + F(F(p.b - p.r) / d));
    else h = F(4 + F(F(p.r - p.g) / d));
    return F(h * 60);
  }
  function Ring(P, onBuilt, onReset) {
    const R = { src: null, ring: null, hue: null, count: 0 };
    R.build = function (pal) {
      R.src = pal; R.ring = null; R.hue = null; R.count = 0; if (onReset) onReset();
      if (pal === null || pal === undefined) return;
      const c = new Array(pal.length), h = new Array(pal.length); let n = 0;
      for (let i = 0; i < pal.length; i++) {
        const p = pal[i]; if (p === null || p === undefined) continue;
        const max = Math.max(p.r, Math.max(p.g, p.b)), min = Math.min(p.r, Math.min(p.g, p.b));
        const sat = max === 0 ? 0 : F(F(max - min) / max), val = F(max / 255);
        if (sat >= P.RING_MIN_SAT && val >= P.RING_MIN_VAL) { c[n] = p; h[n] = hueOf(p); n++; }
      }
      if (n < 2) return;
      let start = 0, bestD = 360;
      for (let i = 0; i < n; i++) { const d = Math.min(h[i], F(360 - h[i])); if (d < bestD) { bestD = d; start = i; } }
      const rel = new Array(n);
      for (let i = 0; i < n; i++) { let r = F(h[i] - h[start]); if (r < 0) r = F(r + 360); rel[i] = r; }
      for (let i = 1; i < n; i++) {
        const kc = c[i], kr = rel[i]; let j = i - 1;
        while (j >= 0 && rel[j] > kr) { c[j + 1] = c[j]; rel[j + 1] = rel[j]; j--; }
        c[j + 1] = kc; rel[j + 1] = kr;
      }
      R.ring = c.slice(0, n); R.hue = rel.slice(0, n); R.count = n;
      if (onBuilt) onBuilt(R);
    };
    R.at = function (out, pos, snap) {
      let i0 = Math.trunc(pos); if (i0 >= R.count) i0 = R.count - 1; if (i0 < 0) i0 = 0;
      let f = F(pos - i0); if (f > 1) f = 1; if (f < 0) f = 0;
      const a = R.ring[i0], b = R.ring[(i0 + 1) % R.count];
      if (snap) out.set(f < 0.5 ? a : b); else out.lerp(a, b, f);
    };
    return R;
  }
  function speedFraction(P, kmh) {
    let k = kmh;
    if (!P.SMOOTH_COLORS) k = F(jround(F(k / P.SPEED_STEP_KMH)) * P.SPEED_STEP_KMH);
    let u = F(k / P.RAINBOW_SPAN_KMH); if (u < 0) u = 0; if (u > 1) u = 1; return u;
  }

  /* ---- Momentum ---- */
  function Momentum(P) {
    const E = { periodMs: P.PERIOD_MS };
    let smoothV = 0, smoothB = 0, lastMs = 0, sportStart = -1, wasSport = false;
    const hueRef = new Rgb(), normalOut = new Rgb(), white = new Rgb(255, 255, 255);
    const ring = Ring(P);
    function setDisplay(out, ideal, pal, br) {
      if (pal && pal.length > 0) out.set(nearest(ideal, pal)); else out.set(ideal);
      out.scale(br);
    }
    E.start = function (now) { smoothV = 0; smoothB = 0; lastMs = now; sportStart = -1; wasSport = false; };
    E.tick = function (now, v, pal, daylight, out, env) {
      const dt = F(Math.max(1, (now - lastMs) | 0)); lastMs = now;
      let kmh = 0, accelPct = 0, braking = false, gearMode = 0;
      if (fresh(v, now)) { kmh = F(v.speedKmh); if (v.speedUnit === 1) kmh = F(kmh * F(1.609)); accelPct = v.acceleratorPct; braking = v.driverBraking; gearMode = v.gearTransMode; }
      const night = !daylight;
      if (braking) { smoothV = kmh; smoothB = 1; hueRef.setHsv(0, 1, 1); setDisplay(out, hueRef, pal, 1); return true; }
      const profile = env.profile;
      const sportGear = gearMode === GEAR_S && profile !== PROFILE_DYNAMIC;
      const alphaV = F(dt / F(P.TC_SPEED_MS + dt));
      smoothV = F(smoothV + F(F(kmh - smoothV) * alphaV));
      let accelT = F(accelPct / P.NIGHT_ACCEL_SATURATE_PCT); if (accelT > 1) accelT = 1;
      const target = night ? F(P.NIGHT_BRIGHT_FLOOR + F(F(1 - P.NIGHT_BRIGHT_FLOOR) * accelT)) : 1;
      const tcB = target > smoothB ? P.TC_BRIGHT_UP : P.TC_BRIGHT_DOWN;
      const alphaB = F(dt / F(tcB + dt));
      smoothB = F(smoothB + F(F(target - smoothB) * alphaB));
      if (pal !== ring.src) ring.build(pal);
      const u = speedFraction(P, smoothV);
      if (ring.ring !== null) { ring.at(normalOut, F(u * ring.count), !P.SMOOTH_COLORS); normalOut.scale(smoothB); }
      else { hueRef.setHsv(F(u * 360), 1, 1); setDisplay(normalOut, hueRef, pal, smoothB); }
      if (sportGear) {
        if (!wasSport) sportStart = now;
        const el = now - sportStart;
        if (el < P.FLASH_HOLD_MS) out.set(white);
        else if (el < P.FLASH_HOLD_MS + P.FLASH_FADE_MS) { const t = F(F(el - P.FLASH_HOLD_MS) / F(P.FLASH_FADE_MS)); out.lerp(white, normalOut, t); }
        else out.set(normalOut);
      } else out.set(normalOut);
      wasSport = sportGear;
      return true;
    };
    return E;
  }

  /* ---- Momentum select ---- */
  function MomentumSelect(P) {
    const E = { periodMs: P.PERIOD_MS };
    let smoothV = 0, smoothB = 0, lastMs = 0, sportStart = -1, wasSport = false;
    let dynLo = 0, dynHi = 0, coolLo = 0, coolHi = 0;
    const hueRef = new Rgb(), normalOut = new Rgb(), white = new Rgb(255, 255, 255);
    function nearestRingIndex(R, hue) {
      let best = 0, bd = 1000;
      for (let i = 0; i < R.count; i++) { const d = Math.abs(F(R.hue[i] - hue)); if (d < bd) { bd = d; best = i; } }
      return best;
    }
    const ring = Ring(P, R => {
      dynLo = nearestRingIndex(R, P.HUE_START_DYNAMIC); dynHi = nearestRingIndex(R, F(P.HUE_START_DYNAMIC + P.HUE_SPAN_DYNAMIC));
      coolLo = nearestRingIndex(R, P.HUE_START_COMFORT_ECO); coolHi = nearestRingIndex(R, F(P.HUE_START_COMFORT_ECO + P.HUE_SPAN_COMFORT_ECO));
    }, () => { dynLo = 0; dynHi = 0; coolLo = 0; coolHi = 0; });
    function blendNearestTwo(out, target, pal) {
      let best = pal[0], second = pal[0], bestD = 2147483647, secondD = 2147483647;
      for (let i = 0; i < pal.length; i++) {
        const d = target.distance(pal[i]);
        if (d < bestD) { secondD = bestD; second = best; bestD = d; best = pal[i]; }
        else if (d < secondD) { secondD = d; second = pal[i]; }
      }
      const sum = bestD + secondD;
      const t = sum > 0 ? F(F(bestD) / F(sum)) : 0;
      out.lerp(best, second, t);
    }
    function setDisplay(out, ideal, pal, br) {
      if (pal && pal.length > 0) { if (P.TRY_BLENDED_COLORS && pal.length > 1) blendNearestTwo(out, ideal, pal); else out.set(nearest(ideal, pal)); }
      else out.set(ideal);
      out.scale(br);
    }
    E.start = function (now) { smoothV = 0; smoothB = 0; lastMs = now; sportStart = -1; wasSport = false; };
    E.tick = function (now, v, pal, daylight, out, env) {
      const dt = F(Math.max(1, (now - lastMs) | 0)); lastMs = now;
      let kmh = 0, accelPct = 0, braking = false, gearMode = 0;
      if (fresh(v, now)) { kmh = F(v.speedKmh); if (v.speedUnit === 1) kmh = F(kmh * F(1.609)); accelPct = v.acceleratorPct; braking = v.driverBraking; gearMode = v.gearTransMode; }
      if (braking) { smoothV = kmh; smoothB = 1; hueRef.setHsv(0, 1, 1); setDisplay(out, hueRef, pal, 1); return true; }
      const profile = env.profile;
      const sportGear = gearMode === GEAR_S && profile !== PROFILE_DYNAMIC;
      let tcHue = P.TC_HUE_DEFAULT, tcUp = P.TC_BRIGHT_DEFAULT_UP, tcDown = P.TC_BRIGHT_DEFAULT_DOWN;
      let hueStart = P.HUE_START_DEFAULT, hueSpan = P.HUE_SPAN_DEFAULT, dynamic = false, cool = false, target;
      if (profile === PROFILE_DYNAMIC) {
        tcHue = P.TC_HUE_DYNAMIC; tcUp = P.TC_BRIGHT_DYNAMIC; tcDown = P.TC_BRIGHT_DYNAMIC;
        hueStart = P.HUE_START_DYNAMIC; hueSpan = P.HUE_SPAN_DYNAMIC; dynamic = true; target = 1;
      } else if (profile === PROFILE_COMFORT || profile === PROFILE_EFFICIENCY) {
        tcHue = P.TC_HUE_COMFORT_ECO; tcUp = P.TC_BRIGHT_COMFORT_ECO_UP; tcDown = P.TC_BRIGHT_COMFORT_ECO_DOWN;
        hueStart = P.HUE_START_COMFORT_ECO; hueSpan = P.HUE_SPAN_COMFORT_ECO; cool = true;
        target = F(P.COMFORT_BRIGHT_FLOOR + F(P.COMFORT_BRIGHT_SPAN * F(accelPct / 100)));
      } else {
        let accelT = F(accelPct / P.NIGHT_ACCEL_SATURATE_PCT); if (accelT > 1) accelT = 1;
        target = daylight ? 1 : F(P.NIGHT_BRIGHT_FLOOR + F(F(1 - P.NIGHT_BRIGHT_FLOOR) * accelT));
      }
      const alphaV = F(dt / F(tcHue + dt));
      smoothV = F(smoothV + F(F(kmh - smoothV) * alphaV));
      const tcB = target > smoothB ? tcUp : tcDown;
      const alphaB = F(dt / F(tcB + dt));
      smoothB = F(smoothB + F(F(target - smoothB) * alphaB));
      let clamped = smoothV; if (clamped < 0) clamped = 0; if (clamped > P.RAINBOW_SPAN_KMH) clamped = P.RAINBOW_SPAN_KMH;
      if (pal !== ring.src) ring.build(pal);
      let u = speedFraction(P, clamped);
      if (ring.ring !== null) {
        if (dynamic) { u = F(u * F(1 - F(P.DYNAMIC_THROTTLE_PULL * F(accelPct / 100)))); ring.at(normalOut, F(dynLo + F(u * F(dynHi - dynLo))), !P.SMOOTH_COLORS); }
        else if (cool) ring.at(normalOut, F(coolLo + F(u * F(coolHi - coolLo))), !P.SMOOTH_COLORS);
        else ring.at(normalOut, F(u * ring.count), !P.SMOOTH_COLORS);
        normalOut.scale(smoothB);
      } else {
        let hue = F(hueStart + F(u * hueSpan));
        if (dynamic) hue = F(hue * F(1 - F(P.DYNAMIC_THROTTLE_PULL * F(accelPct / 100))));
        if (hue >= 360) hue = F(hue - 360); if (hue < 0) hue = F(hue + 360);
        hueRef.setHsv(hue, 1, 1); setDisplay(normalOut, hueRef, pal, smoothB);
      }
      if (sportGear) {
        if (!wasSport) sportStart = now;
        const el = now - sportStart;
        if (el < P.FLASH_HOLD_MS) out.set(white);
        else if (el < P.FLASH_HOLD_MS + P.FLASH_FADE_MS) { const t = F(F(el - P.FLASH_HOLD_MS) / F(P.FLASH_FADE_MS)); out.lerp(white, normalOut, t); }
        else out.set(normalOut);
      } else out.set(normalOut);
      wasSport = sportGear;
      return true;
    };
    return E;
  }

  /* ---- Atmosphere ---- */
  function Atmosphere(P) {
    const E = { periodMs: P.PERIOD_MS };
    let smoothT = 0, lastMs = 0, startMs = 0;
    const hueRef = new Rgb();
    function setDisplay(out, ideal, pal, br) {
      if (P.SNAP_FACTORY && pal && pal.length > 0) out.set(nearest(ideal, pal)); else out.set(ideal);
      out.scale(br);
    }
    E.start = function (now) { smoothT = P.TEMP_FALLBACK_C; lastMs = now; startMs = now; };
    E.tick = function (now, v, pal, daylight, out, env) {
      const dt = F(Math.max(1, (now - lastMs) | 0)); lastMs = now;
      const elapsed = F(now - startMs);
      const target = (env.tempC !== null && env.tempC !== undefined) ? F(env.tempC) : P.TEMP_FALLBACK_C;
      const alphaT = F(dt / F(P.TC_TEMP_MS + dt));
      smoothT = F(smoothT + F(F(target - smoothT) * alphaT));
      let t = F(F(smoothT - P.TEMP_COLD_C) / F(P.TEMP_WARM_C - P.TEMP_COLD_C)); if (t < 0) t = 0; if (t > 1) t = 1;
      const hue = F(P.HUE_COLD + F(F(P.HUE_WARM - P.HUE_COLD) * t));
      hueRef.setHsv(hue, 1, 1);
      const bMin = daylight ? P.DAY_BREATHE_MIN : P.NIGHT_BREATHE_MIN, bMax = daylight ? P.DAY_BREATHE_MAX : P.NIGHT_BREATHE_MAX;
      const phase = F(F(elapsed % P.BREATHE_PERIOD_MS) / P.BREATHE_PERIOD_MS);
      const breathe = F(0.5 + F(0.5 * F(Math.sin(phase * 2.0 * Math.PI))));
      const br = F(bMin + F(F(bMax - bMin) * breathe));
      setDisplay(out, hueRef, pal, br);
      return true;
    };
    return E;
  }

  /* ---- Scroll ---- */
  function Scroll(P) {
    const E = { periodMs: P.PERIOD_MS };
    let smoothV = 0, curHue = 0, lastMs = 0;
    const hueRef = new Rgb();
    const LR = F(0.2126), LG = F(0.7152), LB = F(0.0722);
    function relLum(c) { return F(F(F(F(LR * c.r) + F(LG * c.g)) + F(LB * c.b)) / 255); }
    function satFor(hue) {
      const probe = new Rgb(); probe.setHsv(hue, 1, 1);
      const y1 = relLum(probe);
      if (y1 >= P.TARGET_LUMINANCE) return 1;
      let s = F(F(1 - P.TARGET_LUMINANCE) / F(1 - y1));
      if (s < P.MIN_SATURATION_FLOOR) s = P.MIN_SATURATION_FLOOR;
      return s;
    }
    function setDisplay(out, ideal, pal, br) {
      if (!P.TRY_FREE_COLOR && pal && pal.length > 0) out.set(nearest(ideal, pal)); else out.set(ideal);
      out.scale(br);
    }
    E.start = function (now) { smoothV = 0; curHue = 0; lastMs = now; };
    E.tick = function (now, v, pal, daylight, out, env) {
      const dt = F(Math.max(1, (now - lastMs) | 0)); lastMs = now;
      let kmh = 0, braking = false;
      if (fresh(v, now)) { kmh = F(v.speedKmh); if (v.speedUnit === 1) kmh = F(kmh * F(1.609)); braking = v.driverBraking; }
      if (braking) { hueRef.setHsv(0, 1, 1); setDisplay(out, hueRef, pal, 1); return true; }
      if (kmh < 0) kmh = 0;
      const alphaV = F(dt / F(P.TC_SPEED_MS + dt));
      smoothV = F(smoothV + F(F(kmh - smoothV) * alphaV));
      const dtSec = F(dt / 1000);
      curHue = F(curHue + F(F(P.IDLE_ROTATION_DEG_PER_SEC + F(smoothV * P.HUE_SPEED_FACTOR)) * dtSec));
      while (curHue >= 360) curHue = F(curHue - 360);
      hueRef.setHsv(curHue, satFor(curHue), 1);
      const shimmerPhase = F(F(now % Math.trunc(P.SHIMMER_PERIOD_MS)) / P.SHIMMER_PERIOD_MS);
      const shimmer = F(0.5 + F(0.5 * F(Math.sin(shimmerPhase * 2.0 * Math.PI))));
      const shimmerFactor = F(P.SHIMMER_BRIGHTNESS_MIN + F(F(1 - P.SHIMMER_BRIGHTNESS_MIN) * shimmer));
      const br = F((daylight ? 1 : P.NIGHT_BRIGHTNESS) * shimmerFactor);
      setDisplay(out, hueRef, pal, br);
      return true;
    };
    return E;
  }

  /* ---- Drive ---- */
  function Drive(P) {
    const E = { periodMs: P.PERIOD_MS };
    let thrEma = 0, steerEma = 0, lastMs = 0;
    const hueRef = new Rgb();
    const clamp01 = x => x < 0 ? 0 : (x > 1 ? 1 : x);
    function setDisplay(out, ideal, pal, br) {
      if (P.SNAP_FACTORY && pal && pal.length > 0) out.set(nearest(ideal, pal)); else out.set(ideal);
      out.scale(br);
    }
    E.start = function (now) { thrEma = 0; steerEma = 0; lastMs = now; };
    E.tick = function (now, v, pal, daylight, out, env) {
      const dt = F(Math.max(1, (now - lastMs) | 0)); lastMs = now;
      let accelPct = 0, wheel = 0, braking = false;
      if (fresh(v, now)) { accelPct = v.acceleratorPct; wheel = v.wheelAngle; braking = v.driverBraking; }
      if (braking) { hueRef.setHsv(0, 1, 1); setDisplay(out, hueRef, pal, 1); return true; }
      const targetThr = clamp01(F(accelPct / 100));
      const tcThr = targetThr > thrEma ? P.TC_THROTTLE_UP_MS : P.TC_THROTTLE_DOWN_MS;
      thrEma = F(thrEma + F(F(dt / F(tcThr + dt)) * F(targetThr - thrEma)));
      const targetSteer = F(Math.sqrt(clamp01(F(Math.abs(wheel) / P.STEER_FULL_DEG))));
      steerEma = F(steerEma + F(F(dt / F(P.TC_STEER_MS + dt)) * F(targetSteer - steerEma)));
      const t = clamp01(F(thrEma / P.PEDAL_FULL));
      const h = F(P.DRIVE_BASE_HUE + F(P.DRIVE_THROTTLE_SWEEP * t));
      const satDrv = t > steerEma ? t : steerEma;
      const sat = F(P.DRIVE_SAT_REST + F(F(1 - P.DRIVE_SAT_REST) * satDrv));
      let val = F(P.DRIVE_V_REST + F(P.DRIVE_V_SWEEP * t));
      if (val < P.DRIVE_V_FLOOR) val = P.DRIVE_V_FLOOR; if (val > 1) val = 1;
      let hc = F(h + F(P.DRIVE_SPLIT_DEG * steerEma)); if (hc > P.DRIVE_HUE_CAP) hc = P.DRIVE_HUE_CAP;
      let cv = F(val + F(P.DRIVE_CONTOUR_BOOST * steerEma)); if (cv > 1) cv = 1;
      const br = daylight ? cv : F(cv * P.NIGHT_FACTOR);
      hueRef.setHsv(hc, sat, 1);
      setDisplay(out, hueRef, pal, br);
      return true;
    };
    return E;
  }

  const MAKERS = { momentum: Momentum, momentumselect: MomentumSelect, atmosphere: Atmosphere, scroll: Scroll, drive: Drive };

  /** Geschaetzte Werksfarbliste (gleiche wie TplEquivTest.palette()) / estimated factory colour list (same as TplEquivTest.palette()). */
  function palette() {
    const p = [], t = new Rgb();
    for (let i = 0; i < 28; i++) {
      let hue = F(F(F(i * 360) / 28) + F(((i % 3) - 1) * 2)); if (hue < 0) hue = F(hue + 360);
      t.setHsv(hue, F(F(0.92) - F((i % 4) * F(0.02))), F(F(0.97) - F((i % 5) * F(0.01))));
      p.push(new Rgb(t.r, t.g, t.b));
    }
    p.push(new Rgb(255, 255, 255)); p.push(new Rgb(250, 248, 240));
    return p;
  }

  return {
    Rgb, palette, loadParams,
    make: (key, over) => MAKERS[key](loadParams(key, over)),
    PROFILE_UNKNOWN
  };
})();
if (typeof module !== 'undefined') module.exports = TplEngine;
