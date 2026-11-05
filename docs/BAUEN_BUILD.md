# Bauen / Build

🇩🇪 **Deutsch** · 🇬🇧 [English below](#english)

> [!CAUTION]
> Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr – siehe [RECHT_LEGAL.md](RECHT_LEGAL.md). Nutzung auf eigene Gefahr.

Das Repository enthält **keinen Audi-Code**. Du erzeugst die drei Original-Klassen, die AmbientFX ersetzt, selbst aus **deiner eigenen** Firmware und wendest unsere kleinen Änderungen als Patch an. Danach baust du Jar und Plugins.

## Übersicht

```
deine lsd.jxe ──(jxe2jar)──▶ vendor/lsd.jar ──(decompile_stock.sh)──▶ java_stock/   (Audi-Code, nie einchecken)
                                                                          │
                                  patches/*.patch ──(prepare_sources.sh)──┴──▶ java_patch/de/audi/   (nie einchecken)
                                                                                     │
                       java_patch/de/mk/ambientfx/** (unser Code) ───────────────────┴──▶ build_java.sh ──▶ build/ambient_fx.jar
                                                                                          verify.sh      ──▶ Bytecode-Check
                                                                                          tools/build_all_plugins.sh ──▶ build/plugins/*.ambiente
```

`vendor/`, `java_stock/`, `java_patch/de/audi/` und `build/` stehen in der `.gitignore` und werden nie hochgeladen.

## 1. Voraussetzungen

- **JDK 8** (für Java-1.2-Bytecode): auf dem Mac `brew install --cask temurin@8`, das Skript nutzt `/usr/libexec/java_home -v 1.8`.
- **Java 17 oder neuer** nur für den Decompiler Vineflower.
- `bash`, `patch`, `unzip`, `python3`, `perl`.

## 2. `vendor/` anlegen

```text
vendor/lsd.jar                                     <- deine konvertierte lsd.jxe (siehe unten)
vendor/osgi/org.osgi.framework-1.10.0.jar
vendor/osgi/org.osgi.util.tracker-1.5.4.jar
vendor/decompilers/vineflower-1.12.0.jar
```

```bash
mkdir -p vendor/osgi vendor/decompilers
curl -L -o vendor/osgi/org.osgi.framework-1.10.0.jar \
  https://repo1.maven.org/maven2/org/osgi/org.osgi.framework/1.10.0/org.osgi.framework-1.10.0.jar
curl -L -o vendor/osgi/org.osgi.util.tracker-1.5.4.jar \
  https://repo1.maven.org/maven2/org/osgi/org.osgi.util.tracker/1.5.4/org.osgi.util.tracker-1.5.4.jar
curl -L -o vendor/decompilers/vineflower-1.12.0.jar \
  https://github.com/Vineflower/vineflower/releases/download/1.12.0/vineflower-1.12.0.jar
```

**`lsd.jar`:** Die HMI deines Geräts liegt als `lsd.jxe` im HMI-Ordner (`/mnt/app/eso/hmi/lsd/`). Kopiere sie von **deinem** Gerät, auf das du selbst Zugriff hast, und wandle sie mit einem JXE→JAR-Konverter (z. B. dem Community-Werkzeug *jxe2jar*) in `vendor/lsd.jar` um. Dieses Repository liefert weder die `lsd.jxe` noch die `lsd.jar`.

## 3. Quellen vorbereiten

```bash
VF_JAVA=/pfad/zu/java17/bin/java tools/decompile_stock.sh   # erzeugt java_stock/ aus deiner lsd.jar
tools/prepare_sources.sh                                     # erzeugt java_patch/de/audi/ (= java_stock + unsere Patches)
```

`prepare_sources.sh` bricht mit einer Fehlermeldung ab, wenn ein Patch nicht passt. Das passiert, wenn deine Firmware oder dein Decompiler von unserem Stand abweicht. **In diesem Fall nicht einfach weiterbauen**, sondern die Stelle prüfen (ein Issue im Repository hilft).

## 4. Bauen und prüfen

```bash
./build_java.sh               # kompiliert mit -source/-target 1.2 -> build/ambient_fx.jar
./verify.sh                   # vergleicht die drei ersetzten Klassen mit dem Original
tools/build_all_plugins.sh    # baut die fünf Effekte als .ambiente nach build/plugins/
```

`verify.sh` muss bei den drei Klassen **„nur erwartete Abweichungen“** und bei den Class-Versionen **„alle ok“** (Version 46 = Java 1.2) melden. **Meldet es etwas anderes, nicht installieren.**

Die Jar enthält die Effekt-Klassen, und jedes `.ambiente` enthält dieselbe Effekt-Klasse noch einmal (Parent-first-Classloading). Deshalb gilt: **Jar und alle Plugins immer zusammen bauen und installieren.**

## Java-1.2-Regeln

Die VM des MHI2Q versteht nur Java-1.2-Bytecode: **keine Generics, keine Enums, keine Lambdas, kein `StringBuilder`**, keine Annotationen. Der Compiler (`-source 1.2`) lehnt das meiste ab, `verify.sh` prüft die Class-Versionen.

## Fehlersuche

| Meldung | Ursache |
|---|---|
| `lsd.jar nicht gefunden` | `vendor/lsd.jar` fehlt oder `LSD_JAR=…` setzen. |
| `… passt nicht / does not apply` | Andere Firmware oder anderer Decompiler als erwartet. |
| `cannot find symbol … SingleZoneIdentity` | Decompiler-Ausgabe weicht ab; `decompile_stock.sh` repariert genau diese Stelle, bei anderer Firmware können weitere nötig sein. |
| `verify.sh`: `MISSING`/`DIFF (unexpected)` | Eine ersetzte Klasse weicht ab. Nicht installieren. |

---

<a id="english"></a>

# Build

🇩🇪 [Deutsch oben](#bauen--build) · 🇬🇧 **English** (English version of the German text above)

> [!CAUTION]
> For testing on private property only, not in public road traffic – see [RECHT_LEGAL.md](RECHT_LEGAL.md). Use at your own risk.

The repository contains **no Audi code**. You generate the three original classes AmbientFX replaces yourself from **your own** firmware and apply our small changes as a patch. Then you build the jar and the plugins.

## Overview

```
your lsd.jxe ──(jxe2jar)──▶ vendor/lsd.jar ──(decompile_stock.sh)──▶ java_stock/   (Audi code, never commit)
                                                                        │
                                patches/*.patch ──(prepare_sources.sh)──┴──▶ java_patch/de/audi/   (never commit)
                                                                                   │
                     java_patch/de/mk/ambientfx/** (our code) ─────────────────────┴──▶ build_java.sh ──▶ build/ambient_fx.jar
                                                                                        verify.sh      ──▶ bytecode check
                                                                                        tools/build_all_plugins.sh ──▶ build/plugins/*.ambiente
```

`vendor/`, `java_stock/`, `java_patch/de/audi/` and `build/` are in `.gitignore` and are never uploaded.

## 1. Requirements

- **JDK 8** (for Java 1.2 bytecode): on a Mac `brew install --cask temurin@8`; the script uses `/usr/libexec/java_home -v 1.8`.
- **Java 17 or newer** only for the Vineflower decompiler.
- `bash`, `patch`, `unzip`, `python3`, `perl`.

## 2. Create `vendor/`

See the file layout and the `curl` commands in the German part above (they are identical).

**`lsd.jar`:** your unit’s HMI is the file `lsd.jxe` in the HMI folder (`/mnt/app/eso/hmi/lsd/`). Copy it from **your own** unit that you have access to, and convert it with a JXE→JAR converter (e.g. the community tool *jxe2jar*) into `vendor/lsd.jar`. This repository ships neither the `lsd.jxe` nor the `lsd.jar`.

## 3. Prepare the sources

```bash
VF_JAVA=/path/to/java17/bin/java tools/decompile_stock.sh   # creates java_stock/ from your lsd.jar
tools/prepare_sources.sh                                     # creates java_patch/de/audi/ (= java_stock + our patches)
```

`prepare_sources.sh` aborts with an error if a patch does not apply. That happens if your firmware or decompiler differs from ours. **In that case do not just keep building**, check the spot first (an issue in the repository helps).

## 4. Build and verify

```bash
./build_java.sh               # compiles with -source/-target 1.2 -> build/ambient_fx.jar
./verify.sh                   # compares the three replaced classes with the original
tools/build_all_plugins.sh    # builds the five effects as .ambiente into build/plugins/
```

`verify.sh` must report **“only expected differences”** for the three classes and **“all ok”** for the class versions (version 46 = Java 1.2). **If it reports anything else, do not install.**

The jar contains the effect classes, and every `.ambiente` contains the same effect class again (parent-first class loading). Therefore: **always build and install the jar and all plugins together.**

## Java 1.2 rules

The MHI2Q VM only understands Java 1.2 bytecode: **no generics, no enums, no lambdas, no `StringBuilder`**, no annotations. The compiler (`-source 1.2`) rejects most of it; `verify.sh` checks the class versions.

## Troubleshooting

| Message | Cause |
|---|---|
| `lsd.jar not found` | `vendor/lsd.jar` is missing, or set `LSD_JAR=…`. |
| `… does not apply` | Different firmware or decompiler than expected. |
| `cannot find symbol … SingleZoneIdentity` | Decompiler output differs; `decompile_stock.sh` fixes exactly this spot, other firmware may need more. |
| `verify.sh`: `MISSING`/`DIFF (unexpected)` | A replaced class differs. Do not install. |
