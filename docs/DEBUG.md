<a id="english"></a>

# Debug log (RAM only)

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> For testing on private property only, not in public road traffic – see [RECHT_LEGAL.md](RECHT_LEGAL.md). Telnet access to the head unit is **not** part of this project and is at your own risk.

AmbientFX can keep a **log in working memory (RAM)** that you read via telnet, for example when an effect does not react as expected. It is **off by default**.

## Properties

- **Marker only:** without the marker file **nothing** is written, no file is created.
- **RAM only:** the log lives in `/dev/shmem/ambientfx.log` and is **gone after every reboot** of the unit. It is never written to flash or SD. If `/dev/shmem` is not writable, AmbientFX falls back to `/tmp/ambientfx.log`.
- **The marker stays:** the marker lives on `/mnt/persist` and **survives a reboot** until you delete it. So after you enable it, AmbientFX logs from the start of every drive without you opening telnet each time.
- **Hard limit:** **256 KB per boot.** Once reached, AmbientFX writes a final line (`=== GRENZE 256 KB erreicht … ===` (German for “limit of 256 KB reached”)) and **stops writing**. **Nothing is rotated and nothing is deleted**; the beginning is kept. Reset: delete the file (`rm`) or reboot the unit.
- **No network, no own thread.** Every write error is swallowed; the log cannot disturb the HMI.
- **Content:** lines like `[+12.345s] AmbientFx: …` (seconds since enabling, because the unit’s clock is unreliable), errors with stack trace and measured values (`fx.<name> = <value>`, on change only). No vehicle identification number, no position. Speed, gear and effect names can appear though: **look at the log before you post it anywhere.**

## Use (telnet)

```sh
# 1) Set the marker (once; it stays after a reboot)
mount -uw /net/mmx/mnt/persist            # if /mnt/persist is read-only
mkdir -p /mnt/persist/ambientfx
touch /mnt/persist/ambientfx/debug

# 2) Drive or test (the marker is checked every ~2 s, no reboot needed)

# 3) Read it
cat /dev/shmem/ambientfx.log               # everything
tail -f /dev/shmem/ambientfx.log           # follow live

# 4) Switch off
rm /mnt/persist/ambientfx/debug
```

The log lives on the **MMX** (where the HMI runs). With telnet on **port 23** (MMX directly) the paths above apply. With telnet on **port 123** (RCC) prefix `/net/mmx`: `/net/mmx/dev/shmem/ambientfx.log` and marker `/net/mmx/mnt/persist/ambientfx/debug` (see [DEPLOY_TELNET.md](../DEPLOY_TELNET.md)).

The **SD menu** has no entry of its own for this: *Status* only shows whether the marker is set, and *Uninstall* also deletes the marker and the RAM log.

## Limits

- An error that happens **before** the marker was set is not in the log. The RAM log is gone after a reboot even though the marker stays.
- The log says what AmbientFX **sees and does**. It is not a capture of the vehicle bus.
- Whether `/dev/shmem` is writable and really RAM on your unit is **not yet confirmed on the author’s test unit**. Check with `mount` and `df /dev/shmem`.

## For developers

The code is in `java_patch/de/mk/ambientfx/RamLog.java` (sink) and `Log.java` (facade: `Log.i`, `Log.w`, `Log.e`, `Log.value`, `Log.enabled`). Check `Log.enabled()` before building expensive text. The test `deploy/sd-menu/tests/test_ramlog.sh` checks marker, limit, reset and fallback folder (compiled with Java 1.2 rules).

---

<a id="deutsch"></a>

# Debug-Protokoll (nur im RAM)

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr – siehe [RECHT_LEGAL.md](RECHT_LEGAL.md). Telnet-Zugriff aufs Steuergerät ist **nicht** Teil dieses Projekts und geschieht auf eigene Gefahr.

AmbientFX kann ein **Protokoll im Arbeitsspeicher (RAM)** führen, das du per Telnet auslesen kannst, zum Beispiel wenn ein Effekt nicht wie erwartet reagiert. Es ist **standardmäßig aus**.

## Eigenschaften

