<a id="english"></a>

# AmbientFX – Technical documentation

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **For testing on private property only, not in public road traffic** (see [RECHT_LEGAL.md](RECHT_LEGAL.md)). Intervention in the HMI software, use at your own risk.

Java patch for the Audi MHI2Q (MU1329) that extends the menu **Vehicle settings → Ambient lighting** with animated and vehicle-reactive effects. This document describes the structure, how it works, and the analysis of the stock HMI.

Overview and quick start: [README](../README.md) · Build: [BAUEN_BUILD.md](BAUEN_BUILD.md) · Install: [INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md) · Your own effects: [EIGENE-DESIGNS_CUSTOM-DESIGNS.md](EIGENE-DESIGNS_CUSTOM-DESIGNS.md) · Licence: [LICENSE.md](../LICENSE.md) (applies to the original code, **not** to Audi/third-party code; the replaced Audi classes are not shipped here).

---

## What the patch does

Stock menu (screen 600028):

```
Kontur-Weiß        (profile 3)
Maritim-Blau       (profile 4)
Impuls-Rot         (profile 6)
individuell        (profile 1)
Audi drive select  (profile 2)
```

With the patch (jar) + the five bundled plugin files (see
“Plugin system” further down – since 25.09.2026 **no** effect is hard-wired
into the jar any more, all are installed separately as `.ambiente` files)
five entries are added below them (the order in the menu has been
alphabetical by file name since 26.09.2026, see “Plugin system”):

| Entry  | Contour (line)                                                  | Surface |
|----------|------------------------------------------------------------------|--------|
| Atmosphere | **Independent, deliberately non-reactive** mood (see “Atmosphere” below) – hue by outside temperature, brightness as a slow “breathing”, no speed/throttle/brake reaction. | untouched, like Momentum |
| Drive | **Throttle + steering angle** drive hue/saturation/brightness (see “Drive” below, concept/formulas taken from LuKa_dev) – rest = pale violet, full throttle swings towards warm ember, steering lock additionally spreads the hue. Brake = instantly full red like the others. | untouched, like Momentum |
| Momentum | Colour = run through **all 28 coloured Audi factory colours** over 0–130 km/h (red → orange → yellow → green → cyan → blue → violet → pink-red → **red again at 130 km/h**), blended continuously between neighbouring factory colours – see “Colour table Momentum / Momentum select” below. Brightness **always 100 % during the day** (independent of the throttle), **at night** 5 % at 0 % throttle, rising to 100 % at 50 % throttle and staying at 100 % above that (asymmetrically smoothed, brightens faster than it dims). Brake = instantly full red, full brightness, independent of everything else. **Gear lever D → S** (except in the Dynamic profile): once, instantly white at 100 % (day and night), held for 1 s, then a 2 s soft fade back to the normal colour (see “Gear lever D → S” below). | **untouched** – stays the manual colour/brightness control in the MIB (its own value remembered by the vehicle itself) |
| Momentum select | **Independent copy of Momentum** (see “Momentum Select” below) – same colour table (all factory colours, 0–130 km/h) and brake principle, but the colour range, brightness and reaction curve additionally depend on the **Audi drive select mode** (Comfort/Auto/Dynamic/Eco/Individual). The same D→S white sequence as Momentum (see “Gear lever D → S” below). | untouched, like Momentum |
| Scroll | **Pure rainbow show element** (see “Scroll” below, idea/formula taken from LuKa_dev) – the hue rotates endlessly over the colour wheel at a speed proportional to the vehicle speed, the colour keeps rotating slowly even at standstill (8°/s), the faster you drive the faster it turns. No throttle relation; at night a fixed dimming value (40 %), plus a very slow shimmer (15 s). Brake = instantly full red like the others. | untouched, like Momentum |

### Colour table Momentum / Momentum select (since 03.10.2026)

Reason: the colours changed too abruptly (snap to only **one** nearest
factory colour, 25 different colours over the whole speed range, jumps of up to 240
RGB levels per km/h) and many green/yellow tones of the vehicle list were never
shown. After the live tests with Scroll (free streaming, **no flicker**
on the retrofit strip) the snapping in Momentum and Momentum select
was replaced by a **colour ring from the factory list**:

* The list (30 entries, delivered by the vehicle at runtime: 28 coloured + 2 white) is
  filtered in `buildRing()` (saturation ≥ 0.35 → white drops out) and **sorted
  by hue starting at red**. The order of the list in the vehicle does not
  matter, the table adapts to the reported list.
* Speed → position on the **closed** ring: 0 km/h = first red,
  130 km/h = the same red again (13 steps of 10 km/h, `RAINBOW_SPAN_KMH=130`,
  `SPEED_STEP_KMH=10`). Between two factory colours it blends continuously
  (`SMOOTH_COLORS=true`); every 10 km/h passes through a good two factory colours.
  The last stretch (violet → pink-red → red) closes the circle without
  the list needing a magenta colour of its own.
* **Fallback on flicker:** `SMOOTH_COLORS=false` → only real factory colours
  in hard 10 km/h steps (14 colours, step = rounding to 10 km/h).
* Without a factory list (not arrived yet): pure hue as before.
* **Brake unchanged:** instantly factory red, full brightness. **Brightness unchanged**
  (day 100 %, night 5 % at 0 % throttle → 100 % at 50 % throttle; Comfort/Eco and
  Dynamic as before). **D→S white sequence unchanged.**
* Momentum select keeps its **colour ranges per profile**, now made of real
  factory colours: Auto/Individual = the whole ring (0 km/h red … 130 km/h red);
  Dynamic = section red → yellow over 0–130 km/h (throttle still pulls towards
  red); Comfort/Eco = section green → blue over 0–130 km/h.
* Measurement (simulation with a photo approximation of the factory colours, 1 km/h grid,
  0–140 km/h): Momentum **25 → 130 different colours**, largest jump per
  km/h **240 → 52**.
  The real 30 RGB values appear, with the debug marker set, as the line
  “factory color list values: …” in the log (they come from the vehicle, not from the
  MMI dump).
* Backup before the rework: `backups/2026-10-03-vor-farbtabelle/`.

### Factory colour snapping (until 02.10.2026, because of the retrofit LED strip)

> **Historical:** for Momentum/Momentum select it now only applies to the **brake**
> and the fallback without a factory list; for Atmosphere/Drive/Scroll see each of them.
> The assumption “free in-between values flicker” was not confirmed by the Scroll test
> (80–130 km/h, no flicker) – see “Colour table” above.

The retrofitted LED strip in the vehicle only shows the colours from the
**Audi’s own “individuell” colour list** reliably – freely streamed
in-between values flicker. That is why Momentum computes the rainbow hue
internally continuously but **snaps it before every send to the
nearest real factory colour** (`Rgb.nearest(...)`, the palette comes passively
via DSI from the vehicle, see `AmbientFx.onColorListChunk`). This is deliberately
no longer an optional setting (the earlier marker-file experiment is
gone) – on this vehicle with this LED it is the only configuration
that runs cleanly. Important: the brightness is multiplied in **separately
afterwards**, not snapped along – otherwise every dimmed colour would jump back
to the full factory colour and make the throttle brightness ineffective.
Until the factory colour list has arrived from the vehicle (it should happen
automatically at start, see the log line “factory color list
complete: N entries”), Momentum shows the unaltered rainbow hue.

**Important, different from v2: the engine no longer sends a surface at all.**
`Effect.tick()` only delivers the contour colour now; the surface you
still set completely as usual via the colour control in the MIB itself, and the
vehicle remembers this value like for any other profile – our
patch does not interfere there.

Technically the effect runs on the vehicle profile **“individuell” (1)**
(necessary so the contour can be freely determined): the engine switches the profile
and then only streams the contour colour via DSI to the vehicle. The
radio button in the menu stays on “Momentum”.

### Day/night behaviour

**Corrected after the first live test:** the first version had practically
0 % brightness permanently during the day with normal, moderate throttle (< 50 %) –
so the contour was practically invisible in normal daytime driving, only a
brief flash with a firm launch (throttle > 50 %) was visible. That was
a bug in the brightness logic, not a day/night switch-off.

The day/night detection deliberately sits **in the effect itself** (not as a
blanket rule at engine level – that existed in v3 before but was
removed because it would have cut off Momentum’s curve peak again). The source
is **`DaylightData`** (`de/mk/ambientfx/DaylightData.java`), **confirmed live in the
vehicle**: flipping the light switch flips the signal in real time.
`VehicleData.lowBeamOn` (the first, obvious signal), on the other hand, stayed
`false` throughout in several tests – instead the same
DSI service and the same attribute is used (`DSIGeneralVehicleStates`, attribute 16,
`updateDisplayDayNightDesign`), which demonstrably (by decompiling
`de.audi.app.terminalmode.NightDayModeHandler`) also feeds CarPlay’s own
day/night switching:

* **Day** – contour always **100 % bright**, independent of the throttle. During the day
  nothing dazzles, and “mostly invisible” was worse than “always
  visible”.
* **Night** – brightness follows the throttle. Curve readjusted several times:
  50–100 % (too tight) → 25–100 % linear over the whole throttle range → since
  26.09.2026 (second curve): **5 % at 0 % throttle** (never completely off), rises to
  **100 %** linearly up to **50 % throttle** and stays capped above that – resolves the
  everyday-relevant lower/middle throttle range more finely instead of reaching full brightness only at
  full throttle. Since 26.09.2026 additionally smoothed
  **asymmetrically** (idea from `luka-collection`, see
  `Momentum.java`): faster brightening when giving throttle (120 ms), noticeably
  slower dimming when releasing it (350 ms).
* **Brake** overrides both instantly with full red at full brightness.

### Momentum Select (drive-select-reactive variant)

