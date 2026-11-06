/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * "Atmosphere" - dritter Effekt, bewusst das Gegenteil von Momentum/
 * Momentum Select: NICHT tempo-/gas-/bremsreaktiv, sondern eine ruhige,
 * langsame Stimmung fuer alle im Auto statt ein Signal fuer den Fahrer.
 * BEWUSST eine komplette, unabhaengige Datei - kein Import/keine Referenz
 * auf Momentum.java oder MomentumSelect.java.
 *
 * Farbton: haengt von der Aussentemperatur ab (OutsideTempData, siehe
 * dort - Bordcomputer-Wert, derselbe wie das Thermometer-Icon im Kombi).
 * Kalt -> kuehles Blau, warm -> warmes Amber, dazwischen linear. Die
 * Temperatur selbst wird sehr langsam nachgezogen (mehrere Sekunden) -
 * hier soll nichts hektisch hinterherspringen. Noch kein echter Messwert
 * da (OutsideTempData.valid == false) -> neutraler Platzhalter-Wert
 * (TEMP_FALLBACK_C), bis der erste Update kommt.
 *
 * Helligkeit: kein Bezug zu Gas/Tempo/Bremse. Stattdessen ein sehr
 * langsames "Atmen" (Sinus zwischen einem Min- und einem Max-Wert, ~5s
 * Zyklus) - tagsueber etwas praesenter, nachts deutlich gedaempfter (siehe
 * DAY_BREATHE_MIN/MAX und NIGHT_BREATHE_MIN/MAX). Bremse wird hier ABSICHTLICH
 * NICHT ausgewertet (kein hartes Rot wie bei Momentum/Momentum Select) -
 * genau das wuerde den ruhigen Charakter sofort zerstoeren.
 *
 * Werksfarben-Snapping: wie Momentum (harter Snap auf die EINE naechste
 * Werksfarbe, Rgb.nearest-Prinzip), NICHT die Zwischenfarben-Mischung aus
 * Momentum Select - laut Live-Beobachtung im Fahrzeug traten mit dem
 * harten Snap bisher keinerlei Farbprobleme auf dem Retrofit-LED-Streifen
 * auf, das ist also die bewaehrte Basis fuer einen dritten Effekt.
 *
 * Kein Blinker-Echo (mehr): ein frueherer Entwurf liess die Kontur waehrend
 * des Blinkens im eigenen Takt amber pulsieren - inzwischen entfernt.
 * Grund: der Farbtransport (DSI -> MIBRoot -> Licht-ECU) laesst pro Zeit
 * nur eine Anfrage "in flight" zu, alles Weitere waehrenddessen wird
 * verworfen statt geblendet - ein Effekt, der versucht, mit dem echten
 * Blinktakt synchron zu bleiben, geraet dadurch zwangslaeufig aus der
 * Phase. Aus der Recherche von LuKa_dev (siehe luka-collection,
 * docs/ambient_modes_design.md, Abschnitt "Rejected") - dort wurde genau
 * das schon gemessen und aus Sicherheitsgruenden verworfen: ein
 * ausser-Takt-geratenes, blinkerfarbenes Pulsieren kann wie eine eigene,
 * verwirrende Warnleuchte wirken. Atmosphere reagiert deshalb jetzt auf
 * GAR NICHTS mehr aus dem Fahrverhalten - konsequent zu Ende gedacht.
 */
package de.mk.ambientfx.effects;

import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.OutsideTempData;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

public class Atmosphere implements Effect {

    /* Temperatur -> Farbton. */
    private static final float TEMP_COLD_C = -5f;
    private static final float TEMP_WARM_C = 30f;
    private static final float HUE_COLD = 210f; /* kuehles Blau */
    private static final float HUE_WARM = 30f;  /* warmes Amber */
    /** Platzhalter, solange OutsideTempData noch keinen echten Wert hat. */
    private static final float TEMP_FALLBACK_C = 15f;
    /** Sehr traege - Temperatur soll nicht hektisch nachziehen. */
    private static final float TC_TEMP_MS = 8000f;

    /* "Atmen": langsame Helligkeitsschwankung statt Reaktion. */
    private static final float BREATHE_PERIOD_MS = 5000f;
    private static final float DAY_BREATHE_MIN = 0.5f;
    private static final float DAY_BREATHE_MAX = 0.85f;
    private static final float NIGHT_BREATHE_MIN = 0.15f;
    private static final float NIGHT_BREATHE_MAX = 0.35f;

    private float smoothTempC;
    private long lastMs;
    private long startMs;
    private final Rgb hueRef = new Rgb();

    public void start(long nowMs) {
        smoothTempC = TEMP_FALLBACK_C;
        lastMs = nowMs;
        startMs = nowMs;
    }

    public boolean tick(long nowMs, VehicleData v, Rgb[] factoryPalette, boolean daylight, Rgb contourOut) {
        float dt = Math.max(1, (int) (nowMs - lastMs));
        lastMs = nowMs;

        float elapsed = nowMs - startMs;

        OutsideTempData t = AmbientFx.getInstance().getOutsideTemp();
        float targetTempC = (t != null && t.valid) ? t.celsius : TEMP_FALLBACK_C;
        float alphaT = dt / (TC_TEMP_MS + dt);
        smoothTempC += (targetTempC - smoothTempC) * alphaT;

        float hue = hueForTemp(smoothTempC);
        hueRef.setHsv(hue, 1f, 1f);

        float breatheMin = daylight ? DAY_BREATHE_MIN : NIGHT_BREATHE_MIN;
        float breatheMax = daylight ? DAY_BREATHE_MAX : NIGHT_BREATHE_MAX;
        float phase = (elapsed % BREATHE_PERIOD_MS) / BREATHE_PERIOD_MS;
        float breathe = 0.5f + 0.5f * (float) Math.sin(phase * 2.0 * Math.PI);
        float brightness = breatheMin + (breatheMax - breatheMin) * breathe;

        setDisplay(contourOut, hueRef, factoryPalette, brightness);
        return true;
    }

    private static float hueForTemp(float tempC) {
        float t = (tempC - TEMP_COLD_C) / (TEMP_WARM_C - TEMP_COLD_C);
        if (t < 0f) {
            t = 0f;
        }
        if (t > 1f) {
            t = 1f;
        }
        return HUE_COLD + (HUE_WARM - HUE_COLD) * t;
    }

    /** Snappt idealHue (falls Werksliste vorhanden) auf die naechste
     *  Werksfarbe - bewusst der harte Snap wie bei Momentum (siehe
     *  Klassenkommentar), erst danach die Helligkeit anwenden. */
    private static void setDisplay(Rgb out, Rgb idealHue, Rgb[] factoryPalette, float brightness) {
        if (factoryPalette != null && factoryPalette.length > 0) {
            out.set(nearest(idealHue, factoryPalette));
        } else {
            out.set(idealHue);
        }
        out.scale(brightness);
    }

    /** Eigene Kopie von Rgb.nearest (bewusst dupliziert - komplette
     *  Unabhaengigkeit von Momentum/MomentumSelect). */
    private static Rgb nearest(Rgb target, Rgb[] palette) {
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

    public int periodMs() {
        return 150;
    }
}
