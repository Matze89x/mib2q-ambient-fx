/*
 * Beispiel / Example: eigener AmbientFX-Effekt - ruhiges "Atmen", Farbton folgt dem Tempo.
 * Anleitung / Guide: docs/EIGENE-DESIGNS_CUSTOM-DESIGNS.md
 *
 * Kopieren nach / copy to:  java_patch/de/mk/ambientfx/effects/MeinEffekt.java
 * Bauen / build:            ./build_plugin.sh java_patch/de/mk/ambientfx/effects/MeinEffekt.java "Mein Effekt"
 *
 * Java 1.2: keine Generics, keine Enums, kein StringBuilder / no generics, no enums, no StringBuilder.
 */
package de.mk.ambientfx.effects;

import de.mk.ambientfx.Effect;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

public class MeinEffekt implements Effect {

    private final Rgb ideal = new Rgb();
    private long startMs;

    /* Wird einmal beim Aktivieren aufgerufen / called once on activation */
    public void start(long nowMs) {
        startMs = nowMs;
    }

    /* Pro Frame: Wunschfarbe der Kontur in contourOut schreiben / write the wanted contour colour into contourOut */
    public boolean tick(long nowMs, VehicleData vehicle, Rgb[] factoryPalette, boolean daylight, Rgb contourOut) {
        /* Tempo in km/h (speedUnit 1 = mph, hier der Einfachheit halber ignoriert / ignored here for simplicity) */
        float speed = vehicle.isFresh(nowMs) ? vehicle.speedKmh : 0f;
        if (speed > 130f) speed = 130f;

        /* Farbton: 200 Grad (Blau) bei Stillstand -> 320 Grad (Pink) bei 130 km/h / hue 200 (blue) at standstill -> 320 (pink) at 130 km/h */
        float hue = 200f + speed / 130f * 120f;

        /* Ideale Farbe auf die naechste Werksfarbe des Fahrzeugs "snappen" (siehe Handbuch, Abschnitt 2) / snap to the nearest factory colour */
        ideal.setHsv(hue, 1f, 1f);
        if (factoryPalette != null && factoryPalette.length > 0) {
            ideal.set(Rgb.nearest(ideal, factoryPalette));
        }

        /* Helligkeit: sanftes Atmen, ~4 s Zyklus; nachts deutlich dunkler / gentle breathing, ~4 s cycle; much dimmer at night */
        double phase = (nowMs - startMs) / 4000.0 * 2.0 * Math.PI;
        float breathe = 0.6f + 0.4f * (float) Math.sin(phase);
        float maxLevel = daylight ? 1.0f : 0.35f;
        ideal.scale(breathe * maxLevel);

        contourOut.set(ideal);
        return true;
    }

    /* Sende-Intervall in ms / send interval in ms */
    public int periodMs() {
        return 100;
    }
}
