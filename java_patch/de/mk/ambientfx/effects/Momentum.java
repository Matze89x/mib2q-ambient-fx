/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * "Momentum" - einziger aktiver Effekt (siehe FxCatalog-Kommentar: bewusst
 * auf einen konzentriert). Wirkt NUR auf die Kontur/Linie - die Flaeche
 * bleibt komplett dem manuellen Farbregler im MIB ueberlassen.
 *
 * Farbe der Kontur = Durchlauf durch ALLE farbigen Werksfarben des Fahrzeugs
 * ueber das Tempo (neu gestaltet am 03.10.2026): bei 0 km/h Rot, dann in
 * Richtung Orange/Gelb/Gruen/Cyan/Blau/Violett/Rosa-Rot, bei 130 km/h wieder
 * Rot (geschlossener Kreis, darueber bleibt es Rot). Zwischen zwei
 * benachbarten Werksfarben wird weich ueberblendet, es gibt also keine harten
 * Spruenge mehr - alle 10 km/h laufen gut zwei Werksfarben durch (13 Schritte
 * a 10 km/h von 0 bis 130). Die Rohgeschwindigkeit wird ~600 ms geglaettet.
 *
 * Die Farbliste kommt zur Laufzeit aus dem Fahrzeug (factoryPalette, siehe
 * Effect.java, 30 Eintraege: 28 farbige + 2 Weiss). Weiss wird ausgelassen, die
 * farbigen werden nach Farbton ab dem Rot sortiert (buildRing()) - die Tabelle
 * passt sich damit automatisch an, auch wenn ein anderes Fahrzeug eine andere
 * Liste meldet. Fehlt die Liste ganz, faellt der Effekt auf den reinen
 * Regenbogen-Farbton zurueck ("fail visible").
 *
 * Frueher (bis 02.10.2026): der ideale Regenbogen-Farbton wurde hart auf die
 * EINE naechste Werksfarbe gesnappt (Rgb.nearest) - wegen des Retrofit-LED-
 * Streifens vorne. Das fuehrte zu groben Spruengen und liess viele Gruen-/Gelb-
 * Toene der Liste ungenutzt. Nach den Live-Tests mit Scroll (freies Streamen
 * ohne jedes Flackern) bewusst aufgegeben. Falls es doch flackert: SMOOTH_COLORS
 * auf false -> dann nur noch echte Werksfarben in 10-km/h-Stufen.
 *
 * Die Bremse (sofort Rot) snappt weiterhin hart auf die naechste Werks-Rot-
 * Variante. Die Helligkeit wird auch hier erst NACH der Farbwahl
 * aufmultipliziert (setDisplay()/scale), damit Gas-Reaktivitaet und Farbe
 * unabhaengig bleiben.
 *
 * Helligkeit der Kontur = Tag/Nacht (daylight-Parameter, siehe Effect.java -
 * NICHT VehicleData.lowBeamOn, das blieb im Test immer false) + Gaspedal
 * (NICHT Tempo):
 *   TAG (daylight=true) -> immer 100% Helligkeit, unabhaengig vom Gaspedal -
 *     bewusst unveraendert, Nutzer-Vorgabe 26.09.2026.
 *   NACHT (daylight=false) -> seit 26.09.2026 (zweite Kurve, Nutzer-Vorgabe):
 *     5% Helligkeit bei 0% Gas (nie ganz aus), steigt bis 50% Gas linear auf
 *     100% an, bleibt darueber (50-100% Gas) bei 100% - erreicht die volle
 *     Helligkeit also schon bei halbem Gas statt erst bei Vollgas, loest
 *     sich dadurch feiner im alltagsrelevanten unteren/mittleren Gasbereich
 *     auf (siehe ACCEL_SATURATE_PCT/NIGHT_BRIGHT_FLOOR unten). Vorherige
 *     Kurve war 25-100% linear ueber den GESAMTEN Gasbereich.
 *
 * Seit 26.09.2026: die Helligkeits-Glaettung ist ASYMMETRISCH statt der
 * frueheren einen 250ms-Zeitkonstante fuer beide Richtungen (Idee aus
 * luka-collection/AmbientSpeedComponent.java, computeMomentum()/
 * updateThrottleEma() - dort fuer den Gasansprech-Farbton benutzt, hier
 * fuer unsere Helligkeit uebernommen): schnell HELLER (TC_BRIGHT_UP) beim
 * Gasgeben, spuerbar langsamer wieder DUNKLER (TC_BRIGHT_DOWN) beim
 * Loslassen - "atmet aus" statt abrupt zu verblassen, wirkt lebendiger als
 * eine symmetrische Glaettung. Bleibt ein reiner Tiefpass (kein Feder-/PID-
 * Modell), kann also grundsaetzlich nicht ueberschwingen.
 *
 * Bremse: sobald der Fahrer bremst, wird die Kontur sofort (ohne jeden
 * Uebergang) auf volles Rot bei voller Helligkeit gesetzt - unabhaengig
 * von Tempo, Gaspedal und Tag/Nacht (auch das wird auf die naechste
 * Werks-Rot-Variante gesnappt).
 *
 * Gangwahlhebel D -> S (seit 30.09.2026, aus dem Testeffekt "MomentumGDS"
 * uebernommen): beim WECHSEL auf S (steigende Flanke, nicht dauerhaft) spielt
 * einmalig eine Eintrittssequenz - "wie Bremsen, nur in Weiss":
 *   1. SOFORT Weiss bei 100% Helligkeit (Tag und Nacht gleich, ohne Rampe),
 *      FLASH_HOLD_MS lang gehalten
 *   2. dann FLASH_FADE_MS lang weiches Ueberblenden von Weiss zu dem, was
 *      Momentum gerade laufend berechnet (Tempo-Farbe + Gas-Helligkeit, live
 *      mitverfolgt - kein statisches Ziel; nachts blendet es also auch auf die
 *      dann gerade gueltige, dunklere Helligkeit herunter)
 *   3. ab da 1:1 normales Momentum, bis erneut auf D und wieder auf S geschaltet
 *      wird. Zurueck auf D: sofort normales Momentum, KEIN Uebergang.
 * Nicht im Drive-Select-Profil Dynamic - dort steht das Getriebe ohnehin
 * dauerhaft auf S, das Signal waere bedeutungslos. Die Bremse hat weiterhin
 * Vorrang vor allem (auch vor der Sequenz). Die Sequenz laeuft nach Uhrzeit,
 * nicht nach Tick-Zahl, und ueberlebt daher kurzes Bremsen mittendrin. Die
 * fruehere schwache 20%-Weiss-Sekunde davor entfiel auf Nutzerwunsch.
 * Braucht dafuer DriveModeData (nur fuer die Dynamic-Ausschlusspruefung, kein
 * eigenes Farbschema wie bei Momentum select).
 */
