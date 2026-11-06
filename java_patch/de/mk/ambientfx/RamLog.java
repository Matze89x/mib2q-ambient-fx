/*
 * Copyright (c) 2026 Matze89x in cooperation with DEB and LuKa_dev
 * Lizenz: CC BY-NC-SA 4.0, siehe LICENSE.md (Namensnennung Pflicht, KEINE kommerzielle Nutzung/Verkauf)
 * License: CC BY-NC-SA 4.0, see LICENSE.md (attribution required, NO commercial use / selling)
 *
 * AmbientFX - Debug-Protokoll NUR im Arbeitsspeicher (RAM), nur per Marker, mit fester Obergrenze.
 * AmbientFX debug log in RAM ONLY, only via a marker file, with a hard size limit.
 *
 * DE:
 *   Einschalten (einmal per Telnet):   touch /mnt/persist/ambientfx/debug      (ggf. vorher: mount -uw /net/mmx/mnt/persist)
 *   Der Marker liegt auf /mnt/persist und bleibt nach einem Neustart erhalten, bis du ihn loeschst (rm).
 *   Ausschalten:                       rm /mnt/persist/ambientfx/debug
 *   Auslesen (per Telnet):             cat /dev/shmem/ambientfx.log           (falls /dev/shmem nicht beschreibbar ist: /tmp/ambientfx.log)
 *   Das Protokoll liegt im RAM (/dev/shmem) und ist nach jedem Neustart des Geraetes weg. Ohne Marker wird NICHTS geschrieben.
 *   Grenze: MAX_BYTES (256 KB) pro Start. Ist sie erreicht, schreibt AmbientFX eine Schlusszeile und hoert auf zu schreiben.
 *   Es wird NICHTS rotiert und NICHTS geloescht (der Anfang bleibt erhalten). Zuruecksetzen: Datei loeschen (rm) oder Neustart.
 *   Kein Netz, kein eigener Thread, keine Fahrzeug-Kennung, keine Position. Jeder Fehler beim Schreiben wird verschluckt.
 *
 * EN:
 *   Enable (once, via telnet):   touch /mnt/persist/ambientfx/debug   (if needed first: mount -uw /net/mmx/mnt/persist)
 *   The marker lives on /mnt/persist and survives a reboot until you delete it (rm).
 *   Disable:                     rm /mnt/persist/ambientfx/debug
 *   Read (via telnet):           cat /dev/shmem/ambientfx.log          (if /dev/shmem is not writable: /tmp/ambientfx.log)
 *   The log lives in RAM (/dev/shmem) and is gone after every reboot of the unit. Without the marker NOTHING is written.
 *   Limit: MAX_BYTES (256 KB) per boot. Once reached, AmbientFX writes a final line and stops writing.
 *   NOTHING is rotated and NOTHING is deleted (the beginning is kept). Reset: delete the file (rm) or reboot.
 *   No network, no own thread, no vehicle identifier, no position. Every write error is swallowed.
 *
 * Java 1.2 kompatibel (J9 / MHI2Q): kein Generics, kein String.format, kein StringBuilder, kein Autoboxing.
 */
package de.mk.ambientfx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

final class RamLog {

    /* Schalter: Datei vorhanden = AN. Auf /mnt/persist, ueberlebt den Neustart. */
    static final String MARKER_DEFAULT = "/mnt/persist/ambientfx/debug";
    /* Zielordner in dieser Reihenfolge: zuerst /dev/shmem (RAM), sonst /tmp. */
    static final String[] DIRS_DEFAULT = { "/dev/shmem", "/tmp" };
    static final String FILE_NAME = "ambientfx.log";

    /** Harte Obergrenze pro Start. Danach wird nicht mehr geschrieben (nichts rotiert, nichts geloescht). */
    static final long MAX_BYTES = 262144L;
    /** Platz, der fuer die Schlusszeile frei bleibt. */
    private static final long RESERVE = 256L;
    private static final long LIMIT = MAX_BYTES - RESERVE;
    private static final int MAX_LINE = 600;
    private static final int MAX_TRACE = 1500;
    private static final int MAX_VALUES = 64;

    /* nur fuer Tests ueberschreibbar */
    static String markerPath = MARKER_DEFAULT;
    static String[] dirs = DIRS_DEFAULT;
    static long checkMs = 2000L;

    private static long checkedMs = 0L;
    private static boolean on = false;
    private static boolean full = false;
    private static long size = 0L;
    private static long t0 = 0L;
    private static String path = null;          /* der erste Ordner, in den erfolgreich geschrieben wurde */
    private static final Hashtable lastValues = new Hashtable();

    private RamLog() {}

    /** Nur fuer Tests: setzt den gesamten Zustand zurueck. */
    static synchronized void resetForTest() {
        checkedMs = 0L;
        on = false;
        full = false;
        size = 0L;
        t0 = 0L;
        path = null;
        lastValues.clear();
    }

