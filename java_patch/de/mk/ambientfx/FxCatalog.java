/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Katalog der Zusatz-Eintraege im Menue "Ambientebeleuchtung".
 *
 * Jeder Eintrag bekommt eine widgetID ab FIRST_WIDGET_ID (die Stock-Profile
 * belegen 1..8).
 *
 * Seit 25.09.2026 (im Fahrzeug bestaetigt funktionierend): ALLE Effekte
 * kommen ausschliesslich aus PLUGINS (*.ambiente-Dateien in
 * /net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/, siehe
 * EffectPluginLoader) - auch Momentum, Momentum select und Atmosphere.
 * Kein einziger Effekt ist mehr fest in diesem Jar verdrahtet. Das heisst:
 * ein Effekt hinzufuegen/entfernen/austauschen braucht NIE mehr einen
 * Neubau/Redeploy von ambient_fx.jar - nur eine .ambiente-Datei auf dem
 * MIB hinzufuegen oder loeschen und neu starten (siehe EFFEKTE_HANDBUCH.md).
 *
 * Der static-Block ruft nur noch EffectPluginLoader.scanAndRegister() auf.
 * Ein defektes/fehlendes Plugin wird uebersprungen bzw. ergibt einfach
 * einen leeren Katalog (kein Absturz, siehe EffectPluginLoader) - dann
 * zeigt das Menue schlicht keine Zusatz-Eintraege.
 *
 * Der eingebaute Weg (addBuiltin(), siehe unten) existiert weiterhin als
 * Mechanismus, falls mal ein Effekt fest/garantiert verfuegbar sein soll,
 * unabhaengig vom Plugin-Ordner - wird aktuell aber von niemandem genutzt.
 *
 * Menue, Range2D-Limit und Auswahl ziehen in jedem Fall automatisch nach.
 */
package de.mk.ambientfx;

import java.util.Vector;

public final class FxCatalog {

    /** Erste widgetID fuer Effekte (Stock-Profile = 1..8). */
    public static final int FIRST_WIDGET_ID = 9;

    /** Interne Menue-IDs (nur fuer Diagnose/Fokus-Restore, muessen eindeutig sein). */
    public static final int FIRST_INTERNAL_ID = 690001;

    private static final Vector labels = new Vector();
    private static final Vector factories = new Vector();

    static {
        try {
            EffectPluginLoader.scanAndRegister();
        } catch (Throwable t) {
            Log.e("FxCatalog", "Plugin-Scan fehlgeschlagen - Menue zeigt evtl. keine Effekte", t);
        }
    }

    private static void addBuiltin(String label, EffectFactory factory) {
        labels.addElement(label);
        factories.addElement(factory);
    }

    private FxCatalog() {}

    public static int count() {
        return labels.size();
    }

    public static int lastWidgetId() {
        return FIRST_WIDGET_ID + count() - 1;
    }

    public static boolean isFxWidgetId(int widgetId) {
        return widgetId >= FIRST_WIDGET_ID && widgetId <= lastWidgetId();
    }

    public static String label(int index) {
        return (String) labels.elementAt(index);
    }

    /** Neue Effekt-Instanz fuer den Katalog-Index (0-basiert). */
    public static Effect create(int index) {
        if (index < 0 || index >= factories.size()) {
            return null;
        }
        try {
            return ((EffectFactory) factories.elementAt(index)).create();
        } catch (Throwable t) {
            Log.e("FxCatalog", "create(" + index + ") fehlgeschlagen", t);
            return null;
        }
    }

    public static Effect createForWidgetId(int widgetId) {
        if (!isFxWidgetId(widgetId)) {
            return null;
        }
        return create(widgetId - FIRST_WIDGET_ID);
    }

    /** Wird von EffectPluginLoader aufgerufen, um ein erfolgreich geladenes
     *  Plugin anzuhaengen - bewusst nur additiv, nie ein Ersetzen. */
    public static synchronized void registerPlugin(String label, EffectFactory factory) {
        labels.addElement(label);
        factories.addElement(factory);
    }
}
