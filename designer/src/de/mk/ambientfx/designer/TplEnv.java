/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md / License: CC BY-NC-SA 4.0, see LICENSE.md
 *
 * DE: Gemeinsame Hilfe der Effekt-Vorlagen (Tpl*.java): liest die Design-Datei, begrenzt Werte, holt Fahrprofil und Aussentemperatur
 *     auf demselben Weg wie die mitgelieferten Effekte. Java 1.2 kompatibel (keine Generics, kein Autoboxing).
 * EN: Shared helper of the effect templates (Tpl*.java): reads the design file, clamps values, gets drive profile and outside temperature
 *     the same way as the bundled effects. Java 1.2 compatible (no generics, no autoboxing).
 */
package de.mk.ambientfx.designer;

import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.DriveModeData;
import de.mk.ambientfx.OutsideTempData;

import java.io.InputStream;
import java.util.Properties;

public final class TplEnv {

    public static final String DESIGN_FILE = "/ambientfx_design.properties";

    private TplEnv() {}

    /** Liest die Design-Datei dieses Plugins; wirft bei fehlender/fremder Datei (der Plugin-Lader ueberspringt dann das Plugin). */
    public static Properties load(String expectedTemplateClass) {
        InputStream in = TplEnv.class.getResourceAsStream(DESIGN_FILE);
        if (in == null) {
            ClassLoader cl = TplEnv.class.getClassLoader();
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
            throw new IllegalStateException("ambientfx_design.properties nicht lesbar: " + e);
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignore) {
            }
        }
        if (!"2".equals(p.getProperty("format", "").trim())) {
            throw new IllegalStateException("Design-Datei: format=2 erwartet");
        }
        String t = p.getProperty("template", "").trim();
        if (!expectedTemplateClass.equals(t)) {
            throw new IllegalStateException("Design-Datei gehoert zu einer anderen Vorlage: " + t);
        }
        return p;
    }

    private static String raw(Properties p, String name) {
        String s = p.getProperty("p." + name);
        return s == null ? null : s.trim();
    }

    public static float f(Properties p, String name, float def, float min, float max) {
        String s = raw(p, name);
        if (s == null) {
            return def;
        }
        float v;
        try {
            v = Float.valueOf(s).floatValue();
        } catch (NumberFormatException e) {
            return def;
        }
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return def;
        }
        return v < min ? min : (v > max ? max : v);
    }

    public static long l(Properties p, String name, long def, long min, long max) {
        String s = raw(p, name);
        if (s == null) {
            return def;
        }
        long v;
        try {
            v = (long) Double.valueOf(s).doubleValue();
        } catch (NumberFormatException e) {
            return def;
        }
        return v < min ? min : (v > max ? max : v);
    }

    public static int i(Properties p, String name, int def, int min, int max) {
        return (int) l(p, name, def, min, max);
    }

    public static boolean b(Properties p, String name, boolean def) {
        String s = raw(p, name);
        if (s == null) {
            return def;
        }
        if ("true".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s)) {
            return false;
        }
        return def;
    }

    /** Wie im Original: aktives Drive-Select-Profil, sonst PROFILE_UNKNOWN. */
    public static int profile() {
        DriveModeData dm = AmbientFx.getInstance().getDriveMode();
        return (dm != null && dm.valid) ? dm.profile : DriveModeData.PROFILE_UNKNOWN;
    }

    /** Wie im Original: Aussentemperatur, sonst der Ersatzwert. */
    public static float tempC(float fallback) {
        OutsideTempData t = AmbientFx.getInstance().getOutsideTemp();
        return (t != null && t.valid) ? t.celsius : fallback;
    }
}