- **Nur mit Marker:** Ohne die Marker-Datei wird **nichts** geschrieben, es entsteht keine Datei.
- **Nur im RAM:** Das Protokoll liegt in `/dev/shmem/ambientfx.log` und ist nach jedem **Neustart des Geräts weg**. Es wird nie auf Flash oder SD geschrieben. Ist `/dev/shmem` nicht beschreibbar, weicht AmbientFX auf `/tmp/ambientfx.log` aus.
- **Marker bleibt:** Der Marker liegt auf `/mnt/persist` und **überlebt den Neustart**, bis du ihn löschst. So protokolliert AmbientFX nach dem Einschalten bei jeder Fahrt ab dem Start mit, ohne dass du jedes Mal Telnet öffnen musst.
- **Feste Obergrenze:** **256 KB pro Start.** Ist sie erreicht, schreibt AmbientFX eine Schlusszeile (`=== GRENZE 256 KB erreicht … ===`) und **hört auf zu schreiben**. Es wird **nichts rotiert und nichts gelöscht**, der Anfang bleibt erhalten. Zurücksetzen: Datei löschen (`rm`) oder das Gerät neu starten.
- **Kein Netz, kein eigener Thread.** Jeder Schreibfehler wird verschluckt, das Protokoll kann die HMI nicht stören.
- **Inhalt:** Zeilen wie `[+12.345s] AmbientFx: …` (Sekunden seit dem Einschalten, weil die Geräteuhr unzuverlässig ist), Fehler mit Stacktrace und Messwerte (`fx.<name> = <wert>`, nur bei Änderung). Keine Fahrzeug-Identnummer, keine Position. Es können aber Tempo, Gang und Effektnamen vorkommen: **schau ins Protokoll, bevor du es irgendwo postest.**

## Benutzen (Telnet)

```sh
# 1) Marker setzen (einmal; bleibt nach Neustart erhalten)
mount -uw /net/mmx/mnt/persist            # falls /mnt/persist schreibgeschützt ist
mkdir -p /mnt/persist/ambientfx
touch /mnt/persist/ambientfx/debug

# 2) Fahren oder testen (der Marker wird alle ~2 s geprüft, kein Neustart nötig)

# 3) Auslesen
cat /dev/shmem/ambientfx.log               # alles
tail -f /dev/shmem/ambientfx.log           # live mitlesen

# 4) Ausschalten
rm /mnt/persist/ambientfx/debug
```

Das Protokoll liegt auf der **MMX** (dort läuft die HMI). Per Telnet auf **Port 23** (MMX direkt) gelten die Pfade oben. Per Telnet auf **Port 123** (RCC) stellst du `/net/mmx` voran: `/net/mmx/dev/shmem/ambientfx.log` und Marker `/net/mmx/mnt/persist/ambientfx/debug` (siehe [DEPLOY_TELNET.md](../DEPLOY_TELNET.md)).

Das **SD-Menü** hat dafür keinen eigenen Punkt: *Status* zeigt nur, ob der Marker gesetzt ist, und *Deinstallieren* löscht Marker und RAM-Protokoll mit.

## Grenzen

- Ein Fehler, der passiert, **bevor** der Marker gesetzt war, ist nicht im Protokoll. Das RAM-Protokoll ist nach einem Neustart weg, auch wenn der Marker bleibt.
- Das Protokoll sagt, was AmbientFX **sieht und tut**. Es ist kein Mitschnitt des Fahrzeug-Busses.
- Ob `/dev/shmem` auf deinem Gerät beschreibbar und wirklich RAM ist, ist **auf dem Testgerät des Autors noch nicht bestätigt**. Prüfe es mit `mount` und `df /dev/shmem`.

## Für Entwickler

Der Code steht in `java_patch/de/mk/ambientfx/RamLog.java` (Senke) und `Log.java` (Fassade: `Log.i`, `Log.w`, `Log.e`, `Log.value`, `Log.enabled`). Wer teuren Text zusammenbaut, fragt vorher `Log.enabled()` ab. Der Test `deploy/sd-menu/tests/test_ramlog.sh` prüft Marker, Obergrenze, Zurücksetzen und Ausweichordner (mit Java-1.2-Regeln übersetzt).
