/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - kleine RGB-Hilfsklasse mit HSV-Umrechnung und Blending.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

public final class Rgb {

    public int r;
    public int g;
    public int b;

    public Rgb() {}

    public Rgb(int r, int g, int b) {
        set(r, g, b);
    }

    public void set(int r, int g, int b) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
    }

    public void set(Rgb o) {
        this.r = o.r;
        this.g = o.g;
        this.b = o.b;
    }

    public boolean equalsRgb(Rgb o) {
        return o != null && o.r == r && o.g == g && o.b == b;
    }

    /** Differenz-Summe (0..765) - zum Unterdruecken minimaler Aenderungen. */
    public int distance(Rgb o) {
        return Math.abs(r - o.r) + Math.abs(g - o.g) + Math.abs(b - o.b);
    }

    /** Skaliert alle Kanaele mit f (0..1). */
    public void scale(float f) {
        set(Math.round(r * f), Math.round(g * f), Math.round(b * f));
    }

    /**
     * HSV -> RGB. h in Grad (0..360, wird normalisiert), s und v in 0..1.
     */
    public void setHsv(float h, float s, float v) {
        h = h % 360f;
        if (h < 0f) {
            h += 360f;
        }
        float c = v * s;
        float hp = h / 60f;
        float x = c * (1f - Math.abs((hp % 2f) - 1f));
        float r1 = 0f, g1 = 0f, b1 = 0f;
        if (hp < 1f) {
            r1 = c; g1 = x;
        } else if (hp < 2f) {
            r1 = x; g1 = c;
        } else if (hp < 3f) {
            g1 = c; b1 = x;
        } else if (hp < 4f) {
            g1 = x; b1 = c;
        } else if (hp < 5f) {
            r1 = x; b1 = c;
        } else {
            r1 = c; b1 = x;
        }
        float m = v - c;
        set(Math.round((r1 + m) * 255f), Math.round((g1 + m) * 255f), Math.round((b1 + m) * 255f));
    }

    /** Lineares Mischen zweier Farben, t in 0..1 (0 = a, 1 = b). */
    public void lerp(Rgb a, Rgb b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        set(Math.round(a.r + (b.r - a.r) * t),
            Math.round(a.g + (b.g - a.g) * t),
            Math.round(a.b + (b.b - a.b) * t));
    }

    /**
     * Blendet ueber eine Farbpalette: pos in 0..1 laeuft einmal durch alle
     * Stuetzstellen (zyklisch, d.h. letzte -> erste).
     */
    public void palette(Rgb[] pal, float pos) {
        int n = pal.length;
        if (n == 0) {
            return;
        }
        if (n == 1) {
            set(pal[0]);
            return;
        }
        pos = pos - (float) Math.floor(pos);
        float f = pos * n;
        int i = (int) Math.floor(f);
        float t = f - i;
        Rgb a = pal[i % n];
        Rgb b = pal[(i + 1) % n];
        lerp(a, b, smooth(t));
    }

    /** Naechste Farbe aus einer Palette (z.B. Audi-Werksfarbliste), nach Manhattan-Distanz. */
    public static Rgb nearest(Rgb target, Rgb[] palette) {
        Rgb best = palette[0];
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int d = target.distance(palette[i]);
            if (d < bestDist) {
                bestDist = d;
                best = palette[i];
            }
        }
        return best;
    }

    /** Smoothstep fuer weiche Uebergaenge. */
    public static float smooth(float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(24);
        sb.append('(').append(r).append(',').append(g).append(',').append(b).append(')');
        return sb.toString();
    }
}
