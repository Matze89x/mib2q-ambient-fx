/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz / License: LICENSE.md
 *
 * DE: Laedt .ambiente-Dateien (z. B. aus dem Design Studio) ueber den ORIGINAL-Plugin-Lader EffectPluginLoader.loadOne() und fuehrt ein paar Takte
 *     mit Testwerten aus. DesignEffect liegt dabei NICHT im Klassenpfad, nur in den Plugins - wie im Fahrzeug.
 * EN: Loads .ambiente files (e.g. from the Design Studio) through the ORIGINAL plugin loader EffectPluginLoader.loadOne() and runs a few ticks
 *     with test values. DesignEffect is NOT on the classpath, only inside the plugins - like in the vehicle.
 *
 * Aufruf / usage:  designer/check_ambiente.sh meine.ambiente [weitere.ambiente ...]
 */
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.EffectPluginLoader;
import de.mk.ambientfx.FxCatalog;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LoaderCheck {
    public static void main(String[] a) throws Exception {
        if (a.length == 0) {
            System.out.println("Aufruf / usage: designer/check_ambiente.sh datei.ambiente [...]");
            System.exit(2);
        }
        Method load = EffectPluginLoader.class.getDeclaredMethod("loadOne", File.class);
        load.setAccessible(true);
        int before = FxCatalog.count();
        for (int i = 0; i < a.length; i++) {
            try {
                load.invoke(null, new File(a[i]));
                System.out.println("geladen / loaded: " + a[i]);
            } catch (java.lang.reflect.InvocationTargetException e) {
                System.out.println("ABGELEHNT / REJECTED: " + a[i] + " -> " + e.getCause());
                System.exit(1);
            }
        }
        int n = FxCatalog.count();
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        VehicleData v = (VehicleData) unsafe.allocateInstance(VehicleData.class);
        v.valid = true;
        Rgb out = new Rgb();
        for (int i = before; i < n; i++) {
            Effect e = FxCatalog.create(i);
            if (e == null) {
                System.out.println("FEHLER / ERROR: create(" + i + ") = null");
                System.exit(1);
            }
            System.out.println("Menue-Eintrag / menu entry: \"" + FxCatalog.label(i) + "\", Intervall / period " + e.periodMs() + " ms");
            e.start(5000L);
            for (int k = 0; k < 6; k++) {
                long now = 5000L + k * 2000L;
                v.lastUpdateMs = now;
                v.speedKmh = 30 * k;
                v.acceleratorPct = 15 * k;
                v.wheelAngle = 25 * k;
                v.driverBraking = false;
                e.tick(now, v, null, true, out);
                System.out.println("   Tempo/speed " + (30 * k) + " km/h, Gas/throttle " + (15 * k) + " % -> rgb(" + out.r + "," + out.g + "," + out.b + ")");
            }
        }
        System.out.println("OK");
    }
}
