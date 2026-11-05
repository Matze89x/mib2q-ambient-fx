/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 * License: see LICENSE.md (attribution required, NO commercial use / selling)
 *
 * AmbientFX Design Studio - Laufzeit-Effekt, der sein Design aus einer Textdatei liest.
 * AmbientFX Design Studio - runtime effect that reads its design from a text file.
 *
 * DE: Diese EINE Klasse steckt in jeder .ambiente-Datei, die das Design Studio (docs/designer/index.html) erzeugt. Das Design selbst
 *     (Farbverlauf, Helligkeit, Quellen ...) steht als Text in "ambientfx_design.properties" im selben .ambiente. So braucht der Editor im
 *     Browser keinen Compiler. Die Klasse liegt NICHT in ambient_fx.jar (jedes Plugin laedt seine eigene Kopie ueber seinen eigenen
 *     Classloader), das Jar bleibt unveraendert. Gebaut wird sie mit designer/build_runtime.sh, die Pruefsumme steht im Design Studio.
 * EN: This ONE class is inside every .ambiente file produced by the Design Studio (docs/designer/index.html). The design itself (colour
 *     ramp, brightness, sources ...) is plain text in "ambientfx_design.properties" in the same .ambiente. So the browser editor needs
 *     no compiler. The class is NOT in ambient_fx.jar (each plugin loads its own copy through its own classloader); the jar stays unchanged.
 *     Built with designer/build_runtime.sh; the checksum is shown in the Design Studio.
 *
 * Reihenfolge wie bei den mitgelieferten Effekten: ideale Farbe -> glaetten -> auf Werksfarbe snappen -> erst dann Helligkeit.
 * Same order as the bundled effects: ideal colour -> smooth -> snap to factory colour -> only then brightness.
 *
 * Sicherheitsklammern (nicht abschaltbar) / safety clamps (cannot be disabled):
 *   - Sende-Intervall 50..500 ms, Atem-Periode mindestens 2 s (keine schnellen Blitze), alle Werte begrenzt.
 *   - Fehlerhaftes Design -> der Konstruktor wirft, der Plugin-Lader ueberspringt das Plugin (kein Absturz).
 *
 * Java 1.2 kompatibel (J9 / MHI2Q): keine Generics, keine Enums, kein StringBuilder, kein Autoboxing.
 */
package de.mk.ambientfx.designer;

import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.Log;
import de.mk.ambientfx.OutsideTempData;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

import java.io.InputStream;
import java.util.Properties;
import java.util.StringTokenizer;

public class DesignEffect implements Effect {

    /** Name der Design-Datei im .ambiente. / Name of the design file inside the .ambiente. */
    public static final String DESIGN_FILE = "/ambientfx_design.properties";

    /* Quellen / sources */
    private static final int SRC_NONE = 0;
    private static final int SRC_SPEED = 1;
    private static final int SRC_THROTTLE = 2;
    private static final int SRC_STEER = 3;
    private static final int SRC_BRAKE = 4;
    private static final int SRC_RPM = 5;
    private static final int SRC_ACCEL = 6;
    private static final int SRC_LAT = 7;
    private static final int SRC_TEMP = 8;

    private static final int MODE_RAMP = 0;
    private static final int MODE_CYCLE = 1;
    private static final int MODE_SOLID = 2;

    private static final double TEMP_FALLBACK_C = 15.0;
    private static final int MAX_STOPS = 16;

    /* ---- Design (aus der Datei) / design (from the file) ---- */
    private String id = "";
    private int period;
    private int colorMode;
    private int colorSrc;
    private double colorMin, colorMax;
    private int nStops;
    private double[] stopPos = new double[MAX_STOPS];
    private double[] stopR = new double[MAX_STOPS];
    private double[] stopG = new double[MAX_STOPS];
    private double[] stopB = new double[MAX_STOPS];
    private boolean stopSmooth;
    private double cycBase, cycSpeed, cycSat;
    private int brSrc;
    private double brSrcMin, brSrcMax;
    private boolean brInvert;
    private double brMin, brMax;
    private boolean breath;
    private double breathSec, breathDepth;
    private double nightFactor;
    private boolean brakeOn;
    private double brakeR, brakeG, brakeB, brakeBright;
    private double smoothMs;
    private boolean snap;

    /* ---- Zustand / state ---- */
    private boolean first = true;
    private long startMs;
    private long lastMs;
    private long lastSentMs;
    private double hue;
    private double smR, smG, smB, smBright;
    private final Rgb work = new Rgb();
    private final Rgb lastOut = new Rgb(-1, -1, -1);

