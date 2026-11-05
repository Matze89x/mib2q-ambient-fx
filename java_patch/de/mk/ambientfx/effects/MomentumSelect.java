/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * "Momentum Select" - eigenstaendige Variante von "Momentum" (siehe dort),
 * die zusaetzlich auf den Audi-Drive-Select-Modus reagiert (Comfort/Auto/
 * Dynamic/Eco/Individual, ueber AmbientFx.getInstance().getDriveMode() /
 * DriveModeData). BEWUSST eine komplette, unabhaengige Kopie: keinerlei
 * Import/Erweiterung/Referenz auf Momentum.java, damit beide Effekte sich
 * gegenseitig nicht beeinflussen - wird einer der beiden aus FxCatalog
 * entfernt, bleibt der andere unveraendert lauffaehig.
 *
 * Grundprinzip wie Momentum: Regenbogen-Durchlauf der Kontur ueber das
 * Tempo, Bremse ueberschreibt sofort mit vollem Rot, Werksfarben-Snapping
 * wegen des Retrofit-LED-Streifens (siehe Momentum.java-Kommentar) -
 * dieser Snap-Mechanismus bleibt in JEDER Iteration unangetastet ("das
 * System muss wie bei Momentum beibehalten werden", Nutzer-Vorgabe
 * 26.09.2026) - nur WELCHE Farbe/Helligkeit angepeilt wird, unterscheidet
 * sich je Profil.
 *
 * NEU gegenueber Momentum - Verhalten je Drive-Select-Profil:
 *   DYNAMIC            -> Helligkeit immer 100% (Tag UND Nacht, unabhaengig
 *                          vom Gaspedal), Farbwechsel/Helligkeit reagieren
 *                          viel schneller (kurze Glaettungs-Zeitkonstanten).
 *                          "ohne Ueberlauf": es wird nur ein einfacher
 *                          Tiefpass (Exponential-Glaettung) benutzt, kein
 *                          Feder-/PID-Modell - der kann grundsaetzlich nicht
 *                          ueberschwingen, auch nicht bei kurzer Zeitkonstante.
 *   AUTO, INDIVIDUAL    -> exakt wie Momentum (Tag=100% fest, Nacht=neue
 *                          5-100%-Kurve mit Saettigung bei 50% Gas seit
 *                          26.09.2026, siehe Momentum.java) - inkl. der
 *                          asymmetrischen Helligkeits-Attack/Release
 *                          (siehe unten). BEWUSST NICHT auf DYNAMIC oder
 *                          COMFORT/EFFICIENCY uebertragen - deren Tag- UND
 *                          Nachtverhalten ist jeweils eigens gestaltet
 *                          (Dynamic: immer 100%, Comfort/Eco: folgt immer
 *                          dem Gas) und war nicht Teil dieser Anfrage.
 *   COMFORT, EFFICIENCY -> Helligkeit folgt IMMER dem Gaspedal (3% bei 0%
 *                          Gas bis 100% bei Vollgas, unabhaengig Tag/Nacht),
 *                          alles deutlich langsamer geglaettet (ruhigeres,
 *                          "komfortableres" Verhalten statt Momentums
 *                          scharfer Tag/Nacht-Umschaltung).
 *   unbekannt/kein Signal -> faellt auf das Momentum-Standardverhalten
 *                          zurueck (wie AUTO/INDIVIDUAL) - "fail visible".
 *
 * Seit 26.09.2026: Helligkeit wird je Profil ASYMMETRISCH geglaettet -
 * schneller HELLER (TC_BRIGHT_*_UP), langsamer wieder DUNKLER
 * (TC_BRIGHT_*_DOWN), gleiche Idee/Quelle wie bei Momentum.java (siehe
 * dort). DYNAMIC bleibt bewusst SYMMETRISCH und schnell in beide
 * Richtungen - passt zum knackigen, sofortigen Charakter dieses Profils;
 * ein "Ausatmen" wuerde dort eher unentschlossen wirken.
 *
 * Seit 26.09.2026 (zweite Iteration) - EIGENE FARBWELT je Profil statt
 * immer des vollen Regenbogens (vorher unterschieden sich die Profile NUR
 * in Timing/Helligkeit, tagsueber sahen Dynamic und Auto/Individual dadurch
 * fast identisch aus):
 *   DYNAMIC             -> warme Welt, nur Rot->Orange->Gelb (HUE_START/
 *                           SPAN_DYNAMIC), UND zusaetzlich ein gasreaktiver
 *                           Farbzug: kraeftiges Gasgeben zieht die Farbe
 *                           zusaetzlich Richtung reinem Rot (DYNAMIC_
 *                           THROTTLE_PULL), unabhaengig vom aktuellen
 *                           Tempo-Farbton - fuehlt sich dadurch spuerbar
 *                           reaktiver/aggressiver an, nicht nur schneller
 *                           geglaettet.
 *   COMFORT, EFFICIENCY -> kuehle, ruhige Welt, nur Gruen->Blau (HUE_START/
 *                           SPAN_COMFORT_ECO) - nie Rot/Orange, passt zum
 *                           entspannten Charakter.
 *   AUTO, INDIVIDUAL    -> unveraendert voller Regenbogen (0..360 Grad) -
 *                           bleibt die neutrale Grundeinstellung.
 * Betrifft ausschliesslich den ANGEPEILTEN Farbton VOR dem Werksfarben-Snap
 * - der Snap selbst (setDisplay()) ist unveraendert.
 *
 * EXPERIMENT - mehr Zwischenfarben (auf Wunsch testweise aktiv):
 *   Momentum snappt seine ideale Regenbogenfarbe hart auf die EINE naechste
 *   Werksfarbe (Rgb.nearest) - noetig, weil der Retrofit-LED-Streifen bei
 *   frei gestreamten Zwischenwerten flackert. Hier wird stattdessen
 *   probeweise zwischen den ZWEI naechsten Werksfarben gewichtet gemischt
 *   (blendNearestTwo) - mehr wahrnehmbare Farbabstufungen, aber die
 *   Mischwerte sind KEINE reinen Werksfarben mehr und koennten denselben
 *   Flacker-Effekt zeigen wie freies Streaming. TRY_BLENDED_COLORS ist
 *   deshalb ein einzelner Schalter: bleibt es sauber, so lassen; flackert
 *   es, auf false zuruecksetzen -> dann exakt derselbe harte Snap wie bei
 *   Momentum, nur eben in dieser komplett separaten Datei.
 *
 * NEUE FARBTABELLE (03.10.2026) - gleiches Prinzip wie Momentum.java, siehe dort
 * fuer die volle Beschreibung: statt des harten Snaps auf EINE Werksfarbe
 * (bzw. der Mischung der zwei naechsten) laeuft die Kontur jetzt stufenlos
 * durch ALLE farbigen Werksfarben des Fahrzeugs (Ring ab Rot, nach Farbton
 * sortiert, Weiss ausgelassen), Tempo 0..130 km/h in 10-km/h-Schritten. Die
 * Farbwelten je Profil bleiben erhalten, nur dass sie jetzt aus echten
 * Werksfarben bestehen:
 *   AUTO, INDIVIDUAL, unbekannt -> der ganze Ring: 0 km/h Rot ... 130 km/h wieder Rot
 *   DYNAMIC             -> Ring-Abschnitt Rot..Gelb (HUE_START/SPAN_DYNAMIC) ueber
 *                           0..130 km/h, Gas zieht weiter Richtung Rot
 *   COMFORT, EFFICIENCY -> Ring-Abschnitt Gruen..Blau (HUE_START/SPAN_COMFORT_ECO)
 *                           ueber 0..130 km/h
 * Helligkeit/Glaettung je Profil sind unveraendert. TRY_BLENDED_COLORS gilt
 * nur noch fuer Bremse und den Fallback ohne Werksliste. SMOOTH_COLORS=false
 * schaltet auf echte Werksfarben in harten 10-km/h-Stufen um.
 *
 * Gangwahlhebel D -> S (seit 30.09.2026, aus dem Testeffekt "MomentumGDS
 * select" uebernommen) - identisches Prinzip wie in Momentum.java, siehe dort
 * fuer die volle Beschreibung. Kurz: beim WECHSEL auf S (steigende Flanke,
 * Gangwahlhebel S UND Profil NICHT Dynamic) spielt einmalig "wie Bremsen, nur
 * in Weiss": SOFORT Weiss bei 100% (Tag und Nacht), FLASH_HOLD_MS gehalten,
 * dann FLASH_FADE_MS weiches Ueberblenden zu dem, was Momentum select gerade
 * fuer das AKTIVE Profil berechnet (inkl. dessen Farbwelt/Helligkeitskurve),
 * danach 1:1 normales Momentum select. Zurueck auf D: sofort normal, kein
 * Uebergang. Dynamic bleibt aussen vor (Getriebe steht dort ohnehin dauernd
 * auf S). Bremse hat weiterhin Vorrang vor allem, auch vor der Sequenz.
 */
package de.mk.ambientfx.effects;

import de.mk.ambientfx.AmbientFx;
import de.mk.ambientfx.DriveModeData;
import de.mk.ambientfx.Effect;
import de.mk.ambientfx.Rgb;
import de.mk.ambientfx.VehicleData;

public class MomentumSelect implements Effect {

    /** Tempo, bei dem der Farbweg einmal durch ist - danach bleibt es auf der Endfarbe.
     *  Seit 03.10.2026 130 statt 100 km/h, in SPEED_STEP_KMH-Schritten (13 Schritte). */
    private static final float RAINBOW_SPAN_KMH = 130f;
    private static final float SPEED_STEP_KMH = 10f;

    /** true = stufenlos zwischen benachbarten Werksfarben ueberblenden; false = nur
     *  echte Werksfarben in harten SPEED_STEP_KMH-Stufen. Siehe Klassenkommentar. */
    private static final boolean SMOOTH_COLORS = true;

    /** Ab welcher Saettigung/Helligkeit ein Werkslisten-Eintrag als "farbig" zaehlt
     *  (Weiss-Eintraege fallen heraus). */
    private static final float RING_MIN_SAT = 0.35f;
    private static final float RING_MIN_VAL = 0.25f;

    /* Glaettungs-Zeitkonstanten (ms) je Drive-Select-Profil - siehe tick().
     * TC_BRIGHT_* ist seit 26.09.2026 je ein UP/DOWN-Paar statt eines
     * einzelnen Werts (siehe Klassenkommentar) - DYNAMIC bewusst weiterhin
     * symmetrisch (UP == DOWN). */
    private static final float TC_HUE_DEFAULT = 600f;
    private static final float TC_BRIGHT_DEFAULT_UP = 120f;
    private static final float TC_BRIGHT_DEFAULT_DOWN = 350f;

    /* Nacht-Helligkeitskurve fuer AUTO/INDIVIDUAL - identisch zu Momentum.java,
     * siehe dort fuer die Begruendung. */
    private static final float NIGHT_ACCEL_SATURATE_PCT = 50f;
    private static final float NIGHT_BRIGHT_FLOOR = 0.05f;
    private static final float TC_HUE_DYNAMIC = 150f;
    private static final float TC_BRIGHT_DYNAMIC = 100f;
    private static final float TC_HUE_COMFORT_ECO = 1000f;
    private static final float TC_BRIGHT_COMFORT_ECO_UP = 250f;
    private static final float TC_BRIGHT_COMFORT_ECO_DOWN = 700f;

    /* Farbwelt je Profil (Grad) - hue = HUE_START + (Tempo-Anteil) * HUE_SPAN,
     * siehe Klassenkommentar "EIGENE FARBWELT je Profil". AUTO/INDIVIDUAL
     * nutzt weiterhin den vollen Kreis wie Momentum. */
    private static final float HUE_START_DEFAULT = 0f;
    private static final float HUE_SPAN_DEFAULT = 360f;
    private static final float HUE_START_DYNAMIC = 0f;      /* Rot */
    private static final float HUE_SPAN_DYNAMIC = 70f;      /* -> Gelb bei 100 km/h */
    private static final float HUE_START_COMFORT_ECO = 140f; /* Fruehlingsgruen */
    private static final float HUE_SPAN_COMFORT_ECO = 70f;   /* -> Blau bei 100 km/h */

    /** Bei DYNAMIC: Anteil, um den kraeftiges Gasgeben den Farbton zusaetzlich
     *  Richtung reinem Rot (0 Grad) zieht - 1.0 = bei Vollgas komplett Rot,
     *  unabhaengig vom Tempo-Farbton. Siehe Klassenkommentar. */
    private static final float DYNAMIC_THROTTLE_PULL = 0.8f;

    /** Experiment "mehr Zwischenfarben" - siehe Klassenkommentar. Bei Flackern
     *  auf false setzen (dann exakt harter Snap wie bei Momentum). */
    private static final boolean TRY_BLENDED_COLORS = true;

    /* D->S-Eintrittssequenz (siehe Klassenkommentar), Zeiten wie Momentum.java. */
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

        float tcHue = TC_HUE_DEFAULT;
        float tcBrightUp = TC_BRIGHT_DEFAULT_UP;
        float tcBrightDown = TC_BRIGHT_DEFAULT_DOWN;
        float hueStart = HUE_START_DEFAULT;
        float hueSpan = HUE_SPAN_DEFAULT;
        boolean dynamic = false;
        boolean coolWorld = false;
        float targetBrightness;

        if (profile == DriveModeData.PROFILE_DYNAMIC) {
            tcHue = TC_HUE_DYNAMIC;
            tcBrightUp = TC_BRIGHT_DYNAMIC;
            tcBrightDown = TC_BRIGHT_DYNAMIC;
            hueStart = HUE_START_DYNAMIC;
            hueSpan = HUE_SPAN_DYNAMIC;
            dynamic = true;
            targetBrightness = 1f;
        } else if (profile == DriveModeData.PROFILE_COMFORT || profile == DriveModeData.PROFILE_EFFICIENCY) {
            tcHue = TC_HUE_COMFORT_ECO;
            tcBrightUp = TC_BRIGHT_COMFORT_ECO_UP;
            tcBrightDown = TC_BRIGHT_COMFORT_ECO_DOWN;
            hueStart = HUE_START_COMFORT_ECO;
            hueSpan = HUE_SPAN_COMFORT_ECO;
            coolWorld = true;
            targetBrightness = 0.03f + 0.97f * (accelPct / 100f);
        } else {
            /* AUTO, INDIVIDUAL, unbekannt -> exakt wie Momentum */
            float accelT = accelPct / NIGHT_ACCEL_SATURATE_PCT;
            if (accelT > 1f) {
                accelT = 1f;
            }
            targetBrightness = daylight ? 1f : (NIGHT_BRIGHT_FLOOR + (1f - NIGHT_BRIGHT_FLOOR) * accelT);
        }

        float alphaV = dt / (tcHue + dt);
        smoothV += (kmh - smoothV) * alphaV;

        float tcBright = targetBrightness > smoothBrightness ? tcBrightUp : tcBrightDown;
        float alphaB = dt / (tcBright + dt);
        smoothBrightness += (targetBrightness - smoothBrightness) * alphaB;

        float clamped = smoothV;
        if (clamped < 0f) {
            clamped = 0f;
        }
        if (clamped > RAINBOW_SPAN_KMH) {
            clamped = RAINBOW_SPAN_KMH;
        }
        if (factoryPalette != ringSrc) {
            buildRing(factoryPalette);
        }
        float u = speedFraction(clamped);
        if (ring != null) {
            if (dynamic) {
                /* Gasreaktiver Zug Richtung Rot (Anfang des Abschnitts), siehe Klassenkommentar. */
                u = u * (1f - DYNAMIC_THROTTLE_PULL * (accelPct / 100f));
                ringAt(normalOut, dynLo + u * (dynHi - dynLo), !SMOOTH_COLORS);
            } else if (coolWorld) {
                ringAt(normalOut, coolLo + u * (coolHi - coolLo), !SMOOTH_COLORS);
            } else {
                /* AUTO, INDIVIDUAL, unbekannt: der ganze Kreis, bei u=1 wieder Rot */
                ringAt(normalOut, u * ringCount, !SMOOTH_COLORS);
            }
            normalOut.scale(smoothBrightness);
        } else {
            /* keine Werksliste -> reiner Farbton wie frueher */
            float hue = hueStart + u * hueSpan;
            if (dynamic) {
                hue = hue * (1f - DYNAMIC_THROTTLE_PULL * (accelPct / 100f));
            }
            if (hue >= 360f) {
                hue -= 360f;
            }
            if (hue < 0f) {
                hue += 360f;
            }
            hueRef.setHsv(hue, 1f, 1f);
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
        resetWorlds();
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
        computeWorlds();
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

    /* Grenzen der Profil-Farbwelten als Ring-Index (siehe Klassenkommentar). */
    private float dynLo, dynHi, coolLo, coolHi;

    private void resetWorlds() {
        dynLo = 0f;
        dynHi = 0f;
        coolLo = 0f;
        coolHi = 0f;
    }

    private void computeWorlds() {
        dynLo = nearestRingIndex(HUE_START_DYNAMIC);
        dynHi = nearestRingIndex(HUE_START_DYNAMIC + HUE_SPAN_DYNAMIC);
        coolLo = nearestRingIndex(HUE_START_COMFORT_ECO);
        coolHi = nearestRingIndex(HUE_START_COMFORT_ECO + HUE_SPAN_COMFORT_ECO);
    }

    /** Index des Ring-Eintrags, dessen Farbton (relativ zum Start-Rot) am naechsten an hue liegt. */
    private int nearestRingIndex(float hue) {
        int best = 0;
        float bestD = 1000f;
        for (int i = 0; i < ringCount; i++) {
            float d = Math.abs(ringHue[i] - hue);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Snappt/mischt idealHue (falls Werksliste vorhanden) auf Werksfarbe(n),
     *  erst DANACH die Helligkeit anwenden - haelt beides unabhaengig. */
    private static void setDisplay(Rgb out, Rgb idealHue, Rgb[] factoryPalette, float brightness) {
        if (factoryPalette != null && factoryPalette.length > 0) {
            if (TRY_BLENDED_COLORS && factoryPalette.length > 1) {
                blendNearestTwo(out, idealHue, factoryPalette);
            } else {
                out.set(nearest(idealHue, factoryPalette));
            }
        } else {
            out.set(idealHue);
        }
        out.scale(brightness);
    }

    /** Eigene Kopie von Rgb.nearest (bewusst dupliziert statt aufgerufen -
     *  komplette Unabhaengigkeit von jeder Momentum-spezifischen Logik). */
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

    /** Gewichtete Mischung der zwei naechsten Werksfarben (Experiment, siehe
     *  TRY_BLENDED_COLORS oben). Gewicht nach Distanzverhaeltnis: je naeher
     *  die beste Farbe, desto weniger wird zur zweitbesten gemischt. */
    private static void blendNearestTwo(Rgb out, Rgb target, Rgb[] palette) {
        Rgb best = palette[0];
        Rgb second = palette[0];
        int bestDist = Integer.MAX_VALUE;
        int secondDist = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int d = target.distance(palette[i]);
            if (d < bestDist) {
                secondDist = bestDist;
                second = best;
                bestDist = d;
                best = palette[i];
            } else if (d < secondDist) {
                secondDist = d;
                second = palette[i];
            }
        }
        int sum = bestDist + secondDist;
        float t = sum > 0 ? ((float) bestDist / (float) sum) : 0f;
        out.lerp(best, second, t);
    }

    public int periodMs() {
        return 150;
    }
}
