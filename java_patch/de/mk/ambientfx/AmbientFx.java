/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Engine (v3: nur noch Kontur/Linie, Flaeche bleibt manuell).
 *
 * Singleton, das
 *   - vom gepatchten IntLightComponentEvo gebunden wird (DSI + Modelle),
 *   - vom gepatchten IntLightRange2DBusiness bei Auswahl eines Effekt-
 *     Eintrags aktiviert/deaktiviert wird,
 *   - in einem eigenen Thread den Effekt "tickt" und NUR die Kontur/Linie
 *     per DSI an das Fahrzeug schickt (Profil "individuell" = 1 wird dazu
 *     aktiviert). Die Flaeche wird von AmbientFx nie angefasst - sie bleibt
 *     komplett dem manuellen Farb-/Helligkeitsregler im MIB ueberlassen und
 *     behaelt dadurch automatisch ihren eigenen, vom Fahrzeug selbst
 *     gemerkten Wert.
 *
 * Tag/Nacht: gibt es hier KEINE generische Regel mehr auf Engine-Ebene (die
 * fruehere pauschale Kontur-Deckelung auf max. 45% nachts ist raus). Grund:
 * Momentum braucht nachts eine eigene, asymmetrische Helligkeitskurve
 * (25%-100% je nach Gaspedal statt 0%-100%) statt einer einfachen
 * Abdunklung, und eine pauschale Engine-Deckelung wuerde dieser eigenen
 * Kurve wieder die Spitze kappen. Tag/Nacht wird deshalb als daylight-
 * Parameter an effect.tick() durchgereicht, Quelle ist DaylightData (siehe
 * dort) - live im Fahrzeug bestaetigt (Lichtschalter-Test toggelt
 * daylight zuverlaessig). Falls ein kuenftiger, einfacherer Effekt doch
 * nur eine pauschale Deckelung braucht, kann das wieder auf Engine-Ebene
 * ergaenzt werden.
 *
 * Ablauf bei Auswahl (z.B. "Momentum", widgetID 9):
 *   1. Range2D-Modell X := 9  -> Radio-Button im Menue springt auf "Momentum"
 *   2. dsi.setIntLightActiveProfile(1) -> Fahrzeug schaltet auf "individuell"
 *   3. Worker-Thread: alle periodMs -> effect.tick() liefert die fertige
 *      Kontur-Farbe (Tag/Nacht ist darin bereits eingerechnet) ->
 *      setIntLightContourLightColor (nur bei Aenderung)
 *   Das Fahrzeug meldet updateIntLightActiveProfile(1) zurueck; der Stock-Code
 *   wuerde X wieder auf 1 setzen -> IntLightComponentEvo ruft restoreSelection()
 *   auf, damit "Momentum" markiert bleibt.
 *
 * Werksfarbliste: das Fahrzeug sendet die Liste der frei waehlbaren
 * "individuell"-Farben, die man normalerweise per Drehregler durchklickt.
 * Wir sammeln sie passiv mit (IntLightComponentEvo leitet die DSI-Antworten
 * durch, siehe onColorListCount/onColorListChunk) und reichen sie an
 * effect.tick() durch (factoryPalette-Parameter). Hintergrund: ein
 * Retrofit-LED-Streifen im Fahrzeug stellt nur genau diese Werksfarben
 * zuverlaessig dar, frei gestreamte RGB-Werte dazwischen flackern - deshalb
 * snappt Momentum seine ideale Farbe selbst auf die naechste Werksfarbe
 * (siehe Effect.java und Momentum.tick()), bevor es die eigene Helligkeit
 * aufmultipliziert.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import de.audi.app.car.common.app.ICarApplication;
import de.audi.app.car.common.handler.Range2DModelHandler;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import org.dsi.ifc.carlight.DSICarLight;
import org.dsi.ifc.carlight.IntLightBrightness;
import org.dsi.ifc.carlight.IntLightRGBColorListRA0;
import org.dsi.ifc.carlight.IntLightRGBValues;
import java.io.File;
import java.util.Vector;

public final class AmbientFx {

    private static final String TAG = "AmbientFx";

    /** Stand dieses Builds - erscheint in der Debug-Ausgabe (Signal fx.build + erste Zeile),
     *  damit sofort sichtbar ist, WELCHE Jar im Auto laeuft. */
    static final String BUILD_ID = "V1.0 RAM-Debug";

    /** Profil, auf dem die Effekte laufen ("individuell"). */
    public static final int PROFILE_INDIVIDUAL = 1;