package de.mk.ambientfx.effects;

import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.DriveModeData;
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

public class Momentum implements Effect {

    /** Tempo, bei dem der Farbkreis einmal durch ist (wieder Rot) - danach bleibt es Rot.
     *  Seit 03.10.2026 130 statt 100 km/h, in SPEED_STEP_KMH-Schritten (13 Schritte). */
    private static final float RAINBOW_SPAN_KMH = 130f;
    private static final float SPEED_STEP_KMH = 10f;

    /** true = stufenlos zwischen benachbarten Werksfarben ueberblenden (Zwischentoene
     *  sind dann KEINE reinen Werksfarben); false = nur echte Werksfarben in harten
     *  SPEED_STEP_KMH-Stufen. Siehe Klassenkommentar. */
    private static final boolean SMOOTH_COLORS = true;

    /** Ab welcher Saettigung/Helligkeit ein Werkslisten-Eintrag als "farbig" zaehlt
     *  (Weiss-Eintraege fallen heraus). */
    private static final float RING_MIN_SAT = 0.35f;
    private static final float RING_MIN_VAL = 0.25f;

    /* Asymmetrische Helligkeits-Zeitkonstanten (ms) - siehe Klassenkommentar. */
    private static final float TC_BRIGHT_UP = 120f;
    private static final float TC_BRIGHT_DOWN = 350f;

    /* Nacht-Helligkeitskurve (siehe Klassenkommentar) - unter diesem
     * Gaspedal-Prozentsatz linear, darueber gedeckelt auf 100%. */
    private static final float NIGHT_ACCEL_SATURATE_PCT = 50f;
    private static final float NIGHT_BRIGHT_FLOOR = 0.05f;

    /* D->S-Eintrittssequenz (siehe Klassenkommentar). */
    private static final long FLASH_HOLD_MS = 1000L;
    private static final long FLASH_FADE_MS = 2000L;

    private float smoothV;
    private float smoothBrightness;
    private long lastMs;
    private final Rgb hueRef = new Rgb();
    private final Rgb normalOut = new Rgb();
    private final Rgb flashWhite = new Rgb(255, 255, 255);

