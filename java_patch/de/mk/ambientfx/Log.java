/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 * License: see LICENSE.md (attribution required, NO commercial use / selling)
 *
 * AmbientFX - Debug-Fassade. / AmbientFX debug facade.
 *
 * DE: Standardmaessig AUS. Eingeschaltet wird per Marker-Datei /mnt/persist/ambientfx/debug (bleibt nach einem Neustart erhalten, bis du sie
 *     loeschst). Das Protokoll liegt NUR im RAM (/dev/shmem/ambientfx.log) und ist per Telnet lesbar, mit fester Obergrenze (256 KB pro Start,
 *     danach Stopp, nichts rotiert, nichts geloescht). Ohne Marker wird nichts geschrieben. Kein Netz. Details: RamLog.java und docs/TECHNIK.md.
 * EN: OFF by default. Enabled by the marker file /mnt/persist/ambientfx/debug (survives a reboot until you delete it). The log lives in RAM ONLY
 *     (/dev/shmem/ambientfx.log), readable via telnet, with a hard limit (256 KB per boot, then it stops; nothing rotated, nothing deleted).
 *     Without the marker nothing is written. No network. Details: RamLog.java and docs/TECHNIK.md.
 *
 * Wer teuren Text zusammenbaut, fragt vorher enabled() ab. / Check enabled() before building expensive text.
 *
 * Java 1.2 kompatibel (J9 / MHI2Q).
 */
package de.mk.ambientfx;

public final class Log {

    private Log() {}

    /** true, solange der Marker da ist (hoechstens alle 2 s neu geprueft). / true while the marker exists (re-checked at most every 2 s). */
    public static boolean enabled() {
        return RamLog.on();
    }

    public static void i(String tag, String msg) {
        RamLog.log(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (RamLog.on()) {
            RamLog.log(tag, "WARN " + msg);
        }
    }

    public static void e(String tag, String msg, Throwable t) {
        RamLog.error(tag, msg, t);
    }

    /** Messwert (Name/Wert), nur bei Aenderung geschrieben. / Measured value (name/value), written on change only. */
    public static void value(String name, Object v) {
        RamLog.value(name, v);
    }
}