    /** true, solange der Marker da ist. Hoechstens alle checkMs neu geprueft (billig). */
    static synchronized boolean on() {
        long now = System.currentTimeMillis();
        if (checkedMs != 0L && now - checkedMs < checkMs && now >= checkedMs) {
            return on;
        }
        checkedMs = now;
        boolean was = on;
        boolean nowOn;
        try {
            nowOn = new File(markerPath).exists();
        } catch (Throwable t) {
            nowOn = false;
        }
        if (nowOn) {
            try {
                size = fileLength();
                if (full && size < MAX_BYTES / 2) {
                    full = false;               /* Datei wurde geloescht oder gekuerzt: wieder schreiben */
                }
                if (!full && size >= LIMIT) {
                    full = true;                /* schon voll (z. B. nach einem HMI-Neustart ohne Geraete-Neustart) */
                }
            } catch (Throwable t) {
                /* ignorieren */
            }
            on = true;
            if (!was) {
                t0 = now;
                lastValues.clear();
                raw("=== AmbientFX Debug AN | Marker " + markerPath + " | Grenze " + (MAX_BYTES / 1024L) + " KB ===");
            }
        } else {
            if (was) {
                raw("=== AmbientFX Debug AUS (Marker entfernt) ===");
            }
            on = false;
        }
        return on;
    }

    /** Protokollzeile (nur wenn der Marker da ist). */
    static synchronized void log(String tag, String msg) {
        if (!on()) {
            return;
        }
        raw(stamp() + tag + ": " + msg);
    }

    static synchronized void error(String tag, String msg, Throwable th) {
        if (!on()) {
            return;
        }
        String s = stamp() + tag + ": ERROR " + msg;
        if (th != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                th.printStackTrace(pw);
                pw.flush();
                String tr = sw.toString();
                if (tr.length() > MAX_TRACE) {
                    tr = tr.substring(0, MAX_TRACE) + "...";
                }
                s = s + "\n" + tr;
            } catch (Throwable t) {
                s = s + " (" + th + ")";
            }
        }
        raw(s);
    }

    /** Messwert; nur bei Aenderung geschrieben. */
    static synchronized void value(String name, Object v) {
        if (!on() || name == null || v == null) {
            return;
        }
        String s = v.toString();
        Object old = lastValues.get(name);
        if (old != null && old.equals(s)) {
            return;
        }
        if (old == null && lastValues.size() >= MAX_VALUES) {
            return;                              /* nie unbegrenzt wachsen */
        }
        lastValues.put(name, s);
        raw(stamp() + "fx." + name + " = " + s);
    }

    /* ---------------------------------------------------------------- intern */

    /** "[+12.345s] " - Sekunden seit dem Einschalten (die Uhr im Geraet ist unzuverlaessig). */
    private static String stamp() {
        long ms = System.currentTimeMillis() - t0;
        if (ms < 0L) {
            ms = 0L;
        }
        long frac = ms % 1000L;
        String f = frac < 10L ? "00" + frac : (frac < 100L ? "0" + frac : "" + frac);
        return "[+" + (ms / 1000L) + "." + f + "s] ";
    }

    private static long fileLength() {
        if (path != null) {
            return new File(path).length();
        }
        for (int i = 0; i < dirs.length; i++) {
            File f = new File(dirs[i], FILE_NAME);
            if (f.exists()) {
                return f.length();
            }
        }
        return 0L;
    }

    /** Eine Zeile anhaengen - mit Grenze. Verschluckt jeden Fehler. */
    private static void raw(String line) {
        if (full) {
            return;
        }
        try {
            if (line.length() > MAX_LINE && line.indexOf('\n') < 0) {
                line = line.substring(0, MAX_LINE) + "...";
            }
            byte[] b = (line + "\n").getBytes();
            if (size + b.length > LIMIT) {
                full = true;
                byte[] e = ("=== GRENZE " + (MAX_BYTES / 1024L) + " KB erreicht - Protokoll beendet. "
                        + "Neu starten: Datei loeschen (rm) oder Geraet neu starten ===\n").getBytes();
                if (append(e)) {
                    size += e.length;
                }
                return;
            }
            if (append(b)) {
                size += b.length;
            }
        } catch (Throwable t) {
            /* nie eskalieren */
        }
    }

    private static boolean append(byte[] b) {
        if (path != null) {
            if (appendTo(path, b)) {
                return true;
            }
            path = null;                         /* ausgefallen: naechsten Ordner versuchen */
        }
        for (int i = 0; i < dirs.length; i++) {
            String p = new File(dirs[i], FILE_NAME).getPath();
            if (appendTo(p, b)) {
                path = p;
                return true;
            }
        }
        return false;
    }

    private static boolean appendTo(String p, byte[] b) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(p, true);
            out.write(b);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable t) {
                    /* ignorieren */
                }
            }
        }
    }
}
