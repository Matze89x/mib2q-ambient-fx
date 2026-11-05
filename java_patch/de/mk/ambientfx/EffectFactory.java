/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 *
 * AmbientFX - kleine Fabrik fuer eine Effekt-Instanz.
 *
 * Wird gebraucht, weil FxCatalog jetzt zur Laufzeit wachsen kann (siehe
 * EffectPluginLoader) - fuer eingebaute Effekte ist die Fabrik ein
 * anonymer "new Momentum()"-Wrapper, fuer nachgeladene Plugins ruft sie
 * per Reflection effectClass.newInstance() auf (siehe EffectPluginLoader).
 *
 * Java 1.2 kompatibel.
 */
package de.mk.ambientfx;

public interface EffectFactory {
    Effect create();
}