**Deliberately a complete, independent copy** of `Momentum.java` –
`effects/MomentumSelect.java` imports/extends/references Momentum at no
point, so that the two effects cannot influence each other:
if one `.ambiente` file is deleted from the MIB, the other
effect stays runnable unchanged. The same basic principle (rainbow over speed through the factory colour ring,
instant red on brake), but the brightness/reaction curve additionally
depends on the **Audi drive select mode**; the source is the new
`DriveModeData` (`org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristics`,
attribute 13, `updateCharismaActiveProfile`) – the same attribute that had been
on the wish list for a while, connected here for the first time. It is queried
via `AmbientFx.getInstance().getDriveMode()`, deliberately **not** as an
additional parameter in `Effect.tick()`, so that Momentum stays
unchanged.

| Profile | Brightness | Reaction speed (hue / brightness up·down) |
|---|---|---|
| **Dynamic** | always 100 % (day *and* night, independent of the throttle) | much faster, **symmetric** (150 / 100·100 ms) – deliberately no attack/release, suits the snappy character |
| **Auto, Individual** | exactly like Momentum (day 100 %, night 5–100 % by throttle, capped from 50 % throttle) | exactly like Momentum (600 / 120·350 ms, asymmetric since 26.09.2026) |
| **Comfort, Eco (Efficiency)** | **always** follows the throttle: 3 % at 0 % throttle up to 100 % at full throttle (independent of day/night) | clearly slower, asymmetric (1000 / 250·700 ms) |
| unknown / no signal | like Auto/Individual | like Auto/Individual |

“Without overshoot” for Dynamic: it is just a simple low-pass
(exponential smoothing as in Momentum), no spring/PID model – that
cannot overshoot in principle, independent of the time constant.

**Experiment “more in-between colours”** (active on request, `TRY_BLENDED_COLORS
= true` in `MomentumSelect.java`): instead of snapping hard to the one nearest
factory colour, Momentum Select blends weighted between the
**two** nearest factory colours – more perceptible in-between tones, but the
blended values are no longer pure factory colours and could show the same flicker effect
on the retrofit strip as free streaming. On
flicker: `TRY_BLENDED_COLORS` to `false` – then exactly the same hard snap
as in Momentum, only in this separate file. Both variants are deliberately kept in the
code (a single switch), not as two files.

### Gear lever D → S (in Momentum and Momentum select)

Directly in `effects/Momentum.java` and
`effects/MomentumSelect.java` since 30.09.2026. Created on 27.–29.09.2026 as two
separate test effects “Momentum GDS” / “Momentum GDS select” (deliberately separate at the user’s request,
so the originals stayed untouched); adopted after the
successful live test, the two test effects were removed
(copies: `backups/2026-09-30-vor-gds-merge/`). On the MIB the old
files `momentumgds.ambiente` and `momentumgdsselect.ambiente` have to be deleted,
otherwise they keep showing up in the menu (see DEPLOY_TELNET.md 4.5b).

Source: `VehicleData.gearTransMode` (`DynamicVehicleInfoMidFrequent`,
`automaticGearShiftTransMode`). If the lever is on **S** (sport) **and** Audi
drive select is **not** on Dynamic (`DriveModeData.PROFILE_DYNAMIC`),
a white entry sequence plays on the **change** to S (not permanently, only once per switch)
– “like braking, only in white”:

1. **Instantly** white at 100 % brightness (day and night the same, without a ramp),
   held for 1 s (`FLASH_HOLD_MS`).
2. Then a 2 s soft fade (`FLASH_FADE_MS`) from white to whatever the
   effect is currently computing for colour/brightness (followed live,
   no static target; at night it therefore fades down to the then valid, darker
   night brightness).
3. After that 1:1 normal Momentum/Momentum select, no further
   special treatment until the lever is switched to D again and then to S again.

Back to **D**: normal behaviour immediately, no transition. **Dynamic deliberately
excluded** (user’s requirement): there the transmission is permanently on
S anyway, the signal would be meaningless. **The brake still has priority** over everything,
even over the sequence; the sequence runs by time (not by tick count)
and has therefore progressed accordingly after a brief braking in the middle. If
you brake *while* the lever is switched to S, the red wins and the
white sequence only starts when the brake is released.

The earlier intermediate step “1 s white at 20 %” before the 100 % jump was dropped
on 30.09.2026 at the user’s request. The very first attempt (28.09., permanent
colour pull towards red as long as S is active) felt restless in the live test because
the speed rainbow fought against it in parallel.

Confirmed with `javap` against your own `lsd.jar` (MU1329):
`GEARTRANSMISSIONMODE_POSITION_D=4`, `_S=5` – present identically on this
vehicle.

### Atmosphere (calm, non-reactive variant)

**Also a completely independent file** – `effects/Atmosphere.java`
imports/extends/references neither Momentum nor Momentum Select.
Deliberately the opposite of the other two: those are *reactive* (they jump with
speed/throttle/brake), Atmosphere is *calm* – no relation to speed, throttle or
brake, meant as a mood for everyone in the car instead of a signal for the
driver.

* **Hue**: depends on the **outside temperature** – the source is the new
  `OutsideTempData` (`org.dsi.ifc.carkombi.DSICarKombi`, attribute 65,
  `updateBCOutsideTemperature`, the same number as the thermometer icon in the
  instrument cluster display). Cold (≤ −5 °C) → cool blue (hue 210°), warm (≥ 30 °C)
  → warm amber (hue 30°), interpolated linearly in between. **Not yet
  confirmed live beyond doubt** (unlike `DaylightData`/`DriveModeData`)
  – whether a real value arrives or only the neutral placeholder
  (15 °C) is running is checked by the alive log directly since 26.09.2026 (`temp[valid=…
  c=…]`, see `AmbientFx.loop()`). Additionally, since
  26.09.2026 `OutsideTempData` also checks `CarBCTemperature.state == NORMAL_OPERATION`, not just
  the coarse `validFlag`. The temperature is also followed very sluggishly
  (~8 s time constant) – nothing is supposed to jump after it here.
* **Brightness**: no relation to throttle/speed. Instead a slow
  “breathing” – a sine between a min and max value over ~5 s, somewhat more
  present during the day (50–85 %), clearly more subdued at night (15–35 %).
* **Brake**: **deliberately not evaluated** – no hard red as in
  Momentum/Momentum Select. That would destroy the calm character immediately.
* **Indicator**: since 26.09.2026 **no reaction any more** (earlier versions
  let the contour pulse amber when indicating – removed because of the
  send-discipline problem). Of
  driving behaviour Atmosphere now reacts to **nothing at all**.
* **Factory colour snap**: like Momentum – hard snap to the one nearest
  factory colour (`Rgb.nearest` principle), not the in-between-colour blending from
  Momentum Select. According to live observation in the vehicle no
  colour problems at all occurred on the retrofit strip with it so far – so this is the
  proven basis.

### Scroll (rainbow show element)

**Fourth effect, completely independent file** – `effects/Scroll.java`.
Origin: `luka-collection/AmbientSpeedComponent.java`, mode “Scroll” –
there the hue rotates endlessly over the complete colour wheel at a
speed proportional to the vehicle speed (0.36° per (km/h·s),
formula/value taken 1:1 from Luka). Unlike Momentum (speed → fixed
hue **position**, stays at red from 100 km/h) Scroll is a
continuous hue **rotation**: the colour keeps rotating even at standstill (base rotation of 8°/s since 26.09.2026), the faster
you drive the faster it turns – no end stop, runs
endlessly in a circle.

**Deliberately NOT taken 1:1 from Luka** (see the class comment in
`Scroll.java` for the full reasoning):
* Luka streams raw HSV values over the complete colour wheel without a
  factory colour snap. We have a switch for that, `TRY_FREE_COLOR`: since the test on 26.09.2026 it is
  **active on a trial basis** (free streaming, no flicker observed so far on the retrofit strip),
  together with a compensation of the perceived brightness across the colour wheel
  (`TARGET_LUMINANCE`, `MIN_SATURATION_FLOOR`). If it flickers, set it to `false`: then Scroll
  snaps hard to the nearest factory colour just like Momentum.
* Luka’s version drives surface and contour at the same time – for us, due to the design
  (Effect interface/engine), only the contour, like everywhere in this
  project.
* Brake = instantly full red – our own addition for consistency with
  Momentum/Momentum Select, not present in Luka’s.

Brightness deliberately simple: day 100 %, night a fixed dimming value (40 %), without
throttle relation and without breathing, plus a very slow, speed-independent shimmer of the brightness (period 15 s, 85–100 %) – Scroll is a pure show element, not
driving feedback like Momentum or a mood like Atmosphere.

### Drive (throttle + steering angle)

**Fifth effect, completely independent file** – `effects/Drive.java`.
Origin: `luka-collection/AmbientSpeedComponent.java`, mode “Drive” –
described there as the “flagship”. The throttle owns the hue axis
(rest = pale violet, full throttle swings via red towards warm ember),
steering lock additionally spreads the hue and raises the saturation
– the more load/corner, the more saturated the colour, at rest pale/silvery.
Steering angle source: new field `VehicleData.wheelAngle`
(`DynamicVehicleInfoHighFrequent`, attribute 14) – connected for the
first time on 26.09.2026, confirmed with `javap` against your own `lsd.jar`
to be present identically to Luka’s finding.

**Deliberately NOT taken 1:1 from Luka** (see the class comment in
`Drive.java` for the full reasoning):
* Factory colour snap instead of raw HSV streaming – as with Scroll/Momentum.
* Only the contour – Luka’s own contour formula (`hc`/`cv`, steering lock
  spreads it further away from the rest value) is used directly as the **only**
  output, since we only have one channel anyway.