    public DesignEffect() {
        InputStream in = DesignEffect.class.getResourceAsStream(DESIGN_FILE);
        if (in == null) {
            /* zweiter Weg (alte Klassenbibliotheken): direkt ueber den Classloader dieses Plugins, ohne fuehrenden Schraegstrich */
            ClassLoader cl = DesignEffect.class.getClassLoader();
            if (cl != null) {
                in = cl.getResourceAsStream(DESIGN_FILE.substring(1));
            }
        }
        if (in == null) {
            throw new IllegalStateException("ambientfx_design.properties fehlt im Plugin");
        }
        Properties p = new Properties();
        try {
            p.load(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Design nicht lesbar: " + e);
        } finally {
            try {
                in.close();
            } catch (java.io.IOException e) {
                /* egal */
            }
        }
        parse(p);
    }

    /** Fuer Tests: Design direkt aus Properties. / For tests: design straight from Properties. */
    public DesignEffect(Properties p) {
        parse(p);
    }

    /* ---------------------------------------------------------------- Parsen */

    private static double num(Properties p, String key, double def, double lo, double hi) {
        String s = p.getProperty(key);
        double v = def;
        if (s != null && s.trim().length() > 0) {
            try {
                v = Double.valueOf(s.trim()).doubleValue();
            } catch (NumberFormatException e) {
                throw new IllegalStateException("ungueltige Zahl bei " + key + ": " + s);
            }
        }
        if (v != v) {
            v = def;                              /* NaN */
        }
        if (v < lo) {
            v = lo;
        }
        if (v > hi) {
            v = hi;
        }
        return v;
    }

    private static boolean flag(Properties p, String key, boolean def) {
        String s = p.getProperty(key);
        if (s == null) {
            return def;
        }
        s = s.trim();
        return s.equals("1") || s.equalsIgnoreCase("true");
    }

    private static int source(Properties p, String key, int def) {
        String s = p.getProperty(key);
        if (s == null) {
            return def;
        }
        s = s.trim().toLowerCase();
        if (s.equals("none")) return SRC_NONE;
        if (s.equals("speed")) return SRC_SPEED;
        if (s.equals("throttle")) return SRC_THROTTLE;
        if (s.equals("steer")) return SRC_STEER;
        if (s.equals("brake")) return SRC_BRAKE;
        if (s.equals("rpm")) return SRC_RPM;
        if (s.equals("accel")) return SRC_ACCEL;
        if (s.equals("lat")) return SRC_LAT;
        if (s.equals("temp")) return SRC_TEMP;
        throw new IllegalStateException("unbekannte Quelle bei " + key + ": " + s);
    }

    private static int hex(String s, String what) {
        s = s.trim();
        if (s.length() != 7 || s.charAt(0) != '#') {
            throw new IllegalStateException("Farbe '" + s + "' (" + what + ") muss #RRGGBB sein");
        }
        try {
            return Integer.parseInt(s.substring(1), 16);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Farbe '" + s + "' (" + what + ") ungueltig");
        }
    }

    private void parse(Properties p) {
        String fmt = p.getProperty("format");
        if (fmt == null || !fmt.trim().equals("1")) {
            throw new IllegalStateException("Design-Format " + fmt + " wird nicht unterstuetzt (erwartet 1)");
        }
        id = p.getProperty("design.id", "").trim();           /* nur zur Wiedererkennung (Studio, Debug-Protokoll); das Auto merkt sich den Effekt ueber den Namen */
        if (id.length() > 40) {
            id = id.substring(0, 40);
        }
        period = (int) num(p, "period", 100, 50, 500);

        String m = p.getProperty("color.mode", "ramp").trim().toLowerCase();
        if (m.equals("ramp")) colorMode = MODE_RAMP;
        else if (m.equals("cycle")) colorMode = MODE_CYCLE;
        else if (m.equals("solid")) colorMode = MODE_SOLID;
        else throw new IllegalStateException("unbekannter Farbmodus: " + m);
        colorSrc = source(p, "color.source", SRC_SPEED);
        colorMin = num(p, "color.min", 0, -100000, 100000);
        colorMax = num(p, "color.max", 130, -100000, 100000);
        stopSmooth = flag(p, "color.smooth", true);

        String st = p.getProperty("color.stops", "0:#FFFFFF");
        StringTokenizer tk = new StringTokenizer(st, ",");
        nStops = 0;
        double lastPos = -1;
        while (tk.hasMoreTokens()) {
            String one = tk.nextToken().trim();
            if (one.length() == 0) {
                continue;
            }
            if (nStops >= MAX_STOPS) {
                throw new IllegalStateException("zu viele Farbstopps (max " + MAX_STOPS + ")");
            }
            int c = one.indexOf(':');
            if (c <= 0) {
                throw new IllegalStateException("Farbstopp '" + one + "' muss Position:#RRGGBB sein");
            }
            double pos;
            try {
                pos = Double.valueOf(one.substring(0, c).trim()).doubleValue();
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Position im Farbstopp '" + one + "' ungueltig");
            }
            if (pos < 0) pos = 0;
            if (pos > 100) pos = 100;
            if (pos < lastPos) {
                throw new IllegalStateException("Farbstopps muessen nach Position sortiert sein");
            }
            lastPos = pos;
            int rgb = hex(one.substring(c + 1), "color.stops");
            stopPos[nStops] = pos;
            stopR[nStops] = (rgb >> 16) & 255;
            stopG[nStops] = (rgb >> 8) & 255;
            stopB[nStops] = rgb & 255;
            nStops++;
        }
        if (nStops == 0) {
            throw new IllegalStateException("color.stops ist leer");
        }

        cycBase = num(p, "cycle.base", 10, -720, 720);
        cycSpeed = num(p, "cycle.speed", 60, -720, 720);
        cycSat = num(p, "cycle.sat", 100, 0, 100) / 100.0;

        brSrc = source(p, "bright.source", SRC_NONE);
        brSrcMin = num(p, "bright.srcMin", 0, -100000, 100000);
        brSrcMax = num(p, "bright.srcMax", 100, -100000, 100000);
        brInvert = flag(p, "bright.invert", false);
        brMin = num(p, "bright.min", 30, 0, 100) / 100.0;
        brMax = num(p, "bright.max", 100, 0, 100) / 100.0;
        breath = flag(p, "bright.breath", false);
        breathSec = num(p, "bright.breathSec", 6, 2, 120);            /* mindestens 2 s: keine schnellen Blitze */
        breathDepth = num(p, "bright.breathDepth", 40, 0, 100) / 100.0;
        nightFactor = num(p, "night.factor", 40, 0, 100) / 100.0;

        brakeOn = flag(p, "brake.on", false);
        int bc = hex(p.getProperty("brake.color", "#FF0000"), "brake.color");
        brakeR = (bc >> 16) & 255;
        brakeG = (bc >> 8) & 255;
        brakeB = bc & 255;
        brakeBright = num(p, "brake.bright", 100, 0, 100) / 100.0;

        smoothMs = num(p, "smooth.ms", 400, 0, 5000);
        snap = flag(p, "snap", true);
    }

    /* ---------------------------------------------------------------- Effect */

    public void start(long nowMs) {
        if (Log.enabled()) {
            Log.i("DesignEffect", "start design.id=" + id);
        }
        first = true;
        startMs = nowMs;
        lastMs = nowMs;
        lastSentMs = 0L;
        hue = 0.0;
        lastOut.set(-1, -1, -1);
    }

    public int periodMs() {
        return period;
    }

    private static double norm(double v, double lo, double hi) {
        if (hi == lo) {
            return 0.0;
        }
        double t = (v - lo) / (hi - lo);
        if (t < 0.0) return 0.0;
        if (t > 1.0) return 1.0;
        return t;
    }

    private double input(int src, VehicleData v, boolean fresh) {
        switch (src) {
            case SRC_SPEED:
                if (!fresh) return 0.0;
                return v.speedUnit == 1 ? v.speedKmh * 1.609 : v.speedKmh;       /* immer km/h */
            case SRC_THROTTLE:
                return fresh ? v.acceleratorPct : 0.0;
            case SRC_STEER:
                return fresh ? Math.abs(v.wheelAngle) : 0.0;
            case SRC_BRAKE:
                return fresh && v.driverBraking ? 100.0 : 0.0;
            case SRC_RPM:
                return fresh ? v.engineRpm : 0.0;
            case SRC_ACCEL:
                return fresh ? v.longAccel : 0.0;
            case SRC_LAT:
                return fresh ? v.latAccel : 0.0;
            case SRC_TEMP:
                try {
                    OutsideTempData t = AmbientFx.getInstance().getOutsideTemp();
                    return (t != null && t.valid) ? t.celsius : TEMP_FALLBACK_C;
                } catch (Throwable e) {
                    return TEMP_FALLBACK_C;
                }
            default:
                return 0.0;
        }
    }

    /** Farbverlauf an Position t (0..1) -> smR/smG/smB-artiges Triple in out[0..2]. */
    private void ramp(double t, double[] out) {
        double pos = t * 100.0;
        if (nStops == 1 || pos <= stopPos[0]) {
            out[0] = stopR[0]; out[1] = stopG[0]; out[2] = stopB[0];
            return;
        }
        if (pos >= stopPos[nStops - 1]) {
            out[0] = stopR[nStops - 1]; out[1] = stopG[nStops - 1]; out[2] = stopB[nStops - 1];
            return;
        }
        int i = 0;
        while (i < nStops - 2 && pos > stopPos[i + 1]) {
            i++;
        }
        double span = stopPos[i + 1] - stopPos[i];
        double f = span <= 0.0 ? 1.0 : (pos - stopPos[i]) / span;
        if (stopSmooth) {
            f = f * f * (3.0 - 2.0 * f);
        }
        out[0] = stopR[i] + (stopR[i + 1] - stopR[i]) * f;
        out[1] = stopG[i] + (stopG[i + 1] - stopG[i]) * f;
        out[2] = stopB[i] + (stopB[i + 1] - stopB[i]) * f;
    }

    private final double[] tmp = new double[3];

    public boolean tick(long nowMs, VehicleData v, Rgb[] factoryPalette, boolean daylight, Rgb contourOut) {
        boolean fresh = v != null && v.isFresh(nowMs);
        double dt = first ? 0.0 : (double) (nowMs - lastMs);
        if (dt < 0.0) dt = 0.0;
        if (dt > 1000.0) dt = 1000.0;
        lastMs = nowMs;

        /* --- Zielhelligkeit / target brightness --- */
        double bt;
        if (brSrc == SRC_NONE) {
            bt = brMax;
        } else {
            double f = norm(input(brSrc, v, fresh), brSrcMin, brSrcMax);
            if (brInvert) {
                f = 1.0 - f;
            }
            bt = brMin + (brMax - brMin) * f;
        }
        if (breath) {
            double ph = (nowMs - startMs) / (breathSec * 1000.0) * 2.0 * Math.PI;
            double k = 0.5 + 0.5 * Math.sin(ph);
            bt = bt * ((1.0 - breathDepth) + breathDepth * k);
        }
        if (!daylight) {
            bt = bt * nightFactor;
        }

        /* --- Zielfarbe / target colour --- */
        double tr, tg, tb;
        if (colorMode == MODE_CYCLE) {
            double sp = norm(input(SRC_SPEED, v, fresh), 0.0, 130.0);
            hue += (cycBase + cycSpeed * sp) * dt / 1000.0;
            hue = hue % 360.0;
            if (hue < 0.0) hue += 360.0;
            work.setHsv((float) hue, (float) cycSat, 1f);
            tr = work.r; tg = work.g; tb = work.b;
        } else if (colorMode == MODE_SOLID) {
            tr = stopR[0]; tg = stopG[0]; tb = stopB[0];
        } else {
            ramp(norm(input(colorSrc, v, fresh), colorMin, colorMax), tmp);
            tr = tmp[0]; tg = tmp[1]; tb = tmp[2];
        }

        /* --- Glaetten (exponentiell) / smoothing (exponential) --- */
        double a = smoothMs <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt / smoothMs);
        if (first) {
            a = 1.0;
        }
        if (colorMode == MODE_CYCLE) {
            smR = tr; smG = tg; smB = tb;                 /* die Rotation ist selbst schon gleitend */
        } else {
            smR += (tr - smR) * a;
            smG += (tg - smG) * a;
            smB += (tb - smB) * a;
        }
        smBright += (bt - smBright) * a;
        first = false;

        /* --- Bremsen-Ueberschreibung (sofort) / brake override (instant) --- */
        double outR = smR, outG = smG, outB = smB, outBright = smBright;
        if (brakeOn && fresh && v.driverBraking) {
            outR = brakeR; outG = brakeG; outB = brakeB;
            outBright = brakeBright * (daylight ? 1.0 : nightFactor);
            smR = outR; smG = outG; smB = outB; smBright = outBright;
        }

        /* --- Ausgabe: ideale Farbe -> Werksfarbe -> Helligkeit / output: ideal -> factory colour -> brightness --- */
        work.set((int) Math.floor(outR + 0.5), (int) Math.floor(outG + 0.5), (int) Math.floor(outB + 0.5));
        if (snap && factoryPalette != null && factoryPalette.length > 0) {
            work.set(Rgb.nearest(work, factoryPalette));
        }
        if (outBright < 0.0) outBright = 0.0;
        if (outBright > 1.0) outBright = 1.0;
        work.scale((float) outBright);

        contourOut.set(work);
        boolean changed = !work.equalsRgb(lastOut);
        if (changed || nowMs - lastSentMs >= 1000L) {
            lastOut.set(work);
            lastSentMs = nowMs;
            return true;
        }
        return false;
    }
}
