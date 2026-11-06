/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * "Drive" - fuenfter Effekt, das "Flaggschiff" aus luka-collection/
 * AmbientSpeedComponent.java (MODE_DRIVE, computeDrive()). Gaspedal besitzt
 * die Farbton-Achse (Ruhe = Violet, Vollgas schwenkt Richtung warmes
 * Ember/Rot), Lenkeinschlag "spreizt" den Farbton zusaetzlich auf (mehr
 * Einschlag = staerker Richtung heisser gedreht), Effort (Gaspedal-Staerke)
 * und Lenkeinschlag zusammen bestimmen die Saettigung (in Ruhe blass/
 * silbrig-lavendel, unter Last/Kurvenfahrt voll gesaettigt).
 *
 * WICHTIG - bewusst NICHT 1:1 von Luka uebernommen (gleiche drei Gruende
 * wie bei Scroll.java, siehe dort fuer die volle Begruendung):
 *   1. Kein rohes HSV-Streaming - Werksfarben-Snap wie bei allen anderen
 *      eigenen Effekten (Retrofit-LED-Streifen-Sicherheit).
 *   2. Nur die Kontur (bauartbedingt durch Effect/AmbientFx), nie die
 *      Flaeche. Luka steuert bei Drive bewusst ZWEI leicht unterschiedliche
 *      Farben (Flaeche vs. Kontur, per Lenkeinschlag auseinandergespreizt) -
 *      da wir ohnehin nur einen Kanal haben, wird direkt NUR Lukas
 *      Kontur-Formel (hc/cv, siehe computeDrive()) als einziger Ausgang
 *      benutzt. Das entspricht exakt der Kontur-Rolle in seinem Original
 *      ("contour leads the ambient deeper into the sweep").
 *   3. BREMSE: bei Luka zieht Bremsen den Farbton Richtung Azurblau -
 *      bewusst NICHT uebernommen. In diesem Projekt gilt fahrzeugweit eine
 *      andere, bereits etablierte Konvention: Bremse = SOFORT volles
 *      (Werks-)Rot, wie bei Momentum/Momentum select/Scroll auch. Ein
 *      einzelner Effekt mit "Bremse = Blau" waere inkonsistent mit den
 *      anderen drei und potenziell verwirrend. Drive benutzt deshalb NUR
 *      das Gaspedal fuer die Farbton-Achse, Bremse ist die gleiche harte
 *      Rot-Ausnahme wie ueberall sonst.
 *
 * Lenkwinkel-Quelle: VehicleData.wheelAngle (neu seit 26.09.2026, exakt fuer
 * diesen Effekt angebunden - siehe VehicleData.java). Per javap gegen
 * unser eigenes vendor/lsd.jar (MU1329) bestaetigt identisch zu Lukas
 * MU1316 vorhanden (DynamicVehicleInfoHighFrequent.wheelAngle, int, ca.
 * +-520 laut Lukas Messung).
 *
 * DRIVE_HUE_CAP ist bei Luka ausdruecklich als "SAFETY INVARIANT, not a
 * taste knob" markiert (verhindert, dass Vollgas + Vollausschlag den
 * berechneten Farbton ungewollt in Gruen kippen laesst) - 1:1 uebernommen,
 * bevor auf Werksfarbe gesnappt wird: ein falsch umgeschlagener Hue wuerde
 * sonst einfach auf die naechste (falsche) Werksfarbe snappen, der Cap
 * verhindert das schon vorher.
 *
 * Tag/Nacht: bei Luka nicht vorhanden (seine Komponente kennt gar kein
 * Tag/Nacht-Signal). Eigene, einfache Ergaenzung passend zu unserer
 * Effect-Schnittstelle: nachts wird die berechnete Helligkeit pauschal
 * halbiert (kein eigenes Gas-Verhalten wie bei Momentum, keine Breathing-
 * Kurve wie bei Atmosphere - Drive lebt ohnehin schon vom Gaspedal/
 * Lenkeinschlag, ein einfacher Nachtfaktor reicht).
 */
package de.mk.ambientfx.effects;

import de.mk.ambientfx.Effect;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

public class Drive implements Effect {

    /* Gaspedal-Ansprechzeiten (ms) - schnell hoch, langsamer wieder runter,
     * wie bei Momentum/Momentum select seit 26.09.2026 (siehe dort). */
    private static final float TC_THROTTLE_UP_MS = 100f;
    private static final float TC_THROTTLE_DOWN_MS = 400f;
    /** Lenkwinkel-Glaettung (ms) - traeger als das Gaspedal, damit ein
     *  einzelnes kurzes Gegenlenken nicht sofort durchschlaegt. */
    private static final float TC_STEER_MS = 200f;