* **Brake = instantly full red**, not Luka’s azure blue – consistency with the
  already established brake convention of the other four effects was more
  important here than fidelity to the original.

The safety clamp `DRIVE_HUE_CAP` (marked by Luka explicitly as “SAFETY
INVARIANT, not a taste knob” – prevents full throttle +
full lock from unintentionally tipping the hue towards green) was taken
over 1:1. Day/night is an addition of our own, kept simple (not present at all
in Luka’s): at night the computed brightness is simply halved.

### Plugin system (since 25.09.2026, confirmed in the vehicle)

No effect is hard-wired into the jar any more – not even Momentum, Momentum
select, Atmosphere, Scroll and Drive.
All five are delivered as independent **`.ambiente` files**
and are loaded automatically at start from
`/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/` (on the MIB itself,
not the SD card – that only serves as transport)
(`EffectPluginLoader`, `java.net.URLClassLoader`). A `.ambiente` file
is technically a small jar (compiled effect class + `plugin.properties`
with class name/label) – do not want Momentum select? Simply
delete the file from the MIB, no rebuild of `ambient_fx.jar` needed. A
broken/missing plugin is skipped during the scan and logged, never a
crash – in the worst case the menu simply shows no extra entries.

Complete guide to writing and delivering your own effects:
[EFFEKTE_HANDBUCH.md](../EFFEKTE_HANDBUCH.md).

### Safety: only the contour reacts

Everything reactive (speed colour, throttle brightness, brake red) runs **exclusively
on the contour** (narrow line), never on the surface. Large-area
reacting/flashing in the peripheral field of view (footwell, large door panels)
would be a real dazzle and irritation hazard at night – the surface is therefore
left out on principle.

### Debug

The debug facade `Log.java` (`Log.i`, `Log.w`, `Log.e`, `Log.value`, `Log.enabled`) writes via `RamLog.java` into a **log in RAM only** (`/dev/shmem/ambientfx.log`, fallback `/tmp`). It is enabled exclusively by the marker file `/mnt/persist/ambientfx/debug` (survives the reboot; `RamLog.on()` checks it at most every 2 s). Without the marker no file is created. The file has a **hard limit** (`MAX_BYTES` = 256 KB per boot): after that exactly one final line follows, then nothing is written any more, nothing is rotated and nothing is deleted. If the file is deleted or shortened, `RamLog` writes again. No network, no own thread, every write error is swallowed. Measured values (`Log.value`) are only written on change (at most 64 different names). Operation and limits: [DEBUG.md](DEBUG.md).

Auto-restore of the last effect after a reboot is active
(`AmbientFx.RESTORE_ON_BOOT = true`), stored under `/mnt/persist/ambientfx/`
on the MMX. **Confirmed live (26.09.2026):** `/mnt/persist` is writable without a
prior `mount -uw`, the log showed correct
save operations per vehicle key (`ambientfx_key2.state`). The
restore path after a real reboot has not been tested separately yet,
but should work symmetrically. If writing/reading does fail
at some point, everything else stays untouched (no crash, just no restore),
see the comment above `AmbientFx.saveState()`.

---

## How it works in the stock HMI (analysis)

Everything decompiled from the `lsd.jxe` (MU1329) (`tools/decompile_stock.sh`, result local in `java_stock/`, not in the repository).

* **Screen** `600028` = `CarScreenBag5.cARCARSETTINGSINTLIGHTMAIN(...)`
  (`de.audi.tghu.car.hmi.evohigh`). Generated widget tree: 8 static
  `MenuItemController`s of type 256 (radio button), all on the Range2D model
  **601073** (X = active profile, Y = brightness), event **600040**.
  `widgetID` = profile number: 1 individuell, 2 Audi drive select, 3 Kontur-Weiß,
  4 Maritim-Blau, 5 Solar-Orange, 6 Impuls-Rot, 7 Vision-Weiß, 8 Sport-Rot.
  Visibility comes from the vehicle (`IntLightViewOptions`, models 601079…601085,
  602368) – Luka’s “Sport red” is simply profile 8 made visible.
* **Texts** come from `lang_data/resources/HMICarEvoHigh/strings_<lang>.data`
  (text IDs 601979 “individuell”, 601980 “Audi drive select”, 601030 …).
  For our entries `FxMenuItemController` sets the text directly as a
  string model on the label – no language table needed.
* **Selection**: focus → `IntLightMenuEventBusiness.processItemFocused(widgetID)`
  → `IntLightCurrentFocus`. Press → `IntLightRange2DBusiness.processKeyTyped`
  → `dsi.setIntLightActiveProfile(focus)`.
* **Vehicle feedback**: `AbstractIntLightComponent.updateIntLightActiveProfile`
  sets X of the Range2D model to the active profile.