    /** Stock-Fallback-Profil "Kontur-Weiss" (siehe README, Profilnummern
     *  1..8) - wird aktiviert, wenn ein per Neustart wiederherzustellender
     *  Effekt nicht mehr gefunden wird (Plugin-Datei geloescht), siehe
     *  onLightReady(). Damit steht das Fahrzeug nie mit einer nicht mehr
     *  erklaerten, eingefrorenen "individuell"-Farbe da, sondern zeigt ein
     *  sauberes, definiertes Stock-Profil, bis manuell neu ausgewaehlt wird. */
    private static final int PROFILE_FALLBACK_WHITE = 3;

    /** Kleinstes Sende-Intervall in ms (Schutz fuer DSI/CAN). */
    private static final int MIN_PERIOD_MS = 50;

    /** Unterhalb dieser RGB-Aenderung (Summe der Kanaldifferenzen) nicht senden.
     *  Bewusst hoch: ein alter Retrofit-LED-Streifen im Fahrzeug reagiert auf
     *  sehr haeufige minimale Zwischenschritte (Streaming) mit Aussetzern,
     *  obwohl er jede einzelne, ruhig gehaltene Farbe klaglos anzeigt.
     *  Groesserer Schwellwert = seltener senden, groessere Farbspruenge. */
    private static final int MIN_DELTA = 14;

    /** Erzwungenes Resend des AKTUELLEN (nicht des zuletzt gesendeten) Kontur-
     *  Werts, spaetestens alle KEEPALIVE_MS - unabhaengig von MIN_DELTA.
     *  Grund (siehe luka-collection/docs/ambient_modes_design.md): der
     *  Farbtransport DSI -> MIBRoot -> Licht-ECU laesst pro Zeit nur eine
     *  Anfrage "in flight" zu, verworfene Sends werden nie automatisch
     *  nachgeholt. Ohne Keepalive koennte ein einzelner verworfener Send bis
     *  zur naechsten ECHTEN Farbaenderung (>= MIN_DELTA) sichtbar haengen
     *  bleiben. 1000ms wie bei Luka - kurz genug, dass ein Aussetzer sich
     *  binnen einer Sekunde von selbst heilt, lang genug, um das DSI/CAN
     *  nicht zusaetzlich zu belasten. */
    private static final long KEEPALIVE_MS = 1000L;

    /**
     * Letzten Effekt merken und nach Neustart automatisch wieder starten.
     * Ablageort: /mnt/persist auf der MMX - dieselbe Partition, auf der das
     * Fahrzeug selbst seine Einstellungen (Sitzmemory, Sendespeicher etc.)
     * ablegt, und die deshalb im Betrieb vermutlich schreibbar ist, OHNE
     * dass man wie bei /mnt/app erst "mount -uw" braucht. Bewusst NICHT die
     * SD-Karte: die koennte fehlen/gezogen werden, das Ziel ist "im MMI
     * selbst", unabhaengig von externen Datentraegern.
     *
     * LIVE BESTAETIGT (26.09.2026): /mnt/persist ist zur Laufzeit ohne
     * vorheriges "mount -uw" beschreibbar - Log zeigte u.a. "state saved:
     * label=\"Atmosphere\" key=2 -> /mnt/persist/ambientfx/ambientfx_key2.state".
     * Damit erledigt sich die urspruenglich erwogene Umstellung auf die
     * offizielle IStorageAccess-Speicher-API (siehe luka-collection/
     * IntLightComponentEvo.java) - kein Grund mehr, das anzugehen. Noch
     * NICHT separat verifiziert: der READ-Pfad (Restore nach echtem
     * Neustart) - siehe onLightReady()/loadState(), sollte aber symmetrisch
     * funktionieren, da derselbe Dateizugriff.
     *
     * Seit 25.09.2026: gespeichert wird das LABEL des Effekts (z.B.
     * "Momentum"), NICHT mehr die widgetId. Grund: seit dem Plugin-System
     * ist die widgetId nur noch eine Position (FIRST_WIDGET_ID + Index in
     * der Scan-Reihenfolge) - wird ein Plugin geloescht/hinzugefuegt,
     * koennen sich alle nachfolgenden Positionen verschieben. Eine alte,
     * rein positionsbasierte gespeicherte widgetId koennte nach einer
     * Plugin-Aenderung den FALSCHEN (aber zufaellig noch gueltigen) Effekt
     * treffen, statt einfach nichts zu restoren. Das Label ist stabil,
     * unabhaengig von der Reihenfolge - wird es nicht mehr gefunden
     * (Plugin geloescht), wird sauber nichts restored (siehe onLightReady).
     *
     * Zusaetzlich seit 25.09.2026: je Fahrzeugschluessel ein eigener Stand
     * (siehe VehicleData.activeKey, KeyData.activeKey - seit 26.09.2026
     * LIVE bestaetigt, key=2 wurde korrekt gelesen und fuer den Dateinamen
     * benutzt) - Datei "ambientfx_key<N>.state" statt der einen globalen
     * "ambientfx.state".
     * Ist der Schluessel (noch) nicht bekannt, oder gibt es fuer diesen
     * Schluessel noch keinen eigenen Stand, faellt es automatisch auf die
     * globale Datei zurueck - kann also nie schlechter sein als vorher,
     * nur eventuell besser (siehe stateFileFor()/loadState()).
     */
    private static final boolean RESTORE_ON_BOOT = true;
    private static final String STATE_DIR = "/mnt/persist/ambientfx";
    private static final String STATE_FILE_GLOBAL = STATE_DIR + "/ambientfx.state";
    private static final long RESTORE_DELAY_MS = 2500L;

