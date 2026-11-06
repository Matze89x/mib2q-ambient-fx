/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - laedt zusaetzliche Effekte aus einzelnen ".ambiente"-Dateien,
 * OHNE dass sie beim Bauen des Haupt-Jars mit reinkompiliert werden muessen.
 *
 * Format: eine ".ambiente"-Datei ist technisch ein ganz normales Jar (mit
 * "jar cf" gebaut, siehe build_plugin.sh) mit genau zwei Dingen drin:
 *   - der kompilierten .class-Datei einer Effect-Implementierung
 *   - einer Text-Datei "plugin.properties" mit drei Zeilen:
 *       class=de.mk.ambientfx.effects.MeinEffekt
 *       label=Mein Effekt
 *       version=1
 * Warum kein rohes .java? Das MIB hat keinen Compiler an Bord (kein javac
 * auf der QNX-Box) - kompiliert wird immer am Mac (build_plugin.sh),
 * geladen wird nur fertiger Bytecode.
 *
 * "version" wird von build_plugin.sh automatisch aus Effect.CONTRACT_VERSION
 * gestempelt und muss exakt zur aktuell gueltigen Zahl passen - sonst wird
 * das Plugin abgelehnt (Versions-Mismatch), statt mit einem moeglicherweise
 * inkompatiblen tick()-Aufruf zur Laufzeit zu scheitern.
 *
 * Ablageort: /mnt/app/eso/hmi/lsd/ambientfx_effects/*.ambiente - bewusst
 * AUF DEM MIB SELBST, nicht auf der SD-Karte. Die SD-Karte dient nur als
 * TRANSPORTMITTEL beim Installieren (wie bei ambient_fx.jar selbst auch),
 * genau wie die SD-Karte kann sich mitten in der Session abmelden/neu
 * enumerieren (siehe docs/TECHNIK.md) - Effekte sollen aber dauerhaft
 * verfuegbar bleiben, egal ob/welche Karte gerade steckt. Deshalb NICHT
 * /net/mmx/fs/sda0/... (SD), sondern derselbe App-Partition-Pfad, auf dem
 * auch unser eigenes Jar liegt (bewaehrt beschreibbar via "mount -uw
 * /net/mmx/mnt/app" beim Installieren, danach normal read-only - Lesen
 * geht auch read-only, nur Schreiben braucht den mount -uw-Schritt).
 * NICHT /mnt/persist (dessen Beschreibbarkeit noch unbestaetigt ist).
 *
 * Fehlt der Ordner (frische Installation, noch keine Plugins draufkopiert)
 * oder ist ein einzelnes Plugin defekt: wird nur geloggt und uebersprungen,
 * nie ein Absturz. Die drei eingebauten Effekte (Momentum, Momentum
 * select, Atmosphere) haengen NICHT von diesem Ordner ab und bleiben so
 * auch ganz ohne Plugins voll funktionsfaehig - das Plugin-System ist eine
 * zusaetzliche, rein additive Ebene fuer neue/experimentelle Effekte.
 *
 * Technik: java.net.URLClassLoader (Standard seit Java 1.2) pro Datei -
 * die Effect/Rgb/VehicleData/...-Klassen selbst muessen NICHT in der
 * .ambiente-Datei stecken, die findet der Plugin-Classloader automatisch
 * beim Parent-Classloader (der unser eigenes ambient_fx.jar schon geladen
 * hat) - ganz normales Classloader-Delegationsprinzip.
 *
 * Wird von FxCatalog's statischem Initialisierer aufgerufen (siehe dort) -
 * dadurch ist garantiert, dass alle Plugins registriert sind, BEVOR
 * FxMenu.createMenuItems() zum ersten Mal FxCatalog.count() abfragt, ohne
 * dass irgendein anderer Code explizit "jetzt scannen" aufrufen muss.
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

public final class EffectPluginLoader {

    private static final String TAG = "EffectPluginLoader";

    /** Ordner AUF DEM MIB (App-Partition), der nach *.ambiente-Dateien
     *  durchsucht wird - siehe Klassenkommentar, bewusst nicht die SD-Karte. */
    private static final String PLUGIN_DIR = "/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects";

    private static final String EXTENSION = ".ambiente";

    private EffectPluginLoader() {}

    /** Durchsucht PLUGIN_DIR und registriert jede erfolgreich geladene
     *  Datei in FxCatalog. Fehlt der Ordner, fehlt die SD-Karte, oder ist
     *  eine einzelne Datei defekt/inkompatibel: wird geloggt und
     *  uebersprungen, nie ein Absturz.
     *
     *  Reihenfolge im Menue = alphabetische Reihenfolge der Dateinamen
     *  (sortByName() unten) - dir.listFiles() liefert sonst die rohe,
     *  nicht definierte Dateisystem-Reihenfolge, die je nach Kopiervorgang
     *  wechseln kann. Dateiname und Label sind in diesem Projekt bewusst
     *  gleich benannt (z.B. atmosphere.ambiente -> "Atmosphere"), daher
     *  entspricht das auch der im Menue sichtbaren Reihenfolge. */
    public static void scanAndRegister() {
        File dir = new File(PLUGIN_DIR);
        File[] files = dir.listFiles();
        if (files == null) {
            Log.i(TAG, "kein Plugin-Ordner gefunden (" + PLUGIN_DIR + ") - ueberspringe, nur eingebaute Effekte aktiv");
            return;
        }
        sortByName(files);
        int loaded = 0;
        int skipped = 0;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String name = f.getName();
            if (f.isDirectory() || !endsWithIgnoreCase(name, EXTENSION)) {
                continue;
            }
            try {
                loadOne(f);
                loaded++;
            } catch (Throwable t) {
                skipped++;
                Log.e(TAG, "Plugin uebersprungen (" + name + "): " + t, t);
            }
        }
        Log.i(TAG, "Plugin-Scan fertig: " + loaded + " geladen, " + skipped + " uebersprungen (" + PLUGIN_DIR + ")");
    }

    private static void loadOne(File file) throws Exception {
        URL url = file.toURL();
        final URLClassLoader loader = new URLClassLoader(new URL[] { url });

        InputStream in = loader.getResourceAsStream("plugin.properties");
        if (in == null) {
            throw new Exception("keine plugin.properties enthalten");
        }
        Properties props = new Properties();
        try {
            props.load(in);
        } finally {
            in.close();
        }

        final String className = props.getProperty("class");
        String label = props.getProperty("label");
        String versionStr = props.getProperty("version");
        if (className == null || className.length() == 0) {
            throw new Exception("plugin.properties ohne 'class'-Eintrag");
        }
        if (label == null || label.length() == 0) {
            label = file.getName();
        }
        int version;
        try {
            version = Integer.parseInt(versionStr == null ? "" : versionStr.trim());
        } catch (NumberFormatException nfe) {
            throw new Exception("plugin.properties ohne gueltige 'version' (gebaut mit altem build_plugin.sh?)");
        }
        if (version != Effect.CONTRACT_VERSION) {
            throw new Exception("Versions-Mismatch: Plugin ist fuer Version " + version
                    + " gebaut, aktuell gueltig ist Version " + Effect.CONTRACT_VERSION
                    + " - mit aktuellem build_plugin.sh neu bauen");
        }

        final Class effectClass = loader.loadClass(className);
        if (!Effect.class.isAssignableFrom(effectClass)) {
            throw new Exception(className + " implementiert nicht de.mk.ambientfx.Effect");
        }
        /* Probelauf: einmal instanziieren, damit Klassenfehler (fehlende
         * Abhaengigkeit, falscher Konstruktor ...) schon jetzt beim Scan
         * auffliegen statt erst beim Aktivieren im Menue. */
        effectClass.newInstance();

        FxCatalog.registerPlugin(label, new EffectFactory() {
            public Effect create() {
                try {
                    return (Effect) effectClass.newInstance();
                } catch (Throwable t) {
                    Log.e(TAG, "Instanziierung fehlgeschlagen: " + className, t);
                    return null;
                }
            }
        });
        Log.i(TAG, "Plugin geladen: " + file.getName() + " -> " + className + " (\"" + label + "\")");
    }

    /** Einfacher Selection-Sort nach Dateiname (Java 1.2 kompatibel, keine
     *  Comparator-API noetig) - Anzahl Plugins ist immer klein (einstellig),
     *  Performance spielt keine Rolle. */
    private static void sortByName(File[] files) {
        for (int i = 0; i < files.length - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < files.length; j++) {
                if (files[j].getName().compareToIgnoreCase(files[minIdx].getName()) < 0) {
                    minIdx = j;
                }
            }
            if (minIdx != i) {
                File tmp = files[i];
                files[i] = files[minIdx];
                files[minIdx] = tmp;
            }
        }
    }

    private static boolean endsWithIgnoreCase(String s, String suffix) {
        int sl = s.length();
        int fl = suffix.length();
        if (sl < fl) {
            return false;
        }
        return s.substring(sl - fl).equalsIgnoreCase(suffix);
    }
}
