/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
 *
 * DE: Beweis "1:1": laesst jede Effekt-Vorlage (Tpl*) mit den Originalwerten gegen die ECHTE mitgelieferte Effekt-Klasse laufen, mit denselben
 *     zufaelligen Eingaben (Tempo, Gas, Lenkwinkel, Bremse, Gangwahlhebel D/S, Fahrprofil, Tag/Nacht, Aussentemperatur, mph, ungueltige Daten,
 *     Werksfarbliste oder keine). Jede Abweichung wird gemeldet. Ausserdem schreibt der Test mit "gen <Ordner>" die Eingaben + Ausgaben der Vorlage
 *     fuer beliebige Werte-Aenderungen, damit die JavaScript-Engine des Studios dagegen geprueft werden kann (siehe tpl_parity.sh).
 * EN: Proof "1:1": runs every effect template (Tpl*) with the original values against the REAL bundled effect class, with the same random inputs.
 *     Any deviation is reported. With "gen <dir>" it also writes inputs + outputs of the template for arbitrary value changes, so the Studio's
 *     JavaScript engine can be checked against it (see tpl_parity.sh).
 *
 * Aufruf / usage:  java -cp <build/tpl_cls>:build/ambient_fx.jar:vendor/lsd.jar:<test-classes> TplEquivTest <build/tpl_cls>
 */
import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.DriveModeData;
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.OutsideTempData;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Random;

public class TplEquivTest {

    static sun.misc.Unsafe unsafe;

    static final String[][] EFFECTS = {
        {"Momentum", "de.mk.ambientfx.effects.Momentum", "de.mk.ambientfx.designer.TplMomentum"},
        {"MomentumSelect", "de.mk.ambientfx.effects.MomentumSelect", "de.mk.ambientfx.designer.TplMomentumSelect"},
        {"Atmosphere", "de.mk.ambientfx.effects.Atmosphere", "de.mk.ambientfx.designer.TplAtmosphere"},
        {"Scroll", "de.mk.ambientfx.effects.Scroll", "de.mk.ambientfx.designer.TplScroll"},
        {"Drive", "de.mk.ambientfx.effects.Drive", "de.mk.ambientfx.designer.TplDrive"},
    };

    /** Geschaetzte Werksfarbliste: 28 farbige (nach Farbton verteilt, leicht unregelmaessig) + 2 Weiss. Gleiche Liste in der JS-Engine. */
    public static Rgb[] palette() {
        Rgb[] p = new Rgb[30];
        Rgb t = new Rgb();
        for (int i = 0; i < 28; i++) {
            float hue = (i * 360f / 28f) + ((i % 3) - 1) * 2f;
            if (hue < 0f) {
                hue += 360f;
            }
            t.setHsv(hue, 0.92f - (i % 4) * 0.02f, 0.97f - (i % 5) * 0.01f);
            p[i] = new Rgb(t.r, t.g, t.b);
        }
        p[28] = new Rgb(255, 255, 255);
        p[29] = new Rgb(250, 248, 240);
        return p;
    }