    private static AmbientFx instance;

    /* ---- Bindung an die Stock-Komponente ---- */
    private volatile DSICarLight dsi;
    private volatile Range2DModelHandler range2d;
    private volatile ICarApplication app;

    /* ---- Fahrzeugdaten (lazy) ---- */
    private VehicleData vehicle;
    private DaylightData daylightData;
    /** Audi-Drive-Select-Modus - nur fuer Effekte, die ihn brauchen (z.B.
     *  MomentumSelect), siehe getDriveMode() unten. */
    private DriveModeData driveModeData;
    /** Aussentemperatur - nur fuer Effekte, die sie brauchen (z.B.
     *  Atmosphere), siehe getOutsideTemp() unten. */
    private OutsideTempData outsideTempData;
    /** Einparkhilfe-Distanzen - nur fuer Effekte, die sie brauchen, siehe
     *  getParkingDistance() unten. */
    private ParkingDistanceData parkingDistanceData;

    /* ---- Werksfarbliste (fuer optionales Snapping) ---- */
    private final Object paletteLock = new Object();
    private Vector paletteBuf;
    private int paletteExpectedCount = -1;
    private volatile Rgb[] factoryPalette;

    /** Letzter bekannter Zustand des Debug-Gates, fuer die Nachlieferung beim Einschalten (siehe debugEdge()). */
    private boolean debugWasOn = false;

    /* ---- Laufzeit ---- */
    private final Object lock = new Object();
    private volatile int activeWidgetId = 0;
    private volatile Effect effect;
    private volatile boolean running = false;
    private Thread worker;
    private boolean restoreScheduled = false;

    private AmbientFx() {}

    public static synchronized AmbientFx getInstance() {
        if (instance == null) {
            instance = new AmbientFx();
        }
        return instance;
    }

    /* =====================================================================
     * Bindung (aus IntLightComponentEvo)
     * ===================================================================== */

    /** Wird aufgerufen, sobald der DSICarLight-Proxy verfuegbar ist. */
    public void bind(DSICarLight dsi, Range2DModelHandler range2d, ICarApplication app) {
        this.dsi = dsi;
        this.range2d = range2d;
        this.app = app;
        Log.i(TAG, "bound (dsi=" + (dsi != null) + ", range2d=" + (range2d != null) + ")");
    }