    /** Zeitpunkt (nowMs) des letzten Wechsels auf S; -1 = keine aktive Sequenz. */
    private long sportStartMs = -1L;
    private boolean wasSportGear = false;

    public void start(long nowMs) {
        smoothV = 0f;
        smoothBrightness = 0f;
        lastMs = nowMs;
        sportStartMs = -1L;
        wasSportGear = false;
    }

    public boolean tick(long nowMs, VehicleData v, Rgb[] factoryPalette, boolean daylight, Rgb contourOut) {
        float dt = Math.max(1, (int) (nowMs - lastMs));
        lastMs = nowMs;

        float kmh = 0f;
        int accelPct = 0;
        boolean braking = false;
        int gearMode = 0;
        if (v != null && v.isFresh(nowMs)) {
            kmh = v.speedKmh;
            if (v.speedUnit == 1) {
                kmh = kmh * 1.609f;
            }
            accelPct = v.acceleratorPct;
            braking = v.driverBraking;
            gearMode = v.gearTransMode;
        }
        boolean night = !daylight;

        if (braking) {
            smoothV = kmh;
            smoothBrightness = 1f;
            hueRef.setHsv(0f, 1f, 1f);
            setDisplay(contourOut, hueRef, factoryPalette, 1f);
            return true;
        }

        DriveModeData dm = AmbientFx.getInstance().getDriveMode();
        int profile = (dm != null && dm.valid) ? dm.profile : DriveModeData.PROFILE_UNKNOWN;
        boolean sportGear = gearMode == VehicleData.GEAR_TRANS_MODE_S
                && profile != DriveModeData.PROFILE_DYNAMIC;

        float alphaV = dt / (600f + dt);
        smoothV += (kmh - smoothV) * alphaV;

        float accelT = accelPct / NIGHT_ACCEL_SATURATE_PCT;
        if (accelT > 1f) {
            accelT = 1f;
        }
        float targetBrightness = night ? (NIGHT_BRIGHT_FLOOR + (1f - NIGHT_BRIGHT_FLOOR) * accelT) : 1f;
        float tcBright = targetBrightness > smoothBrightness ? TC_BRIGHT_UP : TC_BRIGHT_DOWN;
        float alphaB = dt / (tcBright + dt);
        smoothBrightness += (targetBrightness - smoothBrightness) * alphaB;

        if (factoryPalette != ringSrc) {
            buildRing(factoryPalette);
        }
        float u = speedFraction(smoothV);
        if (ring != null) {
            ringAt(normalOut, u * ringCount, !SMOOTH_COLORS);
            normalOut.scale(smoothBrightness);
        } else {
            /* keine Werksliste -> reiner Farbton wie frueher (ohne Snap) */
            hueRef.setHsv(u * 360f, 1f, 1f);
            setDisplay(normalOut, hueRef, factoryPalette, smoothBrightness);
        }

        /* D->S-Eintrittssequenz ueberlagert das Ergebnis, siehe Klassenkommentar. */
        if (sportGear) {
            if (!wasSportGear) {
                sportStartMs = nowMs;
            }
            long el = nowMs - sportStartMs;
            if (el < FLASH_HOLD_MS) {
                contourOut.set(flashWhite);
            } else if (el < FLASH_HOLD_MS + FLASH_FADE_MS) {
                float t = (el - FLASH_HOLD_MS) / (float) FLASH_FADE_MS;
                contourOut.lerp(flashWhite, normalOut, t);
            } else {
                contourOut.set(normalOut);
            }
        } else {
            contourOut.set(normalOut);
        }
        wasSportGear = sportGear;
        return true;
    }

    /* ---- Farbring aus der Werksliste (seit 03.10.2026, siehe Klassenkommentar) ---- */

    /** Palette, aus der der Ring zuletzt gebaut wurde (Referenzvergleich - die
     *  Engine reicht dasselbe Array durch, bis das Fahrzeug eine neue Liste meldet). */
    private Rgb[] ringSrc;
    /** Die farbigen Werksfarben (ohne Weiss), aufsteigend nach Farbton ab Rot;
     *  null = keine brauchbare Werksliste -> Fallback auf reinen Farbton. */
    private Rgb[] ring;
    /** Farbton je Ring-Eintrag in Grad RELATIV zum Start-Rot (0 = Start). */
    private float[] ringHue;
    private int ringCount;