    static void setField(Object o, String name, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    public static void main(String[] a) throws Exception {
        File tplDir = new File(a[0]);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        unsafe = (sun.misc.Unsafe) uf.get(null);

        AmbientFx fx = AmbientFx.getInstance();
        DriveModeData dm = (DriveModeData) unsafe.allocateInstance(DriveModeData.class);
        OutsideTempData ot = (OutsideTempData) unsafe.allocateInstance(OutsideTempData.class);
        setField(fx, "driveModeData", dm);
        setField(fx, "outsideTempData", ot);
        VehicleData v = (VehicleData) unsafe.allocateInstance(VehicleData.class);

        int total = 0, bad = 0;
        for (int e = 0; e < EFFECTS.length; e++) {
            String name = EFFECTS[e][0];
            File propDir = File.createTempFile("tpl", "dir");
            propDir.delete();
            propDir.mkdirs();
            PrintWriter pw = new PrintWriter(new FileOutputStream(new File(propDir, "ambientfx_design.properties")));
            pw.println("format=2");
            pw.println("template=" + EFFECTS[e][0].toLowerCase());
            pw.close();
            URLClassLoader cl = new URLClassLoader(new URL[] {tplDir.toURI().toURL(), propDir.toURI().toURL()}, TplEquivTest.class.getClassLoader());
            Effect orig = (Effect) Class.forName(EFFECTS[e][1]).newInstance();
            Effect tpl = (Effect) cl.loadClass(EFFECTS[e][2]).newInstance();
            if (orig.periodMs() != tpl.periodMs()) {
                System.out.println(name + ": periodMs verschieden");
                bad++;
            }
            int[] profiles = {DriveModeData.PROFILE_UNKNOWN, DriveModeData.PROFILE_COMFORT, DriveModeData.PROFILE_AUTO_NORMAL,
                              DriveModeData.PROFILE_DYNAMIC, DriveModeData.PROFILE_EFFICIENCY, DriveModeData.PROFILE_INDIVIDUAL};
            for (int pass = 0; pass < 2; pass++) {
                Rgb[] pal = pass == 0 ? palette() : null;
                Random rnd = new Random(1234 + e * 17 + pass);
                long now = 100000L;
                orig.start(now);
                tpl.start(now);
                float spd = 0f;
                int thr = 0, wheel = 0, gear = 4, profIdx = 2;
                boolean brake = false, day = true;
                int cnt = 0, mism = 0;
                Rgb o1 = new Rgb(), o2 = new Rgb();
                for (int i = 0; i < 6000; i++) {
                    now += (i % 7 == 0) ? 60 : 150;                    /* auch unregelmaessige Takte */
                    if (i % 23 == 0) { spd = rnd.nextInt(5) == 0 ? 0f : rnd.nextInt(190); }
                    spd += (rnd.nextFloat() - 0.5f) * 6f;
                    if (spd < 0f) { spd = 0f; }
                    if (i % 11 == 0) { thr = rnd.nextInt(3) == 0 ? 0 : rnd.nextInt(101); }
                    if (i % 17 == 0) { wheel = rnd.nextInt(700) - 350; }
                    if (i % 41 == 0) { brake = rnd.nextInt(4) == 0; }
                    if (i % 53 == 0) { gear = rnd.nextInt(3) == 0 ? 4 : (rnd.nextInt(2) == 0 ? 5 : 0); }
                    if (i % 211 == 0) { profIdx = rnd.nextInt(profiles.length); }
                    if (i % 97 == 0) { day = rnd.nextInt(3) != 0; }
                    boolean dmValid = !(profiles[profIdx] == DriveModeData.PROFILE_UNKNOWN && i % 2 == 0);
                    dm.valid = dmValid;
                    dm.profile = profiles[profIdx];
                    ot.valid = (i / 300) % 4 != 3;
                    ot.celsius = -15f + ((i / 5) % 56);
                    v.valid = (i % 400) < 380;
                    v.lastUpdateMs = (i % 61 == 0) ? now - 5000L : now;         /* ab und zu "veraltet" */
                    v.speedUnit = (i / 800) % 3 == 2 ? 1 : 0;
                    v.speedKmh = spd;
                    v.acceleratorPct = thr;
                    v.wheelAngle = wheel;
                    v.gearTransMode = gear;
                    v.driverBraking = brake;
                    boolean r1 = orig.tick(now, v, pal, day, o1);
                    boolean r2 = tpl.tick(now, v, pal, day, o2);
                    cnt++;
                    if (r1 != r2 || o1.r != o2.r || o1.g != o2.g || o1.b != o2.b) {
                        mism++;
                        if (mism <= 3) {
                            System.out.println("  " + name + " pass" + pass + " tick " + i + ": orig=" + o1.r + "," + o1.g + "," + o1.b
                                    + " tpl=" + o2.r + "," + o2.g + "," + o2.b);
                        }
                    }
                }
                System.out.println(name + (pass == 0 ? " (mit Werksliste)" : " (ohne Werksliste)") + ": " + cnt + " Takte, Abweichungen: " + mism);
                total += cnt;
                bad += mism;
            }
        }
        System.out.println("GESAMT: " + total + " Takte, Abweichungen: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
