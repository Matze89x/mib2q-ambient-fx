/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - Vertrag fuer einen Licht-Effekt.
 *
 * Ein Effekt schreibt pro Frame seine Wunschfarbe fuer die Kontur/Linie in
 * contourOut. Die Flaeche (Ambient) fasst AmbientFx bewusst nicht an - sie
 * bleibt komplett dem manuellen Farbregler im MIB ueberlassen (eigene, vom
 * Fahrzeug selbst gemerkte Werte).
 *
 * factoryPalette ist die vom Fahrzeug per DSI gemeldete Liste der im
 * "individuell"-Profil waehlbaren Werksfarben (kann null/leer sein, bevor
 * sie eingetroffen ist). Hintergrund: ein Retrofit-LED-Streifen im
 * Fahrzeug stellt nur genau diese Farben zuverlaessig dar, frei gestreamte
 * RGB-Werte dazwischen flackern. Ein Effekt, der das beachten muss, snappt
 * seine IDEALE Farbe selbst per Rgb.nearest(...) auf die naechste
 * Werksfarbe, BEVOR er seine eigene Helligkeit aufmultipliziert (Farbe und
 * Helligkeit sollen unabhaengig bleiben - siehe Momentum.tick()).
 *
 * Tag/Nacht-Dimmung der Kontur macht seit v3 nicht mehr die Engine
 * pauschal, sondern jeder Effekt bei Bedarf selbst - ueber den daylight-
 * Parameter (siehe unten). Quelle: AmbientFx.resolveDaylight(), gespeist
 * von DaylightData (siehe dort) - live im Fahrzeug bestaetigt.
 *
 * Java 1.2 kompatibel: keine Generics, keine Enums.
 */
package de.mk.ambientfx;

public interface Effect {

    /** Version dieses Vertrags - siehe EffectPluginLoader/build_plugin.sh.
     *  Jede .ambiente-Datei traegt in plugin.properties die Version, fuer
     *  die sie gebaut wurde (von build_plugin.sh automatisch aus dieser
     *  Konstante gestempelt); passt sie nicht zur hier aktuell gueltigen
     *  Zahl, wird das Plugin beim Laden abgelehnt statt mit einem evtl.
     *  inkompatiblen tick()-Aufruf zu scheitern. Hochzaehlen, wenn sich
     *  diese Schnittstelle aendert (z.B. neuer tick()-Parameter). */
    int CONTRACT_VERSION = 1;

    /** Wird einmal beim Aktivieren aufgerufen (Zustand zuruecksetzen). */
    void start(long nowMs);

    /**
     * Naechster Frame.
     *
     * @param nowMs          Monotone Zeit in ms
     * @param vehicle        Letzte bekannte Fahrzeugdaten (nie null, ggf. leer/ungueltig)
     * @param factoryPalette Aktuelle Audi-Werksfarbliste (kann null/leer sein)
     * @param daylight       true = Tag (Default, bis DaylightData das erste
     *                       echte Update hat - "fail visible"), false = Nacht
     * @param contourOut     Zielfarbe Kontur/Linie - der Effekt schreibt r/g/b (0..255) hinein
     * @return true wenn ein neuer Wert gesendet werden soll
     */
    boolean tick(long nowMs, VehicleData vehicle, Rgb[] factoryPalette, boolean daylight, Rgb contourOut);

    /** Sende-Intervall in ms, das dieser Effekt fuer fluessige Optik braucht. */
    int periodMs();
}
