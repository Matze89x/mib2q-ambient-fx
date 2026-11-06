/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * "Scroll" - vierter Effekt, reines Regenbogen-Showelement. Ursprung:
 * luka-collection/AmbientSpeedComponent.java, Modus MODE_SPEED ("Scroll") -
 * dort dreht sich der Farbton mit einer zum Tempo proportionalen
 * Geschwindigkeit ueber das komplette Farbrad (0.36 Grad pro (km/h * s),
 * HUE_SPEED_FACTOR unten exakt von Luka uebernommen - siehe Klassenkommentar
 * dort fuer die Herleitung). Anders als Momentum (Tempo -> feste Hue-
 * POSITION, bleibt ab 100 km/h bei Rot stehen) ist das hier eine
 * fortlaufende Hue-ROTATION: je schneller gefahren wird, desto schneller
 * dreht sie sich weiter - laeuft unbegrenzt weiter im Kreis, kein Anschlag.
 *
 * Seit 26.09.2026 (zweite Iteration, nach Feedback aus dem ersten
 * Stand-Test): dreht sich IMMER weiter, auch im Stand (IDLE_ROTATION_DEG_
 * PER_SEC als Grundgeschwindigkeit) - vorher fror die Farbe bei Tempo 0
 * komplett ein (1:1 Lukas Original-Verhalten "stopped means frozen
 * colour"), was beim Testen im Stand faelschlich wie "haengt bei Rot fest"
 * aussah, statt wie ein Showelement zu wirken. Fahren beschleunigt die
 * Drehung zusaetzlich obendrauf - der Tempo-Bezug bleibt also erhalten,
 * nur eben nicht mehr bei Null anfangend.
 *
 * WICHTIG - bewusst NICHT 1:1 von Luka uebernommen, sondern durch unsere
 * eigene Pipeline gebaut (siehe Chat-Diskussion 26.09.2026):
 *   1. Luka streamt die HSV-Werte roh/ungefiltert ueber das komplette
 *      Farbrad, ohne Snap auf eine Werksfarbe. Unser Retrofit-LED-Streifen
 *      vorne flackert nachweislich bei genau solchen freien Zwischenwerten
 *      (siehe Momentum.java) - deshalb wird hier, GENAU WIE bei Momentum,
 *      hart auf die naechste echte Audi-Werksfarbe gesnappt (Rgb.nearest),
 *      bevor die Helligkeit angewendet wird.
 *   2. Lukas Version steuert Flaeche UND Kontur gleichzeitig. Bei uns
 *      reagiert (durch die Effect-Schnittstelle/AmbientFx-Engine bauartbedingt
 *      fuer JEDEN Effekt) ausschliesslich die Kontur - die Flaeche bleibt
 *      wie ueberall in diesem Projekt dem manuellen Farbregler ueberlassen.
 * Die Rotationsgeschwindigkeits-Formel selbst (Grad pro Zeit, proportional
 * zum geglaetteten Tempo) ist dagegen exakt Lukas Wert, wie gewuenscht.
 *
 * Die fortlaufende Hue-Rotation ist trotzdem "legal" im Sinne des
 * DSI-Sendediscipline-Regelwerks aus luka-collection/docs/
 * ambient_modes_design.md ("EMA legal, Random-Walk illegal"): der Hue-Wert
 * ist eine DETERMINISTISCHE Funktion aus vergangener Zeit und geglaettetem
 * Tempo, kein Zufall - ein verworfener Send verliert nichts Unvorhersehbares,
 * der naechste erfolgreiche Send zeigt einfach den inzwischen korrekt
 * weitergelaufenen Wert. Der 1Hz-Keepalive der Engine (siehe AmbientFx.java)
 * deckt das zusaetzlich ab.
 *
 * Bremse: wie bei Momentum/Momentum select sofort volles (Werks-)Rot bei
 * voller Helligkeit - eigene Ergaenzung (bei Luka nicht vorhanden), aber
 * konsistent mit der Bremse-Konvention der anderen beiden Effekte in
 * diesem Projekt.
 *
 * Helligkeit: bewusst simpel und OHNE Gaspedal-Bezug (anders als Momentum/
 * Momentum select) - Scroll ist ein reines Showelement, kein Fahr-Feedback.
 * Tag = 100%, Nacht = fester gedaempfter Wert (NIGHT_BRIGHTNESS), kein
 * Breathing, keine Gas-Reaktion - eigene, einfachste Variante.
 *
 * EXPERIMENT - freies Farbstreaming (TESTWEISE aktiv, 26.09.2026):
 *   TRY_FREE_COLOR = true schaltet den Werksfarben-Snap fuer DIESEN Effekt
 *   komplett ab und sendet den vollen, stufenlosen HSV-Farbverlauf roh -
 *   exakt wie Lukas Original, bewusst zum Testen, ob der Flacker-Effekt auf
 *   dem Retrofit-Streifen wirklich an den Farbwerten selbst lag oder eher
 *   an der Sendediscipline (die inzwischen durch den 1Hz-Keepalive und die
 *   Kontur-only-Beschraenkung deutlich robuster ist als beim urspruenglichen
 *   Fund). BEWUSST NUR HIER, NICHT in Momentum/Momentum select - deren
 *   bewaehrtes Snap-System (siehe dort) bleibt unangetastet. Flackert es:
 *   TRY_FREE_COLOR auf false zuruecksetzen -> exakt der harte Snap wie
 *   vorher, kein Neubau von Momentum/Momentum select noetig.
 *
 *   ERGEBNIS erster Autobahntest (26.09.2026, 80-130 km/h, laengere
 *   Strecke): KEIN einziger Ausfall/Flackern der Nachruest-LED. Deutet
 *   stark darauf hin, dass der urspruenglich vermutete Grund fuer das
 *   Flackern (freie Zwischenfarbwerte an sich) nicht die eigentliche
 *   Ursache war - plausibler ist die frueher fehlende Sendediscipline
 *   (kein Keepalive, moeglicherweise zu haeufige/zu kleine Farbspruenge).
 *   Trotzdem bewusst weiterhin NUR hier aktiv, nicht in Momentum/Momentum
 *   select - fuer eine Ausweitung auf die anderen (fahrverhaltensrelevanten)
 *   Effekte braucht es erst ein bewusstes Ok, kein automatisches Ausrollen.
 *
 * Seit 26.09.2026 (dritte Iteration, nach dem Autobahntest) - zwei
 * Verfeinerungen basierend auf Live-Feedback:
 *   1. Helligkeitsausgleich UEBERS GANZE FARBRAD (nicht mehr nur punktuell
 *      um Lila - erste Fassung war zu eng, Nutzer-Feedback: "alle Farben
 *      sollen ihr Spektrum nutzen koennen"). Physikalische Grundlage: bei
 *      HSV s=1/v=1 ist die Farbe zwar "maximal hell" im HSV-Sinne, die
 *      WAHRGENOMMENE Helligkeit (Rec.709: Y = 0.2126R + 0.7152G + 0.0722B)
 *      schwankt trotzdem um Faktor ~13 uebers Farbrad (Gelb ~0.93, reines
 *      Blau nur ~0.07) - der Gruen-Kanal traegt mit Abstand am meisten bei.
 *      Ein reiner "Value"-Boost hilft dabei NICHT bei den sechs reinen
 *      Grundfarbtoenen (Rot/Gelb/Gruen/Cyan/Blau/Magenta genau auf 0/60/
 *      120/180/240/300 Grad) - dort ist mindestens ein Kanal exakt 0, und
 *      0 mal irgendwas bleibt 0. Der einzige Weg, einen dunklen reinen
 *      Farbton wirklich heller zu bekommen, ist etwas Weiss beizumischen
 *      (Saettigung senken) - saturationFor() berechnet dazu je Farbton die
 *      rechnerische Ziel-Saettigung, damit alle Farbtoene eine
 *      vergleichbare Mindest-Helligkeit erreichen, ohne unter
 *      MIN_SATURATION_FLOOR komplett zu verwaschen.
 *   2. SHIMMER: bei laengerer konstanter Autobahnfahrt (100-130 km/h, ~1h)
 *      war keine spuerbare Aenderung der Drehgeschwindigkeit wahrnehmbar -
 *      mathematisch dreht sie sich zwar tatsaechlich schneller (ca. 44 vs.
 *      55 Grad/s), aber bei einer ohnehin schon zuegig laufenden Drehung
 *      ist dieser relative Unterschied fuers Auge kaum verfolgbar. Statt
 *      die Tempo-Kurve weiter zu verschaerfen (steigende Rueckgaben ab
 *      einem gewissen Punkt), gibt es zusaetzlich eine langsame, vom Tempo
 *      UNABHAENGIGE Saettigungs-Schwingung (SHIMMER_PERIOD_MS) - ein
 *      sanftes "Schweben", das auch bei stundenlanger konstanter Fahrt
 *      sichtbar bleibt.
 */
package de.mk.ambientfx.effects;

import de.mk.ambientfx.Effect;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

public class Scroll implements Effect {

    /** Grad Farbraddrehung pro (km/h * Sekunde) - exakt Lukas Wert
     *  (AmbientSpeedComponent.HUE_SPEED_FACTOR): 360 Grad / 10s bei 100 km/h. */
    private static final float HUE_SPEED_FACTOR = 0.36f;

    /** Tempo-Glaettung (ms) - nah an Lukas TAU_SPEED (450ms), etwas
     *  knackiger als Momentums 600ms, da Scroll ein reines Tempo-Showelement
     *  ohne weitere Reaktivitaet ist. */
    private static final float TC_SPEED_MS = 450f;

    /** Feste Nacht-Helligkeit (kein Breathing, kein Gas-Bezug - siehe Klassenkommentar). */
    private static final float NIGHT_BRIGHTNESS = 0.40f;

    /** Grundrotation im Stand (Grad/Sekunde) - Fahren beschleunigt zusaetzlich
     *  obendrauf (siehe tick()). Bewusst gemaechlich - ein reines "Show
     *  laeuft", kein hektisches Drehen im Stand. */
    private static final float IDLE_ROTATION_DEG_PER_SEC = 8f;

    /** TESTWEISE aktiv (26.09.2026) - siehe Klassenkommentar "EXPERIMENT
     *  freies Farbstreaming". Bei Flackern auf false setzen. */
    private static final boolean TRY_FREE_COLOR = true;

    /* ---- Wahrgenommene-Helligkeit-Ausgleich uebers ganze Farbrad, siehe
     * Klassenkommentar Punkt 1. Ziel-Mindesthelligkeit (Rec.709 Y) und wie
     * weit dafuer maximal entsaettigt werden darf. ---- */
    private static final float TARGET_LUMINANCE = 0.30f;
    private static final float MIN_SATURATION_FLOOR = 0.65f;

    /* ---- Langsames, tempounabhaengiges "Schweben", siehe
     * Klassenkommentar Punkt 2 - moduliert die HELLIGKEIT (nicht die
     * Saettigung), damit es sich nicht mit dem Saettigungs-Ausgleich aus
     * Punkt 1 in die Quere kommt bzw. dessen Mindest-Saettigung unterlaeuft. ---- */
    private static final float SHIMMER_PERIOD_MS = 15000f;
    private static final float SHIMMER_BRIGHTNESS_MIN = 0.85f;

    private float smoothV;
    private float currentHue;
    private long lastMs;
    private final Rgb hueRef = new Rgb();

    public void start(long nowMs) {
        smoothV = 0f;
        currentHue = 0f;
        lastMs = nowMs;
    }

    public boolean tick(long nowMs, VehicleData v, Rgb[] factoryPalette, boolean daylight, Rgb contourOut) {
        float dt = Math.max(1, (int) (nowMs - lastMs));
        lastMs = nowMs;

        float kmh = 0f;
        boolean braking = false;
        if (v != null && v.isFresh(nowMs)) {
            kmh = v.speedKmh;
            if (v.speedUnit == 1) {
                kmh = kmh * 1.609f;
            }
            braking = v.driverBraking;
        }

        if (braking) {
            hueRef.setHsv(0f, 1f, 1f);
            setDisplay(contourOut, hueRef, factoryPalette, 1f);
            return true;
        }

        if (kmh < 0f) {
            kmh = 0f;
        }
        float alphaV = dt / (TC_SPEED_MS + dt);
        smoothV += (kmh - smoothV) * alphaV;

        float dtSec = dt / 1000f;
        currentHue += (IDLE_ROTATION_DEG_PER_SEC + smoothV * HUE_SPEED_FACTOR) * dtSec;
        while (currentHue >= 360f) {
            currentHue -= 360f;
        }

        hueRef.setHsv(currentHue, saturationFor(currentHue), 1f);

        float shimmerPhase = (nowMs % (long) SHIMMER_PERIOD_MS) / SHIMMER_PERIOD_MS;
        float shimmer = 0.5f + 0.5f * (float) Math.sin(shimmerPhase * 2.0 * Math.PI);
        float shimmerFactor = SHIMMER_BRIGHTNESS_MIN + (1f - SHIMMER_BRIGHTNESS_MIN) * shimmer;

        float brightness = (daylight ? 1f : NIGHT_BRIGHTNESS) * shimmerFactor;
        setDisplay(contourOut, hueRef, factoryPalette, brightness);
        return true;
    }

    /** Rechnerische Ziel-Saettigung fuer hue, damit seine wahrgenommene
     *  Helligkeit (Rec.709) mindestens TARGET_LUMINANCE erreicht - siehe
     *  Klassenkommentar Punkt 1. Entsaettigt (mischt Weiss bei) nur so weit
     *  wie noetig, nie unter MIN_SATURATION_FLOOR. Farbtoene, die bei s=1
     *  schon hell genug sind (z.B. Gelb/Gruen), bleiben bei 1.0. */
    private static float saturationFor(float hue) {
        Rgb probe = new Rgb();
        probe.setHsv(hue, 1f, 1f);
        float y1 = relLuminance(probe);
        if (y1 >= TARGET_LUMINANCE) {
            return 1f;
        }
        float s = (1f - TARGET_LUMINANCE) / (1f - y1);
        if (s < MIN_SATURATION_FLOOR) {
            s = MIN_SATURATION_FLOOR;
        }
        return s;
    }

    /** Rec.709-gewichtete relative Helligkeit (0..1) - Gruen traegt am
     *  meisten bei, Blau am wenigsten (siehe Klassenkommentar Punkt 1). */
    private static float relLuminance(Rgb c) {
        return (0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b) / 255f;
    }

    /** Snappt idealHue (falls Werksliste vorhanden UND TRY_FREE_COLOR aus)
     *  hart auf die naechste Werksfarbe - identisches Prinzip wie
     *  Momentum.java (siehe dort fuer die Begruendung ueber den Retrofit-
     *  LED-Streifen). Bei TRY_FREE_COLOR (siehe Klassenkommentar) wird
     *  stattdessen der rohe idealHue-Wert direkt benutzt. Helligkeit wird
     *  in jedem Fall erst DANACH angewendet. Eigene Kopie statt Aufruf -
     *  komplette Unabhaengigkeit von Momentum/Momentum select/Atmosphere. */
    private static void setDisplay(Rgb out, Rgb idealHue, Rgb[] factoryPalette, float brightness) {
        if (!TRY_FREE_COLOR && factoryPalette != null && factoryPalette.length > 0) {
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