* **DSI** `org.dsi.ifc.carlight.DSICarLight` – searched completely, these
  are **all** the setters there are:
  `setIntLightActiveProfile(int)`, `setIntLightColour(int)`,
  `setIntLightIlluminationSet(setNr, brightness)` (brightness **per zone**,
  no colour), `setIntLightAmbientLightColor(IntLightRGBValues)`,
  `setIntLightContourLightColor(IntLightRGBValues)`,
  `setIntLightIlluminationProfile(profile, brightness)`. **No further
  colour setters** – see [What does not work](#what-does-not-work-research-round-2)
  below. All fire-and-forget (`remoteCallMethod`).
* **Vehicle data** `org.dsi.ifc.carvehiclestates.DSICarVehicleStates`,
  attribute 14 `DynamicVehicleInfoHighFrequent` (engineSpeed, vehicleSpeed,
  longitudinalAcceleration, acceleratorPosition, shiftLight, powermeter …),
  attribute 15 `DynamicVehicleInfoMidFrequent` (currentGear, recommendedGear,
  driverIsBraking, acceleratorKickDown, automaticGearShiftTransMode,
  blinkingState, lowBeamHeadlightIsActive – **caution:** the latter stayed
  `false` throughout in the live test, see day/night behaviour above).
* **Day/night** `org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStates`,
  attribute 16, `updateDisplayDayNightDesign(boolean,int)` – the source that actually
  works (see `DaylightData.java`), extracted from
  `de.audi.app.terminalmode.NightDayModeHandler` (which drives CarPlay’s own
  day/night switching there).
* **Drive select mode** (for a later “Drive” effect):
  `DSICarDrivingCharacteristics` attribute 13 `CHARISMAACTIVEPROFILE`.

### What does not work (research round 2)

Two wishes from the wish list were checked against hard limits of the DSI layer
and answered – not as a guess, but based on the
completely decompiled interface:

* **Controlling individual LEDs/zones independently** (footwell ≠ door pocket ≠
  cup holder, as shown in the VW/Audi service manual wiring diagram for interior/
  background lighting with LIN bus on J519): **not possible.**
  `IntLightMembersMultiColor` / `IntLightMembersIlluminationSet` are pure
  configuration flags (“is zone X installed”), no colour values. There are only
  the two channels surface/contour; which physical LEDs hang on which channel
  is decided by the factory wiring/coding (ambient package QQ2),
  not by the Java layer.
* **Music equaliser for reactive lighting**: **not possible with what
  we have access to.** `org.dsi.ifc.audio.DSISound`/`DSISoundListener`
  searched completely – there are only tone controls (bass/mid/treble/fader as
  fixed target values), no real-time level meter/spectrum of the running playback.
  Music-reactive lighting would need a tap on the raw audio pipeline (a project of its own,
  much larger, not part of this patch).

---

## Architecture of the patch

```
java_patch/
├── de/audi/tghu/car/hmi/evohigh/CarScreenBag5.java     REPLACEMENT – only cARCARSETTINGSINTLIGHTMAIN changed
├── de/audi/app/car/evo/light/IntLightComponentEvo.java REPLACEMENT – bind engine, X limit, keep selection
├── de/audi/app/car/core/light/IntLightRange2DBusiness.java REPLACEMENT – effect entries on press
└── de/mk/ambientfx/
    ├── AmbientFx.java              engine (singleton, worker thread, sends only the contour via DSI)
    ├── FxCatalog.java              effect list, filled at runtime (see EffectPluginLoader)
    ├── EffectPluginLoader.java     loads *.ambiente files from ambientfx_effects/ on the MIB
    ├── EffectFactory.java          small factory interface for the plugin system
    ├── FxMenu.java                 builds the menu entries for the screen
    ├── FxMenuItemController.java   MenuItem with free text
    ├── VehicleData.java            DSICarVehicleStates listener (rpm, speed, throttle, brake, …)
    ├── DaylightData.java           DSIGeneralVehicleStates listener (day/night, attribute 16)
    ├── DriveModeData.java          DSICarDrivingCharacteristics listener (drive select, attribute 13)
    ├── OutsideTempData.java        DSICarKombi listener (outside temperature, attribute 65 - still untested)
    ├── ParkingDistanceData.java    DSICarParkingSystem listener (parking aid, attributes 10/11)
    ├── Effect.java / Rgb.java      contract (contour only) + colour helpers (HSV, palette, lerp)
    ├── Log.java / RamLog.java      debug facade + RAM log (only with marker, limit 256 KB)
    └── effects/                    Momentum, Momentum Select, Atmosphere, Scroll, Drive - source lives here,
                                     but they are delivered as .ambiente plugins (see below),
                                     NO LONGER hard-wired into FxCatalog
```

Classes in `/mnt/app/eso/hmi/lsd/jars/*.jar` are on the bootclasspath **in front of**
`lsd.jxe` (see `lsd.sh`), which is why classes of the same name replace the
stock classes. The three replaced classes are recompiled from the decompilate –
`verify.sh` proves that only the intended methods differ
from the original (all other methods are instruction-identical).
Everything effect-related lives in `de.mk.ambientfx` – the three stock replacement
classes did **not have to be touched a single time** for v2 (surface/contour separation,
day/night, 9th effect), which `verify.sh` confirms.

### Flow when “Momentum” is selected

1. Menu focus → `IntLightCurrentFocus = 9`
2. Press → `IntLightRange2DBusiness.processKeyTyped`: `9 >= FIRST_WIDGET_ID`
   → `AmbientFx.activate(9)`
3. Engine: Range2D X := 9 (radio button jumps), `setIntLightActiveProfile(1)`,
   worker thread starts
4. Vehicle reports `updateIntLightActiveProfile(1)` → stock code sets X = 1 →
   `IntLightComponentEvo` calls `AmbientFx.restoreSelection()` → X = 9
5. Worker: every `periodMs` → `effect.tick()` delivers the finished contour colour
   (day/night is already included, see above) → on a change
   ≥ 14 (RGB sum) `setIntLightContourLightColor`. The surface is never
   touched.
6. Choosing a stock profile → `deactivate()` + `setIntLightActiveProfile(n)`

### Adding a new effect

Since 25.09.2026 exclusively via the **plugin system** – no `FxCatalog`
entry, no rebuild of the main jar needed any more. Short version (details,
including all available vehicle data, in `EFFEKTE_HANDBUCH.md`):

1. Class in `de/mk/ambientfx/effects/` (interface `Effect`, delivers only the
   contour colour; day/night comes in as a finished `daylight` parameter,
   see `Momentum.tick()`)
2. `./build_java.sh && ./verify.sh`
3. `./build_plugin.sh java_patch/de/mk/ambientfx/effects/YourEffect.java "Label"`
   → `build/plugins/youreffect.ambiente`
4. `deploy/copy_to_sd.sh` (brings `build/plugins/` along automatically) → copy via
   telnet to `/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/`
   → complete MMI reboot

Menu, Range2D limit and selection follow automatically.

---

## Build

See **[BAUEN_BUILD.md](BAUEN_BUILD.md)**: create `vendor/` with your own `lsd.jar`, `tools/decompile_stock.sh`, `tools/prepare_sources.sh`, `./build_java.sh`, `./verify.sh`, `tools/build_all_plugins.sh`.

---

## Deploy

See **[INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)** (SD menu in the Green Engineering Menu or telnet, always the jar + all plugins together), [SD-ANLEITUNG.md](../SD-ANLEITUNG.md) and [DEPLOY_TELNET.md](../DEPLOY_TELNET.md).

Coexists with other jars in the folder `jars/` (for example `carplay_hook.jar`) – no class overlap.

**Removal:** menu entry *Uninstall*, or delete the jar and plugin folder, reboot. Afterwards the vehicle may still be on the “individuell” profile with the last effect colour – just choose a profile in the menu.

---

## Next steps / ideas

* First test of your own designs and original templates from the Design Studio in the vehicle
* Confirm the outside-temperature source (`OutsideTempData`) for Atmosphere live (see “Atmosphere” above)
* Test the restore path after a real reboot separately (see “Debug” above and the comment above `AmbientFx.saveState()`)
* Add the passive LED lighting (surface); so far AmbientFX only drives the contour
* Parking-distance effect (`ParkingDistanceData`, unit unconfirmed, see EFFEKTE_HANDBUCH.md)
* **Indicator effect** (`blinkingState` 1/2 → light up one side briefly); earlier attempts were dropped because of the send discipline (see “Atmosphere” above), please mind safety
* Luka’s “* on the steering wheel = comfort ⇄ dynamic”: own patch via
  `DSICarDrivingCharacteristics.setCharismaActiveProfile` + steering wheel button

---

## References

* Inspiration (not part of the build): [LuKa_dev’s](https://github.com/luka-dev) custom patch for the MHI2Q (we became aware of it through [mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi)); his ideas for **Drive** and **Scroll** and the bootclasspath mechanism (`jars/*.jar` in front of `lsd.jxe`) were adopted or modelled on, deviations are in the class comments.
* The Audi classes (`CarScreenBag5`, `IntLightComponentEvo`, `IntLightRange2DBusiness`) belong to Audi/e.solutions and are **not** shipped here; `patches/` only contains our changes to them.

---

<a id="deutsch"></a>

# AmbientFX – Technische Dokumentation

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **Nur zu Testzwecken auf privatem Grundstück, nicht im öffentlichen Straßenverkehr** (siehe [RECHT_LEGAL.md](RECHT_LEGAL.md)). Eingriff in die HMI-Software, Nutzung auf eigene Gefahr.

Java-Patch für das Audi MHI2Q (MU1329), der das Menü **Fahrzeugeinstellungen → Ambientebeleuchtung** um animierte und fahrzeugreaktive Effekte erweitert. Dieses Dokument beschreibt Aufbau, Funktionsweise und die Analyse des Stock-HMI.

Überblick und Schnellstart: [README](../README.md) · Bauen: [BAUEN_BUILD.md](BAUEN_BUILD.md) · Installieren: [INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md) · Eigene Effekte: [EIGENE-DESIGNS_CUSTOM-DESIGNS.md](EIGENE-DESIGNS_CUSTOM-DESIGNS.md) · Lizenz: [LICENSE.md](../LICENSE.md) (gilt für den eigenen Code, **nicht** für Audi-/Drittanbieter-Code; die ersetzten Audi-Klassen werden hier nicht mitgeliefert).

---

## Was der Patch macht

Stock-Menü (Screen 600028):

```
Kontur-Weiß        (Profil 3)
Maritim-Blau       (Profil 4)
Impuls-Rot         (Profil 6)
individuell        (Profil 1)
Audi drive select  (Profil 2)
```

Mit Patch (Jar) + den fünf mitgelieferten Plugin-Dateien (siehe
„Plugin-System" weiter unten – seit 25.09.2026 ist **kein** Effekt mehr
fest im Jar verdrahtet, alle werden als `.ambiente`-Dateien separat
installiert) kommen darunter fünf Einträge dazu (Reihenfolge im Menü ist
seit 26.09.2026 alphabetisch nach Dateiname, siehe „Plugin-System"):

| Eintrag  | Kontur (Linie)                                                  | Fläche |
|----------|------------------------------------------------------------------|--------|
| Atmosphere | **Eigenständige, bewusst nicht-reaktive** Stimmung (siehe „Atmosphere" unten) – Farbton nach Außentemperatur, Helligkeit als langsames „Atmen", keine Tempo-/Gas-/Brems-Reaktion. | unangetastet, wie Momentum |
| Drive | **Gaspedal + Lenkwinkel** steuern Farbton/Sättigung/Helligkeit (siehe „Drive" unten, Konzept/Formeln von LuKa_dev übernommen) – Ruhe = blasses Violett, Vollgas schwenkt Richtung warmes Ember, Lenkeinschlag spreizt den Farbton zusätzlich auf. Bremse = sofort voll Rot wie bei den anderen. | unangetastet, wie Momentum |
| Momentum | Farbe = Durchlauf durch **alle 28 farbigen Audi-Werksfarben** über 0–130 km/h (Rot → Orange → Gelb → Grün → Cyan → Blau → Violett → Rosa-Rot → wieder **Rot bei 130 km/h**), stufenlos zwischen benachbarten Werksfarben überblendet – siehe „Farbtabelle Momentum / Momentum select“ unten. Helligkeit **tagsüber immer 100 %** (unabhängig vom Gaspedal), **nachts** 5 % bei 0 % Gas, steigt bis 50 % Gas auf 100 % an und bleibt darüber bei 100 % (asymmetrisch geglättet, schneller heller als dunkler). Bremse = sofort voll Rot, voll hell, unabhängig von allem anderen. **Gangwahlhebel D → S** (außer im Dynamic-Profil): einmalig sofort Weiß bei 100 % (Tag und Nacht), 1 s gehalten, dann 2 s weiches Überblenden zurück zur normalen Farbe (siehe „Gangwahlhebel D → S“ unten). | **unangetastet** – bleibt der manuelle Farb-/Helligkeitsregler im MIB (eigener, vom Fahrzeug selbst gemerkter Wert) |
| Momentum select | **Eigenständige Kopie von Momentum** (siehe „Momentum Select" unten) – gleiche Farbtabelle (alle Werksfarben, 0–130 km/h) und Brems-Prinzip, aber die Farbwelt, Helligkeits- und Reaktionskurve hängt zusätzlich vom **Audi-Drive-Select-Modus** ab (Comfort/Auto/Dynamic/Eco/Individual). Dieselbe D→S-Weißsequenz wie Momentum (siehe „Gangwahlhebel D → S“ unten). | unangetastet, wie Momentum |
| Scroll | **Reines Regenbogen-Showelement** (siehe „Scroll" unten, Idee/Formel von LuKa_dev übernommen) – Farbton dreht sich mit einer zum Tempo proportionalen Geschwindigkeit endlos über das Farbrad, die Farbe dreht auch im Stand langsam weiter (8 °/s), je schneller gefahren wird, desto schneller. Kein Gas-Bezug; nachts ein fester Dimmwert (40 %), dazu ein sehr langsames Schimmern (15 s). Bremse = sofort voll Rot wie bei den anderen. | unangetastet, wie Momentum |

### Farbtabelle Momentum / Momentum select (seit 03.10.2026)

Anlass: die Farben wechselten zu hart (Snap auf nur **eine** nächste
Werksfarbe, 25 verschiedene Farben im ganzen Tempobereich, Sprünge bis 240
RGB-Stufen pro km/h) und viele Grün-/Gelbtöne der Fahrzeugliste wurden nie
angezeigt. Nach den Live-Tests mit Scroll (freies Streamen, **kein Flackern**
auf dem Retrofit-Streifen) wurde das Snapping in Momentum und Momentum select
durch einen **Farbring aus der Werksliste** ersetzt:

* Die Liste (30 Einträge, zur Laufzeit vom Fahrzeug: 28 farbig + 2 Weiß) wird
  in `buildRing()` gefiltert (Sättigung ≥ 0,35 → Weiß fällt heraus) und **nach
  Farbton ab Rot sortiert**. Die Reihenfolge der Liste im Fahrzeug spielt keine
  Rolle, die Tabelle passt sich an die gemeldete Liste an.
* Geschwindigkeit → Position auf dem **geschlossenen** Ring: 0 km/h = erstes Rot,
  130 km/h = wieder dasselbe Rot (13 Schritte à 10 km/h, `RAINBOW_SPAN_KMH=130`,
  `SPEED_STEP_KMH=10`). Zwischen zwei Werksfarben wird stufenlos überblendet
  (`SMOOTH_COLORS=true`); alle 10 km/h laufen gut zwei Werksfarben durch.
  Das letzte Stück (Violett → Rosa-Rot → Rot) schließt den Kreis, ohne dass
  die Liste eine eigene Magenta-Farbe bräuchte.
* **Rückfall bei Flackern:** `SMOOTH_COLORS=false` → nur noch echte Werksfarben
  in harten 10-km/h-Stufen (14 Farben, Stufe = Rundung auf 10 km/h).
* Ohne Werksliste (noch nicht eingetroffen): reiner Farbton wie früher.
* **Bremse unverändert:** sofort Werks-Rot, voll hell. **Helligkeit unverändert**
  (Tag 100 %, Nacht 5 % bei 0 % Gas → 100 % bei 50 % Gas; Comfort/Eco und
  Dynamic wie gehabt). **D→S-Weißsequenz unverändert.**
* Momentum select behält seine **Farbwelten je Profil**, jetzt aus echten
  Werksfarben: Auto/Individual = ganzer Ring (0 km/h Rot … 130 km/h Rot);
  Dynamic = Abschnitt Rot → Gelb über 0–130 km/h (Gas zieht weiter Richtung
  Rot); Comfort/Eco = Abschnitt Grün → Blau über 0–130 km/h.
* Messung (Simulation mit Foto-Näherung der Werksfarben, 1-km/h-Raster,
  0–140 km/h): Momentum **25 → 130 verschiedene Farben**, größter Sprung pro
  km/h **240 → 52**. 
  Die echten 30 RGB-Werte stehen bei gesetztem Debug-Marker als Zeile
  „factory color list values: …“ im Log (kommen vom Fahrzeug, nicht aus dem
  MMI-Dump).
* Backup vor dem Umbau: `backups/2026-10-03-vor-farbtabelle/`.

### Werksfarben-Snapping (bis 02.10.2026, wegen Retrofit-LED-Streifen)

> **Historisch:** gilt für Momentum/Momentum select nur noch für die **Bremse**
> und den Fallback ohne Werksliste; Atmosphere/Drive/Scroll siehe jeweils dort.
> Die Annahme „freie Zwischenwerte flackern“ hat sich mit dem Scroll-Test
> (80–130 km/h, kein Flackern) nicht bestätigt – siehe „Farbtabelle“ oben.

Der nachgerüstete LED-Streifen im Fahrzeug stellt nur die Farben aus der
**Audi-eigenen „individuell“-Farbliste** zuverlässig dar – frei gestreamte
Zwischenwerte flackern. Deshalb rechnet Momentum den Regenbogen-Farbton
intern zwar stufenlos aus, **snappt ihn aber vor jedem Senden auf die
nächstliegende echte Werksfarbe** (`Rgb.nearest(...)`, Palette kommt passiv
per DSI vom Fahrzeug, siehe `AmbientFx.onColorListChunk`). Das ist bewusst
keine optionale Einstellung mehr (das frühere Marker-Datei-Experiment ist
raus) – auf diesem Fahrzeug mit dieser LED ist es die einzige Konfiguration,
die sauber läuft. Wichtig: die Helligkeit wird **danach separat**
aufmultipliziert, nicht mitgesnappt – sonst würde jede gedimmte Farbe wieder
auf die volle Werksfarbe zurückspringen und die Gas-Helligkeit wirkungslos
machen. Bis die Werksfarbliste vom Fahrzeug eingetroffen ist (sollte
automatisch beim Start passieren, siehe Log-Zeile „factory color list
complete: N entries“), zeigt Momentum den unverfälschten Regenbogen-Farbton.

**Wichtig, anders als in v2: die Engine sendet gar keine Fläche mehr.**
`Effect.tick()` liefert nur noch die Kontur-Farbe; die Fläche bekommst du
weiterhin ganz normal über den Farbregler im MIB selbst eingestellt, und das
Fahrzeug merkt sich diesen Wert wie bei jedem anderen Profil auch – unser
Patch mischt sich da nicht ein.

Der Effekt läuft technisch auf dem Fahrzeug-Profil **„individuell“ (1)**
(nötig, damit die Kontur frei bestimmbar ist): die Engine schaltet das Profil
um und streamt dann nur die Kontur-Farbe per DSI ans Fahrzeug. Der
Radio-Button im Menü bleibt auf „Momentum“.

### Tag/Nacht-Verhalten

**Korrigiert nach dem ersten Live-Test:** die erste Fassung hatte bei
normalem, moderatem Gas (< 50 %) tagsüber quasi durchgehend 0 % Helligkeit –
dadurch war die Kontur bei normaler Tagfahrt praktisch unsichtbar, sichtbar
wurde nur ein kurzes Aufblitzen bei kräftigem Antritt (Gas > 50 %). Das war
ein Fehler in der Helligkeitslogik, keine Tag/Nacht-Abschaltung.

Die Tag/Nacht-Erkennung sitzt bewusst **im Effekt selbst** (nicht als
pauschale Regel auf Engine-Ebene – die gab es in v3 vorher, wurde aber
entfernt, weil sie Momentums Kurve wieder die Spitze gekappt hätte). Quelle
ist **`DaylightData`** (`de/mk/ambientfx/DaylightData.java`), **live im
Fahrzeug bestätigt**: Lichtschalter umlegen kippt das Signal in Echtzeit.
`VehicleData.lowBeamOn` (das erste, naheliegende Signal) blieb dagegen in
mehreren Tests durchgehend `false` – benutzt wird stattdessen derselbe
DSI-Dienst und dasselbe Attribut (`DSIGeneralVehicleStates`, Attribut 16,
`updateDisplayDayNightDesign`), das nachweislich (per Decompile von
`de.audi.app.terminalmode.NightDayModeHandler`) auch CarPlays eigene
Tag/Nacht-Umschaltung speist:

* **Tag** – Kontur immer **100 % hell**, unabhängig vom Gaspedal. Tagsüber
  blendet nichts, und „meistens unsichtbar" war schlechter als „immer
  sichtbar".
* **Nacht** – Helligkeit folgt dem Gaspedal. Verlauf mehrfach nachjustiert:
  50–100 % (zu knapp) → 25–100 % linear über den ganzen Gasbereich → seit
  26.09.2026 (zweite Kurve): **5 % bei 0 % Gas** (nie ganz aus), steigt bis
  **50 % Gas** linear auf **100 %** und bleibt darüber gedeckelt – löst den
  alltagsrelevanten unteren/mittleren Gasbereich feiner auf, statt erst bei
  Vollgas die volle Helligkeit zu erreichen. Seit 26.09.2026 zusätzlich
  **asymmetrisch** geglättet (Idee aus `luka-collection`, siehe
  `Momentum.java`): schneller heller beim Gasgeben (120 ms), spürbar
  langsamer wieder dunkler beim Loslassen (350 ms).
* **Bremse** überschreibt beides sofort mit vollem Rot bei voller Helligkeit.

### Momentum Select (Drive-Select-reaktive Variante)

**Bewusst eine komplette, unabhängige Kopie** von `Momentum.java` –
`effects/MomentumSelect.java` importiert/erweitert/referenziert Momentum an
keiner Stelle, damit beide Effekte sich nicht gegenseitig beeinflussen
können: wird eine `.ambiente`-Datei vom MIB gelöscht, bleibt der andere
Effekt unverändert lauffähig. Gleiches Grundprinzip (Regenbogen über Tempo durch den Werksfarben-Ring,
Sofort-Rot bei Bremse), aber die Helligkeits-/Reaktionskurve hängt
zusätzlich vom **Audi-Drive-Select-Modus** ab, Quelle ist die neue
`DriveModeData` (`org.dsi.ifc.cardrivingcharacteristics.DSICarDrivingCharacteristics`,
Attribut 13, `updateCharismaActiveProfile`) – dasselbe Attribut, das schon
länger in der Wunschliste stand, hier zum ersten Mal angebunden. Abfrage
über `AmbientFx.getInstance().getDriveMode()`, bewusst **nicht** als
zusätzlicher Parameter in `Effect.tick()`, damit Momentum unverändert
bleibt.

| Profil | Helligkeit | Reaktionsgeschwindigkeit (Hue / Helligkeit hoch·runter) |
|---|---|---|
| **Dynamic** | immer 100 % (Tag *und* Nacht, unabhängig vom Gaspedal) | viel schneller, **symmetrisch** (150 / 100·100 ms) – bewusst kein Attack/Release, passt zum knackigen Charakter |
| **Auto, Individual** | exakt wie Momentum (Tag 100 %, Nacht 5–100 % nach Gas, gedeckelt ab 50 % Gas) | exakt wie Momentum (600 / 120·350 ms, asymmetrisch seit 26.09.2026) |
| **Comfort, Eco (Efficiency)** | folgt **immer** dem Gaspedal: 3 % bei 0 % Gas bis 100 % bei Vollgas (unabhängig Tag/Nacht) | deutlich langsamer, asymmetrisch (1000 / 250·700 ms) |
| unbekannt / kein Signal | wie Auto/Individual | wie Auto/Individual |

„Ohne Überlauf" bei Dynamic: es ist nur ein einfacher Tiefpass
(Exponential-Glättung wie bei Momentum), kein Feder-/PID-Modell – der kann
grundsätzlich nicht überschwingen, unabhängig von der Zeitkonstante.

**Experiment „mehr Zwischenfarben"** (auf Wunsch aktiv, `TRY_BLENDED_COLORS
= true` in `MomentumSelect.java`): statt hart auf die eine nächste
Werksfarbe zu snappen, mischt Momentum Select gewichtet zwischen den
**zwei** nächsten Werksfarben – mehr wahrnehmbare Zwischentöne, aber die
Mischwerte sind keine reinen Werksfarben mehr und könnten auf dem
Retrofit-Streifen denselben Flacker-Effekt zeigen wie freies Streaming. Bei
Flackern: `TRY_BLENDED_COLORS` auf `false` – dann exakt derselbe harte Snap
wie bei Momentum, nur in dieser separaten Datei. Beide Varianten bewusst im
Code erhalten (ein einziger Schalter), nicht als zwei Dateien.

### Gangwahlhebel D → S (in Momentum und Momentum select)

Seit 30.09.2026 direkt in `effects/Momentum.java` und
`effects/MomentumSelect.java`. Entstanden am 27.–29.09.2026 als zwei
getrennte Testeffekte „Momentum GDS“ / „Momentum GDS select“ (auf Nutzerwunsch
bewusst separat, damit die Originale unangetastet blieben); nach dem
erfolgreichen Live-Test übernommen, die beiden Testeffekte sind entfernt
(Kopien: `backups/2026-09-30-vor-gds-merge/`). Auf dem MIB müssen die alten
Dateien `momentumgds.ambiente` und `momentumgdsselect.ambiente` gelöscht
werden, sonst tauchen sie weiter im Menü auf (siehe DEPLOY_TELNET.md 4.5b).

Quelle: `VehicleData.gearTransMode` (`DynamicVehicleInfoMidFrequent`,
`automaticGearShiftTransMode`). Steht der Hebel auf **S** (Sport) **und** ist
Audi drive select **nicht** auf Dynamic (`DriveModeData.PROFILE_DYNAMIC`),
spielt beim **Wechsel** auf S (nicht dauerhaft, nur einmalig pro Umschaltung)
eine weiße Eintrittssequenz – „wie Bremsen, nur in Weiß“:

1. **Sofort** Weiß bei 100 % Helligkeit (Tag und Nacht gleich, ohne Rampe),
   1 s lang gehalten (`FLASH_HOLD_MS`).
2. Danach 2 s weiches Überblenden (`FLASH_FADE_MS`) von Weiß zu dem, was der
   Effekt gerade laufend für Farbe/Helligkeit berechnet (live mitverfolgt,
   kein statisches Ziel; nachts blendet es also auf die dann gültige, dunklere
   Nachthelligkeit herunter).
3. Danach 1:1 normales Momentum/Momentum select, keine weitere
   Sonderbehandlung, bis erneut auf D und wieder auf S geschaltet wird.

Zurück auf **D**: sofort normales Verhalten, kein Übergang. **Dynamic bewusst
ausgenommen** (Nutzer-Vorgabe): dort steht das Getriebe ohnehin dauerhaft auf
S, das Signal wäre bedeutungslos. **Bremse hat weiterhin Vorrang** vor allem,
auch vor der Sequenz; die Sequenz läuft nach Uhrzeit (nicht nach Tick-Zahl)
und ist deshalb nach kurzem Bremsen mittendrin entsprechend weiter. Wird
gebremst, *während* der Hebel auf S geschaltet wird, gewinnt das Rot, und die
Weißsequenz startet erst beim Loslassen der Bremse.

Die frühere Zwischenstufe „1 s Weiß bei 20 %“ vor dem 100-%-Sprung entfiel
am 30.09.2026 auf Nutzerwunsch. Der allererste Versuch (28.09., dauerhafter
Farbzug Richtung Rot, solange S aktiv ist) wirkte im Live-Test unruhig, weil
der Tempo-Regenbogen parallel dagegen ankämpfte.

Per `javap` gegen die eigene `lsd.jar` (MU1329) bestätigt:
`GEARTRANSMISSIONMODE_POSITION_D=4`, `_S=5` – identisch auf diesem
Fahrzeug vorhanden.

### Atmosphere (ruhige, nicht-reaktive Variante)

**Ebenfalls eine komplett unabhängige Datei** – `effects/Atmosphere.java`
importiert/erweitert/referenziert weder Momentum noch Momentum Select.
Bewusst das Gegenteil der beiden anderen: die sind *reaktiv* (springen mit
Tempo/Gas/Bremse), Atmosphere ist *ruhig* – kein Bezug zu Tempo, Gas oder
Bremse, gedacht als Stimmung für alle im Auto statt als Signal für den
Fahrer.

* **Farbton**: hängt von der **Außentemperatur** ab – Quelle ist die neue
  `OutsideTempData` (`org.dsi.ifc.carkombi.DSICarKombi`, Attribut 65,
  `updateBCOutsideTemperature`, dieselbe Zahl wie das Thermometer-Icon im
  Kombi-Display). Kalt (≤ −5 °C) → kühles Blau (Hue 210°), warm (≥ 30 °C)
  → warmes Amber (Hue 30°), dazwischen linear interpoliert. **Noch nicht
  zweifelsfrei live bestätigt** (anders als `DaylightData`/`DriveModeData`)
  – ob wirklich ein echter Wert ankommt oder nur der neutrale Platzhalter
  (15 °C) läuft, prüft seit 26.09.2026 das Alive-Log direkt (`temp[valid=…
  c=…]`, siehe `AmbientFx.loop()`). Zusätzlich prüft `OutsideTempData` seit
  26.09.2026 auch `CarBCTemperature.state == NORMAL_OPERATION`, nicht nur
  das grobe `validFlag`. Die Temperatur wird zusätzlich sehr träge
  nachgezogen (~8 s Zeitkonstante) – hier soll nichts hinterherspringen.
* **Helligkeit**: kein Bezug zu Gas/Tempo. Stattdessen ein langsames
  „Atmen" – ein Sinus zwischen einem Min- und Max-Wert über ~5 s, tagsüber
  etwas präsenter (50–85 %), nachts deutlich gedämpfter (15–35 %).
* **Bremse**: wird **bewusst nicht ausgewertet** – kein hartes Rot wie bei
  Momentum/Momentum Select. Das würde den ruhigen Charakter sofort
  kaputtmachen.
* **Blinker**: seit 26.09.2026 **keine Reaktion mehr** (frühere Versionen
  ließen die Kontur beim Blinken amber pulsieren – wegen des
  Sendediscipline-Problems entfernt). Aus
  Fahrverhalten reagiert Atmosphere jetzt auf **gar nichts** mehr.
* **Werksfarben-Snap**: wie Momentum – harter Snap auf die eine nächste
  Werksfarbe (`Rgb.nearest`-Prinzip), nicht die Zwischenfarben-Mischung aus
  Momentum Select. Laut Live-Beobachtung im Fahrzeug traten damit bisher
  keinerlei Farbprobleme auf dem Retrofit-Streifen auf – das ist also die
  bewährte Basis.

### Scroll (Regenbogen-Showelement)

**Vierter Effekt, komplett unabhängige Datei** – `effects/Scroll.java`.
Ursprung: `luka-collection/AmbientSpeedComponent.java`, Modus „Scroll" –
dort dreht sich der Farbton mit einer zum Tempo proportionalen
Geschwindigkeit endlos über das komplette Farbrad (0,36° pro (km/h·s),
Formel/Wert 1:1 von Luka übernommen). Anders als Momentum (Tempo → feste
Hue-**Position**, bleibt ab 100 km/h bei Rot stehen) ist Scroll eine
fortlaufende Hue-**Rotation**: die Farbe dreht auch im Stand weiter (Grundrotation 8 °/s seit 26.09.2026), je schneller
gefahren wird, desto schneller – kein Anschlag, läuft
unbegrenzt im Kreis.

**Bewusst NICHT 1:1 von Luka übernommen** (siehe Klassenkommentar in
`Scroll.java` für die volle Begründung):
* Luka streamt rohe HSV-Werte über das komplette Farbrad ohne
  Werksfarben-Snap. Bei uns gibt es dafür den Schalter `TRY_FREE_COLOR`: seit dem Test am 26.09.2026 ist er
  **testweise aktiv** (freies Streaming, bisher kein Flackern auf dem Retrofit-Streifen beobachtet),
  zusammen mit einem Ausgleich der wahrgenommenen Helligkeit über das Farbrad
  (`TARGET_LUMINANCE`, `MIN_SATURATION_FLOOR`). Bei Flackern auf `false` setzen: dann snappt Scroll
  wie Momentum hart auf die nächste Werksfarbe.
* Lukas Version steuert Fläche und Kontur gleichzeitig – bei uns bauartbedingt
  (Effect-Schnittstelle/Engine) nur die Kontur, wie überall in diesem
  Projekt.
* Bremse = sofort volles Rot – eigene Ergänzung für Konsistenz mit
  Momentum/Momentum Select, bei Luka nicht vorhanden.

Helligkeit bewusst einfach: Tag 100 %, Nacht ein fester Dimmwert (40 %), ohne
Gas-Bezug und ohne Atmen, dazu ein sehr langsames, tempounabhängiges Schimmern der Helligkeit (Periode 15 s, 85–100 %) – Scroll ist ein reines Showelement, kein
Fahr-Feedback wie Momentum oder eine Stimmung wie Atmosphere.

### Drive (Gaspedal + Lenkwinkel)

**Fünfter Effekt, komplett unabhängige Datei** – `effects/Drive.java`.
Ursprung: `luka-collection/AmbientSpeedComponent.java`, Modus „Drive" –
dort als „Flaggschiff" bezeichnet. Das Gaspedal besitzt die Farbton-Achse
(Ruhe = blasses Violett, Vollgas schwenkt über Rot Richtung warmes Ember),
Lenkeinschlag spreizt den Farbton zusätzlich auf und hebt die Sättigung an
– je mehr Last/Kurve, desto satter die Farbe, in Ruhe blass/silbrig.
Lenkwinkel-Quelle: neues Feld `VehicleData.wheelAngle`
(`DynamicVehicleInfoHighFrequent`, Attribut 14) – seit 26.09.2026 zum
ersten Mal angebunden, per `javap` gegen die eigene `lsd.jar`
bestätigt identisch zu Lukas Fund vorhanden.

**Bewusst NICHT 1:1 von Luka übernommen** (siehe Klassenkommentar in
`Drive.java` für die volle Begründung):
* Werksfarben-Snap statt rohem HSV-Streaming – wie bei Scroll/Momentum.
* Nur die Kontur – Lukas eigene Kontur-Formel (`hc`/`cv`, Lenkeinschlag
  spreizt sie weiter vom Ruhewert weg) wird direkt als **einziger** Ausgang
  benutzt, da wir ohnehin nur einen Kanal haben.
* **Bremse = sofort volles Rot**, nicht Lukas Azurblau – Konsistenz mit der
  bereits etablierten Bremse-Konvention der anderen vier Effekte war hier
  wichtiger als Werktreue zum Original.

Die Sicherheits-Klammer `DRIVE_HUE_CAP` (bei Luka ausdrücklich als „SAFETY
INVARIANT, not a taste knob" markiert – verhindert, dass Vollgas +
Vollausschlag den Farbton ungewollt Richtung Grün kippen lässt) wurde 1:1
übernommen. Tag/Nacht ist eine eigene, einfache Ergänzung (bei Luka gar
nicht vorhanden): nachts wird die berechnete Helligkeit pauschal halbiert.

### Plugin-System (seit 25.09.2026, im Fahrzeug bestätigt)

Kein Effekt ist mehr fest im Jar verdrahtet – auch Momentum, Momentum
select, Atmosphere, Scroll und Drive
nicht. Alle fünf werden als eigenständige **`.ambiente`-Dateien**
ausgeliefert und beim Start automatisch aus
`/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/` (auf dem MIB selbst,
nicht der SD-Karte – die dient nur als Transport) geladen
(`EffectPluginLoader`, `java.net.URLClassLoader`). Eine `.ambiente`-Datei
ist technisch ein kleines Jar (kompilierte Effekt-Klasse + `plugin.properties`
mit Klassenname/Label) – Momentum select nicht dabei haben wollen? Einfach
die Datei vom MIB löschen, kein Neubau von `ambient_fx.jar` nötig. Ein
defektes/fehlendes Plugin wird beim Scan übersprungen und geloggt, nie ein
Absturz – im schlimmsten Fall zeigt das Menü einfach keine Zusatz-Einträge.

Vollständige Anleitung zum Schreiben und Ausliefern eigener Effekte:
[EFFEKTE_HANDBUCH.md](../EFFEKTE_HANDBUCH.md).

### Sicherheit: nur die Kontur reagiert

Alles Reaktive (Tempofarbe, Gas-Helligkeit, Brems-Rot) läuft **ausschließlich
auf der Kontur** (schmale Linie), nie auf der Fläche. Ein großflächiges
Reagieren/Blitzen im peripheren Sichtfeld (Fußraum, große Türpappen) wäre
nachts eine echte Blend- und Irritationsgefahr – die Fläche bleibt deshalb
grundsätzlich außen vor.

### Debug

Die Debug-Fassade `Log.java` (`Log.i`, `Log.w`, `Log.e`, `Log.value`, `Log.enabled`) schreibt über `RamLog.java` in ein **Protokoll nur im RAM** (`/dev/shmem/ambientfx.log`, Ausweich `/tmp`). Eingeschaltet wird sie ausschließlich durch die Marker-Datei `/mnt/persist/ambientfx/debug` (überlebt den Neustart; `RamLog.on()` prüft sie höchstens alle 2 s). Ohne Marker entsteht keine Datei. Die Datei hat eine **harte Obergrenze** (`MAX_BYTES` = 256 KB pro Start): danach folgt genau eine Schlusszeile, dann wird nichts mehr geschrieben, es wird nichts rotiert und nichts gelöscht. Wird die Datei gelöscht oder gekürzt, schreibt `RamLog` wieder. Kein Netz, kein eigener Thread, jeder Schreibfehler wird verschluckt. Messwerte (`Log.value`) werden nur bei Änderung geschrieben (höchstens 64 verschiedene Namen). Bedienung und Grenzen: [DEBUG.md](DEBUG.md).

Auto-Restore des letzten Effekts nach Neustart ist aktiv
(`AmbientFx.RESTORE_ON_BOOT = true`), Ablage unter `/mnt/persist/ambientfx/`
auf der MMX. **Live bestätigt (26.09.2026):** `/mnt/persist` ist ohne
vorheriges `mount -uw` beschreibbar, das Log zeigte korrekte
Speichervorgänge je Fahrzeugschlüssel (`ambientfx_key2.state`). Der
Restore-Pfad nach einem echten Neustart ist noch nicht separat getestet,
sollte aber symmetrisch funktionieren. Schlägt das Schreiben/Lesen doch
mal fehl, bleibt alles Übrige unberührt (kein Absturz, nur kein Restore),
siehe Kommentar über `AmbientFx.saveState()`.

---

## Wie es im Stock-HMI funktioniert (Analyse)

Alles aus der `lsd.jxe` (MU1329) decompiliert (`tools/decompile_stock.sh`, Ergebnis lokal in `java_stock/`, nicht im Repository).

* **Screen** `600028` = `CarScreenBag5.cARCARSETTINGSINTLIGHTMAIN(...)`
  (`de.audi.tghu.car.hmi.evohigh`). Generierter Widget-Baum: 8 statische
  `MenuItemController` vom Typ 256 (Radio-Button), alle am Range2D-Modell
  **601073** (X = aktives Profil, Y = Helligkeit), Event **600040**.
  `widgetID` = Profilnummer: 1 individuell, 2 Audi drive select, 3 Kontur-Weiß,
  4 Maritim-Blau, 5 Solar-Orange, 6 Impuls-Rot, 7 Vision-Weiß, 8 Sport-Rot.
  Sichtbarkeit kommt vom Fahrzeug (`IntLightViewOptions`, Modelle 601079…601085,
  602368) – Lükas „Sport red“ ist schlicht Profil 8 sichtbar.
* **Texte** kommen aus `lang_data/resources/HMICarEvoHigh/strings_<lang>.data`
  (Text-IDs 601979 „individuell“, 601980 „Audi drive select“, 601030 …).
  Für unsere Einträge setzt `FxMenuItemController` den Text direkt als
  String-Modell auf das Label – keine Sprachtabelle nötig.
* **Auswahl**: Fokus → `IntLightMenuEventBusiness.processItemFocused(widgetID)`
  → `IntLightCurrentFocus`. Druck → `IntLightRange2DBusiness.processKeyTyped`
  → `dsi.setIntLightActiveProfile(focus)`.
* **Fahrzeug-Rückmeldung**: `AbstractIntLightComponent.updateIntLightActiveProfile`
  setzt X des Range2D-Modells auf das aktive Profil.
* **DSI** `org.dsi.ifc.carlight.DSICarLight` – vollständig durchsucht, das
  sind **alle** Setter, die es gibt:
  `setIntLightActiveProfile(int)`, `setIntLightColour(int)`,
  `setIntLightIlluminationSet(setNr, helligkeit)` (Helligkeit **pro Zone**,
  keine Farbe), `setIntLightAmbientLightColor(IntLightRGBValues)`,
  `setIntLightContourLightColor(IntLightRGBValues)`,
  `setIntLightIlluminationProfile(profil, helligkeit)`. **Keine weiteren
  Farb-Setter** – siehe [Was nicht geht](#was-nicht-geht-recherche-runde-2)
  unten. Alles fire-and-forget (`remoteCallMethod`).
* **Fahrzeugdaten** `org.dsi.ifc.carvehiclestates.DSICarVehicleStates`,
  Attribut 14 `DynamicVehicleInfoHighFrequent` (engineSpeed, vehicleSpeed,
  longitudinalAcceleration, acceleratorPosition, shiftLight, powermeter …),
  Attribut 15 `DynamicVehicleInfoMidFrequent` (currentGear, recommendedGear,
  driverIsBraking, acceleratorKickDown, automaticGearShiftTransMode,
  blinkingState, lowBeamHeadlightIsActive – **Vorsicht:** Letzteres blieb im
  Live-Test durchgehend `false`, siehe Tag/Nacht-Verhalten oben).
* **Tag/Nacht** `org.dsi.ifc.generalvehiclestates.DSIGeneralVehicleStates`,
  Attribut 16, `updateDisplayDayNightDesign(boolean,int)` – die tatsächlich
  funktionierende Quelle (siehe `DaylightData.java`), extrahiert aus
  `de.audi.app.terminalmode.NightDayModeHandler` (steuert dort CarPlays
  eigene Tag/Nacht-Umschaltung).
* **Drive-Select-Modus** (für einen späteren „Drive“-Effekt):
  `DSICarDrivingCharacteristics` Attribut 13 `CHARISMAACTIVEPROFILE`.

### Was nicht geht (Recherche Runde 2)

Zwei Wünsche aus der Wunschliste sind an harten Grenzen der DSI-Schicht
geprüft und beantwortet worden – nicht als Vermutung, sondern anhand des
vollständig decompilierten Interfaces:

* **Einzelne LEDs/Zonen unabhängig ansteuern** (Fußraum ≠ Türtasche ≠
  Becherhalter, wie im VW/Audi-Servicehandbuch-Schaltplan zu Interior/
  Background Lighting mit LIN-Bus an J519 gezeigt): **nicht möglich.**
  `IntLightMembersMultiColor` / `IntLightMembersIlluminationSet` sind reine
  Konfigurations-Flags („ist Zone X verbaut“), keine Farbwerte. Es gibt nur
  die zwei Kanäle Fläche/Kontur; welche physischen LEDs an welchem Kanal
  hängen, entscheidet die Werksverkabelung/Codierung (Ambiente-Paket QQ2),
  nicht die Java-Schicht.
* **Musik-Equalizer für reaktives Licht**: **nicht möglich mit dem, worauf
  wir Zugriff haben.** `org.dsi.ifc.audio.DSISound`/`DSISoundListener`
  komplett durchsucht – es gibt nur Klangregler (Bass/Mitten/Höhen/Fader als
  feste Sollwerte), keinen Echtzeit-Pegelmesser/Spektrum der laufenden Wiedergabe.
  Musikreaktives Licht bräuchte einen Tap auf die rohe Audio-Pipeline (eigenes,
  deutlich größeres Projekt, nicht Teil dieses Patches).

---

## Architektur des Patches

```
java_patch/
├── de/audi/tghu/car/hmi/evohigh/CarScreenBag5.java     ERSATZ – nur cARCARSETTINGSINTLIGHTMAIN geändert
├── de/audi/app/car/evo/light/IntLightComponentEvo.java ERSATZ – Engine binden, X-Limit, Auswahl halten
├── de/audi/app/car/core/light/IntLightRange2DBusiness.java ERSATZ – Effekt-Einträge beim Drücken
└── de/mk/ambientfx/
    ├── AmbientFx.java              Engine (Singleton, Worker-Thread, sendet nur die Kontur per DSI)
    ├── FxCatalog.java              Effekt-Liste, zur Laufzeit befüllt (siehe EffectPluginLoader)
    ├── EffectPluginLoader.java     laedt *.ambiente-Dateien aus ambientfx_effects/ auf dem MIB
    ├── EffectFactory.java          kleine Fabrik-Schnittstelle fürs Plugin-System
    ├── FxMenu.java                 baut die Menü-Einträge für den Screen
    ├── FxMenuItemController.java   MenuItem mit freiem Text
    ├── VehicleData.java            DSICarVehicleStates-Listener (rpm, speed, gas, bremse, …)
    ├── DaylightData.java           DSIGeneralVehicleStates-Listener (Tag/Nacht, Attribut 16)
    ├── DriveModeData.java          DSICarDrivingCharacteristics-Listener (Drive Select, Attribut 13)
    ├── OutsideTempData.java        DSICarKombi-Listener (Außentemperatur, Attribut 65 - noch ungetestet)
    ├── ParkingDistanceData.java    DSICarParkingSystem-Listener (Einparkhilfe, Attribute 10/11)
    ├── Effect.java / Rgb.java      Vertrag (nur Kontur) + Farb-Helfer (HSV, Palette, Lerp)
    ├── Log.java / RamLog.java      Debug-Fassade + RAM-Protokoll (nur mit Marker, Obergrenze 256 KB)
    └── effects/                    Momentum, Momentum Select, Atmosphere, Scroll, Drive - Quelltext liegt hier,
                                     ausgeliefert werden sie aber als .ambiente-Plugins (siehe unten),
                                     NICHT mehr fest in FxCatalog verdrahtet
```

Klassen in `/mnt/app/eso/hmi/lsd/jars/*.jar` stehen im Bootclasspath **vor**
`lsd.jxe` (siehe `lsd.sh`), deshalb ersetzen gleichnamige Klassen die
Stock-Klassen. Die drei ersetzten Klassen sind aus dem Decompilat neu
kompiliert – `verify.sh` beweist, dass sich nur die beabsichtigten Methoden
vom Original unterscheiden (alle anderen Methoden sind instruktionsgleich).
Alles Effekt-bezogene lebt in `de.mk.ambientfx` – die drei Stock-Ersatz-
Klassen mussten für v2 (Fläche/Kontur-Trennung, Tag/Nacht, 9. Effekt)
**kein einziges Mal** angefasst werden, das bestätigt `verify.sh`.

### Ablauf bei Auswahl „Momentum“

1. Menü-Fokus → `IntLightCurrentFocus = 9`
2. Drücken → `IntLightRange2DBusiness.processKeyTyped`: `9 >= FIRST_WIDGET_ID`
   → `AmbientFx.activate(9)`
3. Engine: Range2D-X := 9 (Radio-Button springt), `setIntLightActiveProfile(1)`,
   Worker-Thread startet
4. Fahrzeug meldet `updateIntLightActiveProfile(1)` → Stock-Code setzt X = 1 →
   `IntLightComponentEvo` ruft `AmbientFx.restoreSelection()` → X = 9
5. Worker: alle `periodMs` → `effect.tick()` liefert die fertige Kontur-Farbe
   (Tag/Nacht ist darin bereits eingerechnet, siehe oben) → bei Änderung
   ≥ 14 (RGB-Summe) `setIntLightContourLightColor`. Die Fläche wird nie
   angefasst.
6. Stock-Profil wählen → `deactivate()` + `setIntLightActiveProfile(n)`

### Neuen Effekt hinzufügen

Seit 25.09.2026 ausschließlich über das **Plugin-System** – kein `FxCatalog`-
Eintrag, kein Neubau des Haupt-Jars mehr nötig. Kurzfassung (Details,
inkl. aller verfügbaren Fahrzeugdaten, in `EFFEKTE_HANDBUCH.md`):

1. Klasse in `de/mk/ambientfx/effects/` (Interface `Effect`, liefert nur die
   Kontur-Farbe; Tag/Nacht kommt als fertiger `daylight`-Parameter rein,
   siehe `Momentum.tick()`)
2. `./build_java.sh && ./verify.sh`
3. `./build_plugin.sh java_patch/de/mk/ambientfx/effects/DeinEffekt.java "Label"`
   → `build/plugins/deineffekt.ambiente`
4. `deploy/copy_to_sd.sh` (bringt `build/plugins/` automatisch mit) → per
   Telnet nach `/net/mmx/mnt/app/eso/hmi/lsd/ambientfx_effects/` kopieren
   → kompletter MMI-Neustart

Menü, Range2D-Limit und Auswahl ziehen automatisch nach.

---

## Build

Siehe **[BAUEN_BUILD.md](BAUEN_BUILD.md)**: `vendor/` mit der eigenen `lsd.jar` anlegen, `tools/decompile_stock.sh`, `tools/prepare_sources.sh`, `./build_java.sh`, `./verify.sh`, `tools/build_all_plugins.sh`.

---

## Deploy

Siehe **[INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)** (SD-Menü im Green Engineering Menu oder Telnet, immer Jar + alle Plugins zusammen), [SD-ANLEITUNG.md](../SD-ANLEITUNG.md) und [DEPLOY_TELNET.md](../DEPLOY_TELNET.md).

Verträgt sich mit anderen Jars im Ordner `jars/` (zum Beispiel `carplay_hook.jar`) – keine Klassen-Überschneidung.

**Rückbau:** Menüpunkt *Deinstallieren* oder Jar und Plugin-Ordner löschen, Neustart. Das Fahrzeug steht danach ggf. noch auf dem Profil „individuell“ mit der letzten Effektfarbe – im Menü einfach ein Profil wählen.

---

## Nächste Schritte / Ideen

* Eigene Designs und Original-Vorlagen aus dem Design Studio zum ersten Mal im Fahrzeug testen
* Außentemperatur-Quelle (`OutsideTempData`) für Atmosphere live bestätigen (siehe „Atmosphere“ oben)
* Den Restore-Pfad nach einem echten Neustart separat testen (siehe „Debug“ oben und Kommentar über `AmbientFx.saveState()`)
* Passive LED-Beleuchtung (Fläche) mit aufnehmen; bisher steuert AmbientFX nur die Kontur
* Einparkhilfe-Effekt (`ParkingDistanceData`, Einheit unbestätigt, siehe EFFEKTE_HANDBUCH.md)
* **Blinker-Effekt** (`blinkingState` 1/2 → Seite kurz aufleuchten lassen); frühere Versuche wurden wegen der Sendedisziplin verworfen (siehe „Atmosphere“ oben), bitte Sicherheit beachten
* Lükas „* am Lenkrad = comfort ⇄ dynamic“: eigener Patch über
  `DSICarDrivingCharacteristics.setCharismaActiveProfile` + Lenkrad-Taste

---

## Referenzen

* Inspiration (kein Build-Bestandteil): [LuKa_devs](https://github.com/luka-dev) Custom-Patch für das MHI2Q (aufmerksam geworden durch [mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi)); seine Ideen zu **Drive** und **Scroll** und der Bootclasspath-Mechanismus (`jars/*.jar` vor `lsd.jxe`) sind übernommen bzw. angelehnt, Abweichungen stehen in den Klassenkommentaren.
* Die Audi-Klassen (`CarScreenBag5`, `IntLightComponentEvo`, `IntLightRange2DBusiness`) gehören Audi/e.solutions und werden hier **nicht** mitgeliefert; `patches/` enthält nur unsere Änderungen daran.