    /* Tunables 1:1 aus Lukas computeDrive() uebernommen (Gradzahlen/Werte). */
    private static final float DRIVE_BASE_HUE = 285.0f;       /* Violet in Ruhe */
    private static final float DRIVE_THROTTLE_SWEEP = 95.0f;  /* -> 380 (=20), durch Rot Richtung Ember */
    private static final float DRIVE_SAT_REST = 0.30f;
    private static final float DRIVE_SPLIT_DEG = 65.0f;       /* Kontur schwenkt mit Lenkeinschlag weiter */
    private static final float DRIVE_CONTOUR_BOOST = 0.25f;
    /** SICHERHEITS-KLAMMER, keine Geschmacksfrage (Original-Kommentar von
     *  Luka uebernommen): ohne diesen Deckel wuerde Vollgas + Vollausschlag
     *  den Farbton ungewollt Richtung Gruen kippen lassen. Nicht anheben. */
    private static final float DRIVE_HUE_CAP = 385.0f;
    private static final float DRIVE_V_REST = 0.60f;
    private static final float DRIVE_V_SWEEP = 0.40f;
    private static final float DRIVE_V_FLOOR = 0.15f;

    /** Beobachteter Gaspedal-Deckel auf diesem Fahrzeugtyp (Lukas Messung:
     *  acceleratorPosition erreicht nie 100, sondern saettigt bei ~66/100) -
     *  ohne diese Normierung wuerde Vollgas nie die obere Grenze der
     *  Farbrampe erreichen. Unbestaetigt auf unserem eigenen Fahrzeug,
     *  aber risikofrei uebernehmbar: schlimmstenfalls saettigt die Rampe
     *  etwas frueher als gedacht, kein Fehlerfall. */
    private static final float PEDAL_FULL = 0.66f;

    private float throttleEma;
    private float steerEma;
    private long lastMs;
    private final Rgb hueRef = new Rgb();

    public void start(long nowMs) {
        throttleEma = 0f;
        steerEma = 0f;
        lastMs = nowMs;
    }

    public boolean tick(long nowMs, VehicleData v, Rgb[] factoryPalette, boolean daylight, Rgb contourOut) {
        float dt = Math.max(1, (int) (nowMs - lastMs));
        lastMs = nowMs;

        int accelPct = 0;
        int wheelAngle = 0;
        boolean braking = false;
        if (v != null && v.isFresh(nowMs)) {
            accelPct = v.acceleratorPct;
            wheelAngle = v.wheelAngle;
            braking = v.driverBraking;
        }

        if (braking) {
            hueRef.setHsv(0f, 1f, 1f);
            setDisplay(contourOut, hueRef, factoryPalette, 1f);
            return true;
        }

        float targetThrottle = clamp01(accelPct / 100f);
        float tcThrottle = targetThrottle > throttleEma ? TC_THROTTLE_UP_MS : TC_THROTTLE_DOWN_MS;
        throttleEma += (dt / (tcThrottle + dt)) * (targetThrottle - throttleEma);

        float targetSteer = (float) Math.sqrt(clamp01(Math.abs(wheelAngle) / 180f));
        steerEma += (dt / (TC_STEER_MS + dt)) * (targetSteer - steerEma);

        float t = clamp01(throttleEma / PEDAL_FULL);

        float h = DRIVE_BASE_HUE + DRIVE_THROTTLE_SWEEP * t;
        float satDrv = t > steerEma ? t : steerEma;
        float sat = DRIVE_SAT_REST + (1f - DRIVE_SAT_REST) * satDrv;
        float val = DRIVE_V_REST + DRIVE_V_SWEEP * t;
        if (val < DRIVE_V_FLOOR) {
            val = DRIVE_V_FLOOR;
        }
        if (val > 1f) {
            val = 1f;
        }

        float hc = h + DRIVE_SPLIT_DEG * steerEma;
        if (hc > DRIVE_HUE_CAP) {
            hc = DRIVE_HUE_CAP;
        }
        float cv = val + DRIVE_CONTOUR_BOOST * steerEma;
        if (cv > 1f) {
            cv = 1f;
        }

        float brightness = daylight ? cv : cv * 0.5f;
        hueRef.setHsv(hc, sat, 1f);
        setDisplay(contourOut, hueRef, factoryPalette, brightness);
        return true;
    }

    private static float clamp01(float x) {
        if (x < 0f) {
            return 0f;
        }
        if (x > 1f) {
            return 1f;
        }
        return x;
    }

    /** Snappt idealHue (falls Werksliste vorhanden) hart auf die naechste
     *  Werksfarbe - identisches Prinzip wie Momentum/Scroll (siehe dort fuer
     *  die Begruendung ueber den Retrofit-LED-Streifen), erst DANACH die
     *  Helligkeit anwenden. Eigene Kopie statt Aufruf - komplette
     *  Unabhaengigkeit von den anderen Effekt-Dateien. */
    private static void setDisplay(Rgb out, Rgb idealHue, Rgb[] factoryPalette, float brightness) {
        if (factoryPalette != null && factoryPalette.length > 0) {
            out.set(nearest(idealHue, factoryPalette));
        } else {
            out.set(idealHue);
        }
        out.scale(brightness);
    }

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