    private void buildRing(Rgb[] pal) {
        ringSrc = pal;
        ring = null;
        ringHue = null;
        ringCount = 0;
        if (pal == null) {
            return;
        }
        Rgb[] c = new Rgb[pal.length];
        float[] h = new float[pal.length];
        int n = 0;
        for (int i = 0; i < pal.length; i++) {
            Rgb p = pal[i];
            if (p == null) {
                continue;
            }
            int max = Math.max(p.r, Math.max(p.g, p.b));
            int min = Math.min(p.r, Math.min(p.g, p.b));
            float sat = max == 0 ? 0f : (max - min) / (float) max;
            float val = max / 255f;
            if (sat >= RING_MIN_SAT && val >= RING_MIN_VAL) {
                c[n] = p;
                h[n] = hueOf(p);
                n++;
            }
        }
        if (n < 2) {
            return;
        }
        /* Start = die Farbe, die dem reinen Rot (0 Grad) am naechsten liegt */
        int start = 0;
        float bestD = 360f;
        for (int i = 0; i < n; i++) {
            float d = Math.min(h[i], 360f - h[i]);
            if (d < bestD) {
                bestD = d;
                start = i;
            }
        }
        float[] rel = new float[n];
        for (int i = 0; i < n; i++) {
            float r = h[i] - h[start];
            if (r < 0f) {
                r += 360f;
            }
            rel[i] = r;
        }
        /* Einfuegesortierung nach relativem Farbton (n ~ 30, einmal pro Palette) */
        for (int i = 1; i < n; i++) {
            Rgb kc = c[i];
            float kr = rel[i];
            int j = i - 1;
            while (j >= 0 && rel[j] > kr) {
                c[j + 1] = c[j];
                rel[j + 1] = rel[j];
                j--;
            }
            c[j + 1] = kc;
            rel[j + 1] = kr;
        }
        ring = new Rgb[n];
        ringHue = new float[n];
        for (int i = 0; i < n; i++) {
            ring[i] = c[i];
            ringHue[i] = rel[i];
        }
        ringCount = n;
    }

    private static float hueOf(Rgb p) {
        int max = Math.max(p.r, Math.max(p.g, p.b));
        int min = Math.min(p.r, Math.min(p.g, p.b));
        float d = max - min;
        if (d == 0f) {
            return 0f;
        }
        float h;
        if (max == p.r) {
            h = (p.g - p.b) / d;
            if (h < 0f) {
                h += 6f;
            }
        } else if (max == p.g) {
            h = 2f + (p.b - p.r) / d;
        } else {
            h = 4f + (p.r - p.g) / d;
        }
        return h * 60f;
    }

    /** Farbe an Position pos auf dem Ring (Index-Einheiten, Nachkommastelle =
     *  Mischung zum naechsten Eintrag; nach dem letzten Eintrag geht es
     *  geschlossen zurueck zum ersten). snap=true -> kein Mischen, nur Werksfarben. */
    private void ringAt(Rgb out, float pos, boolean snap) {
        int i0 = (int) pos;
        if (i0 >= ringCount) {
            i0 = ringCount - 1;
        }
        if (i0 < 0) {
            i0 = 0;
        }
        float f = pos - i0;
        if (f > 1f) {
            f = 1f;
        }
        if (f < 0f) {
            f = 0f;
        }
        Rgb a = ring[i0];
        Rgb b = ring[(i0 + 1) % ringCount];
        if (snap) {
            out.set(f < 0.5f ? a : b);
        } else {
            out.lerp(a, b, f);
        }
    }

    /** Geschwindigkeit -> Anteil 0..1 auf der Farbstrecke. Mit SMOOTH_COLORS
     *  stufenlos, sonst in SPEED_STEP_KMH-Stufen (harte Stufen). */
    private static float speedFraction(float kmh) {
        float k = kmh;
        if (!SMOOTH_COLORS) {
            k = Math.round(k / SPEED_STEP_KMH) * SPEED_STEP_KMH;
        }
        float u = k / RAINBOW_SPAN_KMH;
        if (u < 0f) {
            u = 0f;
        }
        if (u > 1f) {
            u = 1f;
        }
        return u;
    }

    /** Snappt idealHue (falls Werksliste vorhanden) auf die naechste Werksfarbe,
     *  erst DANACH die Helligkeit anwenden - haelt beides unabhaengig. */
    private static void setDisplay(Rgb out, Rgb idealHue, Rgb[] factoryPalette, float brightness) {
        if (factoryPalette != null && factoryPalette.length > 0) {
            out.set(Rgb.nearest(idealHue, factoryPalette));
        } else {
            out.set(idealHue);
        }
        out.scale(brightness);
    }

    public int periodMs() {
        return 150;
    }
}
