/*
 * Test fuer RamLog (Marker, RAM-Datei, Obergrenze). Aufruf: java -cp <Klassen> de.mk.ambientfx.RamLogTest <leeres Arbeitsverzeichnis>
 * Kein JUnit; gibt "ok: ..." / "FEHLER: ..." aus, Exit-Code 1 bei Fehler.
 */
package de.mk.ambientfx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class RamLogTest {

    static int bad = 0;

    static void check(boolean c, String what) {
        if (c) {
            System.out.println("ok: " + what);
        } else {
            System.out.println("FEHLER: " + what);
            bad++;
        }
    }

    static String read(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        int n = 0;
        while (n < b.length) {
            int r = in.read(b, n, b.length - n);
            if (r < 0) {
                break;
            }
            n += r;
        }
        in.close();
        return new String(b, 0, n);
    }

    static int count(String s, String sub) {
        int c = 0;
        int i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            /* egal */
        }
    }

    public static void main(String[] a) throws Exception {
        File base = new File(a[0]);
        File ram = new File(base, "shmem");
        File persist = new File(base, "persist");
        ram.mkdirs();
        persist.mkdirs();
        File marker = new File(persist, "debug");
        File log = new File(ram, "ambientfx.log");
        RamLog.markerPath = marker.getPath();
        RamLog.dirs = new String[] { ram.getPath() };
        RamLog.checkMs = 50L;

        /* 1. ohne Marker: nichts */
        check(!RamLog.on(), "ohne Marker: on() = false");
        RamLog.log("T", "unsichtbar");
        Log.i("T", "auch unsichtbar");
        check(!log.exists(), "ohne Marker: keine Datei angelegt");

        /* 2. mit Marker: Kopfzeile + Zeilen, Messwert nur bei Aenderung */
        new FileOutputStream(marker).close();
        sleep(80);
        check(RamLog.on(), "mit Marker: on() = true");
        RamLog.log("T", "Zeile eins");
        RamLog.value("effect", "Momentum");
        RamLog.value("effect", "Momentum");
        RamLog.value("effect", "Drive");
        RamLog.error("T", "kaputt", new RuntimeException("boom"));
        String s = read(log);
        check(s.indexOf("Debug AN") >= 0, "Kopfzeile 'Debug AN'");
        check(s.indexOf("[+") >= 0 && s.indexOf("] T: Zeile eins") >= 0, "Zeile mit Zeitstempel und Tag");
        check(count(s, "fx.effect = Momentum") == 1 && count(s, "fx.effect = Drive") == 1, "Messwert nur bei Aenderung (1x Momentum, 1x Drive)");
        check(s.indexOf("ERROR kaputt") >= 0 && s.indexOf("RuntimeException") >= 0, "Fehler mit Stacktrace");

        /* 3. Obergrenze: schreibt bis kurz vor MAX_BYTES, dann genau EINE Schlusszeile, danach nichts mehr */
        String filler = "";
        for (int i = 0; i < 300; i++) {
            filler = filler + "x";
        }
        for (int i = 0; i < 2000; i++) {
            RamLog.log("T", "fuell " + i + " " + filler);
        }
        long len1 = log.length();
        s = read(log);
        check(len1 <= RamLog.MAX_BYTES, "Datei <= MAX_BYTES (" + len1 + " <= " + RamLog.MAX_BYTES + ")");
        check(len1 > RamLog.MAX_BYTES / 2, "Datei wurde bis nahe an die Grenze gefuellt (" + len1 + ")");
        check(count(s, "GRENZE") == 1, "genau eine Schlusszeile 'GRENZE'");
        check(s.indexOf("Zeile eins") >= 0 && s.indexOf("Debug AN") >= 0, "Anfang bleibt erhalten (nichts rotiert, nichts geloescht)");
        for (int i = 0; i < 50; i++) {
            RamLog.log("T", "nach der Grenze " + i);
        }
        check(log.length() == len1 && read(log).indexOf("nach der Grenze") < 0, "nach der Grenze wird nichts mehr geschrieben");
        sleep(80);
        RamLog.log("T", "immer noch nach der Grenze");
        check(log.length() == len1, "auch nach erneuter Marker-Pruefung weiter Stopp");

        /* 4. Datei loeschen = Neustart des Protokolls */
        log.delete();
        sleep(80);
        RamLog.log("T", "nach rm");
        check(log.exists() && read(log).indexOf("nach rm") >= 0, "nach 'rm' der Datei wird wieder geschrieben");
        check(read(log).indexOf("GRENZE") < 0, "neue Datei beginnt ohne alte Schlusszeile");

        /* 5. Marker weg: Schlusszeile 'AUS', danach nichts mehr */
        marker.delete();
        sleep(80);
        RamLog.log("T", "nach Marker-rm");
        s = read(log);
        check(s.indexOf("Debug AUS") >= 0, "Marker entfernt: Zeile 'Debug AUS'");
        check(s.indexOf("nach Marker-rm") < 0, "Marker entfernt: danach nichts mehr");
        long len2 = log.length();
        Log.w("T", "warn");
        Log.e("T", "err", new RuntimeException("x"));
        check(log.length() == len2, "Log.w/Log.e ohne Marker schreiben nichts");

        /* 6. unbeschreibbares Ziel: kein Fehler nach aussen, Fallback auf zweiten Ordner */
        File ram2 = new File(base, "tmp2");
        ram2.mkdirs();
        RamLog.resetForTest();
        RamLog.markerPath = marker.getPath();
        RamLog.checkMs = 50L;
        RamLog.dirs = new String[] { new File(base, "gibt-es-nicht/x").getPath(), ram2.getPath() };
        new FileOutputStream(marker).close();
        sleep(80);
        RamLog.log("T", "Fallback");
        File fb = new File(ram2, "ambientfx.log");
        check(fb.exists() && read(fb).indexOf("Fallback") >= 0, "Fallback auf zweiten Ordner, wenn der erste nicht beschreibbar ist");
        marker.delete();

        System.out.println(bad == 0 ? "ALLE OK" : ("FEHLER: " + bad));
        System.exit(bad == 0 ? 0 : 1);
    }
}
