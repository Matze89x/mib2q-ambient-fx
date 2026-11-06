/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
 *
 * DE: Rechnet die erzeugten Java-Vorlagen (Tpl*) fuer die Faelle aus einem Ordner (je Fall eine .properties-Datei = Design-Datei, "<name>.pal" = mit
 *     Werksliste) mit den Eingaben aus inputs.txt und gibt je Takt "<name> <i> r g b" aus. Gegenstueck: designer/tpl_engine.js (tpl_parity.sh vergleicht).
 * EN: Runs the generated Java templates (Tpl*) for the cases in a directory (one .properties per case = design file, "<name>.pal" = with factory
 *     list) with the inputs from inputs.txt and prints "<name> <i> r g b" per tick. Counterpart: designer/tpl_engine.js (tpl_parity.sh compares).
 */
import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.DriveModeData;
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.OutsideTempData;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Properties;

public class TplParityTest {
    public static void main(String[] a) throws Exception {
        File tplDir = new File(a[0]);
        File caseDir = new File(a[1]);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        AmbientFx fx = AmbientFx.getInstance();
        DriveModeData dm = (DriveModeData) unsafe.allocateInstance(DriveModeData.class);
        OutsideTempData ot = (OutsideTempData) unsafe.allocateInstance(OutsideTempData.class);
        Field f1 = fx.getClass().getDeclaredField("driveModeData"); f1.setAccessible(true); f1.set(fx, dm);
        Field f2 = fx.getClass().getDeclaredField("outsideTempData"); f2.setAccessible(true); f2.set(fx, ot);
        VehicleData v = (VehicleData) unsafe.allocateInstance(VehicleData.class);
        Rgb[] pal = TplEquivTest.palette();

        ArrayList in = new ArrayList();
        BufferedReader br = new BufferedReader(new FileReader(new File(caseDir, "inputs.txt")));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.length() > 0) { in.add(line.split(" ")); }
        }
        br.close();

        File[] fs = caseDir.listFiles();
        java.util.Arrays.sort(fs);
        for (int k = 0; k < fs.length; k++) {
            String n = fs[k].getName();
            if (!n.endsWith(".properties")) { continue; }
            String name = n.substring(0, n.length() - ".properties".length());
            boolean usePal = new File(caseDir, name + ".pal").exists();
            Properties p = new Properties();
            FileInputStream fin = new FileInputStream(fs[k]);
            p.load(fin);
            fin.close();
            String key = p.getProperty("template");
            String cls = null;
            if ("momentum".equals(key)) cls = "TplMomentum";
            else if ("momentumselect".equals(key)) cls = "TplMomentumSelect";
            else if ("atmosphere".equals(key)) cls = "TplAtmosphere";
            else if ("scroll".equals(key)) cls = "TplScroll";
            else if ("drive".equals(key)) cls = "TplDrive";
            File propDir = File.createTempFile("tplp", "dir");
            propDir.delete();
            propDir.mkdirs();
            FileOutputStream fo = new FileOutputStream(new File(propDir, "ambientfx_design.properties"));
            FileInputStream fin2 = new FileInputStream(fs[k]);
            byte[] buf = new byte[4096];
            int r;
            while ((r = fin2.read(buf)) > 0) { fo.write(buf, 0, r); }
            fin2.close();
            fo.close();
            URLClassLoader cl = new URLClassLoader(new URL[] {tplDir.toURI().toURL(), propDir.toURI().toURL()}, TplParityTest.class.getClassLoader());
            Effect e = (Effect) cl.loadClass("de.mk.ambientfx.designer." + cls).newInstance();
            long now = 100000L;
            e.start(now);
            Rgb out = new Rgb();
            for (int i = 0; i < in.size(); i++) {
                String[] t = (String[]) in.get(i);
                now += Long.parseLong(t[0]);
                v.valid = "1".equals(t[1]);
                v.lastUpdateMs = "1".equals(t[2]) ? now - 5000L : now;
                v.speedUnit = Integer.parseInt(t[3]);
                v.speedKmh = Float.parseFloat(t[4]);
                v.acceleratorPct = Integer.parseInt(t[5]);
                v.wheelAngle = Integer.parseInt(t[6]);
                v.gearTransMode = Integer.parseInt(t[7]);
                v.driverBraking = "1".equals(t[8]);
                dm.valid = "1".equals(t[9]);
                dm.profile = Integer.parseInt(t[10]);
                ot.valid = "1".equals(t[11]);
                ot.celsius = Float.parseFloat(t[12]);
                boolean day = "1".equals(t[13]);
                e.tick(now, v, usePal ? pal : null, day, out);
                System.out.println(name + " " + i + " " + out.r + " " + out.g + " " + out.b);
            }
        }
    }
}