    /** Erste ViewOptions vom Fahrzeug -> Lichtsystem ist bereit. */
    public void onLightReady() {
        if (!RESTORE_ON_BOOT) {
            return;
        }
        synchronized (lock) {
            if (restoreScheduled || activeWidgetId != 0) {
                return;
            }
            restoreScheduled = true;
        }
        /* Frueh starten (nicht erst beim ersten activate()), damit
         * vehicle.activeKey moeglichst schon bekannt ist, wenn nach
         * RESTORE_DELAY_MS unten der gespeicherte Stand geladen wird. */
        ensureVehicleData();
        Log.i(TAG, "restore scheduled in " + RESTORE_DELAY_MS + " ms");
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(RESTORE_DELAY_MS);
                } catch (InterruptedException ignored) {
                }
                String savedLabel = loadState();
                if (savedLabel == null || savedLabel.length() == 0) {
                    Log.i(TAG, "no saved effect to restore");
                    return;
                }
                int widgetId = resolveWidgetIdForLabel(savedLabel);
                if (widgetId == 0) {
                    Log.i(TAG, "saved effect \"" + savedLabel + "\" not found (Plugin entfernt?) - falle auf Stock-Profil zurueck");
                    saveState(null);
                    fallbackToStockWhite();
                    return;
                }
                Log.i(TAG, "restoring saved effect \"" + savedLabel + "\" (widgetId=" + widgetId + ")");
                activate(widgetId);
            }
        }, "AmbientFx-Restore");
        t.setDaemon(true);
        t.start();
    }

    /** Wird aufgerufen, wenn ein gespeicherter Effekt beim Neustart nicht
     *  mehr gefunden wird (Plugin-Datei geloescht, waehrend er aktiv war).
     *  Ohne diesen Schritt wuerde das Fahrzeug einfach auf seinem eigenen,
     *  zuletzt gemerkten DSI-Zustand bleiben - typischerweise Profil
     *  "individuell" mit der letzten, jetzt nicht mehr erklaerten
     *  Effekt-Farbe eingefroren. Stattdessen aktiv auf ein definiertes
     *  Stock-Profil ("Kontur-Weiss") wechseln, bis der Nutzer im Menue neu
     *  waehlt - kein Absturz-Risiko, nur ein normaler Profilwechsel wie
     *  jeder andere auch. */
    private void fallbackToStockWhite() {
        DSICarLight d = dsi;
        if (d == null) {
            return;
        }
        try {
            d.setIntLightActiveProfile(PROFILE_FALLBACK_WHITE);
            Log.i(TAG, "fallback: Stock-Profil " + PROFILE_FALLBACK_WHITE + " (Kontur-Weiss) aktiviert");
        } catch (Throwable t) {
            Log.e(TAG, "fallback setIntLightActiveProfile failed", t);
        }
    }

    /** Sucht das Label in FxCatalog und liefert die AKTUELLE widgetId dafuer
     *  (0 = nicht gefunden, z.B. weil das Plugin inzwischen entfernt wurde). */
    private static int resolveWidgetIdForLabel(String label) {
        int n = FxCatalog.count();
        for (int i = 0; i < n; i++) {
            if (label.equals(FxCatalog.label(i))) {
                return FxCatalog.FIRST_WIDGET_ID + i;
            }
        }
        return 0;
    }

    public void unbind() {
        deactivate(false);
        this.dsi = null;
        this.range2d = null;
        Log.i(TAG, "unbound");
    }

    /* =====================================================================
     * Steuerung (aus IntLightRange2DBusiness)
     * ===================================================================== */

    public boolean isActive() {
        return activeWidgetId != 0;
    }

    public int getActiveWidgetId() {
        return activeWidgetId;
    }

    /** Effekt fuer widgetId starten (ersetzt einen laufenden Effekt). */
    public void activate(int widgetId) {
        Effect e = FxCatalog.createForWidgetId(widgetId);
        if (e == null) {
            Log.w(TAG, "activate: unknown widgetId " + widgetId);
            return;
        }
        DSICarLight d = dsi;
        if (d == null) {
            Log.w(TAG, "activate: DSI not bound yet");
            return;
        }
        Log.i(TAG, "activate widgetId=" + widgetId + " (" + FxCatalog.label(widgetId - FxCatalog.FIRST_WIDGET_ID) + ")");
        Log.value("effect", FxCatalog.label(widgetId - FxCatalog.FIRST_WIDGET_ID));

        long now = System.currentTimeMillis();
        e.start(now);

        synchronized (lock) {
            ensureVehicleData();
            effect = e;
            activeWidgetId = widgetId;
            setSelectionX(widgetId);
            try {
                d.setIntLightActiveProfile(PROFILE_INDIVIDUAL);
            } catch (Throwable t) {
                Log.e(TAG, "setIntLightActiveProfile failed", t);
            }
            forceMaxBrightness(d);
            startWorker();
        }
        saveState(FxCatalog.label(widgetId - FxCatalog.FIRST_WIDGET_ID));
    }

    /** Erzwingt volle Helligkeit auf DSI-Ebene beim Aktivieren eines Effekts.
     *  Grund: Profil "individuell" (auf dem alle Effekte laufen) erbt sonst
     *  den physischen Helligkeitsregler-Stand vom zuletzt gewaehlten Profil -
     *  ein leise gedrehter Regler wuerde die gestreamte Kontur-Farbe
     *  unbemerkt abdunkeln, ohne dass der Effekt-Code selbst je davon
     *  erfaehrt. Per javap gegen unser eigenes vendor/lsd.jar (MU1329)
     *  bestaetigt: DSICarLight.setIntLightBrightness(IntLightBrightness)
     *  existiert identisch zu Lukas Fund auf MU1316 (siehe luka-collection/
     *  AmbientSpeedComponent.java, forceMaxBrightness()) - Konstruktor
     *  (short brightness, boolean state) uebernommen. Betrifft nur den
     *  DSI-Helligkeitswert fuer das aktive Profil, NICHT die manuell vom
     *  Nutzer eingestellte Farbe/Helligkeit der Flaeche. */
    private static void forceMaxBrightness(DSICarLight d) {
        try {
            d.setIntLightBrightness(new IntLightBrightness((short) 100, true));
        } catch (Throwable t) {
            Log.e(TAG, "forceMaxBrightness failed", t);
        }
    }

    /** Effekt beenden (z.B. weil ein Stock-Profil gewaehlt wurde). */
    public void deactivate() {
        deactivate(true);
    }

    private void deactivate(boolean persist) {
        boolean wasActive;
        synchronized (lock) {
            wasActive = activeWidgetId != 0;
            activeWidgetId = 0;
            effect = null;
            running = false;
            if (worker != null) {
                worker.interrupt();
                worker = null;
            }
        }
        if (wasActive) {
            Log.i(TAG, "deactivated");
            Log.value("effect", "aus");
        }
        if (persist) {
            saveState(null);
        }
    }

    /**
     * Wird vom gepatchten IntLightComponentEvo nach updateIntLightActiveProfile
     * aufgerufen: der Stock-Code hat X auf das Fahrzeug-Profil gesetzt, wir
     * setzen es auf den aktiven Effekt zurueck.
     */
    public void restoreSelection() {
        int id = activeWidgetId;
        if (id != 0) {
            setSelectionX(id);
        }
    }

    /** Tag/Nacht aus DaylightData (siehe dort) - "fail visible" (Tag), bis
     *  das erste echte Update kam. */
    private boolean resolveDaylight() {
        DaylightData d = daylightData;
        return d == null || !d.valid || !d.night;
    }

    /** Audi-Drive-Select-Modus (Comfort/Auto/Dynamic/Eco/Individual) fuer
     *  Effekte, die das brauchen (z.B. MomentumSelect) - bewusst NICHT Teil
     *  von Effect.tick(), damit Momentum unveraendert bleibt. LAZY: wird
     *  erst bei ERSTEM Aufruf angemeldet, nicht schon bei jeder Aktivierung
     *  irgendeines Effekts - spart DSI-Anmeldungen fuer Effekte, die das
     *  gar nicht nutzen (z.B. Momentum, Atmosphere). Kann weiterhin null
     *  sein (kein Fahrzeug gebunden, oder Anmeldung fehlgeschlagen). */
    public DriveModeData getDriveMode() {
        if (driveModeData == null) {
            synchronized (lock) {
                if (driveModeData == null) {
                    ICarApplication a = app;
                    if (a != null) {
                        try {
                            DriveModeData d = new DriveModeData(a);
                            d.init();
                            driveModeData = d;
                            Log.i(TAG, "DriveModeData component started (lazy)");
                        } catch (Throwable t) {
                            Log.e(TAG, "DriveModeData init failed", t);
                        }
                    }
                }
            }
        }
        return driveModeData;
    }

    /** Aussentemperatur (Grad Celsius) fuer Effekte, die das brauchen (z.B.
     *  Atmosphere) - siehe getDriveMode()-Kommentar oben, gleiches Prinzip
     *  (lazy, erst bei erstem Aufruf). */
    public OutsideTempData getOutsideTemp() {
        if (outsideTempData == null) {
            synchronized (lock) {
                if (outsideTempData == null) {
                    ICarApplication a = app;
                    if (a != null) {
                        try {
                            OutsideTempData d = new OutsideTempData(a);
                            d.init();
                            outsideTempData = d;
                            Log.i(TAG, "OutsideTempData component started (lazy)");
                        } catch (Throwable t) {
                            Log.e(TAG, "OutsideTempData init failed", t);
                        }
                    }
                }
            }
        }
        return outsideTempData;
    }

    /** Einparkhilfe-Distanzen (PDC) fuer Effekte, die das brauchen - siehe
     *  getDriveMode()-Kommentar oben, gleiches Prinzip (lazy). */
    public ParkingDistanceData getParkingDistance() {
        if (parkingDistanceData == null) {
            synchronized (lock) {
                if (parkingDistanceData == null) {
                    ICarApplication a = app;
                    if (a != null) {
                        try {
                            ParkingDistanceData d = new ParkingDistanceData(a);
                            d.init();
                            parkingDistanceData = d;
                            Log.i(TAG, "ParkingDistanceData component started (lazy)");
                        } catch (Throwable t) {
                            Log.e(TAG, "ParkingDistanceData init failed", t);
                        }
                    }
                }
            }
        }
        return parkingDistanceData;
    }

    /* =====================================================================
     * Werksfarbliste (aus IntLightComponentEvo durchgereicht, siehe dort)
     * ===================================================================== */

    public void onColorListCount(int count) {
        synchronized (paletteLock) {
            if (paletteExpectedCount != count) {
                paletteExpectedCount = count;
                paletteBuf = new Vector();
                factoryPalette = null;
            }
        }
        Log.i(TAG, "factory color list: expecting " + count + " entries");
    }

    public void onColorListChunk(IntLightRGBColorListRA0[] entries) {
        if (entries == null) {
            return;
        }
        synchronized (paletteLock) {
            if (paletteBuf == null) {
                paletteBuf = new Vector();
            }
            for (int i = 0; i < entries.length; i++) {
                if (entries[i] == null || entries[i].getValues() == null) {
                    continue;
                }
                IntLightRGBValues v = entries[i].getValues();
                paletteBuf.addElement(new Rgb(v.getBaseColorRed(), v.getBaseColorGreen(), v.getBaseColorBlue()));
            }
            if (paletteExpectedCount > 0 && paletteBuf.size() >= paletteExpectedCount && factoryPalette == null) {
                Rgb[] arr = new Rgb[paletteBuf.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = (Rgb) paletteBuf.elementAt(i);
                }
                factoryPalette = arr;
                Log.i(TAG, "factory color list complete: " + arr.length + " entries");
                /* Seit 26.09.2026: die tatsaechlichen RGB-Werte mitloggen (vorher nur
                 * die Anzahl) - fuer eine datenbasierte Analyse statt Raten. Seit
                 * 03.10.2026 als kompakte Hex-Zeilen (siehe dumpPalette()), weil eine
                 * einzige Zeile ein Zeilenlimit eines Debug-Protokolls sprengt. */
                dumpPalette(arr);
            }
        }
    }

    /* =====================================================================
     * intern
     * ===================================================================== */

    private void setSelectionX(int x) {
        Range2DModelHandler h = range2d;
        if (h == null) {
            return;
        }
        try {
            h.updateRangeModelValueX(x);
        } catch (Throwable t) {
            Log.e(TAG, "updateRangeModelValueX failed", t);
        }
    }

    /** Liefert Tempo/Gas/Bremse/Schluessel (VehicleData) und Tag/Nacht
     *  (DaylightData, siehe resolveDaylight()) - die beiden werden IMMER
     *  gebraucht (jeder Effekt bekommt sie direkt als tick()-Parameter,
     *  ausserdem braucht loadState() vehicle.activeKey), deshalb weiterhin
     *  eager. DriveModeData/OutsideTempData/ParkingDistanceData sind seit
     *  25.09.2026 LAZY (siehe getDriveMode()/getOutsideTemp()/
     *  getParkingDistance()) - nur Effekte, die sie per Getter tatsaechlich
     *  abfragen, loesen ueberhaupt eine DSI-Anmeldung aus. */
    private void ensureVehicleData() {
        ICarApplication a = app;
        if (a == null) {
            Log.w(TAG, "no application - vehicle data unavailable");
            return;
        }
        if (vehicle == null) {
            try {
                VehicleData v = new VehicleData(a);
                v.init();
                vehicle = v;
                Log.i(TAG, "VehicleData component started");
            } catch (Throwable t) {
                Log.e(TAG, "VehicleData init failed", t);
            }
        }
        if (daylightData == null) {
            try {
                DaylightData d = new DaylightData(a);
                d.init();
                daylightData = d;
                Log.i(TAG, "DaylightData component started");
            } catch (Throwable t) {
                Log.e(TAG, "DaylightData init failed", t);
            }
        }
    }

    private void startWorker() {
        if (running && worker != null && worker.isAlive()) {
            return;
        }
        running = true;
        worker = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "AmbientFx-Worker");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY + 1);
        worker.start();
    }

    /** Werksfarbliste als kompakte Hex-Zeilen (RRGGBB, 15 pro Zeile) ins Debug-
     *  Protokoll. Reihenfolge = Reihenfolge der Fahrzeugliste. Nur bei Debug AN. */
    private void dumpPalette(Rgb[] arr) {
        if (arr == null || !Log.enabled()) {
            return;
        }
        Log.value("palette_n", new Integer(arr.length));
        StringBuffer line = new StringBuffer(120);
        int first = 1;
        for (int i = 0; i < arr.length; i++) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(hex2(arr[i].r)).append(hex2(arr[i].g)).append(hex2(arr[i].b));
            if ((i + 1) % 15 == 0 || i == arr.length - 1) {
                Log.i(TAG, "factory color list values [" + first + "-" + (i + 1) + "] (RRGGBB): " + line);
                line.setLength(0);
                first = i + 2;
            }
        }
    }

    private static String hex2(int v) {
        String h = Integer.toHexString(v & 255);
        return h.length() < 2 ? "0" + h : h;
    }

    /** Erkennt das Einschalten des Debugs (steigende Flanke) und liefert dann
     *  einmalig den Grundzustand nach: die Werksfarbliste z.B. wird nur einmal
     *  beim Start gemeldet und waere sonst weg, wenn das Debug erst spaeter
     *  eingeschaltet wird. Wird beim Worker-Start und alle 10 s geprueft
     *  (laeuft also nur, solange ein Effekt aktiv ist). */
    private void debugEdge() {
        boolean on = Log.enabled();
        if (on && !debugWasOn) {
            debugWasOn = true;
            replayState();
        } else if (!on) {
            debugWasOn = false;
        }
    }

    private void replayState() {
        Log.value("build", BUILD_ID);
        Log.i(TAG, "BUILD " + BUILD_ID);
        int wid = activeWidgetId;
        String lbl = wid != 0 ? FxCatalog.label(wid - FxCatalog.FIRST_WIDGET_ID) : "(aus)";
        Log.value("effect", lbl);
        Log.i(TAG, "aktiver Effekt: " + lbl + " (widgetId=" + wid + ")");
        Rgb[] pal = factoryPalette;
        if (pal != null) {
            Log.i(TAG, "factory color list complete: " + pal.length + " entries (nachgeliefert)");
            dumpPalette(pal);
        } else {
            Log.i(TAG, "factory color list: noch nicht eingetroffen");
        }
        DriveModeData dm = driveModeData;
        if (dm != null && dm.valid) {
            Log.value("profile", new Integer(dm.profile));
            Log.i(TAG, "drive select profile=" + dm.profile);
        }
    }

    private void loop() {
        Log.i(TAG, "worker started");
        debugEdge();
        Rgb con = new Rgb();
        Rgb lastCon = new Rgb();
        lastCon.r = -1000;
        Effect lastEffect = null;
        int sentCon = 0;
        long lastSendMs = 0L;
        long lastLog = System.currentTimeMillis();

        while (running) {
            Effect e = effect;
            if (e == null) {
                break;
            }
            if (e != lastEffect) {
                lastEffect = e;
                lastCon.r = -1000;
                lastSendMs = 0L;
            }
            int period = Math.max(e.periodMs(), MIN_PERIOD_MS);
            try {
                long now = System.currentTimeMillis();
                boolean daylight = resolveDaylight();
                if (e.tick(now, vehicle, factoryPalette, daylight, con)) {
                    boolean changed = con.distance(lastCon) >= MIN_DELTA;
                    boolean keepaliveDue = (now - lastSendMs) >= KEEPALIVE_MS;
                    if (changed || keepaliveDue) {
                        sendContour(con);
                        lastCon.set(con);
                        lastSendMs = now;
                        sentCon++;
                    }
                }
                /* temp[] zeigt IMMER den letzten bekannten Zustand, auch wenn nie ein
                 * echtes DSI-Update ankam (valid=false) - sonst ist aus dem Log nicht
                 * unterscheidbar, ob der Sensor stumm ist oder ob wir nur vergessen
                 * haben hinzuschauen (26.09.2026, gleiche Lehre wie beim Blinker-Test:
                 * lieber sichtbar machen als stillschweigend annehmen). */
                if (now - lastLog > 10000L) {
                    lastLog = now;
                    debugEdge();
                    /* Bei Debug AUS wird hier gar kein Text zusammengebaut. */
                    if (Log.enabled()) {
                        OutsideTempData temp = outsideTempData;
                        Log.i(TAG, "alive: conSent=" + sentCon + " con=" + con + " daylight=" + daylight
                                + (vehicle != null ? " veh[" + vehicle + "]" : "")
                                + (temp != null ? " temp[valid=" + temp.valid + " c=" + temp.celsius + "]" : ""));
                        Log.value("daylight", daylight ? "tag" : "nacht");
                        DriveModeData dmv = driveModeData;
                        if (dmv != null && dmv.valid) {
                            Log.value("profile", new Integer(dmv.profile));
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "tick failed", t);
            }
            try {
                Thread.sleep(period);
            } catch (InterruptedException ie) {
                break;
            }
        }
        Log.i(TAG, "worker stopped");
    }

    private void sendContour(Rgb c) {
        DSICarLight d = dsi;
        if (d == null) {
            return;
        }
        try {
            d.setIntLightContourLightColor(new IntLightRGBValues(c.r, c.g, c.b));
        } catch (Throwable t) {
            Log.e(TAG, "setIntLightContourLightColor failed", t);
        }
    }

    /* ---- Persistenz: fest auf /mnt/persist (siehe Konstanten oben und
     *      Klassenkommentar). "Fest und sicher" heisst hier konkret:
     *        - jede Aktivierung/Deaktivierung schreibt SOFORT (kein Timer,
     *          kein Cache, kein Datenverlust bei ploetzlichem Abschalten
     *          zwischen zwei Schreibvorgaengen)
     *        - Verzeichnis wird bei Bedarf selbst angelegt (mkdirs())
     *        - jeder Erfolg UND jeder Fehlschlag landet im Log, damit man
     *          beim Testen schwarz auf weiss sieht, ob /mnt/persist wirklich
     *          beschreibbar ist, statt es nur am Verhalten zu raten
     *        - schlaegt das Schreiben/Lesen fehl (z.B. read-only), bleibt
     *          alles Uebrige unberuehrt - kein Absturz, nur kein Restore,
     *          genau wie vor diesem Feature ---- */

    /** Zustandsdatei fuer einen bestimmten Schluessel (oder die globale
     *  Datei, wenn kein Schluessel bekannt ist - key < 0). */
    private static String stateFileFor(int key) {
        return key >= 0 ? STATE_DIR + "/ambientfx_key" + key + ".state" : STATE_FILE_GLOBAL;
    }

    /** label == null/leer -> "kein Effekt aktiv" gespeichert (z.B. Stock-
     *  Profil gewaehlt). Schreibt in die Datei des aktuell bekannten
     *  Schluessels (VehicleData.activeKey) - noch unbekannt -> globale Datei. */
    private void saveState(String label) {
        if (!RESTORE_ON_BOOT) {
            return;
        }
        int key = (vehicle != null) ? vehicle.activeKey : -1;
        String file = stateFileFor(key);
        FileWriter fw = null;
        try {
            new File(STATE_DIR).mkdirs();
            fw = new FileWriter(file, false);
            fw.write(label == null ? "" : label);
            fw.write('\n');
            fw.close();
            fw = null;
            Log.i(TAG, "state saved: label=\"" + label + "\" key=" + key + " -> " + file);
        } catch (Throwable t) {
            Log.w(TAG, "saveState failed (" + file + "): " + t);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Liest den gespeicherten Effekt-Namen fuer den aktuell bekannten
     *  Schluessel - gibt es dafuer (noch) keinen eigenen Stand, oder ist
     *  der Schluessel (noch) unbekannt, wird auf die globale Datei
     *  zurueckgefallen (nie schlechter als vor der Je-Schluessel-Funktion,
     *  hoechstens praeziser). null = nichts gespeichert/lesbar. */
    private String loadState() {
        int key = (vehicle != null) ? vehicle.activeKey : -1;
        if (key >= 0) {
            String label = readStateFile(stateFileFor(key));
            if (label != null) {
                return label;
            }
            Log.i(TAG, "loadState: kein Stand fuer key=" + key + ", falle auf globalen Stand zurueck");
        }
        return readStateFile(STATE_FILE_GLOBAL);
    }

    private static String readStateFile(String file) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line = br.readLine();
            if (line == null || line.trim().length() == 0) {
                return null;
            }
            String label = line.trim();
            Log.i(TAG, "state loaded: label=\"" + label + "\" <- " + file);
            return label;
        } catch (java.io.FileNotFoundException notYet) {
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "loadState failed (" + file + "): " + t);
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
